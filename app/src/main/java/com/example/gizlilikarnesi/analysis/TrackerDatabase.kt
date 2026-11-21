package com.example.gizlilikarnesi.analysis

import org.json.JSONObject

/** Veritabanindaki tek bir kayit: bu domain hangi sirkete ait, ne ise yariyor. */
data class TrackerInfo(
    val domain: String,
    val company: String,
    val category: TrackerCategory
)

/**
 * Domain -> tracker eslemesi. Eslestirme "sonek" (suffix) mantigiyla yapilir:
 * listede "doubleclick.net" varsa "stats.g.doubleclick.net" da eslesir, cunku
 * sirketler alt domainleri serbestce olusturur ve hepsini listelemek imkansizdir.
 *
 * Ikinci bir liste daha tutar: ALTYAPI (bulut depolama, CDN). Bunlar tracker
 * degildir; "bu adres Amazon'un bulutunda duruyor" bilgisini verir ki kullanici
 * bunlari "bilinmeyen baglanti" sanmasin.
 */
class TrackerDatabase(
    entries: List<TrackerInfo>,
    infrastructure: Map<String, String> = emptyMap()
) {

    private val byDomain: Map<String, TrackerInfo> =
        entries.associateBy { it.domain.lowercase() }

    /** Altyapi domaini -> saglayici adi (or. "cloudfront.net" -> "Amazon CloudFront") */
    private val infrastructureByDomain: Map<String, String> =
        infrastructure.mapKeys { it.key.lowercase() }

    val size: Int get() = byDomain.size

    /**
     * Domaini once tam haliyle, sonra bastan birer etiket atarak arar:
     * a.b.doubleclick.net -> b.doubleclick.net -> doubleclick.net -> net
     * Ilk bulunan (yani en spesifik) kayit doner.
     */
    fun match(domain: String): TrackerInfo? = lookupBySuffix(domain, byDomain)

    /** Domain bilinen bir altyapi saglayicisina mi ait? Saglayici adini dondurur. */
    fun matchInfrastructure(domain: String): String? = lookupBySuffix(domain, infrastructureByDomain)

    private fun <T> lookupBySuffix(domain: String, table: Map<String, T>): T? {
        var candidate = domain.lowercase().trimEnd('.')
        while (true) {
            table[candidate]?.let { return it }
            val dot = candidate.indexOf('.')
            if (dot < 0) return null
            candidate = candidate.substring(dot + 1)
        }
    }

    companion object {
        @Volatile private var shared: TrackerDatabase? = null

        /**
         * assets/trackers.json'dan yuklenen ortak liste. Servis de ekran da
         * ayni nesneyi kullanir; dosya uygulama boyunca bir kez okunur.
         */
        fun load(context: android.content.Context): TrackerDatabase =
            shared ?: synchronized(this) {
                shared ?: fromJson(
                    context.assets.open("trackers.json").bufferedReader().use { it.readText() }
                ).also { shared = it }
            }

        /**
         * assets/trackers.json formatini okur. Bilinmeyen kategoriye sahip
         * kayitlar atlanir (tum listeyi bozmak yerine). "infrastructure"
         * bolumu istege baglidir (eski v1 dosyalarinda yok).
         */
        fun fromJson(json: String): TrackerDatabase {
            val root = JSONObject(json)
            val array = root.getJSONArray("trackers")
            val entries = (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                val category = runCatching {
                    TrackerCategory.valueOf(obj.getString("category"))
                }.getOrNull() ?: return@mapNotNull null
                TrackerInfo(
                    domain = obj.getString("domain"),
                    company = obj.getString("company"),
                    category = category
                )
            }

            val infraArray = root.optJSONArray("infrastructure")
            val infrastructure = if (infraArray == null) emptyMap() else
                (0 until infraArray.length()).associate { i ->
                    val obj = infraArray.getJSONObject(i)
                    obj.getString("domain") to obj.getString("provider")
                }

            return TrackerDatabase(entries, infrastructure)
        }
    }
}
