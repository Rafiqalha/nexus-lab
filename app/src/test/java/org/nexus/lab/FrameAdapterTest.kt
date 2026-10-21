package org.nexus.lab
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
class FrameAdapterTest {
    @Test fun knownColorsPositionsPaddingCropAndSourceLifetime() {
        val colors = listOf(byteArrayOf(-1,0,0,7),byteArrayOf(0,-1,0,99),byteArrayOf(0,0,-1,3),byteArrayOf(-1,-1,-1,0))
        val src = ByteBuffer.allocate(5 + 4*24)
        for (y in 0..3) for (x in 0..3) for (k in 0..3) src.put(5+y*24+x*4+k,colors[x][k])
        src.position(5); src.limit(5+3*24+16)
        val originalPosition=src.position(); val originalLimit=src.limit()
        val dst=ByteBuffer.allocateDirect(3*3*4)
        val out=FrameAdapter.packRgba(src,4,4,24,4,FrameAdapter.Crop(1,1,4,4),dst)
        assertEquals(0,out.position());assertEquals(36,out.limit())
        for(y in 0..2) for(x in 0..2) for(k in 0..3) assertEquals(colors[x+1][k],out.get(y*12+x*4+k))
        assertEquals(originalPosition,src.position());assertEquals(originalLimit,src.limit())
        // Owned copy is independent after the camera's buffer is reused/closed.
        src.put(5+24+4,12);assertEquals(0,out.get(0).toInt())
        FrameAdapter.wipe(out);for(i in 0 until 36) assertEquals(0,out.get(i).toInt())
    }
    @Test fun rejectsTruncationRowOverlapUnknownPixelLayoutAndInvalidCrop() {
        fun rejected(src:ByteBuffer,row:Int,step:Int,c:FrameAdapter.Crop) {
            try { FrameAdapter.packRgba(src,4,4,row,step,c,ByteBuffer.allocateDirect(64));fail("must reject") }
            catch(_:IllegalArgumentException) { }
        }
        val c=FrameAdapter.Crop(0,0,4,4)
        rejected(ByteBuffer.allocate(63),16,4,c)
        rejected(ByteBuffer.allocate(64),15,4,c)
        rejected(ByteBuffer.allocate(64),16,1,c)
        rejected(ByteBuffer.allocate(64),16,4,FrameAdapter.Crop(2,0,5,4))
        rejected(ByteBuffer.allocate(64),16,4,FrameAdapter.Crop(0,0,2,4))
    }
    @Test fun boundedNearestRankMetrics() {
        val timings=Timings();assertEquals("NOT_MEASURED",timings.summary())
        repeat(600) { timings.add((it+1)*1_000_000L) }
        assertTrue(timings.summary().contains("n=512"))
        assertTrue(timings.summary().contains("p50=344"))
    }
}
