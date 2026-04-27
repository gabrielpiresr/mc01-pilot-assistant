package com.betpass.mc01pilot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CockpitColorScheme = darkColorScheme(
    primary = CockpitPalette.Info,
    onPrimary = CockpitPalette.Background,
    primaryContainer = CockpitPalette.SurfaceContainer,
    onPrimaryContainer = CockpitPalette.OnBackground,

    secondary = CockpitPalette.StatusOk,
    onSecondary = CockpitPalette.Background,
    secondaryContainer = CockpitPalette.SurfaceContainer,
    onSecondaryContainer = CockpitPalette.OnBackground,

    tertiary = CockpitPalette.Attention,
    onTertiary = CockpitPalette.Background,
    tertiaryContainer = CockpitPalette.SurfaceContainer,
    onTertiaryContainer = CockpitPalette.OnBackground,

    error = CockpitPalette.Critical,
    onError = CockpitPalette.Background,
    errorContainer = CockpitPalette.SurfaceContainer,
    onErrorContainer = CockpitPalette.OnBackground,

    background = CockpitPalette.Background,
    onBackground = CockpitPalette.OnBackground,
    surface = CockpitPalette.Surface,
    onSurface = CockpitPalette.OnSurface,
    surfaceVariant = CockpitPalette.SurfaceContainer,
    onSurfaceVariant = CockpitPalette.OnSurfaceVariant,

    outline = CockpitPalette.OnSurfaceVariant,
    outlineVariant = CockpitPalette.SurfaceContainer,
    inverseSurface = CockpitPalette.OnBackground,
    inverseOnSurface = CockpitPalette.Background,
    inversePrimary = CockpitPalette.Info,
    scrim = CockpitPalette.Background
)

@Composable
fun MC01Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CockpitColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
