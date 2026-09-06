package com.lostf1sh.pixelplayeross.data.media.converter

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import com.lostf1sh.pixelplayeross.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

enum class TargetFormat(val label: String, val extension: String, val mimeType: String) {
    AAC_M4A("M4A / AAC", "m4a", "audio/mp4"),
    WAV_PCM("WAV / PCM 16-bit", "wav", "audio/wav")
}

data class ConversionConfig(
    val targetFormat: TargetFormat = TargetFormat.AAC_M4A,
    val bitrate: Int = 192_000,
    val preserveMetadata: Boolean = true
)

sealed interface ConversionState {
    data object Idle : ConversionState
    data class Converting(val progress: Int, val phase: String) : ConversionState
    data class Success(val outputFile: File, val outputUri: Uri?) : ConversionState
    data class Error(val message: String) : ConversionState
}

object AudioConverter {
    private const val TAG = "AudioConverter"
    private const val TIMEOUT_US = 10_000L
    private const val AAC_MIME = "audio/mp4a-latm"
    private const val WAV_HEADER_SIZE = 44

    suspend fun convert(
        context: Context,
        song: Song,
        config: ConversionConfig,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val outputDir = File(musicDir, "Liquid_Converted").apply {
                if (!exists()) mkdirs()
            }

            val safeArtist = song.displayArtist.ifBlank { "Unknown" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val safeTitle = song.title.ifBlank { "Untitled" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            var outputFile = File(outputDir, "$safeArtist - $safeTitle.${config.targetFormat.extension}")
            var counter = 1
            while (outputFile.exists()) {
                outputFile = File(outputDir, "$safeArtist - $safeTitle ($counter).${config.targetFormat.extension}")
                counter++
            }

            val sourceUri = if (song.path.isNotBlank() && File(song.path).exists()) {
                Uri.fromFile(File(song.path))
            } else {
                song.contentUriString.toUri()
            }

            onProgress(5, "Инициализация кодека...")

            when (config.targetFormat) {
                TargetFormat.WAV_PCM -> decodeToWav(context, sourceUri, outputFile, onProgress)
                TargetFormat.AAC_M4A -> transcodeToAac(context, sourceUri, outputFile, config.bitrate, onProgress)
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Выходной файл пуст или не был создан"))
            }

            // Tagging
            if (config.preserveMetadata) {
                onProgress(95, "Запись метаданных тегов...")
                try {
                    val audioFile = AudioFileIO.read(outputFile)
                    val tag = audioFile.tagOrCreateAndSetDefault
                    if (song.title.isNotBlank()) tag.setField(FieldKey.TITLE, song.title)
                    if (song.displayArtist.isNotBlank()) tag.setField(FieldKey.ARTIST, song.displayArtist)
                    if (song.album.isNotBlank()) tag.setField(FieldKey.ALBUM, song.album)
                    song.albumArtist?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.ALBUM_ARTIST, it) }
                    song.genre?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.GENRE, it) }
                    audioFile.commit()
                } catch (e: Exception) {
                    Timber.tag(TAG).w("Warning: Could not write tags: ${e.message}")
                }
            }

            onProgress(99, "Регистрация в фонотеке...")
            MediaScannerConnection.scanFile(
                context,
                arrayOf(outputFile.absolutePath),
                arrayOf(config.targetFormat.mimeType),
                null
            )

            onProgress(100, "Готово!")
            Result.success(outputFile)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Conversion failed")
            Result.failure(e)
        }
    }

    private fun decodeToWav(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        onProgress: (Int, String) -> Unit
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var sampleRate = 44_100
        var channelCount = 2

        try {
            extractor.setDataSource(context, inputUri, null)
            val trackIndex = selectAudioTrack(extractor)
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("Неизвестный MIME-тип аудио")
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) inputFormat.getLong(MediaFormat.KEY_DURATION) else 0L
            if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            inputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)

            FileOutputStream(outputFile).use { output ->
                output.write(ByteArray(WAV_HEADER_SIZE))
                val dec = MediaCodec.createDecoderByType(mime).apply {
                    configure(inputFormat, null, null, 0)
                    start()
                }
                decoder = dec

                val info = MediaCodec.BufferInfo()
                var sawInputEnd = false
                var sawOutputEnd = false
                var pcmBytes = 0L

                while (!sawOutputEnd) {
                    if (!sawInputEnd) {
                        val inputIndex = dec.dequeueInputBuffer(TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val inputBuffer = dec.getInputBuffer(inputIndex) ?: error("Буфер декодера недоступен")
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                dec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEnd = true
                            } else {
                                dec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    when (val outputIndex = dec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outputFormat = dec.outputFormat
                            if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            val outputBuffer = dec.getOutputBuffer(outputIndex)
                            if (outputBuffer != null && info.size > 0) {
                                outputBuffer.position(info.offset)
                                outputBuffer.limit(info.offset + info.size)
                                pcmBytes += writePcm16(outputBuffer, dec.getOutputFormat(outputIndex), output)
                            }

                            val done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            dec.releaseOutputBuffer(outputIndex, false)
                            if (durationUs > 0) {
                                val progress = min(90, max(5, ((info.presentationTimeUs * 85) / durationUs).toInt()))
                                onProgress(progress, "Декодирование в WAV PCM ($progress%)...")
                            }
                            sawOutputEnd = done
                        }
                    }
                }
                output.flush()
            }
            patchWavHeader(outputFile, sampleRate, channelCount)
        } finally {
            extractor.release()
            runCatching { decoder?.stop() }
            decoder?.release()
        }
    }

    private fun transcodeToAac(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        targetBitrate: Int,
        onProgress: (Int, String) -> Unit
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            extractor.setDataSource(context, inputUri, null)
            val trackIndex = selectAudioTrack(extractor)
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("Неизвестный MIME-тип аудио")
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) inputFormat.getLong(MediaFormat.KEY_DURATION) else 0L
            val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44_100
            val channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            inputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)

            val outputFormat = MediaFormat.createAudioFormat(AAC_MIME, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }

            val dec = MediaCodec.createDecoderByType(inputMime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }
            decoder = dec

            val enc = MediaCodec.createEncoderByType(AAC_MIME).apply {
                configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            encoder = enc

            val mux = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux

            val decoderInfo = MediaCodec.BufferInfo()
            val encoderInfo = MediaCodec.BufferInfo()
            var sawExtractorEnd = false
            var sawDecoderEnd = false
            var sawEncoderEnd = false
            var muxerTrack = -1

            fun drainEncoder() {
                var drained = false
                while (!drained) {
                    when (val encoderOutput = enc.dequeueOutputBuffer(encoderInfo, TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) error("Формат энкодера изменился повторно")
                            muxerTrack = mux.addTrack(enc.outputFormat)
                            mux.start()
                            muxerStarted = true
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> drained = true
                        else -> if (encoderOutput >= 0) {
                            val encoded = enc.getOutputBuffer(encoderOutput)
                            if (muxerStarted && encoded != null && encoderInfo.size > 0) {
                                encoded.position(encoderInfo.offset)
                                encoded.limit(encoderInfo.offset + encoderInfo.size)
                                mux.writeSampleData(muxerTrack, encoded, encoderInfo)
                            }

                            if (encoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                sawEncoderEnd = true
                            }
                            enc.releaseOutputBuffer(encoderOutput, false)
                        }
                    }
                }
            }

            while (!sawEncoderEnd) {
                if (!sawExtractorEnd) {
                    val decoderInput = dec.dequeueInputBuffer(TIMEOUT_US)
                    if (decoderInput >= 0) {
                        val buffer = dec.getInputBuffer(decoderInput) ?: error("Буфер декодера недоступен")
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) {
                            dec.queueInputBuffer(decoderInput, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawExtractorEnd = true
                        } else {
                            dec.queueInputBuffer(decoderInput, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var decoderDrained = false
                while (!decoderDrained && !sawDecoderEnd) {
                    when (val decoderOutput = dec.dequeueOutputBuffer(decoderInfo, TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                        MediaCodec.INFO_TRY_AGAIN_LATER -> decoderDrained = true
                        else -> if (decoderOutput >= 0) {
                            val isEos = decoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            if (isEos) sawDecoderEnd = true

                            val decoded = dec.getOutputBuffer(decoderOutput)
                            if (decoded != null && decoderInfo.size > 0) {
                                decoded.position(decoderInfo.offset)
                                decoded.limit(decoderInfo.offset + decoderInfo.size)
                                val pcm = pcm16Buffer(decoded, dec.getOutputFormat(decoderOutput))

                                while (pcm.hasRemaining()) {
                                    var encoderInput = enc.dequeueInputBuffer(TIMEOUT_US * 5)
                                    var retries = 0
                                    while (encoderInput < 0 && retries < 10) {
                                        drainEncoder()
                                        encoderInput = enc.dequeueInputBuffer(TIMEOUT_US * 5)
                                        retries++
                                    }
                                    if (encoderInput < 0) error("Энкодер перегружен")

                                    val encodedInput = enc.getInputBuffer(encoderInput)
                                        ?: error("Буфер энкодера недоступен")
                                    encodedInput.clear()

                                    val chunkSize = min(pcm.remaining(), encodedInput.remaining())
                                    val oldLimit = pcm.limit()
                                    pcm.limit(pcm.position() + chunkSize)
                                    encodedInput.put(pcm)
                                    pcm.limit(oldLimit)

                                    val chunkFlags = if (!pcm.hasRemaining() && isEos) {
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    } else {
                                        0
                                    }

                                    enc.queueInputBuffer(
                                        encoderInput,
                                        0,
                                        chunkSize,
                                        decoderInfo.presentationTimeUs,
                                        chunkFlags
                                    )
                                }
                            } else if (isEos) {
                                var encoderInput = enc.dequeueInputBuffer(TIMEOUT_US * 5)
                                var retries = 0
                                while (encoderInput < 0 && retries < 10) {
                                    drainEncoder()
                                    encoderInput = enc.dequeueInputBuffer(TIMEOUT_US * 5)
                                    retries++
                                }
                                if (encoderInput >= 0) {
                                    enc.queueInputBuffer(
                                        encoderInput,
                                        0,
                                        0,
                                        decoderInfo.presentationTimeUs,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                }
                            }

                            dec.releaseOutputBuffer(decoderOutput, false)
                            if (durationUs > 0) {
                                val progress = min(90, max(5, ((decoderInfo.presentationTimeUs * 85) / durationUs).toInt()))
                                onProgress(progress, "Кодирование M4A AAC ($progress%)...")
                            }
                        }
                    }
                }
                drainEncoder()
            }
        } finally {
            extractor.release()
            runCatching { decoder?.stop() }
            decoder?.release()
            runCatching { encoder?.stop() }
            encoder?.release()
            if (muxerStarted) runCatching { muxer?.stop() }
            muxer?.release()
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return index
        }
        error("Аудиодорожка не найдена в исходном файле")
    }

    private fun writePcm16(buffer: ByteBuffer, format: MediaFormat, output: OutputStream): Int {
        val pcm = pcm16Buffer(buffer, format)
        val chunk = ByteArray(min(pcm.remaining(), 32 * 1024))
        var total = 0
        while (pcm.hasRemaining()) {
            val size = min(pcm.remaining(), chunk.size)
            pcm.get(chunk, 0, size)
            output.write(chunk, 0, size)
            total += size
        }
        return total
    }

    private fun pcm16Buffer(buffer: ByteBuffer, format: MediaFormat): ByteBuffer {
        val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }
        if (encoding == AudioFormat.ENCODING_PCM_16BIT) return buffer.slice()
        if (encoding != AudioFormat.ENCODING_PCM_FLOAT) {
            error("Неподдерживаемый PCM формат: $encoding")
        }

        val floats = buffer.slice().order(ByteOrder.nativeOrder()).asFloatBuffer()
        val pcm = ByteBuffer.allocate(floats.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN)
        while (floats.hasRemaining()) {
            val sample = floats.get().coerceIn(-1f, 1f)
            pcm.putShort((sample * Short.MAX_VALUE).toInt().toShort())
        }
        pcm.flip()
        return pcm
    }

    private fun patchWavHeader(file: File, sampleRate: Int, channelCount: Int) {
        val pcmBytes = max(0L, file.length() - WAV_HEADER_SIZE)
        val bitsPerSample = 16
        val byteRate = sampleRate * channelCount * bitsPerSample / 8
        val blockAlign = channelCount * bitsPerSample / 8

        val buffer = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt((36 + pcmBytes).toInt())
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1.toShort())
        buffer.putShort(channelCount.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmBytes.toInt())

        java.io.RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(buffer.array())
        }
    }
}
