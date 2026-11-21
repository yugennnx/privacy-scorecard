package com.example.gizlilikarnesi.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Y2K / Frutiger Aero paleti: buz mavisi zemin, krom/gumus yuzeyler,
 * parlak "jel" vurgular. Referans: 2000'ler basi Sony/Panasonic reklamlari,
 * "ambient lounge" CD kapaklari, Windows Vista/Aero arayuzleri.
 */

// Yazi renkleri: saf siyah yerine derin lacivert (buz zeminde daha yumusak)
val Ink = Color(0xFF0B2A4A)
val InkSoft = Color(0xFF4E6A86)
val InkFaint = Color(0xFF8BA3BA)

// Ana vurgu: aqua (Aero'nun imza rengi)
val AquaLight = Color(0xFF8FDBF7)
val Aqua = Color(0xFF1E9BD7)
val AquaDeep = Color(0xFF0A6FB0)

// Zemin gradyani (ustten alta)
val SkyTop = Color(0xFFF6FBFE)
val SkyMid = Color(0xFFDDF0FB)
val SkyBottom = Color(0xFFC2E2F5)

// Cam paneller ve krom detaylar
val GlassFill = Color(0x9EFFFFFF)     // %62 opak beyaz
val GlassEdge = Color(0xFFFFFFFF)
val Hairline = Color(0x669CC4DE)      // ince ayirici cizgiler
val SilverLight = Color(0xFFF4F7FA)
val Silver = Color(0xFFC9D6E0)
val SilverDeep = Color(0xFF9FB2C2)

/**
 * Not rozetleri icin "jel" renk ciftleri (ust acik -> alt koyu). Trafik
 * isigi mantigi korunuyor (iyi = yesil/mavi, kotu = turuncu/kirmizi) ama
 * tonlar Aero paletinden secildi ki tasarima yabanci durmasin.
 */
data class GelColors(val top: Color, val bottom: Color)

val GelA = GelColors(Color(0xFF9BEAB0), Color(0xFF2FA866)) // aero yesil
val GelB = GelColors(Color(0xFF9BE0F8), Color(0xFF1E9BD7)) // aqua
val GelC = GelColors(Color(0xFFC3CBFF), Color(0xFF6474E5)) // periwinkle
val GelD = GelColors(Color(0xFFFFD58A), Color(0xFFF08A1C)) // amber
val GelF = GelColors(Color(0xFFFFA3AE), Color(0xFFE0344F)) // mercan kirmizi
val GelSilver = GelColors(SilverLight, SilverDeep)
