package com.example.gizlilikarnesi.analysis

/**
 * "Ilk saniyeler" analizi: bir uygulamanin bu taramadaki ILK ag etkinliginden
 * sonraki kisa sure icinde baglandigi takip sirketleri.
 *
 * Neden onemli? Uygulama acilir acilmaz (cogu zaman kullanici bir onay
 * ekranina cevap vermeden once) reklam/analitik sirketlerine baglanmasi,
 * kullanicinin secim sansi olmadan veri gittigini gosterir.
 *
 * Durustluk notu: Onay ekraninin ne zaman cevaplandigini DNS'ten bilemeyiz.
 * Bu yuzden olcut ZAMANDIR ("ilk N saniye"), "onaydan once" iddiasi degildir.
 * Uygulama tarama baslamadan once zaten aciksa ilk etkinlik acilis degildir.
 */
object EarlyContacts {

    const val DEFAULT_WINDOW_MS = 10_000L

    data class Result(
        /** Ilk N saniyede baglanilan sirketler (alfabetik) */
        val companies: List<String>,
        /** Bu sirketlere ait, ilk N saniyede sorulan adres sayisi */
        val domainCount: Int,
        val windowSeconds: Long
    )

    /**
     * @param firstSeen bu uygulamanin domain -> ilk sorgu zamani (ms)
     */
    fun analyze(
        report: AppReport,
        firstSeen: Map<String, Long>,
        windowMs: Long = DEFAULT_WINDOW_MS
    ): Result? {
        if (firstSeen.isEmpty()) return null
        val start = firstSeen.values.min()
        val early = report.trackerOwners.filterKeys { domain ->
            val t = firstSeen[domain] ?: return@filterKeys false
            t - start <= windowMs
        }
        return Result(
            companies = early.values.toSortedSet().toList(),
            domainCount = early.size,
            windowSeconds = windowMs / 1000
        )
    }
}
