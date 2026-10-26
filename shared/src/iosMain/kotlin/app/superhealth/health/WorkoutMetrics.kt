package app.superhealth.health

import app.superhealth.platform.*

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import app.superhealth.ui.formatNumber
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKMetricPrefixCenti
import platform.HealthKit.HKMetricPrefixMilli
import platform.HealthKit.HKObjectQueryNoLimit
import platform.HealthKit.HKQuantitySample
import platform.HealthKit.HKQuantityType
import platform.HealthKit.HKQuantityTypeIdentifierCyclingCadence
import platform.HealthKit.HKQuantityTypeIdentifierCyclingPower
import platform.HealthKit.HKQuantityTypeIdentifierEstimatedWorkoutEffortScore
import platform.HealthKit.HKQuantityTypeIdentifierRunningGroundContactTime
import platform.HealthKit.HKQuantityTypeIdentifierRunningPower
import platform.HealthKit.HKQuantityTypeIdentifierRunningStrideLength
import platform.HealthKit.HKQuantityTypeIdentifierRunningVerticalOscillation
import platform.HealthKit.HKQuantityTypeIdentifierStepCount
import platform.HealthKit.HKQuery
import platform.HealthKit.HKQueryOptionStrictStartDate
import platform.HealthKit.HKQuantityTypeIdentifierWorkoutEffortScore
import platform.HealthKit.HKSampleQuery
import platform.HealthKit.HKStatisticsOptionCumulativeSum
import platform.HealthKit.HKStatisticsOptionDiscreteAverage
import platform.HealthKit.HKStatisticsQuery
import platform.HealthKit.HKUnit
import platform.HealthKit.HKWorkout
import platform.HealthKit.appleEffortScoreUnit
import platform.HealthKit.countUnit
import platform.HealthKit.meterUnit
import platform.HealthKit.meterUnitWithMetricPrefix
import platform.HealthKit.minuteUnit
import platform.HealthKit.predicateForSamplesWithStartDate
import platform.HealthKit.secondUnitWithMetricPrefix
import platform.HealthKit.unitDividedByUnit
import platform.HealthKit.wattUnit
import platform.UIKit.UIDevice

/** Version majeure d'iOS (types HealthKit récents : leurs constantes n'existent pas sur les versions plus anciennes). */
internal fun iosAtLeast(major: Int): Boolean = UIDevice.currentDevice.systemVersion.substringBefore('.').toIntOrNull()?.let { it >= major } ?: false

/**
 * Mesures d'une séance enregistrées par l'Apple Watch : dynamique de course (puissance, foulée, oscillation verticale,
 * temps de contact au sol, cadence), puissance et cadence à vélo, effort sur 10 (watchOS 11).
 */
internal object WorkoutMetrics {
    val readIds: List<String?> get() = listOf(
        HKQuantityTypeIdentifierRunningPower, HKQuantityTypeIdentifierRunningStrideLength, HKQuantityTypeIdentifierRunningVerticalOscillation,
        HKQuantityTypeIdentifierRunningGroundContactTime, HKQuantityTypeIdentifierCyclingPower, HKQuantityTypeIdentifierCyclingCadence,
    ) + (if (iosAtLeast(18)) listOf(HKQuantityTypeIdentifierWorkoutEffortScore, HKQuantityTypeIdentifierEstimatedWorkoutEffortScore) else emptyList())

    private fun date(ms: Long): NSDate = NSDate.dateWithTimeIntervalSince1970(ms / 1000.0)

    private suspend fun stat(store: HKHealthStore, id: String?, w: HKWorkout, unit: HKUnit, sum: Boolean = false): Double? {
        val type = HKQuantityType.quantityTypeForIdentifier(id) ?: return null
        val pred = HKQuery.predicateForSamplesWithStartDate(w.startDate, w.endDate, HKQueryOptionStrictStartDate)
        return suspendCancellableCoroutine { c ->
            val q = HKStatisticsQuery(type, pred, if (sum) HKStatisticsOptionCumulativeSum else HKStatisticsOptionDiscreteAverage) { _, s, _ ->
                c.resume((if (sum) s?.sumQuantity() else s?.averageQuantity())?.doubleValueForUnit(unit))
            }
            store.executeQuery(q)
        }
    }

    private suspend fun lastSample(store: HKHealthStore, id: String?, from: Long, to: Long): HKQuantitySample? {
        val type = HKQuantityType.quantityTypeForIdentifier(id) ?: return null
        return suspendCancellableCoroutine { c ->
            val q = HKSampleQuery(type, HKQuery.predicateForSamplesWithStartDate(date(from), date(to), 0u), HKObjectQueryNoLimit, null) { _, l, _ ->
                c.resume(l.orEmpty().filterIsInstance<HKQuantitySample>().maxByOrNull { it.startDate.timeIntervalSince1970 })
            }
            store.executeQuery(q)
        }
    }

    /** Libellés (traduits à l'affichage) et valeurs ; seules les mesures présentes sont rendues. */
    suspend fun dynamics(store: HKHealthStore, w: HKWorkout, running: Boolean, cycling: Boolean): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val minutes = w.duration / 60
        if (running) {
            stat(store, HKQuantityTypeIdentifierRunningPower, w, HKUnit.wattUnit())?.let { out += "Puissance" to "${it.toInt()} W" }
            stat(store, HKQuantityTypeIdentifierStepCount, w, HKUnit.countUnit(), sum = true)?.takeIf { minutes > 0 }?.let { out += "Cadence" to "${(it / minutes).toInt()} pas/min" }
            stat(store, HKQuantityTypeIdentifierRunningStrideLength, w, HKUnit.meterUnit())?.let { out += "Longueur de foulée" to formatNumber(it, 2) + " m" }
            stat(store, HKQuantityTypeIdentifierRunningVerticalOscillation, w, HKUnit.meterUnitWithMetricPrefix(HKMetricPrefixCenti))?.let { out += "Oscillation verticale" to formatNumber(it, 1) + " cm" }
            stat(store, HKQuantityTypeIdentifierRunningGroundContactTime, w, HKUnit.secondUnitWithMetricPrefix(HKMetricPrefixMilli))?.let { out += "Contact au sol" to "${it.toInt()} ms" }
        }
        if (cycling) {
            stat(store, HKQuantityTypeIdentifierCyclingPower, w, HKUnit.wattUnit())?.let { out += "Puissance" to "${it.toInt()} W" }
            stat(store, HKQuantityTypeIdentifierCyclingCadence, w, HKUnit.countUnit().unitDividedByUnit(HKUnit.minuteUnit()))?.let { out += "Cadence" to "${it.toInt()} tr/min" }
        }
        return out
    }

    /** Effort noté sur la montre après la séance, sinon celui qu'elle estime (iOS 18 et plus). */
    suspend fun effort(store: HKHealthStore, w: HKWorkout, startMs: Long, endMs: Long): Pair<Double, Boolean>? {
        if (!iosAtLeast(18)) return null
        val unit = HKUnit.appleEffortScoreUnit()
        // l'effort est enregistré à la fin de la séance, parfois quelques minutes après
        val to = endMs + 3 * 3_600_000L
        lastSample(store, HKQuantityTypeIdentifierWorkoutEffortScore, startMs, to)?.let { return it.quantity.doubleValueForUnit(unit) to false }
        lastSample(store, HKQuantityTypeIdentifierEstimatedWorkoutEffortScore, startMs, to)?.let { return it.quantity.doubleValueForUnit(unit) to true }
        return null
    }
}
