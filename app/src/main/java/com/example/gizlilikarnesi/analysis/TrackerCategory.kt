package com.example.gizlilikarnesi.analysis

/**
 * Tracker kategorileri ve her birinin gizlilik notuna etkisi (puan).
 * Puan ne kadar yuksekse kullanici icin o kadar "zararli" sayilir:
 * reklam ve parmak izi takibi kisiyi profilleyip izler (3), analitik ve
 * sosyal medya SDK'lari davranis verisi toplar (2), hata raporlama ise
 * cogunlukla teknik veri gonderir (1).
 */
enum class TrackerCategory(val weight: Int, val displayName: String) {
    ADVERTISING(3, "Reklam"),
    FINGERPRINTING(3, "Parmak izi"),
    ANALYTICS(2, "Analitik"),
    SOCIAL(2, "Sosyal medya"),
    CRASH_REPORTING(1, "Hata raporlama");
}
