package app.superhealth.meditation

import app.superhealth.platform.*

import app.superhealth.modules.Modules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Plugin Méditation : minuteur, ambiances locales et courtes pauses avec des repères écrits.
 * Les minutes alimentent la carte, le coach et le classement.
 */
object Meditation {
    /** Phrase d'une séance : dite à [at] secondes du début. */
    @Serializable data class Line(val at: Int, val fr: String, val en: String = fr) {
        val text get() = if (app.superhealth.ui.Lang.english) en else fr
    }

    @Serializable data class Session(val id: String, val name: String, val nameEn: String = name, val summary: String = "", val summaryEn: String = summary,
                                     val minutes: Int, val lines: List<Line>, val theme: String = "") {
        val title get() = if (app.superhealth.ui.Lang.english) nameEn else name
        val about get() = if (app.superhealth.ui.Lang.english) summaryEn else summary
    }

    /** Suite de séances à faire dans l'ordre. */
    @Serializable data class Program(val id: String, val name: String, val nameEn: String = name, val sessions: List<String>) {
        val title get() = if (app.superhealth.ui.Lang.english) nameEn else name
    }

    /** [sos] : séance courte proposée sous « Besoin de calme ». */
    @Serializable data class Content(val sessions: List<Session> = emptyList(), val programs: List<Program> = emptyList(), val sos: String? = null)
    @Serializable data class Done(val day: Long, val at: Long, val minutes: Int, val kind: String)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var cache: Content? = null

    fun available() = Modules.isActive(Modules.MEDITATION)
    fun content(): Content = cache ?: runCatching {
        json.decodeFromString(Content.serializer(), Modules.file(Modules.MEDITATION, "meditation.json")!!.readText())
    }.getOrDefault(Content()).also { if (it.sessions.isNotEmpty()) cache = it }
    fun invalidate() { cache = null }
    fun audio(name: String): File? = Modules.file(Modules.MEDITATION, "audio/$name.mp3")
    /** Ambiance en boucle (Ogg : bouclage sans blanc). */
    fun ambience(name: String): File? {
        if (!available() || name !in setOf("rain", "ocean", "brown")) return null
        Modules.file(Modules.MEDITATION, "audio/$name.m4a")?.let { return it }
        // AVAudioPlayer ne lit pas Ogg/Vorbis : mêmes ambiances synthétiques, converties en AAC pour iOS.
        val file=File(appContext.cacheDir,"meditation/$name.m4a")
        if (!file.isFile) readAsset("meditation/audio/$name.m4a")?.let { file.writeBytes(it) }
        return file.takeIf { it.isFile }
    }
    /** Séance à jouer (guidée, libre ou du coach). */
    data class Play(val title: String, val minutes: Int, val lines: List<Line> = emptyList(), val intervalMin: Int = 0, val kind: String = "free", val session: String? = null)
    val current = MutableStateFlow<Play?>(null)

    fun play(s: Session, kind: String = "guided") { current.value = Play(s.title, s.minutes, s.lines, kind = kind, session = s.id) }

    /** Pauses sobres, sans voix ; le catalogue garde les identifiants pour l'historique. */
    fun simpleSessions(): List<Session> = simple.sessions
    private val simple: Content by lazy { json.decodeFromString(Content.serializer(), """{"sessions":[{"id":"stress3","name":"Une pause de trois minutes","nameEn":"A three-minute pause","summary":"Quelques repères, puis du silence.","summaryEn":"A few prompts, then quiet.","minutes":3,"theme":"calm","lines":[{"at":5,"fr":"Prenez une position confortable. Laissez les épaules se relâcher.","en":"Find a comfortable position. Let your shoulders soften."},{"at":45,"fr":"Remarquez les points de contact avec le sol ou le siège. Rien à réussir.","en":"Notice where your body touches the floor or seat. There is nothing to achieve."},{"at":120,"fr":"Si votre attention part ailleurs, revenez simplement à une sensation présente.","en":"If your attention wanders, gently return to one sensation in the present."}]},{"id":"souffle5","name":"Revenir au présent","nameEn":"Back to the present","summary":"Cinq minutes avec trois repères discrets.","summaryEn":"Five minutes with three gentle prompts.","minutes":5,"theme":"focus","lines":[{"at":5,"fr":"Installez-vous comme vous êtes. Vous pouvez garder les yeux ouverts.","en":"Settle in as you are. You can keep your eyes open."},{"at":60,"fr":"Observez votre respiration naturelle, sans chercher à la modifier.","en":"Notice your natural breathing without trying to change it."},{"at":180,"fr":"Un bruit, une pensée, une sensation : remarquez-les, puis revenez tranquillement au moment présent.","en":"A sound, a thought, a sensation: notice it, then gently return to the present moment."}]},{"id":"sommeil10","name":"Ralentir avant la nuit","nameEn":"Wind down for the night","summary":"Dix minutes calmes, sans voix ni rythme imposé.","summaryEn":"Ten quiet minutes, with no voice or imposed rhythm.","minutes":10,"theme":"sleep","lines":[{"at":5,"fr":"Choisissez une position confortable et une lumière qui vous convient.","en":"Choose a comfortable position and lighting that suits you."},{"at":120,"fr":"Laissez le visage et les mains se détendre, à votre rythme.","en":"Let your face and hands soften at your own pace."},{"at":300,"fr":"Vous n'avez rien à terminer ici. Laissez simplement un peu de place au calme.","en":"There is nothing to finish here. Simply leave a little room for quiet."}]}],"programs":[],"sos":"stress3"}""") }
    fun daily(today: Long = LocalDate.now().toEpochDay()): Pair<Session, String>? {
        val all = simpleSessions()
        return all[Math.floorMod(today, all.size.toLong()).toInt()] to "guided"
    }

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
    fun session(id: String) = simpleSessions().firstOrNull { it.id == id } ?: content().sessions.firstOrNull { it.id == id }

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
