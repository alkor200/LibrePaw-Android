package org.librepaw.app.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * The effective dark/light decision after folding in the user's Appearance preference, not just
 * the raw system setting. Features that pick an asset by theme (e.g. the Map's tile style) should
 * read this instead of calling `isSystemInDarkTheme()` directly, so an explicit Light/Dark
 * override is honored everywhere, not just by [LibrePawTheme]'s own colors.
 */
val LocalDarkTheme = compositionLocalOf { false }
