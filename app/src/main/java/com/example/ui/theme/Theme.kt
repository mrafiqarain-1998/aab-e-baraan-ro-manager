package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = WaterBlueMedium,
    onPrimary = Color.White,
    primaryContainer = WaterBlueDeep,
    onPrimaryContainer = Color.White,
    secondary = WaterCyanBright,
    onSecondary = Color.White,
    secondaryContainer = WaterNavyPrimary,
    onSecondaryContainer = Color.White,
    tertiary = WaterMint,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = WaterBlueMedium
)

private val LightColorScheme = lightColorScheme(
    primary = WaterNavyPrimary,
    onPrimary = Color.White,
    primaryContainer = WaterSkyLight,
    onPrimaryContainer = WaterNavyDark,
    secondary = WaterAqua,
    onSecondary = Color.White,
    secondaryContainer = WaterSkySurface,
    onSecondaryContainer = WaterNavyPrimary,
    tertiary = WaterCyanBright,
    onTertiary = Color.White,
    background = WaterSkySurface,
    onBackground = TextPrimaryDark,
    surface = WaterSurfaceWhite,
    onSurface = TextPrimaryDark,
    surfaceVariant = WaterSkyLight,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderLight
)

@Composable
fun waterPrimaryButtonColors() = androidx.compose.material3.ButtonDefaults.buttonColors(
    containerColor = WaterSkyButton,
    contentColor = WaterSkyButtonContent,
    disabledContainerColor = Color(0xFFE2E8F0),
    disabledContentColor = TextMuted
)

@Composable
fun waterFilterChipColors() = androidx.compose.material3.FilterChipDefaults.filterChipColors(
    selectedContainerColor = WaterSkyButton,
    selectedLabelColor = WaterSkyButtonContent,
    selectedLeadingIconColor = WaterSkyButtonContent,
    containerColor = WaterSkyLight,
    labelColor = WaterNavyDark,
    iconColor = WaterNavyPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep branded water theme consistent
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
