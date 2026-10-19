package app.superhealth.bridge

import app.superhealth.PhoneSession
import app.superhealth.SuperPrefs
import app.superhealth.nutrition.NutriApp
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Activité lancée depuis le téléphone (course, marche, vélo…) : chrono, pause, et fréquence cardiaque en direct de la
 * montre. Vit avec le processus (le service Gadgetbridge le garde actif tant que la montre est connectée), pas avec l'écran.
 */
object LiveSession {
    data class State(
        val kind: Bridge.Kind,
        val start: Long,
        /** Temps passé en pause (ms), hors pause en cours. */
        val pausedMs: Long = 0,
        val pausedAt: Long? = null,
        val hr: List<Pair<Long, Int>> = emptyList(),
        val steps: Int = 0,
    ) {
        fun activeMs(now: Long = System.currentTimeMillis()) = (pausedAt ?: now) - start - pausedMs
        val paused: Boolean get() = pausedAt != null
        val lastBpm: Int? get() = hr.lastOrNull()?.takeIf { System.currentTimeMillis() - it.first < 30_000 }?.second
        val avgBpm: Int? get() = hr.takeIf { it.isNotEmpty() }?.map { it.second }?.average()?.roundToInt()
        val maxBpm: Int? get() = hr.maxOfOrNull { it.second }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    fun start(kind: Bridge.Kind) {
        if (_state.value != null) return
        _state.value = State(kind, System.currentTimeMillis())
        job = scope.launch {
            LiveHeart.samples.collect { s ->
                _state.update { st ->
                    if (st == null || st.paused) st
                    else st.copy(hr = if (s.bpm != null) st.hr + (s.timeMs to s.bpm) else st.hr, steps = st.steps + (s.steps ?: 0))
                }
            }
        }
    }

    fun pause() = _state.update { it?.takeIf { s -> !s.paused }?.copy(pausedAt = System.currentTimeMillis()) ?: it }

    fun resume() = _state.update { st ->
        val at = st?.pausedAt ?: return@update st
        st.copy(pausedMs = st.pausedMs + System.currentTimeMillis() - at, pausedAt = null)
    }

    /** Termine la séance ; enregistrée si elle a duré au moins une minute. Renvoie la séance enregistrée. */
    fun stop(save: Boolean): PhoneSession? {
        job?.cancel(); job = null
        val st = _state.value ?: return null
        _state.value = null
        val end = st.pausedAt ?: System.currentTimeMillis()
        val active = st.activeMs(end) / 1000
        if (!save || active < 60) return null
        val session = PhoneSession(
            id = UUID.randomUUID().toString(), kind = st.kind.name, start = st.start, end = end, activeSec = active,
            avgHr = st.avgBpm, maxHr = st.maxBpm, kcal = kcal(st.hr, active), steps = st.steps, hr = resample(st, end),
        )
        SuperPrefs.updateSessions { it + session }
        return session
    }

    fun delete(id: String) = SuperPrefs.updateSessions { l -> l.filterNot { it.id == id } }

    /** Courbe enregistrée : moyenne par tranche de [PhoneSession.HR_STEP_S] secondes. */
    private fun resample(st: State, end: Long): List<Int?> {
        val step = PhoneSession.HR_STEP_S * 1000L
        val n = ((end - st.start) / step + 1).toInt().coerceAtMost(2_000)
        val buckets = st.hr.groupBy { ((it.first - st.start) / step).toInt() }
        return (0 until n).map { i -> buckets[i]?.map { it.second }?.average()?.roundToInt() }
    }

    /**
     * Calories d'après la fréquence cardiaque (formule de Keytel et al., 2005), avec le profil de la nutrition.
     * Sans fréquence cardiaque, pas d'estimation.
     */
    fun kcal(hr: List<Pair<Long, Int>>, activeSec: Long): Int? {
        if (hr.size < 3) return null
        val p = NutriApp.repository.prefs.profile.value
        // Poids de corps recopié par le pont depuis la dernière pesée.
        val w = app.superhealth.training.WorkoutApp.store.state.value.settings.bodyweightKg.takeIf { it > 0 } ?: return null
        val age = (LocalDate.now().year - p.birthYear).toDouble()
        val avg = hr.map { it.second }.average()
        val perMin = if (p.sex.name == "MALE") (-55.0969 + 0.6309 * avg + 0.1988 * w + 0.2017 * age) / 4.184
            else (-20.4022 + 0.4472 * avg - 0.1263 * w + 0.074 * age) / 4.184
        return (perMin.coerceAtLeast(0.0) * activeSec / 60).roundToInt()
    }

    /** Fréquence cardiaque maximale théorique (220 − âge), pour les zones. */
    fun maxHr(): Int = 220 - (LocalDate.now().year - NutriApp.repository.prefs.profile.value.birthYear)
}
