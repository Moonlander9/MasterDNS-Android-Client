package com.masterdnsvpn.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4AE3C6),
    onPrimary = Color(0xFF04231F),
    primaryContainer = Color(0xFF0E3D36),
    onPrimaryContainer = Color(0xFFC4FFF4),
    secondary = Color(0xFF93B7FF),
    onSecondary = Color(0xFF0D1D38),
    secondaryContainer = Color(0xFF1A2D4F),
    onSecondaryContainer = Color(0xFFDCE7FF),
    tertiary = Color(0xFFFFC980),
    onTertiary = Color(0xFF3B2500),
    tertiaryContainer = Color(0xFF5A3A00),
    onTertiaryContainer = Color(0xFFFFE1B8),
    error = Color(0xFFFF8E8E),
    onError = Color(0xFF5D1111),
    errorContainer = Color(0xFF7A1E1E),
    onErrorContainer = Color(0xFFFFDAD8),
    background = Color(0xFF04080F),
    onBackground = Color(0xFFF2F7FF),
    surface = Color(0xFF0E1724),
    onSurface = Color(0xFFF2F7FF),
    surfaceVariant = Color(0xFF162131),
    onSurfaceVariant = Color(0xFF9AA7BC),
    surfaceContainer = Color(0xFF111B2A),
    surfaceContainerHigh = Color(0xFF172234),
    outline = Color(0xFF334154),
    outlineVariant = Color(0xFF223044),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6CF7DA),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF355A9A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE7FF),
    onSecondaryContainer = Color(0xFF001A43),
    tertiary = Color(0xFF7A5600),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA9),
    onTertiaryContainer = Color(0xFF261900),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FAFF),
    onBackground = Color(0xFF0E1724),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111B2A),
    surfaceVariant = Color(0xFFDCE3F0),
    onSurfaceVariant = Color(0xFF4C5A6C),
    surfaceContainer = Color(0xFFF0F4FA),
    surfaceContainerHigh = Color(0xFFE8EEF7),
    outline = Color(0xFF768497),
    outlineVariant = Color(0xFFC4CCD8),
)

private val AppTypography = Typography()

@Composable
fun MasterDnsVPNAndroidTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
