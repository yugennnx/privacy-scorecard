package com.example.gizlilikarnesi.capture

import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Bir baglantinin (protokol + kaynak + hedef) hangi uygulamaya ait oldugunu
 * bulur. ConnectivityManager.getConnectionOwnerUid, sadece su an aktif olan
 * VpnService'i calistiran uygulama tarafindan cagrilabilir (API 29+).
 */
class UidResolver(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val uidToPackageCache = ConcurrentHashMap<Int, String?>()

    fun resolvePackageName(
        protocol: Int,
        sourceAddress: java.net.InetAddress,
        sourcePort: Int,
        destAddress: java.net.InetAddress,
        destPort: Int
    ): String? {
        val local = InetSocketAddress(sourceAddress, sourcePort)
        val remote = InetSocketAddress(destAddress, destPort)

        val uid = try {
            connectivityManager.getConnectionOwnerUid(protocol, local, remote)
        } catch (e: Exception) {
            Process.INVALID_UID
        }

        if (uid == Process.INVALID_UID) return null

        return uidToPackageCache.getOrPut(uid) {
            val packages = context.packageManager.getPackagesForUid(uid)
            packages?.firstOrNull()
        }
    }
}
