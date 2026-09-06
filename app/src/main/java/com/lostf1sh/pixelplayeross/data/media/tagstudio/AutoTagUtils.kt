package com.lostf1sh.pixelplayeross.data.media.tagstudio

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

data class OnlineTrackCandidate(
    val title: String,
    val artist: String,
    val album: String,
    val year: String?,
    val genre: String?,
    val trackNumber: Int?,
    val coverUrl: String?
)

object AutoTagUtils {
    private const val TAG = "AutoTagUtils"
    private val deezerSizeRegex = Regex("/\\d{2,4}x\\d{2,4}([\\-.])")

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // Regex junk patterns commonly found in downloaded mp3 files
    private val junkPatterns = listOf(
        Regex("""\[.*?vk\.com.*?\]""", RegexOption.IGNORE_CASE),
        Regex("""\(.*?vk\.com.*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\[.*?zaycev.*?\]""", RegexOption.IGNORE_CASE),
        Regex("""\(.*?zaycev.*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\[.*?muzmo.*?\]""", RegexOption.IGNORE_CASE),
        Regex("""\[.*?mp3party.*?\]""", RegexOption.IGNORE_CASE),
        Regex("""\[.*?promodj.*?\]""", RegexOption.IGNORE_CASE),
        Regex("""https?://\S+""", RegexOption.IGNORE_CASE),
        Regex("""www\.[a-zA-Z0-9.\-_]+\.[a-zA-Z]{2,6}""", RegexOption.IGNORE_CASE),
        Regex("""\b(320\s*kbps|256\s*kbps|192\s*kbps|128\s*kbps)\b""", RegexOption.IGNORE_CASE),
        Regex("""\[\s*320\s*\]|\[\s*FLAC\s*\]|\[\s*MP3\s*\]""", RegexOption.IGNORE_CASE),
        Regex("""\(official\s*(video|audio|music\s*video|lyric\s*video|visualizer)\)""", RegexOption.IGNORE_CASE),
        Regex("""\[official\s*(video|audio|music\s*video|lyric\s*video|visualizer)\]""", RegexOption.IGNORE_CASE),
        Regex("""\((hd|hq|4k|1080p|720p)\)""", RegexOption.IGNORE_CASE),
        Regex("""\[(hd|hq|4k|1080p|720p)\]""", RegexOption.IGNORE_CASE),
        Regex("""\b(explicit|clean)\b""", RegexOption.IGNORE_CASE)
    )

    /**
     * Clean messy metadata string by stripping junk tags, URLs, bitrate markers, and normalizing spaces.
     */
    fun cleanMetadataText(text: String): String {
        var cleaned = text
        for (pattern in junkPatterns) {
            cleaned = pattern.replace(cleaned, "")
        }
        // Replace underscores with spaces if separated words
        cleaned = cleaned.replace('_', ' ')
        // Remove empty brackets like () or []
        cleaned = cleaned.replace(Regex("""\(\s*\)|\[\s*\]"""), "")
        // Collapse multiple spaces
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()
        return cleaned
    }

    /**
     * Parse audio file name into (Artist, Title) pair.
     * Handles:
     * - "01. Artist - Title.mp3"
     * - "Artist - Title.flac"
     * - "01 - Artist - Title.mp3"
     * - "Artist_-_Title.mp3"
     */
    fun parseFilenameToMetadata(filePathOrName: String): Pair<String, String>? {
        val fileName = File(filePathOrName).nameWithoutExtension
        var name = cleanMetadataText(fileName)

        // Strip leading track number: e.g. "01. ", "01 - ", "1. ", "01 "
        name = name.replace(Regex("""^\d{1,3}[\s.\-_]+"""), "").trim()

        // Check for delimiter " - " or "-"
        val delimiter = when {
            name.contains(" - ") -> " - "
            name.contains(" — ") -> " — "
            name.contains(" – ") -> " – "
            name.contains("_-_") -> "_-_"
            else -> null
        }

        return if (delimiter != null) {
            val parts = name.split(delimiter, limit = 2)
            val artist = cleanMetadataText(parts[0])
            val title = cleanMetadataText(parts[1])
            if (artist.isNotBlank() && title.isNotBlank()) {
                Pair(capitalizeProperly(artist), capitalizeProperly(title))
            } else null
        } else {
            // No delimiter found: treat as title only
            if (name.isNotBlank()) Pair("", capitalizeProperly(name)) else null
        }
    }

