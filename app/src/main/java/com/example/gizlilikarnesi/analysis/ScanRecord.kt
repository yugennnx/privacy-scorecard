package com.example.gizlilikarnesi.analysis

/**
 * Bitmis bir taramanin HAM kaydi. Bilerek karneleri (notlari) degil ham
 * gozlemi (uygulama -> domainler) sakliyoruz: tracker listesi buyudukce eski
 * taramalar yeniden hesaplanip daha dogru sonuc verir.
 */
data class ScanRecord(
    /** Dosya adi ve CSV'de kullanilan kimlik, or. "20261120-103331" */
    val id: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    /** Paket adi -> o uygulamanin sordugu domainler */
    val observed: Map<String, Set<String>>,
    /** Uygulamaya atanabilen / atanamayan DNS sorgusu sayisi */
    val attributedQueries: Int = 0,
    val unattributedQueries: Int = 0,
    /** DNS iletim istatistigi (olcum kalitesi icin) */
    val dnsAnswered: Int = 0,
    val dnsFailed: Int = 0
) {
    val durationSeconds: Long get() = (endedAtMillis - startedAtMillis) / 1000
}

/**
 * Tarama sonuclarini CSV'ye (Excel/Google Sheets/Python ile acilabilen
 * tablo) cevirir. Her satir = bir uygulamanin baglandigi bir domain.
 * Saf Kotlin: Android'e bagimli degil, unit test ile kontrol edilir.
 */
object ScanCsv {

    val HEADER = listOf(
        "scan_id", "app_package", "app_grade", "app_score",
        "domain", "group", "company", "category", "provider"
    )

    /** Domainin dort gruptan hangisine dustugu (PrivacyGrader ile ayni oncelik). */
    fun groupOf(report: AppReport, domain: String): String = when {
        domain in report.trackerOwners -> "tracker"
        domain in report.firstPartyDomains -> "first_party"
        domain in report.infrastructureProviders -> "infrastructure"
        else -> "unclassified"
    }

    fun build(record: ScanRecord, database: TrackerDatabase): String {
        val lines = mutableListOf(HEADER.joinToString(","))
        val reports = record.observed
            .map { (pkg, domains) -> PrivacyGrader.grade(pkg, domains, database) }
            .sortedBy { it.packageName }
        for (report in reports) {
            for (domain in report.allDomains.sorted()) {
                val row = listOf(
                    record.id,
                    report.packageName,
                    report.grade.toString(),
                    report.score.toString(),
                    domain,
                    groupOf(report, domain),
                    report.trackerOwners[domain] ?: "",
                    report.trackerCategories[domain]?.name ?: "",
                    report.infrastructureProviders[domain] ?: ""
                )
                lines += row.joinToString(",") { escape(it) }
            }
        }
        return lines.joinToString("\n") + "\n"
    }

    /** CSV kurali: virgul, tirnak veya satir sonu iceren alan tirnak icine alinir. */
    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
}
