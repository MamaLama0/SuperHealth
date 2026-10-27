package app.superhealth.nutrition.data

import android.content.Context
import app.superhealth.nutrition.domain.CheckIn
import app.superhealth.nutrition.domain.Goal
import app.superhealth.nutrition.domain.Profile
import app.superhealth.nutrition.domain.Program
import app.superhealth.nutrition.domain.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true; coerceInputValues = true }

/** Profil, programme et réglages, stockés en JSON dans les SharedPreferences (100 % local). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("nutrilocal", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(read("profile", Profile.serializer()) ?: Profile())
    private val _program = MutableStateFlow(read("program", Program.serializer()) ?: Program())
    private val _settings = MutableStateFlow(read("settings", Settings.serializer()) ?: Settings())

    private val _goals = MutableStateFlow(read("goals", ListSerializer(Goal.serializer())) ?: emptyList())
    private val _checkIns = MutableStateFlow(read("checkins", ListSerializer(CheckIn.serializer())) ?: emptyList())
    private val _ai = MutableStateFlow(read("ai", AiConfig.serializer()) ?: AiConfig())

    /** Réglages IA (clé API) : hors sauvegarde. */
    val ai: StateFlow<AiConfig> = _ai
    fun setAi(c: AiConfig) { _ai.value = c; write("ai", AiConfig.serializer(), c) }

    val profile: StateFlow<Profile> = _profile
    val program: StateFlow<Program> = _program
    val settings: StateFlow<Settings> = _settings

    fun setProfile(p: Profile) { _profile.value = p; write("profile", Profile.serializer(), p) }
    fun setProgram(p: Program) { _program.value = p; write("program", Program.serializer(), p) }
    fun setSettings(s: Settings) { _settings.value = s; write("settings", Settings.serializer(), s) }

    /** Historique des objectifs (le dernier en cours est l'objectif actif). */
    val goals: StateFlow<List<Goal>> = _goals
    /** Historique des check-ins hebdomadaires (mises à jour du programme). */
    val checkIns: StateFlow<List<CheckIn>> = _checkIns

    fun setGoals(g: List<Goal>) { val v = g.sortedBy { it.startDay }; _goals.value = v; write("goals", ListSerializer(Goal.serializer()), v) }
    fun setCheckIns(c: List<CheckIn>) { val v = c.sortedBy { it.day }; _checkIns.value = v; write("checkins", ListSerializer(CheckIn.serializer()), v) }


    private fun <T> read(key: String, ser: KSerializer<T>): T? =
        sp.getString(key, null)?.let { runCatching { AppJson.decodeFromString(ser, it) }.getOrNull() }

    private fun <T> write(key: String, ser: KSerializer<T>, value: T) {
        sp.edit().putString(key, AppJson.encodeToString(ser, value)).apply()
    }
}