    private fun capitalizeProperly(text: String): String {
        if (text.isBlank()) return text
        // Don't modify if it's already mixed-case or deliberately styled
        if (text.any { it.isLowerCase() } && text.any { it.isUpperCase() }) return text
        // If all lowercase or all uppercase, capitalize words
        return text.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    /**
     * Search Deezer for canonical track metadata and high-res cover art.
     */
    fun searchTrackOnDeezer(query: String): List<OnlineTrackCandidate> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()

        return try {
            val encoded = Uri.encode(cleanQuery)
            val url = "https://api.deezer.com/search?q=$encoded&limit=5"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LiquidPlayer/1.0 (Android)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body.string()
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return emptyList()

                val results = mutableListOf<OnlineTrackCandidate>()
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val title = item.optString("title").takeIf { it.isNotBlank() } ?: continue
                    val artistObj = item.optJSONObject("artist")
                    val artist = artistObj?.optString("name") ?: ""
                    val albumObj = item.optJSONObject("album")
                    val album = albumObj?.optString("title") ?: ""

                    val coverUrl = (
                        albumObj?.optString("cover_xl")?.takeIf { it.isNotBlank() }
                            ?: albumObj?.optString("cover_big")?.takeIf { it.isNotBlank() }
                            ?: albumObj?.optString("cover_medium")?.takeIf { it.isNotBlank() }
                        )?.let { deezerSizeRegex.replace(it, "/1000x1000$1") }

                    results.add(
                        OnlineTrackCandidate(
                            title = title,
                            artist = artist,
                            album = album,
                            year = null,
                            genre = null,
                            trackNumber = null,
                            coverUrl = coverUrl
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            Timber.tag(TAG).d("Deezer search failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Search Deezer for multiple cover art options for a given artist and album/title.
     */
    fun fetchCoverCandidates(artist: String, albumOrTitle: String): List<String> {
        val results = mutableSetOf<String>()

        // 1. Search albums
        if (artist.isNotBlank() && albumOrTitle.isNotBlank()) {
            try {
                val q = Uri.encode("$artist $albumOrTitle")
                val request = Request.Builder()
                    .url("https://api.deezer.com/search/album?q=$q&limit=4")
                    .header("User-Agent", "LiquidPlayer/1.0 (Android)")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body.string())
                        val data = json.optJSONArray("data")
                        if (data != null) {
                            for (i in 0 until data.length()) {
                                val item = data.getJSONObject(i)
                                val cover = item.optString("cover_xl").takeIf { it.isNotBlank() }
                                    ?: item.optString("cover_big").takeIf { it.isNotBlank() }
                                cover?.let { results.add(deezerSizeRegex.replace(it, "/1000x1000$1")) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).d("Cover search album error: ${e.message}")
            }
        }

        // 2. Search tracks
        if (results.size < 4 && (artist.isNotBlank() || albumOrTitle.isNotBlank())) {
            try {
                val q = Uri.encode(if (artist.isNotBlank()) "$artist $albumOrTitle" else albumOrTitle)
                val request = Request.Builder()
                    .url("https://api.deezer.com/search?q=$q&limit=6")
                    .header("User-Agent", "LiquidPlayer/1.0 (Android)")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body.string())
                        val data = json.optJSONArray("data")
                        if (data != null) {
                            for (i in 0 until data.length()) {
                                val item = data.getJSONObject(i)
                                val album = item.optJSONObject("album")
                                val cover = album?.optString("cover_xl")?.takeIf { it.isNotBlank() }
                                    ?: album?.optString("cover_big")?.takeIf { it.isNotBlank() }
                                cover?.let { results.add(deezerSizeRegex.replace(it, "/1000x1000$1")) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).d("Cover search track error: ${e.message}")
            }
        }

        return results.toList()
    }

    /**
     * Downloads image bytes from URL.
     */
    fun downloadImageBytes(url: String): ByteArray? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LiquidPlayer/1.0 (Android)")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body.bytes()
                } else null
            }
        } catch (e: Exception) {
            Timber.tag(TAG).d("Image download failed: ${e.message}")
            null
        }
    }
}
