package app.superhealth.health.data

import android.content.Context
import app.superhealth.bridge.Bridge
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Scores repris des méthodes publiées que documente NOOP (docs/ANALYTICS.md), calculés pour toute source (Garmin, WHOOP,
 * autres montres, Health Connect) :
 *  - **Charge** (récupération, 0–100) : z-scores robustes de la VFC (55 %), du pouls au repos (20 %), du sommeil (15 %),
 *    de la respiration (5 %) et de l'écart de température (5 %) contre des références personnelles (moyennes mobiles
 *    exponentielles winsorisées), passés dans une logistique ancrée à 58 % pour z = 0 ;
 *  - **Effort** (charge cardiovasculaire du jour, 0–100) : TRIMP d'Edwards (zones de 50/60/70/80/90 % de la réserve
 *    cardiaque de Karvonen, poids 1 à 5 par minute), compressé en 100 · ln(TRIMP + 1) / ln(7201) ;
 *  - **Dette de sommeil** : 55 % du besoin non couvert reporté sur la nuit suivante, sur 14 nuits, sans « crédit ».
 * Approximations transparentes de méthodes ouvertes, pas les scores propriétaires de WHOOP ni un avis médical.
 */
object Scores {
    // ---- Références personnelles ----------------------------------------------------------

    /** Configuration d'une référence : bornes de plausibilité et écart minimal. */
    data class Cfg(val min: Double, val max: Double, val floorSpread: Double)

    val HRV = Cfg(5.0, 250.0, 5.0)
    val RHR = Cfg(30.0, 120.0, 2.0)
    val RESP = Cfg(4.0, 40.0, 0.5)
    val TEMP = Cfg(20.0, 42.0, 0.3)

    data class Baseline(val mean: Double, val spread: Double, val n: Int) {
        val usable: Boolean get() = n >= MIN_NIGHTS
        /** z robuste : écart absolu moyen → σ gaussien (× 1,253). */
        fun z(v: Double) = (v - mean) / (1.253 * spread)
    }

    const val MIN_NIGHTS = 4
    private const val HALF_CENTER = 14.0
    private const val HALF_SPREAD = 21.0
    private const val WINSOR_K = 3.0
    private const val HARD_OUTLIER_K = 5.0

    /** Moyenne mobile exponentielle winsorisée : demi-vies 14 nuits (centre) et 21 (écart), valeurs aberrantes ignorées. */
    fun baseline(values: List<Double>, cfg: Cfg): Baseline? {
        val lc = 1 - Math.pow(0.5, 1 / HALF_CENTER)
        val ls = 1 - Math.pow(0.5, 1 / HALF_SPREAD)
        var b: Baseline? = null
        val seed = ArrayList<Double>()
        for (v in values) {
            if (v.isNaN() || v < cfg.min || v > cfg.max) continue
            val cur = b
            if (cur == null) {
                seed += v
                if (seed.size >= MIN_NIGHTS) {
                    val m = seed.average()
                    b = Baseline(m, max(cfg.floorSpread, seed.map { abs(it - m) }.average()), seed.size)
                }
                continue
            }
            if (abs(v - cur.mean) > HARD_OUTLIER_K * cur.spread) continue
            val clamped = v.coerceIn(cur.mean - WINSOR_K * cur.spread, cur.mean + WINSOR_K * cur.spread)
            val mean = lc * clamped + (1 - lc) * cur.mean
            b = Baseline(mean, max(cfg.floorSpread, ls * abs(v - mean) + (1 - ls) * cur.spread), cur.n + 1)
        }
        return b
    }

    // ---- Charge ---------------------------------------------------------------------------

    data class Night(val hrv: Double?, val rhr: Double?, val sleepPerf: Double?, val resp: Double?, val temp: Double?)

    data class Charge(val score: Int, val nights: Int, val drivers: Map<String, Double>)

