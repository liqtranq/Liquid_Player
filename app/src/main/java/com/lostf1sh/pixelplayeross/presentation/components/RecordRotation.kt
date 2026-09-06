package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive

/** Retains the angle on pause and updates only the artwork's render layer. */
@Composable
internal fun Modifier.recordRotation(running: Boolean): Modifier {
    val rotation = remember { Animatable(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(running, lifecycle) {
        if (!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) awaitCancellation()
                rotation.animateTo(360f, tween(((360f - rotation.value) / 360f * 12000).toInt().coerceAtLeast(1), easing = LinearEasing))
                rotation.snapTo(0f)
            }
        }
    }
    return graphicsLayer { rotationZ = rotation.value }
}
