package com.example.gizlilikarnesi.analysis

/** Tek bir uygulamanin tarama sonucu karnesi. */
data class AppReport(
    val packageName: String,
    val grade: Char,
    val score: Int,
    /** Sirket adi -> o sirket icin sayilan (en agir) kategori */
    val companies: Map<String, TrackerCategory>,
    /** Tracker'a eslesen domain -> sahibi olan sirket (detay ekrani icin) */
    val trackerOwners: Map<String, String>,
    /** Uygulamanin sordugu tum domainler (tracker olsun olmasin) */
    val allDomains: Set<String>,
    /** Tracker domaini -> o domainin kendi kategorisi (ayni sirketin farkli isleri icin) */
    val trackerCategories: Map<String, TrackerCategory> = emptyMap(),
    /** Uygulamanin kendi sirketine ait sunucular (or. Brick Blast icin easybrain.com) */
    val firstPartyDomains: Set<String> = emptySet(),
    /** Bulut/CDN domaini -> saglayici (or. "x.cloudfront.net" -> "Amazon CloudFront") */
    val infrastructureProviders: Map<String, String> = emptyMap()
) {
    val trackerDomains: Set<String> get() = trackerOwners.keys
    val totalDomains: Int get() = allDomains.size

    /** Hicbir gruba girmeyen, gercekten tanimlanamayan baglantilar. */
    val unclassifiedDomains: Set<String>
        get() = allDomains - trackerDomains - firstPartyDomains - infrastructureProviders.keys
}

/**
 * Kural tabanli notlandirma motoru.
 *
 * Puanlama SIRKET basina yapilir, domain basina degil: Google'in 5 farkli
 * reklam domaini 5 ayri sirket degildir. Bir sirket birden fazla kategoride
 * gorulurse (or. Google hem reklam hem analitik) en agir kategorisi sayilir,
 * boylece ayni sirket iki kez cezalandirilmaz.
 *
 * Her domain TEK bir gruba girer, oncelik sirasi:
 *   1) tracker  2) uygulamanin kendi sunucusu  3) altyapi  4) siniflandirilamayan
 * Not SADECE tracker'lardan hesaplanir; diger gruplar bilgi amaclidir.
 */
object PrivacyGrader {

    /** Puan esikleri: puan <= deger ise o not. Bu esikler ayarlanabilir. */
    private val thresholds = listOf(
        0 to 'A',
        3 to 'B',
        6 to 'C',
        10 to 'D'
    )

    fun gradeForScore(score: Int): Char =
        thresholds.firstOrNull { score <= it.first }?.second ?: 'F'

    fun grade(packageName: String, domains: Set<String>, database: TrackerDatabase): AppReport {
        val companies = mutableMapOf<String, TrackerCategory>()
        val trackerOwners = mutableMapOf<String, String>()
        val trackerCategories = mutableMapOf<String, TrackerCategory>()
        val firstParty = mutableSetOf<String>()
        val infrastructure = mutableMapOf<String, String>()

        for (domain in domains) {
            val tracker = database.match(domain)
            if (tracker != null) {
                trackerOwners[domain] = tracker.company
                trackerCategories[domain] = tracker.category
                val previous = companies[tracker.company]
                if (previous == null || tracker.category.weight > previous.weight) {
                    companies[tracker.company] = tracker.category
                }
                continue
            }
            if (FirstPartyDetector.isFirstParty(domain, packageName)) {
                firstParty += domain
                continue
            }
            database.matchInfrastructure(domain)?.let { infrastructure[domain] = it }
        }

        val score = companies.values.sumOf { it.weight }
        return AppReport(
            packageName = packageName,
            grade = gradeForScore(score),
            score = score,
            companies = companies,
            trackerOwners = trackerOwners,
            allDomains = domains,
            trackerCategories = trackerCategories,
            firstPartyDomains = firstParty,
            infrastructureProviders = infrastructure
        )
    }
}
