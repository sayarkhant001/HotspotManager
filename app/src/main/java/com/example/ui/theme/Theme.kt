package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Enterprise Color Identity: Emerald + Slate (money-green, readable) ──
// High-contrast, fixed palettes — NOT wallpaper/dynamic driven.
// Dynamic color put dark-on-dark text on many devices ("texts not seeable" bug).

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF00E676),        // vivid emerald green (never dark)
    onPrimary = Color(0xFF00210C),      // near-black on green = high contrast
    primaryContainer = Color(0xFF003920),
    onPrimaryContainer = Color(0xFF8FF7BD),
    secondary = Color(0xFF10B981),
    onSecondary = Color(0xFF003325),
    secondaryContainer = Color(0xFF0A4D3A),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = Color(0xFF38BDF8),       // sky blue
    onTertiary = Color(0xFF00283F),
    tertiaryContainer = Color(0xFF003B5C),
    onTertiaryContainer = Color(0xFFB5E6FF),
    background = Color(0xFF0B1220),     // deep slate navy
    onBackground = Color(0xFFE2E8F0),   // light gray text — readable
    surface = Color(0xFF111A2E),        // cards
    onSurface = Color(0xFFE2E8F0),      // primary text on cards
    surfaceVariant = Color(0xFF1B2740),
    onSurfaceVariant = Color(0xFFCBD5E1), // secondary text — readable
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF047857),        // deep emerald (readable on light)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF059669),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8FAFC),     // clean off-white
    onBackground = Color(0xFF0F172A),   // near-black text
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF334155), // dark gray secondary text — readable
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFECACA),
    onErrorContainer = Color(0xFF7F1D1D),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
  // Dynamic color DISABLED — it caused unreadable text on many phones.
  // We now always use the fixed enterprise emerald/slate palette.
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
