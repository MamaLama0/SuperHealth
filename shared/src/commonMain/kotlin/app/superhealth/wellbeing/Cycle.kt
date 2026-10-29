package app.superhealth.wellbeing

import app.superhealth.platform.*

import app.superhealth.platform.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.superhealth.platform.File
import app.superhealth.platform.LocalDate

/**
 * Suivi du cycle : jours de règles notés dans l'app ou lus dans Health Connect / Apple Santé, durée des cycles et
 * estimation des phases. Méthode calendaire : ovulation ≈ 14 jours avant les règles suivantes (phase lutéale la plus
 * stable, Lenton 1984), fenêtre fertile = les 5 jours avant et le jour de l'ovulation (Wilcox 1995). Une estimation, pas
 * une contraception. Les données restent sur le téléphone et ne sont jamais envoyées à l'IA.
 */
object Cycle {
    @Serializable private data class Store(val on: Set<Long> = emptySet(), val off: Set<Long> = emptySet(), val synced: Set<Long> = emptySet())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var file: File? = null
    private var store = Store()

    /** Jours de règles retenus : notés à la main ou importés, sauf ceux retirés à la main. */
    val days = MutableStateFlow<Set<Long>>(emptySet())

    fun init(ctx: Context) {
        val f = File(File(ctx.filesDir, "wellbeing").apply { mkdirs() }, "cycle.json").also { file = it }
        store = runCatching { json.decodeFromString(Store.serializer(), f.readText()) }.getOrDefault(Store())
        publish()
    }

    private fun publish() { days.value = (store.on + store.synced) - store.off }

    private fun save() {
        publish()
        // la Charge compare à la même phase du cycle : à recalculer
        app.superhealth.health.data.Scores.invalidate()
        runCatching { file?.writeText(json.encodeToString(Store.serializer(), store)) }
    }

    /** Coche ou décoche un jour de règles. */
    fun toggle(day: Long) {
        store = if (day in days.value) store.copy(on = store.on - day, off = store.off + day)
        else store.copy(on = store.on + day, off = store.off - day)
        save()
    }

    /** Jours de règles lus dans Health Connect / Apple Santé depuis [from] (remplacent l'import précédent sur cette période). */
    fun mergeSynced(from: Long, daysIn: Set<Long>) {
        val merged = store.synced.filter { it < from }.toSet() + daysIn
        if (merged == store.synced) return
        store = store.copy(synced = merged)
        save()
    }

    enum class Phase(val label: String) {
        MENSTRUAL("Règles"), FOLLICULAR("Phase folliculaire"), FERTILE("Fenêtre fertile estimée"), LUTEAL("Phase lutéale")
    }

    data class Info(
        val start: Long,
        /** Jour du cycle (1 = premier jour des règles). */
        val dayOfCycle: Int,
        /** Médiane des derniers cycles complets ; null sous [MIN_CYCLES] cycles. */
        val length: Int?,
        val periodLength: Int,
        val nextStart: Long?,
        val ovulation: Long?,
        val phase: Phase?,
        val cycles: List<Pair<Long, Int>>,
    )

    /** Cycles complets nécessaires avant toute prédiction. */
    const val MIN_CYCLES = 2
    /** Sans règles depuis 10 jours, un nouveau jour de règles ouvre un nouveau cycle (les petits saignements s'y rattachent). */
    private const val GAP = 10

    /** Débuts de règles, du plus ancien au plus récent. */
    fun starts(d: Set<Long> = days.value): List<Long> = d.sorted().filter { day -> (1..GAP).none { (day - it) in d } }

    /**
     * Le jour [day] est-il en phase lutéale (après l'ovulation, estimée 14 jours avant les règles suivantes : réelles si
     * le cycle est terminé, sinon prévues avec [length]) ? Null hors d'un cycle connu.
     */
    fun luteal(day: Long, d: Set<Long> = days.value, length: Int? = info(d = d)?.length): Boolean? {
        val st = starts(d)
        val s = st.lastOrNull { it <= day } ?: return null
        val next = st.firstOrNull { it > s } ?: (s + (length ?: return null))
        if (next - s !in 15..60) return null
        return day > next - 14
    }

    fun info(today: Long = LocalDate.now().toEpochDay(), d: Set<Long> = days.value): Info? {
        val st = starts(d).filter { it <= today }
        val start = st.lastOrNull() ?: return null
        val cycles = st.zipWithNext { a, b -> a to (b - a).toInt() }.filter { it.second in 15..60 }
        val recent = cycles.takeLast(6).map { it.second }.sorted()
        val n = recent.size
        val length = if (n < MIN_CYCLES) null else if (n % 2 == 1) recent[n / 2] else (recent[n / 2 - 1] + recent[n / 2] + 1) / 2
        val periodLength = generateSequence(start) { it + 1 }.takeWhile { it in d }.count()
        val dayOfCycle = (today - start).toInt() + 1
        val next = length?.let { start + it }
        val ov = next?.let { it - 14 }
        val phase = when {
            today in d && today - start < periodLength -> Phase.MENSTRUAL
            ov == null -> null
            today in (ov - 5)..ov -> Phase.FERTILE
            today < ov - 5 -> Phase.FOLLICULAR
            else -> Phase.LUTEAL
        }
        return Info(start, dayOfCycle, length, periodLength, next, ov, phase, cycles)
    }
}
