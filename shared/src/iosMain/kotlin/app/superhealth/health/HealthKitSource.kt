package app.superhealth.health

import app.superhealth.platform.*

import app.superhealth.health.data.ActivityKind
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.HealthSource
import app.superhealth.health.data.Lap
import app.superhealth.health.data.Metric
import app.superhealth.health.data.Point
import app.superhealth.health.data.SourceDevice
import app.superhealth.health.data.TrackPoint
import app.superhealth.health.data.Workout
import app.superhealth.health.data.WorkoutDetail
import app.superhealth.health.data.WorkoutDetails
import app.superhealth.health.data.ActivitySummary
import app.superhealth.health.data.HeartEvent
import app.superhealth.health.data.HeartEventKind
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSCalendar
import platform.Foundation.NSDateComponents
import platform.HealthKit.HKActivitySummary
import platform.HealthKit.HKActivitySummaryQuery
import platform.HealthKit.HKActivitySummaryType
import platform.HealthKit.HKAnchoredObjectQuery
import platform.HealthKit.HKCategoryTypeIdentifierAppleStandHour
import platform.HealthKit.HKCategoryTypeIdentifierHighHeartRateEvent
import platform.HealthKit.HKCategoryTypeIdentifierIrregularHeartRhythmEvent
import platform.HealthKit.HKCategoryTypeIdentifierLowHeartRateEvent
import platform.HealthKit.HKCategoryTypeIdentifierMindfulSession
import platform.HealthKit.HKCategoryValueAppleStandHourStood
import platform.HealthKit.HKElectrocardiogram
import platform.HealthKit.HKElectrocardiogramClassificationAtrialFibrillation
import platform.HealthKit.HKElectrocardiogramClassificationInconclusiveHighHeartRate
import platform.HealthKit.HKElectrocardiogramClassificationInconclusiveLowHeartRate
import platform.HealthKit.HKElectrocardiogramClassificationInconclusiveOther
import platform.HealthKit.HKElectrocardiogramClassificationInconclusivePoorReading
import platform.HealthKit.HKElectrocardiogramClassificationSinusRhythm
import platform.HealthKit.HKMetadataKeyHeartRateEventThreshold
import platform.HealthKit.HKQuantityTypeIdentifierAppleSleepingBreathingDisturbances
import platform.HealthKit.HKQuantityTypeIdentifierAppleWalkingSteadiness
import platform.HealthKit.HKQuantityTypeIdentifierAtrialFibrillationBurden
import platform.HealthKit.HKQuantityTypeIdentifierEnvironmentalAudioExposure
import platform.HealthKit.HKQuantityTypeIdentifierFlightsClimbed
import platform.HealthKit.HKQuantityTypeIdentifierHeartRateRecoveryOneMinute
import platform.HealthKit.HKQuantityTypeIdentifierTimeInDaylight
import platform.HealthKit.HKQuantityTypeIdentifierWalkingHeartRateAverage
import platform.HealthKit.HKQuantityTypeIdentifierWalkingSpeed
import platform.HealthKit.HKQueryAnchor
import platform.HealthKit.decibelAWeightedSoundPressureLevelUnit
import platform.HealthKit.predicateForActivitySummaryWithDateComponents
import platform.HealthKit.secondUnit
import kotlin.coroutines.resume
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocation
import platform.Foundation.NSDate
import platform.Foundation.NSNumber
import platform.Foundation.NSSortDescriptor
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.HealthKit.HKCategorySample
import platform.HealthKit.HKCategoryType
import platform.HealthKit.HKCategoryTypeIdentifierSleepAnalysis
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepCore
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepDeep
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepREM
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepUnspecified
import platform.HealthKit.HKCategoryValueSleepAnalysisAwake
import platform.HealthKit.HKCategoryValueSleepAnalysisInBed
import platform.HealthKit.HKCorrelation
import platform.HealthKit.HKCorrelationType
import platform.HealthKit.HKCorrelationTypeIdentifierFood
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKMetadataKeySyncIdentifier
import platform.HealthKit.HKMetadataKeySyncVersion
import platform.HealthKit.HKMetricPrefixKilo
import platform.HealthKit.HKMetricPrefixMilli
import platform.HealthKit.HKObjectQueryNoLimit
import platform.HealthKit.HKObjectType
import platform.HealthKit.HKQuantity
import platform.HealthKit.HKQuantitySample
import platform.HealthKit.HKQuantityType
import platform.HealthKit.HKQuantityTypeIdentifierActiveEnergyBurned
import platform.HealthKit.HKQuantityTypeIdentifierAppleExerciseTime
import platform.HealthKit.HKQuantityTypeIdentifierAppleSleepingWristTemperature
import platform.HealthKit.HKQuantityTypeIdentifierBodyFatPercentage
import platform.HealthKit.HKQuantityTypeIdentifierBodyMass
import platform.HealthKit.HKQuantityTypeIdentifierDietaryCarbohydrates
import platform.HealthKit.HKQuantityTypeIdentifierDietaryEnergyConsumed
import platform.HealthKit.HKQuantityTypeIdentifierDietaryFatTotal
import platform.HealthKit.HKQuantityTypeIdentifierDietaryFiber
import platform.HealthKit.HKQuantityTypeIdentifierDietaryProtein
import platform.HealthKit.HKQuantityTypeIdentifierDietaryWater
import platform.HealthKit.HKQuantityTypeIdentifierDistanceWalkingRunning
import platform.HealthKit.HKQuantityTypeIdentifierHeartRate
import platform.HealthKit.HKQuantityTypeIdentifierHeartRateVariabilitySDNN
import platform.HealthKit.HKQuantityTypeIdentifierOxygenSaturation
import platform.HealthKit.HKQuantityTypeIdentifierRespiratoryRate
import platform.HealthKit.HKQuantityTypeIdentifierRestingHeartRate
import platform.HealthKit.HKQuantityTypeIdentifierStepCount
import platform.HealthKit.HKQuantityTypeIdentifierVO2Max
import platform.HealthKit.HKQuery
import platform.HealthKit.HKQueryOptionStrictStartDate
import platform.HealthKit.HKSample
import platform.HealthKit.HKSampleQuery
import platform.HealthKit.HKSampleSortIdentifierStartDate
import platform.HealthKit.HKSeriesType
import platform.HealthKit.HKStatistics
import platform.HealthKit.HKStatisticsOptionCumulativeSum
import platform.HealthKit.HKStatisticsOptionDiscreteAverage
import platform.HealthKit.HKStatisticsQuery
import platform.HealthKit.HKUnit
import platform.HealthKit.HKWorkout
import platform.HealthKit.HKWorkoutActivityTypeCycling
import platform.HealthKit.HKWorkoutActivityTypeElliptical
import platform.HealthKit.HKWorkoutActivityTypeFunctionalStrengthTraining
import platform.HealthKit.HKWorkoutActivityTypeHighIntensityIntervalTraining
import platform.HealthKit.HKWorkoutActivityTypeHiking
import platform.HealthKit.HKWorkoutActivityTypePilates
import platform.HealthKit.HKWorkoutActivityTypeRowing
import platform.HealthKit.HKWorkoutActivityTypeRunning
import platform.HealthKit.HKWorkoutActivityTypeSwimming
import platform.HealthKit.HKWorkoutActivityTypeTraditionalStrengthTraining
import platform.HealthKit.HKWorkoutActivityTypeWalking
import platform.HealthKit.HKWorkoutActivityTypeYoga
import platform.HealthKit.HKWorkoutRoute
import platform.HealthKit.HKWorkoutRouteQuery
import platform.HealthKit.countUnit
import platform.HealthKit.degreeCelsiusUnit
import platform.HealthKit.gramUnit
import platform.HealthKit.gramUnitWithMetricPrefix
import platform.HealthKit.kilocalorieUnit
import platform.HealthKit.literUnitWithMetricPrefix
import platform.HealthKit.meterUnit
import platform.HealthKit.minuteUnit
import platform.HealthKit.percentUnit
import platform.HealthKit.predicateForObjectsFromWorkout
import platform.HealthKit.predicateForObjectsWithMetadataKey
import platform.HealthKit.predicateForSamplesWithStartDate
import platform.HealthKit.secondUnitWithMetricPrefix
import platform.HealthKit.unitDividedByUnit

