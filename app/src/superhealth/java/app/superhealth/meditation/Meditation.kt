package app.superhealth.meditation

import android.content.Context
import app.superhealth.modules.Modules
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Plugin Méditation : minuteur libre (gongs et bruit blanc du plugin), séances guidées (texte affiché et lu par la voix du téléphone)
 * et séances écrites par le coach. Les minutes alimentent la carte, le coach et le classement.
 */
object Meditation {
    /** Phrase d'une séance : dite à [at] secondes du début. */
    @Serializable data class Line(val at: Int, val fr: String, val en: String = fr) {
        val text get() = if (app.superhealth.ui.Lang.english) en else fr
    }

    @Serializable data class Session(val id: String, val name: String, val nameEn: String = name, val summary: String = "", val summaryEn: String = summary,
                                     val minutes: Int, val lines: List<Line>) {
        val title get() = if (app.superhealth.ui.Lang.english) nameEn else name
        val about get() = if (app.superhealth.ui.Lang.english) summaryEn else summary
    }

    /** Suite de séances à faire dans l'ordre. */
    @Serializable data class Program(val id: String, val name: String, val nameEn: String = name, val sessions: List<String>) {
        val title get() = if (app.superhealth.ui.Lang.english) nameEn else name
    }

    @Serializable data class Content(val sessions: List<Session> = emptyList(), val programs: List<Program> = emptyList())
    @Serializable data class Done(val day: Long, val at: Long, val minutes: Int, val kind: String)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var cache: Content? = null

    fun available() = Modules.isActive(Modules.MEDITATION)
    fun content(): Content = cache ?: runCatching {
        json.decodeFromString(Content.serializer(), Modules.file(Modules.MEDITATION, "meditation.json")!!.readText())
    }.getOrDefault(Content()).also { if (it.sessions.isNotEmpty()) cache = it }
    fun invalidate() { cache = null }
    fun audio(name: String): File? = Modules.file(Modules.MEDITATION, "audio/$name.mp3")

    /** Séance à jouer (guidée, libre ou du coach). */
    data class Play(val title: String, val minutes: Int, val lines: List<Line> = emptyList(), val intervalMin: Int = 0, val noise: Boolean = false, val kind: String = "free")
    val current = MutableStateFlow<Play?>(null)

    /** Séance écrite par le coach : « mm:ss | phrase » par ligne, tout le reste est ignoré. */
    fun parseScript(raw: String): List<Line> = raw.lines().mapNotNull { l ->
        val m = Regex("""^\W*(\d{1,2}):(\d{2})\s*\|\s*(.+)$""").find(l.trim()) ?: return@mapNotNull null
        Line(m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt(), m.groupValues[3].trim().take(400))
    }.sortedBy { it.at }

    // ---- Journal -------------------------------------------------------------------------------------

    val log = MutableStateFlow<List<Done>>(emptyList())
    private fun file(ctx: Context) = File(ctx.filesDir, "meditation_log.json")

    fun load(ctx: Context) {
        log.value = runCatching { json.decodeFromString(ListSerializer(Done.serializer()), file(ctx).readText()) }.getOrDefault(emptyList())
    }

    fun record(ctx: Context, minutes: Int, kind: String) {
        if (minutes < 1) return
        log.value = (log.value + Done(LocalDate.now().toEpochDay(), System.currentTimeMillis(), minutes, kind)).takeLast(1000)
        runCatching { file(ctx).writeText(json.encodeToString(ListSerializer(Done.serializer()), log.value)) }
    }

    /** Séances déjà faites d'un programme : [kind] = « program:id:n ». */
    fun programDone(p: Program) = log.value.mapNotNull { d -> d.kind.takeIf { it.startsWith("program:${p.id}:") }?.substringAfterLast(':')?.toIntOrNull() }.toSet().size.coerceAtMost(p.sessions.size)
    fun session(id: String) = content().sessions.firstOrNull { it.id == id }

    /** Plus longue série de jours. */
    fun bestStreak(): Int {
        val days = log.value.map { it.day }.distinct().sorted()
        var best = 0; var run = 0; var prev = Long.MIN_VALUE
        for (d in days) { run = if (d == prev + 1) run + 1 else 1; if (run > best) best = run; prev = d }
        return best
    }

    fun minutes(from: Long, to: Long = Long.MAX_VALUE) = log.value.filter { it.day in from..to }.sumOf { it.minutes }

    /** Jours d'affilée avec une séance, aujourd'hui ou hier compris. */
    fun streak(today: Long = LocalDate.now().toEpochDay()): Int {
        val days = log.value.map { it.day }.toSet()
        var d = if (today in days) today else today - 1
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }
}
