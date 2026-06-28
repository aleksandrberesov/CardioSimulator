package com.example.cardiosimulator.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = AccentGreen,
    onPrimary = OnAccent,
    secondary = TextSecondary,
    tertiary = EcgTraceTeal,
    background = PageBackground,
    surface = PanelBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = ControlFill,
    onSurfaceVariant = TextSecondary,
    outline = ControlBorder,
    outlineVariant = Hairline,
    surfaceContainer = PanelBackground,
    surfaceContainerLow = PanelBackground,
    surfaceContainerLowest = PanelBackground,
    surfaceContainerHigh = PanelBackground,
    surfaceContainerHighest = PanelBackground,
)

private val LightColorScheme = lightColorScheme(
    primary = AccentGreen,
    onPrimary = OnAccent,
    secondary = TextSecondary,
    tertiary = EcgTraceTeal,
    background = PageBackground,
    surface = PanelBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = ControlFill,
    onSurfaceVariant = TextSecondary,
    outline = ControlBorder,
    outlineVariant = Hairline,
    surfaceContainer = PanelBackground,
    surfaceContainerLow = PanelBackground,
    surfaceContainerLowest = PanelBackground,
    surfaceContainerHigh = PanelBackground,
    surfaceContainerHighest = PanelBackground,
)

val CardioShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun CardioSimulatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is disabled to match brand theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = CardioShapes,
        content = content
    )
}
