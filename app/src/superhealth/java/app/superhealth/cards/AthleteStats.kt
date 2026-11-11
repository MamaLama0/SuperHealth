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
    val character: CharacterStats? = null,
) {
    val female get() = sex == "F"
    val ovr: Int? get() = ovr10?.let { (it + 5) / 10 }
    val tier get() = Tier.of(ovr)
    operator fun get(stat: Stat): Int? = values[stat.code]

    /** Famille la plus basse : à poids égal dans la note, c'est elle qui rapporte le plus vite. */
    val mostProfitable: Stat? get() = Stat.families.filter { values[it.code] != null }.minByOrNull { values[it.code]!! }
}

object AthleteStatsEngine {
    const val MODEL = 3
    private const val DAY = 86_400_000L

    data class Run(val start: Long, val distanceM: Double, val seconds: Double)
    /** Une activité d'endurance : minutes pondérées par l'intensité et kilomètres en équivalent course. */
    data class Endurance(val start: Long, val minutes: Double, val km: Double)

    /** Récence : une activité compte pour moitié au bout de 3 semaines ([HALF_LIFE] jours). */
    const val HALF_LIFE = 21.0
    fun recency(start: Long, now: Long) = 0.5.pow(((now - start) / DAY.toDouble()).coerceAtLeast(0.0) / HALF_LIFE)
    /** Fenêtre des activités « récentes » : six mois, l'app étant jeune, les données plus anciennes manquent souvent. */
    const val WINDOW = 182
    /** Ancienneté retenue pour la force : le meilleur niveau de l'année, un peu vieilli. */
    const val YEAR = 365

    /**
     * Somme hebdomadaire pondérée par la récence sur [days] jours : une semaine récente pèse plus qu'une vieille. On ne
     * divise que par les jours couverts depuis [origin] (premières données, 4 semaines au moins) : un compte récent
     * n'est pas noté comme s'il était resté inactif avant.
     */
    private fun <T> weekly(items: List<T>, now: Long, days: Int, start: (T) -> Long, origin: Long? = null, value: (T) -> Double): Double {
        val covered = origin?.let { ((now - it) / DAY).toInt().coerceIn(28, days) } ?: days
        val weeks = (0 until covered).sumOf { 0.5.pow(it / HALF_LIFE) } / 7
        return items.filter { start(it) in now - days * DAY..now }.sumOf { value(it) * recency(start(it), now) } / weeks
    }

    data class Input(
        val history: List<Workout>, val db: ExerciseDb, val bodyweight: Double, val sex: Sex, val age: Int, val now: Long,
        val weeklyTarget: Int = 3, val vo2max: Double? = null, val runs: List<Run> = emptyList(), val cardioMinutes28: Double? = null,
        /** Jours (epochDay) où l'app a servi : repas, pesée, séance, activité. Nourrit la régularité avec l'entraînement. */
        val trackedDays: Set<Long> = emptySet(),
        /** Pas moyens par jour sur 28 jours, comptés dans le cardio. */
        val dailySteps28: Double? = null,
        /** Activités d'endurance datées (six mois comptent, la récence pondère), FC au repos et taille pour estimer la VO₂max. */
        val endurance: List<Endurance>? = null,
        /** Pas par jour (début du jour en ms → pas) sur 8 semaines, pour pondérer par la récence. */
        val steps: List<Pair<Long, Double>>? = null, val restingHr: Double? = null, val heightCm: Double? = null,
        val zone: ZoneId = ZoneId.systemDefault(),
    )

    private class ExerciseBest(var points: Double, var last: Long, val confidence: Double)

