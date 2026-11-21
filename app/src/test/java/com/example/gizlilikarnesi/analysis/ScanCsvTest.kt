package com.example.gizlilikarnesi.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanCsvTest {

    private val db = TrackerDatabase(
        listOf(TrackerInfo("doubleclick.net", "Google", TrackerCategory.ADVERTISING)),
        mapOf("cloudfront.net" to "Amazon CloudFront")
    )

    private val record = ScanRecord(
        id = "20261120-103331",
        startedAtMillis = 0L,
        endedAtMillis = 60_000L,
        observed = mapOf(
            "com.easybrain.brick.balls.blast" to setOf(
                "stats.g.doubleclick.net", "cfg.easybrain.com", "x.cloudfront.net", "api.firwinds.site"
            )
        )
    )

    @Test
    fun herDomainBirSatirVeDogruGrup() {
        val lines = ScanCsv.build(record, db).trim().lines()
        assertEquals(ScanCsv.HEADER.joinToString(","), lines[0])
        assertEquals(5, lines.size) // baslik + 4 domain
        assertTrue(lines.contains(
            "20261120-103331,com.easybrain.brick.balls.blast,B,3,stats.g.doubleclick.net,tracker,Google,ADVERTISING,"
        ))
        assertTrue(lines.any { it.contains(",cfg.easybrain.com,first_party,") })
        assertTrue(lines.any { it.endsWith(",x.cloudfront.net,infrastructure,,,Amazon CloudFront") })
        assertTrue(lines.any { it.contains(",api.firwinds.site,unclassified,") })
    }

    @Test
    fun virgulVeTirnakIcerenAlanKacisli() {
        assertEquals("\"Microsoft, Inc\"", ScanCsv.escape("Microsoft, Inc"))
        assertEquals("\"a \"\"b\"\"\"", ScanCsv.escape("a \"b\""))
        assertEquals("duz", ScanCsv.escape("duz"))
    }

    @Test
    fun sureSaniyeOlarakHesaplanir() {
        assertEquals(60L, record.durationSeconds)
    }
}
