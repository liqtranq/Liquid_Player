package com.lostf1sh.pixelplayeross.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.preferences.VuMeterStyle
import com.lostf1sh.pixelplayeross.data.service.player.AudioMeter
import com.lostf1sh.pixelplayeross.presentation.viewmodel.VuMeterViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

@Composable
fun LiquidStereoVuMeter(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    viewModel: VuMeterViewModel = hiltViewModel()
) {
    val style by viewModel.style.collectAsStateWithLifecycle()
    if (style == VuMeterStyle.OFF) return
    var left by remember { mutableFloatStateOf(0f) }
    var right by remember { mutableFloatStateOf(0f) }
    var available by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(isPlaying, visible, lifecycle) {
        left = 0f
        right = 0f
        if (!visible || !isPlaying) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            AudioMeter.subscribe()
            try {
                while (isActive) {
                    val reading = AudioMeter.read()
                    available = reading.available
                    val l = meterScale(reading.left)
                    val r = meterScale(reading.right)
                    left += (l - left) * if (l > left) 0.65f else 0.12f
                    right += (r - right) * if (r > right) 0.65f else 0.12f
                    delay(33)
                }
            } finally {
                AudioMeter.unsubscribe()
                left = 0f
                right = 0f
            }
        }
    }
    Column(modifier.background(Color(0xFF141311), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(
            text = if (!available && isPlaying) stringResource(R.string.vu_meter_unavailable) else stringResource(R.string.vu_meter_scale),
            color = LiquidTextMutedDark,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
        // Sample state is read only in the draw phase, never by the player layout.
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            for (channel in 0..1) {
                val level = if (channel == 0) left else right
                if (style == VuMeterStyle.NEEDLES) {
                    val width = size.width / 2
                    val center = Offset(width * (channel + 0.5f), size.height - 1.dp.toPx())
                    val radius = minOf(width * 0.45f, size.height * 0.9f)
                    drawArc(LiquidTextMutedDark.copy(alpha = 0.35f), 200f, 140f, false,
                        Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke(2.dp.toPx()))
                    val angle = Math.toRadians((200f + 140f * level).toDouble())
                    drawLine(LiquidRustDark, center, Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius), 2.dp.toPx(), StrokeCap.Round)
                    drawCircle(LiquidTextMutedDark, 2.dp.toPx(), center)
                } else {
                    val y = channel * size.height / 2 + 3.dp.toPx()
                    val h = size.height / 2 - 5.dp.toPx()
                    val count = if (style == VuMeterStyle.SEGMENTS) 24 else 64
                    val gap = if (style == VuMeterStyle.SEGMENTS) 2.dp.toPx() else 0f
                    val width = size.width / count
                    for (i in 0 until count) {
                        val fraction = i.toFloat() / count
                        val color = when {
                            fraction < 0.75f -> LiquidSuccessDark
                            fraction < 0.9f -> Color(0xFFE5A83B)
                            else -> LiquidRustDark
                        }
                        drawRect(color.copy(alpha = if (level > fraction) 1f else 0.15f), Offset(i * width, y), Size((width - gap).coerceAtLeast(1f), h))
                    }
                }
            }
        }
    }
}

internal fun meterScale(rms: Float): Float =
    if (rms <= 0f) 0f else ((20f * log10(rms) + 48f) / 48f).coerceIn(0f, 1f)
