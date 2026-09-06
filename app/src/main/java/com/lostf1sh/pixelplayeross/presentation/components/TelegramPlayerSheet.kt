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
        }
    }
}
