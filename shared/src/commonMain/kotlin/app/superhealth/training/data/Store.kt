package app.superhealth.training.data

import app.superhealth.platform.*

import app.superhealth.platform.Context
import app.superhealth.training.domain.CustomWorkout
import app.superhealth.training.domain.ExerciseConfig
import app.superhealth.training.domain.Gym
import app.superhealth.training.domain.Program
import app.superhealth.training.domain.Session
import app.superhealth.training.domain.Settings
import app.superhealth.training.domain.Workout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.superhealth.platform.File

/** Tout l'état de l'application (aucun compte, aucun serveur) : programmes, bibliothèque, historique, salles, réglages. */
@Serializable
data class AppState(
    val version: Int = 1,
    val settings: Settings = Settings(),
    val programs: List<Program> = emptyList(),
    val customWorkouts: List<CustomWorkout> = emptyList(),
    val history: List<Workout> = emptyList(),
    val gyms: List<Gym> = emptyList(),
    val exerciseConfig: Map<String, ExerciseConfig> = emptyMap(),
    /** Exercices créés par l'utilisateur (même format que la base de l'APK). */
    val customExercises: List<app.superhealth.training.domain.Exercise> = emptyList(),
    val session: Session? = null,
)

val StoreJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

/** Persistance JSON dans le stockage privé ; écriture atomique en arrière-plan, la dernière version l'emporte. */
class Store(context: Context) {
    private val file = File(context.filesDir, "state.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = Channel<AppState>(Channel.CONFLATED)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state

    init {
        scope.launch { for (s in pending) write(s) }
    }

    private fun load(): AppState = runCatching { StoreJson.decodeFromString(AppState.serializer(), file.readText()) }
        .getOrElse { File(file.path + ".bak").takeIf { it.exists() }?.let { b -> runCatching { StoreJson.decodeFromString(AppState.serializer(), b.readText()) }.getOrNull() } ?: AppState() }

    private fun write(s: AppState) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(StoreJson.encodeToString(AppState.serializer(), s))
        if (file.exists()) file.copyTo(File(file.path + ".bak"), overwrite = true)
        tmp.renameTo(file)
    }

    fun update(f: (AppState) -> AppState) {
        val next = f(_state.value)
        _state.value = next
        pending.trySend(next)
    }

    fun export(): String = StoreJson.encodeToString(AppState.serializer(), _state.value.copy(session = null))

    fun import(text: String) {
        val s = StoreJson.decodeFromString(AppState.serializer(), text)
        // positions de programme invalides (sauvegarde ancienne ou modifiée à la main) recalées
        update { s.copy(session = it.session, programs = s.programs.map(app.superhealth.training.domain.ProgramEdit::fixCursor)) }
    }
}