/**
 * Apple Santé : l'Apple Watch (et toute app qui écrit dans Santé : Garmin Connect, Polar Flow, Withings…) fournit pas,
 * pouls, VFC, sommeil par phases, SpO2, respiration, température au poignet, VO2 max et séances avec tracé GPS.
 * Remplace le moteur Gadgetbridge et Health Connect de la version Android.
 */
object HealthKitSource : HealthSource {
    private val store by lazy { HKHealthStore() }
    private val workoutIds = SyncMap<Long, HKWorkout>()

    override val name = "Apple Santé"
    override val available: Boolean get() = HKHealthStore.isHealthDataAvailable()

    private fun q(id: String?): HKQuantityType = HKQuantityType.quantityTypeForIdentifier(id)!!
    private val sleepType: HKCategoryType get() = HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierSleepAnalysis)!!

    private val bpm: HKUnit get() = HKUnit.countUnit().unitDividedByUnit(HKUnit.minuteUnit())
    private val kg: HKUnit get() = HKUnit.gramUnitWithMetricPrefix(HKMetricPrefixKilo)
    private val ms: HKUnit get() = HKUnit.secondUnitWithMetricPrefix(HKMetricPrefixMilli)
    private val ml: HKUnit get() = HKUnit.literUnitWithMetricPrefix(HKMetricPrefixMilli)
    private val vo2: HKUnit get() = HKUnit.unitFromString("ml/kg*min")

    private val readIds get() = listOf(
        HKQuantityTypeIdentifierStepCount, HKQuantityTypeIdentifierDistanceWalkingRunning, HKQuantityTypeIdentifierActiveEnergyBurned,
        HKQuantityTypeIdentifierAppleExerciseTime, HKQuantityTypeIdentifierHeartRate, HKQuantityTypeIdentifierRestingHeartRate,
        HKQuantityTypeIdentifierHeartRateVariabilitySDNN, HKQuantityTypeIdentifierOxygenSaturation, HKQuantityTypeIdentifierRespiratoryRate,
        HKQuantityTypeIdentifierVO2Max, HKQuantityTypeIdentifierAppleSleepingWristTemperature, HKQuantityTypeIdentifierBodyMass,
        HKQuantityTypeIdentifierBodyFatPercentage, HKQuantityTypeIdentifierDietaryWater,
        // Apple Watch : cœur, mobilité, environnement
        HKQuantityTypeIdentifierWalkingHeartRateAverage, HKQuantityTypeIdentifierHeartRateRecoveryOneMinute,
        HKQuantityTypeIdentifierAtrialFibrillationBurden, HKQuantityTypeIdentifierAppleWalkingSteadiness, HKQuantityTypeIdentifierWalkingSpeed,
        HKQuantityTypeIdentifierFlightsClimbed, HKQuantityTypeIdentifierEnvironmentalAudioExposure,
    ) + (if (iosAtLeast(17)) listOf(HKQuantityTypeIdentifierTimeInDaylight) else emptyList()) +
        (if (iosAtLeast(18)) listOf(HKQuantityTypeIdentifierAppleSleepingBreathingDisturbances) else emptyList()) + WorkoutMetrics.readIds
    private val categoryIds = listOf(HKCategoryTypeIdentifierAppleStandHour, HKCategoryTypeIdentifierMindfulSession,
        HKCategoryTypeIdentifierHighHeartRateEvent, HKCategoryTypeIdentifierLowHeartRateEvent, HKCategoryTypeIdentifierIrregularHeartRhythmEvent)
    private val writeIds = listOf(
        HKQuantityTypeIdentifierBodyMass, HKQuantityTypeIdentifierBodyFatPercentage, HKQuantityTypeIdentifierDietaryEnergyConsumed,
        HKQuantityTypeIdentifierDietaryProtein, HKQuantityTypeIdentifierDietaryFatTotal, HKQuantityTypeIdentifierDietaryCarbohydrates,
        HKQuantityTypeIdentifierDietaryFiber, HKQuantityTypeIdentifierDietaryWater,
    )

    override suspend fun requestAuthorization(): Boolean = suspendCancellableCoroutine { c ->
        if (!available) { c.resume(false); return@suspendCancellableCoroutine }
        val read = readIds.mapNotNull { HKQuantityType.quantityTypeForIdentifier(it) }.toSet<Any>() +
            setOf(sleepType, HKObjectType.workoutType(), HKSeriesType.workoutRouteType(), HKObjectType.activitySummaryType(),
                HKObjectType.electrocardiogramType()) + categoryIds.mapNotNull { HKCategoryType.categoryTypeForIdentifier(it) }
        val write = writeIds.mapNotNull { HKQuantityType.quantityTypeForIdentifier(it) }.toSet<Any>() + setOf(HKObjectType.workoutType())
        store.requestAuthorizationToShareTypes(write, read) { ok, _ -> c.resume(ok) }
    }

    override fun supports(metric: Metric): Boolean = available && when (metric) {
        Metric.STEPS, Metric.DISTANCE, Metric.CALORIES, Metric.ACTIVE_MINUTES, Metric.HEART_RATE, Metric.RESTING_HR, Metric.HRV,
        Metric.SLEEP, Metric.SPO2, Metric.RESPIRATORY_RATE, Metric.VO2MAX, Metric.TEMPERATURE, Metric.WEIGHT,
        Metric.STAND_HOURS, Metric.FLIGHTS, Metric.WALKING_HR, Metric.HR_RECOVERY, Metric.AFIB_BURDEN, Metric.WALKING_STEADINESS,
        Metric.WALKING_SPEED, Metric.MINDFUL, Metric.NOISE -> true
        Metric.DAYLIGHT -> iosAtLeast(17)
        Metric.BREATHING_DISTURBANCES -> iosAtLeast(18)
        // propres à Garmin (Body Battery, stress, disposition, score de sommeil) : absents d'Apple Santé
        else -> false
    }

    // ---- Requêtes --------------------------------------------------------------------------------------------

    private fun date(ms: Long): NSDate = NSDate.dateWithTimeIntervalSince1970(ms / 1000.0)
    private fun NSDate.ms(): Long = (timeIntervalSince1970 * 1000).toLong()
    private fun range(from: Long, to: Long) = HKQuery.predicateForSamplesWithStartDate(date(from), date(to), HKQueryOptionStrictStartDate)

    private suspend fun statistics(type: HKQuantityType, from: Long, to: Long, sum: Boolean): HKStatistics? = suspendCancellableCoroutine { c ->
        val query = HKStatisticsQuery(type, range(from, to), if (sum) HKStatisticsOptionCumulativeSum else HKStatisticsOptionDiscreteAverage) { _, s, _ -> c.resume(s) }
        store.executeQuery(query)
        c.invokeOnCancellation { store.stopQuery(query) }
    }

    private suspend fun samples(type: platform.HealthKit.HKSampleType, predicate: platform.Foundation.NSPredicate?, limit: ULong = HKObjectQueryNoLimit,
                                newestFirst: Boolean = false): List<HKSample> = suspendCancellableCoroutine { c ->
        val sort = NSSortDescriptor.sortDescriptorWithKey(HKSampleSortIdentifierStartDate, ascending = !newestFirst)
        val query = HKSampleQuery(type, predicate, limit, listOf(sort)) { _, results, _ -> c.resume(results.orEmpty().filterIsInstance<HKSample>()) }
        store.executeQuery(query)
        c.invokeOnCancellation { store.stopQuery(query) }
    }

    private suspend fun sum(id: String?, from: Long, to: Long, unit: HKUnit): Double? =
        statistics(q(id), from, to, sum = true)?.sumQuantity()?.doubleValueForUnit(unit)

    private suspend fun avg(id: String?, from: Long, to: Long, unit: HKUnit): Double? =
        statistics(q(id), from, to, sum = false)?.averageQuantity()?.doubleValueForUnit(unit)

    private suspend fun latest(id: String?, from: Long, to: Long, unit: HKUnit): Double? =
        (samples(q(id), range(from, to), 1u, newestFirst = true).firstOrNull() as? HKQuantitySample)?.quantity?.doubleValueForUnit(unit)

    override suspend fun daily(metric: Metric, date: LocalDate): Double? {
        val from = HealthRepository.startMs(date)
        val to = HealthRepository.endMs(date)
        return when (metric) {
            Metric.STEPS -> sum(HKQuantityTypeIdentifierStepCount, from, to, HKUnit.countUnit())
            Metric.DISTANCE -> sum(HKQuantityTypeIdentifierDistanceWalkingRunning, from, to, HKUnit.meterUnit())?.div(1000.0)
            Metric.CALORIES -> sum(HKQuantityTypeIdentifierActiveEnergyBurned, from, to, HKUnit.kilocalorieUnit())
            Metric.ACTIVE_MINUTES -> sum(HKQuantityTypeIdentifierAppleExerciseTime, from, to, HKUnit.minuteUnit())
            Metric.RESTING_HR -> avg(HKQuantityTypeIdentifierRestingHeartRate, from, to, bpm)
            Metric.HRV -> avg(HKQuantityTypeIdentifierHeartRateVariabilitySDNN, from, to, ms)
            Metric.RESPIRATORY_RATE -> avg(HKQuantityTypeIdentifierRespiratoryRate, from, to, bpm)
            Metric.SPO2 -> avg(HKQuantityTypeIdentifierOxygenSaturation, from, to, HKUnit.percentUnit())?.times(100)
            Metric.TEMPERATURE -> avg(HKQuantityTypeIdentifierAppleSleepingWristTemperature, from, to, HKUnit.degreeCelsiusUnit())
            Metric.VO2MAX -> latest(HKQuantityTypeIdentifierVO2Max, from, to, vo2)
            Metric.WEIGHT -> latest(HKQuantityTypeIdentifierBodyMass, from, to, kg)
            Metric.SLEEP -> HealthRepository.sleepNight(date)?.filter { it.kind != ActivityKind.AWAKE_SLEEP }?.sumOf { it.end - it.start }?.div(60_000.0)
            Metric.STAND_HOURS -> category(HKCategoryTypeIdentifierAppleStandHour, from, to).count { it.value == HKCategoryValueAppleStandHourStood }.toDouble()
            Metric.FLIGHTS -> sum(HKQuantityTypeIdentifierFlightsClimbed, from, to, HKUnit.countUnit())
            Metric.WALKING_HR -> avg(HKQuantityTypeIdentifierWalkingHeartRateAverage, from, to, bpm)
            Metric.HR_RECOVERY -> latest(HKQuantityTypeIdentifierHeartRateRecoveryOneMinute, from, to, bpm)
            Metric.AFIB_BURDEN -> latest(HKQuantityTypeIdentifierAtrialFibrillationBurden, from, to, HKUnit.percentUnit())?.times(100)
            Metric.WALKING_STEADINESS -> latest(HKQuantityTypeIdentifierAppleWalkingSteadiness, from, to, HKUnit.percentUnit())?.times(100)
            Metric.WALKING_SPEED -> avg(HKQuantityTypeIdentifierWalkingSpeed, from, to, HKUnit.meterUnit().unitDividedByUnit(HKUnit.secondUnit()))?.times(3.6)
            Metric.DAYLIGHT -> if (iosAtLeast(17)) sum(HKQuantityTypeIdentifierTimeInDaylight, from, to, HKUnit.minuteUnit()) else null
            Metric.MINDFUL -> category(HKCategoryTypeIdentifierMindfulSession, from, to).sumOf { it.endDate.ms() - it.startDate.ms() } / 60_000.0
            Metric.NOISE -> avg(HKQuantityTypeIdentifierEnvironmentalAudioExposure, from, to, HKUnit.decibelAWeightedSoundPressureLevelUnit())
            Metric.BREATHING_DISTURBANCES -> if (iosAtLeast(18)) latest(HKQuantityTypeIdentifierAppleSleepingBreathingDisturbances, from, to, HKUnit.countUnit()) else null
            else -> null
        }
    }

    private suspend fun category(id: String?, from: Long, to: Long): List<HKCategorySample> =
        HKCategoryType.categoryTypeForIdentifier(id)?.let { t -> samples(t, range(from, to)).filterIsInstance<HKCategorySample>() }.orEmpty()

    // ---- Anneaux, alertes cardiaques, ECG, pouls en direct --------------------------------------------------------

    override suspend fun activitySummary(date: LocalDate): ActivitySummary? = suspendCancellableCoroutine { c ->
        val comps = NSDateComponents().apply {
            year = date.year.toLong(); month = date.monthValue.toLong(); day = date.dayOfMonth.toLong()
            calendar = NSCalendar.currentCalendar
        }
        val query = HKActivitySummaryQuery(HKQuery.predicateForActivitySummaryWithDateComponents(comps)) { _, list, _ ->
            val a = list?.firstOrNull() as? HKActivitySummary
            c.resume(a?.let {
                ActivitySummary(
                    move = it.activeEnergyBurned.doubleValueForUnit(HKUnit.kilocalorieUnit()),
                    moveGoal = it.activeEnergyBurnedGoal.doubleValueForUnit(HKUnit.kilocalorieUnit()),
                    exercise = it.appleExerciseTime.doubleValueForUnit(HKUnit.minuteUnit()),
                    exerciseGoal = it.appleExerciseTimeGoal.doubleValueForUnit(HKUnit.minuteUnit()),
                    stand = it.appleStandHours.doubleValueForUnit(HKUnit.countUnit()),
                    standGoal = it.appleStandHoursGoal.doubleValueForUnit(HKUnit.countUnit()),
                )
            })
        }
        store.executeQuery(query)
        c.invokeOnCancellation { store.stopQuery(query) }
    }

    override suspend fun heartEvents(fromMs: Long, toMs: Long): List<HeartEvent> {
        val alerts = listOf(HKCategoryTypeIdentifierHighHeartRateEvent to HeartEventKind.HIGH, HKCategoryTypeIdentifierLowHeartRateEvent to HeartEventKind.LOW,
            HKCategoryTypeIdentifierIrregularHeartRhythmEvent to HeartEventKind.IRREGULAR).flatMap { (id, kind) ->
            category(id, fromMs, toMs).map { e ->
                val threshold = (e.metadata?.get(HKMetadataKeyHeartRateEventThreshold) as? HKQuantity)?.doubleValueForUnit(bpm)?.toInt()
                HeartEvent(e.startDate.ms(), kind, threshold, alert = true)
            }
        }
        val ecgs = samples(HKObjectType.electrocardiogramType(), range(fromMs, toMs)).filterIsInstance<HKElectrocardiogram>().map { e ->
            val label = when (e.classification) {
                HKElectrocardiogramClassificationSinusRhythm -> "Rythme sinusal"
                HKElectrocardiogramClassificationAtrialFibrillation -> "Fibrillation auriculaire"
                HKElectrocardiogramClassificationInconclusiveLowHeartRate -> "Non concluant (pouls bas)"
                HKElectrocardiogramClassificationInconclusiveHighHeartRate -> "Non concluant (pouls élevé)"
                HKElectrocardiogramClassificationInconclusivePoorReading -> "Non concluant (mauvais enregistrement)"
                HKElectrocardiogramClassificationInconclusiveOther -> "Non concluant"
                else -> "Non classé"
            }
            HeartEvent(e.startDate.ms(), HeartEventKind.ECG, e.averageHeartRate?.doubleValueForUnit(bpm)?.toInt(), label,
                alert = e.classification == HKElectrocardiogramClassificationAtrialFibrillation)
        }
        return (alerts + ecgs).sortedByDescending { it.timeMs }
    }

    /** Pouls des 10 dernières minutes puis chaque nouvelle mesure (pendant une séance, la montre en envoie toutes les secondes). */
    override fun liveHeart(): Flow<Point> = callbackFlow {
        val type = q(HKQuantityTypeIdentifierHeartRate)
        val predicate = HKQuery.predicateForSamplesWithStartDate(date(nowMs() - 600_000L), null, 0u)
        fun emit(list: List<*>?) = list.orEmpty().filterIsInstance<HKQuantitySample>().sortedBy { it.startDate.ms() }
            .forEach { trySend(Point(it.startDate.ms(), it.quantity.doubleValueForUnit(bpm))) }
        val query = HKAnchoredObjectQuery(type, predicate, null as HKQueryAnchor?, HKObjectQueryNoLimit) { _, added, _, _, _ -> emit(added) }
        query.setUpdateHandler { _, added, _, _, _ -> emit(added) }
        store.executeQuery(query)
        awaitClose { store.stopQuery(query) }
    }

    override suspend fun samples(metric: Metric, fromMs: Long, toMs: Long): List<Point> {
        val (id, unit, scale) = when (metric) {
            Metric.HEART_RATE -> Triple(HKQuantityTypeIdentifierHeartRate, bpm, 1.0)
            Metric.STEPS -> Triple(HKQuantityTypeIdentifierStepCount, HKUnit.countUnit(), 1.0)
            Metric.SPO2 -> Triple(HKQuantityTypeIdentifierOxygenSaturation, HKUnit.percentUnit(), 100.0)
            else -> return emptyList()
        }
        return samples(q(id), range(fromMs, toMs)).filterIsInstance<HKQuantitySample>().map {
            Point(it.startDate.ms(), it.quantity.doubleValueForUnit(unit) * scale)
        }
    }

    override suspend fun sleep(fromMs: Long, toMs: Long): List<HealthRepository.SleepPhase> {
        val all = samples(sleepType, HKQuery.predicateForSamplesWithStartDate(date(fromMs), date(toMs), 0u)).filterIsInstance<HKCategorySample>()
        // phases détaillées (Apple Watch) si elles existent ; sinon « endormi » / « au lit » (autres apps, iPhone seul)
        val staged = all.filter { it.value in setOf(HKCategoryValueSleepAnalysisAsleepCore, HKCategoryValueSleepAnalysisAsleepDeep, HKCategoryValueSleepAnalysisAsleepREM) }
        val use = if (staged.isNotEmpty()) all.filter { it.value != HKCategoryValueSleepAnalysisInBed && it.value != HKCategoryValueSleepAnalysisAsleepUnspecified }
            else all.filter { it.value != HKCategoryValueSleepAnalysisInBed }.ifEmpty { all }
        return use.map { s ->
            val kind = when (s.value) {
                HKCategoryValueSleepAnalysisAsleepDeep -> ActivityKind.DEEP_SLEEP
                HKCategoryValueSleepAnalysisAsleepREM -> ActivityKind.REM_SLEEP
                HKCategoryValueSleepAnalysisAwake -> ActivityKind.AWAKE_SLEEP
                HKCategoryValueSleepAnalysisAsleepCore -> ActivityKind.LIGHT_SLEEP
                else -> ActivityKind.SLEEP_ANY
            }
            HealthRepository.SleepPhase(kind, s.startDate.ms(), s.endDate.ms())
        }.distinctBy { Triple(it.kind, it.start, it.end) }
    }

    // ---- Séances -------------------------------------------------------------------------------------------------

    private fun kindOf(type: ULong): ActivityKind = when (type) {
        HKWorkoutActivityTypeRunning -> ActivityKind.RUNNING
        HKWorkoutActivityTypeWalking -> ActivityKind.WALKING
        HKWorkoutActivityTypeHiking -> ActivityKind.HIKING
        HKWorkoutActivityTypeCycling -> ActivityKind.CYCLING
        HKWorkoutActivityTypeSwimming -> ActivityKind.SWIMMING
        HKWorkoutActivityTypeYoga -> ActivityKind.YOGA
        HKWorkoutActivityTypePilates -> ActivityKind.PILATES
        HKWorkoutActivityTypeTraditionalStrengthTraining, HKWorkoutActivityTypeFunctionalStrengthTraining -> ActivityKind.STRENGTH_TRAINING
        HKWorkoutActivityTypeHighIntensityIntervalTraining -> ActivityKind.HIIT
        HKWorkoutActivityTypeElliptical -> ActivityKind.ELLIPTICAL_TRAINER
        HKWorkoutActivityTypeRowing -> ActivityKind.ROWING_MACHINE
        else -> ActivityKind.EXERCISE
    }

    /** Identifiant positif et stable tiré de l'UUID de la séance. */
    private fun idOf(w: HKWorkout): Long = (w.UUID.UUIDString.hashCode().toLong() and 0x7fffffffL) + 1

    private fun device(w: HKWorkout) = SourceDevice("healthkit", w.sourceRevision.source.name, w.sourceRevision.productType)

    override suspend fun workouts(fromMs: Long, toMs: Long): List<Workout> {
        val list = samples(HKObjectType.workoutType(), range(fromMs.coerceAtLeast(0), toMs.coerceAtMost(nowMs() + 86_400_000L)), newestFirst = true)
            .filterIsInstance<HKWorkout>()
            // séances écrites par SuperHealth : déjà listées comme séances de musculation
            .filter { (it.metadata?.get(HKMetadataKeySyncIdentifier) as? String)?.startsWith("wo-") != true }
        return list.map { w ->
            val id = idOf(w).also { workoutIds[it] = w }
            @Suppress("DEPRECATION")
            Workout(id, null, kindOf(w.workoutActivityType), Date(w.startDate.ms()), Date(w.endDate.ms()), device(w),
                w.totalDistance?.doubleValueForUnit(HKUnit.meterUnit())?.takeIf { it > 0 },
                w.totalEnergyBurned?.doubleValueForUnit(HKUnit.kilocalorieUnit())?.takeIf { it > 0 })
        }
    }

    private suspend fun route(w: HKWorkout): List<CLLocation> {
        val routes = samples(HKSeriesType.workoutRouteType(), HKQuery.predicateForObjectsFromWorkout(w)).filterIsInstance<HKWorkoutRoute>()
        val out = ArrayList<CLLocation>()
        routes.forEach { r ->
            suspendCancellableCoroutine { c ->
                val query = HKWorkoutRouteQuery(r) { _, locations, done, _ ->
                    locations.orEmpty().filterIsInstance<CLLocation>().let { synchronized(out) { out += it } }
                    if (done && c.isActive) c.resume(Unit)
                }
                store.executeQuery(query)
                c.invokeOnCancellation { store.stopQuery(query) }
            }
        }
        return out.sortedBy { it.timestamp.timeIntervalSince1970 }
    }

    override suspend fun workoutDetail(id: Long): WorkoutDetail? {
        val w = workoutIds[id] ?: run { workouts(0, nowMs()); workoutIds[id] } ?: return null
        val t0 = w.startDate.ms()
        val t1 = w.endDate.ms()
        val hr = samples(q(HKQuantityTypeIdentifierHeartRate), range(t0, t1)).filterIsInstance<HKQuantitySample>()
            .map { it.startDate.ms() to it.quantity.doubleValueForUnit(bpm).toInt() }
        fun hrAt(t: Long) = hr.minByOrNull { kotlin.math.abs(it.first - t) }?.takeIf { kotlin.math.abs(it.first - t) < 30_000 }?.second
        val loc = runCatching { route(w) }.getOrDefault(emptyList())
        val pts = if (loc.isNotEmpty()) loc.map { l ->
            val t = (l.timestamp.timeIntervalSince1970 * 1000).toLong()
            val (lat, lon) = l.coordinate.useContents { latitude to longitude }
            TrackPoint((t - t0) / 1000.0, 0.0, lat, lon, l.altitude.takeIf { l.verticalAccuracy >= 0 }, hrAt(t), l.speed.takeIf { it > 0 }, null)
        } else hr.map { (t, v) -> TrackPoint((t - t0) / 1000.0, 0.0, null, null, null, v, null, null) }
        val points = WorkoutDetails.withDistances(pts)
        @Suppress("DEPRECATION") val dist = w.totalDistance?.doubleValueForUnit(HKUnit.meterUnit())?.takeIf { it > 0 } ?: points.lastOrNull()?.d?.takeIf { it > 0 }
        @Suppress("DEPRECATION") val kcal = w.totalEnergyBurned?.doubleValueForUnit(HKUnit.kilocalorieUnit())?.takeIf { it > 0 }
        // dénivelé positif et négatif (hausses de plus de 2 m, comme les imports)
        var up = 0.0; var down = 0.0; var ref: Double? = null
        points.forEach { p -> p.alt?.let { a -> val r = ref; if (r == null) ref = a else if (a - r > 2) { up += a - r; ref = a } else if (r - a > 2) { down += r - a; ref = a } } }
        val kind = kindOf(w.workoutActivityType)
        val hrs = hr.map { it.second }
        val effort = runCatching { WorkoutMetrics.effort(store, w, t0, t1) }.getOrNull()
        return WorkoutDetail(
            id = id, name = app.superhealth.bridge.Bridge.kindLabel(app.superhealth.bridge.Bridge.kindOf(kind.name).name), kind = kind,
            start = t0, end = t1, deviceName = w.sourceRevision.source.name, activeSec = w.duration,
            distanceM = dist, kcal = kcal, avgHr = hrs.takeIf { it.isNotEmpty() }?.average()?.toInt(), maxHr = hrs.maxOrNull(),
            ascentM = up.takeIf { it > 0 }, descentM = down.takeIf { it > 0 }, avgCadence = null,
            maxSpeedMps = points.mapNotNull { it.speed }.maxOrNull(), steps = null, aerobicTe = null, anaerobicTe = null, recoverySec = null,
            poolLengthM = null, swolf = null, strokes = null, predictions = emptyList(), zoneBounds = null, points = points, laps = kmLaps(points),
            dynamics = runCatching { WorkoutMetrics.dynamics(store, w, kind.name.let { "RUN" in it || it == "TREADMILL" }, "CYCL" in kind.name) }.getOrDefault(emptyList()),
            effort = effort?.first, effortEstimated = effort?.second ?: false,
        )
    }

    /** Tours au kilomètre, comme l'auto-lap des montres. */
    private fun kmLaps(pts: List<TrackPoint>): List<Lap> {
        val laps = ArrayList<Lap>()
        var from = pts.firstOrNull() ?: return laps
        var k = 1
        pts.forEach { p ->
            if (p.d - from.d >= 1000) {
                val seg = pts.filter { it.t in from.t..p.t }
                laps += Lap(k++, p.d - from.d, p.t - from.t, seg.mapNotNull { it.hr }.takeIf { it.isNotEmpty() }?.average()?.toInt(), null)
                from = p
            }
        }
        return laps
    }

    override suspend fun weights(fromMs: Long, toMs: Long): List<Triple<Long, Double, Double?>> {
        val mine = "app.superhealth"
        val zone = ZoneId.systemDefault()
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()
        val fat = samples(q(HKQuantityTypeIdentifierBodyFatPercentage), range(fromMs, toMs)).filterIsInstance<HKQuantitySample>()
            .filter { it.sourceRevision.source.bundleIdentifier != mine }
            .associate { day(it.startDate.ms()) to it.quantity.doubleValueForUnit(HKUnit.percentUnit()) * 100 }
        return samples(q(HKQuantityTypeIdentifierBodyMass), range(fromMs, toMs)).filterIsInstance<HKQuantitySample>()
            .filter { it.sourceRevision.source.bundleIdentifier != mine }
            .map { s -> Triple(s.startDate.ms(), s.quantity.doubleValueForUnit(kg), fat[day(s.startDate.ms())]) }
    }

    // ---- Écriture (identifiant de synchro : une nouvelle version remplace l'ancienne) -----------------------------

    private fun meta(id: String): Map<Any?, Any> = mapOf(HKMetadataKeySyncIdentifier to id, HKMetadataKeySyncVersion to NSNumber(longLong = nowMs()))

    private suspend fun save(objects: List<Any>) = suspendCancellableCoroutine { c ->
        if (objects.isEmpty()) { c.resume(Unit); return@suspendCancellableCoroutine }
        store.saveObjects(objects) { _, _ -> c.resume(Unit) }
    }

    private fun sample(id: String?, unit: HKUnit, value: Double, start: Long, end: Long, sync: String) =
        HKQuantitySample.quantitySampleWithType(q(id), HKQuantity.quantityWithUnit(unit, value), date(start), date(end), meta(sync))

    private fun noon(epochDay: Long): Long = LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 8 * 3_600_000L

    override suspend fun writeWeight(epochDay: Long, kg: Double, fatPct: Double?) {
        val t = noon(epochDay)
        save(listOfNotNull(sample(HKQuantityTypeIdentifierBodyMass, this.kg, kg, t, t, "w-$epochDay"),
            fatPct?.let { sample(HKQuantityTypeIdentifierBodyFatPercentage, HKUnit.percentUnit(), it / 100, t, t, "bf-$epochDay") }))
    }

    override suspend fun writeNutrition(epochDay: Long, kcal: Double, protein: Double, fat: Double, carbs: Double, fiber: Double) {
        val start = LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = start + 86_399_000L
        val parts = setOf(
            sample(HKQuantityTypeIdentifierDietaryEnergyConsumed, HKUnit.kilocalorieUnit(), kcal, start, end, "n-$epochDay-kcal"),
            sample(HKQuantityTypeIdentifierDietaryProtein, HKUnit.gramUnit(), protein, start, end, "n-$epochDay-p"),
            sample(HKQuantityTypeIdentifierDietaryFatTotal, HKUnit.gramUnit(), fat, start, end, "n-$epochDay-f"),
            sample(HKQuantityTypeIdentifierDietaryCarbohydrates, HKUnit.gramUnit(), carbs, start, end, "n-$epochDay-c"),
            sample(HKQuantityTypeIdentifierDietaryFiber, HKUnit.gramUnit(), fiber, start, end, "n-$epochDay-fi"),
        )
        val food = HKCorrelationType.correlationTypeForIdentifier(HKCorrelationTypeIdentifierFood)!!
        save(listOf(HKCorrelation.correlationWithType(food, date(start), date(end), parts, meta("n-$epochDay"))))
    }

    override suspend fun deleteNutrition(epochDay: Long) {
        val food = HKCorrelationType.correlationTypeForIdentifier(HKCorrelationTypeIdentifierFood)!!
        suspendCancellableCoroutine { c ->
            store.deleteObjectsOfType(food, HKQuery.predicateForObjectsWithMetadataKey(HKMetadataKeySyncIdentifier, listOf("n-$epochDay"))) { _, _, _ -> c.resume(Unit) }
        }
    }

    override suspend fun writeWorkout(id: String, startMs: Long, endMs: Long, title: String, kcal: Double?) {
        val w = HKWorkout.workoutWithActivityType(HKWorkoutActivityTypeTraditionalStrengthTraining, date(startMs), date(endMs), null,
            kcal?.let { HKQuantity.quantityWithUnit(HKUnit.kilocalorieUnit(), it) }, null, meta(id) + mapOf("SuperHealthTitle" to title))
        save(listOf(w))
    }

    override suspend fun writeWater(id: Long, timestampMs: Long, ml: Double) {
        save(listOf(sample(HKQuantityTypeIdentifierDietaryWater, this.ml, ml, timestampMs, timestampMs + 60_000, "h-$id")))
    }
}
