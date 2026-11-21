package com.example.gizlilikarnesi.capture

import java.net.InetAddress
import java.nio.ByteBuffer

data class ParsedPacket(
    val protocol: Int,
    val sourceAddress: InetAddress,
    val sourcePort: Int,
    val destAddress: InetAddress,
    val destPort: Int,
    val payloadOffset: Int,
    val payloadLength: Int
)

object PacketParser {

    const val PROTOCOL_TCP = 6
    const val PROTOCOL_UDP = 17

    /**
     * Sadece IPv4 destekleniyor (IPv6 bu projede kapsam disi birakildi,
     * cogu Android uygulamasi hala IPv4 uzerinden de konusuyor).
     */
    fun parseIpv4Packet(buffer: ByteArray, length: Int): ParsedPacket? {
        if (length < 20) return null

        val versionAndIhl = buffer[0].toInt() and 0xFF
        val version = versionAndIhl shr 4
        if (version != 4) return null

        val ihl = versionAndIhl and 0x0F
        val ipHeaderLength = ihl * 4
        if (ipHeaderLength < 20 || length < ipHeaderLength) return null

        val protocol = buffer[9].toInt() and 0xFF
        val sourceAddress = InetAddress.getByAddress(buffer.copyOfRange(12, 16))
        val destAddress = InetAddress.getByAddress(buffer.copyOfRange(16, 20))

        return when (protocol) {
            PROTOCOL_TCP -> parseTcp(buffer, length, ipHeaderLength, protocol, sourceAddress, destAddress)
            PROTOCOL_UDP -> parseUdp(buffer, length, ipHeaderLength, protocol, sourceAddress, destAddress)
            else -> null
        }
    }

    private fun parseTcp(
        buffer: ByteArray,
        length: Int,
        ipHeaderLength: Int,
        protocol: Int,
        sourceAddress: InetAddress,
        destAddress: InetAddress
    ): ParsedPacket? {
        if (length < ipHeaderLength + 20) return null

        val bb = ByteBuffer.wrap(buffer, ipHeaderLength, 20)
        val sourcePort = bb.getShort(ipHeaderLength).toInt() and 0xFFFF
        val destPort = bb.getShort(ipHeaderLength + 2).toInt() and 0xFFFF

        val dataOffsetByte = buffer[ipHeaderLength + 12].toInt() and 0xFF
        val tcpHeaderLength = (dataOffsetByte shr 4) * 4
        if (tcpHeaderLength < 20 || length < ipHeaderLength + tcpHeaderLength) return null

        val payloadOffset = ipHeaderLength + tcpHeaderLength
        val payloadLength = length - payloadOffset

        return ParsedPacket(protocol, sourceAddress, sourcePort, destAddress, destPort, payloadOffset, payloadLength)
    }

    private fun parseUdp(
        buffer: ByteArray,
        length: Int,
        ipHeaderLength: Int,
        protocol: Int,
        sourceAddress: InetAddress,
        destAddress: InetAddress
    ): ParsedPacket? {
        if (length < ipHeaderLength + 8) return null

        val sourcePort = ((buffer[ipHeaderLength].toInt() and 0xFF) shl 8) or
            (buffer[ipHeaderLength + 1].toInt() and 0xFF)
        val destPort = ((buffer[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or
            (buffer[ipHeaderLength + 3].toInt() and 0xFF)

        val payloadOffset = ipHeaderLength + 8
        val payloadLength = length - payloadOffset

        return ParsedPacket(protocol, sourceAddress, sourcePort, destAddress, destPort, payloadOffset, payloadLength)
    }
}
