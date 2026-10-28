package com.clusterjam.mobile.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ClusterJamColorScheme =
    lightColorScheme(
        primary = JamColors.Ink,
        onPrimary = JamColors.Surface,
        primaryContainer = JamColors.Highlight,
        onPrimaryContainer = JamColors.Ink,
        secondary = JamColors.Blue,
        onSecondary = JamColors.Surface,
        secondaryContainer = JamColors.Highlight,
        onSecondaryContainer = JamColors.Ink,
        tertiary = JamColors.Blue,
        onTertiary = JamColors.Surface,
        tertiaryContainer = JamColors.Highlight,
        onTertiaryContainer = JamColors.Ink,
        background = JamColors.Background,
        onBackground = JamColors.Text,
        surface = JamColors.Surface,
        onSurface = JamColors.Text,
        surfaceVariant = JamColors.Highlight,
        onSurfaceVariant = JamColors.Muted,
        surfaceTint = JamColors.Ink,
        surfaceBright = JamColors.Surface,
        surfaceDim = JamColors.Background,
        surfaceContainerLowest = JamColors.Surface,
        surfaceContainerLow = JamColors.Background,
        surfaceContainer = JamColors.Background,
        surfaceContainerHigh = JamColors.Highlight,
        surfaceContainerHighest = JamColors.Highlight,
        outline = JamColors.Muted,
        outlineVariant = JamColors.Border,
        error = JamColors.Error,
        onError = JamColors.Surface,
        errorContainer = JamColors.ErrorBackground,
        onErrorContainer = JamColors.Error,
        inverseSurface = JamColors.Text,
        inverseOnSurface = JamColors.Surface,
        inversePrimary = JamColors.Highlight,
        scrim = JamColors.Text,
    )

/** Fixed identity, independent of wallpaper and system night mode. */
@Composable
fun ClusterJAMClientTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClusterJamColorScheme,
        typography = Typography,
        shapes = JamShapes,
        content = content,
    )
}
