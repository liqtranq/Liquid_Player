package com.lostf1sh.pixelplayeross.presentation.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.media.converter.AudioConverter
import com.lostf1sh.pixelplayeross.data.media.converter.ConversionConfig
import com.lostf1sh.pixelplayeross.data.media.converter.ConversionState
import com.lostf1sh.pixelplayeross.data.media.converter.TargetFormat
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.ui.theme.LiquidRustDark
import com.lostf1sh.pixelplayeross.ui.theme.LiquidSuccessDark
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioConverterSheet(
    song: Song,
    onDismiss: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedFormat by remember { mutableStateOf(TargetFormat.AAC_M4A) }
    var selectedBitrate by remember { mutableIntStateOf(192_000) }
    var preserveTags by remember { mutableStateOf(true) }
    var conversionState by remember { mutableStateOf<ConversionState>(ConversionState.Idle) }

    val isConverting = conversionState is ConversionState.Converting

    ModalBottomSheet(
        onDismissRequest = {
            if (!isConverting) onDismiss()
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        containerColor = Color(0xFF141311),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF3A362E)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            text = "AUDIO CONVERTER STUDIO",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE8E3D8)
                        )
                    }
                    Text(
                        text = "AIMP-GRADE // HARDWARE ACCELERATED TRANSCODER",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF9B9485),
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .background(Color(0xFF1C1B17), RoundedCornerShape(3.dp))
                        .border(1.dp, Color(0xFF3A362E), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isConverting) "PROCESSING" else "READY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConverting) LiquidRustDark else LiquidSuccessDark
                    )
                }
            }

            // Source Track Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1A1915),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2923))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF24221D)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.albumArtUriString != null) {
                            SmartImage(
                                model = song.albumArtUriString,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AudioFile,
                                contentDescription = null,
                                tint = LiquidRustDark,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE8E3D8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.displayArtist,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF9B9485),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val srcExt = remember(song.path) { File(song.path).extension.uppercase().ifBlank { "AUDIO" } }
                        val srcSize = remember(song.path) { File(song.path).takeIf { it.exists() }?.length() ?: 0L }
                        Text(
                            text = "SRC: $srcExt · ${formatFileSize(srcSize)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = LiquidRustDark
                        )
                    }
                }
            }

            // Target Format Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "ЦЕЛЕВОЙ ФОРМАТ // TARGET FORMAT",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9B9485)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TargetFormat.entries.forEach { fmt ->
                        val isSelected = selectedFormat == fmt
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) LiquidRustDark else Color(0xFF1A1915),
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) LiquidRustDark else Color(0xFF2C2923),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable(enabled = !isConverting) { selectedFormat = fmt }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = fmt.label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFFE8E3D8)
                            )
                        }
                    }
                }
            }

            // Bitrate Selector (Only for AAC/M4A)
            AnimatedVisibility(visible = selectedFormat == TargetFormat.AAC_M4A) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "БИТРЕЙТ // ENCODER BITRATE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9B9485)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            128_000 to "128k",
                            192_000 to "192k HQ",
                            256_000 to "256k VHQ",
                            320_000 to "320k MAX"
                        ).forEach { (rate, label) ->
                            val isSelected = selectedBitrate == rate
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) Color(0xFF2C2923) else Color(0xFF1A1915),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) LiquidRustDark else Color(0xFF2C2923),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable(enabled = !isConverting) { selectedBitrate = rate }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) LiquidRustDark else Color(0xFF9B9485)
                                )
                            }
                        }
                    }
                }
            }

            // Preserve Metadata Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1915), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF2C2923), RoundedCornerShape(4.dp))
                    .clickable(enabled = !isConverting) { preserveTags = !preserveTags }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "СОХРАНЯТЬ ТЕГИ И ОБЛОЖКУ",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE8E3D8)
                    )
                    Text(
                        text = "Title, Artist, Album, Year, Cover Art",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = Color(0xFF9B9485)
                    )
                }
                Switch(
                    checked = preserveTags,
                    onCheckedChange = { preserveTags = it },
                    enabled = !isConverting,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LiquidRustDark
                    )
                )
            }

            // Output destination note
            Text(
                text = "ВЫВОД: Music/Liquid_Converted/",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color(0xFF9B9485)
            )

            // Progress & State
            when (val state = conversionState) {
                is ConversionState.Converting -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = state.phase.uppercase(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = LiquidRustDark
                            )
                            Text(
                                text = "${state.progress}%",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE8E3D8)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = LiquidRustDark,
                            trackColor = Color(0xFF2C2923)
                        )
                    }
                }

                is ConversionState.Success -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF17241A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LiquidSuccessDark)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = LiquidSuccessDark,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "КОНВЕРТАЦИЯ ЗАВЕРШЕНА УСПЕШНО",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LiquidSuccessDark
                                )
                            }
                            Text(
                                text = state.outputFile.name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFFE8E3D8)
                            )
                            Text(
                                text = "Размер: ${formatFileSize(state.outputFile.length())}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF9B9485)
                            )
                        }
                    }
                }

                is ConversionState.Error -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF2B1414),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE04532))
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFE04532),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.message,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFFE8E3D8)
                            )
                        }
                    }
                }

                ConversionState.Idle -> Unit
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isConverting,
                    modifier = Modifier.weight(0.4f)
                ) {
                    Text(
                        text = if (conversionState is ConversionState.Success) "ЗАКРЫТЬ" else "ОТМЕНА",
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF9B9485)
                    )
                }

                Button(
                    onClick = {
                        if (conversionState is ConversionState.Success) {
                            // Play converted track
                            val file = (conversionState as ConversionState.Success).outputFile
                            Toast.makeText(context, "Файл зарегистрирован в фонотеке", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            coroutineScope.launch {
                                conversionState = ConversionState.Converting(0, "Запуск...")
                                val config = ConversionConfig(
                                    targetFormat = selectedFormat,
                                    bitrate = selectedBitrate,
                                    preserveMetadata = preserveTags
                                )
                                val result = AudioConverter.convert(
                                    context = context,
                                    song = song,
                                    config = config,
                                    onProgress = { pct, phase ->
                                        conversionState = ConversionState.Converting(pct, phase)
                                    }
                                )
                                result.onSuccess { outputFile ->
                                    conversionState = ConversionState.Success(outputFile, null)
                                    Toast.makeText(
                                        context,
                                        "Сохранено в Music/Liquid_Converted",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }.onFailure { error ->
                                    conversionState = ConversionState.Error(
                                        error.message ?: "Ошибка конвертации"
                                    )
                                }
                            }
                        }
                    },
                    enabled = !isConverting,
                    modifier = Modifier.weight(0.6f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiquidRustDark,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (conversionState is ConversionState.Success) "ГОТОВО" else "КОНВЕРТИРОВАТЬ",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(
        java.util.Locale.US,
        "%.1f %s",
        bytes / Math.pow(1024.0, digitGroups.toDouble()),
        units[digitGroups]
    )
}
