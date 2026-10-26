package app.superhealth.health.data

import app.superhealth.platform.*

import kotlin.math.abs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Signes vitaux de la nuit, à la manière de l'app Signes vitaux de watchOS : pouls au repos, respiration, température au
 * poignet, SpO₂, sommeil et troubles respiratoires de la dernière nuit comparés à votre plage habituelle.
 *
 * Règle SuperHealth (pas celle d'Apple, non publiée) : plage habituelle = médiane des 28 nuits précédentes ± 2 écarts
 * absolus médians (×1,4826, équivalent d'un écart-type robuste), avec un écart minimal propre à chaque mesure ; il faut
 * au moins 7 nuits pour juger.
 */
object NightVitals {
    enum class Status { TYPICAL, HIGH, LOW, UNKNOWN }

    data class Item(val metric: Metric, val value: Double?, val low: Double?, val high: Double?, val status: Status)

    data class Night(val date: LocalDate, val items: List<Item>) {
        val outliers: Int get() = items.count { it.status == Status.HIGH || it.status == Status.LOW }
        val measured: Int get() = items.count { it.value != null }
    }

    val METRICS = listOf(Metric.RESTING_HR, Metric.RESPIRATORY_RATE, Metric.TEMPERATURE, Metric.SPO2, Metric.SLEEP, Metric.BREATHING_DISTURBANCES)

    /** Écart minimal de la plage (sinon une semaine très régulière rendrait la moindre variation « hors norme »). */
    private fun minSpread(m: Metric) = when (m) {
        Metric.RESTING_HR -> 3.0
        Metric.RESPIRATORY_RATE -> 0.8
        Metric.TEMPERATURE -> 0.3
        Metric.SPO2 -> 1.5
        Metric.SLEEP -> 45.0
        Metric.BREATHING_DISTURBANCES -> 2.0
        else -> 0.0
    }

    private const val MIN_NIGHTS = 7

    fun range(history: List<Double>, m: Metric): Pair<Double, Double>? {
        if (history.size < MIN_NIGHTS) return null
        val sorted = history.sorted()
        val med = sorted[sorted.size / 2]
        val mad = history.map { abs(it - med) }.sorted().let { it[it.size / 2] } * 1.4826
        val half = maxOf(2 * mad, minSpread(m))
        return med - half to med + half
    }

    /** Dernière nuit (celle qui finit le jour [date]) et plage habituelle de chaque mesure disponible. */
    suspend fun of(ctx: Context, date: LocalDate = LocalDate.now()): Night = coroutineScope {
        val metrics = METRICS.filter { HealthRepository.isSupported(it) }
        val items = metrics.map { m ->
            async {
                val series = HealthRepository.series(ctx, m, date.minusDays(28), date)
                val value = series.lastOrNull()?.second
                val past = series.dropLast(1).mapNotNull { it.second }
                val r = range(past, m)
                val status = when {
                    value == null || r == null -> Status.UNKNOWN
                    value > r.second -> Status.HIGH
                    value < r.first -> Status.LOW
                    else -> Status.TYPICAL
                }
                Item(m, value, r?.first, r?.second, status)
            }
        }.awaitAll()
        Night(date, items)
    }
}
