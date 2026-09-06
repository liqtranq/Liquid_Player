package com.lostf1sh.pixelplayeross.data.telegram

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
}

data class TelegramChannelInfo(
    val username: String,
    val title: String,
    val description: String
)
