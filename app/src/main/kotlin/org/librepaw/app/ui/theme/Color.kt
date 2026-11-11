package org.librepaw.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * LibrePaw's brand color tokens, kept matching the iOS app's own asset-catalog colorsets
 * (`Color+Theme.swift`) hex-for-hex so both platforms look like the same app.
 */

// Page background.
val AppBackgroundLight = Color(0xFFF5F8F4)
val AppBackgroundDark = Color(0xFF171D1A)

// Card/row surface drawn on top of the page background.
val AppSurfaceLight = Color(0xFFFFFFFF)
val AppSurfaceDark = Color(0xFF252E29)

// Soft brand tone, same in both appearances; used for container fills (top bars, FABs).
val AppPrimary = Color(0xFFA8C5AE)

// Stronger variant of AppPrimary for text/icons that need more contrast than the soft tone gives.
val AppPrimaryDarkLight = Color(0xFF668B70)
val AppPrimaryDarkDark = Color(0xFF85AA8D)

// Primary text.
val AppTextLight = Color(0xFF29352D)
val AppTextDark = Color(0xFFEFF5EF)

// Secondary/caption text.
val AppSecondaryTextLight = Color(0xFF78847B)
val AppSecondaryTextDark = Color(0xFFA9B7AC)

// Hairlines, dividers, strokes.
val AppBorderLight = Color(0xFFE2EAE2)
val AppBorderDark = Color(0xFF37423B)

// Text/icon color for content drawn directly on a solid AppPrimaryDark fill (e.g. a filled Button).
val AppOnPrimaryLight = Color(0xFFFFFFFF)
val AppOnPrimaryDark = Color(0xFF0E150F)
