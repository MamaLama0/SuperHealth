package app.superhealth.platform

/*
 * Archives ZIP (sauvegardes, plugins, exports CSV, import de courses) : lecture des entrées « stockées » ou « deflate »
 * (zlib de la plateforme), écriture en entrées stockées. Remplace java.util.zip.
 */

/** Décompresse un flux deflate brut (sans en-tête zlib) de taille finale [size] (−1 : inconnue). */
expect fun inflateRaw(data: ByteArray, size: Int): ByteArray

/** Décompresse un fichier gzip (.gz). */
fun gunzip(data: ByteArray): ByteArray {
    require(data.size > 18 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) { "pas un fichier gzip" }
    val flags = data[3].toInt()
    var p = 10
    if (flags and 4 != 0) p += 2 + ((data[p].toInt() and 0xff) or ((data[p + 1].toInt() and 0xff) shl 8))
    if (flags and 8 != 0) { while (data[p].toInt() != 0) p++; p++ }
    if (flags and 16 != 0) { while (data[p].toInt() != 0) p++; p++ }
    if (flags and 2 != 0) p += 2
    val n = data.size
    val size = (data[n - 4].toInt() and 0xff) or ((data[n - 3].toInt() and 0xff) shl 8) or ((data[n - 2].toInt() and 0xff) shl 16) or ((data[n - 1].toInt() and 0xff) shl 24)
    return inflateRaw(data.copyOfRange(p, n - 8), size)
}

object Crc32 {
    private val table = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (0xEDB88320.toInt() xor (c ushr 1)) else c ushr 1 }
        c
    }
    fun of(b: ByteArray): Int {
        var c = -1
        for (x in b) c = table[(c xor x.toInt()) and 0xff] xor (c ushr 8)
        return c.inv()
    }
}

object Zip {
    private fun u16(b: ByteArray, p: Int) = (b[p].toInt() and 0xff) or ((b[p + 1].toInt() and 0xff) shl 8)
    private fun u32(b: ByteArray, p: Int) = u16(b, p) or (u16(b, p + 2) shl 16)

    fun isZip(b: ByteArray) = b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()

    /** Entrées (nom, contenu) d'une archive, dossiers exclus. */
    fun read(b: ByteArray): List<Pair<String, ByteArray>> {
        // fin du répertoire central (« PK\5\6 »), cherchée depuis la fin
        var eocd = b.size - 22
        while (eocd >= 0 && u32(b, eocd) != 0x06054b50) eocd--
        require(eocd >= 0) { "archive ZIP invalide" }
        val count = u16(b, eocd + 10)
        var p = u32(b, eocd + 16)
        val out = ArrayList<Pair<String, ByteArray>>(count)
        repeat(count) {
            require(u32(b, p) == 0x02014b50) { "répertoire ZIP invalide" }
            val method = u16(b, p + 10)
            val csize = u32(b, p + 20)
            val usize = u32(b, p + 24)
            val nlen = u16(b, p + 28); val elen = u16(b, p + 30); val clen = u16(b, p + 32)
            val local = u32(b, p + 42)
            val name = b.copyOfRange(p + 46, p + 46 + nlen).decodeToString()
            p += 46 + nlen + elen + clen
            if (name.endsWith("/")) return@repeat
            val start = local + 30 + u16(b, local + 26) + u16(b, local + 28)
            val raw = b.copyOfRange(start, start + csize)
            out += name to when (method) {
                0 -> raw
                8 -> inflateRaw(raw, usize)
                else -> error("compression ZIP non gérée ($method)")
            }
        }
        return out
    }

    /** Archive d'entrées stockées (sans compression). */
    fun write(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ArrayList<Byte>()
        val central = ArrayList<Byte>()
        fun MutableList<Byte>.u16(v: Int) { add(v.toByte()); add((v ushr 8).toByte()) }
        fun MutableList<Byte>.u32(v: Int) { u16(v and 0xffff); u16(v ushr 16) }
        entries.forEach { (name, data) ->
            val n = name.encodeToByteArray()
            val crc = Crc32.of(data)
            val offset = out.size
            out.u32(0x04034b50); out.u16(20); out.u16(0x0800); out.u16(0); out.u16(0); out.u16(0x21)
            out.u32(crc); out.u32(data.size); out.u32(data.size); out.u16(n.size); out.u16(0)
            n.forEach { out.add(it) }; data.forEach { out.add(it) }
            central.u32(0x02014b50); central.u16(20); central.u16(20); central.u16(0x0800); central.u16(0); central.u16(0); central.u16(0x21)
            central.u32(crc); central.u32(data.size); central.u32(data.size); central.u16(n.size); central.u16(0); central.u16(0)
            central.u16(0); central.u16(0); central.u32(0); central.u32(offset)
            n.forEach { central.add(it) }
        }
        val cdStart = out.size
        out.addAll(central)
        out.u32(0x06054b50); out.u16(0); out.u16(0); out.u16(entries.size); out.u16(entries.size)
        out.u32(central.size); out.u32(cdStart); out.u16(0)
        return out.toByteArray()
    }
}
