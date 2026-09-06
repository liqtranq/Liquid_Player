package com.lostf1sh.pixelplayeross.data.service.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import io.mockk.*
import java.nio.ByteBuffer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MeteringAudioSinkTest {
    @Test fun `enabled meter requires decoded PCM and disabled meter preserves encoded output support`() {
        val delegate = mockk<AudioSink>(relaxed = true)
        every { delegate.supportsFormat(any()) } returns true
        every { delegate.getFormatSupport(any()) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        val encoded = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).build()
        val pcm = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(C.ENCODING_PCM_16BIT).build()
        val enabled = MeteringAudioSink(delegate, requirePcm = true)
        val disabled = MeteringAudioSink(delegate, requirePcm = false)
        try {
            assertFalse(enabled.supportsFormat(encoded))
            assertEquals(AudioSink.SINK_FORMAT_UNSUPPORTED, enabled.getFormatSupport(encoded))
            assertTrue(enabled.supportsFormat(pcm))
            assertTrue(disabled.supportsFormat(encoded))
        } finally { enabled.release(); disabled.release() }
    }

    @Test fun `buffer retries are delegated without consuming or replacing the decoder buffer`() {
        val delegate = mockk<AudioSink>(relaxed = true)
        every { delegate.getCurrentPositionUs(any()) } returns 0L
        val data = ByteBuffer.wrap(byteArrayOf(0, 64, 0, 32))
        every { delegate.handleBuffer(data, 0L, 1) } returnsMany listOf(false, true)
        val sink = MeteringAudioSink(delegate)
        AudioMeter.subscribe()
        try {
            sink.configure(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setPcmEncoding(C.ENCODING_PCM_16BIT).setChannelCount(2).setSampleRate(48000).build(), 0, null)
            assertFalse(sink.handleBuffer(data, 0L, 1))
            assertTrue(sink.handleBuffer(data, 0L, 1))
            assertEquals(0, data.position())
            assertArrayEquals(byteArrayOf(0, 64, 0, 32), data.array())
            verify(exactly = 2) { delegate.handleBuffer(data, 0L, 1) }
        } finally { AudioMeter.unsubscribe(); sink.release() }
    }
}
