package com.example.gizlilikarnesi.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDurationTest {

    @Test
    fun saniyeDakikaSaatDogruYazilir() {
        assertEquals("0 sn", formatDuration(0))
        assertEquals("45 sn", formatDuration(45))
        assertEquals("1 dk", formatDuration(60))
        assertEquals("3 dk 12 sn", formatDuration(192))
        assertEquals("1 sa", formatDuration(3600))
        assertEquals("1 sa 5 dk", formatDuration(3600 + 5 * 60 + 30)) // saniye saatte gosterilmez
        assertEquals("0 sn", formatDuration(-5))                       // saat geri alinsa bile negatif yok
    }
}
