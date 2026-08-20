package de.nexterp.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A59C6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E8FF),
    onPrimaryContainer = Color(0xFF001A4D),
    secondary = Color(0xFF315F91),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E4F7),
    onSecondaryContainer = Color(0xFF0A1D33),
    tertiary = Color(0xFFFF6B21),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0D0),
    onTertiaryContainer = Color(0xFF351000),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF151A21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151A21),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF3E4652),
    outline = Color(0xFF727984)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF002B61),
    primaryContainer = Color(0xFF003D82),
    onPrimaryContainer = Color(0xFFD8E8FF),
    secondary = Color(0xFFB3C8E8),
    onSecondary = Color(0xFF17304D),
    secondaryContainer = Color(0xFF24476E),
    onSecondaryContainer = Color(0xFFD6E4F7),
    tertiary = Color(0xFFFFB68F),
    onTertiary = Color(0xFF552000),
    tertiaryContainer = Color(0xFF783100),
    onTertiaryContainer = Color(0xFFFFE0D0),
    background = Color(0xFF0E141C),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF141B24),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF3E4652),
    onSurfaceVariant = Color(0xFFC2CAD5),
    outline = Color(0xFF8C95A1)
)

private val NextERPShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

private val NextERPTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 33.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
)

@Composable
fun NextERPTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = NextERPTypography,
        shapes = NextERPShapes,
        content = content
    )
}
