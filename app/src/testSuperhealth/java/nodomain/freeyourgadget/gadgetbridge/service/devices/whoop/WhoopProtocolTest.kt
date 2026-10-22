package nodomain.freeyourgadget.gadgetbridge.service.devices.whoop

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.random.Random

class WhoopProtocolTest {
    /** Trame WHOOP 4 complète autour d'un enregistrement intérieur (type, seq/version, cmd, charge utile). */
    private fun w4(inner: ByteArray): ByteArray {
        val len = inner.size + 4
        val head = byteArrayOf(0xAA.toByte(), len.toByte(), (len shr 8).toByte())
        val crc = WhoopCrc.crc32(inner, 0, inner.size)
        return head + byteArrayOf(WhoopCrc.crc8(head, 1, 3).toByte()) + inner +
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(crc.toInt()).array()
    }

    /** Trame WHOOP 5 (format 1) autour d'un corps déjà aligné sur 4 octets. */
    private fun w5(body: ByteArray): ByteArray {
        val len = body.size + 4
        val head = byteArrayOf(0xAA.toByte(), 1, len.toByte(), (len shr 8).toByte(), 1, 0)
        val c16 = WhoopCrc.crc16Modbus(head, 0, 6)
        val crc = WhoopCrc.crc32(body, 0, body.size)
        return head + byteArrayOf(c16.toByte(), (c16 shr 8).toByte()) + body + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(crc.toInt()).array()
    }

    @Test fun clientHelloIsAFormat1Command() {
        // le « CLIENT_HELLO » fixe des notes de protocole est exactement GET_HELLO (145), révision 1, séquence 1
        assertArrayEquals(WhoopFrames.W5_CLIENT_HELLO, WhoopFrames.command(WhoopFamily.WHOOP5, 145, 1, byteArrayOf(1)))
        assertTrue(WhoopFrames.valid(WhoopFamily.WHOOP5, WhoopFrames.W5_CLIENT_HELLO))
    }

    @Test fun whoop4CommandFrame() {
        val f = WhoopFrames.command(WhoopFamily.WHOOP4, WhoopCommand.GET_BATTERY_LEVEL, 7)
        assertEquals(12, f.size) // type, seq, cmd, 1 octet de charge + 4 d'en-tête + 4 de CRC32
        assertTrue(WhoopFrames.valid(WhoopFamily.WHOOP4, f))
        assertEquals(WhoopPacket.COMMAND, WhoopFrames.type(WhoopFamily.WHOOP4, f))
        f[8] = (f[8] + 1).toByte()
        assertTrue(!WhoopFrames.valid(WhoopFamily.WHOOP4, f))
    }

    @Test fun reassemblerSplitsAndSkipsGarbage() {
        val a = WhoopFrames.command(WhoopFamily.WHOOP4, 26, 1)
        val b = WhoopFrames.command(WhoopFamily.WHOOP4, 34, 2)
        val stream = byteArrayOf(1, 2, 0xAA.toByte(), 9) + a + b
        val r = WhoopFrames.Reassembler(WhoopFamily.WHOOP4)
        val out = stream.toList().chunked(5).flatMap { r.feed(it.toByteArray()) }
        assertEquals(2, out.size)
        assertArrayEquals(a, out[0]); assertArrayEquals(b, out[1])
    }

    @Test fun whoop4BatteryResponse() {
        // type 36, seq, cmd 26, origine, résultat 1, batterie 873 dixièmes
        val f = w4(byteArrayOf(36, 5, 26, 3, 1, 0x69, 0x03, 0, 0))
        val r = WhoopFrames.response(WhoopFamily.WHOOP4, f)!!
        assertEquals(87, WhoopFrames.battery(WhoopFamily.WHOOP4, r))
    }

