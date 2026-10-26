package app.superhealth.bridge

import app.superhealth.platform.*

import app.superhealth.SuperPrefs
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.HealthSources
import app.superhealth.health.data.ManualLogStore
import app.superhealth.health.data.Metric
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.data.WeighInEntity
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.SetType
import app.superhealth.ui.tr
import app.superhealth.ui.trf

/**
 * Échanges avec Apple Santé (rôle de Health Connect sur Android) : pesées des autres apps importées, et SuperHealth
 * y écrit ses pesées, le total nutritionnel du jour, ses séances de musculation et l'hydratation. Les constantes
 * (pouls, sommeil, VFC…) sont lues directement par HealthRepository.
 */
object HealthConnectHub {
    /** Nombre de types demandés à Santé (affichage « n sur m accordées »). */
    val PERMISSIONS: Set<String> = setOf("steps", "distance", "energy", "exercise", "heart", "resting", "hrv", "sleep", "spo2",
        "respiration", "vo2max", "temperature", "workouts", "weight", "bodyfat", "nutrition", "water")

    enum class Status { UNAVAILABLE, NEEDS_UPDATE, AVAILABLE }

    fun status(ctx: Context): Status = if (HealthSources.current.available) Status.AVAILABLE else Status.UNAVAILABLE

    /**
     * HealthKit ne dit jamais quelles lectures sont autorisées (confidentialité) : une fois la demande faite, on considère
     * l'accès accordé ; les mesures refusées restent simplement vides.
     */
    suspend fun granted(ctx: Context): Set<String> = if (SuperPrefs.settings.value.healthConnect) PERMISSIONS else emptySet()

    /** Affiche la demande d'autorisation de Santé, puis active l'échange. */
    suspend fun request(): Boolean {
        val ok = runCatching { HealthSources.current.requestAuthorization() }.getOrDefault(false)
        if (ok) SuperPrefs.update { it.copy(healthConnect = true) }
        HealthRepository.invalidate()
        return ok
    }

    data class Result(val readDays: Int, val weightsIn: Int, val sessionsIn: Int, val written: Int, val error: String? = null) {
        fun summary(): String = error?.let(::tr)
            ?: trf("%1\$s j lus · %2\$s pesée(s) · %3\$s activité(s) · %4\$s envoi(s)", readDays, weightsIn, sessionsIn, written)
    }

    private const val WINDOW_DAYS = 30L

    suspend fun sync(ctx: Context): Result {
        if (!SuperPrefs.settings.value.healthConnect) return Result(0, 0, 0, 0, "Apple Santé désactivé")
        val src = HealthSources.current
        if (!src.available) return Result(0, 0, 0, 0, "Apple Santé indisponible")
        val repo = NutriApp.repository
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val from = today.minusDays(WINDOW_DAYS)
        val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()
        var weightsIn = 0; var sessionsIn = 0; var written = 0
        val result = runCatching {
            // Pesées d'autres apps (balance connectée…) ; les pesées saisies dans SuperHealth restent prioritaires.
            val local = repo.weighIns.all().map { it.epochDay }.toSet()
            val news = src.weights(fromMs, toMs).sortedBy { it.first }.associateBy { day(it.first) }
                .filterKeys { it !in local }
                .map { (d, w) -> WeighInEntity(d, w.second, w.third) }
            repo.weighIns.upsertAll(news); weightsIn = news.size
            sessionsIn = runCatching { src.workouts(fromMs, toMs).size }.getOrDefault(0)

            // Écriture : pesées, total nutritionnel du jour, séances de musculation, hydratation.
            repo.weighIns.all().filter { it.epochDay >= from.toEpochDay() }.forEach { w ->
                src.writeWeight(w.epochDay, w.weightKg, w.bodyFatPct); written++
            }
            val byDay = repo.entries.all().filter { it.epochDay >= from.toEpochDay() }.groupBy { it.epochDay }
            byDay.forEach { (d, es) ->
                src.writeNutrition(d, es.sumOf { it.kcal }, es.sumOf { it.protein }, es.sumOf { it.fat }, es.sumOf { it.carbs }, es.sumOf { it.fiber })
                written++
            }
            (from.toEpochDay()..today.toEpochDay()).filter { it !in byDay }.forEach { runCatching { src.deleteNutrition(it) } }
            WorkoutApp.store.state.value.history.filter { it.startTime >= fromMs && it.durationSec > 0 }.forEach { w ->
                val sets = w.exercises.sumOf { e -> e.sets.count { it.done && it.type != SetType.WARMUP } }
                src.writeWorkout("wo-${w.id}", w.startTime, w.startTime + w.durationSec.coerceAtLeast(60) * 1000L, w.name, null)
                written++
            }
            ManualLogStore.get(ctx).query(Metric.HYDRATION, fromMs, System.currentTimeMillis() + 1).forEach { l ->
                src.writeWater(l.id, l.timestampMs, l.value); written++
            }
            HealthRepository.invalidate()
            Result(WINDOW_DAYS.toInt(), weightsIn, sessionsIn, written)
        }.getOrElse { Result(0, weightsIn, sessionsIn, written, it.message ?: it::class.simpleName) }
        SuperPrefs.update { it.copy(lastHcSync = System.currentTimeMillis(), lastHcSummary = result.summary()) }
        return result
    }
}

/** Pouls en direct : chaque mesure dès qu'Apple Santé la reçoit (pendant une séance, la montre en envoie chaque seconde). */
object LiveHeart {
    data class Sample(val timeMs: Long, val bpm: Int?)
    fun available(): Boolean = Watch.enabled && Watch.devices().isNotEmpty() && HealthSources.current.supports(Metric.HEART_RATE)
    val samples: kotlinx.coroutines.flow.Flow<Sample> = kotlinx.coroutines.flow.flow {
        HealthSources.current.liveHeart()?.collect { emit(Sample(it.timestampMs, it.value.toInt())) }
    }
}
