package com.example.gizlilikarnesi.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.telephony.TelephonyManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gizlilikarnesi.analysis.AppReport
import com.example.gizlilikarnesi.analysis.EarlyContacts
import com.example.gizlilikarnesi.analysis.ScanResults
import com.example.gizlilikarnesi.analysis.TrackerCategory
import com.example.gizlilikarnesi.ui.theme.HudLabel
import com.example.gizlilikarnesi.ui.theme.Ink
import com.example.gizlilikarnesi.ui.theme.InkSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------------------------------------------------------------------------
// "Ilk 10 saniye" paneli
// ---------------------------------------------------------------------------

@Composable
fun EarlyContactsPanel(result: EarlyContacts.Result) {
    val n = result.companies.size
    GlassPanel(label = "İLK ${result.windowSeconds} SANİYE", trailingLabel = "%02d".format(n)) {
        Text(
            if (n == 0) "İlk ${result.windowSeconds} saniyede hiçbir takip şirketine bağlanmadı."
            else "Bu taramadaki ilk ağ etkinliğinden sonraki ${result.windowSeconds} saniyede $n takip şirketine bağlandı.",
            style = MaterialTheme.typography.titleSmall
        )
        if (n > 0) {
            Spacer(Modifier.height(6.dp))
            Text(result.companies.joinToString(" · "), style = MaterialTheme.typography.bodyMedium.copy(color = Ink))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Uygulamayı tarama başladıktan sonra açtıysan bu süre, çoğu zaman bir onay ekranını " +
                "yanıtlamadan önceki süredir. Onay ekranının ne zaman yanıtlandığını DNS'ten bilemeyiz; " +
                "ölçüt yalnızca zamandır.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ---------------------------------------------------------------------------
// "Reklamcilarin gozunden sen" karti
// ---------------------------------------------------------------------------

data class AdvertiserProfile(val rows: List<Pair<String, String>>, val adCompanies: Int)

/**
 * Reklam sirketlerinin bu tur baglantilarla tipik olarak aldigi bilgilerin
 * BU TELEFONDAKI gercek degerleri. Not: degerler cihazdan okunur; trafikte
 * gonderildikleri gozlenmedi (sifreli icerik okunmaz). Sirketlerin bunlari
 * topladigi, cogunun kendi veri beyanlarina dayanir.
 */
@Composable
fun rememberAdvertiserProfile(report: AppReport, firstSeen: Map<String, Long>, scanStartedAt: Long?): AdvertiserProfile? {
    val context = LocalContext.current
    return remember(report, firstSeen, scanStartedAt) {
        if (report.packageName == ScanResults.UNKNOWN_APP) return@remember null
        val adCompanies = report.companies.values.count {
            it == TrackerCategory.ADVERTISING || it == TrackerCategory.FINGERPRINTING
        }
        if (adCompanies == 0) return@remember null
        buildProfile(context, report, firstSeen, scanStartedAt, adCompanies)
    }
}

private fun buildProfile(
    context: Context,
    report: AppReport,
    firstSeen: Map<String, Long>,
    scanStartedAt: Long?,
    adCompanies: Int
): AdvertiserProfile {
    val rows = mutableListOf<Pair<String, String>>()
    val maker = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.ROOT) }
    rows += "CİHAZ" to "$maker ${Build.MODEL}"
    rows += "İŞLETİM SİSTEMİ" to "Android ${Build.VERSION.RELEASE}"
    rows += "DİL VE SAAT DİLİMİ" to "${Locale.getDefault().toLanguageTag()} · ${TimeZone.getDefault().id}"
    runCatching {
        context.getSystemService(TelephonyManager::class.java)?.networkOperatorName
    }.getOrNull()?.takeIf { it.isNotBlank() }?.let { rows += "OPERATÖR" to it }
    rows += "YAKLAŞIK KONUM" to "IP adresinden, genellikle şehir düzeyinde"
    val active = firstSeen.values.minOrNull() ?: scanStartedAt
    if (active != null) {
        rows += "AKTİF OLDUĞUN SAAT" to SimpleDateFormat("HH:mm", Locale("tr")).format(Date(active))
    }
    appCategory(context, report.packageName)?.let { rows += "İLGİ ALANI İPUCU" to it }
    rows += "REKLAM KİMLİĞİ" to "$adCompanies reklam şirketine ulaşmış olabilir"
    return AdvertiserProfile(rows, adCompanies)
}

/** Uygulamanin Play kategorisinden basit bir "ilgi alani" ipucu. */
private fun appCategory(context: Context, packageName: String): String? = runCatching {
    when (context.packageManager.getApplicationInfo(packageName, 0).category) {
        ApplicationInfo.CATEGORY_GAME -> "Mobil oyun oynuyorsun"
        ApplicationInfo.CATEGORY_AUDIO -> "Müzik / ses uygulaması kullanıyorsun"
        ApplicationInfo.CATEGORY_VIDEO -> "Video izliyorsun"
        ApplicationInfo.CATEGORY_IMAGE -> "Fotoğraf uygulaması kullanıyorsun"
        ApplicationInfo.CATEGORY_SOCIAL -> "Sosyal medya kullanıyorsun"
        ApplicationInfo.CATEGORY_NEWS -> "Haber okuyorsun"
        ApplicationInfo.CATEGORY_MAPS -> "Harita / navigasyon kullanıyorsun"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Verimlilik uygulaması kullanıyorsun"
        else -> null
    }
}.getOrNull()

@Composable
fun AdvertiserProfilePanel(profile: AdvertiserProfile) {
    GlassPanel(label = "REKLAMCILARIN GÖZÜNDEN SEN") {
        Text(
            "Reklam şirketleri bu tür bağlantılarla genellikle aşağıdaki bilgileri alır ya da çıkarır. " +
                "Değerler bu telefonun kendisinden okundu:",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        profile.rows.forEachIndexed { index, (label, value) ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(label, style = HudLabel)
                Spacer(Modifier.height(2.dp))
                Text(value, style = MaterialTheme.typography.titleSmall.copy(color = Ink))
            }
            if (index < profile.rows.lastIndex) HairlineDivider()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tek başına önemsiz görünen bu bilgiler birleşince seni farklı uygulamalarda tanımaya yeter. " +
                "Not: Şifreli içerik okunmadığı için bu bilgilerin gönderildiği doğrudan gözlenmedi; " +
                "şirketlerin kendi veri beyanlarına dayanır.",
            style = MaterialTheme.typography.bodySmall.copy(color = InkSoft)
        )
    }
}
