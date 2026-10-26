package app.superhealth.bridge

import app.superhealth.platform.*

/**
 * Lecteur de fichiers FIT (profil ANT+/Garmin) limité aux messages d'une activité : « record » (20) pour le tracé et
 * « session » (18) pour le résumé. Remplace le FitFile de Gadgetbridge, absent sur iOS.
 */
object FitParser {
    class Record(val ms: Long, val lat: Double?, val lon: Double?, val alt: Double?, val hr: Int?, val cad: Int?, val dist: Double?, val speed: Double?)

    class Session(
        val sport: Int?, val timerSec: Double?, val distance: Double?, val kcal: Int?, val avgHr: Int?, val maxHr: Int?,
        val ascent: Int?, val descent: Int?, val cadence: Int?, val maxSpeed: Double?,
    )

    class Result(val records: List<Record>, val sessions: List<Session>)

    /** Secondes entre l'époque Unix et celle de FIT (31 décembre 1989, 00:00 UTC). */
    private const val FIT_EPOCH = 631_065_600L

    private class Field(val num: Int, val size: Int, val base: Int)
    private class Def(val global: Int, val big: Boolean, val fields: List<Field>, val devSize: Int)

    fun parse(b: ByteArray): Result {
        require(b.size >= 12) { "fichier FIT trop court" }
        val headerSize = b[0].toInt() and 0xff
        require(b.size > headerSize && b[8] == '.'.code.toByte() && b[9] == 'F'.code.toByte()) { "pas un fichier FIT" }
        val dataSize = u32(b, 4, false).toInt()
        val end = minOf(b.size, headerSize + dataSize)
        val defs = HashMap<Int, Def>()
        val records = ArrayList<Record>()
        val sessions = ArrayList<Session>()
        var lastTs = 0L
        var p = headerSize
        while (p < end) {
            val h = b[p].toInt() and 0xff; p++
            if (h and 0x80 != 0) {
                // en-tête d'horodatage compressé : type local sur 2 bits, décalage de 5 bits
                val local = (h shr 5) and 3
                val offset = h and 0x1f
                val ts = (lastTs and 0x1fL.inv()) + offset + (if (offset < (lastTs and 0x1f)) 0x20 else 0)
                val d = defs[local] ?: break
                p = data(b, p, d, ts, records, sessions) { lastTs = it }
                continue
            }
            val local = h and 0x0f
            if (h and 0x40 != 0) {
                val dev = h and 0x20 != 0
                val big = b[p + 1].toInt() == 1
                val global = u16(b, p + 2, big)
                val n = b[p + 4].toInt() and 0xff
                p += 5
                val fields = (0 until n).map { i -> Field(b[p + 3 * i].toInt() and 0xff, b[p + 3 * i + 1].toInt() and 0xff, b[p + 3 * i + 2].toInt() and 0xff) }
                p += 3 * n
                var devSize = 0
                if (dev) {
                    val nd = b[p].toInt() and 0xff; p++
                    for (i in 0 until nd) devSize += b[p + 3 * i + 1].toInt() and 0xff
                    p += 3 * nd
                }
                defs[local] = Def(global, big, fields, devSize)
            } else {
                val d = defs[local] ?: break
                p = data(b, p, d, null, records, sessions) { lastTs = it }
            }
        }
        return Result(records, sessions)
    }

    private fun data(b: ByteArray, start: Int, d: Def, compressedTs: Long?, records: MutableList<Record>, sessions: MutableList<Session>,
                     onTs: (Long) -> Unit): Int {
        var p = start
        val v = HashMap<Int, Long>()
        d.fields.forEach { f ->
            val value = when (f.size) {
                1 -> (b[p].toLong() and 0xff).takeIf { it != 0xffL }
                2 -> u16(b, p, d.big).toLong().takeIf { it != 0xffffL }
                4 -> u32(b, p, d.big).takeIf { it != 0xffffffffL }
                else -> null
            }
            // types signés (sint8/16/32) : 0x01, 0x83, 0x85 ; valeur invalide 0x7F…
            val signed = when (f.base and 0x1f) { 1 -> value?.let { if (it >= 0x80) it - 0x100 else it }?.takeIf { it != 0x7fL }
                3 -> value?.let { if (it >= 0x8000) it - 0x10000 else it }?.takeIf { it != 0x7fffL }
                5 -> value?.let { if (it >= 0x80000000L) it - 0x100000000L else it }?.takeIf { it != 0x7fffffffL }
                else -> value }
            if (signed != null) v[f.num] = signed
            p += f.size
        }
        p += d.devSize
        val ts = v[253] ?: compressedTs
        ts?.let(onTs)
        when (d.global) {
            20 -> if (ts != null) {
                val semi = 180.0 / 2147483648.0
                records += Record(
                    ms = (ts + FIT_EPOCH) * 1000,
                    lat = v[0]?.let { it * semi }?.takeIf { it != 0.0 },
                    lon = v[1]?.let { it * semi }?.takeIf { it != 0.0 },
                    alt = (v[78] ?: v[2])?.let { it / 5.0 - 500 },
                    hr = v[3]?.toInt()?.takeIf { it in 30..240 },
                    cad = v[4]?.toInt()?.takeIf { it > 0 },
                    dist = v[5]?.let { it / 100.0 }?.takeIf { it > 0 },
                    speed = (v[73] ?: v[6])?.let { it / 1000.0 }?.takeIf { it > 0 },
                )
            }
            18 -> sessions += Session(
                sport = v[5]?.toInt(), timerSec = v[8]?.let { it / 1000.0 }, distance = v[9]?.let { it / 100.0 },
                kcal = v[11]?.toInt(), avgHr = v[16]?.toInt(), maxHr = v[17]?.toInt(), ascent = v[22]?.toInt(), descent = v[23]?.toInt(),
                cadence = v[18]?.toInt(), maxSpeed = (v[125] ?: v[15])?.let { it / 1000.0 },
            )
        }
        return p
    }

    private fun u16(b: ByteArray, p: Int, big: Boolean): Int =
        if (big) ((b[p].toInt() and 0xff) shl 8) or (b[p + 1].toInt() and 0xff) else (b[p].toInt() and 0xff) or ((b[p + 1].toInt() and 0xff) shl 8)

    private fun u32(b: ByteArray, p: Int, big: Boolean): Long =
        if (big) (u16(b, p, true).toLong() shl 16) or u16(b, p + 2, true).toLong()
        else u16(b, p, false).toLong() or (u16(b, p + 2, false).toLong() shl 16)
}
