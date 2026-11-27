package com.example.gizlilikarnesi.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import com.example.gizlilikarnesi.MainActivity
import com.example.gizlilikarnesi.analysis.ScanRecord
import com.example.gizlilikarnesi.analysis.ScanResults
import com.example.gizlilikarnesi.data.BlockList
import com.example.gizlilikarnesi.data.ScanStore
import com.example.gizlilikarnesi.analysis.TrackerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * "Sadece DNS" VPN servisi (bkz. GELISTIRME_GUNLUGU.txt bolum 18).
 *
 * Telefona DNS sunucusu olarak sahte bir adres (10.0.0.53) verilir ve tunele
 * SADECE bu adres yonlendirilir. Boylece:
 *  - Uygulamalarin tum DNS sorgulari bize gelir: hangi uygulama hangi
 *    domaine baglanmak istiyor, goruruz.
 *  - Sorgulari DnsRelay ile gercek DNS'e iletip cevabi geri yazariz.
 *  - Geri kalan tum trafik normal yoldan gider, internet hic kesilmez.
 */
class TrafficVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val uidResolver by lazy { UidResolver(this) }

    /** assets/trackers.json'dan bir kez yuklenir (ilk DNS sorgusunda). */
    private val trackerDatabase by lazy { TrackerDatabase.load(this) }

    /**
     * Bu tarama oturumunda gorulen (uygulama -> domainler). Set kullaniyoruz
     * cunku ayni domain A + AAAA sorgusu ve tekrarlar yuzunden birkac kez
     * gelir; karne icin her domaini bir kez saymak yeterli.
     */
    private val observedDomains = ConcurrentHashMap<String, MutableSet<String>>()

    /** Uygulama -> (domain -> ilk sorulma zamani). Kayitta "ilk saniyeler" icin. */
    private val firstSeen = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()

    private var dnsRelay: DnsRelay? = null

    /** Uygulamaya atanabilen / atanamayan DNS sorgusu sayisi (dogruluk olcumu). */
    @Volatile private var attributedQueries = 0
    @Volatile private var unattributedQueries = 0

    /** Taramanin baslangic zamani (kayit kimligi ve suresi icin). */
    private var scanStartedAt = 0L

    companion object {
        private const val TAG = "TrafficVpnService"
        private const val CHANNEL_ID = "privacy_scorecard_vpn_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.example.gizlilikarnesi.START_VPN"
        const val ACTION_STOP = "com.example.gizlilikarnesi.STOP_VPN"

        /**
         * Sahte DNS sunucusu adresi. Telefona "DNS sunucun bu" diyoruz ve
         * SADECE bu adresi tunele yonlendiriyoruz; boylece tunele yalnizca DNS
         * sorgulari duser, geri kalan trafik normal yoldan gider.
         */
        private const val VIRTUAL_DNS = "10.0.0.53"

        /** Agin kendi DNS'i cevap vermezse sirayla denenecek yedekler. */
        private val FALLBACK_DNS = listOf("8.8.8.8", "1.1.1.1")

        /**
         * Tarama suresiz: kullanici durdurana kadar surer. Telefon uygulamayi
         * arka planda kapatirsa veri kaybolmasin diye bu aralikla ara kayit
         * yapilir (ayni dosyanin uzerine yazilir).
         */
        private const val AUTOSAVE_INTERVAL_MS = 60_000L
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) {
            Log.d(TAG, "VPN zaten calisiyor, tekrar baslatilmadi")
            return
        }

        scanStartedAt = System.currentTimeMillis()
        startForeground(NOTIFICATION_ID, buildNotification())

        // VPN kurulmadan ONCE: o anki gercek agin DNS sunucusunu ogren.
        // (VPN kurulunca aktif ag VPN'in kendisi olur, sonra bulamayiz.)
        val upstreamServers = (listOfNotNull(findUnderlyingDns()) +
            FALLBACK_DNS.map { InetAddress.getByName(it) }).distinct()
        Log.d(TAG, "Gercek DNS sunuculari: ${upstreamServers.joinToString { it.hostAddress ?: "?" }}")

        // "Sadece DNS" VPN'i: rota olarak yalnizca sahte DNS adresi (/32)
        // ekleniyor, 0.0.0.0/0 DEGIL. Internet tarama boyunca kesilmez.
        val builder = Builder()
            .setSession("Gizlilik Karnesi")
            .addAddress("10.0.0.2", 32)
            .addRoute(VIRTUAL_DNS, 32)
            .addDnsServer(VIRTUAL_DNS)
            // IPv6 adresi/rotasi vermedigimiz icin Android IPv6'yi varsayilan
            // olarak ENGELLER; bu satir IPv6 trafiginin normal yoldan
            // gitmesine izin verir (yoksa bazi baglantilar yavaslar).
            .allowFamily(OsConstants.AF_INET6)
            .setMtu(1500)

        val fd = builder.establish()
        if (fd == null) {
            Log.e(TAG, "VPN arayuzu kurulamadi (establish() null dondu)")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        vpnInterface = fd

        val output = FileOutputStream(fd.fileDescriptor)
        dnsRelay = DnsRelay(
            scope = serviceScope,
            upstreamServers = upstreamServers,
            protect = { socket -> protect(socket) },
            // Birden fazla coroutine ayni anda cevap yazabilir; TUN'a giden
            // her paket tek parca yazilmali, o yuzden synchronized.
            writeToTun = { packet -> synchronized(output) { output.write(packet) } }
        ).also { it.start() }

        observedDomains.clear()
        firstSeen.clear()
        BlockList.load(this)
        attributedQueries = 0
        unattributedQueries = 0
        ScanResults.clear()
        ScanResults.setScanning(scanStartedAt)
        Log.d(TAG, "VPN baslatildi, paket okuma dongusu basliyor")
        serviceScope.launch { runPacketLoop(fd, output) }
        serviceScope.launch { autosaveLoop() }
    }

    private fun runPacketLoop(fd: ParcelFileDescriptor, output: FileOutputStream) {
        val input = FileInputStream(fd.fileDescriptor)
        val buffer = ByteArray(32767)

        var totalPackets = 0
        var dnsPackets = 0

        try {
            while (vpnInterface != null) {
                val length = input.read(buffer)
                if (length <= 0) continue
                totalPackets++

                // Tek bir bozuk/beklenmedik paket tum taramayi durdurmasin:
                // hata sadece o paketi atlatir, dongu devam eder.
                try {
                    val packet = PacketParser.parseIpv4Packet(buffer, length) ?: continue
                    when (packet.protocol) {
                        PacketParser.PROTOCOL_UDP -> if (packet.destPort == 53) {
                            dnsPackets++
                            handleDnsQuery(buffer, packet)
                        }
                        PacketParser.PROTOCOL_TCP -> handleTcp(buffer, packet)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Paket islenemedi, atlandi", e)
                }
            }
        } catch (e: Exception) {
            // stopVpn() once vpnInterface'i null yapip sonra descriptor'i
            // kapatir; bloklanmis read() bu yuzden EBADF firlatir. Bu beklenen
            // normal kapanistir, gercek hata sadece VPN hala aciksa olur.
            if (vpnInterface == null) {
                Log.d(TAG, "Paket okuma dongusu normal sekilde kapandi")
            } else {
                Log.e(TAG, "Paket okuma dongusu hata ile durdu", e)
            }
        } finally {
            Log.d(TAG, "TARAMA BITTI, OZET: toplam_paket=$totalPackets dns_sorgusu=$dnsPackets")
            Log.d(TAG, "UYGULAMA ATAMA: bulunan=$attributedQueries bulunamayan=$unattributedQueries")
            dnsRelay?.let {
                Log.d(
                    TAG,
                    "DNS ILETIM: upstream_cevaplanan=${it.answered.get()} cevapsiz=${it.failed.get()} " +
                        "yerelden_cevaplanan=${it.servedLocally.get()} engellenen=${it.blocked.get()} " +
                        "ort_gecikme=${it.averageLatencyMs}ms"
                )
            }
            input.close()
            output.close()
            logScanReport()
            saveScan()
            // Kayit yayinlandiktan SONRA "tarama bitti" de: ekran bos sonuc gostermesin.
            ScanResults.setScanning(null)
        }
    }

    /** Tarama surdukce belirli araliklarla diske yazar (sonuc ekrani degismez). */
    private suspend fun autosaveLoop() {
        while (vpnInterface != null) {
            delay(AUTOSAVE_INTERVAL_MS)
            if (vpnInterface == null || observedDomains.isEmpty()) continue
            runCatching { ScanStore.save(this, buildRecord()) }
                .onSuccess { Log.d(TAG, "ARA KAYIT: ${observedDomains.size} uygulama") }
        }
    }

    /**
     * Ana tespit yolu: sorgunun sahibi uygulamayi bulur, sorguyu gercek DNS'e
     * iletir (ya da sirket engelliyse NXDOMAIN ile cevaplar), sonra hangi
     * uygulamanin hangi domaini ne zaman sordugunu kaydeder.
     */
    private fun handleDnsQuery(buffer: ByteArray, packet: ParsedPacket) {
        // ONCE sahibini bul, SONRA ilet. Ters sirada (eskiden boyleydi) cevap
        // cok hizli donunce uygulama UDP soketini kapatiyor ve
        // getConnectionOwnerUid artik baglantiyi bulamiyordu -> sorgu
        // "Bilinmeyen / Sistem"e dusuyordu (bkz. gunluk bolum 27).
        val packageName = uidResolver.resolvePackageName(
            packet.protocol,
            packet.sourceAddress,
            packet.sourcePort,
            packet.destAddress,
            packet.destPort
        )
        if (packageName == null) unattributedQueries++ else attributedQueries++

        val domain = DnsQueryParser.extractQueryDomain(buffer, packet.payloadOffset, packet.payloadLength)
        val tracker = domain?.let { trackerDatabase.match(it) }

        // Kullanici bu sirketi engellediyse gercek DNS'e hic gitme: "boyle bir
        // adres yok" (NXDOMAIN) cevabi ver. Aksi halde sorgu HER DURUMDA
        // iletilmeli (domaini okuyamasak bile), yoksa internet kopmus gorunur.
        val blocked = tracker != null && BlockList.isBlocked(tracker.company) &&
            dnsRelay?.answerBlocked(buffer, packet) == true
        if (!blocked) dnsRelay?.forward(buffer, packet)

        if (domain == null) return

        val app = packageName ?: ScanResults.UNKNOWN_APP
        val isNew = observedDomains.getOrPut(app) { ConcurrentHashMap.newKeySet() }.add(domain)
        if (!isNew) return
        // "Ilk saniyeler" analizi icin: bu uygulama bu domaini ilk ne zaman sordu
        firstSeen.getOrPut(app) { ConcurrentHashMap() }.putIfAbsent(domain, System.currentTimeMillis())

        // Yeni domain -> karneleri yeniden hesapla, ekran canli guncellensin.
        ScanResults.publish(observedDomains.mapValues { it.value.toSet() }, trackerDatabase)

        val label = tracker?.let { " [TRACKER: ${it.company} / ${it.category.displayName}]" } ?: ""
        Log.d(TAG, "DNS SORGUSU: app=$app -> domain=$domain$label" + if (blocked) " [ENGELLENDI]" else "")
    }

    /**
     * Tarama bitince karneleri Logcat'e de yazar (ekrandaki sonuclarin aynisi;
     * hata ayiklama ve sunumda log gostermek icin).
     */
    private fun logScanReport() {
        if (observedDomains.isEmpty()) {
            Log.d(TAG, "KARNE: bu taramada hic DNS sorgusu yakalanmadi")
            return
        }
        for (report in ScanResults.reports.value) {
            val companies = report.companies.entries.joinToString { (company, category) ->
                "$company (${category.displayName})"
            }
            Log.d(
                TAG,
                "KARNE: app=${report.packageName} not=${report.grade} puan=${report.score} " +
                    "tracker=${report.trackerDomains.size}/${report.totalDomains} domain" +
                    if (companies.isNotEmpty()) " sirketler=[$companies]" else ""
            )
        }
    }

    /**
     * Bitmis taramayi diske yazar ve ekrana "bitmis tarama" olarak yayinlar
     * (boylece disa aktar dugmesi gorunur ve uygulama kapansa da sonuc kalir).
     * Hic sorgu yakalanmadiysa bos kayit olusturmaz.
     */
    private fun saveScan() {
        if (observedDomains.isEmpty()) return
        val record = buildRecord()
        ScanStore.save(this, record)
        ScanResults.publishRecord(record, trackerDatabase)
    }

    /** Su ana kadar toplanan gozlemden tarama kaydi olusturur. */
    private fun buildRecord(): ScanRecord =
        ScanRecord(
            id = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(scanStartedAt)),
            startedAtMillis = scanStartedAt,
            endedAtMillis = System.currentTimeMillis(),
            observed = observedDomains.mapValues { it.value.toSet() },
            attributedQueries = attributedQueries,
            unattributedQueries = unattributedQueries,
            dnsAnswered = dnsRelay?.answered?.get() ?: 0,
            dnsFailed = dnsRelay?.failed?.get() ?: 0,
            firstSeen = firstSeen.mapValues { it.value.toMap() },
            blockedQueries = dnsRelay?.blocked?.get() ?: 0
        )

    /** VPN acilmadan once aktif agin (Wi-Fi/mobil) IPv4 DNS sunucusunu bulur. */
    private fun findUnderlyingDns(): InetAddress? = runCatching {
        val cm = getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return null
        cm.getLinkProperties(network)?.dnsServers?.firstOrNull { it is Inet4Address }
    }.onFailure { Log.w(TAG, "Agin DNS sunucusu bulunamadi, yedekler kullanilacak: ${it.message}") }
        .getOrNull()

    /**
     * NOT: "Sadece DNS" VPN modunda tunele yalnizca sahte DNS adresine
     * giden paketler duser, normal TCP trafigi tunele HIC girmez; bu
     * fonksiyon su an pratikte log basmaz. Ileride tam NAT eklenirse
     * (bkz. GELISTIRME_GUNLUGU.txt bolum 8 ve 18) dogrudan calisir hale
     * gelecek, o yuzden birakildi.
     */
    private fun handleTcp(buffer: ByteArray, packet: ParsedPacket) {
        if (packet.payloadLength <= 0) return
        val sni = TlsSniExtractor.extractSni(buffer, packet.payloadOffset, packet.payloadLength)
            ?: return

        val packageName = uidResolver.resolvePackageName(
            packet.protocol,
            packet.sourceAddress,
            packet.sourcePort,
            packet.destAddress,
            packet.destPort
        )

        Log.d(TAG, "TLS SNI: app=${packageName ?: "bilinmiyor"} -> domain=$sni (${packet.destAddress.hostAddress}:${packet.destPort})")
    }

    private fun stopVpn() {
        Log.d(TAG, "VPN durduruluyor")
        // Once null yap, sonra kapat: okuma dongusu boylece kapanisin
        // bilerek yapildigini anlar (bkz. runPacketLoop catch blogu).
        val fd = vpnInterface
        vpnInterface = null
        dnsRelay?.stop()
        fd?.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gizlilik Karnesi Izleme",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // Bildirime dokununca uygulama acilir; "DURDUR" taramayi uygulamayi
        // acmadan bitirir (suresiz taramada onemli).
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, TrafficVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Gizlilik Karnesi · tarama sürüyor")
            .setContentText("Uygulamaların bağlantıları izleniyor. İnternetin kesilmez.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            // Gecen sureyi bildirimde canli gosterir (sistem sayar, pil harcamaz)
            .setWhen(scanStartedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(null, "DURDUR", stop).build()
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        dnsRelay?.stop()
        vpnInterface?.close()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }
}
