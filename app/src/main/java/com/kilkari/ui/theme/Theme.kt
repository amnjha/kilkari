package com.kilkari.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val KilkariColors = lightColorScheme(
    primary = KC.Indigo,
    onPrimary = KC.Surface,
    primaryContainer = KC.IndigoBg,
    onPrimaryContainer = KC.IndigoDeep,
    secondary = KC.Violet,
    onSecondary = KC.Surface,
    secondaryContainer = KC.VioletBg,
    onSecondaryContainer = KC.VioletDeep,
    tertiary = KC.Fuchsia,
    onTertiary = KC.Surface,
    tertiaryContainer = KC.FuchsiaBg,
    onTertiaryContainer = KC.FuchsiaDeep,
    background = KC.Screen,
    onBackground = KC.Ink,
    surface = KC.Surface,
    onSurface = KC.Ink,
    surfaceVariant = KC.VioletBg,
    onSurfaceVariant = KC.Muted,
    outline = KC.Border,
    outlineVariant = KC.Divider,
    error = KC.Rose,
    onError = KC.Surface,
    errorContainer = KC.RoseBg,
    onErrorContainer = KC.RoseDeep,
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
