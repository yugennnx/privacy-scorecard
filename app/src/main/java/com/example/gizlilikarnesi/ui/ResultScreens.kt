package com.example.gizlilikarnesi.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.gizlilikarnesi.analysis.AppReport
import com.example.gizlilikarnesi.analysis.EarlyContacts
import com.example.gizlilikarnesi.analysis.ReportSummarizer
import com.example.gizlilikarnesi.analysis.PrivacyGrader
import com.example.gizlilikarnesi.analysis.ScanRecord
import com.example.gizlilikarnesi.analysis.TrackerDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.gizlilikarnesi.analysis.ScanResults
import com.example.gizlilikarnesi.ui.theme.AquaDeep
import com.example.gizlilikarnesi.ui.theme.GelA
import com.example.gizlilikarnesi.ui.theme.GelB
import com.example.gizlilikarnesi.ui.theme.GelC
import com.example.gizlilikarnesi.ui.theme.GelColors
import com.example.gizlilikarnesi.ui.theme.GelD
import com.example.gizlilikarnesi.ui.theme.GelF
import com.example.gizlilikarnesi.ui.theme.GelSilver
import com.example.gizlilikarnesi.ui.theme.HudLabel
import com.example.gizlilikarnesi.ui.theme.Ink
import com.example.gizlilikarnesi.ui.theme.InkFaint
import com.example.gizlilikarnesi.ui.theme.InkSoft
import com.example.gizlilikarnesi.ui.theme.Silver

// ---------------------------------------------------------------------------
// Not ve kategori -> renk / etiket eslemeleri
// ---------------------------------------------------------------------------

fun gradeGel(grade: Char): GelColors = when (grade) {
    'A' -> GelA
    'B' -> GelB
    'C' -> GelC
    'D' -> GelD
    else -> GelF
}

/** Kac segmentlik tehdit gostergesi dolu olacak (A=0 ... F=5). */
private fun threatLevel(grade: Char): Int = when (grade) {
    'A' -> 0
    'B' -> 1
    'C' -> 2
    'D' -> 3
    else -> 5
}

// ---------------------------------------------------------------------------
// Uygulama adi ve ikonu
// ---------------------------------------------------------------------------

/** Ekranda gosterilecek uygulama adi ve ikonu (paket adindan bulunur). */
data class AppDisplayInfo(val label: String, val icon: ImageBitmap?)

/**
 * PackageManager'dan uygulamanin gorunen adini ve ikonunu okur. remember()
 * sayesinde her paket icin bir kez yapilir, liste her guncellendiginde
 * tekrar okunmaz.
 */
@Composable
fun rememberAppInfo(packageName: String): AppDisplayInfo {
    val context = LocalContext.current
    return remember(packageName) {
        if (packageName == ScanResults.UNKNOWN_APP) {
            AppDisplayInfo("Bilinmeyen / Sistem", null)
        } else {
            runCatching {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                AppDisplayInfo(
                    label = pm.getApplicationLabel(info).toString(),
                    icon = pm.getApplicationIcon(info).toBitmap(128, 128).asImageBitmap()
                )
            }.getOrElse { AppDisplayInfo(packageName, null) }
        }
    }
}

@Composable
fun AppIcon(info: AppDisplayInfo, size: Dp = 46.dp) {
    IconFrame(size) {
        if (info.icon != null) {
            Image(bitmap = info.icon, contentDescription = null, modifier = Modifier.size(size * 0.72f))
        } else {
            Text("?", style = MaterialTheme.typography.headlineSmall.copy(color = InkSoft))
        }
    }
}

// ---------------------------------------------------------------------------
// Ana ekran
// ---------------------------------------------------------------------------

@Composable
fun HomeScreen(
    scanning: Boolean,
    remainingSeconds: Int,
    totalSeconds: Int,
    reports: List<AppReport>,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onAppClick: (AppReport) -> Unit,
    modifier: Modifier = Modifier,
    lastScan: ScanRecord? = null,
    onExportClick: () -> Unit = {},
    history: List<ScanRecord> = emptyList(),
    onOpenScan: (ScanRecord) -> Unit = {},
    onShowWelcome: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item { Header(scanning, onShowWelcome) }
        item {
            ScanControlPanel(
                scanning = scanning,
                remainingSeconds = remainingSeconds,
                totalSeconds = totalSeconds,
                onStartClick = onStartClick,
                onStopClick = onStopClick
            )
        }
        if (reports.isNotEmpty()) {
            item { SessionSummaryPanel(reports, lastScan = if (scanning) null else lastScan, onExportClick = onExportClick) }
        }
        item {
            GlassPanel(label = "UYGULAMALAR", trailingLabel = "%02d".format(reports.size)) {
                if (reports.isEmpty()) {
                    Text(
                        if (scanning) "Ağ etkinliği bekleniyor..." else "Henüz veri yok. Taramayı başlat, sonra incelemek istediğin uygulamaları kullan.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    reports.forEachIndexed { index, report ->
                        SubjectRow(report = report, onClick = { onAppClick(report) })
                        if (index < reports.lastIndex) HairlineDivider()
                    }
                }
            }
        }
        if (!scanning && history.isNotEmpty()) {
            item { HistoryPanel(history, currentId = lastScan?.id, onOpenScan = onOpenScan) }
        }
    }
}

