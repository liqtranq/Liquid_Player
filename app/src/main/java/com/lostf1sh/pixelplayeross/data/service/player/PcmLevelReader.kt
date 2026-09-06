package com.lostf1sh.pixelplayeross.data.service.player

import java.nio.ByteBuffer
import kotlin.math.sqrt

/** Reads interleaved PCM without moving or modifying the decoder's buffer. */
internal object PcmLevelReader {
    enum class Encoding(val bytes: Int) { U8(1), S16(2), S24(3), S32(4), FLOAT(4) }
    data class Levels(val left: Float, val right: Float)

    fun read(buffer: ByteBuffer, start: Int, end: Int, channels: Int, encoding: Encoding): Levels {
        require(channels > 0)
        val stride = encoding.bytes * channels
        var left = 0.0
        var right = 0.0
        var count = 0
        var offset = start
        while (offset + stride <= end) {
            val l = sample(buffer, offset, encoding)
            val r = if (channels == 1) l else sample(buffer, offset + encoding.bytes, encoding)
            left += l * l
            right += r * r
            count++
            offset += stride
        }
        return if (count == 0) Levels(0f, 0f)
        else Levels(sqrt(left / count).toFloat(), sqrt(right / count).toFloat())
    }

    private fun sample(buffer: ByteBuffer, offset: Int, encoding: Encoding): Double {
        // Android raw PCM is little endian. Do not depend on ByteBuffer.order().
        var bits = 0
        for (i in 0 until encoding.bytes) bits = bits or ((buffer.get(offset + i).toInt() and 255) shl (8 * i))
        return when (encoding) {
            Encoding.U8 -> (bits - 128) / 128.0
            Encoding.S16 -> bits.toShort() / 32768.0
            Encoding.S24 -> (bits shl 8 shr 8) / 8388608.0
            Encoding.S32 -> bits / 2147483648.0
            Encoding.FLOAT -> Float.fromBits(bits).let { if (it.isFinite()) it.toDouble().coerceIn(-1.0, 1.0) else 0.0 }
        }
    }
}
