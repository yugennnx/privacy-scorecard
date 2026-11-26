package com.example.gizlilikarnesi.analysis

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Servis (VPN) ile ekran (MainActivity) arasindaki kopru.
 *
 * Servis her yeni domain gordugunde karneleri yeniden hesaplayip buraya
 * yazar; ekran StateFlow'u dinledigi icin liste TARAMA SIRASINDA canli
 * guncellenir. Ikisi ayni uygulama surecinde calistigi icin basit bir
 * `object` (tekil nesne) yeterli.
 *
 * Tarama bitince ham kayit (ScanRecord) data/ScanStore ile diske yazilir;
 * uygulama yeniden acilinca son tarama buradan tekrar yayinlanir.
 */
object ScanResults {

    /** UID'i cozulemeyen sorgular (genelde sistem bilesenleri) bu adla toplanir. */
    const val UNKNOWN_APP = "bilinmiyor"

    private val _reports = MutableStateFlow<List<AppReport>>(emptyList())
    val reports: StateFlow<List<AppReport>> = _reports.asStateFlow()

    /** Ekranda gosterilen sonuclarin ait oldugu bitmis tarama (tarama surerken null). */
    private val _lastScan = MutableStateFlow<ScanRecord?>(null)
    val lastScan: StateFlow<ScanRecord?> = _lastScan.asStateFlow()

    /** Diskteki kayitli taramalar (en yeni once) -- "Gecmis taramalar" paneli icin. */
    private val _history = MutableStateFlow<List<ScanRecord>>(emptyList())
    val history: StateFlow<List<ScanRecord>> = _history.asStateFlow()

    fun setHistory(records: List<ScanRecord>) {
        _history.value = records
    }

    fun clear() {
        _reports.value = emptyList()
        _lastScan.value = null
    }

    /** En kotu not en ustte olacak sekilde siralanmis karneleri yayinlar. */
    fun publish(observed: Map<String, Set<String>>, database: TrackerDatabase) {
        _reports.value = observed
            .map { (pkg, domains) -> PrivacyGrader.grade(pkg, domains, database) }
            .sortedWith(compareByDescending<AppReport> { it.score }.thenBy { it.packageName })
    }

    /** Bitmis (veya diskten yuklenmis) bir taramayi ekrana yayinlar. */
    fun publishRecord(record: ScanRecord, database: TrackerDatabase) {
        publish(record.observed, database)
        _lastScan.value = record
    }
}
