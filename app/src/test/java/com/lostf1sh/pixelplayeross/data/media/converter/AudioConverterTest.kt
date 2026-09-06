package com.lostf1sh.pixelplayeross.data.media.converter

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AudioConverterTest {

    @Test
    fun targetFormat_hasCorrectExtensionsAndMimeTypes() {
        assertThat(TargetFormat.AAC_M4A.extension).isEqualTo("m4a")
        assertThat(TargetFormat.AAC_M4A.mimeType).isEqualTo("audio/mp4")

        assertThat(TargetFormat.WAV_PCM.extension).isEqualTo("wav")
        assertThat(TargetFormat.WAV_PCM.mimeType).isEqualTo("audio/wav")
    }

    @Test
    fun conversionConfig_defaultsAreExpected() {
        val config = ConversionConfig()
        assertThat(config.targetFormat).isEqualTo(TargetFormat.AAC_M4A)
        assertThat(config.bitrate).isEqualTo(192_000)
        assertThat(config.preserveMetadata).isTrue()
    }

    @Test
    fun conversionConfig_customizationWorks() {
        val config = ConversionConfig(
            targetFormat = TargetFormat.WAV_PCM,
            bitrate = 320_000,
            preserveMetadata = false
        )
        assertThat(config.targetFormat).isEqualTo(TargetFormat.WAV_PCM)
        assertThat(config.bitrate).isEqualTo(320_000)
        assertThat(config.preserveMetadata).isFalse()
    }
}
