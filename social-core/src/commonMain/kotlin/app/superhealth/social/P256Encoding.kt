package app.superhealth.social

/** Convert the P-256 private representations used by Security.framework and PKCS#8. */
object P256Encoding {
    private data class Value(val tag: Int, val bytes: ByteArray)
    private fun values(bytes: ByteArray): List<Value> {
        val result = mutableListOf<Value>(); var offset = 0
        while (offset < bytes.size) {
            val tag = bytes[offset++].toInt() and 255
            require(offset < bytes.size)
            var length = bytes[offset++].toInt() and 255
            if (length and 128 != 0) {
                val count = length and 127; require(count in 1..2 && offset + count <= bytes.size)
                length = 0
                repeat(count) { length = (length shl 8) or (bytes[offset++].toInt() and 255) }
            }
            require(offset + length <= bytes.size)
            result += Value(tag, bytes.copyOfRange(offset, offset + length)); offset += length
        }
        return result
    }
    fun scalar(private: ByteArray): ByteArray {
        if (private.size == 97 && private[0] == 4.toByte()) return private.copyOfRange(65,97)
        val sequence = values(private).single(); require(sequence.tag == 48)
        val fields = values(sequence.bytes)
        val octets = fields.firstOrNull { it.tag == 4 }?.bytes ?: error("Invalid P-256 private key")
        if (octets.size == 32) return octets
        val ec = values(octets).single(); require(ec.tag == 48)
        return values(ec.bytes).first { it.tag == 4 }.bytes.also { require(it.size == 32) }
    }
    private fun der(tag: Int, bytes: ByteArray): ByteArray {
        val size = bytes.size
        val length = if (size < 128) byteArrayOf(size.toByte()) else byteArrayOf(0x81.toByte(),size.toByte())
        require(size < 256)
        return byteArrayOf(tag.toByte()) + length + bytes
    }
    private fun point(public: ByteArray): ByteArray {
        require(public.size == 91)
        return public.copyOfRange(26,91).also { require(it[0] == 4.toByte()) }
    }
    fun apple(private: ByteArray, public: ByteArray): ByteArray = point(public) + scalar(private)
    fun pkcs8(private: ByteArray, public: ByteArray): ByteArray {
        val scalar = scalar(private)
        val ec = der(48, byteArrayOf(2,1,1) + der(4,scalar) + der(0xA1,der(3,byteArrayOf(0)+point(public))))
        val algorithm = "301306072a8648ce3d020106082a8648ce3d030107".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return der(48, byteArrayOf(2,1,0) + algorithm + der(4,ec))
    }
}
