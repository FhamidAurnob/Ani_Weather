package com.example.wuwaweather.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.wuwaweather.R

val Manrope = FontFamily(
    Font(R.font.manrope_extralight, FontWeight.ExtraLight),
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

/** Velora palette: text is always light on the sky. */
object Velora {
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xCCFFFFFF)
    val TextTertiary = Color(0x8CFFFFFF)
    val GlassFill = Color(0x1FFFFFFF)
    val GlassFillStrong = Color(0x2EFFFFFF)
    val GlassBorder = Color(0x33FFFFFF)
    val Divider = Color(0x1FFFFFFF)
    val Accent = Color(0xFFFFD27A)       // warm sun gold
    val Rain = Color(0xFF8FD3FF)

    /** Soft shadow so white text stays readable over bright skies. */
    val TextShadow = Shadow(color = Color(0x40000000), offset = Offset(0f, 2f), blurRadius = 12f)
}

private val VeloraTypography = Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraLight, fontSize = 112.sp, letterSpacing = (-4).sp),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

@Composable
fun VeloraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Velora.Accent,
            onPrimary = Color(0xFF1A1A1A),
            background = Color(0xFF0A0F24),
            surface = Color(0xFF0A0F24),
            onBackground = Velora.TextPrimary,
            onSurface = Velora.TextPrimary,
        ),
        typography = VeloraTypography,
        content = content
    )
}
