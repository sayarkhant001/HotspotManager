package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// MikroPilot‑Style Enterprise Dashboard Palette
// Deep‑space navy panels + electric‑cyan command color + emerald
// monetary accents + violet tertiary. High‑contrast & readable.
// Fixed palettes — Dynamic color disabled (caused unreadable text).
// ─────────────────────────────────────────────────────────────

private val DarkColorScheme =
  darkColorScheme(
    // Command / interactive color — electric cyan (always bright)
    primary = Color(0xFF00D4FF),
    onPrimary = Color(0xFF002633),
    primaryContainer = Color(0xFF003B4D),
    onPrimaryContainer = Color(0xFFB9EBFF),
    // Revenue / success — emerald
    secondary = Color(0xFF22C55E),
    onSecondary = Color(0xFF00351C),
    secondaryContainer = Color(0xFF0A4D2A),
    onSecondaryContainer = Color(0xFFBBF7BF),
    // Alerts / analytics — violet
    tertiary = Color(0xFF8B8BF7),
    onTertiary = Color(0xFF1D1B4B),
    tertiaryContainer = Color(0xFF35327C),
    onTertiaryContainer = Color(0xFFE4E1FF),
    // Deep‑space background & panels
    background = Color(0xFF070B14),
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF0D1425),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF162137),
    onSurfaceVariant = Color(0xFFB6C2D8),
    surfaceContainerLow = Color(0xFF0F1729),
    surfaceContainer = Color(0xFF121A2E),
    surfaceContainerHigh = Color(0xFF162036),
    surfaceContainerHighest = Color(0xFF1C2740),
    surfaceTint = Color(0xFF00D4FF),
    outline = Color(0xFF5A6B85),
    outlineVariant = Color(0xFF29364E),
    error = Color(0xFFFF5C66),
    onError = Color(0xFF4A0A10),
    errorContainer = Color(0xFF7F1D24),
    onErrorContainer = Color(0xFFFECBCD),
    inverseSurface = Color(0xFFE6EDF7),
    inverseOnSurface = Color(0xFF121A2E),
    inversePrimary = Color(0xFF007295),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF007295),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC2EEFF),
    onPrimaryContainer = Color(0xFF003B4D),
    secondary = Color(0xFF047857),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC6F6D9),
    onSecondaryContainer = Color(0xFF033522),
    tertiary = Color(0xFF6257C7),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6E1FF),
    onTertiaryContainer = Color(0xFF1D1B4B),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEDF1F7),
    onSurfaceVariant = Color(0xFF33415C),
    surfaceContainerLow = Color(0xFFF0F4FA),
    surfaceContainer = Color(0xFFEAEFF7),
    surfaceContainerHigh = Color(0xFFE2E8F2),
    surfaceContainerHighest = Color(0xFFDCE3EF),
    surfaceTint = Color(0xFF007295),
    outline = Color(0xFF8A97AD),
    outlineVariant = Color(0xFFD3DCE8),
    error = Color(0xFFD62A35),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFECBCD),
    onErrorContainer = Color(0xFF7F1D24),
    inverseSurface = Color(0xFF121A2E),
    inverseOnSurface = Color(0xFFE6EDF7),
    inversePrimary = Color(0xFF00D4FF),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
  // Dynamic color DISABLED — it caused unreadable text on many phones.
  // We now always use the fixed MikroPilot enterprise palette.
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
