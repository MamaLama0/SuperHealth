package app.superhealth.wellbeing

import app.superhealth.platform.*

import app.superhealth.Feature
import app.superhealth.SuperPrefs
import app.superhealth.has
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Rappels choisis dans Réglages > Rappels (tous coupés au départ) : pesée du matin, journal alimentaire du soir, journal
 * du moral et coucher conseillé (30 min avant). iOS ne réveille pas l'app à l'heure du rappel : les 7 prochains jours
 * sont programmés d'avance, et le rappel du jour est retiré dès qu'il devient inutile (pesée faite, humeur notée, repas
 * notés dans les 4 heures qui le précèdent), à chaque retour dans l'app.
 */
object Reminders {
    enum class Kind(val title: String, val route: String) {
        WEIGH("Pesée du matin", "weight"),
        LOG("Journal alimentaire", "log"),
        JOURNAL("Comment s'est passée la journée ?", "journal"),
        BEDTIME("Bientôt l'heure du coucher", "home"),
    }

    private const val PREFIX = "rem|"
    private const val DAYS = 7L
    private val lock = Mutex()

    /** Minute du jour du rappel ; null : coupé (ou sans objet, comme le coucher sans assez de nuits). */
    private suspend fun minuteOf(ctx: Context, k: Kind): Int? {
        val s = SuperPrefs.settings.value
        return when (k) {
            Kind.WEIGH -> s.remindWeigh.takeIf { it >= 0 && s.has(Feature.NUTRITION) }
            Kind.LOG -> s.remindLog.takeIf { it >= 0 && s.has(Feature.NUTRITION) }
            Kind.JOURNAL -> s.remindJournal.takeIf { it >= 0 && s.has(Feature.JOURNAL) }
            Kind.BEDTIME -> if (s.remindBedtime) runCatching { Bedtime.tonight(ctx) }.getOrNull()?.let { Math.floorMod(it.bedMinute - 30, 24 * 60) } else null
        }
    }

    /** Le rappel d'aujourd'hui (à [minute]) sert-il encore ? */
    private suspend fun useful(k: Kind, minute: Int): Boolean {
        val today = LocalDate.now().toEpochDay()
        val repo = app.superhealth.nutrition.NutriApp.repository
        val at = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + minute * 60_000L
        return when (k) {
            Kind.WEIGH -> repo.weighIns.all().none { it.epochDay == today }
            Kind.LOG -> repo.entries.forDay(today).first().none { at - it.createdAt < 4 * 3_600_000L }
            Kind.JOURNAL -> Journal.today() == null
            Kind.BEDTIME -> true
        }
    }

    /** Reprogramme tous les rappels (après un changement de réglage, une saisie, ou au retour dans l'app). */
    fun schedule(ctx: Context) {
        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                lock.withLock {
                    cancelNotificationsWithPrefix(PREFIX)
                    val zone = ZoneId.systemDefault()
                    val today = LocalDate.now()
                    val now = System.currentTimeMillis()
                    for (k in Kind.entries) {
                        val minute = minuteOf(ctx, k) ?: continue
                        val body = when (k) {
                            Kind.WEIGH -> tr("Montez sur la balance avant de manger.")
                            Kind.LOG -> tr("Notez vos repas de la journée.")
                            Kind.JOURNAL -> tr("Une note d'humeur, dix secondes.")
                            Kind.BEDTIME -> trf("Coucher conseillé à %1\$s.", Bedtime.clock(Math.floorMod(minute + 30, 24 * 60)))
                        }
                        for (d in 0..DAYS) {
                            val day = today.plusDays(d)
                            val at = day.atStartOfDay(zone).toInstant().toEpochMilli() + minute * 60_000L
                            if (at <= now || (d == 0L && !useful(k, minute))) continue
                            scheduleNotification("$PREFIX${k.name}|${day.toEpochDay()}", tr(k.title), body, at)
                        }
                    }
                }
            }
        }
    }

    /** Route ouverte au toucher d'une notification de rappel ([id] = « rem|KIND|jour »). */
    fun route(id: String): String? = id.split('|').getOrNull(1)?.let { n -> Kind.entries.firstOrNull { it.name == n }?.route }
}
