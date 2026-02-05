package ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Light theme colors (primary provided: #EE1733)
// Light theme – RED ONLY
private val md_theme_light_primary = Color(0xFFEE1733) // brand
private val md_theme_light_onPrimary = Color.White

private val md_theme_light_primaryContainer = Color(0xFFE77C8A)
private val md_theme_light_onPrimaryContainer = Color(0xFF3F0008)

private val md_theme_light_secondary = Color(0xFFC40F2A) // tmavší červená
private val md_theme_light_onSecondary = Color.White

private val md_theme_light_secondaryContainer = Color(0xFFF68B99)
private val md_theme_light_onSecondaryContainer = Color(0xFF3A0007)

private val md_theme_light_tertiary = Color(0xFFF1495A) //
private val md_theme_light_onTertiary = Color(0xFF3A0007)

private val md_theme_light_tertiaryContainer = Color(0xFFFFECEE)
private val md_theme_light_onTertiaryContainer = Color(0xFF3A0007)

private val md_theme_light_background = Color(0xFFFAFAFA)
private val md_theme_light_surface = Color.White
private val md_theme_light_onBackground = Color(0xFF1C1C1C)
private val md_theme_light_onSurface = Color(0xFF1C1C1C)

private val md_theme_light_error = Color(0xFFB00020)
private val md_theme_light_onError = Color.White

val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,

    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,

    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,

    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,

    error = md_theme_light_error,
    onError = md_theme_light_onError
)
// Dark theme – RED ONLY
private val md_theme_dark_primary = Color(0xFFFFB3BA)
private val md_theme_dark_onPrimary = Color(0xFF4A0009)

private val md_theme_dark_primaryContainer = Color(0xFF8E0018)
private val md_theme_dark_onPrimaryContainer = Color(0xFFFFD8DC)

private val md_theme_dark_secondary = Color(0xFFFF8A96)
private val md_theme_dark_onSecondary = Color(0xFF4A0009)

private val md_theme_dark_secondaryContainer = Color(0xFF6B0012)
private val md_theme_dark_onSecondaryContainer = Color(0xFFFFD8DC)

private val md_theme_dark_tertiary = Color(0xFFFF6F7D)
private val md_theme_dark_onTertiary = Color(0xFF4A0009)

private val md_theme_dark_tertiaryContainer = Color(0xFF7A0014)
private val md_theme_dark_onTertiaryContainer = Color(0xFFFFE3E6)

private val md_theme_dark_background = Color(0xFF121212)
private val md_theme_dark_surface = Color(0xFF1A1A1A)
private val md_theme_dark_onBackground = Color(0xFFEDEDED)
private val md_theme_dark_onSurface = Color(0xFFEDEDED)

private val md_theme_dark_error = Color(0xFFFFB4AB)
private val md_theme_dark_onError = Color(0xFF690005)

val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,

    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,

    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,

    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,

    error = md_theme_dark_error,
    onError = md_theme_dark_onError
)