@Composable
private fun Header(scanning: Boolean, onShowWelcome: () -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)) {
        // Durum kapsulu basligin yaninda degil altinda: "SCANNING" genisleyince
        // basligi iki satira kirmasin.
        Text("GİZLİLİK KARNESİ", style = MaterialTheme.typography.headlineMedium, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text("UYGULAMALARIN SENİ KİMLERE ANLATTIĞINI GÖR", style = HudLabel.copy(color = AquaDeep))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(scanning)
            Spacer(Modifier.weight(1f))
            Text(
                "Nasıl çalışır?",
                style = MaterialTheme.typography.bodyMedium.copy(color = AquaDeep),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onShowWelcome)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Kayitli taramalar: tarih, uygulama sayisi ve en kotu not. Dokununca o
 * tarama ekrana yuklenir (notlar GUNCEL tracker listesiyle yeniden hesaplanir).
 */
@Composable
private fun HistoryPanel(history: List<ScanRecord>, currentId: String?, onOpenScan: (ScanRecord) -> Unit) {
    val context = LocalContext.current
    val rows = remember(history) {
        val db = TrackerDatabase.load(context)
        val format = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr"))
        history.map { record ->
            val worst = record.observed
                .map { (pkg, domains) -> PrivacyGrader.grade(pkg, domains, db) }
                .maxByOrNull { it.score }
            Triple(record, format.format(Date(record.startedAtMillis)), worst?.grade ?: 'A')
        }
    }
    GlassPanel(label = "GEÇMİŞ TARAMALAR", trailingLabel = "%02d".format(history.size)) {
        rows.forEachIndexed { index, (record, time, worst) ->
            val current = record.id == currentId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenScan(record) }
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(time, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${record.observed.size} uygulama · ${record.durationSeconds} sn" +
                            if (current) " · gösteriliyor" else "",
                        style = MaterialTheme.typography.bodySmall.copy(color = if (current) AquaDeep else InkSoft)
                    )
                }
                GelBadge(worst, gradeGel(worst), size = 36.dp)
            }
            if (index < rows.lastIndex) HairlineDivider()
        }
    }
}

/** "● SCANNING" / "● IDLE" durum kapsulu. */
@Composable
private fun StatusPill(scanning: Boolean) {
    val dot = if (scanning) GelB else GelSilver
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(dot.top, dot.bottom)))
        )
        Spacer(Modifier.width(6.dp))
        Text(if (scanning) "TARANIYOR" else "HAZIR", style = HudLabel.copy(color = if (scanning) AquaDeep else InkSoft))
    }
}

