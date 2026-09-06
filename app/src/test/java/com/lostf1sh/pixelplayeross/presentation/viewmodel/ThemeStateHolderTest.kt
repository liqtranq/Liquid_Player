package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.net.Uri
import android.os.Trace
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.lostf1sh.pixelplayeross.data.preferences.ThemePreferencesRepository
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ThemeStateHolderTest {
    @Test fun `late palette from previous track cannot overwrite the new artwork and palette pair`() = runTest {
        mockkStatic(Trace::class)
        every { Trace.beginAsyncSection(any(), any()) } just Runs
        every { Trace.endAsyncSection(any(), any()) } just Runs
        try {
            val processor = mockk<ColorSchemeProcessor>()
            val preferences = mockk<ThemePreferencesRepository>(relaxed = true)
            every { preferences.playerThemePreferenceFlow } returns flowOf("album_art")
            every { preferences.globalNowPlayingThemeEnabledFlow } returns flowOf(false)
            every { preferences.albumArtPaletteStyleFlow } returns flowOf(com.lostf1sh.pixelplayeross.data.preferences.AlbumArtPaletteStyle.default)
            every { preferences.albumArtColorAccuracyFlow } returns flowOf(com.lostf1sh.pixelplayeross.data.preferences.AlbumArtColorAccuracy.DEFAULT)
            val oldUri = mockk<Uri>()
            every { oldUri.toString() } returns "old"
            val newUri = mockk<Uri>()
            every { newUri.toString() } returns "new"
            val oldPalette = CompletableDeferred<ColorSchemePair>()
            val fresh = ColorSchemePair(lightColorScheme(), darkColorScheme())
            coEvery { processor.getOrGenerateColorScheme("old", any(), any(), any()) } coAnswers { oldPalette.await() }
            coEvery { processor.getOrGenerateColorScheme("new", any(), any(), any()) } returns fresh
            val holder = ThemeStateHolder(processor, preferences)
            holder.initialize(backgroundScope)
            holder.selectCurrentArtwork("old")
            val oldJob = launch { holder.extractAndGenerateColorScheme(oldUri, "old") }
            runCurrent()
            holder.selectCurrentArtwork("new")
            holder.extractAndGenerateColorScheme(newUri, "new")
            runCurrent()
            assertEquals("new", holder.activeArtworkTheme.value.uri)
            assertSame(fresh, holder.activeArtworkTheme.value.scheme)
            oldPalette.complete(ColorSchemePair(lightColorScheme(), darkColorScheme()))
            oldJob.join()
            runCurrent()
            assertEquals("new", holder.activeArtworkTheme.value.uri)
            assertSame(fresh, holder.activeArtworkTheme.value.scheme)
        } finally {
            unmockkStatic(Trace::class)
        }
    }
}
