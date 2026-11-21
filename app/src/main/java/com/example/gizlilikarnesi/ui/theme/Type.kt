package com.example.gizlilikarnesi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.gizlilikarnesi.R

/**
 * Michroma: genis, teknolojik baslik/etiket fontu (HUD etiketleri, notlar).
 * Tek kalinligi var. Google Fonts, OFL lisansi.
 */
val Michroma = FontFamily(Font(R.font.michroma))

/**
 * Space Grotesk: govde metni. "Degisken" (variable) font: tek dosyada tum
 * kalinliklar var, hangisini istedigimizi variationSettings ile seciyoruz.
 * Google Fonts, OFL lisansi.
 */
@OptIn(ExperimentalTextApi::class)
private fun spaceGrotesk(weight: Int) = Font(
    R.font.space_grotesk,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val SpaceGrotesk = FontFamily(
    spaceGrotesk(300),
    spaceGrotesk(400),
    spaceGrotesk(500),
    spaceGrotesk(700)
)

/** HUD etiketleri: kucuk, buyuk harf, genis harf araligi ("THREAT ASSESSMENT"). */
val HudLabel = TextStyle(
    fontFamily = Michroma,
    fontSize = 10.sp,
    letterSpacing = 1.6.sp,
    color = InkSoft
)

val Typography = Typography(
    headlineMedium = TextStyle(fontFamily = Michroma, fontSize = 22.sp, letterSpacing = 1.sp, color = Ink),
    headlineSmall = TextStyle(fontFamily = Michroma, fontSize = 18.sp, letterSpacing = 0.8.sp, color = Ink),
    titleLarge = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Ink),
    titleMedium = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = Ink),
    titleSmall = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Ink),
    bodyLarge = TextStyle(fontFamily = SpaceGrotesk, fontSize = 16.sp, lineHeight = 22.sp, color = Ink),
    bodyMedium = TextStyle(fontFamily = SpaceGrotesk, fontSize = 14.sp, lineHeight = 20.sp, color = InkSoft),
    bodySmall = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Light, fontSize = 12.sp, lineHeight = 16.sp, color = InkSoft),
    labelLarge = TextStyle(fontFamily = Michroma, fontSize = 12.sp, letterSpacing = 1.4.sp),
    labelMedium = TextStyle(fontFamily = Michroma, fontSize = 10.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontFamily = Michroma, fontSize = 9.sp, letterSpacing = 1.2.sp)
)
