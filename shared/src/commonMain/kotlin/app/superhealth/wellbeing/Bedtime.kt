package app.superhealth.wellbeing

import app.superhealth.platform.*

import app.superhealth.platform.Context
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.Scores
import app.superhealth.health.data.ActivityKind
import app.superhealth.platform.DayOfWeek
import app.superhealth.platform.Instant
import app.superhealth.platform.LocalDate
import app.superhealth.platform.ZoneId

/**
 * Coucher conseillé (comme le planificateur de sommeil de WHOOP) : pour vous réveiller à votre heure habituelle après
 * votre besoin de sommeil plus la dette à rattraper, compte tenu du temps que vous passez éveillé au lit.
 * coucher = réveil − (besoin + dette) / efficacité. Réveil : médiane des réveils des 14 dernières nuits du même type de jour
 * (semaine ou week-end, s'il y en a au moins 4), sinon de toutes. Efficacité : médiane de endormi / au lit. Règle SuperHealth.
 */
object Bedtime {
    data class Plan(val bedMinute: Int, val wakeMinute: Int, val needMin: Double, val debtMin: Double, val efficiency: Double, val nights: Int)

    /** Nuits avec heure de coucher et de lever avant de proposer une heure. */
    const val MIN_NIGHTS = 7

    private data class Night(val day: LocalDate, val wakeMinute: Int, val asleepMin: Double, val inBedMin: Double)

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
            Night(d, t.hour * 60 + t.minute, asleep, inBed)
        }
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
        return Plan(bed, wake, need, debt, eff, nights.size)
    }

    private fun median(xs: List<Double>): Double {
        val s = xs.sorted(); val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
    }

    fun clock(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
}
