package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.net.Uri
import android.content.ComponentCallbacks2
import androidx.compose.ui.graphics.Color
import com.lostf1sh.pixelplayeross.data.preferences.AlbumArtColorAccuracy
import com.lostf1sh.pixelplayeross.data.preferences.AlbumArtPaletteStyle
import com.lostf1sh.pixelplayeross.data.preferences.ThemePreferencesRepository
import com.lostf1sh.pixelplayeross.ui.theme.DarkColorScheme
import com.lostf1sh.pixelplayeross.ui.theme.clearExtractedColorCache
import com.lostf1sh.pixelplayeross.utils.traceAsyncSection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeStateHolder @Inject constructor(
    private val colorSchemeProcessor: ColorSchemeProcessor,
    private val themePreferencesRepository: ThemePreferencesRepository
) {

    private var scope: CoroutineScope? = null
    @Volatile
    private var currentPaletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.default
    @Volatile
    private var currentPaletteAccuracy: Int = AlbumArtColorAccuracy.DEFAULT

    data class ArtworkTheme(val uri: String?, val scheme: ColorSchemePair?)
    private val _artworkTheme = MutableStateFlow(ArtworkTheme(null, null))
    private val _activeArtworkTheme = MutableStateFlow(ArtworkTheme(null, null))
    val activeArtworkTheme = _activeArtworkTheme.asStateFlow()
    @Volatile private var requestedArtworkUri: String? = null
    private val requestGeneration = java.util.concurrent.atomic.AtomicLong()

    fun selectCurrentArtwork(uri: String?) {
        requestedArtworkUri = uri
        requestGeneration.incrementAndGet()
    }

    private fun publishTheme(uri: String?, scheme: ColorSchemePair?) {
        _artworkTheme.value = ArtworkTheme(uri, scheme)
        _currentAlbumArtUri.value = uri
        _currentAlbumArtColorSchemePair.value = scheme
    }

    private val _currentAlbumArtColorSchemePair = MutableStateFlow<ColorSchemePair?>(null)
    val currentAlbumArtColorSchemePair: StateFlow<ColorSchemePair?> = _currentAlbumArtColorSchemePair.asStateFlow()
    private val _currentAlbumArtUri = MutableStateFlow<String?>(null)
    val currentAlbumArtUri: StateFlow<String?> = _currentAlbumArtUri.asStateFlow()

    private val _lavaLampColors = MutableStateFlow<ImmutableList<Color>>(persistentListOf())
    val lavaLampColors: StateFlow<ImmutableList<Color>> = _lavaLampColors.asStateFlow()

    private val playerThemePreference = themePreferencesRepository.playerThemePreferenceFlow

    private val _activePlayerColorSchemePair = MutableStateFlow<ColorSchemePair?>(null)
    val activePlayerColorSchemePair: StateFlow<ColorSchemePair?> = _activePlayerColorSchemePair.asStateFlow()

    fun initialize(scope: CoroutineScope) {
        this.scope = scope

        scope.launch {
            combine(
                playerThemePreference,
                themePreferencesRepository.globalNowPlayingThemeEnabledFlow,
                _artworkTheme
            ) { playerPref, useNowPlayingColorsAppWide, albumTheme ->
                if (
                    playerPref == com.lostf1sh.pixelplayeross.data.preferences.ThemePreference.ALBUM_ART ||
                    useNowPlayingColorsAppWide
                ) {
                    albumTheme
                } else {
                    ArtworkTheme(null, null)
                }
            }.collect {
                _activeArtworkTheme.value = it
                _activePlayerColorSchemePair.value = it.scheme
            }
        }

        scope.launch {
            combine(
                themePreferencesRepository.albumArtPaletteStyleFlow,
                themePreferencesRepository.albumArtColorAccuracyFlow
            ) { style, accuracy -> style to accuracy }
                .collect { (style, accuracy) ->
                    val paletteChanged =
                        currentPaletteStyle != style || currentPaletteAccuracy != accuracy
                    currentPaletteStyle = style
                    currentPaletteAccuracy = accuracy

                    if (!paletteChanged) return@collect

                    requestGeneration.incrementAndGet()
                    val uri = requestedArtworkUri ?: return@collect
                    val refreshedScheme = colorSchemeProcessor.getOrGenerateColorScheme(
                        albumArtUri = uri,
                        paletteStyle = style,
                        colorAccuracyLevel = accuracy
                    )
                    if (requestedArtworkUri == uri && currentPaletteStyle == style && currentPaletteAccuracy == accuracy) {
                        publishTheme(uri, refreshedScheme)
                    }
                    individualAlbumColorSchemes[uri]?.value = refreshedScheme
                }
        }

        scope.launch {
            activePlayerColorSchemePair.collect { schemePair ->
                 updateLavaLampColors(schemePair)
            }
        }
    }

    suspend fun extractAndGenerateColorScheme(
        albumArtUriAsUri: Uri?,
        currentSongUriString: String?,
        isPreload: Boolean = false
    ): Unit = traceAsyncSection("ThemeStateHolder.extractAndGenerateColorScheme") {
        val generation = requestGeneration.get()
        try {
            if (albumArtUriAsUri == null) {
                if (!isPreload && currentSongUriString == null) {
                    if (requestedArtworkUri == null) publishTheme(null, null)
                }
                return@traceAsyncSection
            }

            val uriString = albumArtUriAsUri.toString()
            val schemePair = colorSchemeProcessor.getOrGenerateColorScheme(
                albumArtUri = uriString,
                paletteStyle = currentPaletteStyle,
                colorAccuracyLevel = currentPaletteAccuracy
            )

            if (!isPreload && currentSongUriString == uriString && requestedArtworkUri == uriString && requestGeneration.get() == generation) {
                publishTheme(uriString, schemePair)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("ThemeStateHolder").w(e, "Could not load artwork palette")
            // Keep the displayed palette on a transient loading failure.
        }
    }

    private fun updateLavaLampColors(schemePair: ColorSchemePair?) {
        val schemeForLava = schemePair?.dark ?: DarkColorScheme
        _lavaLampColors.update {
            listOf(schemeForLava.primary, schemeForLava.secondary, schemeForLava.tertiary).distinct().toImmutableList()
        }
    }

    private val individualAlbumColorSchemes = object : LinkedHashMap<String, MutableStateFlow<ColorSchemePair?>>(
        32, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableStateFlow<ColorSchemePair?>>?): Boolean {
            return size > 96
        }
    }

    private val emptyAlbumColorScheme = MutableStateFlow<ColorSchemePair?>(null).asStateFlow()
    private val pendingAlbumColorSchemeLock = Any()
    private val pendingAlbumColorSchemeTargets = mutableMapOf<String, MutableSet<MutableStateFlow<ColorSchemePair?>>>()

    private fun requestAlbumColorSchemeGeneration(
        uriString: String,
        targetFlow: MutableStateFlow<ColorSchemePair?>
    ) {
        if (uriString.isBlank()) return

        val shouldStartRequest = synchronized(pendingAlbumColorSchemeLock) {
            val existingTargets = pendingAlbumColorSchemeTargets[uriString]
            if (existingTargets != null) {
                existingTargets.add(targetFlow)
                false
            } else {
                pendingAlbumColorSchemeTargets[uriString] = mutableSetOf(targetFlow)
                true
            }
        }

        if (!shouldStartRequest) return

        val requestScope = scope
        if (requestScope == null) {
            synchronized(pendingAlbumColorSchemeLock) {
                pendingAlbumColorSchemeTargets.remove(uriString)
            }
            return
        }

        requestScope.launch(Dispatchers.IO) {
            var scheme: ColorSchemePair? = null
            try {
                scheme = colorSchemeProcessor.getOrGenerateColorScheme(
                    albumArtUri = uriString,
                    paletteStyle = currentPaletteStyle,
                    colorAccuracyLevel = currentPaletteAccuracy
                )
            } catch (_: Exception) {
            } finally {
                val targets = synchronized(pendingAlbumColorSchemeLock) {
                    pendingAlbumColorSchemeTargets.remove(uriString)?.toList().orEmpty()
                }
                targets.forEach { it.value = scheme }
            }
        }
    }

    fun getAlbumColorSchemeFlow(
        uriString: String,
        eager: Boolean = true
    ): StateFlow<ColorSchemePair?> {
        if (uriString.isBlank()) return emptyAlbumColorScheme

        val existingFlow = individualAlbumColorSchemes[uriString]
        if (existingFlow != null) {
            if (eager && existingFlow.value == null) {
                requestAlbumColorSchemeGeneration(uriString, existingFlow)
            }
            return existingFlow.asStateFlow()
        }

        val newFlow = MutableStateFlow<ColorSchemePair?>(null)
        individualAlbumColorSchemes[uriString] = newFlow

        if (eager) {
            requestAlbumColorSchemeGeneration(uriString, newFlow)
        }

        return newFlow.asStateFlow()
    }

    fun ensureAlbumColorScheme(uriString: String) {
        if (uriString.isBlank()) return

        val targetFlow = individualAlbumColorSchemes[uriString]
            ?: MutableStateFlow<ColorSchemePair?>(null).also { individualAlbumColorSchemes[uriString] = it }

        if (targetFlow.value != null) return
        requestAlbumColorSchemeGeneration(uriString, targetFlow)
    }
    
    suspend fun getOrGenerateColorScheme(uriString: String): ColorSchemePair? {
         return colorSchemeProcessor.getOrGenerateColorScheme(
             albumArtUri = uriString,
             paletteStyle = currentPaletteStyle,
             colorAccuracyLevel = currentPaletteAccuracy
         )
    }

    suspend fun forceRegenerateColorScheme(
        uriString: String?,
        regenerateAllStyles: Boolean = false
    ) {
         if (uriString == null) {
             if (requestedArtworkUri == null) publishTheme(null, null)
             return
         }

         Timber.tag("ThemeStateHolder").d("forceRegenerateColorScheme called for: $uriString")
         Timber.tag("ThemeStateHolder").d("Current tracked global URI: ${_currentAlbumArtUri.value}")
         
         colorSchemeProcessor.invalidateScheme(uriString)

         val newScheme = if (regenerateAllStyles) {
             var selectedStyleScheme: ColorSchemePair? = null
             AlbumArtPaletteStyle.entries.forEach { style ->
                 val generated = colorSchemeProcessor.getOrGenerateColorScheme(
                     albumArtUri = uriString,
                     paletteStyle = style,
                     colorAccuracyLevel = currentPaletteAccuracy,
                     forceRefresh = true
                 )
                 if (style == currentPaletteStyle) {
                     selectedStyleScheme = generated
                 }
             }
             selectedStyleScheme
         } else {
             colorSchemeProcessor.getOrGenerateColorScheme(
                 albumArtUri = uriString,
                 paletteStyle = currentPaletteStyle,
                 colorAccuracyLevel = currentPaletteAccuracy,
                 forceRefresh = true
             )
         }

         val activeFlow = individualAlbumColorSchemes[uriString]
         if (activeFlow != null) {
             activeFlow.value = newScheme
         }
         
         if (requestedArtworkUri == uriString) {
             Timber.tag("ThemeStateHolder").d("Updating global color scheme flow directly.")
             publishTheme(uriString, newScheme)
         } else {
             Timber.tag("ThemeStateHolder").d("Global URI did not match. Skipping global update.")
         }
    }

    @Suppress("DEPRECATION")
    fun trimMemory(level: Int) {
        colorSchemeProcessor.clearMemoryCache()
        clearExtractedColorCache()

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            individualAlbumColorSchemes.clear()
        }

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            synchronized(pendingAlbumColorSchemeLock) {
                pendingAlbumColorSchemeTargets.clear()
            }
        }
    }

    fun onCleared() {
        scope = null
    }

}
