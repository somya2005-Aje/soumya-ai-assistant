package ai.soumya.assistant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme

val Navy900 = Color(0xFF070B1A)
val Navy800 = Color(0xFF0B1026)
val Navy700 = Color(0xFF11152B)
val CardBg = Color(0xFF151B38)
val Cyan400 = Color(0xFF21D4FD)
val Blue500 = Color(0xFF2878FF)
val Violet500 = Color(0xFF7B3FF2)
val Mint400 = Color(0xFF38E6B5)
val TextPrimary = Color(0xFFF2F5FF)
val TextSecondary = Color(0xFF9AA7C7)

private val SoumyaColors = darkColorScheme(
    primary = Cyan400,
    secondary = Violet500,
    tertiary = Mint400,
    background = Navy900,
    surface = Navy800,
    surfaceVariant = CardBg,
    onPrimary = Navy900,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

private val SoumyaTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp)
)

@Composable
fun SoumyaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SoumyaColors,
        typography = SoumyaTypography,
        content = content
    )
}
