package com.shelf.archive.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Pine = Color(0xFF0E6B52)
private val PineInk = Color(0xFF06281E)
private val Mint = Color(0xFFCDE9DC)
private val Foam = Color(0xFF8EE0C0)
private val Paper = Color(0xFFF3EEE4)
private val Card = Color(0xFFFFFBF5)
private val Ink = Color(0xFF1C2430)
private val Clay = Color(0xFF8A4B32)
private val Sea = Color(0xFF1E4E7B)
private val Night = Color(0xFF101412)
private val NightCard = Color(0xFF1A211E)

private val LightColors = lightColorScheme(
    primary = Pine,
    onPrimary = Color(0xFFF3FFF8),
    primaryContainer = Mint,
    onPrimaryContainer = PineInk,
    secondary = Clay,
    onSecondary = Color(0xFFFFF7F3),
    secondaryContainer = Color(0xFFF3D9CC),
    onSecondaryContainer = Color(0xFF3A1C12),
    tertiary = Sea,
    onTertiary = Color(0xFFF4F8FF),
    tertiaryContainer = Color(0xFFD5E5F7),
    onTertiaryContainer = Color(0xFF0C2238),
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E0D4),
    onSurfaceVariant = Color(0xFF4A453C),
    outline = Color(0xFFD3CBBE),
    outlineVariant = Color(0xFFE4DDD2),
    error = Color(0xFF9F2D2D),
    onError = Color(0xFFFFF5F5),
    errorContainer = Color(0xFFF8D6D2),
    onErrorContainer = Color(0xFF3F0C0C),
    surfaceContainerLowest = Card,
    surfaceContainerLow = Color(0xFFF7F2E9),
    surfaceContainer = Color(0xFFF1EBDF),
    surfaceContainerHigh = Color(0xFFEBE4D6),
    surfaceContainerHighest = Color(0xFFE4DDCF),
)

private val DarkColors = darkColorScheme(
    primary = Foam,
    onPrimary = Color(0xFF063528),
    primaryContainer = Color(0xFF0E6B52),
    onPrimaryContainer = Mint,
    secondary = Color(0xFFE7B298),
    onSecondary = Color(0xFF3A1C12),
    secondaryContainer = Color(0xFF5C3222),
    onSecondaryContainer = Color(0xFFF3D9CC),
    tertiary = Color(0xFFA9C7EA),
    onTertiary = Color(0xFF0C2238),
    tertiaryContainer = Color(0xFF1E4E7B),
    onTertiaryContainer = Color(0xFFD5E5F7),
    background = Night,
    onBackground = Color(0xFFE7E2D8),
    surface = NightCard,
    onSurface = Color(0xFFE7E2D8),
    surfaceVariant = Color(0xFF2A332E),
    onSurfaceVariant = Color(0xFFC9C2B6),
    outline = Color(0xFF3E4943),
    outlineVariant = Color(0xFF2C3530),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFF8D6D2),
    surfaceContainerLowest = Color(0xFF0C100E),
    surfaceContainerLow = Color(0xFF151C19),
    surfaceContainer = Color(0xFF1E2622),
    surfaceContainerHigh = Color(0xFF28312C),
    surfaceContainerHighest = Color(0xFF333C37),
)

private val ShelfTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
)

@Composable
fun ShelfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = ShelfTypography,
        content = content,
    )
}