    /**
     * Charge du matin [today] à partir des nuits précédentes ([history], de la plus ancienne à la veille). Null tant que
     * la référence de VFC n'a pas 4 nuits valides ou sans VFC cette nuit (« en calibrage »).
     */
    fun charge(history: List<Night>, today: Night): Charge? {
        val hrvB = baseline(history.mapNotNull { it.hrv }, HRV) ?: return null
        val hrv = today.hrv ?: return null
        val terms = LinkedHashMap<String, Pair<Double, Double>>()
        terms["hrv"] = 0.55 to hrvB.z(hrv)
        today.rhr?.let { v -> baseline(history.mapNotNull { it.rhr }, RHR)?.takeIf { it.usable }?.let { terms["rhr"] = 0.20 to -it.z(v) } }
        today.sleepPerf?.let { terms["sleep"] = 0.15 to (it - 0.85) / 0.12 }
        today.resp?.let { v -> baseline(history.mapNotNull { it.resp }, RESP)?.takeIf { it.usable }?.let { terms["resp"] = 0.05 to -it.z(v) } }
        today.temp?.let { v -> baseline(history.mapNotNull { it.temp }, TEMP)?.takeIf { it.usable }?.let { terms["temp"] = 0.05 to -abs(v - it.mean) / 0.5 } }
        val w = terms.values.sumOf { it.first }
        val z = terms.values.sumOf { it.first * it.second.coerceIn(-4.0, 4.0) } / w
        val score = 100 / (1 + exp(-1.6 * (z - -0.20)))
        return Charge(score.toInt().coerceIn(0, 100), hrvB.n, terms.mapValues { it.value.second })
    }

    /** Bandes de couleur de la Charge : rouge < 34, jaune < 67, vert. */
    fun band(score: Int) = when { score < 34 -> 0; score < 67 -> 1; else -> 2 }

    // ---- Effort ---------------------------------------------------------------------------

    const val STRAIN_D = 7201.0

    /** Fréquence maximale : Tanaka (208 − 0,7 × âge), ou le 99,5e percentile observé s'il est plus haut (≥ 600 mesures). */
    fun hrMax(age: Int, observed: List<Int>): Double {
        val tanaka = 208 - 0.7 * age
        if (observed.size < 600) return tanaka
        val s = observed.sorted()
        return max(tanaka, s[((s.size - 1) * 0.995).toInt()].toDouble())
    }

    /**
     * Effort du jour : [samples] = (horodatage en s, pouls). Chaque mesure compte pour l'intervalle jusqu'à la suivante
     * (2 min au plus). Null sous 60 min de pouls mesuré ou si la réserve cardiaque est invalide.
     */
    fun effort(samples: List<Pair<Long, Int>>, rest: Double, max: Double): Double? {
        if (max <= rest) return null
        val s = samples.sortedBy { it.first }
        var covered = 0.0; var trimp = 0.0
        for (i in s.indices) {
            val dt = ((s.getOrNull(i + 1)?.first ?: (s[i].first + 60)) - s[i].first).coerceIn(0, 120) / 60.0
            covered += dt
            val pct = ((s[i].second - rest) / (max - rest) * 100).coerceIn(0.0, 100.0)
            val zone = when { pct >= 90 -> 5; pct >= 80 -> 4; pct >= 70 -> 3; pct >= 60 -> 2; pct >= 50 -> 1; else -> 0 }
            trimp += zone * dt
        }
        if (covered < 60) return null
        return 100 * ln(trimp + 1) / ln(STRAIN_D)
    }

    /** Minutes passées dans chaque zone de réserve cardiaque (0 = sous 50 %, 1..5), même découpage que l'Effort. */
    fun zones(samples: List<Pair<Long, Int>>, rest: Double, max: Double): IntArray {
        val out = IntArray(6)
        if (max <= rest) return out
        val s = samples.sortedBy { it.first }
        val minutes = DoubleArray(6)
        for (i in s.indices) {
            val dt = ((s.getOrNull(i + 1)?.first ?: (s[i].first + 60)) - s[i].first).coerceIn(0, 120) / 60.0
            val pct = (s[i].second - rest) / (max - rest) * 100
            val z = when { pct >= 90 -> 5; pct >= 80 -> 4; pct >= 70 -> 3; pct >= 60 -> 2; pct >= 50 -> 1; else -> 0 }
            minutes[z] += dt
        }
        for (k in 0..5) out[k] = minutes[k].toInt()
        return out
    }

    // ---- Dette de sommeil -----------------------------------------------------------------

    /**
     * Dette (min) à l'entrée de la nuit qui suit [nights] (14 dernières nuits utiles, de la plus ancienne à la dernière) :
     * dette = 0,55 × max(0, besoin + dette − sommeil). Sous 10 min, elle est considérée comme soldée.
     */
    fun sleepDebt(nights: List<Double?>, needMin: Double = 480.0): Double {
        var debt = 0.0
        nights.filterNotNull().filter { it > 60 }.takeLast(14).forEach { slept -> debt = 0.55 * max(0.0, needMin + debt - slept) }
        return if (debt < 10) 0.0 else debt
    }

