package com.example.gizlilikarnesi.capture

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DnsResponseBuilderTest {

    private val app = byteArrayOf(10, 0, 0, 2)
    private val fakeDns = byteArrayOf(10, 0, 0, 53)

    @Test
    fun adreslerVePortlarTersCevrilir() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val packet = DnsResponseBuilder.build(app, 40000, fakeDns, 53, payload)

        val parsed = PacketParser.parseIpv4Packet(packet, packet.size)!!
        assertEquals(PacketParser.PROTOCOL_UDP, parsed.protocol)
        assertArrayEquals(fakeDns, parsed.sourceAddress.address)
        assertEquals(53, parsed.sourcePort)
        assertArrayEquals(app, parsed.destAddress.address)
        assertEquals(40000, parsed.destPort)
        assertEquals(payload.size, parsed.payloadLength)
        assertArrayEquals(payload, packet.copyOfRange(parsed.payloadOffset, packet.size))
    }

    @Test
    fun ipChecksumGecerli() {
        val packet = DnsResponseBuilder.build(app, 40000, fakeDns, 53, ByteArray(33))
        // Checksum alani dahil baslik tekrar toplanirsa sonuc 0 olmali
        assertEquals(0, DnsResponseBuilder.ipChecksum(packet, 0, 20))
    }

    /** "ads.x.com" icin A sorgusu + sonunda EDNS (OPT) ek kaydi; ID = 0xBEEF, RD = 1 */
    private fun sampleQuery(): ByteArray {
        val header = intArrayOf(0xBE, 0xEF, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01)
        val qname = intArrayOf(3, 'a'.code, 'd'.code, 's'.code, 1, 'x'.code, 3, 'c'.code, 'o'.code, 'm'.code, 0)
        val qtypeClass = intArrayOf(0x00, 0x01, 0x00, 0x01)
        val opt = intArrayOf(0x00, 0x00, 0x29, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        return (header + qname + qtypeClass + opt).map { it.toByte() }.toByteArray()
    }

    @Test
    fun nxdomainCevabiDogruBasliklaUretilir() {
        val query = sampleQuery()
        val resp = DnsResponseBuilder.nxdomain(query)!!
        // Sadece baslik (12) + soru (11 + 4); EDNS eki atildi
        assertEquals(12 + 11 + 4, resp.size)
        assertEquals(0xBE, resp[0].toInt() and 0xFF)     // ID korunur
        assertEquals(0xEF, resp[1].toInt() and 0xFF)
        assertEquals(0x81, resp[2].toInt() and 0xFF)     // QR=1, RD=1
        assertEquals(0x83, resp[3].toInt() and 0xFF)     // RA=1, RCODE=3 (NXDOMAIN)
        assertEquals(1, resp[5].toInt())                 // 1 soru
        assertEquals(0, resp[7].toInt() + resp[9].toInt() + resp[11].toInt()) // 0 cevap/yetki/ek
        assertArrayEquals(query.copyOfRange(12, 27), resp.copyOfRange(12, 27)) // soru aynen
    }

    @Test
    fun bozukSorgudaNxdomainUretilmez() {
        assertEquals(null, DnsResponseBuilder.nxdomain(ByteArray(5)))
        // Etiket uzunlugu paketin disina tasiyor
        val broken = ByteArray(14).also { it[12] = 40 }
        assertEquals(null, DnsResponseBuilder.nxdomain(broken))
    }

    @Test
    fun bilinenOrnekChecksum() {
        // Wikipedia "IPv4 header checksum" ornegi: beklenen 0xB861
        val header = intArrayOf(
            0x45, 0x00, 0x00, 0x73, 0x00, 0x00, 0x40, 0x00, 0x40, 0x11,
            0x00, 0x00, 0xc0, 0xa8, 0x00, 0x01, 0xc0, 0xa8, 0x00, 0xc7
        ).map { it.toByte() }.toByteArray()
        assertEquals(0xB861, DnsResponseBuilder.ipChecksum(header, 0, 20))
    }
}
