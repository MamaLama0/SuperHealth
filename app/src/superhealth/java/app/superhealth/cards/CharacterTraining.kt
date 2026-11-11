package app.superhealth.cards

import app.superhealth.training.domain.Analytics
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Only normalized game scores are shared. Raw training evidence stays on this device. */
@Serializable
data class CharacterTraining(
    val volumeScore: Double? = null,
    val consistencyScore: Double? = null,
    val coveredDays: Int = 0,
    val provisional: Boolean = true,
    @Transient val evidence: GymEvidence? = null,
)

data class GymEvidence(val weeklyKg: Double, val weeklySets: Double, val bodyweightKg: Double, val bodyweightKnown: Boolean)

/** Game calibration, not training recommendations. Annual averages include weeks without logged sessions. */
internal object CharacterTrainingEngine {
    private const val DAY = 86_400_000L
    const val VOLUME_PER_BODYWEIGHT = 250.0
    const val SETS_PER_WEEK = 50.0

    fun compute(input: AthleteStatsEngine.Input): CharacterTraining {
        val bodyweight = input.bodyweight.takeIf { it.isFinite() && it > 0 } ?: return CharacterTraining()
        val past = input.history.filter { it.startTime in 1..input.now }.distinctBy { it.id }
        val validWorkouts = mutableListOf<Pair<Long, Pair<Double, Int>>>()
        for (workout in past) {
            var tonnage = 0.0
            var count = 0
            for (entry in workout.exercises) {
                for (set in Analytics.workingSets(entry)) {
                    val reps = set.reps?.takeIf { it in 1..100 } ?: continue
                    val external = set.weight ?: 0.0
                    if (!external.isFinite() || external < 0) continue
                    count++
                    tonnage += external * reps

                }
            }
            if (count > 0) validWorkouts += workout.startTime to (tonnage to count)
        }
        val first = validWorkouts.minOfOrNull { it.first }
        val covered = first?.let { ((input.now - it) / DAY + 1).toInt().coerceIn(28, 365) } ?: 0
        val year = validWorkouts.filter { it.first >= input.now - 365 * DAY }
        val weeks = covered / 7.0
        val kg = if (weeks > 0) year.sumOf { it.second.first } / weeks else 0.0
        val sets = if (weeks > 0) year.sumOf { it.second.second } / weeks else 0.0
        val volume = if (first == null) null else .75 * (kg / (bodyweight * VOLUME_PER_BODYWEIGHT)).coerceIn(0.0, 1.0) +
            .25 * (sets / SETS_PER_WEEK).coerceIn(0.0, 1.0)
        // One training day cannot count as several sessions by splitting it into several logs.
        val activeDays = year.map { java.time.Instant.ofEpochMilli(it.first).atZone(input.zone).toLocalDate().toEpochDay() }.distinct().size
        val consistency = if (first == null) null else (activeDays / (weeks * input.weeklyTarget.coerceIn(1, 7))).coerceIn(0.0, 1.0)
        return CharacterTraining(volume, consistency, covered, covered < 365 || !input.bodyweightKnown,
            GymEvidence(kg, sets, bodyweight, input.bodyweightKnown))
    }
}
