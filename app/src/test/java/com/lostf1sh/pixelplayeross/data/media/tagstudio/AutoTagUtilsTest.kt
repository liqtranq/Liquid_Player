package com.lostf1sh.pixelplayeross.data.media.tagstudio

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AutoTagUtilsTest {

    @Test
    fun cleanMetadataText_removesJunkWebsitesAndBitrateTags() {
        val raw = "[vk.com/music] Daft Punk - One More Time [320 kbps] (Official Audio)"
        val cleaned = AutoTagUtils.cleanMetadataText(raw)
        assertThat(cleaned).isEqualTo("Daft Punk - One More Time")
    }

    @Test
    fun cleanMetadataText_removesZaycevAndUnderscores() {
        val raw = "Eminem_-_Without_Me_(zaycev.net)"
        val cleaned = AutoTagUtils.cleanMetadataText(raw)
        assertThat(cleaned).isEqualTo("Eminem - Without Me")
    }

    @Test
    fun parseFilenameToMetadata_parsesTrackNumberAndArtistTitle() {
        val filename = "01. The Weeknd - Blinding Lights.mp3"
        val parsed = AutoTagUtils.parseFilenameToMetadata(filename)
        assertThat(parsed).isNotNull()
        assertThat(parsed?.first).isEqualTo("The Weeknd")
        assertThat(parsed?.second).isEqualTo("Blinding Lights")
    }

    @Test
    fun parseFilenameToMetadata_handlesHyphenWithUnderscores() {
        val filename = "Linkin_Park_-_In_The_End.flac"
        val parsed = AutoTagUtils.parseFilenameToMetadata(filename)
        assertThat(parsed).isNotNull()
        assertThat(parsed?.first).isEqualTo("Linkin Park")
        assertThat(parsed?.second).isEqualTo("In The End")
    }

    @Test
    fun parseFilenameToMetadata_handlesNumberedPrefixWithoutArtist() {
        val filename = "04 Bohemian Rhapsody.mp3"
        val parsed = AutoTagUtils.parseFilenameToMetadata(filename)
        assertThat(parsed).isNotNull()
        assertThat(parsed?.first).isEmpty()
        assertThat(parsed?.second).isEqualTo("Bohemian Rhapsody")
    }
}
