package app.superhealth.wellbeing

import app.superhealth.platform.*

import app.superhealth.platform.Context
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.Metric
import app.superhealth.health.data.Scores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.superhealth.platform.LocalDate
import kotlin.math.abs

/**
 * « Ton corps lutte peut-être » : pouls au repos, respiration et température de la nuit au-dessus de votre plage
 * habituelle, VFC en dessous, deux nuits de suite (logique de l'alerte de Mishra et al. 2020 : hausse du pouls de repos
 * avant les symptômes chez 63 % des cas). Plage = médiane des 28 nuits précédentes ± 2 écarts absolus médians (× 1,4826),
 * avec un écart minimal par mesure ; en phase lutéale, seules les nuits de la même phase servent de référence.
 *
 * Nuance (règle SuperHealth) : pouls et VFC réagissent aussi à l'effort, à l'alcool et aux réveils. Ils ne comptent pas
 * s'ils s'expliquent par la veille ou la nuit : Effort de la veille au-dessus de vos jours habituels, effort dans les
 * 3 heures avant le coucher, nuit agitée (au moins 45 min éveillé ou 300 pas pendant la nuit), alcool, journée stressante
 * (le stress psychologique élève le pouls et abaisse la VFC, Kim et al. 2018) ou voyage notés au journal la veille.
 * La respiration et la température comptent toujours. Une énergie ressentie basse (1 ou 2 sur 5) ou « Malade » noté au
 * journal la veille ou le jour même tient lieu de seconde nuit : signes physiques et symptôme ensemble, comme l'alerte à
 * deux niveaux de Mishra et al.
 */
object BodyAlert {
    enum class Level { NONE, WATCH, ALERT }

    enum class Cause(val label: String) {
        HARD_DAY("la grosse journée d'effort d'hier"),
        LATE("l'effort en soirée"),
        RESTLESS("la nuit agitée"),
    }

    /** Écart à la médiane habituelle, dans l'unité de la mesure. */
    data class Sign(val metric: Metric, val delta: Double)

    data class Status(val level: Level, val signs: List<Sign>, val causes: List<Cause>)

    private val WATCHED = listOf(Metric.RESTING_HR, Metric.RESPIRATORY_RATE, Metric.TEMPERATURE, Metric.HRV)

    /** Pouls et VFC : ceux que l'effort, l'alcool et les réveils font aussi bouger. */
    private fun cardiac(m: Metric) = m == Metric.RESTING_HR || m == Metric.HRV

    private fun minSpread(m: Metric, median: Double) = when (m) {
        Metric.RESTING_HR -> 3.0
        Metric.RESPIRATORY_RATE -> 0.8
        Metric.TEMPERATURE -> 0.3
        else -> 0.15 * median
    }

    const val MIN_NIGHTS = 7
    private const val WINDOW = 28

    /** Plage habituelle (bas, médiane, haut) ; null sous [MIN_NIGHTS] nuits. */
    fun range(history: List<Double>, m: Metric): Triple<Double, Double, Double>? {
        if (history.size < MIN_NIGHTS) return null
        val sorted = history.sorted()
        val med = sorted[sorted.size / 2]
        val mad = history.map { abs(it - med) }.sorted().let { it[it.size / 2] } * 1.4826
        val half = maxOf(2 * mad, minSpread(m, med))
        return Triple(med - half, med, med + half)
    }

    /** Mesures hors plage d'une nuit : [values] = valeurs de la nuit, [history] = nuits précédentes. */
    fun signs(values: Map<Metric, Double>, history: Map<Metric, List<Double>>): Pair<List<Sign>, Int> {
        var judged = 0
        val out = WATCHED.mapNotNull { m ->
            val v = values[m] ?: return@mapNotNull null
            val r = range(history[m].orEmpty(), m) ?: return@mapNotNull null
            judged++
            val off = if (m == Metric.HRV) v < r.first else v > r.third
            if (off) Sign(m, v - r.second) else null
        }
        return out to judged
    }

    /** Signes qui comptent : ceux du pouls et de la VFC tombent quand la nuit s'explique par [causes]. */
    fun unexplained(signs: List<Sign>, causes: List<Cause>) = signs.filter { !(cardiac(it.metric) && causes.isNotEmpty()) }

    /**
     * Alerte : deux signes inexpliqués deux nuits de suite, ou une nuit avec une énergie ressentie basse ([feelLow]).
     * À surveiller : deux signes la dernière nuit, expliqués ou non.
     */
    fun level(last: List<Sign>, lastCauses: List<Cause>, previous: List<Sign>, previousCauses: List<Cause>, feelLow: Boolean = false): Level = when {
        unexplained(last, lastCauses).size >= 2 && (unexplained(previous, previousCauses).size >= 2 || feelLow) -> Level.ALERT
        last.size >= 2 -> Level.WATCH
        else -> Level.NONE
    }

