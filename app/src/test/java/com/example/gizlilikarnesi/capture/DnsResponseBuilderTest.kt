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
