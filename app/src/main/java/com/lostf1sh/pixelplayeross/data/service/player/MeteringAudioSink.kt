package com.lostf1sh.pixelplayeross.data.service.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/** Observes decoded PCM; leaves all buffer bytes unchanged. PCM is required when the meter is enabled. */
@UnstableApi
internal class MeteringAudioSink(sink: AudioSink, private val requirePcm: Boolean = true) : ForwardingAudioSink(sink) {
    private val source = AudioMeter.Source()
    private var encoding: PcmLevelReader.Encoding? = null
    private var channels = 0
    private var sampleRate = 0
    private var measuredBuffer: ByteBuffer? = null

    init { AudioMeter.register(source) }

    override fun supportsFormat(format: Format): Boolean =
        (!requirePcm || format.sampleMimeType == MimeTypes.AUDIO_RAW) && super.supportsFormat(format)

    override fun getFormatSupport(format: Format): Int =
        if (requirePcm && format.sampleMimeType != MimeTypes.AUDIO_RAW) AudioSink.SINK_FORMAT_UNSUPPORTED
        else super.getFormatSupport(format)

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
        channels = inputFormat.channelCount
        sampleRate = inputFormat.sampleRate
        encoding = if (inputFormat.sampleMimeType != MimeTypes.AUDIO_RAW) null else when (inputFormat.pcmEncoding) {
            C.ENCODING_PCM_8BIT -> PcmLevelReader.Encoding.U8
            C.ENCODING_PCM_16BIT -> PcmLevelReader.Encoding.S16
            C.ENCODING_PCM_24BIT -> PcmLevelReader.Encoding.S24
            C.ENCODING_PCM_32BIT -> PcmLevelReader.Encoding.S32
            C.ENCODING_PCM_FLOAT -> PcmLevelReader.Encoding.FLOAT
            else -> null
        }
        source.available = encoding != null && channels > 0 && sampleRate > 0
        source.clear()
        measuredBuffer = null
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        val pcm = encoding
        if (buffer !== measuredBuffer && AudioMeter.enabled && pcm != null && channels > 0 && sampleRate > 0) {
            val stride = pcm.bytes * channels
            val framesPerWindow = (sampleRate / 50).coerceAtLeast(1)
            val start = buffer.position()
            var offset = start
            while (offset + stride <= buffer.limit()) {
                val frames = minOf(framesPerWindow, (buffer.limit() - offset) / stride)
                val end = offset + frames * stride
                source.add(
                    presentationTimeUs + (offset - start) / stride * 1_000_000L / sampleRate,
                    frames * 1_000_000L / sampleRate,
                    PcmLevelReader.read(buffer, offset, end, channels, pcm)
                )
                offset = end
            }
        }
        measuredBuffer = buffer
        val consumed = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        if (consumed) measuredBuffer = null
        updatePosition()
        return consumed
    }

    private fun updatePosition() {
        val position = super.getCurrentPositionUs(false)
        if (position != Long.MIN_VALUE) source.position(position)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long = super.getCurrentPositionUs(sourceEnded).also {
        if (it != Long.MIN_VALUE) source.position(it)
    }
    override fun play() { super.play(); updatePosition(); source.playing = true }
    override fun pause() { super.pause(); source.playing = false }
    override fun setVolume(volume: Float) { super.setVolume(volume); source.volume = volume }
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        super.setPlaybackParameters(playbackParameters)
        source.speed = super.getPlaybackParameters().speed
    }
    override fun handleDiscontinuity() { super.handleDiscontinuity(); source.clear(); measuredBuffer = null }
    override fun flush() { super.flush(); source.clear(); measuredBuffer = null }
    override fun reset() { super.reset(); source.clear(); source.available = false; measuredBuffer = null }
    override fun release() { try { super.release() } finally { AudioMeter.unregister(source) } }
}
