/*  Copyright (C) 2026 SuperHealth

    This file is part of Gadgetbridge (SuperHealth fork).

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Protocol facts (UUIDs, frame layouts, CRC parameters, command/packet numbers, byte offsets) come from
    the public WHOOP interoperability notes of the NOOP project (docs/PROTOCOL*.md, github.com/ryanbr/noop),
    themselves built on johnmiddleton12/my-whoop (WHOOP 4.0) and b-nnett/goose (WHOOP 5.0). The code below
    is an independent implementation. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.whoop

import java.util.UUID
import java.util.zip.CRC32

/** WHOOP 4.0 (« Harvard », service 61080001) ou WHOOP 5.0 / MG (« Maverick / puffin », service fd4b0001). */
enum class WhoopFamily { WHOOP4, WHOOP5 }

object WhoopUuids {
    val W4_SERVICE: UUID = UUID.fromString("61080001-8d6d-82b8-614a-1c8cb0f8dcc6")
    val W4_CMD: UUID = UUID.fromString("61080002-8d6d-82b8-614a-1c8cb0f8dcc6")
    val W4_CMD_NOTIFY: UUID = UUID.fromString("61080003-8d6d-82b8-614a-1c8cb0f8dcc6")
    val W4_EVENTS: UUID = UUID.fromString("61080004-8d6d-82b8-614a-1c8cb0f8dcc6")
    val W4_DATA: UUID = UUID.fromString("61080005-8d6d-82b8-614a-1c8cb0f8dcc6")

    val W5_SERVICE: UUID = UUID.fromString("fd4b0001-cce1-4033-93ce-002d5875f58a")
    val W5_CMD: UUID = UUID.fromString("fd4b0002-cce1-4033-93ce-002d5875f58a")
    val W5_NOTIFY: List<UUID> = listOf("fd4b0003", "fd4b0004", "fd4b0005", "fd4b0007").map { UUID.fromString("$it-cce1-4033-93ce-002d5875f58a") }

    val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
}

/** Numéros de paquet (octet 4 sur WHOOP 4, 8 sur WHOOP 5). */
object WhoopPacket {
    const val COMMAND = 35
    const val COMMAND_RESPONSE = 36
    const val REALTIME_DATA = 40
    const val REALTIME_RAW_DATA = 43
    const val HISTORICAL_DATA = 47
    const val EVENT = 48
    const val METADATA = 49
    const val CONSOLE_LOGS = 50
    const val PUFFIN_METADATA = 56
}

/** Commandes utilisées (sous-ensemble sûr : aucune commande de micrologiciel, d'effacement ou de remise à zéro). */
object WhoopCommand {
    const val TOGGLE_REALTIME_HR = 3
    const val REPORT_VERSION_INFO = 7
    const val SET_CLOCK = 10
    const val GET_CLOCK = 11
    const val SEND_HISTORICAL_DATA = 22
    const val HISTORICAL_DATA_RESULT = 23
    const val GET_BATTERY_LEVEL = 26
    const val GET_DATA_RANGE = 34
    const val GET_HELLO_HARVARD = 35
    const val SEND_R10_R11_REALTIME = 63
    const val RUN_HAPTICS_PATTERN = 79
}

object WhoopMeta {
    const val HISTORY_START = 1
    const val HISTORY_END = 2
    const val HISTORY_COMPLETE = 3
}

/** Événements poussés par le bracelet (type 48). */
object WhoopEvent {
    const val BATTERY_LEVEL = 3
    const val CHARGING_ON = 7
    const val CHARGING_OFF = 8
    const val WRIST_ON = 9
    const val WRIST_OFF = 10
    const val DOUBLE_TAP = 14
}