    @kotlin.concurrent.Volatile private var cache: Pair<LocalDate, Status?>? = null

    fun invalidate() { cache = null }

    /** État de la nuit qui finit aujourd'hui ; null sans assez d'historique (au moins deux mesures jugeables). */
    suspend fun today(ctx: Context): Status? = withContext(Dispatchers.Default) {
        val today = LocalDate.now()
        cache?.takeIf { it.first == today }?.let { return@withContext it.second }
        val dates = (WINDOW + 1L downTo 0L).map { today.minusDays(it) }
        val values = WATCHED.associateWith { m -> dates.map { runCatching { Bridge.healthValue(ctx, m, it) }.getOrNull() } }
        val phases = Scores.phases(dates)
        fun night(i: Int): Pair<List<Sign>, Int> {
            val phase = phases?.getOrNull(i)
            val from = (i - WINDOW).coerceAtLeast(0)
            val hist = WATCHED.associateWith { m ->
                val idx = (from until i).filter { values[m]!![it] != null }
                val same = if (phase != null) idx.filter { phases[it] == phase } else emptyList()
                (if (same.size >= MIN_NIGHTS) same else idx).map { values[m]!![it]!! }
            }
            return signs(WATCHED.mapNotNull { m -> values[m]!![i]?.let { m to it } }.toMap(), hist)
        }
        val last = night(dates.lastIndex)
        val status = if (last.second < 2) null else {
            val prev = night(dates.lastIndex - 1)
            val lastCauses = causes(ctx, today)
            val prevCauses = causes(ctx, today.minusDays(1))
            val feelLow = (Checkin.today()?.energy ?: 5) <= 2
            Status(level(last.first, lastCauses, prev.first, prevCauses, feelLow), last.first, lastCauses)
        }
        cache = today to status
        status
    }

    /** Ce qui explique un pouls haut ou une VFC basse la nuit qui finit le jour [date]. */
    suspend fun causes(ctx: Context, date: LocalDate): List<Cause> {
        val out = ArrayList<Cause>()
        val eve = date.minusDays(1)
        // Effort de la veille au-dessus du 90e centile des 4 semaines d'avant (et au moins 60 sur 100)
        val effort = runCatching { Scores.effortOf(ctx, eve) }.getOrNull()
        val past = (29L downTo 2L).mapNotNull { runCatching { Scores.effortOf(ctx, date.minusDays(it)) }.getOrNull() }.sorted()
        if (effort != null && past.size >= 14 && effort >= maxOf(60.0, past[(past.size - 1) * 9 / 10])) out += Cause.HARD_DAY
        val night = Bridge.sleepNight(date)
        if (night != null && night.isNotEmpty()) {
            val start = night.minOf { it.start }; val end = night.maxOf { it.end }
            // effort dans les 3 heures avant le coucher : 10 min au-dessus de 60 % de la réserve cardiaque
            val rest = Bridge.healthValue(ctx, Metric.RESTING_HR, date) ?: Bridge.healthValue(ctx, Metric.RESTING_HR, eve)
            if (rest != null) {
                val hr = (Bridge.minuteHeart(eve) + Bridge.minuteHeart(date)).filter { it.first * 1000 in (start - 3 * 3_600_000L) until start }
                val max = Scores.hrMax(age(), hr.map { it.second }, runCatching { app.superhealth.health.data.ActivityUser().gender == app.superhealth.health.data.ActivityUser.GENDER_FEMALE }.getOrDefault(false))
                if (hr.count { it.second >= rest + 0.6 * (max - rest) } >= 10) out += Cause.LATE
            }
            // réveils : au moins 45 min éveillé ou 300 pas dans les heures entières de la nuit
            val awake = night.filter { it.kind == app.superhealth.health.data.ActivityKind.AWAKE_SLEEP }.sumOf { it.end - it.start } / 60000.0
            val steps = (Bridge.hourlySteps(ctx, eve) + Bridge.hourlySteps(ctx, date))
                .filter { it.timestampMs >= start && it.timestampMs + 3_600_000L <= end }.sumOf { it.value }
            if (awake >= 45 || steps >= 300) out += Cause.RESTLESS
        }
        return out
    }

    private fun age(): Int = runCatching { app.superhealth.health.data.ActivityUser().age }.getOrDefault(30).coerceIn(10, 100)
}
