package com.lostf1sh.pixelplayeross.data.service.player

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PcmLevelReaderTest {
    @Test fun `stereo channels have independent measured levels and input stays untouched`() {
        val data = ByteBuffer.allocate(400).order(ByteOrder.LITTLE_ENDIAN)
        repeat(100) { data.putShort(16384); data.putShort(0) }
        data.flip()
        data.position(4)
        val copy = data.array().clone()
        val level = PcmLevelReader.read(data, data.position(), data.limit(), 2, PcmLevelReader.Encoding.S16)
        assertEquals(0.5f, level.left)
        assertEquals(0f, level.right)
        assertEquals(4, data.position())
        assertEquals(400, data.limit())
        assertArrayEquals(copy, data.array())
    }

    @Test fun `all supported PCM encodings measure full scale and mono mirrors to both channels`() {
        val samples = mapOf(
            PcmLevelReader.Encoding.U8 to byteArrayOf(0),
            PcmLevelReader.Encoding.S16 to byteArrayOf(0, -128),
            PcmLevelReader.Encoding.S24 to byteArrayOf(0, 0, -128),
            PcmLevelReader.Encoding.S32 to byteArrayOf(0, 0, 0, -128),
            PcmLevelReader.Encoding.FLOAT to byteArrayOf(0, 0, -128, -65)
        )
        samples.forEach { (encoding, bytes) ->
            val levels = PcmLevelReader.read(ByteBuffer.wrap(bytes), 0, bytes.size, 1, encoding)
            assertEquals(1f, levels.left, encoding.name)
            assertEquals(levels.left, levels.right)
        }
    }

    @Test fun `float sine wave has RMS amplitude divided by square root of two`() {
        val data = ByteBuffer.allocate(480 * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(480) { data.putFloat((0.5 * sin(2 * Math.PI * it / 48)).toFloat()) }
        val level = PcmLevelReader.read(data, 0, data.limit(), 1, PcmLevelReader.Encoding.FLOAT)
        assertEquals(0.5f / sqrt(2f), level.left, 0.0001f)
    }

    @Test fun `silence nonfinite samples and incomplete frames never produce false activity`() {
        val data = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        data.putFloat(Float.NaN).putFloat(Float.POSITIVE_INFINITY).putFloat(0f).putFloat(0f)
        assertEquals(PcmLevelReader.Levels(0f, 0f), PcmLevelReader.read(data, 0, 16, 2, PcmLevelReader.Encoding.FLOAT))
        assertEquals(PcmLevelReader.Levels(0f, 0f), PcmLevelReader.read(ByteBuffer.wrap(byteArrayOf(127)), 0, 1, 2, PcmLevelReader.Encoding.S16))
    }

    @Test fun `sink timeline waits for buffered audio and pauses flushes and volume are respected`() {
        val source = AudioMeter.Source { 0L }
        source.available = true
        source.playing = true
        source.add(1_000_000, 20_000, PcmLevelReader.Levels(0.8f, 0.4f))
        source.position(0)
        assertEquals(0f, source.read().left)
        source.position(1_000_000)
        source.volume = 0.5f
        assertEquals(0.4f, source.read().left)
        assertEquals(0.2f, source.read().right)
        source.playing = false
        assertEquals(0f, source.read().left)
        source.playing = true
        source.clear()
        assertEquals(0f, source.read().left)
    }

    @Test fun `expired audio never leaves a stuck level`() {
        val source = AudioMeter.Source { 0L }
        source.playing = true
        source.add(0, 20_000, PcmLevelReader.Levels(1f, 1f))
        source.position(1_000_000)
        assertEquals(0f, source.read().left)
    }
}
