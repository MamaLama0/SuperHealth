package app.superhealth.cards

import app.superhealth.nutrition.domain.Sex
import app.superhealth.training.domain.Analytics
import app.superhealth.training.domain.ExerciseDb
import app.superhealth.training.domain.MuscleGroup
import app.superhealth.training.domain.MuscleGroup.*
import app.superhealth.training.domain.Strength
import app.superhealth.training.domain.Workout
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.pow
import kotlin.math.roundToInt

/** Les six statistiques de la carte. Les quatre familles musculaires font la note, RÉG et CAR la complètent. */
enum class Stat(val code: String, val label: String, val muscles: Map<MuscleGroup, Double> = emptyMap(), val weeklySets: Double = 0.0) {
    // « tes pectoraux », « ton dos » : voir [mine].
    PEC("PEC", "Pectoraux", mapOf(CHEST to 3.0, SERRATUS to .5), 10.0),
    DOS("DOS", "Dos", mapOf(LATS to 3.0, UPPER_BACK to 2.0, UPPER_TRAPS to 1.0, LOWER_BACK to 1.0), 14.0),
    // Bras et épaules ne font qu'une famille : les développés travaillent les deux.
    BRA("BRA", "Bras et épaules", mapOf(FRONT_DELTS to 1.5, SIDE_DELTS to 1.5, REAR_DELTS to 1.0, BICEPS to 1.5, TRICEPS to 2.0, FOREARMS to .7, NECK to .2), 22.0),
    JAM("JAM", "Jambes", mapOf(QUADS to 3.0, HAMSTRINGS to 2.0, GLUTES to 3.0, ADDUCTORS to 1.0, ABDUCTORS to .5, CALVES to 1.0, TIBS to .3, HIP_FLEXORS to .3), 18.0),
    REG("RÉG", "Régularité"),
    CAR("CAR", "Cardio");

    val muscular get() = muscles.isNotEmpty()
    val at get() = when (this) { DOS -> "au dos"; BRA -> "aux bras et aux épaules"; REG -> "en régularité"; CAR -> "en cardio"; else -> "aux " + label.lowercase() }
    val mine get() = when (this) { DOS -> "ton dos"; BRA -> "tes bras et tes épaules"; REG -> "ta régularité"; CAR -> "ton cardio"; else -> "tes " + label.lowercase() }

    companion object {
        val families = entries.filter { it.muscular }
        fun of(code: String) = entries.firstOrNull { it.code == code }
    }
}

enum class Tier(val min: Int, val label: String) {
    BRONZE(0, "Bronze"), ARGENT(60, "Argent"), OR(70, "Or"), ELITE(80, "Élite"), ICONE(88, "Icône");
    val next: Tier? get() = entries.getOrNull(ordinal + 1)
    companion object { fun of(ovr: Int?) = entries.lastOrNull { (ovr ?: 0) >= it.min } ?: BRONZE }
}

/** Statistiques calculées, partageables telles quelles dans la carte sociale (quelques centaines d'octets). */
@Serializable
data class AthleteStats(
    val model: Int = AthleteStatsEngine.MODEL,
    /** Code de [Stat] → valeur 25..99 ; absent = pas évaluée. */
    val values: Map<String, Int> = emptyMap(),
    /** Statistiques encore provisoires (moins de trois séances en huit semaines). */
    val provisional: Set<String> = emptySet(),
    /** Note globale × 10 (87,7 → 877), null tant que moins de deux familles sont évaluées. */
    val ovr10: Int? = null,
    /** Points par muscle (clé de [MuscleGroup]) pour la carte du corps au verso. */
    val muscles: Map<String, Int> = emptyMap(),
    val computedAt: Long = 0,
    /** Sexe déclaré (« F » ou « M ») : les barèmes de force et de cardio en dépendent, la silhouette par défaut aussi. */
    val sex: String? = null,
) {
    val female get() = sex == "F"
    val ovr: Int? get() = ovr10?.let { (it + 5) / 10 }
    val tier get() = Tier.of(ovr)
    operator fun get(stat: Stat): Int? = values[stat.code]

    /** Famille la plus basse : à poids égal dans la note, c'est elle qui rapporte le plus vite. */
    val mostProfitable: Stat? get() = Stat.families.filter { values[it.code] != null }.minByOrNull { values[it.code]!! }
}

object AthleteStatsEngine {
    const val MODEL = 2
    private const val DAY = 86_400_000L

    data class Run(val start: Long, val distanceM: Double, val seconds: Double)

    data class Input(
        val history: List<Workout>, val db: ExerciseDb, val bodyweight: Double, val sex: Sex, val age: Int, val now: Long,
        val weeklyTarget: Int = 3, val vo2max: Double? = null, val runs: List<Run> = emptyList(), val cardioMinutes28: Double? = null,
        /** Jours (epochDay) où l'app a servi : repas, pesée, séance, activité. Nourrit la régularité avec l'entraînement. */
        val trackedDays: Set<Long> = emptySet(),
        /** Pas moyens par jour sur 28 jours, comptés dans le cardio. */
        val dailySteps28: Double? = null,
        val zone: ZoneId = ZoneId.systemDefault(),
    )

