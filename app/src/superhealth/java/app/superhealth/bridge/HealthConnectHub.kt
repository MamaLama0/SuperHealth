package app.superhealth.bridge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Volume
import app.superhealth.HcCache
import app.superhealth.HcSession
import app.superhealth.SuperPrefs
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.ManualLogStore
import app.superhealth.health.data.Metric
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.data.BodyMetricEntity
import app.superhealth.nutrition.data.Repository
import app.superhealth.nutrition.data.WeighInEntity
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.SetType
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * Health Connect pour toute l'app (regroupe les intégrations de La Bouffe, La Salle et LocalHealth) :
 * - lit les pas, pesées, masse grasse, fréquence cardiaque, sommeil, VFC, SpO₂ et séances des autres apps ;
 * - écrit les pesées, le total nutritionnel du jour, les séances de musculation et l'hydratation.
 * Les données de la montre sont exportées par Gadgetbridge lui-même (Réglages > Montre > Health Connect).
 * Health Connect est un stockage local d'Android : rien ne sort du téléphone.
 */
object HealthConnectHub {
    private fun read(c: KClass<out androidx.health.connect.client.records.Record>) = HealthPermission.getReadPermission(c)
    private fun write(c: KClass<out androidx.health.connect.client.records.Record>) = HealthPermission.getWritePermission(c)

    val PERMISSIONS = setOf(
        read(StepsRecord::class), read(WeightRecord::class), read(BodyFatRecord::class),
        read(HeartRateRecord::class), read(RestingHeartRateRecord::class), read(SleepSessionRecord::class),
        read(HeartRateVariabilityRmssdRecord::class), read(OxygenSaturationRecord::class), read(ExerciseSessionRecord::class),
        write(WeightRecord::class), write(BodyFatRecord::class), write(NutritionRecord::class),
        write(ExerciseSessionRecord::class), write(HydrationRecord::class),
    )

    /** Jours relus / réécrits à chaque synchronisation. */
    private const val WINDOW_DAYS = 30L

    enum class Status { UNAVAILABLE, NEEDS_UPDATE, AVAILABLE }

    fun status(ctx: Context): Status = when (HealthConnectClient.getSdkStatus(ctx)) {
        HealthConnectClient.SDK_AVAILABLE -> Status.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.NEEDS_UPDATE
        else -> Status.UNAVAILABLE
    }

