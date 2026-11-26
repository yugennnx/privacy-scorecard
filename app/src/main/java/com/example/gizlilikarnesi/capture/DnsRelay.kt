package com.example.gizlilikarnesi.capture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Uygulamalarin DNS sorgularini gercek DNS sunucusuna ileten ve cevaplari
 * geri teslim eden kucuk bir "DNS yonlendirici" (dnsmasq benzeri).
 *
 * Uc onemli ozellik (hepsi bolum 18'deki emulator olcumlerinden dogdu):
 *
 * 1) TEK SOKET + ID ESLESTIRME: Tum sorgular tek bir kalici soketten gider.
 *    Her DNS mesajinin ilk 2 bayti bir islem numarasidir (ID), sunucu
 *    cevapta ayni ID'yi yollar. Farkli uygulamalar ayni ID'yi secebilecegi
 *    icin giden sorguya KENDI benzersiz ID'mizi yaziyoruz, cevap gelince
 *    her bekleyen uygulamaya kendi orijinal ID'siyle teslim ediyoruz.
 *
 * 2) AYNI SORUYU BIRLESTIRME: Chrome ayni domaini (ayni kayit tipiyle) kisa
 *    surede defalarca sorar. Cevap beklenirken ayni soru tekrar gelirse
 *    yeniden gondermek yerine bekleyenler listesine ekliyoruz.
 *
 * 3) KISA ONBELLEK: Gelen cevabi CACHE_TTL_MS boyunca sakliyoruz; ayni soru
 *    tekrar gelirse aninda cevapliyoruz. (Basitlik icin sabit sure; gercek
 *    TTL'yi cevaptan okumak V2 isi.)
 *
 * Cevap gelmezse sorgu sirayla diger sunuculara tekrar gonderilir.
 */
class DnsRelay(
    private val scope: CoroutineScope,
    private val upstreamServers: List<InetAddress>,
    /** VpnService.protect: soketin kendi tunelimize geri dusmesini engeller */
    protect: (DatagramSocket) -> Boolean,
    /** Hazir IPv4+UDP cevap paketini TUN arayuzune yazar */
    private val writeToTun: (ByteArray) -> Unit
) {
    /** Cevabi bekleyen tek bir uygulama sorgusu: cevap kime, hangi ID ile gidecek. */
    private class Waiter(
        val originalId: Int,
        val appAddress: ByteArray,
        val appPort: Int,
        val dnsAddress: ByteArray,
        val dnsPort: Int
    )

    /** Upstream'e gonderilmis, cevabi beklenen bir soru (bizim ID'mizle). */
    private class InFlight(
        val key: String,
        val query: ByteArray,
        val sentAt: Long,
        val waiters: MutableList<Waiter>
    )

    private class CachedAnswer(val response: ByteArray, val expiresAt: Long)

    private val socket = DatagramSocket().also { protect(it) }
    private val inFlightById = ConcurrentHashMap<Int, InFlight>()
    private val inFlightByKey = ConcurrentHashMap<String, InFlight>()
    private val cache = ConcurrentHashMap<String, CachedAnswer>()
    private val nextId = AtomicInteger((Math.random() * 0xFFFF).toInt())

    /** Upstream'den cevap alan sorular */
    val answered = AtomicInteger()
    /** Tum denemelere ragmen cevapsiz kalan sorular */
    val failed = AtomicInteger()
    /** Onbellekten ya da bekleyen ayni soruya eklenerek cevaplanan sorgular */
    val servedLocally = AtomicInteger()
    /** Kullanicinin engelledigi sirketlere giden ve NXDOMAIN ile cevaplanan sorgular */
    val blocked = AtomicInteger()
    private val totalLatencyMs = AtomicLong()
    val averageLatencyMs: Long get() = answered.get().let { if (it > 0) totalLatencyMs.get() / it else 0 }

    fun start() {
        scope.launch { receiveLoop() }
    }

    fun stop() {
        socket.close() // bloklanmis receive() hata firlatip dongu biter
        inFlightById.clear()
        inFlightByKey.clear()
        cache.clear()
    }

    /**
     * Engellenen bir sorguya gercek DNS'e gitmeden NXDOMAIN cevabi verir.
     * @return cevap verildiyse true; sorgu cozulemediyse false (cagiran normal iletir)
     */
    fun answerBlocked(buffer: ByteArray, packet: ParsedPacket): Boolean {
        if (packet.payloadLength < 12) return false
        val query = buffer.copyOfRange(packet.payloadOffset, packet.payloadOffset + packet.payloadLength)
        val response = DnsResponseBuilder.nxdomain(query) ?: return false
        deliver(
            response, response.size,
            Waiter(
                originalId = readId(query),
                appAddress = packet.sourceAddress.address,
                appPort = packet.sourcePort,
                dnsAddress = packet.destAddress.address,
                dnsPort = packet.destPort
            )
        )
        blocked.incrementAndGet()
        return true
    }

    /** Okuma dongusunden cagrilir; buffer tekrar kullanildigi icin kopyalar. */
    fun forward(buffer: ByteArray, packet: ParsedPacket) {
        if (packet.payloadLength < 12) return // DNS basligi bile yok
        val query = buffer.copyOfRange(packet.payloadOffset, packet.payloadOffset + packet.payloadLength)
        val waiter = Waiter(
            originalId = readId(query),
            appAddress = packet.sourceAddress.address,
            appPort = packet.sourcePort,
            dnsAddress = packet.destAddress.address,
            dnsPort = packet.destPort
        )
        // Soru anahtari: ID haric tum mesaj (domain + tip + bayraklar).
        val key = String(query, 2, query.size - 2, Charsets.ISO_8859_1)

        cache[key]?.let { cached ->
            if (cached.expiresAt > System.currentTimeMillis()) {
                deliver(cached.response, cached.response.size, waiter)
                servedLocally.incrementAndGet()
                return
            }
            cache.remove(key)
        }

        synchronized(this) {
            inFlightByKey[key]?.let { existing ->
                existing.waiters += waiter
                servedLocally.incrementAndGet()
                return
            }
            val id = allocateId()
            writeId(query, id)
            val entry = InFlight(key, query, System.currentTimeMillis(), mutableListOf(waiter))
            inFlightById[id] = entry
            inFlightByKey[key] = entry
            scope.launch { sendWithRetries(id, entry) }
        }
    }

    /**
     * Ilk gonderim + cevap gelmezse sirayla diger sunuculara tekrar. delay()
     * thread BLOKLAMAZ, binlerce bekleyen sorgu tek bir thread havuzunda
     * rahatca beklenebilir.
     */
    private suspend fun sendWithRetries(id: Int, entry: InFlight) {
        for (attempt in 0 until MAX_ATTEMPTS) {
            if (!inFlightById.containsKey(id)) return // cevap geldi
            val server = upstreamServers[attempt % upstreamServers.size]
            runCatching { socket.send(DatagramPacket(entry.query, entry.query.size, server, 53)) }
            delay(ATTEMPT_TIMEOUT_MS)
        }
        synchronized(this) {
            if (inFlightById.remove(id) != null) {
                inFlightByKey.remove(entry.key)
                failed.incrementAndGet()
            }
        }
    }

    private fun receiveLoop() {
        val buffer = ByteArray(4096)
        val packet = DatagramPacket(buffer, buffer.size)
        while (!socket.isClosed) {
            try {
                // receive() paketin uzunlugunu gelen veriye gore KUCULTUR;
                // sifirlamazsak sonraki buyuk cevaplar kesilir.
                packet.length = buffer.size
                socket.receive(packet)
            } catch (e: Exception) {
                return // stop() soketi kapatti
            }
            // Sadece bizim sordugumuz sunuculardan gelen cevaplari kabul et
            if (packet.address !in upstreamServers || packet.length < 12) continue

            val entry = synchronized(this) {
                inFlightById.remove(readId(buffer))?.also { inFlightByKey.remove(it.key) }
            } ?: continue // gec/yinelenen cevap

            val response = buffer.copyOf(packet.length)
            cache[entry.key] = CachedAnswer(response, System.currentTimeMillis() + CACHE_TTL_MS)
            answered.incrementAndGet()
            totalLatencyMs.addAndGet(System.currentTimeMillis() - entry.sentAt)

            for (waiter in entry.waiters) deliver(response, response.size, waiter)
        }
    }

    /** Cevabi bekleyen uygulamaya, onun orijinal ID'siyle teslim eder. */
    private fun deliver(response: ByteArray, length: Int, waiter: Waiter) {
        val copy = response.copyOf(length)
        writeId(copy, waiter.originalId)
        val reply = DnsResponseBuilder.build(
            querySource = waiter.appAddress,
            querySourcePort = waiter.appPort,
            queryDest = waiter.dnsAddress,
            queryDestPort = waiter.dnsPort,
            dnsPayload = copy
        )
        runCatching { writeToTun(reply) }
    }

    /** 16 bitlik, o an bekleyen hicbir sorguyla cakismayan bir ID. */
    private fun allocateId(): Int {
        while (true) {
            val id = nextId.getAndIncrement() and 0xFFFF
            if (!inFlightById.containsKey(id)) return id
        }
    }

    private fun readId(data: ByteArray): Int =
        ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)

    private fun writeId(data: ByteArray, id: Int) {
        data[0] = (id shr 8).toByte()
        data[1] = id.toByte()
    }

    companion object {
        /**
         * UDP paket kaybina karsi: cevap bu surede gelmezse sorgu tekrar
         * (sirayla bir sonraki sunucuya) gonderilir. Gercek DNS istemcileri
         * de boyle calisir. Toplam bekleme en fazla 3 x 1.5 = 4.5 sn.
         */
        private const val ATTEMPT_TIMEOUT_MS = 1500L
        private const val MAX_ATTEMPTS = 3
        private const val CACHE_TTL_MS = 30_000L
    }
}
