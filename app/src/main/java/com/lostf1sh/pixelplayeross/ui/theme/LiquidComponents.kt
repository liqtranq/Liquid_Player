package com.lostf1sh.pixelplayeross.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

val LiquidEase = CubicBezierEasing(0.18f, 0.74f, 0.20f, 1.00f)
val LiquidSnapEase = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.00f)

@Composable
fun Modifier.liquidTactileClick(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = tween(durationMillis = 60, easing = LiquidSnapEase),
        label = "tactileScale"
    )

    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
}

@Composable
fun LiquidStereoVuMeter(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    segmentsCount: Int = 12
) {
    var leftLevel by remember { mutableFloatStateOf(0f) }
    var rightLevel by remember { mutableFloatStateOf(0f) }
    var leftPeak by remember { mutableFloatStateOf(0f) }
    var rightPeak by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            leftLevel = 0f
            rightLevel = 0f
            leftPeak = 0f
            rightPeak = 0f
            return@LaunchedEffect
        }
        while (isPlaying) {
            val base = 0.45f + Random.nextFloat() * 0.45f
            val varL = (base + Random.nextFloat() * 0.15f).coerceIn(0.1f, 1.0f)
            val varR = (base + Random.nextFloat() * 0.15f).coerceIn(0.1f, 1.0f)

            leftLevel = varL
            rightLevel = varR
            if (varL > leftPeak) leftPeak = varL else leftPeak = (leftPeak - 0.04f).coerceAtLeast(0f)
            if (varR > rightPeak) rightPeak = varR else rightPeak = (rightPeak - 0.04f).coerceAtLeast(0f)

            delay(75)
        }
    }

    val animL by animateFloatAsState(targetValue = leftLevel, animationSpec = tween(70, easing = LiquidSnapEase), label = "vuL")
    val animR by animateFloatAsState(targetValue = rightLevel, animationSpec = tween(70, easing = LiquidSnapEase), label = "vuR")

    Column(
        modifier = modifier
            .background(Color(0xFF141311), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF2C2923), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VU // STEREO MONITOR",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = LiquidRustDark,
                letterSpacing = 0.5.sp
            )
            Text(
                text = if (isPlaying) "▶ ACTIVE" else "❚❚ IDLE",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                color = if (isPlaying) LiquidSuccessDark else LiquidTextMutedDark
            )
        }

        VuChannelRow(label = "L", level = animL, peak = leftPeak, segments = segmentsCount)
        VuChannelRow(label = "R", level = animR, peak = rightPeak, segments = segmentsCount)
    }
}

@Composable
private fun VuChannelRow(
    label: String,
    level: Float,
    peak: Float,
    segments: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = LiquidTextMutedDark,
            modifier = Modifier.width(10.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val litCount = (level * segments).toInt().coerceIn(0, segments)
            val peakIndex = (peak * segments).toInt().coerceIn(0, segments - 1)

            for (i in 0 until segments) {
                val fraction = i.toFloat() / segments
                val segColor = when {
                    fraction < 0.70f -> LiquidSuccessDark
                    fraction < 0.88f -> Color(0xFFE5A83B)
                    else -> LiquidRustDark
                }
                val isLit = i < litCount || (i == peakIndex && peak > 0.05f)
                val alpha = if (isLit) 1.0f else 0.15f

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(segColor.copy(alpha = alpha), RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

@Composable
fun LiquidBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LiquidRustDark,
    borderColor: Color = LiquidLineDark
) {
    Box(
        modifier = modifier
            .background(Color(0xFF1A1915), RoundedCornerShape(2.dp))
            .border(1.dp, borderColor, RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}
