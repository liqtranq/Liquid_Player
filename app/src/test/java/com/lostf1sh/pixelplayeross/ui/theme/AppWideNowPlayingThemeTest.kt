package com.lostf1sh.pixelplayeross.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.presentation.viewmodel.ColorSchemePair
import org.junit.jupiter.api.Test

class AppWideNowPlayingThemeTest {

    private val currentScheme = ColorSchemePair(lightColorScheme(), darkColorScheme())
    private val lastScheme = ColorSchemePair(lightColorScheme(), darkColorScheme())

    @Test
    fun `feature is opt-in`() {
        val resolved = resolveAppWideNowPlayingColorSchemePair(
            enabled = false,
            currentSongId = "song-1",
            isPlaying = true,
            currentSongScheme = currentScheme,
            lastValidSongId = "song-1",
            lastValidScheme = lastScheme
        )

        assertThat(resolved).isNull()
    }

    @Test
    fun `ready current song palette is used`() {
        val resolved = resolveAppWideNowPlayingColorSchemePair(
            enabled = true,
            currentSongId = "song-1",
            isPlaying = true,
            currentSongScheme = currentScheme,
            lastValidSongId = null,
            lastValidScheme = null
        )

        assertThat(resolved).isSameInstanceAs(currentScheme)
    }

    @Test
    fun `paused song keeps its last valid palette`() {
        val resolved = resolveAppWideNowPlayingColorSchemePair(
            enabled = true,
            currentSongId = "song-1",
            isPlaying = false,
            currentSongScheme = null,
            lastValidSongId = "song-1",
            lastValidScheme = lastScheme
        )

        assertThat(resolved).isSameInstanceAs(lastScheme)
    }

    @Test
    fun `last displayed palette is retained until next song palette arrives`() {
        val resolved = resolveAppWideNowPlayingColorSchemePair(
            enabled = true,
            currentSongId = "song-2",
            isPlaying = false,
            currentSongScheme = null,
            lastValidSongId = "song-1",
            lastValidScheme = lastScheme
        )

        assertThat(resolved).isSameInstanceAs(lastScheme)
    }
    @Test
    fun `playing keeps its displayed palette while artwork is loading`() {
        val resolved = resolveAppWideNowPlayingColorSchemePair(
            enabled = true, currentSongId = "song-2", isPlaying = true,
            currentSongScheme = null, lastValidSongId = "song-1", lastValidScheme = lastScheme
        )
        assertThat(resolved).isSameInstanceAs(lastScheme)
    }

    @Test
    fun `tracks without artwork use the base theme`() {
        val resolved = resolveAppWideNowPlayingColorSchemePair(
            enabled = true, currentSongId = "song-2", isPlaying = true,
            currentSongScheme = null, lastValidSongId = "song-1", lastValidScheme = lastScheme,
            hasArtwork = false
        )
        assertThat(resolved).isNull()
    }
}
