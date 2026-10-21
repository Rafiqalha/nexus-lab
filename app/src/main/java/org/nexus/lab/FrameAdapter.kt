package org.nexus.lab

import java.nio.ByteBuffer

/** Pinned CameraX RGBA byte order, not Android Bitmap's platform-dependent integer order.
 * Receives a duplicate's current position/limit. Crops in SOURCE axes, copies straight
 * full-range RGB unchanged; core rotates once. No mirror applied (back camera only).
 * Caller owns/reuses the bounded direct destination and wipes it after synchronous JNI.
 */
object FrameAdapter {
    const val MAX_BYTES = 16 * 1024 * 1024
    data class Crop(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }
    fun packRgba(source: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int,
                 crop: Crop, destination: ByteBuffer): ByteBuffer {
        require(width in 3..4096 && height in 3..4096)
        require(pixelStride == 4 && rowStride.toLong() >= width.toLong() * 4)
        require(crop.left >= 0 && crop.top >= 0 && crop.right <= width && crop.bottom <= height)
        require(crop.width >= 3 && crop.height >= 3)
        val bytes = crop.width.toLong() * crop.height * 4
        require(bytes <= MAX_BYTES && bytes <= destination.capacity() && destination.isDirect)
        val base = source.position().toLong()
        val end = base + (height - 1L) * rowStride + width.toLong() * 4
        require(end <= source.limit().toLong())
        val src = source.duplicate()
        destination.clear()
        for (y in crop.top until crop.bottom) {
            val start = base + y.toLong() * rowStride + crop.left.toLong() * 4
            src.limit((start + crop.width.toLong() * 4).toInt())
            src.position(start.toInt())
            destination.put(src)
        }
        destination.flip()
        return destination
    }
    fun wipe(buffer: ByteBuffer) {
        buffer.clear()
        while (buffer.remaining() >= 8) buffer.putLong(0L)
        while (buffer.hasRemaining()) buffer.put(0)
        buffer.clear()
    }
}
