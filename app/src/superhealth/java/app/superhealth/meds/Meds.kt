package app.superhealth.meds

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.superhealth.modules.Modules
import app.superhealth.ui.Lang
import app.superhealth.ui.tr
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
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

    @Volatile private var content: Content? = null
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
        if (on) cancelNotification(ctx, key)
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

    /** Programme la prochaine prise à venir (une seule alarme à la fois, reprogrammée à chaque déclenchement). */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, 41, Intent(ctx, MedsReceiver::class.java).setAction(ACTION_DUE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
        if (!available() || meds.value.none { it.active }) return
        val now = LocalDateTime.now()
        val next = (0..7L).asSequence().flatMap { d ->
            val day = now.toLocalDate().plusDays(d)
            doses(day).filter { !it.taken }.map { day.atStartOfDay().plusMinutes(it.minute.toLong()) }.asSequence()
        }.firstOrNull { it.isAfter(now) } ?: return
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // exacte si le système l'autorise (rappel à la minute), sinon au plus près en économie d'énergie
        if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** Notifie les prises dont l'heure vient d'arriver (et pas encore cochées). */
    fun notifyDue(ctx: Context) {
        val now = LocalDateTime.now()
        val minute = now.hour * 60 + now.minute
        doses(now.toLocalDate()).filter { !it.taken && it.minute in (minute - 10)..minute }.forEach { notify(ctx, it) }
        schedule(ctx)
    }

    fun notify(ctx: Context, d: Dose) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Rappel médication"), NotificationManager.IMPORTANCE_HIGH))
        fun action(act: String, code: Int) = PendingIntent.getBroadcast(ctx, code, Intent(ctx, MedsReceiver::class.java).setAction(act)
            .putExtra("key", d.key).putExtra("day", LocalDate.now().toEpochDay()), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(ctx, 42, Intent(ctx, app.superhealth.MainActivity::class.java)
            .putExtra(app.superhealth.MainActivity.EXTRA_ROUTE, "meds").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val id = d.key.hashCode()
        nm.notify(id, NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(nodomain.freeyourgadget.gadgetbridge.R.drawable.ic_stat_meds)
            .setContentTitle(d.med.name).setContentText(d.med.dose.ifBlank { tr("C'est l'heure") }).setContentIntent(open).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, tr("Pris"), action(ACTION_TAKEN, id))
            .addAction(0, tr("Plus tard"), action(ACTION_SNOOZE, id + 1)).build())
    }

    private fun cancelNotification(ctx: Context, key: String) = ctx.getSystemService(NotificationManager::class.java).cancel(key.hashCode())

    /** « Plus tard » : la même prise renotifiée dans quelques minutes (durée du plugin). */
    fun snooze(ctx: Context, key: String) {
        cancelNotification(ctx, key)
        val at = System.currentTimeMillis() + content().snoozeMinutes * 60_000L
        val pi = PendingIntent.getBroadcast(ctx, key.hashCode() + 2, Intent(ctx, MedsReceiver::class.java).setAction(ACTION_SNOOZED).putExtra("key", key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    const val ACTION_DUE = "app.superhealth.meds.DUE"
    const val ACTION_TAKEN = "app.superhealth.meds.TAKEN"
    const val ACTION_SNOOZE = "app.superhealth.meds.SNOOZE"
    const val ACTION_SNOOZED = "app.superhealth.meds.SNOOZED"
}

/** Alarmes et boutons des notifications du rappel médication ; reprogrammation au démarrage du téléphone. */
class MedsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        when (intent.action) {
            Meds.ACTION_DUE -> Meds.notifyDue(app)
            Meds.ACTION_TAKEN -> intent.getStringExtra("key")?.let { Meds.setTaken(app, intent.getLongExtra("day", LocalDate.now().toEpochDay()), it, true) }
            Meds.ACTION_SNOOZE -> intent.getStringExtra("key")?.let { Meds.snooze(app, it) }
            Meds.ACTION_SNOOZED -> intent.getStringExtra("key")?.let { k ->
                Meds.doses().firstOrNull { it.key == k && !it.taken }?.let { Meds.notify(app, it) }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIMEZONE_CHANGED -> Meds.schedule(app)
        }
    }
}
