package com.example.gizlilikarnesi.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSummaryTest {

    private val db = TrackerDatabase(
        listOf(
            TrackerInfo("doubleclick.net", "Google", TrackerCategory.ADVERTISING),
            TrackerInfo("crashlytics.com", "Google", TrackerCategory.CRASH_REPORTING),
            TrackerInfo("applovin.com", "AppLovin", TrackerCategory.ADVERTISING),
            TrackerInfo("adjust.com", "Adjust", TrackerCategory.ANALYTICS),
            TrackerInfo("graph.facebook.com", "Meta", TrackerCategory.SOCIAL)
        ),
        mapOf("cloudfront.net" to "Amazon CloudFront")
    )

    private val report = PrivacyGrader.grade(
        "com.easybrain.brick.balls.blast",
        setOf(
            "googleads.g.doubleclick.net", "firebase-settings.crashlytics.com",
            "d.applovin.com", "app.adjust.com", "graph.facebook.com",
            "cfg.easybrain.com", "x1.cloudfront.net", "api.firwinds.site"
        ),
        db
    )

    @Test
    fun sirketlerAmacaGoreGruplanirVeEnZararliOnceGelir() {
        val groups = ReportSummarizer.groupByPurpose(report)
        assertEquals(
            listOf(TrackerCategory.ADVERTISING, TrackerCategory.ANALYTICS, TrackerCategory.SOCIAL),
            groups.map { it.category }
        )
        assertEquals(listOf("AppLovin", "Google"), groups[0].companies.map { it.company })
        assertEquals("SANA REKLAM GÖSTERENLER", groups[0].info.title)
    }

    @Test
    fun sirketinAdresleriniEtiketKategorisiOnceGelecekSekildeSiralar() {
        val google = ReportSummarizer.groupByPurpose(report)[0].companies.first { it.company == "Google" }
        assertEquals(
            listOf("googleads.g.doubleclick.net", "firebase-settings.crashlytics.com"),
            google.domains
        )
    }

    @Test
    fun ozetCumlesiSadeVeDogruSayilarla() {
        val summary = ReportSummarizer.summarize(report, "Brick Blast")
        assertEquals("Brick Blast bu taramada seni 4 takip şirketine tanıttı.", summary.headline)
        assertTrue(summary.detail.startsWith("Bunların 2 tanesi reklam şirketi."))
        assertTrue(summary.detail.contains("1 bağlantı uygulamanın kendi sunucularına gitti."))
        assertTrue(summary.detail.contains("1 bağlantı bulut altyapısına gitti."))
        assertTrue(summary.detail.contains("1 bağlantının sahibi henüz bilinmiyor."))
        // 3 (reklam) + 3 (reklam) + 2 (analitik) + 2 (sosyal) = 10 -> D
        assertTrue(summary.gradeExplanation.startsWith("D notu: yoğun takip."))
        assertTrue(summary.gradeExplanation.contains("10 puan"))
    }

    @Test
    fun tarayiciIcinAciklamaNotuEklenir() {
        val chrome = PrivacyGrader.grade("com.android.chrome", setOf("stats.g.doubleclick.net"), db)
        val summary = ReportSummarizer.summarize(chrome, "Chrome")
        assertTrue(summary.detail.startsWith("Bu bir tarayıcı:"))
    }

    @Test
    fun takipYoksaOlumluOzet() {
        val clean = PrivacyGrader.grade("com.ornek", setOf("www.wikipedia.org"), db)
        val summary = ReportSummarizer.summarize(clean, "Örnek")
        assertEquals("Örnek bu taramada hiçbir takip şirketine bağlanmadı.", summary.headline)
        assertTrue(summary.groups.isEmpty())
        assertTrue(summary.gradeExplanation.startsWith("A notu: en iyi seviye."))
    }
}