/** Une seconde d'historique décodée (tous les champs absents valent null). */
data class WhoopSecond(
    val unix: Long,
    val hr: Int? = null,
    /** Intervalles R-R en millisecondes (0 exclus). */
    val rr: List<Int> = emptyList(),
    val gx: Float? = null, val gy: Float? = null, val gz: Float? = null,
    /** Température cutanée en °C (WHOOP 5 uniquement : la valeur WHOOP 4 n'a pas d'échelle établie). */
    val skinTempC: Float? = null,
    /** Canal brut lié à la respiration (WHOOP 4 v24) : unité inconnue, seule son oscillation est utilisée. */
    val respRaw: Int? = null,
    /** Compteur de pas cumulé du podomètre (WHOOP 5 R18), u16 qui peut reboucler. */
    val stepCounter: Int? = null,
    /** 0 immobile, 1 marche, 2 course (WHOOP 5 R18) ; null sinon. */
    val activity: Int? = null,
) {
    val hasGravity: Boolean get() = gx != null && gy != null && gz != null
}

/** Crc8 (0x07), CRC16-Modbus et CRC32 zlib, little-endian partout. */
object WhoopCrc {
    private val CRC8_TABLE = IntArray(256) { i ->
        var c = i
        repeat(8) { c = if (c and 0x80 != 0) ((c shl 1) xor 0x07) and 0xFF else (c shl 1) and 0xFF }
        c
    }

    fun crc8(b: ByteArray, from: Int, to: Int): Int {
        var c = 0
        for (i in from until to) c = CRC8_TABLE[(c xor (b[i].toInt() and 0xFF)) and 0xFF]
        return c
    }

    fun crc16Modbus(b: ByteArray, from: Int, to: Int): Int {
        var c = 0xFFFF
        for (i in from until to) {
            c = c xor (b[i].toInt() and 0xFF)
            repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xA001 else c ushr 1 }
        }
        return c and 0xFFFF
    }

    fun crc32(b: ByteArray, from: Int, to: Int): Long = CRC32().apply { update(b, from, to - from) }.value
}

internal fun ByteArray.u8(o: Int) = this[o].toInt() and 0xFF
internal fun ByteArray.u16(o: Int) = u8(o) or (u8(o + 1) shl 8)
internal fun ByteArray.i16(o: Int) = u16(o).let { if (it >= 0x8000) it - 0x10000 else it }
internal fun ByteArray.u32(o: Int): Long = (u16(o).toLong() or (u16(o + 2).toLong() shl 16)) and 0xFFFFFFFFL
internal fun ByteArray.f32(o: Int): Float = java.lang.Float.intBitsToFloat(u32(o).toInt())

private fun le16(v: Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte())
private fun le32(v: Long) = byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())

/**
 * Trames des deux générations. WHOOP 4 : `AA len16 crc8(len) | type seq cmd payload | crc32` (taille = len + 4).
 * WHOOP 5 (format 1) : `AA 01 len16 hdr16 crc16(0..6) | type seq cmd payload pad4 | crc32` (taille = len + 8) ;
 * l'enregistrement intérieur commence à l'octet 8 au lieu de 4, d'où le décalage de +4 de tous les champs.
 */
object WhoopFrames {
    const val MAX_FRAME = 8192

    /** Trame fixe d'ouverture d'une session WHOOP 5 / MG, écrite avec réponse juste après la découverte. */
    val W5_CLIENT_HELLO: ByteArray = byteArrayOf(0xAA.toByte(), 0x01, 0x08, 0x00, 0x00, 0x01, 0xE6.toByte(), 0x71, 0x23, 0x01, 0x91.toByte(),
        0x01, 0x36, 0x3E, 0x5C, 0x8D.toByte())

    fun inner(f: WhoopFamily) = if (f == WhoopFamily.WHOOP4) 4 else 8

    fun command(f: WhoopFamily, cmd: Int, seq: Int, payload: ByteArray = byteArrayOf(0)): ByteArray {
        val raw = byteArrayOf(WhoopPacket.COMMAND.toByte(), seq.toByte(), cmd.toByte()) + payload
        return if (f == WhoopFamily.WHOOP4) {
            val len = le16(raw.size + 4)
            byteArrayOf(0xAA.toByte()) + len + byteArrayOf(WhoopCrc.crc8(len, 0, 2).toByte()) + raw + le32(WhoopCrc.crc32(raw, 0, raw.size))
        } else {
            val body = raw + ByteArray((4 - raw.size % 4) % 4)
            val head = byteArrayOf(0xAA.toByte(), 0x01) + le16(body.size + 4) + byteArrayOf(0x00, 0x01)
            head + le16(WhoopCrc.crc16Modbus(head, 0, 6)) + body + le32(WhoopCrc.crc32(body, 0, body.size))
        }
    }