    private class ExerciseBest(var points: Double, var last: Long)

    fun compute(input: Input): AthleteStats {
        val now = input.now
        val past = input.history.filter { it.startTime < now }
        val window = past.filter { it.startTime >= now - 84 * DAY }
        val recent = forcePoints(window.filter { it.startTime >= now - 28 * DAY }, input)
        val all = forcePoints(window, input)
        val older = forcePoints(window.filter { it.startTime < now - 28 * DAY }, input)
        val weekly = Analytics.setsByMuscle(window.filter { it.startTime >= now - 42 * DAY }, input.db).mapValues { it.value / 6.0 }

        val values = linkedMapOf<String, Int>()
        val provisional = mutableSetOf<String>()
        for (stat in Stat.families) {
            val touching = window.filter { w -> w.exercises.any { e -> input.db.exercise(e.exerciseId)?.let { ex -> ex.muscleGroups.any { it in stat.muscles } } == true && Analytics.workingSets(e).isNotEmpty() } }
            if (touching.isEmpty()) continue
            val sets = stat.muscles.keys.sumOf { weekly[it] ?: 0.0 }
            val volume = (sets / stat.weeklySets).coerceIn(0.0, 1.2)
            val force = familyForce(stat, all)
            val value = if (force == null) 35.0 + 45.0 * volume.coerceAtMost(1.0) else {
                val idle = ((now - touching.maxOf { it.startTime }) / DAY - 21).coerceAtLeast(0)
                val detrained = force * (1 - (0.005 * idle).coerceAtMost(0.15))
                val trend = familyForce(stat, recent)?.let { r -> familyForce(stat, older)?.let { o -> ((r - o) / 3).coerceIn(-2.0, 2.0) } } ?: 0.0
                detrained + ((volume - 0.8) * 10).coerceIn(-6.0, 4.0) + trend
            }
            values[stat.code] = value.roundToInt().coerceIn(25, 99)
            if (touching.count { it.startTime >= now - 56 * DAY } < 3) provisional += stat.code
        }

        regularity(past, input)?.let { (v, prov) -> values[Stat.REG.code] = v; if (prov) provisional += Stat.REG.code }
        cardio(input)?.let { values[Stat.CAR.code] = it }

        val fams = Stat.families.mapNotNull { values[it.code] }
        val ovr10 = if (fams.size < 2) null else {
            val parts = listOfNotNull(fams.average() to 0.80, values[Stat.REG.code]?.let { it.toDouble() to 0.12 }, values[Stat.CAR.code]?.let { it.toDouble() to 0.08 })
            (parts.sumOf { it.first * it.second } / parts.sumOf { it.second } * 10).roundToInt()
        }
        val muscles = all.entries.associate { it.key.key to it.value.roundToInt().coerceIn(25, 99) }
        return AthleteStats(values = values, provisional = provisional, ovr10 = ovr10, muscles = muscles, computedAt = now, sex = if (input.sex == Sex.FEMALE) "F" else "M")
    }

    /**
     * Points de force par muscle, sur plusieurs exercices : chaque exercice reconnu donne ses meilleurs points
     * (e1RM rapporté aux standards) à ses muscles principaux avec un poids 1, et à ses secondaires avec un poids 0,35
     * et 5 points de moins. Un muscle fait la moyenne pondérée de ses trois meilleurs exercices ; tant qu'il n'a pas
     * l'équivalent de deux exercices principaux, il perd jusqu'à 8 points (un seul exercice : −4).
     */
    private fun forcePoints(workouts: List<Workout>, input: Input): Map<MuscleGroup, Double> {
        val best = mutableMapOf<String, ExerciseBest>()
        for (w in workouts) for (entry in w.exercises) {
            val ex = input.db.exercise(entry.exerciseId) ?: continue
            val match = Standards.match(ex) ?: continue
            val levels = Standards.thresholds(match.lift, input.sex, input.bodyweight)
            for (set in Analytics.workingSets(entry)) {
                val reps = set.reps ?: continue
                if (reps !in 1..12) continue
                val load = if (match.lift.bodyweightLoad) input.bodyweight * (ex.bodyweight ?: 1.0) + (set.weight ?: 0.0)
                    else set.weight?.takeIf { it.isFinite() && it > 0 } ?: continue
                val e1rm = Strength.e1rm(load, reps, set.rir, ex.upperBody, set.partialReps)
                if (!e1rm.isFinite()) continue
                val points = Standards.points(e1rm / match.coef / input.bodyweight, levels)
                val b = best.getOrPut(ex.id) { ExerciseBest(0.0, 0) }
                if (points > b.points) b.points = points
                b.last = maxOf(b.last, w.startTime)
            }
        }
        val contributions = mutableMapOf<MuscleGroup, MutableList<Pair<Double, Double>>>()
        for ((id, b) in best) {
            val ex = input.db.exercise(id) ?: continue
            ex.muscleGroups.forEach { contributions.getOrPut(it) { mutableListOf() } += b.points to 1.0 }
            ex.secondaryMuscleGroups.forEach { if (it !in ex.muscleGroups) contributions.getOrPut(it) { mutableListOf() } += (b.points - 5) to SECONDARY }
        }
        return contributions.mapValues { (_, list) ->
            val top = list.sortedByDescending { it.first * it.second }.take(3)
            val weight = top.sumOf { it.second }
            top.sumOf { it.first * it.second } / weight - 4.0 * (2.0 - weight).coerceIn(0.0, 2.0)
        }
    }

