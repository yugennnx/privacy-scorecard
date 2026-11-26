package com.example.gizlilikarnesi.data

import android.content.Context
import android.util.Log
import com.example.gizlilikarnesi.analysis.ScanCsv
import com.example.gizlilikarnesi.analysis.ScanRecord
import com.example.gizlilikarnesi.analysis.ScanResults
import com.example.gizlilikarnesi.analysis.TrackerDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Taramalari telefonda kalici saklar.
 *
 * Neden Room (veritabani) degil de JSON dosyasi? Bir tarama tek seferde
 * yazilip tek seferde okunan kucuk bir belge; sorgu/iliski gerekmiyor.
 * Dosya yaklasimi yeni kutuphane eklemeden is goruyor ve dosyalar adb ile
 * dogrudan incelenebiliyor.
 *
 * Yerler:
 *  - Kayitlar: uygulamanin OZEL klasoru  files/scans/<id>.json
 *    (baska uygulamalar okuyamaz, uygulama silinince silinir)
 *  - CSV: Android/data/<paket>/files/exports/  (paylas menusuyle gonderilir)
 */
object ScanStore {

    private const val TAG = "ScanStore"

    private fun scansDir(context: Context): File =
        File(context.filesDir, "scans").apply { mkdirs() }

    fun save(context: Context, record: ScanRecord) {
        runCatching {
            val json = JSONObject()
                .put("version", 2)
                .put("id", record.id)
                .put("startedAt", record.startedAtMillis)
                .put("endedAt", record.endedAtMillis)
                .put("attributedQueries", record.attributedQueries)
                .put("unattributedQueries", record.unattributedQueries)
                .put("dnsAnswered", record.dnsAnswered)
                .put("dnsFailed", record.dnsFailed)
                .put("blockedQueries", record.blockedQueries)
                .put("firstSeen", JSONObject().apply {
                    record.firstSeen.forEach { (pkg, times) ->
                        put(pkg, JSONObject().apply { times.forEach { (d, t) -> put(d, t) } })
                    }
                })
                .put("observed", JSONObject().apply {
                    record.observed.forEach { (pkg, domains) ->
                        put(pkg, JSONArray(domains.sorted()))
                    }
                })
            File(scansDir(context), "${record.id}.json").writeText(json.toString(2))
            Log.d(TAG, "Tarama kaydedildi: ${record.id} (${record.observed.size} uygulama)")
            ScanResults.setHistory(loadAll(context))
        }.onFailure { Log.e(TAG, "Tarama kaydedilemedi", it) }
    }

    /** En son kaydedilen tarama (dosya adlari tarihle basladigi icin siralanabilir). */
    fun loadLatest(context: Context): ScanRecord? =
        scansDir(context).listFiles { f -> f.extension == "json" }
            ?.maxByOrNull { it.name }
            ?.let { load(it) }

    fun count(context: Context): Int =
        scansDir(context).listFiles { f -> f.extension == "json" }?.size ?: 0

    /** Kayitli taramalar, en yeni once (en fazla [limit] tane). */
    fun loadAll(context: Context, limit: Int = 30): List<ScanRecord> =
        scansDir(context).listFiles { f -> f.extension == "json" }
            ?.sortedByDescending { it.name }
            ?.take(limit)
            ?.mapNotNull { load(it) }
            ?: emptyList()

    private fun load(file: File): ScanRecord? = runCatching {
        val json = JSONObject(file.readText())
        val observedJson = json.getJSONObject("observed")
        val observed = observedJson.keys().asSequence().associateWith { pkg ->
            val arr = observedJson.getJSONArray(pkg)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }
        ScanRecord(
            id = json.getString("id"),
            startedAtMillis = json.getLong("startedAt"),
            endedAtMillis = json.getLong("endedAt"),
            observed = observed,
            attributedQueries = json.optInt("attributedQueries"),
            unattributedQueries = json.optInt("unattributedQueries"),
            dnsAnswered = json.optInt("dnsAnswered"),
            dnsFailed = json.optInt("dnsFailed"),
            // v1 kayitlarda yok: bos kalir, "ilk saniyeler" paneli gosterilmez
            firstSeen = json.optJSONObject("firstSeen")?.let { fs ->
                fs.keys().asSequence().associateWith { pkg ->
                    val o = fs.getJSONObject(pkg)
                    o.keys().asSequence().associateWith { o.getLong(it) }
                }
            } ?: emptyMap(),
            blockedQueries = json.optInt("blockedQueries")
        )
    }.onFailure { Log.e(TAG, "Kayit okunamadi: ${file.name}", it) }.getOrNull()

    /** Taramayi CSV dosyasi olarak yazar ve dosyayi dondurur. */
    fun exportCsv(context: Context, record: ScanRecord, database: TrackerDatabase): File {
        val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(dir, "gizlilik-karnesi-${record.id}.csv")
        file.writeText(ScanCsv.build(record, database))
        return file
    }
}