    /** Taille complète annoncée par l'en-tête, ou null si l'en-tête est incomplet. */
    fun declaredSize(f: WhoopFamily, b: ByteArray, at: Int, avail: Int): Int? =
        if (f == WhoopFamily.WHOOP4) { if (avail < 4) null else b.u16(at + 1) + 4 }
        else { if (avail < 8) null else b.u16(at + 2) + 8 }

    private fun headerOk(f: WhoopFamily, b: ByteArray, at: Int) =
        if (f == WhoopFamily.WHOOP4) WhoopCrc.crc8(b, at + 1, at + 3) == b.u8(at + 3)
        else WhoopCrc.crc16Modbus(b, at, at + 6) == b.u16(at + 6)

    /** Trame complète valide (en-tête et CRC32 du contenu). */
    fun valid(f: WhoopFamily, frame: ByteArray): Boolean {
        val min = if (f == WhoopFamily.WHOOP4) 11 else 16
        if (frame.size < min || frame.u8(0) != 0xAA || declaredSize(f, frame, 0, frame.size) != frame.size || !headerOk(f, frame, 0)) return false
        val start = inner(f)
        val end = frame.size - 4
        return WhoopCrc.crc32(frame, start, end) == frame.u32(end)
    }

    fun type(f: WhoopFamily, frame: ByteArray) = frame.u8(inner(f))

    /**
     * Réassemblage des notifications : les trames de 1,9 ko arrivent en plusieurs fragments. Les octets avant un 0xAA sont
     * ignorés ; un faux début de trame (en-tête dont la somme de contrôle ne correspond pas) fait avancer d'un octet.
     */
    class Reassembler(private val family: WhoopFamily) {
        private var buf = ByteArray(0)

        fun reset() { buf = ByteArray(0) }

        fun feed(fragment: ByteArray): List<ByteArray> {
            buf += fragment
            val out = ArrayList<ByteArray>()
            var head = 0
            while (true) {
                while (head < buf.size && buf.u8(head) != 0xAA) head++
                if (head >= buf.size) break
                val avail = buf.size - head
                val size = declaredSize(family, buf, head, avail) ?: break
                val headerLen = if (family == WhoopFamily.WHOOP4) 4 else 8
                if (size < headerLen + 7 || size > MAX_FRAME || !headerOk(family, buf, head)) { head++; continue }
                if (avail < size) break
                out += buf.copyOfRange(head, head + size)
                head += size
            }
            buf = buf.copyOfRange(head, buf.size)
            return out
        }
    }

    // ---- Réponses et métadonnées ------------------------------------------------------------

    /** Réponse de commande : numéro de commande, résultat (0 échec, 1 succès, 2 en attente, 3 non pris en charge) et corps. */
    data class Response(val cmd: Int, val result: Int, val body: ByteArray)

    fun response(f: WhoopFamily, frame: ByteArray): Response? {
        val i = inner(f)
        if (frame.size < i + 5 + 4 || frame.u8(i) != WhoopPacket.COMMAND_RESPONSE) return null
        // commande à i+2, séquence d'origine à i+3, résultat à i+4, corps ensuite (jusqu'au CRC32)
        return Response(frame.u8(i + 2), frame.u8(i + 4), frame.copyOfRange(i + 5, frame.size - 4))
    }

    /** Batterie en % : WHOOP 4 = u16 en dixièmes, WHOOP 5 = u32 entier. */
    fun battery(f: WhoopFamily, r: Response): Int? = when {
        r.cmd != WhoopCommand.GET_BATTERY_LEVEL || r.result != 1 -> null
        f == WhoopFamily.WHOOP4 && r.body.size >= 2 -> (r.body.u16(0) / 10).takeIf { it in 0..100 }
        f == WhoopFamily.WHOOP5 && r.body.size >= 4 -> r.body.u32(0).toInt().takeIf { it in 1..100 }
        else -> null
    }