@Composable
private fun ScanControlPanel(
    scanning: Boolean,
    remainingSeconds: Int,
    totalSeconds: Int,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    GlassPanel(label = "TARAMA", trailingLabel = if (scanning) "CANLI" else null) {
        if (scanning) {
            HudRow("DURUM", "DNS izleniyor", valueColor = AquaDeep)
            HudRow("KALAN SÜRE", "$remainingSeconds sn")
            Spacer(Modifier.height(12.dp))
            GelProgressBar(progress = 1f - remainingSeconds.toFloat() / totalSeconds)
            Spacer(Modifier.height(12.dp))
            SignalWaveform(active = true)
            Spacer(Modifier.height(10.dp))
            Text(
                "İnternetin kesilmez. İncelemek istediğin uygulamaları açıp kullan, sonra buraya dön.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(14.dp))
            GelButton("TARAMAYI DURDUR", onClick = onStopClick, primary = false, modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                "Uygulamalarının hangi reklam ve analitik şirketleriyle konuştuğunu bulur, her uygulamaya A ile F arasında not verir.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(14.dp))
            SignalWaveform(active = false)
            Spacer(Modifier.height(14.dp))
            GelButton("TARAMAYI BAŞLAT  ·  ${totalSeconds} sn", onClick = onStartClick, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Tum taramanin ozeti: kac uygulama, kac farkli takip sirketi, en kotu not. */
@Composable
private fun SessionSummaryPanel(
    reports: List<AppReport>,
    lastScan: ScanRecord?,
    onExportClick: () -> Unit
) {
    val companies = reports.flatMap { it.companies.keys }.toSet()
    val connections = reports.sumOf { it.totalDomains }
    val worst = reports.maxByOrNull { it.score }
    GlassPanel(label = "TARAMA ÖZETİ") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                HudRow("UYGULAMA", reports.size.toString())
                HudRow("TAKİP ŞİRKETİ", companies.size.toString())
                HudRow("ADRES", connections.toString())
            }
            if (worst != null) {
                Spacer(Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GelBadge(worst.grade, gradeGel(worst.grade), size = 64.dp)
                    Spacer(Modifier.height(6.dp))
                    Text("EN KÖTÜ", style = HudLabel)
                }
            }
        }
        // Bitmis tarama: ne zaman yapildigi + tabloya aktarma dugmesi
        if (lastScan != null) {
            Spacer(Modifier.height(10.dp))
            HairlineDivider()
            Spacer(Modifier.height(10.dp))
            val time = remember(lastScan.id) {
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr")).format(Date(lastScan.startedAtMillis))
            }
            Text(
                "Son tarama: $time · ${lastScan.durationSeconds} sn",
                style = MaterialTheme.typography.bodySmall.copy(color = InkSoft)
            )
            Spacer(Modifier.height(10.dp))
            GelButton(
                "SONUÇLARI DIŞA AKTAR (CSV)",
                onClick = onExportClick,
                primary = false,
                height = 44.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SubjectRow(report: AppReport, onClick: () -> Unit) {
    val info = rememberAppInfo(report.packageName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(info)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(info.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                // Genis Michroma (HudLabel) yerine dar Space Grotesk: S23'te
                // "16 SIRKET · 61 ADRE..." diye kesiliyordu (bkz. gunluk bolum 30)
                "${report.companies.size} şirket · ${report.totalDomains} adres",
                style = MaterialTheme.typography.bodyMedium.copy(color = if (report.companies.isEmpty()) InkFaint else InkSoft),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        GelBadge(report.grade, gradeGel(report.grade))
    }
}

// ---------------------------------------------------------------------------
// Detay ekrani: bir uygulamanin "dosyasi"
// ---------------------------------------------------------------------------

@Composable
fun AppDetailScreen(
    report: AppReport,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    firstSeen: Map<String, Long> = emptyMap(),
    scanStartedAt: Long? = null,
    blockedCompanies: Set<String> = emptySet(),
    onToggleBlock: (String) -> Unit = {}
) {
    val info = rememberAppInfo(report.packageName)
    val early = remember(report, firstSeen) { EarlyContacts.analyze(report, firstSeen) }
    val profile = rememberAdvertiserProfile(report, firstSeen, scanStartedAt)
    val summary = remember(report, info.label) { ReportSummarizer.summarize(report, info.label) }
    val ownDomains = report.firstPartyDomains.sorted()
    // Saglayici -> o saglayicidaki domainler (or. "Amazon CloudFront" -> [...])
    val infrastructure = report.infrastructureProviders.entries
        .groupBy({ it.value }, { it.key })
        .toSortedMap()
    val otherDomains = report.unclassifiedDomains.sorted()
    // Adresleri acilmis sirketler (sirkete dokununca adresleri gorunur)
    var expanded by remember(report.packageName) { mutableStateOf(setOf<String>()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GelButton("‹  GERİ", onClick = onBack, primary = false, height = 38.dp)
                Spacer(Modifier.weight(1f))
                Text("UYGULAMA DOSYASI", style = HudLabel.copy(color = AquaDeep))
            }
        }

        item {
            GlassPanel(label = "UYGULAMA") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(info, size = 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(info.label, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(report.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(10.dp))
                    GelBadge(report.grade, gradeGel(report.grade), size = 72.dp)
                }
            }
        }

        // --- Sade ozet: tek cumle + aciklama + notun anlami ---
        item {
            GlassPanel(label = "ÖZET") {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(gradeGel(report.grade).top, gradeGel(report.grade).bottom)))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(summary.headline, style = MaterialTheme.typography.titleMedium)
                }
                if (summary.detail.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(summary.detail, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                HairlineDivider()
                HudRow("RİSK DÜZEYİ", ReportSummarizer.gradeStatus(report.grade), valueColor = gradeGel(report.grade).bottom)
                HudRow("TAKİP ŞİRKETİ", report.companies.size.toString())
                HudRow("TOPLAM BAĞLANTI", report.totalDomains.toString())
                Spacer(Modifier.height(10.dp))
                ThreatMeter(level = threatLevel(report.grade), colors = gradeGel(report.grade))
                Spacer(Modifier.height(10.dp))
                Text(summary.gradeExplanation, style = MaterialTheme.typography.bodySmall.copy(color = InkSoft))
            }
        }

        // --- Ilk saniyeler: acilir acilmaz baglanilan takip sirketleri ---
        if (early != null && report.packageName != ScanResults.UNKNOWN_APP) {
            item { EarlyContactsPanel(early) }
        }

        // --- Reklamcilarin gozunden sen ---
        if (profile != null) {
            item { AdvertiserProfilePanel(profile) }
        }

        // --- Amaca gore gruplar: her grup ayri panel ---
        if (summary.groups.isEmpty()) {
            item {
                GlassPanel(label = "TAKİP ŞİRKETLERİ") {
                    Text("Bu taramada hiçbir takip şirketi görülmedi.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        summary.groups.forEach { group ->
            item(key = "group-${group.category}") {
                GlassPanel(label = group.info.title, trailingLabel = "%02d".format(group.companies.size)) {
                    Text(
                        group.info.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    group.companies.forEachIndexed { index, contact ->
                        val isOpen = contact.company in expanded
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expanded = if (isOpen) expanded - contact.company else expanded + contact.company
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            val isBlocked = contact.company in blockedCompanies
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(contact.company, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                if (isBlocked) {
                                    GelTag("ENGELLİ", GelSilver)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    "${contact.domains.size} adres  ${if (isOpen) "▴" else "▾"}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = InkSoft)
                                )
                            }
                            if (isOpen) {
                                Spacer(Modifier.height(4.dp))
                                contact.domains.forEach { domain ->
                                    Text(domain, style = MaterialTheme.typography.bodySmall.copy(color = Ink), modifier = Modifier.padding(vertical = 2.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                                GelButton(
                                    if (isBlocked) "ENGELİ KALDIR" else "BU ŞİRKETİ ENGELLE",
                                    onClick = { onToggleBlock(contact.company) },
                                    primary = !isBlocked,
                                    height = 38.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    if (isBlocked) "Tarama açıkken bu şirketin adreslerine \"böyle bir adres yok\" cevabı verilir."
                                    else "Engelleme tarama açıkken çalışır. Bazı uygulamalarda reklamlar ya da ödüllü videolar açılmayabilir.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        if (index < group.companies.lastIndex) HairlineDivider()
                    }
                }
            }
        }

        if (ownDomains.isNotEmpty()) {
            item {
                GlassPanel(label = "KENDİ SUNUCULARI", trailingLabel = "%02d".format(ownDomains.size)) {
                    Text(
                        "Uygulamayı yapan şirketin kendi sunucuları. Nota etki etmez.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    ownDomains.forEach { domain ->
                        Text(domain, style = MaterialTheme.typography.bodySmall.copy(color = Ink), modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            }
        }

        if (infrastructure.isNotEmpty()) {
            item {
                GlassPanel(label = "ALTYAPI", trailingLabel = "%02d".format(report.infrastructureProviders.size)) {
                    Text(
                        "Bulut depolama ve içerik dağıtım ağları. Nota etki etmez.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    infrastructure.entries.forEachIndexed { index, (provider, domains) ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(provider, style = MaterialTheme.typography.titleSmall)
                            Text(
                                domains.sorted().joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (index < infrastructure.size - 1) HairlineDivider()
                    }
                }
            }
        }

        item {
            GlassPanel(label = "TANIMLANAMAYANLAR", trailingLabel = "%02d".format(otherDomains.size)) {
                if (otherDomains.isEmpty()) {
                    Text("Yok.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "Henüz tanımlanamadı. Uygulamanın iş ortakları ya da listemizde olmayan takip şirketleri olabilir.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    otherDomains.forEach { domain ->
                        Text(domain, style = MaterialTheme.typography.bodySmall.copy(color = Ink), modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            }
        }
    }
}

/**
 * Bes segmentli tehdit gostergesi (referans gorseldeki yildizli "threat
 * assessment" satirinin Aero yorumu): dolu segmentler notun jel renginde.
 */
@Composable
private fun ThreatMeter(level: Int, colors: GelColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(5) { i ->
            val filled = i < level
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (filled) Brush.verticalGradient(listOf(colors.top, colors.bottom))
                        else Brush.verticalGradient(listOf(Color.White, Silver))
                    )
            )
        }
    }
}
