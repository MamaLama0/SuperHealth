package app.superhealth.wellbeing

import app.superhealth.platform.*

import app.superhealth.platform.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.superhealth.platform.File
import app.superhealth.platform.LocalDate

/**
 * Journal du moral : chaque jour, l'humeur (1 à 5) et, si l'on veut, une note. Rien d'autre n'est demandé : l'humeur se
 * lit seule, sur l'année en couleurs. Elle entre dans la Forme du jour (comparée à votre humeur habituelle).
 */
object Journal {
    @Serializable data class Entry(val mood: Int, val note: String = "", val at: Long = 0)

    val MOODS = listOf("Très mal", "Mal", "Moyen", "Bien", "Très bien")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Serializable private data class Store(val days: Map<Long, Entry> = emptyMap())
    private var file: File? = null

    /** Entrées par jour (epochDay). */
    val days = MutableStateFlow<Map<Long, Entry>>(emptyMap())

    fun init(ctx: Context) {
        val f = File(File(ctx.filesDir, "wellbeing").apply { mkdirs() }, "journal.json").also { file = it }
        days.value = runCatching { json.decodeFromString(Store.serializer(), f.readText()).days }.getOrDefault(emptyMap())
    }

    fun today(): Entry? = days.value[LocalDate.now().toEpochDay()]

    fun set(ctx: Context, day: Long, e: Entry?) {
        days.value = if (e == null) days.value - day else days.value + (day to e)
        runCatching { file?.writeText(json.encodeToString(Store.serializer(), Store(days.value))) }
        Reminders.schedule(ctx)
    }

    fun setMood(ctx: Context, mood: Int, day: Long = LocalDate.now().toEpochDay()) {
        val old = days.value[day]
        set(ctx, day, Entry(mood.coerceIn(1, 5), old?.note.orEmpty(), System.currentTimeMillis()))
    }

    /** Note du jour [day] (il faut une humeur ce jour-là). */
    fun setNote(ctx: Context, note: String, day: Long = LocalDate.now().toEpochDay()) {
        val old = days.value[day] ?: return
        if (old.note != note) set(ctx, day, old.copy(note = note))
    }

    /** Humeur moyenne des [n] derniers jours notés avant [day], et leur nombre. */
    fun moodBaseline(day: Long, n: Int = 28): Pair<Double, Int>? {
        val xs = days.value.filterKeys { it in (day - n) until day }.values.map { it.mood.toDouble() }
        return if (xs.isEmpty()) null else xs.average() to xs.size
    }

    /** Nombre de jours de chaque humeur (index 0 = « Très mal ») entre [from] et [to] inclus. */
    fun counts(from: LocalDate, to: LocalDate): IntArray {
        val out = IntArray(5)
        days.value.forEach { (d, e) -> if (d in from.toEpochDay()..to.toEpochDay()) out[e.mood.coerceIn(1, 5) - 1]++ }
        return out
    }
}
