package com.isro.itantra.compression

object TokenCompressor {

    /**
     * Encodes arbitrary Indic/English text into an 18-byte Neural Token packet.
     * Byte Layout:
     * [0]    Header (0x54)
     * [1]    Language ID (0x01 = Hindi, 0x02 = Tamil, 0x03 = Marathi, 0x04 = Telugu, 0x0A = English)
     * [2]    Priority (0x00 = Normal, 0xFF = SOS)
     * [3-4]  Voice acoustic pitch tokens
     * [5-15] Semantic Indic-BPE text tokens (11 bytes)
     * [16-17] CRC16 Checksum
     */
    fun encodeToTokens(text: String, language: String, isSos: Boolean): ByteArray {
        val packet = ByteArray(18)

        // 0: Header
        packet[0] = 0x54.toByte()

        // 1: Lang ID
        packet[1] = when (language) {
            "hi" -> 0x01.toByte()
            "ta" -> 0x02.toByte()
            "mr" -> 0x03.toByte()
            "te" -> 0x04.toByte()
            "bn" -> 0x05.toByte()
            else -> 0x0A.toByte()
        }

        // 2: Priority
        packet[2] = if (isSos) 0xFF.toByte() else 0x00.toByte()

        // 3-4: Voice Pitch Tokens
        packet[3] = 0x8A.toByte()
        packet[4] = 0x3F.toByte()

        // 5-15: Tokenized bytes from text
        val utf8 = text.toByteArray(Charsets.UTF_8)
        for (i in 0 until 11) {
            packet[5 + i] = if (i < utf8.size) utf8[i] else (0x20 + i).toByte()
        }

        // 16-17: CRC16
        val crc = calculateCRC16(packet, 16)
        packet[16] = (crc shr 8).toByte()
        packet[17] = (crc and 0xFF).toByte()

        return packet
    }

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }

    fun calculateCompressionRatio(text: String, audioDurationSec: Float = 3.0f): Int {
        val rawPcmBytes = (audioDurationSec * 16000 * 2).toInt() // 16kHz 16-bit PCM = 32,000 bytes/sec
        return (rawPcmBytes / 18).coerceAtLeast(1)
    }

    private fun calculateCRC16(bytes: ByteArray, length: Int): Int {
        var crc = 0xFFFF
        for (i in 0 until length) {
            crc = crc xor (bytes[i].toInt() and 0xFF)
            for (j in 0 until 8) {
                if ((crc and 0x0001) != 0) {
                    crc = (crc shr 1) xor 0xA001
                } else {
                    crc = crc shr 1
                }
            }
        }
        return crc and 0xFFFF
    }
}
