package com.lostf1sh.pixelplayeross.data.telegram

import com.lostf1sh.pixelplayeross.data.model.ArtistRef
import com.lostf1sh.pixelplayeross.data.model.Song

/**
 * Audio track retrieved from Telegram channels, chats, or bots.
 */
data class TelegramTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val fileSize: Long,
    val mimeType: String = "audio/mpeg",
    val streamUrl: String,
    val channelTitle: String = "Telegram Music",
    val albumArtUrl: String? = null
) {
    fun toSong(): Song {
        val primaryRef = ArtistRef(
            id = id.hashCode().toLong(),
            name = artist.ifBlank { "Telegram Artist" },
            isPrimary = true
        )
        return Song(
            id = "telegram_$id",
            title = title.ifBlank { "Telegram Track" },
            artist = artist.ifBlank { "Telegram Artist" },
            artistId = id.hashCode().toLong(),
            artists = listOf(primaryRef),
            album = channelTitle,
            albumId = channelTitle.hashCode().toLong(),
            path = streamUrl,
            contentUriString = streamUrl,
            albumArtUriString = albumArtUrl,
            duration = durationSeconds * 1000L,
            mimeType = mimeType,
            bitrate = 320,
            sampleRate = 44100
        )
    }
}
