package com.lostf1sh.pixelplayeross.data.network.cover

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Service to search and download high-resolution album covers from online sources (Deezer).
 * Downloaded artwork is cached locally on disk so it remains permanently available offline.
 */
object OnlineCoverArtService {
    private const val TAG = "OnlineCoverArt"
    private val deezerSizeRegex = Regex("/\\d{2,4}x\\d{2,4}([\\-.])")

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // In-flight request deduplication to prevent hammering the network for the same album/query
    private val inFlightQueries = ConcurrentHashMap.newKeySet<String>()

    /**
     * Search Deezer for album or track artwork, returning the highest-resolution image URL.
     */
    fun searchCoverArtUrl(
        artist: String?,
        album: String?,
        title: String?,
        filePath: String?
    ): String? {
        val cleanArtist = artist?.trim()?.takeIf { isValidMetadata(it) }
        val cleanAlbum = album?.trim()?.takeIf { isValidMetadata(it) }
        val cleanTitle = title?.trim()?.takeIf { isValidMetadata(it) }

        // 1. Try Artist + Album search
        if (cleanArtist != null && cleanAlbum != null) {
            val query = "$cleanArtist $cleanAlbum"
            val url = searchDeezerAlbum(query)
            if (!url.isNullOrBlank()) return url
        }

        // 2. Try Artist + Title search
        if (cleanArtist != null && cleanTitle != null) {
            val query = "$cleanArtist $cleanTitle"
            val url = searchDeezerTrack(query)
            if (!url.isNullOrBlank()) return url
        }

        // 3. Try Album name only if distinct
        if (cleanAlbum != null && cleanAlbum.length > 3) {
            val url = searchDeezerAlbum(cleanAlbum)
            if (!url.isNullOrBlank()) return url
        }

        // 4. Try parsing from file name (e.g. "Artist - Title.mp3")
        if (filePath != null) {
            val fileBase = File(filePath).nameWithoutExtension
            if (fileBase.contains(" - ")) {
                val parts = fileBase.split(" - ", limit = 2)
                val inferredArtist = parts[0].trim()
                val inferredTitle = parts[1].trim()
                if (inferredArtist.length > 1 && inferredTitle.length > 1) {
                    val url = searchDeezerTrack("$inferredArtist $inferredTitle")
                    if (!url.isNullOrBlank()) return url
                }
            } else if (cleanArtist == null && fileBase.length > 3) {
                val url = searchDeezerTrack(fileBase)
                if (!url.isNullOrBlank()) return url
            }
        }

        return null
    }

    private fun searchDeezerAlbum(query: String): String? {
        val dedupKey = "album:${query.lowercase()}"
        if (!inFlightQueries.add(dedupKey)) return null
        return try {
            val encodedQuery = Uri.encode(query)
            val requestUrl = "https://api.deezer.com/search/album?q=$encodedQuery&limit=1"
            val request = Request.Builder()
                .url(requestUrl)
                .header("User-Agent", "liquid-player/1.0 (Android)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyString = response.body.string()
                val json = JSONObject(bodyString)
                val data = json.optJSONArray("data") ?: return null
                if (data.length() == 0) return null

                val first = data.getJSONObject(0)
                val rawUrl = first.optString("cover_xl").takeIf { it.isNotBlank() }
                    ?: first.optString("cover_big").takeIf { it.isNotBlank() }
                    ?: first.optString("cover_medium").takeIf { it.isNotBlank() }
                    ?: first.optString("cover").takeIf { it.isNotBlank() }

                rawUrl?.let(::upgradeToHighRes)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).d("Deezer album search failed for '$query': ${e.message}")
            null
        } finally {
            inFlightQueries.remove(dedupKey)
        }
    }

    private fun searchDeezerTrack(query: String): String? {
        val dedupKey = "track:${query.lowercase()}"
        if (!inFlightQueries.add(dedupKey)) return null
        return try {
            val encodedQuery = Uri.encode(query)
            val requestUrl = "https://api.deezer.com/search?q=$encodedQuery&limit=1"
            val request = Request.Builder()
                .url(requestUrl)
                .header("User-Agent", "liquid-player/1.0 (Android)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyString = response.body.string()
                val json = JSONObject(bodyString)
                val data = json.optJSONArray("data") ?: return null
                if (data.length() == 0) return null

                val first = data.getJSONObject(0)
                val albumObj = first.optJSONObject("album") ?: return null
                val rawUrl = albumObj.optString("cover_xl").takeIf { it.isNotBlank() }
                    ?: albumObj.optString("cover_big").takeIf { it.isNotBlank() }
                    ?: albumObj.optString("cover_medium").takeIf { it.isNotBlank() }
                    ?: albumObj.optString("cover").takeIf { it.isNotBlank() }

                rawUrl?.let(::upgradeToHighRes)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).d("Deezer track search failed for '$query': ${e.message}")
            null
        } finally {
            inFlightQueries.remove(dedupKey)
        }
    }

    /**
     * Download the image bytes from the given URL.
     */
    fun downloadImageBytes(imageUrl: String): ByteArray? {
        return try {
            val request = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", "liquid-player/1.0 (Android)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body.bytes()
                if (bytes.size >= 512) bytes else null
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to download image from $imageUrl")
            null
        }
    }

    /**
     * Attempts to save cover.jpg to the audio file's directory if writable and no cover exists.
     */
    fun trySaveCoverToMusicFolder(filePath: String, imageBytes: ByteArray) {
        runCatching {
            val audioFile = File(filePath)
            val parentDir = audioFile.parentFile ?: return
            if (!parentDir.exists() || !parentDir.canWrite()) return

            val targetFile = File(parentDir, "cover.jpg")
            if (!targetFile.exists()) {
                FileOutputStream(targetFile).use { it.write(imageBytes) }
                Timber.tag(TAG).d("Saved cover.jpg to ${parentDir.absolutePath}")
            }
        }
    }

    private fun upgradeToHighRes(url: String): String {
        return deezerSizeRegex.replace(url, "/1000x1000$1")
    }

    private fun isValidMetadata(text: String): Boolean {
        val lower = text.trim().lowercase()
        return lower.isNotBlank() &&
            lower != "unknown" &&
            lower != "<unknown>" &&
            lower != "unknown artist" &&
            lower != "unknown album" &&
            lower != "various artists"
    }
}
