/*  Copyright (C) 2026 SuperHealth

    This file is part of Gadgetbridge (SuperHealth fork), licensed under the GNU AGPL v3 or later. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.whoop

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Secondes d'historique WHOOP gardées sur le téléphone (un fichier binaire par jour UTC et par bracelet), le temps de
 * recalculer sommeil, VFC et pouls au repos sur des fenêtres qui chevauchent minuit. Environ 15 octets par seconde ;
 * [KEEP_DAYS] jours conservés. Les doublons (blocs renvoyés par le bracelet) sont éliminés à la lecture.
 */
class WhoopRawStore(root: File, address: String) {
    private val dir = File(root, "whoop/" + address.replace(":", "")).apply { mkdirs() }

    fun append(records: List<WhoopSecond>) {
        records.groupBy { day(it.unix) }.forEach { (d, list) ->
            DataOutputStream(java.io.FileOutputStream(File(dir, "$d.bin"), true).buffered()).use { o -> list.forEach { write(o, it) } }
        }
    }

    /** Secondes entre [fromUnix] et [toUnix] (exclu), triées et sans doublon. */
    fun read(fromUnix: Long, toUnix: Long): List<WhoopSecond> {
        val out = HashMap<Long, WhoopSecond>()
        var d = day(fromUnix)
        val last = day(toUnix - 1)
        while (!d.isAfter(last)) {
            val f = File(dir, "$d.bin")
            if (f.exists()) DataInputStream(f.inputStream().buffered()).use { i ->
                while (true) {
                    val r = try { read(i) } catch (e: EOFException) { break }
                    if (r.unix in fromUnix until toUnix) out[r.unix] = merge(out[r.unix], r)
                }
            }
            d = d.plusDays(1)
        }
        return out.values.sortedBy { it.unix }
    }

    /** Jours (UTC) qui ont des données. */
    fun days(): List<LocalDate> = dir.listFiles()?.mapNotNull { runCatching { LocalDate.parse(it.name.removeSuffix(".bin")) }.getOrNull() }?.sorted().orEmpty()

    fun prune(keepDays: Long = KEEP_DAYS) {
        val limit = LocalDate.now(ZoneOffset.UTC).minusDays(keepDays)
        dir.listFiles()?.forEach { f -> runCatching { LocalDate.parse(f.name.removeSuffix(".bin")) }.getOrNull()?.takeIf { it.isBefore(limit) }?.let { f.delete() } }
    }

    companion object {
        const val KEEP_DAYS = 21L

        fun day(unix: Long): LocalDate = Instant.ofEpochSecond(unix).atZone(ZoneOffset.UTC).toLocalDate()

        /** Une seconde reçue deux fois : on garde les champs présents dans l'une ou l'autre. */
        private fun merge(a: WhoopSecond?, b: WhoopSecond): WhoopSecond = if (a == null) b else WhoopSecond(b.unix, b.hr ?: a.hr,
            b.rr.ifEmpty { a.rr }, b.gx ?: a.gx, b.gy ?: a.gy, b.gz ?: a.gz, b.skinTempC ?: a.skinTempC, b.respRaw ?: a.respRaw,
            b.stepCounter ?: a.stepCounter, b.activity ?: a.activity)

        private const val F_GRAVITY = 1
        private const val F_TEMP = 2
        private const val F_RESP = 4
        private const val F_STEPS = 8
        private const val F_ACT = 16

        private fun write(o: DataOutputStream, r: WhoopSecond) {
            o.writeInt(r.unix.toInt())
            o.writeByte(r.hr ?: 0)
            var flags = 0
            if (r.hasGravity) flags = flags or F_GRAVITY
            if (r.skinTempC != null) flags = flags or F_TEMP
            if (r.respRaw != null) flags = flags or F_RESP
            if (r.stepCounter != null) flags = flags or F_STEPS
            if (r.activity != null) flags = flags or F_ACT
            o.writeByte(flags)
            o.writeByte(r.rr.size.coerceAtMost(8))
            if (r.hasGravity) { o.writeShort((r.gx!! * 10000).toInt()); o.writeShort((r.gy!! * 10000).toInt()); o.writeShort((r.gz!! * 10000).toInt()) }
            if (r.skinTempC != null) o.writeShort((r.skinTempC * 100).toInt())
            if (r.respRaw != null) o.writeShort(r.respRaw)
            if (r.stepCounter != null) o.writeShort(r.stepCounter)
            if (r.activity != null) o.writeByte(r.activity)
            r.rr.take(8).forEach { o.writeShort(it) }
        }

        private fun read(i: DataInputStream): WhoopSecond {
            val unix = i.readInt().toLong() and 0xFFFFFFFFL
            val hr = i.readUnsignedByte().takeIf { it > 0 }
            val flags = i.readUnsignedByte()
            val n = i.readUnsignedByte()
            var gx: Float? = null; var gy: Float? = null; var gz: Float? = null
            if (flags and F_GRAVITY != 0) { gx = i.readShort() / 10000f; gy = i.readShort() / 10000f; gz = i.readShort() / 10000f }
            val temp = if (flags and F_TEMP != 0) i.readShort() / 100f else null
            val resp = if (flags and F_RESP != 0) i.readUnsignedShort() else null
            val steps = if (flags and F_STEPS != 0) i.readUnsignedShort() else null
            val act = if (flags and F_ACT != 0) i.readUnsignedByte() else null
            val rr = List(n) { i.readUnsignedShort() }
            return WhoopSecond(unix, hr, rr, gx, gy, gz, temp, resp, steps, act)
        }
    }
}
