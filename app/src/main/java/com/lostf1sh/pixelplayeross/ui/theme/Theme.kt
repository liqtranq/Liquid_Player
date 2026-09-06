package com.lostf1sh.pixelplayeross.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.lostf1sh.pixelplayeross.presentation.viewmodel.ColorSchemePair
import androidx.core.graphics.ColorUtils

val LocalPixelPlayerDarkTheme = staticCompositionLocalOf { false }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION")
@Composable
fun PixelPlayerStatusBarStyle(
    color: Color,
    useDarkIcons: Boolean = ColorUtils.calculateLuminance(color.toArgb()) > 0.55,
    navigationColor: Color? = null,
    useDarkNavigationIcons: Boolean = navigationColor
        ?.let { ColorUtils.calculateLuminance(it.toArgb()) > 0.55 }
        ?: useDarkIcons
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val updateNavigationBar = navigationColor != null
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = useDarkIcons

            if (updateNavigationBar) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                isAppearanceLightNavigationBars = useDarkNavigationIcons
            }
        }
    }
}

val DarkColorScheme = darkColorScheme(
    primary = LiquidRustDark,
    onPrimary = PixelPlayerWhite,
    primaryContainer = LiquidRustDarkMuted,
    onPrimaryContainer = Color(0xFFFFB499),
    secondary = Color(0xFFC1B8A7),
    onSecondary = LiquidCanvasDark,
    secondaryContainer = Color(0xFF25231F),
    onSecondaryContainer = LiquidTextPrimaryDark,
    tertiary = LiquidRustDark,
    onTertiary = PixelPlayerWhite,
    tertiaryContainer = LiquidRustDarkMuted,
    onTertiaryContainer = Color(0xFFFFB499),
    background = LiquidCanvasDark,
    onBackground = LiquidTextPrimaryDark,
    surface = LiquidPanelBgDark,
    onSurface = LiquidTextPrimaryDark,
    surfaceVariant = LiquidCardBgDark,
    onSurfaceVariant = LiquidTextMutedDark,
    surfaceContainer = LiquidPanelBgDark,
    surfaceContainerLow = LiquidPanelInnerDark,
    surfaceContainerHigh = Color(0xFF22201B),
    surfaceContainerHighest = Color(0xFF2A2822),
    outline = LiquidLineDark,
    outlineVariant = LiquidLineLightDark,
    error = LiquidErrorDark,
    onError = PixelPlayerWhite
)

val LightColorScheme = lightColorScheme(
    primary = LiquidRustLight,
    onPrimary = PixelPlayerWhite,
    primaryContainer = Color(0xFFF0DCD3),
    onPrimaryContainer = Color(0xFF3E1508),
    secondary = Color(0xFF625C53),
    onSecondary = PixelPlayerWhite,
    secondaryContainer = Color(0xFFE5DECF),
    onSecondaryContainer = LiquidTextPrimaryLight,
    tertiary = LiquidRustLight,
    onTertiary = PixelPlayerWhite,
    background = LiquidCanvasLight,
    onBackground = LiquidTextPrimaryLight,
    surface = Color(0xFFE2DAC8),
    onSurface = LiquidTextPrimaryLight,
    surfaceVariant = Color(0xFFD4CCA8),
    onSurfaceVariant = LiquidTextMutedLight,
    surfaceContainer = Color(0xFFE2DAC8),
    surfaceContainerLow = Color(0xFFEAE3D2),
    surfaceContainerHigh = Color(0xFFDCD3C0),
    surfaceContainerHighest = Color(0xFFD2C9B5),
    outline = LiquidLineLight,
    outlineVariant = LiquidLineLightLight,
    surfaceTint = LiquidRustLight,
    error = LiquidErrorLight,
    onError = PixelPlayerWhite
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PixelPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemePairOverride: ColorSchemePair? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val finalColorScheme = when {
        colorSchemePairOverride != null -> {
            if (darkTheme) colorSchemePairOverride.dark else colorSchemePairOverride.light
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    PixelPlayerStatusBarStyle(
        color = finalColorScheme.background,
        navigationColor = finalColorScheme.background
    )

    CompositionLocalProvider(LocalPixelPlayerDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
