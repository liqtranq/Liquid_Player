package com.lostf1sh.pixelplayeross.utils

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.lostf1sh.pixelplayeross.data.media.AudioMetadataReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import android.os.Looper
import com.lostf1sh.pixelplayeross.data.network.cover.OnlineCoverArtService
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

object AlbumArtUtils {
    private const val CACHE_VERSION_SUFFIX = "_v5"

    // Cached covers are display-only; the source audio file keeps its original art untouched.
    // Embedded covers can be multi-megabyte full-resolution scans, and re-decoding such a blob
    // on every cold artwork load is the dominant avoidable artwork cost. Bounding the cached
    // copy to 1536 px JPEG is invisible on phone-class displays (the full player tops out at
    // 2048 px) while cutting heavy covers to a few hundred KB — far cheaper to decode and
    // lighter on RAM/IPC.
    private const val MAX_CACHED_ART_DIMENSION_PX = 1536
    private const val CACHED_ART_JPEG_QUALITY = 90
    // Cache entries larger than this are treated as legacy/oversized and shrunk in the
    // background on first access (one-time migration for art cached before bounding existed).
    private const val OVERSIZED_CACHED_ART_BYTES = 900L * 1024

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Tracks cache files currently being shrunk so rapid repeated loads of the same oversized
    // cover don't read the large blob into memory more than once concurrently.
    private val artworkShrinkInFlight = ConcurrentHashMap.newKeySet<String>()
    private val commonArtworkFileNames = listOf(
        "cover.jpg", "cover.png", "cover.jpeg", "cover.webp",
        "folder.jpg", "folder.png", "folder.jpeg", "folder.webp",
        "album.jpg", "album.png", "album.jpeg", "album.webp",
        "albumart.jpg", "albumart.png", "albumart.jpeg", "albumart.webp",
        "artwork.jpg", "artwork.png", "artwork.jpeg", "artwork.webp",
        "front.jpg", "front.png", "front.jpeg", "front.webp",
        ".folder.jpg", ".albumart.jpg",
        "thumb.jpg", "thumbnail.jpg",
        "scan.jpg", "scanned.jpg"
    )
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
    private val genericMixedDirectoryNames = setOf(
        "download",
        "downloads",
        "music",
        "songs",
        "audio",
        "studio",
        "gallery",
        "pictures",
        "photos",
        "images",
        "dcim",
        "camera",
        "screenshots"
    )

    /**
     * Main function to get album art URI for local songs.
     * Supports embedded tags, local folder art (cover.jpg/folder.jpg), and online cover resolution.
     */
    fun getAlbumArtUri(
        appContext: Context,
        path: String,
        songId: Long,
        forceRefresh: Boolean
    ): String? {
        return if (hasLocalAlbumArt(appContext, path, songId, forceRefresh)) {
            LocalArtworkUri.buildSongUri(songId)
        } else {
            null
        }
    }

    /**
     * Lightweight scan-time artwork resolution.
     *
     * Full embedded-art extraction can allocate large ByteArrays per song. During a library scan
     * we only store the stable lazy URI; the normal image-loading path extracts/caches artwork
     * later for visible rows.
     */
    fun getAlbumArtUriForLibraryScan(
        appContext: Context,
        songId: Long,
        forceRefresh: Boolean
    ): String? {
        val cachedFile = getCachedAlbumArtFile(appContext, songId)
        val noArtFile = noArtMarkerFile(appContext, songId)

        if (forceRefresh) {
            cachedFile.delete()
            noArtFile.delete()
        }

        val hasCachedArtwork = cachedFile.exists() && cachedFile.length() > 0
        if (hasCachedArtwork) {
            cachedFile.setLastModified(System.currentTimeMillis())
        }

        return resolveAlbumArtUriForLibraryScan(
            songId = songId,
            hasCachedArtwork = hasCachedArtwork,
            hasNoArtworkMarker = noArtFile.exists(),
            forceRefresh = forceRefresh
        )
    }

