package com.lostf1sh.pixelplayeross.ui.theme

import com.lostf1sh.pixelplayeross.presentation.viewmodel.ColorSchemePair

/**
 * Selects the optional application-wide now-playing palette.
 *
 * Keep the displayed palette while the next artwork loads, then apply the new palette once.
 */
internal fun resolveAppWideNowPlayingColorSchemePair(
    enabled: Boolean,
    currentSongId: String?,
    isPlaying: Boolean,
    currentSongScheme: ColorSchemePair?,
    lastValidSongId: String?,
    lastValidScheme: ColorSchemePair?,
    hasArtwork: Boolean = true
): ColorSchemePair? {
    if (!enabled || currentSongId == null || !hasArtwork) return null
    if (currentSongScheme != null) return currentSongScheme
    return lastValidScheme
}
