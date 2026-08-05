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
    primary = Color(0xFF4F7D2A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EDBA),
    onPrimaryContainer = Color(0xFF173800),
    secondary = Color(0xFF53634A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E8CC),
    onSecondaryContainer = Color(0xFF111F0D),
    tertiary = Color(0xFF38666B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBCEBF0),
    onTertiaryContainer = Color(0xFF002F34),
    background = Color(0xFFF7F8F4),
    onBackground = Color(0xFF191C17),
    surface = Color(0xFFFCFDF8),
    onSurface = Color(0xFF191C17),
    surfaceVariant = Color(0xFFE1E4DB),
    onSurfaceVariant = Color(0xFF44483F),
    outline = Color(0xFF75796F)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9D99B),
    onPrimary = Color(0xFF213600),
    primaryContainer = Color(0xFF385113),
    onPrimaryContainer = Color(0xFFD5EDBA),
    secondary = Color(0xFFBBCBAF),
    onSecondary = Color(0xFF263421),
    secondaryContainer = Color(0xFF3C4B36),
    onSecondaryContainer = Color(0xFFD7E8CC),
    tertiary = Color(0xFFA0CFD4),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF1E4D52),
    onTertiaryContainer = Color(0xFFBCEBF0),
    background = Color(0xFF11140F),
    onBackground = Color(0xFFE1E4DB),
    surface = Color(0xFF171A15),
    onSurface = Color(0xFFE1E4DB),
    surfaceVariant = Color(0xFF44483F),
    onSurfaceVariant = Color(0xFFC4C8BC),
    outline = Color(0xFF8E9287)
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
