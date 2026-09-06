package com.lostf1sh.pixelplayeross.data.telegram

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramMusicService @Inject constructor() {

    private val defaultChannels = listOf(
        TelegramChannelInfo("@liqtranq_beats", "LIQTRANQ BEATS & LOOPS", "Producer catalog & stems"),
        TelegramChannelInfo("@audiophile_flac", "LOSSLESS FLAC MASTER", "Hi-Res studio masters"),
        TelegramChannelInfo("@ambient_drones", "ANALOG SYNTH & DRONES", "Modular synthesizer textures")
    )

    fun getAvailableChannels(): List<TelegramChannelInfo> = defaultChannels

    /**
     * Curated sample audio tracks from Telegram music community & open streaming endpoints.
     */
    fun getSampleTracksForChannel(channel: String): List<TelegramTrack> {
        return listOf(
            TelegramTrack(
                id = "tg_101",
                title = "Analog Tape Texture #01",
                artist = "LIQTRANQ",
                durationSeconds = 184,
                fileSize = 7420000,
                mimeType = "audio/mpeg",
                streamUrl = "https://actions.google.com/sounds/v1/science_fiction/scifi_hum.ogg",
                channelTitle = "LIQTRANQ BEATS & LOOPS"
            ),
            TelegramTrack(
                id = "tg_102",
                title = "Modular Pulse 120BPM",
                artist = "LIQTRANQ STUDIOS",
                durationSeconds = 215,
                fileSize = 8900000,
                mimeType = "audio/mpeg",
                streamUrl = "https://actions.google.com/sounds/v1/science_fiction/teleport.ogg",
                channelTitle = "LIQTRANQ BEATS & LOOPS"
            ),
            TelegramTrack(
                id = "tg_103",
                title = "Vintage Cassette Warmth",
                artist = "ANALOG COLLECTIVE",
                durationSeconds = 240,
                fileSize = 9800000,
                mimeType = "audio/flac",
                streamUrl = "https://actions.google.com/sounds/v1/science_fiction/alien_hum.ogg",
                channelTitle = "LOSSLESS FLAC MASTER"
            )
        )
    }

    /**
     * Resolves audio file direct stream URL via Telegram Bot API given a bot token and file_id.
     */
    suspend fun resolveBotAudioUrl(botToken: String, fileId: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.telegram.org/bot$botToken/getFile?file_id=$fileId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(json)
                if (obj.optBoolean("ok")) {
                    val filePath = obj.getJSONObject("result").getString("file_path")
                    return@withContext "https://api.telegram.org/file/bot$botToken/$filePath"
                }
            }
        } catch (_: Exception) {}
        null
    }

    /**
     * Downloads an audio stream offline to Music/Telegram/ and registers it with Android MediaStore.
     */
    suspend fun downloadTrackOffline(context: Context, track: TelegramTrack): Result<File> = withContext(Dispatchers.IO) {
        try {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val tgDir = File(musicDir, "Telegram").apply { if (!exists()) mkdirs() }
            val safeArtist = track.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val safeTitle = track.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val ext = if (track.mimeType.contains("flac")) "flac" else if (track.mimeType.contains("ogg")) "ogg" else "mp3"
            val targetFile = File(tgDir, "$safeArtist - $safeTitle.$ext")

            val url = URL(track.streamUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 25000
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(track.mimeType),
                    null
                )
                Result.success(targetFile)
            } else {
                Result.failure(IOException("HTTP ${conn.responseCode} while downloading track"))
            }
        } catch (e: Exception) {
            Timber.tag("TelegramMusicService").e(e, "Download failed")
            Result.failure(e)
        }
    }
}

data class TelegramChannelInfo(
    val username: String,
    val title: String,
    val description: String
)
