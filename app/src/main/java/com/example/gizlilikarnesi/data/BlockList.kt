package com.example.gizlilikarnesi.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kullanicinin engelledigi takip SIRKETLERI (or. "Moloco").
 *
 * Sirket bazinda tutulur, domain bazinda degil: bir sirketin onlarca alt
 * adresi var, kullanici "bu sirketi istemiyorum" der. VPN servisi her DNS
 * sorgusunda buraya bakar; engelli bir sirkete ait adrese "boyle bir adres
 * yok" (NXDOMAIN) cevabi verir.
 *
 * Kalicidir (SharedPreferences): uygulama kapansa da liste kalir.
 * NOT: Engelleme yalnizca VPN (tarama) acikken calisir.
 */
object BlockList {

    private const val PREFS = "block_list"
    private const val KEY = "blocked_companies"

    private val _companies = MutableStateFlow<Set<String>>(emptySet())
    val companies: StateFlow<Set<String>> = _companies.asStateFlow()

    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        _companies.value = prefs(context).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()
        loaded = true
    }

    fun isBlocked(company: String): Boolean = company in _companies.value

    fun toggle(context: Context, company: String) {
        load(context)
        val updated = _companies.value.toMutableSet().apply {
            if (!add(company)) remove(company)
        }
        _companies.value = updated
        prefs(context).edit().putStringSet(KEY, updated).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
