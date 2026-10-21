package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Theme-aware accent text colors.
 *
 * The old code hard-coded DARK greens (#047857 / #059669 / #1B5E20) as text on
 * pale translucent surfaces — those become near-invisible on the MikroPilot
 * dark theme. These helpers keep the deep green for light mode (where it reads
 * well on white cards) and switch to a bright mint/emerald in dark mode.
 */

/** Success / online text — readable on both dark & light surfaces. */
@Composable
fun successText(): Color =
    if (isSystemInDarkTheme()) Color(0xFF4ADE80) else Color(0xFF047857)

/** Stronger success text (headline values, badges on dark fills). */
@Composable
fun successTextStrong(): Color =
    if (isSystemInDarkTheme()) Color(0xFF86EFAC) else Color(0xFF065F46)

/** Money / revenue text. */
@Composable
fun moneyText(): Color =
    if (isSystemInDarkTheme()) Color(0xFF34D399) else Color(0xFF047857)

/** Active / info text (cyan family works on dark, deep blue on light). */
@Composable
fun activeText(): Color =
    if (isSystemInDarkTheme()) Color(0xFF7DD3FC) else Color(0xFF0369A1)

/** Danger text. */
@Composable
fun dangerText(): Color =
    if (isSystemInDarkTheme()) Color(0xFFFCA5A5) else Color(0xFFB91C1C)