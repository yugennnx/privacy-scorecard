package com.example.gizlilikarnesi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Uygulama HER ZAMAN aydinlik Aero temasini kullanir. Sablondaki "dinamik
 * renk" (Android 12+'da duvar kagidindan renk alma) ve karanlik mod bilerek
 * kapatildi: tasarim kimligi buz/krom paletine dayaniyor, sistemin bunu
 * degistirmesini istemiyoruz.
 */
private val AeroColorScheme = lightColorScheme(
    primary = Aqua,
    onPrimary = Color.White,
    secondary = AquaDeep,
    background = SkyMid,
    onBackground = Ink,
    surface = SilverLight,
    onSurface = Ink,
    surfaceVariant = Silver,
    onSurfaceVariant = InkSoft,
    outline = Hairline
)

@Composable
fun GizlilikarnesiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AeroColorScheme,
        typography = Typography,
        content = content
    )
}