    suspend fun granted(ctx: Context): Set<String> =
        if (status(ctx) != Status.AVAILABLE) emptySet()
        else runCatching { HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    data class Result(val readDays: Int, val weightsIn: Int, val sessionsIn: Int, val written: Int, val error: String? = null) {
        fun summary(): String = error?.let(::tr)
            ?: trf("%1\$s j lus · %2\$s pesée(s) · %3\$s activité(s) · %4\$s envoi(s)", readDays, weightsIn, sessionsIn, written)
    }

    suspend fun sync(ctx: Context): Result {
        if (!SuperPrefs.settings.value.healthConnect) return Result(0, 0, 0, 0, "Health Connect désactivé")
        if (status(ctx) != Status.AVAILABLE) return Result(0, 0, 0, 0, "Health Connect indisponible")
        val client = HealthConnectClient.getOrCreate(ctx)
        val repo = NutriApp.repository
        val granted = client.permissionController.getGrantedPermissions()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val from = today.minusDays(WINDOW_DAYS)
        val range = TimeRangeFilter.between(from.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant())
        val me = ctx.packageName
        fun Instant.day() = atZone(zone).toLocalDate().toEpochDay()
        var readDays = 0; var weightsIn = 0; var sessionsIn = 0; var written = 0
        val result = runCatching {
            // Pas : agrégat par jour (Health Connect dédoublonne téléphone / montres). La montre gérée ici reste prioritaire.
            val watchSteps = Watch.enabled && HealthRepository.isSupported(Metric.STEPS)
            if (read(StepsRecord::class) in granted && !watchSteps) {
                val rows = client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                    timeRangeSlicer = Period.ofDays(1),
                )).mapNotNull { r ->
                    val n = r.result[StepsRecord.COUNT_TOTAL] ?: return@mapNotNull null
                    BodyMetricEntity(r.startTime.toLocalDate().toEpochDay(), Repository.STEPS, n.toDouble())
                }
                repo.body.upsertMetrics(rows); readDays = rows.size
            }
            // Pesées d'autres apps (balance connectée…) ; les pesées saisies dans SuperHealth restent prioritaires.
            if (read(WeightRecord::class) in granted) {
                val fat = if (read(BodyFatRecord::class) in granted)
                    client.readRecords(ReadRecordsRequest(BodyFatRecord::class, range)).records
                        .filter { it.metadata.dataOrigin.packageName != me }
                        .associate { it.time.day() to it.percentage.value }
                else emptyMap()
                val local = repo.weighIns.all().map { it.epochDay }.toSet()
                val news = client.readRecords(ReadRecordsRequest(WeightRecord::class, range)).records
                    .filter { it.metadata.dataOrigin.packageName != me }
                    .sortedBy { it.time }
                    .associateBy { it.time.day() }
                    .filterKeys { it !in local }
                    .map { (d, r) -> WeighInEntity(d, r.weight.inKilograms, fat[d]) }
                repo.weighIns.upsertAll(news); weightsIn = news.size
            }
            // Constantes et séances d'autres apps : servent quand la montre n'est pas gérée par SuperHealth.
            SuperPrefs.setHc(readHealth(client, granted, range, me, zone))
            sessionsIn = SuperPrefs.hc.value.sessions.size

            // Écriture : pesées, total nutritionnel du jour, séances de musculation, hydratation (clientRecordId stable → remplacement).
            val version = System.currentTimeMillis()
            if (write(WeightRecord::class) in granted) {
                val ws = repo.weighIns.all().filter { it.epochDay >= from.toEpochDay() }
                val records = ws.flatMap { w ->
                    val t = LocalDate.ofEpochDay(w.epochDay).atTime(8, 0).atZone(zone)
                    listOfNotNull(
                        WeightRecord(t.toInstant(), t.offset, Mass.kilograms(w.weightKg), Metadata.manualEntry(clientRecordId = "w-${w.epochDay}", clientRecordVersion = version)),
                        w.bodyFatPct?.takeIf { write(BodyFatRecord::class) in granted }?.let {
                            BodyFatRecord(t.toInstant(), t.offset, Percentage(it), Metadata.manualEntry(clientRecordId = "bf-${w.epochDay}", clientRecordVersion = version))
                        },
                    )
                }
                if (records.isNotEmpty()) client.insertRecords(records)
                written += records.size
            }
            if (write(NutritionRecord::class) in granted) {
                val byDay = repo.entries.all().filter { it.epochDay >= from.toEpochDay() }.groupBy { it.epochDay }
                val records = byDay.map { (d, es) ->
                    val start = LocalDate.ofEpochDay(d).atStartOfDay(zone)
                    val end = start.plusDays(1).minusSeconds(1)
                    NutritionRecord(
                        startTime = start.toInstant(), startZoneOffset = start.offset,
                        endTime = end.toInstant(), endZoneOffset = end.offset,
                        name = "SuperHealth",
                        energy = Energy.kilocalories(es.sumOf { it.kcal }),
                        protein = Mass.grams(es.sumOf { it.protein }),
                        totalFat = Mass.grams(es.sumOf { it.fat }),
                        totalCarbohydrate = Mass.grams(es.sumOf { it.carbs }),
                        dietaryFiber = Mass.grams(es.sumOf { it.fiber }),
                        metadata = Metadata.manualEntry(clientRecordId = "n-$d", clientRecordVersion = version),
                    )
                }
                if (records.isNotEmpty()) client.insertRecords(records)
                // Jours vidés depuis la dernière synchro : on retire le total écrit.
                val empty = (from.toEpochDay()..today.toEpochDay()).filter { it !in byDay }.map { "n-$it" }
                runCatching { client.deleteRecords(NutritionRecord::class, emptyList(), empty) }
                written += records.size
            }
            if (write(ExerciseSessionRecord::class) in granted) {
                val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
                val records = WorkoutApp.store.state.value.history.filter { it.startTime >= fromMs && it.durationSec > 0 }.map { w ->
                    val start = Instant.ofEpochMilli(w.startTime)
                    val end = start.plusSeconds(w.durationSec.coerceAtLeast(60))
                    val sets = w.exercises.sumOf { e -> e.sets.count { it.done && it.type != SetType.WARMUP } }
                    ExerciseSessionRecord(
                        startTime = start, startZoneOffset = zone.rules.getOffset(start),
                        endTime = end, endZoneOffset = zone.rules.getOffset(end),
                        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                        title = w.name, notes = "$sets séries · SuperHealth",
                        metadata = Metadata.activelyRecorded(Device(type = Device.TYPE_PHONE), "wo-${w.id}", w.startTime + w.durationSec),
                    )
                }
                if (records.isNotEmpty()) client.insertRecords(records)
                written += records.size
            }
            if (write(HydrationRecord::class) in granted) {
                val logs = ManualLogStore.get(ctx).query(Metric.HYDRATION, from.atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis() + 1)
                val records = logs.map { l ->
                    val t = Instant.ofEpochMilli(l.timestampMs)
                    HydrationRecord(t, zone.rules.getOffset(t), t.plusSeconds(60), zone.rules.getOffset(t), Volume.milliliters(l.value),
                        Metadata.manualEntry(clientRecordId = "h-${l.id}", clientRecordVersion = 1))
                }
                if (records.isNotEmpty()) client.insertRecords(records)
                written += records.size
            }
            Result(readDays, weightsIn, sessionsIn, written)
        }.getOrElse { Result(readDays, weightsIn, sessionsIn, written, it.message ?: it.javaClass.simpleName) }
        SuperPrefs.update { it.copy(lastHcSync = System.currentTimeMillis(), lastHcSummary = result.summary()) }
        return result
    }

    private suspend fun readHealth(client: HealthConnectClient, granted: Set<String>, range: TimeRangeFilter, me: String, zone: ZoneId): HcCache {
        fun Instant.day() = atZone(zone).toLocalDate().toEpochDay()
        fun <T> List<T>.avgBy(day: (T) -> Long, v: (T) -> Double) = groupBy(day).mapValues { (_, l) -> l.map(v).average() }
        val prev = SuperPrefs.hc.value
        val hr = if (read(HeartRateRecord::class) in granted)
            client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range)).records.filter { it.metadata.dataOrigin.packageName != me }
                .flatMap { r -> r.samples.map { it.time.day() to it.beatsPerMinute.toDouble() } }.avgBy({ it.first }, { it.second })
        else prev.heartRate
        val resting = if (read(RestingHeartRateRecord::class) in granted)
            client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, range)).records.filter { it.metadata.dataOrigin.packageName != me }
                .avgBy({ it.time.day() }, { it.beatsPerMinute.toDouble() })
        else prev.restingHr
        val sleep = if (read(SleepSessionRecord::class) in granted)
            client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range)).records.filter { it.metadata.dataOrigin.packageName != me }
                .groupBy { it.endTime.day() }.mapValues { (_, l) -> l.sumOf { (it.endTime.toEpochMilli() - it.startTime.toEpochMilli()) / 60000.0 } }
        else prev.sleepMin
        val hrv = if (read(HeartRateVariabilityRmssdRecord::class) in granted)
            client.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, range)).records.filter { it.metadata.dataOrigin.packageName != me }
                .avgBy({ it.time.day() }, { it.heartRateVariabilityMillis })
        else prev.hrv
        val spo2 = if (read(OxygenSaturationRecord::class) in granted)
            client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, range)).records.filter { it.metadata.dataOrigin.packageName != me }
                .avgBy({ it.time.day() }, { it.percentage.value })
        else prev.spo2
        val sessions = if (read(ExerciseSessionRecord::class) in granted)
            client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range)).records.filter { it.metadata.dataOrigin.packageName != me }
                .map { HcSession(it.metadata.id, it.exerciseType, it.title, it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.metadata.dataOrigin.packageName) }
        else prev.sessions
        return HcCache(hr, resting, sleep, hrv, spo2, sessions.sortedByDescending { it.start })
    }
}
