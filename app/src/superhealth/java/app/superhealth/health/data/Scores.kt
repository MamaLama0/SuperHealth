package app.superhealth.health.data

import android.content.Context
import app.superhealth.bridge.Bridge
import app.superhealth.has
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
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
 *    réparateur (profond + paradoxal), 10 % régularité (SRI) ; besoin personnel = 3e quartile des nuits, jamais sous l'objectif ;
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

    /**
     * [hrv] : VFC de la semaine contre votre zone normale (null : pas assez d'historique, VFC de la nuit seule) ;
     * [luteal] : non null quand les références viennent de la même phase du cycle (lutéale ou non).
     */
    /** [hrvSource] : appareil de la VFC, renseigné quand des nuits d'un autre appareil ont été écartées de la référence. */
    data class Charge(val score: Int, val nights: Int, val drivers: Map<String, Double>, val hrv: HrvTrend? = null, val luteal: Boolean? = null,
                      val hrvSource: String? = null) {
        /** Calculée sans VFC (montre ou appli qui n'en mesure pas) : pouls au repos, sommeil, respiration, température. */
        val withoutHrv: Boolean get() = "hrv" !in drivers
    }

    /**
     * Charge du matin [today] à partir des nuits précédentes ([history], de la plus ancienne à la veille). VFC : moyenne
     * des 7 derniers jours contre votre zone normale ([hrvTrend], Plews) dès 30 jours d'historique ; avant, VFC de la nuit
     * contre une moyenne mobile (méthode NOOP). Sans VFC (règle SuperHealth, pour les montres et applis qui n'en
     * fournissent pas) : mêmes termes et même logistique, poids renormalisés, à condition d'avoir la référence du pouls au
     * repos et au moins un autre terme. Null sinon (« en calibrage »).
     *
     * [phases] (une valeur par nuit, [history] puis [today]) : phase lutéale ou non. Quand la phase du jour est connue et
     * que la même phase compte assez de nuits, VFC, pouls, respiration et température sont comparés à cette phase seulement
     * (en phase lutéale le pouls de nuit et la température montent : Goodale 2019, Alzueta 2022).
     */
    fun charge(history: List<Night>, today: Night, phases: List<Boolean?>? = null): Charge? {
        val phase = phases?.lastOrNull()
        var matched = false
        /** Référence de [sel] sur la même phase si elle a au moins [MIN_NIGHTS] nuits, sinon sur toutes. */
        fun ref(sel: (Night) -> Double?, cfg: Cfg): Baseline? {
            if (phase != null) {
                val same = history.filterIndexed { i, _ -> phases.getOrNull(i) == phase }.mapNotNull(sel)
                baseline(same, cfg)?.takeIf { it.usable }?.let { matched = true; return it }
            }
            return baseline(history.mapNotNull(sel), cfg)
        }
        val trend = hrvTrend(history.map { it.hrv } + today.hrv, phases)
        val hrvB = if (trend == null) ref({ it.hrv }, HRV) else null
        val rhrB = ref({ it.rhr }, RHR)?.takeIf { it.usable }
        val terms = LinkedHashMap<String, Pair<Double, Double>>()
        if (trend != null) terms["hrv"] = 0.55 to trend.z
        else today.hrv?.let { v -> hrvB?.let { terms["hrv"] = 0.55 to it.z(v) } }
        today.rhr?.let { v -> rhrB?.let { terms["rhr"] = 0.20 to -it.z(v) } }
        today.sleepPerf?.let { terms["sleep"] = 0.15 to (it - 0.85) / 0.12 }
        today.resp?.let { v -> ref({ it.resp }, RESP)?.takeIf { it.usable }?.let { terms["resp"] = 0.05 to -it.z(v) } }
        today.temp?.let { v -> ref({ it.temp }, TEMP)?.takeIf { it.usable }?.let { terms["temp"] = 0.05 to -abs(v - it.mean) / 0.5 } }
        if ("hrv" !in terms && ("rhr" !in terms || terms.size < 2)) return null
        val w = terms.values.sumOf { it.first }
        val z = terms.values.sumOf { it.first * it.second.coerceIn(-4.0, 4.0) } / w
        val score = 100 / (1 + exp(-1.6 * (z - -0.20)))
        val nights = trend?.days ?: (hrvB ?: rhrB)!!.n
        return Charge(score.toInt().coerceIn(0, 100), nights, terms.mapValues { it.value.second }, trend,
            phase.takeIf { matched || trend?.matched == true })
    }

    // ---- VFC sur 7 jours (Plews) ----------------------------------------------------------

    /**
     * VFC de la semaine : moyenne des ln(VFC) des 7 derniers jours (au moins 3 mesures, Plews 2014) contre votre zone
     * normale, moyenne ± 0,5 écart-type des ln(VFC) des jours précédents (plus petit changement notable, Plews 2012 ;
     * entraînement guidé par la VFC, Vesterinen 2016). [z] est exprimé dans cette unité : −1 à +1 = dans la zone.
     * [cv] : coefficient de variation des ln(VFC) de la semaine. [overreaching] : sur 3 semaines, moyenne et CV en baisse
     * avec une semaine sous la zone, le profil du surmenage observé par Plews 2012 (étude de cas) — un signe, pas un diagnostic.
     */
    data class HrvTrend(val weekMs: Double, val lowMs: Double, val highMs: Double, val z: Double, val cv: Double,
                        val overreaching: Boolean, val matched: Boolean, val days: Int) {
        /** −1 sous la zone normale, 0 dedans, +1 au-dessus. */
        val zone: Int get() = if (z < -1) -1 else if (z > 1) 1 else 0
    }

    const val HRV_WEEK_MIN = 3
    const val HRV_BASE_DAYS = 30
    private const val HRV_PHASE_DAYS = 14
    private const val HRV_MIN_SD = 0.05

    /** [daily] : VFC (ms) jour par jour jusqu'à aujourd'hui (null : pas de mesure) ; [phases] alignées, comme pour [charge]. */
    fun hrvTrend(daily: List<Double?>, phases: List<Boolean?>? = null): HrvTrend? {
        val ln = daily.map { v -> v?.takeIf { it in HRV.min..HRV.max }?.let(::ln) }
        val n = ln.size
        fun week(end: Int): List<Double>? = (end - 6..end).mapNotNull { ln.getOrNull(it) }.takeIf { it.size >= HRV_WEEK_MIN }
        val wk = week(n - 1) ?: return null
        val before = (0 until n - 7).mapNotNull { i -> ln[i]?.let { i to it } }
        val phase = phases?.lastOrNull()
        val same = if (phase != null) before.filter { phases.getOrNull(it.first) == phase } else emptyList()
        val matched = same.size >= HRV_PHASE_DAYS
        val base = (if (matched) same else before).map { it.second }
        if (!matched && base.size < HRV_BASE_DAYS) return null
        val m = base.average()
        val sd = max(HRV_MIN_SD, sd(base))
        val mean7 = wk.average()
        val z = (mean7 - m) / (0.5 * sd)
        val roll = (n - 21 until n).mapNotNull { e -> week(e)?.let { e.toDouble() to it } }
        val over = z < -1 && roll.size >= 14 &&
            slope(roll.map { it.first to it.second.average() }) < 0 && slope(roll.map { it.first to cv(it.second) }) < 0
        return HrvTrend(exp(mean7), exp(m - 0.5 * sd), exp(m + 0.5 * sd), z, cv(wk), over, matched, base.size)
    }

    private fun sd(xs: List<Double>): Double {
        val m = xs.average()
        return kotlin.math.sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1).coerceAtLeast(1))
    }

    private fun cv(xs: List<Double>): Double = sd(xs) / xs.average() * 100

    /** Pente des moindres carrés. */
    private fun slope(pts: List<Pair<Double, Double>>): Double {
        val mx = pts.sumOf { it.first } / pts.size; val my = pts.sumOf { it.second } / pts.size
        val den = pts.sumOf { (it.first - mx) * (it.first - mx) }
        return if (den == 0.0) 0.0 else pts.sumOf { (it.first - mx) * (it.second - my) } / den
    }

    /** Bandes de couleur de la Charge : rouge < 34, jaune < 67, vert. */
    fun band(score: Int) = when { score < 34 -> 0; score < 67 -> 1; else -> 2 }

    // ---- Effort ---------------------------------------------------------------------------

    const val STRAIN_D = 7201.0

    /**
     * Fréquence maximale : Tanaka (2001, 208 − 0,7 × âge) ; chez la femme Gulati (2010, Circulation 122:130, 5 437 femmes :
     * 206 − 0,88 × âge, la formule « masculine » la surestime). Le 99,5e percentile observé l'emporte s'il est plus haut
     * (≥ 600 mesures), ce qui couvre les femmes entraînées que Gulati sous-estime.
     */
    fun hrMax(age: Int, observed: List<Int>, female: Boolean = false): Double {
        val tanaka = if (female) 206 - 0.88 * age else 208 - 0.7 * age
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
        val (trimp, covered) = trimp(samples, rest, max)
        if (covered < 60) return null
        return effortFromTrimp(trimp)
    }

    fun effortFromTrimp(trimp: Double): Double = 100 * ln(trimp + 1) / ln(STRAIN_D)

    /** Zones × minutes (Edwards) et minutes couvertes par le pouls. */
    fun trimp(samples: List<Pair<Long, Int>>, rest: Double, max: Double): Pair<Double, Double> {
        if (max <= rest) return 0.0 to 0.0
        val s = samples.sortedBy { it.first }
        var covered = 0.0; var trimp = 0.0
        for (i in s.indices) {
            val dt = ((s.getOrNull(i + 1)?.first ?: (s[i].first + 60)) - s[i].first).coerceIn(0, 120) / 60.0
            covered += dt
            val pct = ((s[i].second - rest) / (max - rest) * 100).coerceIn(0.0, 100.0)
            val zone = when { pct >= 90 -> 5; pct >= 80 -> 4; pct >= 70 -> 3; pct >= 60 -> 2; pct >= 50 -> 1; else -> 0 }
            trimp += zone * dt
        }
        return trimp to covered
    }

    /**
     * Charge ressentie d'une séance (Foster et al. 2001, session-RPE) : effort ressenti sur l'échelle CR-10 × minutes. Elle
     * suit les zones cardiaques d'Edwards (r ≈ 0,75–0,90 dans l'étude) ; ramenée aux 5 zones de l'Effort par RPE / 2
     * (règle SuperHealth). Elle ne compte qu'au-delà de ce que le pouls a déjà mesuré pendant la séance : la musculation, où
     * le pouls reste bas, pèse enfin dans l'Effort, sans compter deux fois une séance suivie au cardio.
     */
    fun perceivedTrimp(rpe: Int, minutes: Double): Double = rpe.coerceIn(0, 10) / 2.0 * minutes.coerceAtLeast(0.0)

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

    /**
     * Charge aiguë (7 j) et chronique (28 j) de l'Effort en moyennes mobiles exponentielles, λ = 2 / (N + 1) : plus
     * sensibles que les moyennes glissantes, les derniers jours pesant le plus (Williams et al. 2017, Br J Sports Med
     * 51:209 ; Murray et al. 2017, Br J Sports Med 51:749). Null sans 14 jours mesurés.
     */
    fun load(efforts: List<Double?>): Pair<Double, Double>? {
        if (efforts.takeLast(28).count { it != null } < 14) return null
        return ewma(efforts, 7).last() to ewma(efforts, 28).last()
    }

    /** Moyenne mobile exponentielle jour par jour (jour sans mesure = 0), amorcée sur la moyenne de la fenêtre. */
    fun ewma(values: List<Double?>, days: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val k = 2.0 / (days + 1)
        var m = values.take(days).map { it ?: 0.0 }.average()
        return values.map { v -> m = k * (v ?: 0.0) + (1 - k) * m; m }
    }

    // ---- Lecture des données --------------------------------------------------------------

    private val chargeCache = ConcurrentHashMap<LocalDate, Charge?>()
    private val effortCache = ConcurrentHashMap<LocalDate, Double>()

    fun invalidate() { chargeCache.clear(); effortCache.clear(); restCache.clear(); habits = null; app.superhealth.wellbeing.BodyAlert.invalidate() }

    private fun age(): Int = runCatching { nodomain.freeyourgadget.gadgetbridge.model.ActivityUser().age }.getOrDefault(30).coerceIn(10, 100)
    private fun female(): Boolean = runCatching { nodomain.freeyourgadget.gadgetbridge.model.ActivityUser().gender ==
        nodomain.freeyourgadget.gadgetbridge.model.ActivityUser.GENDER_FEMALE }.getOrDefault(false)

    /** Pouls (s, bpm) du jour pour l'Effort. */
    suspend fun heart(date: LocalDate): List<Pair<Long, Int>> = Bridge.minuteHeart(date)

    suspend fun effortOf(ctx: Context, date: LocalDate): Double? {
        if (date.isBefore(LocalDate.now())) effortCache[date]?.let { return it.takeUnless { v -> v.isNaN() } }
        val hr = heart(date)
        val rest = Bridge.healthValue(ctx, Metric.RESTING_HR, date) ?: hr.map { it.second.toDouble() }.sorted().let { s -> s.getOrNull(s.size / 10) }
        val max = hrMax(age(), hr.map { it.second }, female())
        val (hrTrimp, covered) = if (rest != null) trimp(hr, rest, max) else 0.0 to 0.0
        // séances notées (effort ressenti) : leur charge ressentie, au-delà de ce que le pouls a mesuré pendant la séance
        val from = HealthRepository.startMs(date); val to = HealthRepository.endMs(date)
        val extra = app.superhealth.training.WorkoutApp.store.state.value.history.filter { it.startTime in from until to && it.rpe != null }.sumOf { w ->
            val end = w.startTime + w.durationSec * 1000
            val inside = if (rest != null) trimp(hr.filter { it.first * 1000 in w.startTime until end }, rest, max).first else 0.0
            maxOf(0.0, perceivedTrimp(w.rpe!!, w.durationSec / 60.0) - inside)
        }
        val v = if (covered < 60 && extra <= 0) null else effortFromTrimp(hrTrimp + extra)
        if (date.isBefore(LocalDate.now())) effortCache[date] = v ?: Double.NaN
        return v
    }

    suspend fun zonesOf(ctx: Context, date: LocalDate): IntArray? {
        val hr = heart(date).takeIf { it.isNotEmpty() } ?: return null
        val rest = Bridge.healthValue(ctx, Metric.RESTING_HR, date) ?: hr.map { it.second.toDouble() }.sorted()[hr.size / 10]
        return zones(hr, rest, hrMax(age(), hr.map { it.second }, female()))
    }

    private suspend fun night(ctx: Context, d: LocalDate, need: Double): Night {
        val sleepScore = Bridge.healthValue(ctx, Metric.SLEEP_SCORE, d)
        val sleep = Bridge.healthValue(ctx, Metric.SLEEP, d)
        return Night(Bridge.healthValue(ctx, Metric.HRV, d), Bridge.healthValue(ctx, Metric.RESTING_HR, d),
            sleepScore?.div(100) ?: sleep?.let { (it / need).coerceAtMost(1.0) },
            Bridge.healthValue(ctx, Metric.RESPIRATORY_RATE, d), Bridge.healthValue(ctx, Metric.TEMPERATURE, d))
    }

    /**
     * Charge de chacun des [days] jours jusqu'à [date], nuits lues une seule fois (apprentissage de la Forme adaptative) ;
     * même calcul que [chargeOf], jour par jour.
     */
    suspend fun chargeSeries(ctx: Context, date: LocalDate, days: Int): List<Charge?> {
        if (!Bridge.hasSource(Metric.HRV) && !Bridge.hasSource(Metric.RESTING_HR)) return List(days) { null }
        val need = sleepHabits(ctx).needMin
        val dates = ((days + 66).toLong() downTo 0L).map { date.minusDays(it) }
        val nights = sameHrvSource(dates, dates.map { night(ctx, it, need) }, hrvSource(ctx, date)).first
        val ph = phases(dates)
        return (67 until dates.size).map { i -> charge(nights.subList(i - 67, i), nights[i], ph?.subList(i - 67, i + 1)) }
    }

    suspend fun chargeOf(ctx: Context, date: LocalDate): Charge? {
        if (chargeCache.containsKey(date) && date.isBefore(LocalDate.now())) return chargeCache[date]
        if (!Bridge.hasSource(Metric.HRV) && !Bridge.hasSource(Metric.RESTING_HR)) return null
        val need = sleepHabits(ctx).needMin
        val dates = (67L downTo 0L).map { date.minusDays(it) }
        val src = hrvSource(ctx, date)
        val nights = sameHrvSource(dates, dates.map { night(ctx, it, need) }, src)
        val c = charge(nights.first.dropLast(1), nights.first.last(), phases(dates))?.copy(hrvSource = src.takeIf { nights.second })
        if (date.isBefore(LocalDate.now())) chargeCache[date] = c
        return c
    }

    /**
     * VFC liée à la montre qui la mesure : chaque marque a sa méthode (VFC de nuit en RMSSD chez Garmin, Polar ou Huami,
     * mesures ponctuelles en SDNN sur l'Apple Watch), et les valeurs ne se comparent pas d'un appareil à l'autre. La source
     * de chaque jour est notée ; les nuits d'une autre source sortent de la référence de VFC (pouls, sommeil, respiration
     * et température restent comparés à toutes les nuits). Les jours d'avant le premier relevé comptent comme la source
     * actuelle. Seconde valeur : vrai si des nuits d'une autre source ont été écartées.
     */
    private fun sameHrvSource(dates: List<LocalDate>, nights: List<Night>, src: String?): Pair<List<Night>, Boolean> {
        if (src == null) return nights to false
        val log = app.superhealth.SuperPrefs.settings.value.hrvSources
        var dropped = false
        return nights.mapIndexed { i, n ->
            val k = log[dates[i].toEpochDay()]
            if (k != null && k != src && n.hrv != null) { dropped = true; n.copy(hrv = null) } else n
        } to dropped
    }

    /** Source de la VFC du jour (appareil, sinon appli) ; notée pour aujourd'hui (120 derniers jours gardés). */
    private suspend fun hrvSource(ctx: Context, date: LocalDate): String? {
        val d = runCatching { Bridge.dataSource(Metric.HRV, date) }.getOrNull() ?: return null
        val key = d.device ?: d.app ?: return null
        val day = date.toEpochDay()
        if (date == LocalDate.now() && Bridge.healthValue(ctx, Metric.HRV, date) != null &&
            app.superhealth.SuperPrefs.settings.value.hrvSources[day] != key)
            app.superhealth.SuperPrefs.update { s -> s.copy(hrvSources = (s.hrvSources + (day to key)).filterKeys { it > day - 120 }) }
        return key
    }

    /** Phase lutéale ou non de chaque jour, quand le suivi du cycle est activé et connaît la durée de vos cycles. */
    fun phases(dates: List<LocalDate>): List<Boolean?>? {
        if (!app.superhealth.SuperPrefs.settings.value.has(app.superhealth.Feature.CYCLE)) return null
        val days = app.superhealth.wellbeing.Cycle.days.value
        val length = app.superhealth.wellbeing.Cycle.info(d = days)?.length ?: return null
        return dates.map { app.superhealth.wellbeing.Cycle.luteal(it.toEpochDay(), days, length) }
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

    /**
     * Indice de régularité du sommeil (SRI, Phillips et al. 2017 ; prédit la mortalité mieux que la durée, Windred et al.
     * 2024) : probabilité d'être dans le même état (endormi ou éveillé) à 24 h d'écart, minute par minute, ramenée à
     * −100…100 (100 = horaires identiques chaque jour). [windows] : minutes endormies de chaque fenêtre de midi à midi,
     * null quand la nuit manque ; seules les paires de jours consécutifs mesurés comptent. Null sous [SRI_MIN_PAIRS] paires.
     */
    fun sri(windows: List<BooleanArray?>): Double? {
        var agree = 0L; var pairs = 0
        for (i in 0 until windows.size - 1) {
            val a = windows[i] ?: continue; val b = windows[i + 1] ?: continue
            pairs++
            for (m in 0 until 1440) if (a[m] == b[m]) agree++
        }
        if (pairs < SRI_MIN_PAIRS) return null
        return -100 + 200.0 * agree / (1440.0 * pairs)
    }

    const val SRI_MIN_PAIRS = 5

    /** Minutes endormies de la fenêtre de midi (veille) à midi ([date]) ; null sans nuit enregistrée. */
    suspend fun sleepWindow(date: LocalDate): BooleanArray? {
        val night = Bridge.sleepNight(date)?.takeIf { it.isNotEmpty() } ?: return null
        val start = HealthRepository.startMs(date) - 12 * 3_600_000L
        val out = BooleanArray(1440)
        night.filter { it.kind != nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.AWAKE_SLEEP }.forEach { p ->
            val a = ((p.start - start) / 60_000).toInt().coerceIn(0, 1440); val b = ((p.end - start) / 60_000).toInt().coerceIn(0, 1440)
            for (m in a until b) out[m] = true
        }
        return out
    }

    /** SRI des 14 dernières paires de jours jusqu'à [date]. */
    suspend fun sriOf(date: LocalDate): Double? = sri((14L downTo 0L).map { sleepWindow(date.minusDays(it)) })

    /**
     * Repos (0–100) d'une nuit avec phases : 0,50 × durée / besoin (plafonnée à 100) + 0,20 × efficacité (endormi / au lit)
     * + 0,20 × part réparatrice ((profond + paradoxal) / endormi rapportée à 50 %, plafonnée, × facteur de sommeil profond
     * de 0,5 à 1 selon sa part rapportée à 13 %) + 0,10 × régularité (SRI ramené à 0–1, 0,5 sans historique). Null sans sommeil.
     */
    fun rest(asleepMin: Double, inBedMin: Double, deepMin: Double, remMin: Double, needMin: Double, consistency: Double?): Double? =
        restParts(asleepMin, inBedMin, deepMin, remMin, needMin, consistency)?.total

    /** Les quatre parties de Repos (0–100 chacune), affichées sur la carte Qualité et l'écran Sommeil. */
    data class RestParts(val duration: Double, val efficiency: Double, val restorative: Double, val regular: Double) {
        val total: Double get() = 0.50 * duration + 0.20 * efficiency + 0.20 * restorative + 0.10 * regular
    }

    fun restParts(asleepMin: Double, inBedMin: Double, deepMin: Double, remMin: Double, needMin: Double, consistency: Double?): RestParts? {
        if (asleepMin <= 0 || inBedMin <= 0) return null
        val duration = min(100.0, asleepMin / needMin.coerceAtLeast(6.0) * 100)
        val efficiency = (asleepMin / inBedMin * 100).coerceIn(0.0, 100.0)
        val deepFactor = 0.5 + 0.5 * (deepMin / asleepMin / DEEP_TARGET).coerceIn(0.0, 1.0)
        val restorative = min(100.0, (deepMin + remMin) / asleepMin / 0.50 * 100) * deepFactor
        val regular = ((consistency ?: 0.5) * 100).coerceIn(0.0, 100.0)
        return RestParts(duration, efficiency, restorative, regular)
    }

    /**
     * Repos en mots, pour l'accueil et les widgets (le nombre reste sur l'écran Sommeil) : un score chiffré bas vu au
     * réveil dégrade la journée même quand il est faux (Gavriloff et al. 2018). Courte : sous 85 % du besoin et sous 75 ;
     * Reposante : 75 et plus ; Correcte : 55 et plus ; Agitée sinon (assez longue, mais éveils ou peu de sommeil réparateur).
     */
    enum class RestWord(val label: String) { RESTFUL("Reposante"), FAIR("Correcte"), SHORT("Courte"), RESTLESS("Agitée") }

    fun restWord(score: Double, asleepMin: Double, needMin: Double): RestWord = when {
        asleepMin < 0.85 * needMin && score < 75 -> RestWord.SHORT
        score >= 75 -> RestWord.RESTFUL
        score >= 55 -> RestWord.FAIR
        else -> RestWord.RESTLESS
    }

    /**
     * La nuit comparée à vos habitudes (comme Polar compare chaque partie du sommeil à votre moyenne) : durée endormi,
     * efficacité, part réparatrice (profond + paradoxal) et temps éveillé, contre les 28 nuits précédentes (au moins 14).
     * Habituel = médiane ± 1 écart robuste (écart absolu médian × 1,4826), avec un écart minimal par partie ; au-delà,
     * −1 (en dessous) ou +1 (au-dessus).
     */
    enum class SleepPart { DURATION, EFFICIENCY, RESTORATIVE, AWAKE }

    data class NightParts(val asleepMin: Double, val efficiency: Double, val restorative: Double?, val awakeMin: Double) {
        fun of(p: SleepPart): Double? = when (p) {
            SleepPart.DURATION -> asleepMin; SleepPart.EFFICIENCY -> efficiency; SleepPart.RESTORATIVE -> restorative; SleepPart.AWAKE -> awakeMin
        }
    }

    private fun minSpread(p: SleepPart) = when (p) { SleepPart.DURATION -> 20.0; SleepPart.EFFICIENCY -> 0.03; SleepPart.RESTORATIVE -> 0.04; SleepPart.AWAKE -> 10.0 }

    fun compareToUsual(last: NightParts, history: List<NightParts>): Map<SleepPart, Int>? {
        if (history.size < 14) return null
        return SleepPart.entries.mapNotNull { p ->
            val v = last.of(p) ?: return@mapNotNull null
            val xs = history.mapNotNull { it.of(p) }.sorted().takeIf { it.size >= 14 } ?: return@mapNotNull null
            val med = xs[xs.size / 2]
            val sd = maxOf(minSpread(p), xs.map { abs(it - med) }.sorted()[xs.size / 2] * 1.4826)
            p to when { v < med - sd -> -1; v > med + sd -> 1; else -> 0 }
        }.toMap()
    }

    /** Parties de la nuit qui se termine le jour [date] ; part réparatrice seulement avec des phases. */
    suspend fun nightParts(date: LocalDate): NightParts? {
        val n = Bridge.sleepNight(date)?.takeIf { it.isNotEmpty() } ?: return null
        fun minutes(k: nodomain.freeyourgadget.gadgetbridge.model.ActivityKind) = n.filter { it.kind == k }.sumOf { it.end - it.start } / 60000.0
        val awake = minutes(nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.AWAKE_SLEEP)
        val asleep = n.sumOf { it.end - it.start } / 60000.0 - awake
        val inBed = (n.maxOf { it.end } - n.minOf { it.start }) / 60000.0
        if (asleep < 120 || inBed <= 0) return null
        val restorative = if (Bridge.hasStages(n)) (minutes(nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.DEEP_SLEEP) +
            minutes(nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.REM_SLEEP)) / asleep else null
        return NightParts(asleep, (asleep / inBed).coerceIn(0.0, 1.0), restorative, (inBed - asleep).coerceAtLeast(0.0))
    }

    /** Besoin de sommeil personnel (min) et régularité (SRI des 2 dernières semaines ramené à 0–1), relus une fois par jour. */
    data class SleepHabits(val needMin: Double, val consistency: Double?, val sri: Double? = null)

    @Volatile private var habits: Pair<LocalDate, SleepHabits>? = null

    suspend fun sleepHabits(ctx: Context): SleepHabits {
        val today = LocalDate.now()
        habits?.takeIf { it.first == today }?.let { return it.second }
        val nights = (60L downTo 1L).mapNotNull { Bridge.healthValue(ctx, Metric.SLEEP, today.minusDays(it)) }
        val sri = runCatching { sriOf(today) }.getOrNull()
        val h = SleepHabits(personalNeed(nights, sleepNeed()), sri?.let { (it / 100).coerceIn(0.0, 1.0) }, sri)
        habits = today to h
        return h
    }

    private val restCache = ConcurrentHashMap<LocalDate, Double>()

    /** Repos de la nuit qui se termine le jour [date] (phases de la montre ou de l'appli Health Connect) ; null sans phases. */
    suspend fun restOf(ctx: Context, date: LocalDate): Double? {
        val past = date.isBefore(LocalDate.now())
        if (past) restCache[date]?.let { return it.takeUnless { v -> v.isNaN() } }
        val v = restPartsOf(ctx, date)?.total
        if (past) restCache[date] = v ?: Double.NaN
        return v
    }

    /** Parties de Repos de la nuit qui se termine le jour [date] ; null sans phases. */
    suspend fun restPartsOf(ctx: Context, date: LocalDate): RestParts? {
        val n = Bridge.sleepNight(date)?.takeIf { Bridge.hasStages(it) } ?: return null
        fun minutes(vararg k: nodomain.freeyourgadget.gadgetbridge.model.ActivityKind) = n.filter { it.kind in k }.sumOf { it.end - it.start } / 60000.0
        val awake = minutes(nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.AWAKE_SLEEP)
        val asleep = n.sumOf { it.end - it.start } / 60000.0 - awake
        val h = sleepHabits(ctx)
        return restParts(asleep, asleep + awake, minutes(nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.DEEP_SLEEP),
            minutes(nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.REM_SLEEP), h.needMin, h.consistency)
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
        if (date.dayOfWeek != java.time.DayOfWeek.SATURDAY || date.isAfter(LocalDate.now())) return null
        val days = (6L downTo 0L).map { date.minusDays(it) }
        val rhr = days.mapNotNull { Bridge.healthValue(ctx, Metric.RESTING_HR, it) }.sorted()
        if (rhr.size < 4) return null
        val efforts = days.mapNotNull { runCatching { effortOf(ctx, it) }.getOrNull() }
        val p = app.superhealth.nutrition.NutriApp.repository.prefs.profile.value
        val age = (date.year - p.birthYear).toDouble().takeIf { it in 10.0..100.0 } ?: return null
        return fitnessAge(age, p.sex.name == "FEMALE", rhr[rhr.size / 2], activityIndex(efforts))
    }

    /** Besoin de sommeil : l'objectif de sommeil du profil (en minutes). */
    fun sleepNeed(): Double = runCatching { nodomain.freeyourgadget.gadgetbridge.model.ActivityUser().sleepDurationGoal.toDouble() }.getOrDefault(480.0)
        .takeIf { it in 300.0..720.0 } ?: 480.0
}
