package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.telegram.TelegramChannelInfo
import com.lostf1sh.pixelplayeross.data.telegram.TelegramMusicService
import com.lostf1sh.pixelplayeross.data.telegram.TelegramTrack
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramPlayerSheet(
    onDismissRequest: () -> Unit,
    playerViewModel: PlayerViewModel,
    telegramService: TelegramMusicService = remember { TelegramMusicService() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val downloadingTrackIds = remember { mutableStateMapOf<String, Boolean>() }
    val savedTrackIds = remember { mutableStateMapOf<String, Boolean>() }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedChannel by remember { mutableStateOf("@liqtranq_beats") }
    var customChannelInput by remember { mutableStateOf("") }
    val channels = remember { telegramService.getAvailableChannels() }
    val tracks = remember(selectedChannel) { telegramService.getSampleTracksForChannel(selectedChannel) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        containerColor = Color(0xFF141311),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF3A362E)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "◆",
                            color = LiquidRustDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TELEGRAM AUDIO DECK",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE8E3D8)
                        )
                    }
                    Text(
                        text = "CHANNELS // SAVED MESSAGES // CLOUD STREAM",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF9B9485),
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .background(Color(0xFF1A1915), RoundedCornerShape(2.dp))
                        .border(1.dp, Color(0xFF3A362E), RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "ONLINE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = LiquidSuccessDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Channel Selector Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                channels.forEach { ch ->
                    val isSelected = selectedChannel == ch.username
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isSelected) LiquidRustDark else Color(0xFF1C1B17),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) LiquidRustDark else Color(0xFF3A362E),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { selectedChannel = ch.username }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ch.username,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color(0xFFE8E3D8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tracks Count Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CLOUD QUEUE // ${tracks.size} TRACKS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9B9485),
                    letterSpacing = 0.5.sp
                )

                // Play All button
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1C1B17), RoundedCornerShape(3.dp))
                        .border(1.dp, LiquidRustDark, RoundedCornerShape(3.dp))
                        .clickable {
                            val songList = tracks.map { it.toSong() }
                            if (songList.isNotEmpty()) {
                                playerViewModel.playSongs(songList, songList.first(), "Telegram: $selectedChannel")
                                onDismissRequest()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "▶ PLAY ALL",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = LiquidRustDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Track List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tracks) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A1915), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF2C2923), RoundedCornerShape(4.dp))
                            .clickable {
                                val song = track.toSong()
                                playerViewModel.playSong(song)
                                onDismissRequest()
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Badge
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF141311), RoundedCornerShape(2.dp))
                                .border(1.dp, Color(0xFF3A362E), RoundedCornerShape(2.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (track.mimeType.contains("flac")) "FLAC" else "MP3",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (track.mimeType.contains("flac")) LiquidSuccessDark else LiquidRustDark
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE8E3D8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track.artist} · ${track.durationSeconds / 60}:${(track.durationSeconds % 60).toString().padStart(2, '0')}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF9B9485),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Offline Save Button
                        val isDownloading = downloadingTrackIds[track.id] == true
                        val isSaved = savedTrackIds[track.id] == true

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    if (isSaved) Color(0xFF17241A) else Color(0xFF1C1B17),
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSaved) LiquidSuccessDark else Color(0xFF3A362E),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable(enabled = !isDownloading && !isSaved) {
                                    coroutineScope.launch {
                                        downloadingTrackIds[track.id] = true
                                        val result = telegramService.downloadTrackOffline(context, track)
                                        result.onSuccess {
                                            savedTrackIds[track.id] = true
                                            Toast.makeText(
                                                context,
                                                "Сохранено в Music/Telegram",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }.onFailure { err ->
                                            Toast.makeText(
                                                context,
                                                "Ошибка: ${err.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        downloadingTrackIds[track.id] = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = LiquidRustDark
                                )
                            } else if (isSaved) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Saved",
                                    tint = LiquidSuccessDark,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDownload,
                                    contentDescription = "Save Offline",
                                    tint = Color(0xFF9B9485),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Play Button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(LiquidRustDark, RoundedCornerShape(4.dp))
                                .clickable {
                                    val song = track.toSong()
                                    playerViewModel.playSong(song)
                                    onDismissRequest()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Podcast & Quick Jump Controls
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF1A1915),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2923))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PODCAST DECK // QUICK JUMP & SPEED",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF9B9485)
                        )

                        // Seek buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF24221D), RoundedCornerShape(3.dp))
                                    .border(1.dp, Color(0xFF3A362E), RoundedCornerShape(3.dp))
                                    .clickable {
                                        playerViewModel.seekRelative(-10_000L)
                                        Toast.makeText(context, "-10 сек", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "-10s",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE8E3D8)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF24221D), RoundedCornerShape(3.dp))
                                    .border(1.dp, Color(0xFF3A362E), RoundedCornerShape(3.dp))
                                    .clickable {
                                        playerViewModel.seekRelative(30_000L)
                                        Toast.makeText(context, "+30 сек", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "+30s",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE8E3D8)
                                )
                            }
                        }
                    }

                    // Speed Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.75f to "0.75x", 1.0f to "1.0x", 1.25f to "1.25x", 1.5f to "1.5x", 2.0f to "2.0x")
                            .forEach { (speed, label) ->
                                val isSelected = currentSpeed == speed
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) LiquidRustDark else Color(0xFF141311),
                                            RoundedCornerShape(3.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) LiquidRustDark else Color(0xFF3A362E),
                                            RoundedCornerShape(3.dp)
                                        )
                                        .clickable {
                                            currentSpeed = speed
                                            playerViewModel.setPlaybackSpeed(speed)
                                            Toast.makeText(context, "Скорость: $label", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color(0xFF9B9485)
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}