    fun getCachedAlbumArtUri(
        appContext: Context,
        songId: Long
    ): Uri? {
        val cachedFile = getCachedAlbumArtFile(appContext, songId)
        if (!cachedFile.exists()) return null

        cachedFile.setLastModified(System.currentTimeMillis())
        return shareableCacheUri(appContext, cachedFile)
    }

    fun hasCachedAlbumArt(
        appContext: Context,
        songId: Long
    ): Boolean {
        return getCachedAlbumArtFile(appContext, songId).exists()
    }

    /**
     * Enhanced album art detection without eagerly persisting the whole library to cache.
     */
    fun getEmbeddedAlbumArtUri(
        appContext: Context,
        filePath: String,
        songId: Long,
        deepScan: Boolean
    ): Uri? {
        ensureAlbumArtCachedFile(appContext, songId, filePath, deepScan)?.let { cachedFile ->
            return shareableCacheUri(appContext, cachedFile)
        }
        return null
    }

    fun ensureAlbumArtCachedFile(
        appContext: Context,
        songId: Long,
        filePath: String? = null,
        forceRefresh: Boolean = false
    ): File? {
        val cachedFile = getCachedAlbumArtFile(appContext, songId)
        val noArtFile = noArtMarkerFile(appContext, songId)

        if (!forceRefresh) {
            if (cachedFile.exists() && cachedFile.length() > 0) {
                cachedFile.setLastModified(System.currentTimeMillis())
                scheduleOversizedArtworkShrink(cachedFile)
                return cachedFile
            }
            // Check if legacy _v4 cached file exists and migrate it
            val legacyV4 = legacyCachedAlbumArtFile(appContext, songId, "_v4")
            if (legacyV4.exists() && legacyV4.length() > 0) {
                runCatching {
                    legacyV4.copyTo(cachedFile, overwrite = true)
                    legacyV4.delete()
                    cachedFile.setLastModified(System.currentTimeMillis())
                    return cachedFile
                }
            }
            if (noArtFile.exists()) {
                return null
            }
        } else {
            cachedFile.delete()
            noArtFile.delete()
        }

        val resolvedInfo = resolveSongMediaStoreInfo(appContext, songId)
        val resolvedPath = filePath ?: resolvedInfo?.path
        val audioFile = resolvedPath?.let(::File)

        // 1. Embedded artwork in audio file (ID3, Vorbis, FLAC, MP4)
        if (resolvedPath != null && audioFile?.exists() == true) {
            extractEmbeddedAlbumArtBytes(resolvedPath)?.let { bytes ->
                cacheAlbumArtBytes(appContext, bytes, songId)
                return cachedFile.takeIf { it.exists() && it.length() > 0 }
            }
        }

        // 2. Folder artwork (cover.jpg, folder.jpg, etc.) in the music directory
        if (resolvedPath != null && audioFile?.exists() == true) {
            findExternalAlbumArtFile(resolvedPath)?.let { folderArtFile ->
                val bytes = runCatching { folderArtFile.readBytes() }.getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    cacheAlbumArtBytes(appContext, bytes, songId)
                    return cachedFile.takeIf { it.exists() && it.length() > 0 }
                }
            }
        }

        // 3. MediaStore album art fallback
        resolvedInfo?.albumId?.let { albumId ->
            getMediaStoreAlbumArtBytes(appContext, albumId)?.let { bytes ->
                cacheAlbumArtBytes(appContext, bytes, songId)
                return cachedFile.takeIf { it.exists() && it.length() > 0 }
            }
        }

        // 4. Same-album cached artwork (if already resolved for this album)
        resolvedInfo?.albumId?.let { albumId ->
            val albumCache = getCachedAlbumFile(appContext, albumId)
            if (albumCache.exists() && albumCache.length() > 0) {
                runCatching {
                    albumCache.copyTo(cachedFile, overwrite = true)
                    noArtFile.delete()
                    return cachedFile.takeIf { it.exists() && it.length() > 0 }
                }
            }
        }