    fun compute(input: Input): AthleteStats {
        val now = input.now
        val past = input.history.filter { it.startTime < now }
        val window = past.filter { it.startTime >= now - WINDOW * DAY }
        val year = past.filter { it.startTime >= now - YEAR * DAY }
        val recent = forcePoints(window.filter { it.startTime >= now - 28 * DAY }, input)
        val older = forcePoints(window.filter { it.startTime < now - 28 * DAY }, input)
        // La force est le meilleur niveau de l'année ; un record ancien perd 0,5 point par mois au-delà de 3 mois (4 au plus).
        val best = forcePoints(year, input, aged = true)
        // Au-delà d'un an, les records de toujours parlent encore, désentraînés.
        val lifetime = forcePoints(past, input, aged = true)
        // Séries par semaine sur 6 semaines, ou depuis la première séance si elle est plus récente (2 semaines au moins).
        val volumeWeeks = past.minOfOrNull { it.startTime }?.let { ((now - it) / (7.0 * DAY)).coerceIn(2.0, 6.0) } ?: 6.0
        val weekly = Analytics.setsByMuscle(window.filter { it.startTime >= now - 42 * DAY }, input.db).mapValues { it.value / volumeWeeks }

        val values = linkedMapOf<String, Int>()
        val provisional = mutableSetOf<String>()
        for (stat in Stat.families) {
            val touchingEver = past.filter { w -> w.exercises.any { e -> input.db.exercise(e.exerciseId)?.let { ex -> ex.muscleGroups.any { it in stat.muscles } } == true && Analytics.workingSets(e).isNotEmpty() } }
            if (touchingEver.isEmpty()) continue
            val touching = touchingEver.filter { it.startTime >= now - YEAR * DAY }
            val old = familyForce(stat, lifetime)
            if (touching.isEmpty()) {
                // Rien depuis un an : seuls les anciens records parlent, désentraînés (−0,5 % par jour au-delà de 21 jours, 15 % au plus).
                old ?: continue
                val idle = ((now - touchingEver.maxOf { it.startTime }) / DAY - 21).coerceAtLeast(0)
                values[stat.code] = (old * (1 - (0.005 * idle).coerceAtMost(0.15))).roundToInt().coerceIn(25, 99)
                provisional += stat.code
                continue
            }
            val sets = stat.muscles.keys.sumOf { weekly[it] ?: 0.0 }
            val volume = (sets / stat.weeklySets).coerceIn(0.0, 1.2)
            val force = familyForce(stat, best)
            val value = if (force == null) 35.0 + 45.0 * volume.coerceAtMost(1.0) else {
                val idle = ((now - touching.maxOf { it.startTime }) / DAY - 21).coerceAtLeast(0)
                val detrained = force * (1 - (0.005 * idle).coerceAtMost(0.15))
                val trend = familyForce(stat, recent)?.let { r -> familyForce(stat, older)?.let { o -> ((r - o) / 3).coerceIn(-2.0, 2.0) } } ?: 0.0
                // La régularité (séries par semaine) ne pèse qu'un peu : ±3 points autour du maximum de l'année.
                detrained + ((volume - 0.8) * 6).coerceIn(-3.0, 3.0) + trend
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
        val muscles = best.entries.associate { it.key.key to it.value.roundToInt().coerceIn(25, 99) }
        return AthleteStats(values = values, provisional = provisional, ovr10 = ovr10, muscles = muscles, computedAt = now, sex = if (input.sex == Sex.FEMALE) "F" else "M")
    }

    /**
     * Points de force par muscle, sur plusieurs exercices : chaque exercice reconnu donne ses meilleurs points
     * (e1RM rapporté aux standards) à ses muscles principaux avec un poids 1, et à ses secondaires avec un poids 0,35
     * et 5 points de moins ; un exercice de la charte de compatibilité ([Standards.Match.confidence]) pèse moins. Un muscle fait la moyenne pondérée de ses trois meilleurs exercices ; tant qu'il n'a pas
     * l'équivalent d'un exercice principal et demi, il perd jusqu'à 4 points (un seul exercice : −1,25). Un pratiquant
     * fort qui s'en tient à quelques gros mouvements ne doit pas passer sous un débutant qui en varie beaucoup.
     */
    private fun forcePoints(workouts: List<Workout>, input: Input, aged: Boolean = false): Map<MuscleGroup, Double> {
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
                val months = (input.now - w.startTime) / (30.0 * DAY)
                val points = Standards.points(e1rm / match.coef / input.bodyweight, levels) - if (aged) (0.5 * (months - 3)).coerceIn(0.0, 4.0) else 0.0 -
                    // Charte de compatibilité : petite décote (≈ 1,5 point), sans plafond, pour qu'une charge vraiment lourde passe devant.
                    3.0 * (1 - match.confidence)
                val b = best.getOrPut(ex.id) { ExerciseBest(0.0, 0, match.confidence) }
                if (points > b.points) b.points = points
                b.last = maxOf(b.last, w.startTime)
            }
        }
        val contributions = mutableMapOf<MuscleGroup, MutableList<Pair<Double, Double>>>()
        for ((id, b) in best) {
            val ex = input.db.exercise(id) ?: continue
            // Les exercices à repères comptent plein ; ceux de la charte de compatibilité, selon leur fiabilité.
            ex.muscleGroups.forEach { contributions.getOrPut(it) { mutableListOf() } += b.points to b.confidence }
            ex.secondaryMuscleGroups.forEach { if (it !in ex.muscleGroups) contributions.getOrPut(it) { mutableListOf() } += (b.points - 5) to SECONDARY * b.confidence }
        }
        return contributions.mapValues { (_, list) ->
            val top = list.sortedByDescending { it.first * it.second }.take(3)
            val weight = top.sumOf { it.second }
            top.sumOf { it.first * it.second } / weight - 2.5 * (1.5 - weight).coerceIn(0.0, 1.5)
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
     * (repas, pesée, séance, activité), sur six mois, les semaines récentes pesant davantage.
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
        for (k in 1..26) {
            val start = current.minusWeeks(k.toLong())
            if (start.plusDays(6).toEpochDay() < firstDay) break
            val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = start.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val weight = 27.0 - k
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
     * Cardio : cinq critères notés sur la même échelle (≈ 70 = la moyenne, 90+ = très bon) puis pondérés, en ne gardant
     * que ceux qu'on peut mesurer. Les activités pèsent selon leur récence (moitié au bout de 3 semaines) et la note
     * baisse sans endurance récente.
     *  - niveau (30 %) : la VO₂max mesurée, notée selon les normes Cooper/ACSM par âge et sexe ([Vo2Norms]) ; sinon les
     *    courses récentes (moyenne du VDOT de Daniels et de la grille du 5 km), sinon la formule d'Uth avec la FC au
     *    repos, sinon le modèle sans effort de Jackson (âge, IMC, activité) ;
     *  - fréquence (20 %) : séances d'endurance par semaine (ACSM : 3 à 5) et volume hebdo en minutes pondérées par
     *    l'intensité (course 1, vélo et natation 0,9, rando 0,8, marche 0,5), pas au-delà de 3 000 par jour compris ;
     *  - pas (20 %) : pas quotidiens, d'après les paliers de Tudor-Locke (5 000, 7 500, 10 000, 12 500) ;
     *  - distance (15 %) : kilomètres par semaine en équivalent course (≈ 10 à 16 km pour un coureur moyen) ;
     *  - vitesse (15 %) : allure moyenne des courses, corrigée de l'âge et du sexe (≈ 5:50/km pour un homme moyen).
     */
    private fun cardio(input: Input): Int? {
        val now = input.now
        val acts = input.endurance?.filter { it.start <= now }
        val allRuns = input.runs.filter { it.start <= now }
        val runs = allRuns.filter { it.start >= now - WINDOW * DAY }
        // Pas du jour, les jours récents comptant plus ; à défaut la moyenne sur 28 jours.
        val days = input.steps?.filter { it.first in now - 56 * DAY..now }?.takeIf { it.size >= 7 }
        val dailySteps = days?.let { d -> d.sumOf { it.second * recency(it.first, now) } / d.sumOf { recency(it.first, now) } } ?: input.dailySteps28
        // Volume hebdo équivalent : minutes pondérées, plus les pas au-delà de 3 000 par jour comptés comme de la marche
        // (2 000 pas ≈ 10 min, à 0,5) : 10 000 pas quotidiens valent environ deux heures par semaine.
        val origin = acts?.minOfOrNull { it.start }
        val minutes = (acts?.let { weekly(it, now, WINDOW, Endurance::start, origin, Endurance::minutes) } ?: (input.cardioMinutes28 ?: 0.0) / 4) +
            ((dailySteps ?: 0.0) - 3000).coerceAtLeast(0.0) / 2000 * 10 * 7 * 0.5
        val active = acts != null || input.cardioMinutes28 != null || dailySteps != null
        val level = input.vo2max?.takeIf { it in 15.0..95.0 }?.let { Vo2Norms.points(it, input.sex, input.age) } ?: run {
            // Une course ancienne dit moins du niveau d'aujourd'hui : après 10 jours, son VDOT perd 0,3 % par jour (20 % au plus).
            fun level(rs: List<Run>): Double? {
                val vdot = rs.mapNotNull { r -> CardioNorms.vdot(listOf(r))?.let { it * (1 - (0.003 * ((now - r.start) / DAY - 10).coerceAtLeast(0)).coerceAtMost(0.20)) } }.maxOrNull()
                return listOfNotNull(vdot?.let { Vo2Norms.points(it, input.sex, input.age) }, CardioNorms.best5k(rs, input.sex, input.age)).takeIf { it.isNotEmpty() }?.average()
            }
            // Les records anciens comptent aussi : un tiers du poids à côté des courses des 6 derniers mois.
            val recentLevel = level(runs); val ever = level(allRuns.filter { it.start < now - WINDOW * DAY })
            when { recentLevel != null && ever != null -> 0.65 * recentLevel + 0.35 * maxOf(ever, recentLevel - 10); else -> recentLevel ?: ever?.let { it * 0.9 } }
        } ?: (input.restingHr?.takeIf { it in 30.0..110.0 }?.let { CardioNorms.uth(input.age, it) }
            ?: input.heightCm?.takeIf { active && it in 120.0..230.0 }?.let { CardioNorms.jackson(input.age, input.sex, input.bodyweight / (it / 100).pow(2), minutes) })
            ?.let { Vo2Norms.points(it, input.sex, input.age) }
        val frequency = if (!active) null else listOfNotNull(
            acts?.let { CardioNorms.sessions(weekly(it, now, WINDOW, Endurance::start, origin) { 1.0 }) }, 35 + 55 * (minutes / 150).coerceAtMost(1.2) / 1.2).average()
        val stepScore = dailySteps?.let(CardioNorms::steps)
        val distance = acts?.let { CardioNorms.distance(weekly(it, now, WINDOW, Endurance::start, origin, Endurance::km)) }
        // Allure moyenne : les courses récentes comptent plus.
        val recent = runs.filter { it.distanceM >= 1000 && it.seconds > 0 }
        val speed = recent.takeIf { it.isNotEmpty() }?.let { rs ->
            CardioNorms.pace(rs.sumOf { it.seconds * recency(it.start, now) } / rs.sumOf { it.distanceM / 1000 * recency(it.start, now) }, input.sex, input.age)
        }
        val parts = listOfNotNull(level?.to(0.30), frequency?.to(0.20), stepScore?.to(0.20), distance?.to(0.15), speed?.to(0.15))
        if (parts.isEmpty()) return null
        val value = parts.sumOf { it.first * it.second } / parts.sumOf { it.second }
        // Désentraînement : sans activité d'endurance depuis plus de 2 semaines, −0,5 % par jour (15 % au plus).
        val last = (acts?.map { it.start }.orEmpty() + allRuns.map { it.start }).maxOrNull()
        val idle = last?.let { ((now - it) / DAY - 14).coerceAtLeast(0) } ?: 0
        return (value * (1 - (0.005 * idle).coerceAtMost(0.15))).roundToInt().coerceIn(25, 99)
    }
}
