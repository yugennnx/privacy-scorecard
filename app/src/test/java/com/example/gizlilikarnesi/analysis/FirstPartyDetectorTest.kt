package com.example.gizlilikarnesi.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FirstPartyDetectorTest {

    @Test
    fun kayitliAdDogruBulunur() {
        assertEquals("easybrain.com", FirstPartyDetector.registrableDomain("cfg.easybrain.com"))
        assertEquals("trendyol.com.tr", FirstPartyDetector.registrableDomain("api.cdn.trendyol.com.tr"))
        assertEquals("easybrain", FirstPartyDetector.brandOf("s3-ca-crosspromo.easybrain.com"))
    }

    @Test
    fun paketAdiylaEslesenDomainKendiSunucusudur() {
        val pkg = "com.easybrain.brick.balls.blast"
        assertTrue(FirstPartyDetector.isFirstParty("cfg.easybrain.com", pkg))
        assertTrue(FirstPartyDetector.isFirstParty("consent.easybrain.com", pkg))
        assertTrue(FirstPartyDetector.isFirstParty("media.whatsappcdn.net", "com.whatsapp"))
    }

    @Test
    fun yabanciSirketKendiSunucusuSayilmaz() {
        val pkg = "com.easybrain.brick.balls.blast"
        assertFalse(FirstPartyDetector.isFirstParty("api.bidmachine.io", pkg))
        assertFalse(FirstPartyDetector.isFirstParty("edge.safedk.com", pkg))
        // "com", "android" gibi genel kelimeler eslesme sebebi olamaz
        assertFalse(FirstPartyDetector.isFirstParty("android.com", "com.android.chrome.x"))
        // UID'i bulunamayan sorgularin sahibi belli degil
        assertFalse(FirstPartyDetector.isFirstParty("cfg.easybrain.com", ScanResults.UNKNOWN_APP))
    }

    /**
     * Gercek veriyle regresyon testi: Samsung S23'teki Brick Blast taramasinda
     * "OTHER CONNECTIONS"ta gorulen domainler, yeni listeyle dogru gruplara
     * dusmeli. Gercek assets/trackers.json dosyasi okunur.
     */
    @Test
    fun brickBlastTaramasiDogruGruplanir() {
        val db = loadRealDatabase()
        val domains = setOf(
            // eskiden "diger" gorunen reklam aglari
            "api16-access-ttp.tiktokpangle.us", "api16-dual-event-sg2.pangle.io",
            "sdkapi.dsp-api.moloco.com", "cdn-adn-https.mtgglobals.com",
            "api.bidmachine.io", "cdn2.inner-active.mobi", "sac.presage.io",
            "sdkc.vervegroupinc.net", "mi.adsurge.com", "edge.safedk.com",
            "i-sdk.mediation.unity3d.com", "ae.iads.unity3d.com",
            // oyunun kendi sunuculari
            "cfg.easybrain.com", "consent.easybrain.com", "bib.easybrain.com",
            // altyapi
            "ad-host-backup-asia.oss-ap-southeast-1.aliyuncs.com",
            "firebaseinstallations.googleapis.com",
            // sahibi bilinmeyen
            "api.firwinds.site"
        )
        val report = PrivacyGrader.grade("com.easybrain.brick.balls.blast", domains, db)

        assertEquals("ByteDance (Pangle)", report.trackerOwners["api16-dual-event-sg2.pangle.io"])
        assertEquals("Mintegral", report.trackerOwners["cdn-adn-https.mtgglobals.com"])
        assertEquals("AppLovin", report.trackerOwners["edge.safedk.com"])
        assertEquals("Unity", report.trackerOwners["ae.iads.unity3d.com"])
        assertEquals(
            setOf("cfg.easybrain.com", "consent.easybrain.com", "bib.easybrain.com"),
            report.firstPartyDomains
        )
        assertEquals("Alibaba Cloud", report.infrastructureProviders["ad-host-backup-asia.oss-ap-southeast-1.aliyuncs.com"])
        assertEquals("Google Cloud", report.infrastructureProviders["firebaseinstallations.googleapis.com"])
        assertEquals(setOf("api.firwinds.site"), report.unclassifiedDomains)
        // 11 farkli reklam sirketi (+ AppLovin analitik olarak) -> F
        assertEquals('F', report.grade)
    }

    /**
     * Unit testlerde Android'in org.json'u calismaz; dosya satir satir
     * duzenli oldugu icin basit bir desenle okuyoruz.
     */
    private fun loadRealDatabase(): TrackerDatabase {
        val text = File("src/main/assets/trackers.json").readText()
        val trackerRegex = Regex(""""domain": "([^"]+)", "company": "([^"]+)", "category": "([A-Z_]+)"""")
        val infraRegex = Regex(""""domain": "([^"]+)", "provider": "([^"]+)"""")
        val trackers = trackerRegex.findAll(text).map {
            val (d, c, cat) = it.destructured
            TrackerInfo(d, c, TrackerCategory.valueOf(cat))
        }.toList()
        val infra = infraRegex.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
        assertTrue("liste okunamadi", trackers.size > 100)
        return TrackerDatabase(trackers, infra)
    }
}
