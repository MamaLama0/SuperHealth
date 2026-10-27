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
import app.superhealth.HcPhase
import app.superhealth.HcSession
import app.superhealth.SuperPrefs
import app.superhealth.health.data.DataSource
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.SourceKind
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
            SuperPrefs.setHc(readHealth(client, granted, range, me, zone, ctx.packageManager))
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

    private suspend fun readHealth(client: HealthConnectClient, granted: Set<String>, range: TimeRangeFilter, me: String, zone: ZoneId,
                                   pm: android.content.pm.PackageManager): HcCache {
        fun Instant.day() = atZone(zone).toLocalDate().toEpochDay()
        fun <T> List<T>.avgBy(day: (T) -> Long, v: (T) -> Double) = groupBy(day).mapValues { (_, l) -> l.map(v).average() }
        val prev = SuperPrefs.hc.value
        val origins = prev.origins.toMutableMap()
        /** Retient la source de l'enregistrement le plus récent de [metric]. */
        fun <R : androidx.health.connect.client.records.Record> List<R>.noteOrigin(metric: Metric, time: (R) -> Instant): List<R> = also { rs ->
            rs.maxByOrNull(time)?.let { origins[metric.name] = sourceOf(it.metadata, pm) }
        }
        val hr = if (read(HeartRateRecord::class) in granted)
            client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range)).records.filter { it.metadata.dataOrigin.packageName != me }
                .noteOrigin(Metric.HEART_RATE) { it.endTime }.flatMap { r -> r.samples.map { it.time.day() to it.beatsPerMinute.toDouble() } }.avgBy({ it.first }, { it.second })
        else prev.heartRate
        val resting = if (read(RestingHeartRateRecord::class) in granted)
            client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, range)).records.filter { it.metadata.dataOrigin.packageName != me }
                .noteOrigin(Metric.RESTING_HR) { it.time }.avgBy({ it.time.day() }, { it.beatsPerMinute.toDouble() })
        else prev.restingHr
        val sleepRecords = if (read(SleepSessionRecord::class) in granted)
            client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range)).records.filter { it.metadata.dataOrigin.packageName != me }
                .noteOrigin(Metric.SLEEP) { it.endTime }
        else null
        // Une même nuit écrite par deux applis (montre + téléphone…) ne compte qu'une fois : par jour, l'appli qui a le plus
        // de sommeil ; les siestes d'une même appli s'additionnent. Avec des phases, l'éveil ne compte pas comme du sommeil.
        val nights = sleepRecords?.groupBy { it.endTime.day() }?.mapValues { (_, l) ->
            l.groupBy { it.metadata.dataOrigin.packageName }.values.map { rs -> rs.flatMap(::phasesOf).sortedBy { it.start } }
                .maxBy { ps -> ps.asleepMin() }
        } ?: prev.nights
        val sleep = if (sleepRecords != null) nights.mapValues { (_, ps) -> ps.asleepMin() }.filterValues { it > 0 } else prev.sleepMin
        val hrv = if (read(HeartRateVariabilityRmssdRecord::class) in granted)
            client.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, range)).records.filter { it.metadata.dataOrigin.packageName != me }
                .noteOrigin(Metric.HRV) { it.time }.avgBy({ it.time.day() }, { it.heartRateVariabilityMillis })
        else prev.hrv
        val spo2 = if (read(OxygenSaturationRecord::class) in granted)
            client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, range)).records.filter { it.metadata.dataOrigin.packageName != me }
                .noteOrigin(Metric.SPO2) { it.time }.avgBy({ it.time.day() }, { it.percentage.value })
        else prev.spo2
        val sessions = if (read(ExerciseSessionRecord::class) in granted)
            client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range)).records.filter { it.metadata.dataOrigin.packageName != me }
                .map { HcSession(it.metadata.id, it.exerciseType, it.title, it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.metadata.dataOrigin.packageName) }
        else prev.sessions
        val steps = if (read(StepsRecord::class) in granted) runCatching { stepsHourly(client, range, me) }.getOrDefault(prev.stepsHourly)
        else prev.stepsHourly
        return HcCache(hr, resting, sleep, hrv, spo2, sessions.sortedByDescending { it.start }, steps, origins, nights)
    }

    /** Phases d'une session de sommeil ; sans phases (appli qui ne note que la durée), une seule phase « endormi ». */
    private fun phasesOf(r: SleepSessionRecord): List<HcPhase> =
        r.stages.map { s ->
            val kind = when (s.stage) {
                SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "AWAKE_SLEEP"
                SleepSessionRecord.STAGE_TYPE_LIGHT -> "LIGHT_SLEEP"
                SleepSessionRecord.STAGE_TYPE_DEEP -> "DEEP_SLEEP"
                SleepSessionRecord.STAGE_TYPE_REM -> "REM_SLEEP"
                else -> "SLEEP_ANY"
            }
            HcPhase(kind, s.startTime.toEpochMilli(), s.endTime.toEpochMilli())
        }.ifEmpty { listOf(HcPhase("SLEEP_ANY", r.startTime.toEpochMilli(), r.endTime.toEpochMilli())) }

    private fun List<HcPhase>.asleepMin(): Double = filter { it.kind != "AWAKE_SLEEP" }.sumOf { it.end - it.start } / 60000.0

    /** Appli d'origine et type d'appareil d'un enregistrement (déclaré par l'appli, sinon celui qu'elle utilise d'habitude). */
    private fun sourceOf(m: Metadata, pm: android.content.pm.PackageManager): DataSource {
        val d = m.device
        val kind = when (d?.type) {
            Device.TYPE_WATCH -> SourceKind.WATCH
            Device.TYPE_FITNESS_BAND -> SourceKind.BAND
            Device.TYPE_RING -> SourceKind.RING
            Device.TYPE_CHEST_STRAP -> SourceKind.CHEST
            Device.TYPE_SCALE -> SourceKind.SCALE
            Device.TYPE_PHONE -> SourceKind.PHONE
            else -> null
        }
        val model = listOfNotNull(d?.manufacturer, d?.model).joinToString(" ").takeIf { it.isNotBlank() }
        val pkg = m.dataOrigin.packageName
        val label = DataSource.appName(pkg)
            ?: runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        return DataSource.fromApp(pkg, label, kind, model)
    }

    /**
     * Pas des autres sources par heure (le podomètre du téléphone est enregistré par Android lui-même dans Health Connect).
     * Un enregistrement à cheval sur plusieurs heures est réparti au prorata ; entre deux sources, on garde le maximum de
     * chaque heure (le même pas compté par deux apps ne s'additionne pas).
     */
    private suspend fun stepsHourly(client: HealthConnectClient, range: TimeRangeFilter, me: String): Map<Long, Double> {
        val hour = 3_600_000L
        val byOrigin = HashMap<String, HashMap<Long, Double>>()
        var token: String? = null
        do {
            val res = client.readRecords(ReadRecordsRequest(StepsRecord::class, range, pageSize = 5000, pageToken = token))
            res.records.filter { it.metadata.dataOrigin.packageName != me && it.count > 0 }.forEach { r ->
                val start = r.startTime.toEpochMilli()
                val end = maxOf(r.endTime.toEpochMilli(), start + 1)
                val buckets = byOrigin.getOrPut(r.metadata.dataOrigin.packageName) { HashMap() }
                var h = start / hour * hour
                while (h < end) {
                    val overlap = minOf(end, h + hour) - maxOf(start, h)
                    buckets[h] = (buckets[h] ?: 0.0) + r.count * overlap.toDouble() / (end - start)
                    h += hour
                }
            }
            token = res.pageToken
        } while (token != null)
        val merged = HashMap<Long, Double>()
        byOrigin.values.forEach { b -> b.forEach { (h, v) -> merged[h] = maxOf(merged[h] ?: 0.0, v) } }
        return merged.filterValues { it >= 1 }
    }
}
