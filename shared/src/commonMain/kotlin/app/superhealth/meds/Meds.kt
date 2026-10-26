package app.superhealth.meds

import app.superhealth.platform.*

import app.superhealth.platform.Context
import app.superhealth.modules.Modules
import app.superhealth.ui.Lang
import app.superhealth.ui.tr
import app.superhealth.platform.File
import app.superhealth.platform.LocalDate
import app.superhealth.platform.LocalDateTime
import app.superhealth.platform.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Plugin Rappel médication : médicaments et compléments à heure fixe, une notification à l'heure (« Pris », « Plus tard »),
 * et les prises du jour cochées dans l'app. Le plugin n'apporte que les heures et formes proposées (meds.json) ;
 * la liste et l'historique des prises restent sur le téléphone (files/meds).
 */
object Meds {
    @Serializable data class Txt(val fr: String, val en: String? = null) { val text: String get() = if (Lang.english) en ?: fr else fr }
    @Serializable data class TimePreset(val name: Txt, val minute: Int)
    @Serializable data class Content(val version: String = "1", val times: List<TimePreset> = emptyList(), val forms: List<Txt> = emptyList(),
                                     val snoozeMinutes: Int = 15)

    /** Un médicament : prises aux minutes [times] (0–1439), les jours [days] (1 lundi … 7 dimanche). */
    @Serializable data class Med(
        val id: String, val name: String, val dose: String = "", val times: List<Int> = listOf(480),
        val days: Set<Int> = (1..7).toSet(), val active: Boolean = true,
    )

    /** Une prise du jour. */
    data class Dose(val med: Med, val minute: Int, val taken: Boolean) { val key get() = "${med.id}@$minute" }

    @Serializable private data class Store(val meds: List<Med> = emptyList(), val taken: Map<Long, Set<String>> = emptyMap())

    private const val CHANNEL = "meds"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var dir: File

    val meds = MutableStateFlow<List<Med>>(emptyList())
    /** Prises faites, par jour (les 30 derniers jours gardés). */
    val taken = MutableStateFlow<Map<Long, Set<String>>>(emptyMap())

    @kotlin.concurrent.Volatile private var content: Content? = null
    fun content(): Content = content ?: runCatching {
        json.decodeFromString(Content.serializer(), Modules.file(Modules.MEDS, "meds.json")!!.readText())
    }.getOrNull()?.also { content = it } ?: Content()

    fun invalidate() { content = null }
    fun available() = Modules.isActive(Modules.MEDS)

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "meds").apply { mkdirs() }
        val s = runCatching { json.decodeFromString(Store.serializer(), File(dir, "meds.json").readText()) }.getOrDefault(Store())
        meds.value = s.meds; taken.value = s.taken
        schedule(ctx)
    }

    private fun save(ctx: Context) {
        val today = LocalDate.now().toEpochDay()
        taken.value = taken.value.filterKeys { it > today - 30 }
        runCatching { File(dir, "meds.json").writeText(json.encodeToString(Store.serializer(), Store(meds.value, taken.value))) }
        schedule(ctx)
    }

    fun upsert(ctx: Context, m: Med) { meds.value = meds.value.filter { it.id != m.id } + m; save(ctx) }
    fun remove(ctx: Context, id: String) { meds.value = meds.value.filter { it.id != id }; save(ctx) }

    fun setTaken(ctx: Context, day: Long, key: String, on: Boolean) {
        val cur = taken.value[day].orEmpty()
        taken.value = taken.value + (day to if (on) cur + key else cur - key)
        save(ctx)
        if (on) cancelNotification("med|$key|$day")
    }

    /** Prises du jour [day], dans l'ordre des heures. */
    fun doses(day: LocalDate = LocalDate.now()): List<Dose> {
        val done = taken.value[day.toEpochDay()].orEmpty()
        return meds.value.filter { it.active && day.dayOfWeek.value in it.days }
            .flatMap { m -> m.times.map { t -> Dose(m, t, "${m.id}@$t" in done) } }.sortedBy { it.minute }
    }

    /** Prises dont l'heure est passée et pas encore cochées (pour « À faire »). */
    fun late(now: LocalDateTime = LocalDateTime.now()): List<Dose> =
        if (!available()) emptyList() else doses(now.toLocalDate()).filter { !it.taken && it.minute <= now.hour * 60 + now.minute }

    // ---- Rappels ---------------------------------------------------------------------------------------------

    /**
     * Programme les prises à venir (7 jours, 48 au plus : iOS garde 64 notifications en attente) ; chaque notification
     * porte les actions « Pris » et « Plus tard » (catégorie « meds »).
     */
    fun schedule(ctx: Context) {
        cancelNotificationsWithPrefix("med|")
        if (!available() || meds.value.none { it.active }) return
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        (0..7L).asSequence().flatMap { d ->
            val day = now.toLocalDate().plusDays(d)
            doses(day).filter { !it.taken }.map { day to it }.asSequence()
        }.map { (day, dose) -> Triple(day, dose, day.atStartOfDay(zone).toInstant().toEpochMilli() + dose.minute * 60_000L) }
            .filter { it.third > System.currentTimeMillis() }
            .take(48)
            .forEach { (day, dose, at) -> notifyAt(dose, day.toEpochDay(), at) }
    }

    private fun notifyAt(d: Dose, day: Long, at: Long) =
        scheduleNotification("med|${d.key}|$day", d.med.name, d.med.dose.ifBlank { tr("C'est l'heure") }, at, sound = true, category = "meds")

    /** Notifie les prises dont l'heure vient d'arriver (et pas encore cochées). */
    fun notifyDue(ctx: Context) {
        val now = LocalDateTime.now()
        val minute = now.hour * 60 + now.minute
        doses(now.toLocalDate()).filter { !it.taken && it.minute in (minute - 10)..minute }.forEach { notify(ctx, it) }
        schedule(ctx)
    }

    fun notify(ctx: Context, d: Dose) = notifyAt(d, LocalDate.now().toEpochDay(), 0L)

    /** « Plus tard » : la même prise renotifiée dans quelques minutes (durée du plugin). */
    fun snooze(ctx: Context, key: String) {
        val d = doses().firstOrNull { it.key == key && !it.taken } ?: return
        notifyAt(d, LocalDate.now().toEpochDay(), System.currentTimeMillis() + content().snoozeMinutes * 60_000L)
    }

    /** Bouton d'une notification (« Pris » / « Plus tard ») : identifiant « med|clé|jour ». */
    fun onAction(ctx: Context, id: String, action: String) {
        val parts = id.split('|')
        if (parts.size != 3 || parts[0] != "med") return
        val day = parts[2].toLongOrNull() ?: return
        when (action) {
            ACTION_TAKEN -> setTaken(ctx, day, parts[1], true)
            ACTION_SNOOZE -> snooze(ctx, parts[1])
        }
    }

    const val ACTION_TAKEN = "taken"
    const val ACTION_SNOOZE = "snooze"
}
