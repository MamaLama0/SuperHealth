package app.superhealth.wellbeing

import app.superhealth.platform.*

import app.superhealth.platform.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.superhealth.platform.File
import app.superhealth.platform.LocalDate

/**
 * Ressenti du matin (facultatif) : sommeil et énergie notés de 1 à 5 avant d'afficher les scores. Les questionnaires de
 * ressenti réagissent à la charge d'entraînement plus finement que les mesures objectives (Saw et al. 2016, revue
 * systématique), et un score vu avant de se juger influence le ressenti (Gavriloff et al. 2018 : un faux retour de
 * « mauvaise nuit » dégrade la journée). Les réponses entrent dans la Forme du jour.
 */
object Checkin {
    @Serializable data class Feel(val sleep: Int, val energy: Int)

    @Serializable private data class Store(val days: Map<Long, Feel> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var file: File? = null

    val days = MutableStateFlow<Map<Long, Feel>>(emptyMap())

    val SLEEP = listOf("Très mal", "Mal", "Moyen", "Bien", "Très bien")
    val ENERGY = listOf("Épuisé", "Fatigué", "Normal", "En forme", "Au top")

    fun init(ctx: Context) {
        val f = File(File(ctx.filesDir, "wellbeing").apply { mkdirs() }, "checkin.json").also { file = it }
        days.value = runCatching { json.decodeFromString(Store.serializer(), f.readText()).days }.getOrDefault(emptyMap())
    }

    fun today(): Feel? = days.value[LocalDate.now().toEpochDay()]

    fun set(feel: Feel, day: Long = LocalDate.now().toEpochDay()) {
        days.value = days.value + (day to feel)
        // l'alerte des signes de la nuit en tient compte
        BodyAlert.invalidate()
        runCatching { file?.writeText(json.encodeToString(Store.serializer(), Store(days.value))) }
    }
}