    /** Version du micrologiciel WHOOP 4 (« Harvard » a.b.c.d) dans la réponse à REPORT_VERSION_INFO. */
    fun firmware(r: Response): String? {
        if (r.cmd != WhoopCommand.REPORT_VERSION_INFO || r.body.size < 17) return null
        return (0 until 4).joinToString(".") { r.body.u32(1 + it * 4).toString() }
    }

    /** Horloge du bracelet (secondes Unix) dans la réponse à GET_CLOCK. */
    fun clock(f: WhoopFamily, r: Response): Long? {
        // WHOOP 4 : secondes au début du corps ; WHOOP 5 : octet de révision puis secondes
        val at = if (f == WhoopFamily.WHOOP4) 0 else 1
        return if (r.cmd == WhoopCommand.GET_CLOCK && r.body.size >= at + 4) r.body.u32(at).takeIf { it > 1_600_000_000L } else null
    }

    /** Métadonnée d'historique : type (début, fin, terminé) et, pour une fin, le bloc d'accusé de 8 octets à renvoyer tel quel. */
    data class Meta(val type: Int, val ackBlock: ByteArray?)

    fun meta(f: WhoopFamily, frame: ByteArray): Meta? {
        val i = inner(f)
        val t = frame.u8(i)
        if (t != WhoopPacket.METADATA && t != WhoopPacket.PUFFIN_METADATA) return null
        val mt = frame.u8(i + 2)
        // bloc d'accusé : octets 17..25 (WHOOP 4) ou 21..29 (WHOOP 5) ; il peut chevaucher le CRC32, c'est un écho opaque
        val block = if (mt == WhoopMeta.HISTORY_END && frame.size >= i + 21) frame.copyOfRange(i + 13, i + 21) else null
        return Meta(mt, block)
    }

    /** Commande d'accusé d'un bloc d'historique, à n'envoyer qu'une fois le bloc enregistré. */
    fun ackPayload(block: ByteArray) = byteArrayOf(0x01) + block

    /** Charge utile de SET_CLOCK (secondes Unix + 4 octets de fraction), et la forme héritée à 5 octets pour les WHOOP 4 en 41.17.x. */
    fun clockPayload(nowSec: Long, legacy: Boolean = false) = le32(nowSec) + ByteArray(if (legacy) 5 else 4)

    /** Fréquence cardiaque en direct (type 40) et intervalles R-R en ms. */
    fun realtime(f: WhoopFamily, frame: ByteArray): Pair<Int, List<Int>>? {
        val i = inner(f)
        if (frame.u8(i) != WhoopPacket.REALTIME_DATA) return null
        val end = frame.size - 4
        return if (f == WhoopFamily.WHOOP4) {
            if (end < 14) return null
            val hr = frame.u8(12); val n = frame.u8(13)
            hr to (0 until n).mapNotNull { k -> (14 + 2 * k).takeIf { it + 2 <= end }?.let { frame.u16(it) }?.takeIf { it > 0 } }
        } else {
            if (end < 18) return null
            val hr = frame.u8(16); val n = frame.u8(17)
            hr to (0 until n).mapNotNull { k -> (18 + 2 * k).takeIf { it + 2 <= end }?.let { frame.u16(it) }?.takeIf { it > 0 }?.let { t -> ((t * 1000L + 512) / 1024).toInt() } }
        }
    }

    /** Événement (type 48) : numéro, et pour le niveau de batterie WHOOP 4 le pourcentage et la charge. */
    data class Event(val number: Int, val batteryPct: Int? = null, val charging: Boolean? = null)

    fun event(f: WhoopFamily, frame: ByteArray): Event? {
        val i = inner(f)
        if (frame.u8(i) != WhoopPacket.EVENT || frame.size < i + 4) return null
        val n = frame.u8(i + 2)
        if (f == WhoopFamily.WHOOP4 && n == WhoopEvent.BATTERY_LEVEL && frame.size >= 27 + 4)
            return Event(n, (frame.u16(17) / 10).takeIf { it in 0..100 }, frame.u8(26) and 1 == 1)
        return Event(n)
    }