    private const val SECONDARY = 0.35

    private fun familyForce(stat: Stat, points: Map<MuscleGroup, Double>): Double? {
        val used = stat.muscles.filterKeys { it in points }
        if (used.isEmpty()) return null
        return used.entries.sumOf { points[it.key]!! * it.value } / used.values.sum()
    }

    /**
     * Régularité : 60 % pour les semaines où l'objectif de séances est atteint, 40 % pour les jours où l'app a servi
     * (repas, pesée, séance, activité), sur douze semaines, les plus récentes pesant davantage.
     */
    private fun regularity(past: List<Workout>, input: Input): Pair<Int, Boolean>? {
        val zone = input.zone
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = day(input.now).toEpochDay()
        val tracked = input.trackedDays + past.map { day(it.startTime).toEpochDay() }
        val firstDay = tracked.filter { it <= today }.minOrNull() ?: return null
        fun monday(ms: Long) = day(ms).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val current = monday(input.now)
        val target = input.weeklyTarget.coerceIn(1, 7).toDouble()
        var train = 0.0; var track = 0.0; var den = 0.0; var weeks = 0
        for (k in 1..12) {
            val start = current.minusWeeks(k.toLong())
            if (start.plusDays(6).toEpochDay() < firstDay) break
            val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = start.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val weight = 13.0 - k
            train += weight * (past.count { it.startTime in from until to } / target).coerceAtMost(1.0)
            // Cinq jours suivis sur sept suffisent pour le plein : personne ne pèse tout, tous les jours.
            track += weight * ((start.toEpochDay() until start.toEpochDay() + 7).count { it in tracked } / 5.0).coerceAtMost(1.0)
            den += weight; weeks++
        }
        if (weeks == 0) return null
        val score = (0.6 * train + 0.4 * track) / den
        return (30 + 69 * score).roundToInt().coerceIn(25, 99) to (weeks < 3)
    }

    /**
     * Cardio : le niveau (VMA estimée par la VO₂max, sinon le meilleur 5 km) et le volume de toutes les activités
     * d'endurance, pondérées par leur intensité (course 1, vélo et natation 0,9, rando 0,8, marche 0,5), plus les pas.
     */
    private fun cardio(input: Input): Int? {
        val level: Double? = input.vo2max?.takeIf { it in 15.0..95.0 }?.let { Vo2Norms.points(it, input.sex, input.age) } ?: run {
            val since = input.now - 56 * DAY
            val best5k = input.runs.filter { it.start >= since && it.distanceM >= 1000 && it.seconds > 0 }
                .maxOfOrNull { (it.distanceM / it.seconds) * (it.distanceM / 5000.0).pow(0.06) } ?: return@run null
            val seconds = 5000 / best5k
            val factor = (if (input.sex == Sex.FEMALE) 1.12 else 1.0) * (1 + 0.008 * (input.age - 30).coerceAtLeast(0))
            val levels = doubleArrayOf(2100.0, 1800.0, 1560.0, 1320.0, 1140.0).map { it * factor }
            val pts = doubleArrayOf(40.0, 55.0, 70.0, 83.0, 93.0)
            when {
                seconds >= levels[0] -> 40.0 - (seconds - levels[0]) / 60.0
                seconds <= levels[4] -> 93.0 + 6.0 * (levels[4] - seconds) / (levels[3] - levels[4])
                else -> (0 until 4).first { seconds >= levels[it + 1] }.let { i -> pts[i] + (levels[i] - seconds) / (levels[i] - levels[i + 1]) * (pts[i + 1] - pts[i]) }
            }
        }
        // Volume hebdo équivalent : minutes pondérées, plus les pas au-delà de 3 000 par jour comptés comme de la marche
        // (2 000 pas ≈ 10 min, à 0,5) : 10 000 pas quotidiens valent environ deux heures par semaine.
        val minutes = (input.cardioMinutes28 ?: 0.0) / 4 + ((input.dailySteps28 ?: 0.0) - 3000).coerceAtLeast(0.0) / 2000 * 10 * 7 * 0.5
        val volume = if (input.cardioMinutes28 == null && input.dailySteps28 == null) null else 35 + 55 * (minutes / 150).coerceAtMost(1.2) / 1.2
        val value = when {
            level != null && volume != null -> 0.6 * level + 0.4 * volume
            else -> level ?: volume ?: return null
        }
        return value.roundToInt().coerceIn(25, 99)
    }
}