    @Test fun historyEndAckBlockIsEchoedVerbatim() {
        // métadonnée : type 49, seq, meta 2 (fin), unix, subsec, réservé, curseur ; le bloc d'accusé = octets 17..25
        val payload = ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN).putInt(1_760_000_000).putShort(5).putInt(0).putInt(112193).putInt(77).array()
        val f = w4(byteArrayOf(49, 0, 2) + payload)
        val m = WhoopFrames.meta(WhoopFamily.WHOOP4, f)!!
        assertEquals(WhoopMeta.HISTORY_END, m.type)
        assertArrayEquals(f.copyOfRange(17, 25), m.ackBlock)
        assertEquals(112193L, m.ackBlock!!.u32(0))
        assertArrayEquals(byteArrayOf(1) + f.copyOfRange(17, 25), WhoopFrames.ackPayload(m.ackBlock!!))
    }

    @Test fun whoop4V24Record() {
        val r = ByteBuffer.allocate(78).order(ByteOrder.LITTLE_ENDIAN) // trame 4..82 (CRC à 82)
        r.put(0, 47); r.put(1, 24)
        r.putInt(11 - 4, 1_760_000_000)
        r.put(21 - 4, 58); r.put(22 - 4, 2); r.putShort(23 - 4, 1032); r.putShort(25 - 4, 1010)
        r.putFloat(40 - 4, 0.1f); r.putFloat(44 - 4, -0.2f); r.putFloat(48 - 4, 0.97f)
        r.putShort(80 - 4, 1234)
        val s = WhoopFrames.historical(WhoopFamily.WHOOP4, w4(r.array()))!!
        assertEquals(1_760_000_000L, s.unix)
        assertEquals(58, s.hr)
        assertEquals(listOf(1032, 1010), s.rr)
        assertEquals(0.97f, s.gz!!, 1e-6f)
        assertEquals(1234, s.respRaw)
        assertNull(s.skinTempC)
    }

    @Test fun whoop5R18Record() {
        val b = ByteBuffer.allocate(112).order(ByteOrder.LITTLE_ENDIAN) // corps 8..120
        b.put(0, 47); b.put(1, 18)
        b.putInt(15 - 8, 1_760_000_100)
        b.put(22 - 8, 61); b.put(23 - 8, 1); b.putShort(24 - 8, 1024) // 1024 ticks = 1000 ms
        b.putFloat(45 - 8, 0f); b.putFloat(49 - 8, 0f); b.putFloat(53 - 8, 1f)
        b.putShort(57 - 8, 4321); b.put(63 - 8, 1); b.putShort(73 - 8, 3412)
        val s = WhoopFrames.historical(WhoopFamily.WHOOP5, w5(b.array()))!!
        assertEquals(61, s.hr); assertEquals(listOf(1000), s.rr)
        assertEquals(4321, s.stepCounter); assertEquals(1, s.activity)
        assertEquals(34.12f, s.skinTempC!!, 1e-4f)
    }

    @Test fun rawStoreRoundTrip() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        val st = WhoopRawStore(dir, "AA:BB")
        val recs = listOf(WhoopSecond(1_760_000_000, 60, listOf(1000, 990), 0f, 0f, 1f, 33.5f, 12, 100, 0), WhoopSecond(1_760_000_001, null))
        st.append(recs); st.append(recs.take(1))
        val back = st.read(1_759_999_000, 1_760_001_000)
        assertEquals(2, back.size)
        assertEquals(recs[0].copy(gx = 0f, gy = 0f), back[0])
        dir.deleteRecursively()
    }
}

class WhoopAnalysisTest {
    /** Journée simulée : éveillé et mobile de 12 h à 23 h, nuit immobile de 23 h à 7 h (pouls bas, R-R variables), réveil. */
    private fun day(seed: Int = 1): List<WhoopSecond> {
        val rnd = Random(seed)
        val start = 1_760_004_000L // midi UTC
        val out = ArrayList<WhoopSecond>()
        var g = floatArrayOf(0f, 0f, 1f)
        for (t in 0 until 24 * 3600) {
            val h = t / 3600.0
            val night = h in 11.0..19.0
            val move = if (night) 0.001f else 0.05f
            g = floatArrayOf(g[0] + (rnd.nextFloat() - 0.5f) * move, g[1] + (rnd.nextFloat() - 0.5f) * move, 1f)
            g[0] = g[0].coerceIn(-0.3f, 0.3f); g[1] = g[1].coerceIn(-0.3f, 0.3f)
            val hr = if (night) 52 + (3 * sin(t / 600.0)).toInt() else 78 + rnd.nextInt(15)
            val rrBase = 60000 / hr
            val rr = if (t % 1 == 0) listOf(rrBase + rnd.nextInt(-40, 40)) else emptyList()
            out += WhoopSecond(start + t, hr, rr, g[0], g[1], g[2])
        }
        return out
    }

    @Test fun findsTheNight() {
        val r = WhoopAnalysis.analyze(day())
        assertEquals(1, r.sessions.size)
        val s = r.sessions[0]
        val hours = (s.end - s.start) / 3600.0
        assertTrue("durée $hours h", hours in 7.0..9.0)
        assertTrue(s.restingHr!! in 48..55)
        assertTrue(s.hrv.isNotEmpty())
        assertTrue(s.efficiency > 0.8)
        // stades : du profond en début de nuit seulement, pas de paradoxal dans le premier quart d'heure
        val deep = s.stages.withIndex().filter { it.value.second == WhoopAnalysis.Stage.DEEP }.map { it.index }
        assertTrue(deep.all { it <= s.stages.size / 3 })
        assertTrue(s.stages.take(15).none { it.second == WhoopAnalysis.Stage.REM })
        val score = WhoopAnalysis.restScore(s)
        assertTrue(score in 50..100)
    }

    @Test fun rmssdAndCleaning() {
        // battement ectopique (1500 ms au milieu de ~1000) retiré
        val rr = List(30) { 1000 + (if (it % 2 == 0) 20 else -20) }.toMutableList().also { it[15] = 1500 }
        val clean = WhoopAnalysis.cleanRr(rr)
        assertEquals(29, clean.size)
        assertEquals(40.0, WhoopAnalysis.rmssd(clean)!!, 3.0)
        assertNull(WhoopAnalysis.rmssd(clean.take(10)))
    }

    @Test fun baevskyOrdersStress() {
        val calm = List(60) { 1000 + (it % 7) * 30 - 90 }
        val tense = List(60) { 600 + (it % 3) * 5 }
        assertTrue(WhoopAnalysis.baevsky(tense)!! > WhoopAnalysis.baevsky(calm)!!)
        assertNotNull(WhoopAnalysis.stress(WhoopAnalysis.minutes(day()), emptySet()).firstOrNull())
    }
}
