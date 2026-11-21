package com.example.gizlilikarnesi.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivacyGraderTest {

    private val db = TrackerDatabase(
        listOf(
            TrackerInfo("doubleclick.net", "Google", TrackerCategory.ADVERTISING),
            TrackerInfo("google-analytics.com", "Google", TrackerCategory.ANALYTICS),
            TrackerInfo("appsflyer.com", "AppsFlyer", TrackerCategory.ANALYTICS),
            TrackerInfo("sentry.io", "Sentry", TrackerCategory.CRASH_REPORTING),
            TrackerInfo("graph.facebook.com", "Meta", TrackerCategory.SOCIAL)
        )
    )

    @Test
    fun altDomainSonekIleEslesir() {
        assertEquals("Google", db.match("stats.g.doubleclick.net")?.company)
        assertEquals("Google", db.match("DOUBLECLICK.NET.")?.company)
    }

    @Test
    fun benzerAmaFarkliDomainEslesmez() {
        // "notdoubleclick.net" bir alt domain degil, eslesmemeli
        assertNull(db.match("notdoubleclick.net"))
        // graph.facebook.com listede ama facebook.com'un kendisi degil
        assertNull(db.match("www.facebook.com"))
    }

    @Test
    fun trackerYoksaNotA() {
        val report = PrivacyGrader.grade("com.ornek", setOf("www.wikipedia.org"), db)
        assertEquals('A', report.grade)
        assertEquals(0, report.score)
    }

    @Test
    fun ayniSirketEnAgirKategoridenBirKezSayilir() {
        val report = PrivacyGrader.grade(
            "com.ornek",
            setOf("doubleclick.net", "ssl.google-analytics.com", "www.cnn.com"),
            db
        )
        // Google = reklam (3), analitik (2) degil; toplam 3
        assertEquals(3, report.score)
        assertEquals(TrackerCategory.ADVERTISING, report.companies["Google"])
        assertEquals('B', report.grade)
        assertEquals(2, report.trackerDomains.size)
        assertEquals(3, report.totalDomains)
    }

    @Test
    fun puanlarToplanir() {
        val report = PrivacyGrader.grade(
            "com.ornek",
            setOf("doubleclick.net", "t.appsflyer.com", "o1.ingest.sentry.io", "graph.facebook.com"),
            db
        )
        // 3 (Google) + 2 (AppsFlyer) + 1 (Sentry) + 2 (Meta) = 8
        assertEquals(8, report.score)
        assertEquals('D', report.grade)
    }

    @Test
    fun notEsikleri() {
        assertEquals('A', PrivacyGrader.gradeForScore(0))
        assertEquals('B', PrivacyGrader.gradeForScore(3))
        assertEquals('C', PrivacyGrader.gradeForScore(4))
        assertEquals('D', PrivacyGrader.gradeForScore(10))
        assertEquals('F', PrivacyGrader.gradeForScore(11))
    }
}
