package app.superhealth.bridge

import app.superhealth.platform.*

import app.superhealth.nutrition.domain.stepGoalFor
import app.superhealth.platform.Context
import app.superhealth.HcSession
import app.superhealth.SuperPrefs
import app.superhealth.health.data.ActivityKind
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.Metric
import app.superhealth.health.data.Workout
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.data.BodyMetricEntity
import app.superhealth.nutrition.data.Repository
import app.superhealth.nutrition.data.WeighInEntity
import app.superhealth.nutrition.domain.Profile
import app.superhealth.nutrition.domain.Settings
import app.superhealth.training.WorkoutApp
import app.superhealth.ui.Lang
import app.superhealth.platform.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.superhealth.health.data.ActivityUser
import kotlin.math.roundToInt
import app.superhealth.training.domain.Language as TLanguage
import app.superhealth.training.domain.Sex as TSex
import app.superhealth.training.domain.ThemeMode as TThemeMode
import app.superhealth.training.domain.WeightUnit as TWeightUnit

/**
 * Fait communiquer les trois modules. Une seule source pour chaque donnée :
 * - profil (sexe, naissance, taille), langue, thème et unités : module nutrition (questionnaire d'accueil, Réglages) ;
 * - poids de corps : dernière pesée du module nutrition (saisie, balance de la montre ou Health Connect) ;
 * - pas : la montre quand elle les mesure, sinon Health Connect.
 * Ces valeurs sont recopiées vers le module entraînement et vers le profil Gadgetbridge (envoyé aux montres).
 */
object Bridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start(ctx: Context) {
        val repo = NutriApp.repository
        // Langue : appliquée avant toute interface, puis suivie.
        Lang.apply(repo.prefs.settings.value.language.name)
        scope.launch {
            val lastWeight = repo.weighIns.allFlow().map { l -> l.maxByOrNull { it.epochDay }?.weightKg }.distinctUntilChanged()
            combine(repo.prefs.profile, repo.prefs.settings, lastWeight) { p, s, w -> Triple(p, s, w) }
                .distinctUntilChanged()
                .collect { (p, s, w) -> propagate(ctx, p, s, w) }
        }
    }

    private fun propagate(ctx: Context, p: Profile, s: Settings, weightKg: Double?) {
        Lang.apply(s.language.name)
        WorkoutApp.store.update { st ->
            st.copy(settings = st.settings.copy(
                sex = TSex.valueOf(p.sex.name),
                unit = TWeightUnit.valueOf(s.weightUnit.name),
                bodyweightKg = weightKg ?: st.settings.bodyweightKg,
                theme = TThemeMode.valueOf(s.themeMode.name),
                language = TLanguage.valueOf(s.language.name),
            ))
        }
        // Profil d'activité (objectifs des anneaux, âge des zones cardiaques) : sexe, âge, taille, poids, objectif de pas.
        val gb = ActivityUser.edit()
        gb.putString(ActivityUser.PREF_USER_GENDER, (if (p.sex.name == "MALE") ActivityUser.GENDER_MALE else ActivityUser.GENDER_FEMALE).toString())
        gb.putString(ActivityUser.PREF_USER_DATE_OF_BIRTH, LocalDate.of(p.birthYear, 1, 1).toString())
        gb.putString(ActivityUser.PREF_USER_HEIGHT_CM, p.heightCm.roundToInt().toString())
        weightKg?.let { gb.putString(ActivityUser.PREF_USER_WEIGHT_KG, it.roundToInt().toString()) }
        s.stepGoalFor(LocalDate.now()).takeIf { it > 0 }?.let { gb.putString(ActivityUser.PREF_USER_STEPS_GOAL, it.toString()) }
        gb.apply()
    }

    /**
     * Montre → nutrition : pas des 14 derniers jours (ils affinent la dépense énergétique) et pesées d'une balance
     * gérée par Gadgetbridge, pour les jours sans pesée saisie.
     */
    suspend fun pullWatch(ctx: Context) {
        if (!Watch.enabled || HealthRepository.devices().isEmpty()) return
        val repo = NutriApp.repository
        val today = LocalDate.now()
        val days = (0L until 14L).map { today.minusDays(it) }
        if (HealthRepository.isSupported(Metric.STEPS)) {
            val rows = days.mapNotNull { d ->
                steps(ctx, d)?.let { BodyMetricEntity(d.toEpochDay(), Repository.STEPS, it) }
            }
            if (rows.isNotEmpty()) repo.body.upsertMetrics(rows)
        }
        val local = repo.weighIns.all().map { it.epochDay }.toSet()
        val weights = days.filter { it.toEpochDay() !in local }.mapNotNull { d ->
            HealthRepository.dayValue(ctx, Metric.WEIGHT, d)?.let { WeighInEntity(d.toEpochDay(), it) }
        }
        if (weights.isNotEmpty()) repo.weighIns.upsertAll(weights)
    }

    /**
     * Pas par heure du jour [date] : pour chaque heure, le plus grand compte entre la montre et les autres sources
     * (téléphone via Health Connect). Les heures passées sans la montre comptent donc les pas du téléphone, et une heure
     * où les deux comptaient n'est pas comptée deux fois.
     */
    suspend fun hourlySteps(ctx: Context, date: LocalDate): List<app.superhealth.health.data.Point> {
        val from = HealthRepository.startMs(date)
        val to = HealthRepository.endMs(date)
        val watch = if (Watch.enabled && HealthRepository.isSupported(Metric.STEPS))
            runCatching { HealthRepository.intraday(Metric.STEPS, date) }.getOrDefault(emptyList()).associate { it.timestampMs to it.value }
        else emptyMap()
        val phone = SuperPrefs.hc.value.stepsHourly.filterKeys { it in from until to }
        return (watch.keys + phone.keys).sorted().map { h -> app.superhealth.health.data.Point(h, maxOf(watch[h] ?: 0.0, phone[h] ?: 0.0)) }
    }

    /** Total de pas du jour : fusion montre + téléphone heure par heure, sinon total de la montre ou de Health Connect. */
    suspend fun steps(ctx: Context, date: LocalDate): Double? {
        val watchTotal = if (Watch.enabled && HealthRepository.isSupported(Metric.STEPS)) HealthRepository.dayValue(ctx, Metric.STEPS, date) else null
        val hasPhone = SuperPrefs.hc.value.stepsHourly.keys.any { it in HealthRepository.startMs(date) until HealthRepository.endMs(date) }
        if (!hasPhone) return watchTotal
            ?: NutriApp.repository.body.metrics().firstOrNull { it.epochDay == date.toEpochDay() && it.metric == Repository.STEPS }?.value
        val merged = hourlySteps(ctx, date).sumOf { it.value }
        return maxOf(merged, watchTotal ?: 0.0).takeIf { it > 0 }
    }

    /** Pouls minute par minute (s, bpm) du jour. */
    suspend fun minuteHeart(date: LocalDate): List<Pair<Long, Int>> = HealthRepository.minuteHeart(date)

    /** Valeur santé du jour : montre gérée par SuperHealth, sinon dernière lecture Health Connect. */
    suspend fun healthValue(ctx: Context, metric: Metric, date: LocalDate): Double? {
        if (metric == Metric.STEPS) return steps(ctx, date)
        if (Watch.enabled && HealthRepository.isSupported(metric)) HealthRepository.dayValue(ctx, metric, date)?.let { return it }
        val hc = SuperPrefs.hc.value
        val d = date.toEpochDay()
        return when (metric) {
            Metric.STEPS -> NutriApp.repository.body.metrics().firstOrNull { it.epochDay == d && it.metric == Repository.STEPS }?.value
            Metric.HEART_RATE -> hc.heartRate[d]
            Metric.RESTING_HR -> hc.restingHr[d]
            Metric.SLEEP -> hc.sleepMin[d]
            Metric.HRV -> hc.hrv[d]
            Metric.SPO2 -> hc.spo2[d]
            // pas de score de la montre : Repos calculé d'après les phases de la nuit
            Metric.SLEEP_SCORE -> app.superhealth.health.data.Scores.restOf(ctx, date)
            Metric.HYDRATION, Metric.GLUCOSE, Metric.BODY_TEMPERATURE, Metric.RECOVERY, Metric.SLEEP_DEBT, Metric.EFFORT,
            Metric.FITNESS_AGE -> HealthRepository.dayValue(ctx, metric, date)
            else -> null
        }
    }

    /**
     * Nuit qui se termine le jour [date], phase par phase : Apple Santé (une seule source par nuit, ses phases si
     * elle en fournit, sinon une seule phase « endormi »). null sans nuit.
     */
    suspend fun sleepNight(date: LocalDate): List<HealthRepository.SleepPhase>? {
        if (Watch.enabled && HealthRepository.isSupported(Metric.SLEEP)) HealthRepository.sleepNight(date)?.let { return it }
        return SuperPrefs.hc.value.nights[date.toEpochDay()]
            ?.mapNotNull { p -> runCatching { ActivityKind.valueOf(p.kind) }.getOrNull()?.let { HealthRepository.SleepPhase(it, p.start, p.end) } }
            ?.sortedBy { it.start }?.takeIf { it.isNotEmpty() }
    }

    /** La nuit a-t-elle des phases (léger, profond, paradoxal) ou seulement une durée ? */
    fun hasStages(night: List<HealthRepository.SleepPhase>): Boolean =
        night.any { it.kind == ActivityKind.LIGHT_SLEEP || it.kind == ActivityKind.DEEP_SLEEP || it.kind == ActivityKind.REM_SLEEP }

    /** D'où vient [metric] le jour [date] : l'appareil ou l'appli qui l'a écrit dans Apple Santé, ou la saisie. */
    suspend fun dataSource(metric: Metric, date: LocalDate): app.superhealth.health.data.DataSource? {
        if (metric.manual) return app.superhealth.health.data.DataSource(app.superhealth.health.data.SourceKind.MANUAL)
        if (Watch.enabled && HealthRepository.isSupported(metric)) HealthRepository.sourceOf(metric, date)?.let { return it }
        return SuperPrefs.hc.value.origins[metric.name]
    }

    /** Une mesure est-elle disponible (montre qui la mesure, ou données Health Connect récentes) ? */
    fun hasSource(metric: Metric): Boolean {
        if (metric.manual) return true
        if (Watch.enabled && HealthRepository.isSupported(metric)) return true
        val hc = SuperPrefs.hc.value
        return when (metric) {
            Metric.STEPS -> true
            Metric.HEART_RATE -> hc.heartRate.isNotEmpty()
            Metric.RESTING_HR -> hc.restingHr.isNotEmpty()
            Metric.SLEEP -> hc.sleepMin.isNotEmpty()
            Metric.HRV -> hc.hrv.isNotEmpty()
            Metric.SPO2 -> hc.spo2.isNotEmpty()
            Metric.RECOVERY -> hc.hrv.isNotEmpty() || hc.restingHr.isNotEmpty()
            Metric.FITNESS_AGE -> hc.restingHr.isNotEmpty() || (Watch.enabled && HealthRepository.isSupported(Metric.RESTING_HR))
            Metric.SLEEP_DEBT -> hc.sleepMin.isNotEmpty()
            Metric.SLEEP_SCORE -> Watch.enabled && HealthRepository.isSupported(Metric.SLEEP)
            else -> false
        }
    }

    // ---- Activités : musculation, montre et Health Connect dans une seule liste ---------------------------

    enum class Kind { STRENGTH, RUN, WALK, HIKE, BIKE, SWIM, YOGA, OTHER }
    enum class Source(val label: String) { GYM("SuperHealth"), WATCH("Apple Santé"), HEALTH_CONNECT("Apple Santé"), IMPORT("Import") }

    data class Activity(
        val key: String, val kind: Kind, val title: String, val start: Long, val end: Long, val source: Source,
        /** Séance de musculation (id d'historique), séance de montre, séance Health Connect ou séance importée. */
        val gymId: String? = null, val watch: Workout? = null, val hc: HcSession? = null, val run: RunImport.Run? = null,
    ) {
        val minutes: Long get() = (end - start) / 60000
        val distanceM: Double? get() = watch?.distanceM ?: run?.distanceM
    }

    suspend fun activities(fromMs: Long, toMs: Long = System.currentTimeMillis()): List<Activity> {
        val gym = WorkoutApp.store.state.value.history.filter { it.startTime in fromMs..toMs }.map { w ->
            Activity("g-${w.id}", Kind.STRENGTH, w.name, w.startTime, w.startTime + w.durationSec * 1000L, Source.GYM, gymId = w.id)
        }
        val watch = runCatching { HealthRepository.workouts(fromMs, toMs) }.getOrDefault(emptyList()).map { w ->
            Activity("w-${w.id}", kindOf(w.kind.name), w.name?.takeIf { it.isNotBlank() } ?: kindLabel(kindOf(w.kind.name).name),
                w.start.time, w.end.time, Source.WATCH, watch = w)
        }
        val imported = RunImport.runs.value.filter { it.start in fromMs..toMs }.map { r ->
            Activity("i-${r.id}", runCatching { Kind.valueOf(r.kind) }.getOrDefault(Kind.OTHER), r.name, r.start, r.end, Source.IMPORT, run = r)
        }
        // Séances Health Connect : on écarte celles qui recouvrent une séance déjà listée (même activité vue deux fois).
        val known = gym + watch + imported
        val hc = SuperPrefs.hc.value.sessions.filter { it.start in fromMs..toMs }.filter { s ->
            known.none { it.start < s.end && s.start < it.end }
        }.map { s -> Activity("h-${s.id}", hcKind(s.type), s.title ?: hcLabel(s.type), s.start, s.end, Source.HEALTH_CONNECT, hc = s) }
        return (known + hc).sortedByDescending { it.start }
    }

    fun kindOf(name: String): Kind = when {
        "SWIM" in name -> Kind.SWIM
        "CYCL" in name || "BIK" in name -> Kind.BIKE
        "HIK" in name -> Kind.HIKE
        "WALK" in name -> Kind.WALK
        "RUN" in name || "JOG" in name -> Kind.RUN
        "YOGA" in name -> Kind.YOGA
        "STRENGTH" in name || "WEIGHT" in name -> Kind.STRENGTH
        else -> Kind.OTHER
    }

    /** Types d'exercice de Health Connect (ExerciseSessionRecord), gardés pour les séances déjà en cache. */
    private fun hcKind(type: Int): Kind = when (type) {
        56, 57 -> Kind.RUN
        79 -> Kind.WALK
        37 -> Kind.HIKE
        8, 9 -> Kind.BIKE
        73, 74 -> Kind.SWIM
        83 -> Kind.YOGA
        70, 81 -> Kind.STRENGTH
        else -> Kind.OTHER
    }

    private fun hcLabel(type: Int): String = kindLabel(hcKind(type).name)

    fun kindLabel(kind: String): String = when (runCatching { Kind.valueOf(kind) }.getOrDefault(Kind.OTHER)) {
        Kind.RUN -> "Course"; Kind.WALK -> "Marche"; Kind.HIKE -> "Randonnée"; Kind.BIKE -> "Vélo"; Kind.SWIM -> "Natation"
        Kind.YOGA -> "Yoga"; Kind.STRENGTH -> "Musculation"; Kind.OTHER -> "Activité"
    }

    /** Fréquence cardiaque moyenne de la montre pendant une séance (musculation comprise). */
    suspend fun averageHeartRate(start: Long, end: Long): Int? {
        if (!Watch.enabled || !HealthRepository.isSupported(Metric.HEART_RATE)) return null
        val zone = ZoneId.systemDefault()
        val days = generateSequence(Instant.ofEpochMilli(start).atZone(zone).toLocalDate()) { it.plusDays(1) }
            .takeWhile { !it.isAfter(Instant.ofEpochMilli(end).atZone(zone).toLocalDate()) }.toList()
        val pts = days.flatMap { HealthRepository.intraday(Metric.HEART_RATE, it) }.filter { it.timestampMs in start..end }
        return pts.takeIf { it.isNotEmpty() }?.map { it.value }?.average()?.roundToInt()
    }
}
