package app.superhealth.health.data

import app.superhealth.platform.*

import app.superhealth.platform.Context
import app.superhealth.bridge.Bridge
import app.superhealth.platform.LocalDate
import app.superhealth.platform.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Scores repris des méthodes publiées que documente NOOP (docs/ANALYTICS.md), calculés pour toute source (Garmin, WHOOP,
 * autres montres, Health Connect) :
 *  - **Charge** (récupération, 0–100) : z-scores robustes de la VFC (55 %), du pouls au repos (20 %), du sommeil (15 %),
 *    de la respiration (5 %) et de l'écart de température (5 %) contre des références personnelles (moyennes mobiles
 *    exponentielles winsorisées), passés dans une logistique ancrée à 58 % pour z = 0 ; sans VFC, poids renormalisés ;
 *  - **Effort** (charge cardiovasculaire du jour, 0–100) : TRIMP d'Edwards (zones de 50/60/70/80/90 % de la réserve
 *    cardiaque de Karvonen, poids 1 à 5 par minute), compressé en 100 · ln(TRIMP + 1) / ln(7201) ;
 *  - **Dette de sommeil** : 55 % du besoin non couvert reporté sur la nuit suivante, sur 14 nuits, sans « crédit » ;
 *  - **Repos** (score de sommeil quand la montre n'en donne pas) : 50 % durée / besoin, 20 % efficacité, 20 % sommeil
 *    réparateur (profond + paradoxal), 10 % régularité ; besoin personnel = 3e quartile des nuits, jamais sous l'objectif ;
 *  - **Âge de forme** : modèle sans test d'effort de Nes et al. 2011 (HUNT), inversé à pouls de repos 65 et activité 5.
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

    data class Charge(val score: Int, val nights: Int, val drivers: Map<String, Double>) {
        /** Calculée sans VFC (montre ou appli qui n'en mesure pas) : pouls au repos, sommeil, respiration, température. */
        val withoutHrv: Boolean get() = "hrv" !in drivers
    }

    /**
     * Charge du matin [today] à partir des nuits précédentes ([history], de la plus ancienne à la veille). Avec la VFC
     * (référence d'au moins 4 nuits) : méthode NOOP. Sans VFC (règle SuperHealth, pour les montres et applis qui n'en
     * fournissent pas) : mêmes termes et même logistique, poids renormalisés, à condition d'avoir la référence du pouls au
     * repos et au moins un autre terme. Null sinon (« en calibrage »).
     */
    fun charge(history: List<Night>, today: Night): Charge? {
        val hrvB = baseline(history.mapNotNull { it.hrv }, HRV)
        val rhrB = baseline(history.mapNotNull { it.rhr }, RHR)?.takeIf { it.usable }
        val terms = LinkedHashMap<String, Pair<Double, Double>>()
        today.hrv?.let { v -> hrvB?.let { terms["hrv"] = 0.55 to it.z(v) } }
        today.rhr?.let { v -> rhrB?.let { terms["rhr"] = 0.20 to -it.z(v) } }
        today.sleepPerf?.let { terms["sleep"] = 0.15 to (it - 0.85) / 0.12 }
        today.resp?.let { v -> baseline(history.mapNotNull { it.resp }, RESP)?.takeIf { it.usable }?.let { terms["resp"] = 0.05 to -it.z(v) } }
        today.temp?.let { v -> baseline(history.mapNotNull { it.temp }, TEMP)?.takeIf { it.usable }?.let { terms["temp"] = 0.05 to -abs(v - it.mean) / 0.5 } }
        if ("hrv" !in terms && ("rhr" !in terms || terms.size < 2)) return null
        val w = terms.values.sumOf { it.first }
        val z = terms.values.sumOf { it.first * it.second.coerceIn(-4.0, 4.0) } / w
        val score = 100 / (1 + exp(-1.6 * (z - -0.20)))
        return Charge(score.toInt().coerceIn(0, 100), (hrvB ?: rhrB)!!.n, terms.mapValues { it.value.second })
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

    fun invalidate() { chargeCache.clear(); effortCache.clear(); restCache.clear(); habits = null }

    private fun age(): Int = runCatching { app.superhealth.health.data.ActivityUser().age }.getOrDefault(30).coerceIn(10, 100)

    /** Pouls (s, bpm) du jour pour l'Effort. */
    suspend fun heart(date: LocalDate): List<Pair<Long, Int>> = Bridge.minuteHeart(date)

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
        if (!Bridge.hasSource(Metric.HRV) && !Bridge.hasSource(Metric.RESTING_HR)) return null
        val need = sleepHabits(ctx).needMin
        val nights = (60L downTo 0L).map { date.minusDays(it) }.map { night(ctx, it, need) }
        val c = charge(nights.dropLast(1), nights.last())
        if (date.isBefore(LocalDate.now())) chargeCache[date] = c
        return c
    }

    suspend fun sleepDebtOf(ctx: Context, date: LocalDate): Double? {
        if (!Bridge.hasSource(Metric.SLEEP)) return null
        val nights = (13L downTo 0L).map { Bridge.healthValue(ctx, Metric.SLEEP, date.minusDays(it)) }
        if (nights.count { it != null } < 3) return null
        return sleepDebt(nights, sleepHabits(ctx).needMin)
    }

    // ---- Repos (score de sommeil calculé) --------------------------------------------------

    const val NEED_MIN_NIGHTS = 7
    const val NEED_MAX_MIN = 570.0
    const val DEEP_TARGET = 0.13

    /**
     * Besoin personnel (min) : 3e quartile (interpolé) des nuits — celles où le sommeil n'a pas été écourté, pas la moyenne
     * qu'une privation chronique tire vers le bas —, jamais sous [floorMin] (l'objectif du profil), au plus 9 h 30.
     * Sous 7 nuits : [floorMin].
     */
    fun personalNeed(nightsMin: List<Double>, floorMin: Double): Double {
        val xs = nightsMin.filter { it > 60 }.sorted()
        if (xs.size < NEED_MIN_NIGHTS) return floorMin.coerceAtMost(NEED_MAX_MIN)
        val pos = 0.75 * (xs.size - 1)
        val lo = pos.toInt(); val hi = minOf(lo + 1, xs.size - 1)
        return (xs[lo] + (pos - lo) * (xs[hi] - xs[lo])).coerceIn(floorMin, maxOf(floorMin, NEED_MAX_MIN))
    }

    /** Régularité (0–1) : 1 − coefficient de variation des durées de nuit ; null sous 3 nuits. */
    fun consistency(nightsMin: List<Double>): Double? {
        val xs = nightsMin.filter { it > 60 }
        if (xs.size < 3) return null
        val m = xs.average()
        val sd = kotlin.math.sqrt(xs.sumOf { (it - m) * (it - m) } / xs.size)
        return (1 - sd / m).coerceIn(0.0, 1.0)
    }

    /**
     * Repos (0–100) d'une nuit avec phases : 0,50 × durée / besoin (plafonnée à 100) + 0,20 × efficacité (endormi / au lit)
     * + 0,20 × part réparatrice ((profond + paradoxal) / endormi rapportée à 50 %, plafonnée, × facteur de sommeil profond
     * de 0,5 à 1 selon sa part rapportée à 13 %) + 0,10 × régularité (0,5 sans historique). Null sans sommeil.
     */
    fun rest(asleepMin: Double, inBedMin: Double, deepMin: Double, remMin: Double, needMin: Double, consistency: Double?): Double? {
        if (asleepMin <= 0 || inBedMin <= 0) return null
        val duration = min(100.0, asleepMin / needMin.coerceAtLeast(6.0) * 100)
        val efficiency = (asleepMin / inBedMin * 100).coerceIn(0.0, 100.0)
        val deepFactor = 0.5 + 0.5 * (deepMin / asleepMin / DEEP_TARGET).coerceIn(0.0, 1.0)
        val restorative = min(100.0, (deepMin + remMin) / asleepMin / 0.50 * 100) * deepFactor
        val regular = ((consistency ?: 0.5) * 100).coerceIn(0.0, 100.0)
        return 0.50 * duration + 0.20 * efficiency + 0.20 * restorative + 0.10 * regular
    }

    /** Besoin de sommeil personnel (min) et régularité des 28 dernières nuits, relus une fois par jour. */
    data class SleepHabits(val needMin: Double, val consistency: Double?)

    private var habits: Pair<LocalDate, SleepHabits>? = null

    suspend fun sleepHabits(ctx: Context): SleepHabits {
        val today = LocalDate.now()
        habits?.takeIf { it.first == today }?.let { return it.second }
        val nights = (60L downTo 1L).mapNotNull { Bridge.healthValue(ctx, Metric.SLEEP, today.minusDays(it)) }
        val h = SleepHabits(personalNeed(nights, sleepNeed()), consistency(nights.takeLast(28)))
        habits = today to h
        return h
    }

    private val restCache = ConcurrentHashMap<LocalDate, Double>()

    /** Repos de la nuit qui se termine le jour [date] (phases de la montre ou de l'appli Health Connect) ; null sans phases. */
    suspend fun restOf(ctx: Context, date: LocalDate): Double? {
        val past = date.isBefore(LocalDate.now())
        if (past) restCache[date]?.let { return it.takeUnless { v -> v.isNaN() } }
        val night = Bridge.sleepNight(date)?.takeIf { Bridge.hasStages(it) }
        val v = night?.let { n ->
            fun minutes(vararg k: app.superhealth.health.data.ActivityKind) = n.filter { it.kind in k }.sumOf { it.end - it.start } / 60000.0
            val awake = minutes(app.superhealth.health.data.ActivityKind.AWAKE_SLEEP)
            val asleep = n.sumOf { it.end - it.start } / 60000.0 - awake
            val h = sleepHabits(ctx)
            rest(asleep, asleep + awake, minutes(app.superhealth.health.data.ActivityKind.DEEP_SLEEP),
                minutes(app.superhealth.health.data.ActivityKind.REM_SLEEP), h.needMin, h.consistency)
        }
        if (past) restCache[date] = v ?: Double.NaN
        return v
    }

    // ---- Âge de forme -------------------------------------------------------------------------

    /**
     * Âge de forme (Nes et al. 2011, HUNT, variante tour de taille) : âge auquel une personne de référence (pouls de repos 65,
     * indice d'activité 5) aurait la même VO₂ max estimée ; le tour de taille s'annule. Hommes : âge 0,296, activité 0,226,
     * pouls 0,155 ; femmes : 0,247, 0,198, 0,114. Borné à 20–80 ans.
     */
    fun fitnessAge(age: Double, female: Boolean, restingHr: Double, activity: Double): Double {
        val (a, pa, hr) = if (female) Triple(0.247, 0.198, 0.114) else Triple(0.296, 0.226, 0.155)
        return (age + (hr * (restingHr - 65) - pa * (activity - 5)) / a).coerceIn(20.0, 80.0)
    }

    /**
     * Indice d'activité (échelle du questionnaire HUNT 1, 0–15) reconstitué à partir de l'Effort de 7 jours : fréquence
     * (jours ≥ 30 : 1 → 0,5, 2 → 1, 3–4 → 2,5, 5+ → 5) × intensité-durée (Effort moyen de ces jours / 30, borné à 3).
     */
    fun activityIndex(efforts: List<Double>): Double {
        val active = efforts.filter { it >= 30 }
        val freq = when (active.size) { 0 -> 0.0; 1 -> 0.5; 2 -> 1.0; 3, 4 -> 2.5; else -> 5.0 }
        return if (freq == 0.0) 0.0 else freq * (active.average() / 30).coerceIn(0.0, 3.0)
    }

    /**
     * Âge de forme de la semaine qui finit le samedi [date] (null les autres jours) : médiane du pouls de repos (au moins
     * 4 nuits sur 7) et indice d'activité des 7 jours.
     */
    suspend fun fitnessAgeOf(ctx: Context, date: LocalDate): Double? {
        if (date.dayOfWeek != DayOfWeek.SATURDAY || date.isAfter(LocalDate.now())) return null
        val days = (6L downTo 0L).map { date.minusDays(it) }
        val rhr = days.mapNotNull { Bridge.healthValue(ctx, Metric.RESTING_HR, it) }.sorted()
        if (rhr.size < 4) return null
        val efforts = days.mapNotNull { runCatching { effortOf(ctx, it) }.getOrNull() }
        val p = app.superhealth.nutrition.NutriApp.repository.prefs.profile.value
        val age = (date.year - p.birthYear).toDouble().takeIf { it in 10.0..100.0 } ?: return null
        return fitnessAge(age, p.sex.name == "FEMALE", rhr[rhr.size / 2], activityIndex(efforts))
    }

    /** Besoin de sommeil : l'objectif de sommeil du profil (en minutes). */
    fun sleepNeed(): Double = runCatching { app.superhealth.health.data.ActivityUser().sleepDurationGoal.toDouble() }.getOrDefault(480.0)
        .takeIf { it in 300.0..720.0 } ?: 480.0
}
