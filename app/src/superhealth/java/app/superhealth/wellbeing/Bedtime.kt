package app.superhealth.wellbeing

import android.content.Context
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.Scores
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Coucher conseillé (comme le planificateur de sommeil de WHOOP) : pour vous réveiller à votre heure habituelle après
 * votre besoin de sommeil plus la dette à rattraper, compte tenu du temps que vous passez éveillé au lit.
 * coucher = réveil − (besoin + dette) / efficacité. Réveil : médiane des réveils des 14 dernières nuits du même type de jour
 * (semaine ou week-end, s'il y en a au moins 4), sinon de toutes. Efficacité : médiane de endormi / au lit. Règle SuperHealth.
 *
 * Fenêtre d'endormissement (facultative) : l'envie de dormir culmine autour de votre heure d'endormissement habituelle,
 * et les 2 à 3 heures qui la précèdent sont la « zone interdite » où l'on s'endort mal (porte du sommeil de Lavie 1986 ;
 * Strogatz 1987 ; même idée que SleepWise de Polar, sans son modèle). Fenêtre = médiane des endormissements des mêmes
 * nuits ± la moitié de leur écart interquartile (15 min au moins).
 */
object Bedtime {
    /** [gate] : fenêtre d'endormissement (minutes du jour, début et fin). */
    data class Plan(val bedMinute: Int, val wakeMinute: Int, val needMin: Double, val debtMin: Double, val efficiency: Double, val nights: Int,
                    val gate: Pair<Int, Int>? = null) {
        /** Coucher conseillé plus d'une heure avant la fenêtre : dans la zone où l'on s'endort mal. */
        val tooEarly: Boolean get() = gate != null && fromNoon(gate.first) - fromNoon(bedMinute) > 60
    }

    /** Nuits avec heure de coucher et de lever avant de proposer une heure. */
    const val MIN_NIGHTS = 7

    /** Nuits utilisables trouvées au dernier calcul (pour dire où l'on en est avant [MIN_NIGHTS]). */
    @Volatile var nightsFound = 0
        private set

    private data class Night(val day: LocalDate, val wakeMinute: Int, val asleepMin: Double, val inBedMin: Double, val onsetMinute: Int)

    suspend fun tonight(ctx: Context): Plan? {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val nights = (14L downTo 0L).mapNotNull { k ->
            val d = today.minusDays(k)
            val n = Bridge.sleepNight(d)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val start = n.minOf { it.start }; val end = n.maxOf { it.end }
            val awake = n.filter { it.kind == ActivityKind.AWAKE_SLEEP }.sumOf { it.end - it.start }
            val asleep = (n.sumOf { it.end - it.start } - awake) / 60000.0
            val inBed = (end - start) / 60000.0
            if (asleep < 120 || inBed <= 0) return@mapNotNull null
            val t = Instant.ofEpochMilli(end).atZone(zone).toLocalTime()
            // endormissement : début de la première phase de sommeil (pas d'éveil)
            val o = Instant.ofEpochMilli(n.filter { it.kind != ActivityKind.AWAKE_SLEEP }.minOfOrNull { it.start } ?: start).atZone(zone).toLocalTime()
            Night(d, t.hour * 60 + t.minute, asleep, inBed, o.hour * 60 + o.minute)
        }
        nightsFound = nights.size
        if (nights.size < MIN_NIGHTS) return null
        val tomorrow = today.plusDays(1)
        fun weekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
        val same = nights.filter { weekend(it.day) == weekend(tomorrow) }
        val wake = median((if (same.size >= 4) same else nights).map { it.wakeMinute.toDouble() }).toInt()
        val eff = median(nights.map { (it.asleepMin / it.inBedMin).coerceIn(0.5, 1.0) })
        val need = Scores.sleepHabits(ctx).needMin
        val debt = Scores.sleepDebtOf(ctx, today) ?: 0.0
        // au plus 10 h au lit : une grosse dette se rattrape sur plusieurs nuits
        val inBed = ((need + debt) / eff).coerceAtMost(600.0)
        val bed = Math.floorMod(wake - inBed.toInt(), 24 * 60)
        val ref = if (same.size >= 4) same else nights
        return Plan(bed, wake, need, debt, eff, nights.size, gate(ref.map { it.onsetMinute }))
    }

    /** Minutes depuis midi : les endormissements de part et d'autre de minuit restent dans l'ordre. */
    fun fromNoon(minute: Int) = Math.floorMod(minute - 12 * 60, 24 * 60)

    /** Fenêtre d'endormissement à partir des heures d'endormissement (minutes du jour) ; null sous 4 nuits. */
    fun gate(onsets: List<Int>): Pair<Int, Int>? {
        if (onsets.size < 4) return null
        val xs = onsets.map { fromNoon(it).toDouble() }.sorted()
        val med = median(xs)
        val iqr = xs[(xs.size - 1) * 3 / 4] - xs[(xs.size - 1) / 4]
        val half = maxOf(15.0, iqr / 2)
        fun back(v: Double) = Math.floorMod(v.toInt() + 12 * 60, 24 * 60)
        return back(med - half) to back(med + half)
    }

    private fun median(xs: List<Double>): Double {
        val s = xs.sorted(); val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
    }

    fun clock(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
}
