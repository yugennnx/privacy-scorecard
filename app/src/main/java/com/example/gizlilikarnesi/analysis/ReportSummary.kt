package com.example.gizlilikarnesi.analysis

/**
 * Karneyi sade Turkce'ye ceviren katman. Ekrandan (Compose) bagimsiz tutuldu
 * ki unit test ile metinler ve gruplar kontrol edilebilsin.
 *
 * Iki is yapar:
 *  1) Ozet: tek cumlelik manset + notun ne demek oldugunun aciklamasi
 *  2) Amaca gore gruplama: sirketleri domain listesi yerine "ne yaptiklarina"
 *     gore toplar (sana reklam gosterenler, kullanimini olcenler...)
 */

/** Bir amac grubunun kullaniciya gosterilen adi ve sade aciklamasi. */
data class PurposeInfo(val title: String, val explanation: String)

/** Gruptaki tek bir sirket ve baglanilan adresleri. */
data class CompanyContact(val company: String, val domains: List<String>)

/** Ayni amaca hizmet eden sirketler. */
data class PurposeGroup(
    val category: TrackerCategory,
    val info: PurposeInfo,
    val companies: List<CompanyContact>
)

data class ReportSummary(
    val headline: String,
    val detail: String,
    val gradeExplanation: String,
    val groups: List<PurposeGroup>
)

object ReportSummarizer {

    /** Yaygin tarayicilarin paket adlari. */
    private val knownBrowsers = setOf(
        "com.android.chrome", "org.mozilla.firefox", "com.sec.android.app.sbrowser",
        "com.opera.browser", "com.microsoft.emmx", "com.brave.browser",
        "com.yandex.browser", "com.duckduckgo.mobile.android"
    )

    fun purposeInfo(category: TrackerCategory): PurposeInfo = when (category) {
        TrackerCategory.ADVERTISING -> PurposeInfo(
            "SANA REKLAM GÖSTERENLER",
            "Hangi reklamı göreceğine karar vermek için cihaz bilgilerini ve reklam kimliğini kullanırlar."
        )
        TrackerCategory.FINGERPRINTING -> PurposeInfo(
            "CİHAZINI TANIYANLAR",
            "Telefonunu ayırt edici özelliklerinden tanıyıp seni farklı uygulamalarda izleyebilirler."
        )
        TrackerCategory.ANALYTICS -> PurposeInfo(
            "KULLANIMINI ÖLÇENLER",
            "Uygulamada ne yaptığını, ne kadar kaldığını ve hangi reklamdan geldiğini ölçerler."
        )
        TrackerCategory.SOCIAL -> PurposeInfo(
            "SOSYAL MEDYA",
            "Bu uygulamadaki etkinliğin sosyal medya şirketine iletilebilir."
        )
        TrackerCategory.CRASH_REPORTING -> PurposeInfo(
            "HATA RAPORLAYANLAR",
            "Uygulama çöktüğünde cihaz bilgisiyle birlikte hata raporu gönderirler. En az riskli grup."
        )
    }

    /** Notun tek kelimelik Turkce karsiligi. */
    fun gradeStatus(grade: Char): String = when (grade) {
        'A' -> "TEMİZ"
        'B' -> "DÜŞÜK"
        'C' -> "ORTA"
        'D' -> "YÜKSEK"
        else -> "CİDDİ"
    }

    private fun gradeMeaning(grade: Char): String = when (grade) {
        'A' -> "en iyi seviye"
        'B' -> "az takip"
        'C' -> "orta düzeyde takip"
        'D' -> "yoğun takip"
        else -> "en kötü seviye"
    }

    fun summarize(report: AppReport, appLabel: String): ReportSummary {
        val groups = groupByPurpose(report)
        val companyCount = report.companies.size
        val adCount = report.companies.values.count { it == TrackerCategory.ADVERTISING }

        val headline = if (companyCount == 0) {
            "$appLabel bu taramada hiçbir takip şirketine bağlanmadı."
        } else {
            "$appLabel bu taramada seni $companyCount takip şirketine tanıttı."
        }

        val detail = buildList {
            // Tarayicida baglantilarin cogu tarayicinin kendisinden degil,
            // ziyaret edilen web sitelerinden gelir; bunu belirtmek adil olur.
            if (report.packageName in knownBrowsers) {
                add("Bu bir tarayıcı: bağlantıların çoğu ziyaret ettiğin web sitelerinden geliyor.")
            }
            if (adCount > 0) add("Bunların $adCount tanesi reklam şirketi.")
            val own = report.firstPartyDomains.size
            val infra = report.infrastructureProviders.size
            val unknown = report.unclassifiedDomains.size
            if (own > 0) add("$own bağlantı uygulamanın kendi sunucularına gitti.")
            if (infra > 0) add("$infra bağlantı bulut altyapısına gitti.")
            if (unknown > 0) add("$unknown bağlantının sahibi henüz bilinmiyor.")
        }.joinToString(" ")

        val gradeExplanation =
            "${report.grade} notu: ${gradeMeaning(report.grade)}. " +
                "Her reklam ve parmak izi şirketi 3, analitik ve sosyal medya 2, " +
                "hata raporlama 1 puan ekler. Bu uygulama ${report.score} puan aldı " +
                "(0 = A, 3'e kadar B, 6'ya kadar C, 10'a kadar D, üstü F)."

        return ReportSummary(headline, detail, gradeExplanation, groups)
    }

    /**
     * Sirketleri sayildiklari (en agir) kategoriye gore gruplar. Gruplar en
     * zararlidan baslar; grup icinde sirketler alfabetik.
     */
    fun groupByPurpose(report: AppReport): List<PurposeGroup> =
        report.companies.entries
            .groupBy({ it.value }, { it.key })
            .map { (category, companies) ->
                PurposeGroup(
                    category = category,
                    info = purposeInfo(category),
                    companies = companies.sorted().map { company ->
                        CompanyContact(
                            company = company,
                            domains = report.trackerOwners.filterValues { it == company }.keys
                                .sortedWith(
                                    compareByDescending<String> { report.trackerCategories[it]?.weight ?: 0 }
                                        .thenBy { it }
                                )
                        )
                    }
                )
            }
            .sortedWith(compareByDescending<PurposeGroup> { it.category.weight }.thenBy { it.category.ordinal })
}
