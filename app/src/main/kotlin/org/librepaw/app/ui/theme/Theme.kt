package org.librepaw.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme =
  lightColorScheme(
    primary = AppPrimaryDarkLight,
    onPrimary = AppOnPrimaryLight,
    primaryContainer = AppPrimary,
    // AppText rather than AppPrimaryDark: the latter is itself a fairly soft tone, and paired
    // with the equally-soft AppPrimary container (e.g. a SegmentedButton's selected pill, a
    // FAB) the two sit too close in lightness to read clearly. AppText is tuned for contrast
    // against both AppBackground and AppPrimary's medium-sage tone alike.
    onPrimaryContainer = AppTextLight,
    // LibrePaw's palette is deliberately monochrome (a single brand hue): secondary/tertiary
    // reuse the same tones rather than introducing accent colors the iOS app doesn't have,
    // so Material components that key off them (e.g. NavigationBar's selected-tab pill) stay
    // on-brand instead of falling back to M3's default purple baseline.
    secondary = AppPrimaryDarkLight,
    onSecondary = AppOnPrimaryLight,
    secondaryContainer = AppPrimary,
    onSecondaryContainer = AppTextLight,
    tertiary = AppPrimaryDarkLight,
    onTertiary = AppOnPrimaryLight,
    tertiaryContainer = AppPrimary,
    onTertiaryContainer = AppTextLight,
    background = AppBackgroundLight,
    onBackground = AppTextLight,
    surface = AppSurfaceLight,
    onSurface = AppTextLight,
    surfaceVariant = AppSurfaceLight,
    onSurfaceVariant = AppSecondaryTextLight,
    outline = AppBorderLight,
    outlineVariant = AppBorderLight,
    // Unspecified, these default to M3's baseline cool-gray/blue tones (visible, for example, as
    // a lavender tint behind the bottom NavigationBar), which clash with the brand's warm palette.
    surfaceContainerLowest = AppBackgroundLight,
    surfaceContainerLow = AppBackgroundLight,
    surfaceContainer = AppSurfaceLight,
    surfaceContainerHigh = AppSurfaceLight,
    surfaceContainerHighest = AppSurfaceLight,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = AppPrimaryDarkDark,
    onPrimary = AppOnPrimaryDark,
    primaryContainer = AppPrimary,
    onPrimaryContainer = AppTextLight,
    secondary = AppPrimaryDarkDark,
    onSecondary = AppOnPrimaryDark,
    secondaryContainer = AppPrimary,
    onSecondaryContainer = AppTextLight,
    tertiary = AppPrimaryDarkDark,
    onTertiary = AppOnPrimaryDark,
    tertiaryContainer = AppPrimary,
    onTertiaryContainer = AppTextLight,
    background = AppBackgroundDark,
    onBackground = AppTextDark,
    surface = AppSurfaceDark,
    onSurface = AppTextDark,
    surfaceVariant = AppSurfaceDark,
    onSurfaceVariant = AppSecondaryTextDark,
    outline = AppBorderDark,
    outlineVariant = AppBorderDark,
    surfaceContainerLowest = AppBackgroundDark,
    surfaceContainerLow = AppBackgroundDark,
    surfaceContainer = AppSurfaceDark,
    surfaceContainerHigh = AppSurfaceDark,
    surfaceContainerHighest = AppSurfaceDark,
  )

/** LibrePaw's own Material3 theme, matching the iOS app's brand palette (see [Color.kt]). */
@Composable
fun LibrePawTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme, typography = Typography, content = content)
}
