package com.example.gizlilikarnesi.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gizlilikarnesi.ui.theme.Aqua
import com.example.gizlilikarnesi.ui.theme.AquaDeep
import com.example.gizlilikarnesi.ui.theme.AquaLight
import com.example.gizlilikarnesi.ui.theme.GelColors
import com.example.gizlilikarnesi.ui.theme.GelSilver
import com.example.gizlilikarnesi.ui.theme.GlassEdge
import com.example.gizlilikarnesi.ui.theme.GlassFill
import com.example.gizlilikarnesi.ui.theme.Hairline
import com.example.gizlilikarnesi.ui.theme.HudLabel
import com.example.gizlilikarnesi.ui.theme.Ink
import com.example.gizlilikarnesi.ui.theme.InkSoft
import com.example.gizlilikarnesi.ui.theme.Michroma
import com.example.gizlilikarnesi.ui.theme.Silver
import com.example.gizlilikarnesi.ui.theme.SilverLight
import com.example.gizlilikarnesi.ui.theme.SkyBottom
import com.example.gizlilikarnesi.ui.theme.SkyMid
import com.example.gizlilikarnesi.ui.theme.SkyTop
import kotlin.math.PI
import kotlin.math.sin

/*
 * Aero tasariminin yapi taslari. Hepsi Compose'un kendi cizim araclariyla
 * (gradyan, Canvas) yapildi; resim dosyasi kullanilmadi, bu yuzden her ekran
 * boyutunda keskin gorunur ve APK'yi buyutmez.
 *
 * "Jel" efektinin sirri: dikey gradyan (ustu acik, alti koyu) + ust yarida
 * yari saydam beyaz bir "parlama" katmani. 2000'lerin butonlarinin plastik/
 * cam gorunumu bu iki katmandan gelir.
 */

/** Tum ekranlarin arkasindaki zemin: buz mavisi gradyan + yumusak "baloncuklar". */
@Composable
fun AeroBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTop, SkyMid, SkyBottom)))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Aero baloncuklari: kenarlari yumusakca kaybolan beyaz daireler
            fun bubble(cx: Float, cy: Float, r: Float, alpha: Float) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = alpha), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )
            }
            bubble(size.width * 0.9f, size.height * 0.08f, size.width * 0.55f, 0.9f)
            bubble(size.width * 0.05f, size.height * 0.55f, size.width * 0.45f, 0.5f)
            bubble(size.width * 0.75f, size.height * 0.92f, size.width * 0.5f, 0.45f)

            // Ince "teknik" yatay cizgiler (Y2K tech dokusu), cok soluk
            val step = 6.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
        }
        content()
    }
}

/** Ust yarisi parlayan dikey jel gradyani (buton, rozet, ilerleme cubugu icin). */
private fun Modifier.gel(colors: GelColors, shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(colors.top, colors.bottom)))
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), colors.bottom)), shape)

/**
 * Jel yuzeyin ust yarisina binen parlama katmani. matchParentSize():
 * ebeveynin boyutunu ALIR ama onu buyutmez (fillMaxWidth kullansaydik
 * genisligi sinirsiz bir Row icinde butonu tum satira yayardi). Parlama
 * gradyanin ortada keskin bitmesiyle elde ediliyor.
 */
@Composable
private fun BoxScope.GelShine(cornerRadius: Dp) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .padding(horizontal = 3.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.75f),
                    0.48f to Color.White.copy(alpha = 0.1f),
                    0.48f to Color.Transparent
                )
            )
    )
}

