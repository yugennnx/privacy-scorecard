package com.example.gizlilikarnesi.capture

/**
 * UDP:53 paketinin govdesindeki DNS sorgusunu okuyup istenen domain adini
 * cikarir. TLS ClientHello'nun aksine DNS UDP uzerinden gittigi icin TCP
 * el sikismasina ihtiyac yoktur -- uygulama sorguyu gonderdigi an paket
 * tunele duser, cevap gelsin gelmesin biz zaten domain adini gormus oluruz.
 *
 * DNS mesaj formati (basitlestirilmis):
 *  [0-1]   ID
 *  [2-3]   Flags (bit 15 = QR: 0 ise sorgu, 1 ise cevap)
 *  [4-5]   QDCOUNT (soru sayisi)
 *  [6-7]   ANCOUNT
 *  [8-9]   NSCOUNT
 *  [10-11] ARCOUNT
 *  [12..]  QNAME (uzunluk-onekli etiketler, 0x00 ile biter), QTYPE(2), QCLASS(2)
 */
object DnsQueryParser {

    fun extractQueryDomain(buffer: ByteArray, offset: Int, length: Int): String? {
        return try {
            tryExtract(buffer, offset, length)
        } catch (e: Exception) {
            null
        }
    }

    private fun tryExtract(buffer: ByteArray, offset: Int, length: Int): String? {
        if (length < 12) return null

        val flagsHigh = buffer[offset + 2].toInt() and 0xFF
        val isResponse = (flagsHigh and 0x80) != 0
        if (isResponse) return null // sadece giden sorgularla ilgileniyoruz

        val questionCount = ((buffer[offset + 4].toInt() and 0xFF) shl 8) or
            (buffer[offset + 5].toInt() and 0xFF)
        if (questionCount < 1) return null

        var pos = offset + 12
        val end = offset + length
        val labels = mutableListOf<String>()

        while (pos < end) {
            val labelLength = buffer[pos].toInt() and 0xFF
            pos += 1
            if (labelLength == 0) break
            if (pos + labelLength > end) return null
            labels.add(String(buffer, pos, labelLength, Charsets.US_ASCII))
            pos += labelLength
        }

        if (labels.isEmpty()) return null
        return labels.joinToString(".")
    }
}
