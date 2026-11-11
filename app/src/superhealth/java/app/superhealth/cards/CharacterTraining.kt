package app.superhealth.cards

import app.superhealth.training.domain.Analytics
import app.superhealth.training.domain.Strength
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Only normalized game scores are shared. Raw training evidence stays on this device. */
@Serializable
data class CharacterTraining(
    val prScore: Double? = null,
    val volumeScore: Double? = null,
    val consistencyScore: Double? = null,
    val coveredDays: Int = 0,
    val provisional: Boolean = true,
    @Transient val evidence: GymEvidence? = null,
)

data class GymRecord(val name: String, val loadKg: Double, val reps: Int, val estimatedMaxKg: Double, val score: Double)
data class GymEvidence(val records: List<GymRecord>, val weeklyKg: Double, val weeklySets: Double, val bodyweightKg: Double, val bodyweightKnown: Boolean)

/** Game calibration, not training recommendations. Annual averages include weeks without logged sessions. */
internal object CharacterTrainingEngine {
    private const val DAY = 86_400_000L
    const val VOLUME_PER_BODYWEIGHT = 250.0
    const val SETS_PER_WEEK = 50.0

    private const val RECORD_STANDARD = 1.20

    fun compute(input: AthleteStatsEngine.Input): CharacterTraining {
        val bodyweight = input.bodyweight.takeIf { it.isFinite() && it > 0 } ?: return CharacterTraining()
        val past = input.history.filter { it.startTime in 1..input.now }.distinctBy { it.id }
        val records = mutableMapOf<Standards.Lift, GymRecord>()
        val validWorkouts = mutableListOf<Pair<Long, Pair<Double, Int>>>()
        for (workout in past) {
            var tonnage = 0.0
            var count = 0
            for (entry in workout.exercises) {
                val exercise = input.db.exercise(entry.exerciseId)
                val match = exercise?.let(Standards::match)
                for (set in Analytics.workingSets(entry)) {
                    val reps = set.reps?.takeIf { it in 1..100 } ?: continue
                    val external = set.weight ?: 0.0
                    if (!external.isFinite() || external < 0) continue
                    count++
                    tonnage += external * reps
                    if (exercise == null || match == null || reps > 12) continue
                    val load = if (match.lift.bodyweightLoad) bodyweight * (exercise.bodyweight ?: 1.0) + external else external
                    if (load <= 0) continue
                    // Bounded RIR prevents a generous effort estimate from outweighing the lifted load.
                    val maximum = Strength.e1rm(load, reps, set.rir?.coerceIn(0, 3), exercise.upperBody, set.partialReps.coerceAtLeast(0))
                    if (!maximum.isFinite()) continue
                    val points = (Standards.points(maximum / match.coef / bodyweight / RECORD_STANDARD,
                        Standards.thresholds(match.lift, input.sex, bodyweight)) * match.confidence).coerceIn(0.0, 100.0)
                    if (points > (records[match.lift]?.score ?: -1.0)) records[match.lift] = GymRecord(exercise.name, external, reps, maximum, points)
                }
            }
            if (count > 0) validWorkouts += workout.startTime to (tonnage to count)
        }
        val top = records.values.sortedByDescending { it.score }.take(3)
        val pr = top.firstOrNull()?.let { (.4 * it.score + .6 * top.sumOf { record -> record.score } / 3.0) / 99.0 }
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
        return CharacterTraining(pr, volume, consistency, covered, covered < 365 || top.size < 3 || !input.bodyweightKnown,
            GymEvidence(top, kg, sets, bodyweight, input.bodyweightKnown))
    }
}
