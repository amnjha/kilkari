package com.kilkari.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val KilkariColors = lightColorScheme(
    primary = KC.Coral,
    onPrimary = KC.Surface,
    primaryContainer = KC.CoralBg,
    onPrimaryContainer = KC.CoralDeep,
    secondary = KC.Clay,
    onSecondary = KC.Surface,
    secondaryContainer = KC.ClayBg,
    onSecondaryContainer = KC.ClayDeep,
    tertiary = KC.Gold,
    onTertiary = KC.Surface,
    tertiaryContainer = KC.GoldBg,
    onTertiaryContainer = KC.GoldDeep,
    background = KC.Screen,
    onBackground = KC.Ink,
    surface = KC.Surface,
    onSurface = KC.Ink,
    surfaceVariant = KC.SurfaceWarm,
    onSurfaceVariant = KC.Muted,
    outline = KC.Border,
    outlineVariant = KC.Divider,
    error = KC.Danger,
    onError = KC.Surface,
    errorContainer = KC.DangerBg,
    onErrorContainer = KC.DangerDeep,
)

/**
 * Kilkari is a deliberately light-only surface — the design has one ground colour and the
 * tinted accent cards read incorrectly when inverted, so we do not follow the system theme.
 */
@Composable
fun KilkariTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KilkariColors,
        typography = KilkariTypography,
        content = content,
    )
}