        // 5. Online artwork from Deezer API & save locally so it stays permanently
        if (Looper.myLooper() != Looper.getMainLooper()) {
            val title = resolvedInfo?.title
            val artist = resolvedInfo?.artist
            val album = resolvedInfo?.album

            val onlineUrl = OnlineCoverArtService.searchCoverArtUrl(
                artist = artist,
                album = album,
                title = title,
                filePath = resolvedPath
            )

            if (!onlineUrl.isNullOrBlank()) {
                val downloadedBytes = OnlineCoverArtService.downloadImageBytes(onlineUrl)
                if (downloadedBytes != null && downloadedBytes.isNotEmpty()) {
                    cacheAlbumArtBytes(appContext, downloadedBytes, songId)

                    // Also save for albumId to reuse for other tracks in the same album
                    resolvedInfo?.albumId?.let { albumId ->
                        runCatching {
                            val albumCache = getCachedAlbumFile(appContext, albumId)
                            cachedFile.copyTo(albumCache, overwrite = true)
                        }
                    }

                    // Try saving cover.jpg to the audio file directory if accessible
                    if (resolvedPath != null) {
                        OnlineCoverArtService.trySaveCoverToMusicFolder(resolvedPath, downloadedBytes)
                    }

                    return cachedFile.takeIf { it.exists() && it.length() > 0 }
                }
            }
        }

