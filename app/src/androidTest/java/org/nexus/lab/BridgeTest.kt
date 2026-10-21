package org.nexus.lab
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import android.os.SystemClock
@RunWith(AndroidJUnit4::class)
class BridgeTest {
    private fun load()=Native.load(InstrumentationRegistry.getInstrumentation().targetContext)
    @Test fun packagedArm64LoadedAndColorConversionUsesCorrectChannelsAndOrientation() {
        val id=JSONObject(load());assertEquals(id.getString("sdk_sha256"),id.getString("loaded_sdk_sha256"))
        for(rotation in listOf(0,90,180,270)) for((color,luma) in listOf(intArrayOf(255,0,0,5) to 77.0,intArrayOf(0,255,0,50) to 149.0,intArrayOf(0,0,255,250) to 29.0,intArrayOf(255,255,255,0) to 255.0)) {
            val s=Native.open();var c=0L
            val pixels=ByteBuffer.allocateDirect(3*4*4)
            try {
                c=Native.create(s)
                repeat(12) { color.forEach { pixels.put(it.toByte()) } };pixels.flip()
                val v=JSONObject(Native.submit(c,pixels,3,4,rotation,1))
                assertEquals(0,v.getInt("sdk_status"));assertEquals(luma,v.getJSONArray("quality").getDouble(3),0.0)
                assertEquals(1,v.getInt("retry"));assertEquals(0,v.getInt("product_eligible"))
            } finally { FrameAdapter.wipe(pixels);assertEquals(0,Native.release(s,c,0)) }
        }
    }
    @Test fun asymmetricColorPatternDetectsChannelSwapCropAndDoubleRotation() {
        load()
        val width=5;val height=4;val row=28;val base=7
        val src=ByteBuffer.allocate(base+row*height)
        for(y in 0 until height) for(x in 0 until width) {
            val i=base+y*row+x*4
            val v=(x*83+y*47)%256
            src.put(i,v.toByte());src.put(i+1,((v+31)%256).toByte());src.put(i+2,((v+73)%256).toByte());src.put(i+3,19)
        }
        src.position(base);src.limit(base+(height-1)*row+width*4)
        val crop=FrameAdapter.Crop(1,0,5,4)
        for(rotation in listOf(0,90,180,270)) {
            val packed=ByteBuffer.allocateDirect(crop.width*crop.height*4)
            val s=Native.open();val c=Native.create(s)
            try {
                FrameAdapter.packRgba(src,width,height,row,4,crop,packed)
                val out=JSONObject(Native.submit(c,packed,crop.width,crop.height,rotation,1))
                assertEquals(1,out.getInt("best_present"))
                val w=if(rotation==90||rotation==270) crop.height else crop.width
                val h=if(rotation==90||rotation==270) crop.width else crop.height
                val hash=java.security.MessageDigest.getInstance("SHA-256")
                hash.update("nexus.roi-luma.v1\u0000".toByteArray(Charsets.UTF_8))
                hash.update(ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(w).putInt(h).array())
                for(y in 0 until h) for(x in 0 until w) {
                    val (sx,sy)=when(rotation) {90->Pair(y,crop.height-1-x);180->Pair(crop.width-1-x,crop.height-1-y);270->Pair(crop.width-1-y,x);else->Pair(x,y)}
                    val i=sy*crop.width*4+sx*4
                    val red=packed.get(i).toInt() and 255;val green=packed.get(i+1).toInt() and 255;val blue=packed.get(i+2).toInt() and 255
                    hash.update(((77*red+150*green+29*blue+128) shr 8).toByte())
                }
                assertEquals(hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) },out.getString("best_digest"))
            } finally { FrameAdapter.wipe(packed);assertEquals(0,Native.release(s,c,0)) }
        }
    }
    @Test fun timedSamplingFinalizeValidityAndRepeatedCleanup() {
        load()
        repeat(80) {
            val s=Native.open();val c=Native.create(s)
            assertEquals(0,Native.cancel(c));assertEquals(4,JSONObject(Native.progress(c)).getInt("state"))
            assertEquals(0,Native.release(s,c,0));assertNotEquals(0,Native.release(s,c,0))
        }
        for(rotation in listOf(90,270)) {
            val s=Native.open();val c=Native.create(s);var result=0L
            val bytes=ByteBuffer.allocateDirect(16*16*4)
            try {
                var ready=false
                for(i in 0..5) {
                    // Observe SDK elapsed clock; polling delay never substitutes for the check.
                    val until=SystemClock.elapsedRealtime()+3000
                    while(JSONObject(Native.progress(c)).getLong("elapsed_ms")<i*120L) {
                        check(SystemClock.elapsedRealtime()<until);Thread.sleep(5)
                    }
                    bytes.clear()
                    repeat(256) { x -> val v=(x*83+x/16*47+i)%256;bytes.put(v.toByte()).put(v.toByte()).put(v.toByte()).put(-1) };bytes.flip()
                    val v=JSONObject(Native.submit(c,bytes,16,16,rotation,i+1L));assertEquals(0,v.getInt("sdk_status"))
                    FrameAdapter.wipe(bytes)
                    if(v.getInt("state")==2) { ready=true;break }
                }
                assertTrue(ready);result=Native.finish(c)
                val out=JSONObject(Native.read(c,result));assertTrue(out.getBoolean("current_result_valid"))
                assertEquals(1,out.getInt("decision"));assertEquals(0,out.getInt("secured"));assertEquals(0,out.getInt("required_checks_complete"));assertEquals(0,out.getInt("product_eligible"))
                assertNotEquals(0,Native.cancel(0))
            } finally { FrameAdapter.wipe(bytes);assertEquals(0,Native.release(s,c,result)) }
        }
    }
    @Test fun rejectsHeapAndTruncatedBufferWithoutPublishingNewOutput() {
        load();val s=Native.open();val c=Native.create(s)
        try {
            for(buffer in listOf(ByteBuffer.allocate(64),ByteBuffer.allocateDirect(4),ByteBuffer.allocateDirect(64).apply { position(4) },ByteBuffer.allocateDirect(64).apply { limit(60) })) {
                try { Native.submit(c,buffer,4,4,0,1);fail("must reject") } catch(_:IllegalStateException) { }
            }
            assertEquals(0,JSONObject(Native.progress(c)).getInt("submitted"))
        } finally { assertEquals(0,Native.release(s,c,0)) }
    }
}
