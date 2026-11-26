package com.example.gizlilikarnesi.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EarlyContactsTest {

    private val db = TrackerDatabase(
        listOf(
            TrackerInfo("applovin.com", "AppLovin", TrackerCategory.ADVERTISING),
            TrackerInfo("moloco.com", "Moloco", TrackerCategory.ADVERTISING),
            TrackerInfo("adjust.com", "Adjust", TrackerCategory.ANALYTICS)
        )
    )

    private val domains = setOf("cfg.easybrain.com", "d.applovin.com", "app.adjust.com", "sdk.moloco.com")
    private val report = PrivacyGrader.grade("com.easybrain.brick.balls.blast", domains, db)

    @Test
    fun ilkOnSaniyedekiSirketlerBulunur() {
        val t0 = 1_000_000L
        val firstSeen = mapOf(
            "cfg.easybrain.com" to t0,            // ilk etkinlik (kendi sunucusu)
            "d.applovin.com" to t0 + 2_000,       // 2 sn -> erken
            "app.adjust.com" to t0 + 10_000,      // tam 10 sn -> sinirda, erken sayilir
            "sdk.moloco.com" to t0 + 25_000       // 25 sn -> gec
        )
        val result = EarlyContacts.analyze(report, firstSeen)!!
        assertEquals(listOf("Adjust", "AppLovin"), result.companies)
        assertEquals(2, result.domainCount)
        assertEquals(10L, result.windowSeconds)
    }

    @Test
    fun zamanDamgasiYoksaSonucYok() {
        // Eski (v1) kayitlarda zaman damgasi yok: panel gosterilmemeli
        assertNull(EarlyContacts.analyze(report, emptyMap()))
    }
}