    // ---- Charge acquise / aiguë (Effort) --------------------------------------------------

    /** Charge aiguë (moyenne sur 7 j) et chronique (28 j) de l'Effort ; null sans 14 jours mesurés. */
    fun load(efforts: List<Double?>): Pair<Double, Double>? {
        val last28 = efforts.takeLast(28)
        if (last28.count { it != null } < 14) return null
        return last28.takeLast(7).map { it ?: 0.0 }.average() to last28.map { it ?: 0.0 }.average()
    }

    // ---- Lecture des données --------------------------------------------------------------

    private val chargeCache = ConcurrentHashMap<LocalDate, Charge?>()
    private val effortCache = ConcurrentHashMap<LocalDate, Double>()

    fun invalidate() { chargeCache.clear(); effortCache.clear() }

    private fun age(): Int = runCatching { nodomain.freeyourgadget.gadgetbridge.model.ActivityUser().age }.getOrDefault(30).coerceIn(10, 100)

    /** Pouls (s, bpm) du jour pour l'Effort. */
    suspend fun heart(date: LocalDate): List<Pair<Long, Int>> = HealthRepository.minuteHeart(date)

    suspend fun effortOf(ctx: Context, date: LocalDate): Double? {
        if (date.isBefore(LocalDate.now())) effortCache[date]?.let { return it.takeUnless { v -> v.isNaN() } }
        val hr = heart(date)
        val rest = Bridge.healthValue(ctx, Metric.RESTING_HR, date) ?: hr.map { it.second.toDouble() }.sorted().let { s -> s.getOrNull(s.size / 10) } ?: return null
        val v = effort(hr, rest, hrMax(age(), hr.map { it.second }))
        if (date.isBefore(LocalDate.now())) effortCache[date] = v ?: Double.NaN
        return v
    }

    suspend fun zonesOf(ctx: Context, date: LocalDate): IntArray? {
        val hr = heart(date).takeIf { it.isNotEmpty() } ?: return null
        val rest = Bridge.healthValue(ctx, Metric.RESTING_HR, date) ?: hr.map { it.second.toDouble() }.sorted()[hr.size / 10]
        return zones(hr, rest, hrMax(age(), hr.map { it.second }))
    }

    private suspend fun night(ctx: Context, d: LocalDate, need: Double): Night {
        val sleepScore = Bridge.healthValue(ctx, Metric.SLEEP_SCORE, d)
        val sleep = Bridge.healthValue(ctx, Metric.SLEEP, d)
        return Night(Bridge.healthValue(ctx, Metric.HRV, d), Bridge.healthValue(ctx, Metric.RESTING_HR, d),
            sleepScore?.div(100) ?: sleep?.let { (it / need).coerceAtMost(1.0) },
            Bridge.healthValue(ctx, Metric.RESPIRATORY_RATE, d), Bridge.healthValue(ctx, Metric.TEMPERATURE, d))
    }

    suspend fun chargeOf(ctx: Context, date: LocalDate): Charge? {
        if (chargeCache.containsKey(date) && date.isBefore(LocalDate.now())) return chargeCache[date]
        if (!Bridge.hasSource(Metric.HRV)) return null
        val need = sleepNeed()
        val nights = (60L downTo 0L).map { date.minusDays(it) }.map { night(ctx, it, need) }
        val c = charge(nights.dropLast(1), nights.last())
        if (date.isBefore(LocalDate.now())) chargeCache[date] = c
        return c
    }

    suspend fun sleepDebtOf(ctx: Context, date: LocalDate): Double? {
        if (!Bridge.hasSource(Metric.SLEEP)) return null
        val nights = (13L downTo 0L).map { Bridge.healthValue(ctx, Metric.SLEEP, date.minusDays(it)) }
        if (nights.count { it != null } < 3) return null
        return sleepDebt(nights, sleepNeed())
    }

    /** Besoin de sommeil : l'objectif de sommeil du profil (en minutes). */
    fun sleepNeed(): Double = runCatching { nodomain.freeyourgadget.gadgetbridge.model.ActivityUser().sleepDurationGoal.toDouble() }.getOrDefault(480.0)
        .takeIf { it in 300.0..720.0 } ?: 480.0
}
