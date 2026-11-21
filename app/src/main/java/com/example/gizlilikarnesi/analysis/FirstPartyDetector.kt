package com.example.gizlilikarnesi.analysis

/**
 * "Bu domain uygulamanin KENDI sirketine mi ait?" sorusunu cevaplar.
 *
 * Yontem: domainin kayitli adini (registrable domain) bulup ilk etiketini
 * ("marka") cikariyoruz, sonra paket adinin parcalariyla karsilastiriyoruz.
 *
 *   paket : com.easybrain.brick.balls.blast  -> parcalar: easybrain, brick, balls, blast
 *   domain: cfg.easybrain.com                -> kayitli ad: easybrain.com -> marka: easybrain
 *   => eslesti, "kendi sunucusu"
 *
 * Bilerek TEMKINLI: emin olamadigi durumda "hayir" der. Yanlislikla bir
 * reklam sirketini "kendi sunucusu" saymak, onu gozden kacirmaktan daha
 * kotudur. Ayrica tracker eslemesi her zaman once gelir (PrivacyGrader'da).
 */
object FirstPartyDetector {

    /**
     * Iki parcali uzantilar: "oyun.com.tr" icin kayitli ad "oyun.com.tr"dir,
     * "com.tr" degil. Tam liste (Public Suffix List) binlerce satir; burada
     * en sik gorulenler yeterli.
     */
    private val twoLevelSuffixes = setOf(
        "com.tr", "net.tr", "org.tr", "gov.tr", "edu.tr", "gen.tr", "biz.tr", "web.tr",
        "co.uk", "org.uk", "com.au", "co.jp", "co.kr", "com.br", "com.cn", "co.in", "com.mx"
    )

    /** Paket adinda olup marka belirtmeyen genel kelimeler. */
    private val genericTokens = setOf(
        "com", "org", "net", "io", "co", "tr", "android", "app", "apps", "mobile",
        "game", "games", "free", "pro", "lite", "studio", "studios", "official", "www"
    )

    private const val MIN_LENGTH = 4

    /** "a.b.example.com.tr" -> "example.com.tr";  "cfg.easybrain.com" -> "easybrain.com" */
    fun registrableDomain(domain: String): String {
        val labels = domain.lowercase().trimEnd('.').split('.').filter { it.isNotEmpty() }
        if (labels.size <= 2) return labels.joinToString(".")
        val lastTwo = labels.takeLast(2).joinToString(".")
        val keep = if (lastTwo in twoLevelSuffixes) 3 else 2
        return labels.takeLast(keep).joinToString(".")
    }

    /** Kayitli adin ilk etiketi: "easybrain.com" -> "easybrain" */
    fun brandOf(domain: String): String = registrableDomain(domain).substringBefore('.')

    private fun packageTokens(packageName: String): Set<String> =
        packageName.lowercase()
            .split('.', '_')
            .filter { it.length >= MIN_LENGTH && it !in genericTokens }
            .toSet()

    fun isFirstParty(domain: String, packageName: String): Boolean {
        if (packageName == ScanResults.UNKNOWN_APP) return false
        val brand = brandOf(domain)
        if (brand.length < MIN_LENGTH) return false
        return packageTokens(packageName).any { token ->
            token == brand ||
                // "whatsapp" paketi <-> "whatsappcdn" gibi uzantili markalar
                (token.length >= 5 && brand.startsWith(token)) ||
                (brand.length >= 5 && token.startsWith(brand))
        }
    }
}