/**
 * Buzlu cam panel: yari saydam beyaz zemin, beyaz kenar, ust tarafta parlama.
 * [label] verilirse ustte HUD basligi ("SUBJECTS") ve sagda uc kucuk nokta
 * (referans gorseldeki pencere dugmeleri) cizilir.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    label: String? = null,
    trailingLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = AquaDeep.copy(alpha = 0.25f), spotColor = AquaDeep.copy(alpha = 0.25f))
            .clip(shape)
            .background(GlassFill)
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.7f),
                    0.35f to Color.White.copy(alpha = 0f)
                )
            )
            .border(1.dp, Brush.verticalGradient(listOf(GlassEdge, Hairline)), shape)
            .padding(16.dp)
    ) {
        if (label != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = HudLabel, modifier = Modifier.weight(1f))
                if (trailingLabel != null) {
                    Text(trailingLabel, style = HudLabel.copy(color = AquaDeep))
                    Spacer(Modifier.width(10.dp))
                }
                repeat(3) {
                    Box(
                        Modifier
                            .padding(start = 4.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color.White, Silver)))
                            .border(0.5.dp, SilverDeepHalf, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            HairlineDivider()
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

private val SilverDeepHalf = InkSoft.copy(alpha = 0.3f)

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

/** Parlak jel buton. primary = aqua, degilse gumus/krom. */
@Composable
fun GelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    height: Dp = 52.dp
) {
    val shape = RoundedCornerShape(50)
    val colors = if (primary) GelColors(AquaLight, AquaDeep) else GelSilver
    Box(
        modifier = modifier
            .height(height)
            .shadow(if (primary) 8.dp else 3.dp, shape, spotColor = AquaDeep)
            .gel(colors, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        GelShine(cornerRadius = height / 2)
        Text(
            text = text,
            fontFamily = Michroma,
            fontSize = 12.sp,
            letterSpacing = 1.6.sp,
            color = if (primary) Color.White else Ink,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

/** Parlak jel not rozeti. */
@Composable
fun GelBadge(grade: Char, colors: GelColors, size: Dp = 44.dp) {
    val corner = size / 3.2f
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = Modifier
            .size(size)
            .shadow(size / 6, shape, spotColor = colors.bottom, ambientColor = colors.bottom)
            .gel(colors, shape),
        contentAlignment = Alignment.Center
    ) {
        GelShine(cornerRadius = corner)
        Text(
            text = grade.toString(),
            fontFamily = Michroma,
            fontSize = (size.value * 0.42f).sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

/** Aqua jel dolgulu kapsul ilerleme cubugu (0f..1f). */
@Composable
fun GelProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Silver, SilverLight)))
            .border(1.dp, Hairline, shape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                .gel(GelColors(AquaLight, Aqua), shape)
        ) {
            GelShine(cornerRadius = 7.dp)
        }
    }
}

/** HUD satiri: solda kucuk etiket, sagda deger, altinda ince cizgi. */
@Composable
fun HudRow(label: String, value: String, valueColor: Color = Ink) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = HudLabel, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall.copy(color = valueColor))
    }
    HairlineDivider()
}

/**
 * Tarama sirasinda akan dalga grafigi (referans gorseldeki osiloskop).
 * Iki sinus dalgasi faz kaydirilarak cizilir; [active] false ise duz cizgi.
 */
@Composable
fun SignalWaveform(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), SkyMid.copy(alpha = 0.6f))))
            .border(1.dp, Hairline, RoundedCornerShape(10.dp))
    ) {
        // Izgara
        val cols = 12
        val rows = 4
        for (i in 1 until cols) {
            val x = size.width * i / cols
            drawLine(Hairline, Offset(x, 0f), Offset(x, size.height), 1f)
        }
        for (j in 1 until rows) {
            val y = size.height * j / rows
            drawLine(Hairline, Offset(0f, y), Offset(size.width, y), 1f)
        }

        fun wave(amplitude: Float, frequency: Float, shift: Float, color: Color, stroke: Float) {
            val path = Path()
            val mid = size.height / 2
            val steps = 80
            for (s in 0..steps) {
                val x = size.width * s / steps
                val t = s.toFloat() / steps
                val y = if (active) mid + amplitude * size.height * sin(t * frequency * 2 * PI.toFloat() + phase + shift) else mid
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = stroke))
        }
        wave(0.32f, 2f, 0f, Aqua, 2.5f)
        wave(0.18f, 3.5f, 1.3f, AquaLight, 1.5f)
    }
}

/** Uygulama ikonu icin parlak beyaz/gumus cerceve (ikonu "vitrine" koyar). */
@Composable
fun IconFrame(size: Dp, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(size / 4)
    Box(
        modifier = Modifier
            .size(size)
            .shadow(4.dp, shape, spotColor = AquaDeep)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White, SilverLight, Silver)))
            .border(1.dp, Color.White, shape),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Kucuk kategori etiketi (ADVERTISING, ANALYTICS ...), jel renginde. */
@Composable
fun GelTag(text: String, colors: GelColors) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .gel(colors, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, fontFamily = Michroma, fontSize = 8.sp, letterSpacing = 1.sp, color = Color.White)
    }
}
