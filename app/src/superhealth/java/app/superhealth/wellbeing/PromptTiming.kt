package app.superhealth.wellbeing

import app.superhealth.SuperSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Random

/** Règles d'apparition sur l'accueil, toujours actives : objectif de pas, après une activité, puis le soir
 * (heure habituelle de saisie après sept saisies, sinon tirage). Les accès manuels restent disponibles à toute heure. */
object PromptTiming {
    fun weighMinute(settings: SuperSettings): Int =
        settings.weighPromptMinute.takeIf { it in 0..1439 }
            ?: settings.remindWeigh.takeIf { it in 0..1439 } ?: 7 * 60

    fun weighDue(settings: SuperSettings, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val time = Instant.ofEpochMilli(now).atZone(zone)
        return time.hour * 60 + time.minute >= weighMinute(settings)
    }

    /** Le moral résume la journée : jamais avant midi, quel que soit l'événement (une matinée se juge mal à 9 h). */
    const val EARLIEST = 12 * 60

    /** Activité qui compte : 15 min au moins, 20 pour une marche (les marches détectées seules ne sont pas des séances). */
    fun meaningful(walk: Boolean, start: Long, end: Long): Boolean = end - start >= (if (walk) 20 else 15) * 60_000L

    /** Événements de la journée qui peuvent appeler le moral : chacun a sa fenêtre, toutes après [EARLIEST]. */
    data class Signals(
        /** Heure où le total de pas a franchi l'objectif. */
        val stepGoalAt: Long? = null,
        /** Fins des activités retenues par [meaningful]. */
        val activityEnds: List<Long> = emptyList(),
        /** Fins des méditations et étirements : moment calme, propice au retour sur soi. */
        val calmEnds: List<Long> = emptyList(),
        /** Premier aliment du dîner noté après 18 h : on est déjà dans l'app, en fin de journée. */
        val dinnerAt: Long? = null,
    )

    enum class Trigger { SHOWN, STEPS, ACTIVITY, CALM, DINNER, EVENING }

    /** Tirage stable pour la journée et l'installation, y compris après fermeture de l'app. */
    fun eveningMinute(day: Long, seed: Long): Int = 17 * 60 + Random(seed xor day).nextInt(121)

    fun activityDelayMinutes(end: Long, seed: Long): Int = 30 + Random(seed xor end).nextInt(90)

    /** Quelques dizaines de minutes après l'objectif de pas : le temps de rentrer et de s'asseoir. */
    fun stepsDelayMinutes(at: Long, seed: Long): Int = 15 + Random(seed xor at xor 0x5745).nextInt(31)

    fun calmDelayMinutes(end: Long, seed: Long): Int = 5 + Random(seed xor end xor 0xCA1F).nextInt(16)

    /**
     * Heure de franchissement de [goal] d'après les pas heure par heure ([hourly] : début d'heure, pas de l'heure),
     * interpolée dans l'heure où le cumul passe l'objectif ; null s'il n'est pas atteint.
     */
    fun stepGoalCrossing(hourly: List<Pair<Long, Double>>, goal: Int): Long? {
        if (goal <= 0) return null
        var total = 0.0
        hourly.sortedBy { it.first }.forEach { (start, v) ->
            if (v > 0 && total + v >= goal) return start + ((goal - total) / v * 3_600_000L).toLong()
            total += v
        }
        return null
    }

    fun moodDue(settings: SuperSettings, now: Long, signals: Signals = Signals(), zone: ZoneId = ZoneId.systemDefault(),
                history: Map<Long, Journal.Entry> = emptyMap()): Boolean = moodTrigger(settings, now, signals, zone, history) != null

    /** Raison pour laquelle le moral est proposé maintenant, ou null. */
    fun moodTrigger(settings: SuperSettings, now: Long, signals: Signals = Signals(), zone: ZoneId = ZoneId.systemDefault(),
                    history: Map<Long, Journal.Entry> = emptyMap()): Trigger? {
        val time = Instant.ofEpochMilli(now).atZone(zone)
        val day = time.toLocalDate().toEpochDay()
        val minute = time.hour * 60 + time.minute
        if (settings.moodPromptShownDay == day) return Trigger.SHOWN
        if (minute < EARLIEST) return null
        fun today(at: Long) = Instant.ofEpochMilli(at).atZone(zone).toLocalDate() == LocalDate.ofEpochDay(day)
        fun open(at: Long, delayMin: Int, spanMin: Int) = today(at) && now - at in delayMin * 60_000L..spanMin * 60_000L
        // Trois « pas aujourd'hui » de suite : le moral attend le soir, sans se glisser après un événement.
        val declining = (1L..3L).all { (day - it) in settings.moodSkips }
        if (!declining) {
            signals.calmEnds.firstOrNull { open(it, calmDelayMinutes(it, settings.promptSeed), 120) }?.let { return Trigger.CALM }
            signals.activityEnds.firstOrNull { open(it, activityDelayMinutes(it, settings.promptSeed), 120) }?.let { return Trigger.ACTIVITY }
            signals.stepGoalAt?.takeIf { open(it, stepsDelayMinutes(it, settings.promptSeed), 180) }?.let { return Trigger.STEPS }
            signals.dinnerAt?.takeIf { open(it, 0, 180) && Instant.ofEpochMilli(it).atZone(zone).hour >= 18 }?.let { return Trigger.DINNER }
        }
        val examples = history.mapNotNull { (d, entry) ->
            if (entry.at <= 0) null else Instant.ofEpochMilli(entry.at).atZone(zone).takeIf { it.toLocalDate().toEpochDay() == d }
                ?.let { Attention.Example(d, it.hour * 60 + it.minute) }
        }
        val evening = Attention.preferredMinute(examples, 17 * 60..19 * 60, day) ?: eveningMinute(day, settings.promptSeed)
        return if (minute >= evening) Trigger.EVENING else null
    }
}
