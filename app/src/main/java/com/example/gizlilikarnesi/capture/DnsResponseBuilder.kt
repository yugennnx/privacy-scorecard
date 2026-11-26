package com.example.gizlilikarnesi.capture

/**
 * Gercek DNS sunucusundan gelen cevabi, uygulamanin bekledigi IPv4+UDP
 * paketine sarar. Uygulamanin gozunden cevap, sorguyu gonderdigi sahte DNS
 * adresinden (10.0.0.53) geliyormus gibi gorunmeli; bu yuzden kaynak ve
 * hedef (adres + port) sorgu paketine gore TERS cevrilir.
 */
object DnsResponseBuilder {

    private const val IP_HEADER_LENGTH = 20
    private const val UDP_HEADER_LENGTH = 8

    /**
     * @param querySource sorguyu gonderen uygulamanin IPv4 adresi (4 bayt)
     * @param queryDest sorgunun gittigi sahte DNS adresi (4 bayt)
     */
    fun build(
        querySource: ByteArray,
        querySourcePort: Int,
        queryDest: ByteArray,
        queryDestPort: Int,
        dnsPayload: ByteArray,
        payloadLength: Int = dnsPayload.size
    ): ByteArray {
        val udpLength = UDP_HEADER_LENGTH + payloadLength
        val totalLength = IP_HEADER_LENGTH + udpLength
        val packet = ByteArray(totalLength)

        // --- IPv4 basligi ---
        packet[0] = 0x45                       // versiyon 4, baslik 5*4=20 bayt
        writeShort(packet, 2, totalLength)
        writeShort(packet, 6, 0x4000)          // "Don't Fragment"
        packet[8] = 64                         // TTL
        packet[9] = PacketParser.PROTOCOL_UDP.toByte()
        queryDest.copyInto(packet, 12)         // kaynak = sahte DNS sunucusu
        querySource.copyInto(packet, 16)       // hedef = uygulama
        writeShort(packet, 10, ipChecksum(packet, 0, IP_HEADER_LENGTH))

        // --- UDP basligi ---
        // UDP checksum IPv4'te istege bagli: 0 = "hesaplanmadi" demektir.
        writeShort(packet, 20, queryDestPort)
        writeShort(packet, 22, querySourcePort)
        writeShort(packet, 24, udpLength)

        dnsPayload.copyInto(packet, IP_HEADER_LENGTH + UDP_HEADER_LENGTH, 0, payloadLength)
        return packet
    }

    /**
     * Engellenen bir sorgu icin "boyle bir alan adi yok" (NXDOMAIN) cevabi.
     * Gercek DNS'e hic gidilmez; uygulama adresi bulamaz ve baglanamaz.
     *
     * Cevap = sorgunun basligi + soru bolumu (aynen), ama:
     *  - QR=1 (bu bir cevap), opcode ve RD sorgudan kopyalanir, RA=1
     *  - RCODE=3 (NXDOMAIN)
     *  - cevap/yetki/ek kayit sayilari 0 (sorgudaki EDNS eki atilir)
     * Soru bolumu okunamazsa null doner (o zaman sorgu normal iletilir).
     */
    fun nxdomain(query: ByteArray): ByteArray? {
        if (query.size < 12) return null
        // Soru bolumunun sonunu bul: QNAME etiketleri 0 ile biter, sonra QTYPE+QCLASS (4 bayt)
        var i = 12
        while (i < query.size) {
            val len = query[i].toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 != 0) return null // soruda sikistirma beklenmez
            i += len + 1
        }
        val end = i + 1 + 4
        if (i >= query.size || end > query.size) return null

        val response = query.copyOf(end)
        response[2] = (0x80 or (query[2].toInt() and 0x79)).toByte() // QR + opcode + RD
        response[3] = (0x80 or 0x03).toByte()                         // RA + NXDOMAIN
        writeShort(response, 4, 1)  // soru sayisi
        writeShort(response, 6, 0)  // cevap
        writeShort(response, 8, 0)  // yetki
        writeShort(response, 10, 0) // ek kayit
        return response
    }

    /**
     * Internet checksum (RFC 1071): basligi 16 bitlik kelimeler halinde topla,
     * tasmalari geri ekle, sonucun tersini al. Alici ayni toplamayi yapip
     * 0xFFFF bulursa baslik bozulmamis demektir.
     */
    fun ipChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (length % 2 == 1) sum += (data[offset + length - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun writeShort(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value shr 8).toByte()
        buffer[offset + 1] = value.toByte()
    }
}
