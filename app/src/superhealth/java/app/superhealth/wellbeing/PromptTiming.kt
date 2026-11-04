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

    /** Tirage stable pour la journée et l'installation, y compris après fermeture de l'app. */
    fun eveningMinute(day: Long, seed: Long): Int = 17 * 60 + Random(seed xor day).nextInt(121)

    fun activityDelayMinutes(end: Long, seed: Long): Int = 30 + Random(seed xor end).nextInt(90)

    fun moodDue(settings: SuperSettings, now: Long, steps: Double?, stepGoal: Int,
                activityEnds: List<Long>, zone: ZoneId = ZoneId.systemDefault(), history: Map<Long, Journal.Entry> = emptyMap()): Boolean {
        val time = Instant.ofEpochMilli(now).atZone(zone)
        val day = time.toLocalDate().toEpochDay()
        if (settings.moodPromptShownDay == day) return true
        if (stepGoal > 0 && steps != null && steps >= stepGoal) return true
        if (activityEnds.any { end ->
                val elapsed = now - end
                Instant.ofEpochMilli(end).atZone(zone).toLocalDate() == LocalDate.ofEpochDay(day) &&
                    elapsed in activityDelayMinutes(end, settings.promptSeed) * 60_000L..120 * 60_000L
            }) return true
        val historyMinutes = history.asSequence()
            .filter { (d, entry) -> d in day - 28 until day && entry.at > 0 }
            .mapNotNull { (d, entry) -> Instant.ofEpochMilli(entry.at).atZone(zone).takeIf { it.toLocalDate().toEpochDay() == d }
                ?.let { it.hour * 60 + it.minute } }.filter { it in 17 * 60..19 * 60 }.sorted().toList()
        val minute = if (historyMinutes.size >= 7) historyMinutes[historyMinutes.size / 2] else eveningMinute(day, settings.promptSeed)
        return time.hour * 60 + time.minute >= minute
    }
}