        cachedFile.delete()
        noArtFile.createNewFile()
        return null
    }

    fun openArtworkInputStream(
        appContext: Context,
        uri: Uri
    ): InputStream? {
        val uriString = uri.toString()
        return when {
            LocalArtworkUri.isLocalArtworkUri(uriString) -> {
                val songId = LocalArtworkUri.parseSongId(uriString) ?: return null
                val resolvedPath = resolveSongMediaStoreInfo(appContext, songId)?.path
                ensureAlbumArtCachedFile(
                    appContext = appContext,
                    songId = songId,
                    filePath = resolvedPath
                )?.inputStream()
            }
            uri.scheme.isNullOrBlank() && uri.toString().startsWith("/") -> File(uri.toString()).inputStream()
            else -> appContext.contentResolver.openInputStream(uri)
        }
    }

    private fun hasLocalAlbumArt(
        appContext: Context,
        filePath: String,
        songId: Long,
        deepScan: Boolean
    ): Boolean {
        val audioFile = File(filePath)
        if (!audioFile.exists() || !audioFile.canRead()) {
            return false
        }

        val cachedFile = getCachedAlbumArtFile(appContext, songId)
        val noArtFile = noArtMarkerFile(appContext, songId)

        if (!deepScan) {
            if (noArtFile.exists()) {
                if (cachedFile.exists()) {
                    cachedFile.delete()
                }
                return false
            }

            if (cachedFile.exists() && cachedFile.length() > 0) {
                return true
            }
        } else {
            noArtFile.delete()
        }

        val hasEmbeddedArt = extractEmbeddedAlbumArtBytes(filePath)?.isNotEmpty() == true
        if (hasEmbeddedArt) {
            noArtFile.delete()
            return true
        }

        val hasFolderArt = findExternalAlbumArtFile(filePath) != null
        if (hasFolderArt) {
            noArtFile.delete()
            return true
        }

        // Return true so LocalArtworkUri is assigned to the song, enabling Coil to fetch online art and cache locally!
        return true
    }

    /**
     * Look for external album art files in the same directory.
     */
    fun getExternalAlbumArtUri(filePath: String): Uri? {
        return runCatching {
            findExternalAlbumArtFile(filePath)?.let(Uri::fromFile)
        }.getOrNull()
    }

    internal fun findExternalAlbumArtFile(filePath: String): File? {
        val audioFile = File(filePath)
        val directory = audioFile.parentFile ?: return null
        if (!directory.exists() || !directory.isDirectory || !directory.canRead()) return null
        if (!shouldTrustDirectoryArtwork(directory.name)) return null

        val files = runCatching { directory.listFiles() }.getOrNull() ?: return null
        if (files.isEmpty()) return null

        val imageFiles = files.filter { f ->
            f.isFile && f.canRead() && f.length() >= 512 &&
                f.extension.lowercase() in imageExtensions
        }
        if (imageFiles.isEmpty()) return null

        // 1. Direct match against known artwork file names (case-insensitive)
        for (name in commonArtworkFileNames) {
            val match = imageFiles.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (match != null) return match
        }

        // 2. Base name matches (e.g. "cover", "folder", "front", "album", "artwork")
        val priorityBaseNames = setOf("cover", "folder", "front", "album", "albumart", "artwork", "thumb", ".folder", ".albumart")
        val baseMatch = imageFiles.firstOrNull { f ->
            f.nameWithoutExtension.lowercase() in priorityBaseNames
        }
        if (baseMatch != null) return baseMatch

        // 3. Explicit cover prefix/suffix (e.g. "front_cover", "cover_front", "album_art")
        val patternMatch = imageFiles.firstOrNull { f ->
            val n = f.nameWithoutExtension.lowercase()
            n.startsWith("cover_") || n.startsWith("cover-") ||
                n.endsWith("_cover") || n.endsWith("-cover") ||
                n.startsWith("front_") || n.endsWith("_front") ||
                n == "album_art" || n == "album-art"
        }
        if (patternMatch != null) return patternMatch

        // 4. Matches the audio file name itself (e.g. song "Feels.mp3" with "Feels.jpg")
        val audioBase = audioFile.nameWithoutExtension.lowercase()
        return imageFiles.firstOrNull { it.nameWithoutExtension.equals(audioBase, ignoreCase = true) }
    }

    internal fun shouldTrustDirectoryArtwork(directoryName: String): Boolean {
        val normalized = directoryName.trim().lowercase()
        if (normalized.isBlank()) return false
        return normalized !in genericMixedDirectoryNames
    }

    /**
     * MediaStore's album-art cache can alias unrelated local songs when album metadata is weak or
     * collapsed into "Unknown Album". Keep this helper available for controlled callers, but do
     * not use it as an automatic per-song fallback.
     */
    fun getMediaStoreAlbumArtUri(appContext: Context, albumId: Long): Uri? {
        if (albumId <= 0) return null

        val potentialUri = ContentUris.withAppendedId(
            "content://media/external/audio/albumart".toUri(),
            albumId
        )

        return try {
            appContext.contentResolver.openFileDescriptor(potentialUri, "r")?.use {
                potentialUri
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save embedded art to cache with unique naming
     */
    fun saveAlbumArtToCache(appContext: Context, bytes: ByteArray, songId: Long): Uri {
        val file = cacheAlbumArtBytes(appContext, bytes, songId)
        return shareableCacheUri(appContext, file)
    }

    /**
     * Delete both the cached artwork and the "no art" marker for a specific song.
     */
    fun clearCacheForSong(appContext: Context, songId: Long) {
        listOf(
            getCachedAlbumArtFile(appContext, songId),
            noArtMarkerFile(appContext, songId),
            legacyCachedAlbumArtFile(appContext, songId, "_v4"),
            legacyNoArtMarkerFile(appContext, songId, "_v4"),
            legacyCachedAlbumArtFile(appContext, songId, "_v3"),
            legacyNoArtMarkerFile(appContext, songId, "_v3"),
            legacyCachedAlbumArtFile(appContext, songId, "_v2"),
            legacyNoArtMarkerFile(appContext, songId, "_v2"),
            legacyCachedAlbumArtFile(appContext, songId),
            legacyNoArtMarkerFile(appContext, songId)
        ).forEach { it.delete() }
    }

    fun getCachedAlbumFile(appContext: Context, albumId: Long): File {
        return File(getAlbumArtDir(appContext), "album_art_${albumId}${CACHE_VERSION_SUFFIX}.jpg")
    }

    private fun getMediaStoreAlbumArtBytes(appContext: Context, albumId: Long): ByteArray? {
        if (albumId <= 0) return null
        val potentialUri = ContentUris.withAppendedId(
            "content://media/external/audio/albumart".toUri(),
            albumId
        )
        return runCatching {
            appContext.contentResolver.openInputStream(potentialUri)?.use { it.readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private const val ALBUM_ART_DIR_NAME = "album_art"

    fun getAlbumArtDir(appContext: Context): File {
        val dir = File(appContext.filesDir, ALBUM_ART_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getCachedAlbumArtFile(appContext: Context, songId: Long): File {
        return File(getAlbumArtDir(appContext), "song_art_${songId}${CACHE_VERSION_SUFFIX}.jpg")
    }

    /**
     * Moves any legacy album-art files from cacheDir (old location, wipeable by the OS)
     * into filesDir/album_art/. Idempotent — safe to call on every startup. Runs quickly
     * because it only lists files matching the `song_art_` prefix.
     */
    fun migrateLegacyCacheLocation(appContext: Context) {
        val oldDir = appContext.cacheDir
        val newDir = getAlbumArtDir(appContext)
        val legacyFiles = oldDir.listFiles { f ->
            f.isFile && f.name.startsWith("song_art_")
        } ?: return

        for (file in legacyFiles) {
            val target = File(newDir, file.name)
            if (target.exists()) {
                file.delete()
                continue
            }
            if (!file.renameTo(target)) {
                runCatching {
                    file.copyTo(target, overwrite = false)
                    file.delete()
                }
            }
        }
    }

    private fun cacheAlbumArtBytes(appContext: Context, bytes: ByteArray, songId: Long): File {
        val file = getCachedAlbumArtFile(appContext, songId)

        val boundedBytes = boundArtworkForCache(bytes)
        file.outputStream().use { outputStream ->
            outputStream.write(boundedBytes)
        }
        noArtMarkerFile(appContext, songId).delete()

        appScope.launch {
            AlbumArtCacheManager.cleanCacheIfNeeded(appContext, AlbumArtCacheManager.configuredCacheLimitMb)
        }

        return file
    }

    /**
     * Schedules a one-time, background re-compression of an oversized cached cover. The current
     * load still returns the existing file; subsequent loads get the bounded one. De-duplicated
     * per file so rapid repeated loads don't read the same large blob into memory twice.
     */
    private fun scheduleOversizedArtworkShrink(file: File) {
        if (file.length() <= OVERSIZED_CACHED_ART_BYTES) return
        val key = file.absolutePath
        if (!artworkShrinkInFlight.add(key)) return
        appScope.launch {
            try {
                if (file.length() <= OVERSIZED_CACHED_ART_BYTES) return@launch
                val raw = runCatching { file.readBytes() }.getOrNull() ?: return@launch
                val bounded = boundArtworkForCache(raw)
                if (bounded.size >= raw.size) return@launch
                val tmp = File(file.parentFile, "${file.name}.shrink.tmp")
                runCatching {
                    tmp.outputStream().use { it.write(bounded) }
                    if (!tmp.renameTo(file)) {
                        file.outputStream().use { it.write(bounded) }
                        tmp.delete()
                    }
                }
            } finally {
                artworkShrinkInFlight.remove(key)
            }
        }
    }

    /**
     * Returns artwork bytes bounded for the display cache: longest edge at most
     * [MAX_CACHED_ART_DIMENSION_PX], re-encoded as JPEG when the source is oversized by
     * dimension or by on-disk size. Returns the original bytes unchanged when they are already
     * small enough or when decoding fails, so artwork is never dropped or needlessly re-encoded.
     */
    private fun boundArtworkForCache(bytes: ByteArray): ByteArray {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val srcWidth = bounds.outWidth
            val srcHeight = bounds.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return bytes

            val oversizedByDimension = maxOf(srcWidth, srcHeight) > MAX_CACHED_ART_DIMENSION_PX
            val oversizedBySize = bytes.size > OVERSIZED_CACHED_ART_BYTES
            if (!oversizedByDimension && !oversizedBySize) return bytes

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateArtworkInSampleSize(srcWidth, srcHeight, MAX_CACHED_ART_DIMENSION_PX)
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return bytes
            val scaled = scaleArtworkDownTo(decoded, MAX_CACHED_ART_DIMENSION_PX)
            val encoded = ByteArrayOutputStream().use { stream ->
                scaled.compress(Bitmap.CompressFormat.JPEG, CACHED_ART_JPEG_QUALITY, stream)
                stream.toByteArray()
            }
            if (scaled !== decoded) decoded.recycle()
            scaled.recycle()
            if (encoded.isNotEmpty() && encoded.size < bytes.size) encoded else bytes
        } catch (e: Throwable) {
            Timber.tag("AlbumArtUtils").w(e, "Failed to bound artwork for cache; keeping original bytes")
            bytes
        }
    }

    /** Largest power-of-two subsample that keeps the decoded longest edge >= [maxDimensionPx]. */
    private fun calculateArtworkInSampleSize(srcWidth: Int, srcHeight: Int, maxDimensionPx: Int): Int {
        var inSampleSize = 1
        val longestEdge = maxOf(srcWidth, srcHeight)
        while (longestEdge / (inSampleSize * 2) >= maxDimensionPx) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    /** Scales [src] so its longest edge is [maxDimensionPx]; returns [src] unchanged if already smaller. */
    private fun scaleArtworkDownTo(src: Bitmap, maxDimensionPx: Int): Bitmap {
        val longestEdge = maxOf(src.width, src.height)
        if (longestEdge <= maxDimensionPx) return src
        val scale = maxDimensionPx.toFloat() / longestEdge
        val targetWidth = (src.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (src.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true)
    }

    private fun noArtMarkerFile(appContext: Context, songId: Long): File {
        return File(getAlbumArtDir(appContext), "song_art_${songId}${CACHE_VERSION_SUFFIX}_no.jpg")
    }

    private fun legacyCachedAlbumArtFile(
        appContext: Context,
        songId: Long,
        versionSuffix: String = ""
    ): File {
        return File(getAlbumArtDir(appContext), "song_art_${songId}${versionSuffix}.jpg")
    }

    private fun legacyNoArtMarkerFile(
        appContext: Context,
        songId: Long,
        versionSuffix: String = ""
    ): File {
        return File(getAlbumArtDir(appContext), "song_art_${songId}${versionSuffix}_no.jpg")
    }

    private data class MediaStoreSongInfo(
        val path: String,
        val albumId: Long?,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null
    )

    private fun resolveSongMediaStoreInfo(
        appContext: Context,
        songId: Long
    ): MediaStoreSongInfo? {
        val selection = "${MediaStore.Audio.Media._ID} = ?"
        val selectionArgs = arrayOf(songId.toString())
        val projection = arrayOf(
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM
        )

        return runCatching {
            appContext.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)

                val title = if (titleCol >= 0) cursor.getString(titleCol) else null
                val artist = if (artistCol >= 0) cursor.getString(artistCol) else null
                val album = if (albumCol >= 0) cursor.getString(albumCol) else null

                MediaStoreSongInfo(
                    path = path,
                    albumId = albumId.takeIf { it > 0L },
                    title = title,
                    artist = artist,
                    album = album
                )
            }
        }.getOrNull()
    }

    private fun extractEmbeddedAlbumArtBytes(filePath: String): ByteArray? {
        val retrieverArtwork = MediaMetadataRetrieverPool.withRetriever { retriever ->
            try {
                retriever.setDataSource(filePath)
            } catch (e: IllegalArgumentException) {
                try {
                    FileInputStream(filePath).use { fis ->
                        retriever.setDataSource(fis.fd)
                    }
                } catch (e2: Exception) {
                    return@withRetriever null
                }
            }

            retriever.embeddedPicture?.takeIf { it.isNotEmpty() }
        }

        if (retrieverArtwork != null) {
            return retrieverArtwork
        }

        return runCatching {
            AudioMetadataReader.read(File(filePath))?.artwork?.bytes?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun shareableCacheUri(appContext: Context, file: File): Uri {
        return try {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.provider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
    }
}

internal fun resolveAlbumArtUriForLibraryScan(
    songId: Long,
    hasCachedArtwork: Boolean,
    hasNoArtworkMarker: Boolean,
    forceRefresh: Boolean
): String? {
    if (hasCachedArtwork) {
        return LocalArtworkUri.buildSongUri(songId)
    }
    if (hasNoArtworkMarker && !forceRefresh) {
        return null
    }
    return LocalArtworkUri.buildSongUri(songId)
}