    // ---- Historique -------------------------------------------------------------------------

    /** Seconde d'historique (type 47), ou null pour une disposition inconnue ou invraisemblable. */
    fun historical(f: WhoopFamily, frame: ByteArray): WhoopSecond? {
        if (frame.u8(inner(f)) != WhoopPacket.HISTORICAL_DATA) return null
        return if (f == WhoopFamily.WHOOP4) whoop4(frame) else whoop5(frame)
    }

    private fun plausibleUnix(t: Long) = t in 1_700_000_000L..(System.currentTimeMillis() / 1000 + 86_400)

    private fun whoop4(b: ByteArray): WhoopSecond? {
        val end = b.size - 4
        val version = b.u8(5)
        if (end < 15) return null
        val unix = b.u32(11)
        if (!plausibleUnix(unix)) return null
        if (version == 25) {
            // v25 (84 octets) : gravité en i16 / 16384 à 73/75/77, pas de fréquence cardiaque par seconde
            if (end < 79) return null
            val gx = b.i16(73) / 16384f; val gy = b.i16(75) / 16384f; val gz = b.i16(77) / 16384f
            val mag = kotlin.math.sqrt(gx * gx + gy * gy + gz * gz)
            return if (mag in 0.5f..1.5f) WhoopSecond(unix, gx = gx, gy = gy, gz = gz) else WhoopSecond(unix)
        }
        if (end < 23) return null
        val hr = b.u8(21).takeIf { it in 25..230 }
        val n = minOf(b.u8(22), 4)
        val rr = (0 until n).mapNotNull { k -> (23 + 2 * k).takeIf { it + 2 <= end }?.let { b.u16(it) }?.takeIf { it in 250..2500 } }
        val dsp = version == 24 || version == 12 || (version !in setOf(5, 7, 9))
        if (!dsp || end < 82) return if (version in setOf(5, 7, 9)) WhoopSecond(unix, hr, rr) else null
        val gx = b.f32(40); val gy = b.f32(44); val gz = b.f32(48)
        val mag = kotlin.math.sqrt(gx * gx + gy * gy + gz * gz)
        val gravityOk = gx.isFinite() && gy.isFinite() && gz.isFinite() && mag in 0.5f..1.5f
        // version inconnue : décodée comme v24 seulement si le résultat est physiologique (gravité ≈ 1 g, pouls plausible)
        if (version != 24 && version != 12 && !(mag in 0.8f..1.2f && hr != null)) return null
        return WhoopSecond(unix, hr, rr, if (gravityOk) gx else null, if (gravityOk) gy else null, if (gravityOk) gz else null,
            respRaw = b.u16(80))
    }

    private fun whoop5(b: ByteArray): WhoopSecond? {
        val layout = b.u8(9)
        if (layout != 18 || b.size < 124) return null
        val unix = b.u32(15)
        if (!plausibleUnix(unix)) return null
        val hr = b.u8(22).takeIf { it in 25..230 }
        val n = minOf(b.u8(23), 4)
        val rr = (0 until n).map { b.u16(24 + 2 * it) }.filter { it > 0 }.map { ((it * 1000L + 512) / 1024).toInt() }.filter { it in 250..2500 }
        val gx = b.f32(45); val gy = b.f32(49); val gz = b.f32(53)
        val mag = kotlin.math.sqrt(gx * gx + gy * gy + gz * gz)
        val g = gx.isFinite() && gy.isFinite() && gz.isFinite() && mag in 0.5f..1.5f
        val temp = (b.u16(73) / 100f).takeIf { it in 5f..45f }
        val act = b.u8(63).takeIf { it <= 2 }
        return WhoopSecond(unix, hr, rr, if (g) gx else null, if (g) gy else null, if (g) gz else null, temp, null, b.u16(57), act)
    }
}
