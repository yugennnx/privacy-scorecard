package com.example.gizlilikarnesi.capture

/**
 * TLS ClientHello mesajindan SNI (Server Name Indication) uzantisini okuyarak
 * hangi domain'e baglanildigini cikarir. Sifreleme COZULMEZ -- sadece el
 * sikisma asamasinin acik metin basligi okunur (her TLS baglantisinda domain
 * adi bu asamada acik gider, bu yuzden DPI/decryption gerekmiyor).
 *
 * Sinirlama: ClientHello'nun tek bir TCP segmentine sigdigi varsayilir.
 * Cok nadir durumlarda (ornegin cok uzun sertifika listesi/uzanti seti)
 * ClientHello birden fazla segmente bolunebilir, bu durumda null doner.
 */
object TlsSniExtractor {

    private const val CONTENT_TYPE_HANDSHAKE = 0x16
    private const val HANDSHAKE_TYPE_CLIENT_HELLO = 0x01
    private const val EXTENSION_TYPE_SNI = 0x0000
    private const val SNI_NAME_TYPE_HOST_NAME = 0x00

    fun extractSni(buffer: ByteArray, offset: Int, length: Int): String? {
        return try {
            tryExtract(buffer, offset, length)
        } catch (e: Exception) {
            null
        }
    }

    private fun tryExtract(buffer: ByteArray, offset: Int, length: Int): String? {
        val cursor = Cursor(buffer, offset, offset + length)

        if (cursor.readUInt8() != CONTENT_TYPE_HANDSHAKE) return null
        cursor.skip(2) // TLS version
        val recordLength = cursor.readUInt16()
        if (cursor.remaining() < recordLength) return null

        if (cursor.readUInt8() != HANDSHAKE_TYPE_CLIENT_HELLO) return null
        cursor.skip(3) // handshake length (uint24)
        cursor.skip(2) // client version
        cursor.skip(32) // random

        val sessionIdLength = cursor.readUInt8()
        cursor.skip(sessionIdLength)

        val cipherSuitesLength = cursor.readUInt16()
        cursor.skip(cipherSuitesLength)

        val compressionMethodsLength = cursor.readUInt8()
        cursor.skip(compressionMethodsLength)

        if (cursor.remaining() < 2) return null
        val extensionsLength = cursor.readUInt16()
        val extensionsEnd = cursor.position() + extensionsLength

        while (cursor.position() < extensionsEnd && cursor.remaining() >= 4) {
            val extensionType = cursor.readUInt16()
            val extensionLength = cursor.readUInt16()

            if (extensionType == EXTENSION_TYPE_SNI) {
                return readServerName(cursor, extensionLength)
            } else {
                cursor.skip(extensionLength)
            }
        }

        return null
    }

    private fun readServerName(cursor: Cursor, extensionLength: Int): String? {
        val end = cursor.position() + extensionLength
        if (cursor.remaining() < 2) return null

        cursor.skip(2) // server name list length
        while (cursor.position() < end && cursor.remaining() >= 3) {
            val nameType = cursor.readUInt8()
            val nameLength = cursor.readUInt16()
            if (nameType == SNI_NAME_TYPE_HOST_NAME) {
                return cursor.readString(nameLength)
            } else {
                cursor.skip(nameLength)
            }
        }
        return null
    }

    private class Cursor(private val buffer: ByteArray, start: Int, private val end: Int) {
        private var pos = start

        fun position() = pos
        fun remaining() = end - pos

        fun readUInt8(): Int {
            require(remaining() >= 1)
            return buffer[pos++].toInt() and 0xFF
        }

        fun readUInt16(): Int {
            require(remaining() >= 2)
            val value = ((buffer[pos].toInt() and 0xFF) shl 8) or (buffer[pos + 1].toInt() and 0xFF)
            pos += 2
            return value
        }

        fun readString(len: Int): String {
            require(remaining() >= len)
            val s = String(buffer, pos, len, Charsets.US_ASCII)
            pos += len
            return s
        }

        fun skip(count: Int) {
            require(remaining() >= count) { "Buffer sinirlarinin disina cikildi" }
            pos += count
        }
    }
}
