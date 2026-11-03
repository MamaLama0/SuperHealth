package app.superhealth.league

import kotlinx.serialization.Serializable

/** Une quête principale et, si utile, une secondaire d'un autre domaine. Calcul entièrement local. */
object WeeklyQuests {
    private const val PRIMARY_REWARD = 80
    private const val SECONDARY_REWARD = 30
    const val MAX_WEEKLY_BONUS = PRIMARY_REWARD + SECONDARY_REWARD
    @Serializable enum class Focus(val label: String) { NUTRITION("Nutrition"), STRENGTH("Musculation"), RUN("Course"), DAILY("Au quotidien") }
    @Serializable enum class Reason(val text: String) {
        START("Un premier objectif concret pour découvrir votre rythme."),
        FOOD_GAP("Votre journal est irrégulier ces derniers jours. Un petit objectif pour reprendre le fil."),
        STRENGTH_GAP("Vos dernières semaines comportent moins de séances que votre programme. Reprenons progressivement."),
        PLATEAU("Vos dernières séries comparables évoluent peu. Noter l'effort aidera à comprendre votre progression."),
        FEEDBACK("L'effort est peu renseigné dans vos dernières séances. Ce repère peut rendre le suivi plus utile."),
        RUN_GAP("La course a eu peu de place récemment. Un rendez-vous accessible pour la remettre dans votre semaine."),
        STEPS_GAP("Votre objectif de pas est rarement atteint récemment. Essayez sur quelques jours, à votre rythme."),
        MIND_GAP("Un objectif de méditation ou de mobilité basé sur vos séances récentes."),
        MAINTAIN("Vous avez déjà un bon rythme dans ce domaine. Cette quête aide à le conserver."),
        VARIETY("Une petite quête dans un autre domaine, pour varier votre semaine.")
    }
    @Serializable enum class Metric(val title: String, val route: String, val track: Progression.Track? = null) {
        ACTIVE("Un peu de régularité", "activities"),
        STRENGTH("Votre rendez-vous musculation", "activities", Progression.Track.STRENGTH),
        STRENGTH_REVIEW("Mieux comprendre votre progression", "activities", Progression.Track.STRENGTH),
        RUN("Enchaîner les sorties course", "activities", Progression.Track.RUN),
        FOOD("Garder le fil du journal", "log", Progression.Track.FOOD),
        STEPS("Bouger à votre rythme", "h/metric/STEPS", Progression.Track.STEPS),
        SLEEP("Faire une place au repos", "h/metric/SLEEP", Progression.Track.SLEEP),
        MIND("Méditation et mobilité", "activities", Progression.Track.MIND);
        val focus: Focus get() = when (this) { FOOD -> Focus.NUTRITION; STRENGTH, STRENGTH_REVIEW -> Focus.STRENGTH; RUN -> Focus.RUN; else -> Focus.DAILY }
    }
    @Serializable data class Quest(val metric: Metric, val target: Int, val progress: Int = 0, val reward: Int = PRIMARY_REWARD, val claimed: Boolean = false,
        val secondary: Boolean = false, val reason: Reason = Reason.START) {
        val complete get() = progress >= target
    }
    @Serializable data class Week(val start: Long, val quests: List<Quest>, val focuses: Set<Focus> = emptySet())
    data class Evidence(val previous14: Map<Metric, Int> = emptyMap(), val strengthGoal: Int = 2, val runGoal: Int = 2, val plateau: Boolean = false, val healthData: Boolean = true)
    private data class Candidate(val metric: Metric, val weakness: Double, val reason: Reason)

    fun update(previous: Week?, today: Long, tracked: Set<Progression.Track>, counts: Map<Metric, Int>, focuses: Set<Focus>, evidence: Evidence,
        history: List<Week> = emptyList()): Week {
        val start = Progression.week(today)
        val remaining = (start + 7 - today).toInt().coerceIn(1, 7)
        val old = previous?.takeIf { it.start == start }?.let { week ->
            // Keep earned rewards; retire unavailable activities and the old generic pause quest.
            week.copy(quests = week.quests.filter { q -> q.claimed ||
                (q.metric.track in tracked && (q.metric != Metric.MIND || (evidence.previous14[Metric.MIND] ?: 0) > 0 || (counts[Metric.MIND] ?: 0) > 0)) })
        }?.takeIf { it.quests.isNotEmpty() }
        val plan = old?.quests ?: run {
            fun n(metric: Metric) = evidence.previous14[metric] ?: 0
            fun gap(metric: Metric, expected: Int) = 1.0 - (n(metric).toDouble() / expected.coerceAtLeast(1)).coerceIn(0.0, 1.0)
            val candidates = buildList {
                fun option(metric: Metric, deficit: Double, reason: Reason) {
                    if (metric.track in tracked && metric.focus in focuses &&
                        (metric !in setOf(Metric.STEPS, Metric.RUN) || evidence.healthData) &&
                        (metric != Metric.MIND || n(metric) > 0)) add(Candidate(metric, if (metric in setOf(Metric.STEPS, Metric.RUN) && !evidence.healthData) .2 else deficit, if (metric in setOf(Metric.STEPS, Metric.RUN) && !evidence.healthData) Reason.START else if (n(metric) == 0 && evidence.previous14.values.all { it == 0 }) Reason.START else if (deficit < .25) Reason.MAINTAIN else reason))
                }
                option(Metric.FOOD, gap(Metric.FOOD, 14), Reason.FOOD_GAP)
                val strengthGap = gap(Metric.STRENGTH, evidence.strengthGoal * 2)
                if (strengthGap >= .5) option(Metric.STRENGTH, strengthGap, Reason.STRENGTH_GAP)
                else if (evidence.plateau) option(Metric.STRENGTH_REVIEW, .85, Reason.PLATEAU)
                else if (n(Metric.STRENGTH) > 0 && n(Metric.STRENGTH_REVIEW) * 2 < n(Metric.STRENGTH)) option(Metric.STRENGTH_REVIEW, .65, Reason.FEEDBACK)
                else option(Metric.STRENGTH, strengthGap, Reason.MAINTAIN)
                option(Metric.RUN, gap(Metric.RUN, evidence.runGoal * 2), Reason.RUN_GAP)
                option(Metric.STEPS, gap(Metric.STEPS, 14), Reason.STEPS_GAP)
                option(Metric.MIND, gap(Metric.MIND, 4), Reason.MIND_GAP)
            }
            val lastFocus = history.filter { it.start < start }.maxByOrNull { it.start }?.quests?.firstOrNull { !it.secondary }?.metric?.focus
            val primary = candidates.maxWithOrNull(compareBy<Candidate> { it.weakness - if (it.metric.focus == lastFocus) .15 else 0.0 }
                .thenBy { Math.floorMod(start / 7 + it.metric.ordinal, 7) })
            val secondary = candidates.filter { it.metric.focus != primary?.metric?.focus }
                .maxByOrNull { it.weakness }?.takeIf { it.weakness >= .45 }
            listOfNotNull(primary, secondary).mapIndexed { i, c ->
                val baseline = n(c.metric) / 2
                val goal = when (c.metric) {
                    Metric.STRENGTH -> minOf(evidence.strengthGoal.coerceIn(1, 7), (baseline + 1).coerceAtLeast(1))
                    Metric.STRENGTH_REVIEW -> minOf(2, evidence.strengthGoal.coerceAtLeast(1))
                    Metric.RUN -> minOf(evidence.runGoal.coerceIn(1, 3), (baseline + 1).coerceAtLeast(1))
                    Metric.FOOD -> (baseline + 1).coerceIn(1, 7)
                    Metric.STEPS -> (baseline + 1).coerceIn(1, 7)
                    else -> (baseline + 1).coerceIn(1, 3)
                }
                Quest(c.metric, minOf(goal, (counts[c.metric] ?: 0) + remaining).coerceAtLeast(1), reward = if (i == 0) PRIMARY_REWARD else SECONDARY_REWARD,
                    secondary = i > 0, reason = if (i > 0 && c.weakness < .5) Reason.VARIETY else c.reason)
            }
        }
        return Week(start, plan.map { q -> if (q.claimed) q else q.copy(progress = (counts[q.metric] ?: 0).coerceIn(0, q.target)) }, old?.focuses ?: focuses)
    }
    /** Trois séances comparables au minimum ; une absence d'historique n'est jamais un plateau. */
    fun plateau(values: List<Double>): Boolean = values.takeLast(4).let { tail -> tail.size >= 3 && tail.all { it.isFinite() && it > 0 } && tail.drop(1).maxOrNull()!! <= tail.first() * 1.01 }
    fun claim(week: Week, metric: Metric, today: Long): Week = if (week.start != Progression.week(today)) week else
        week.copy(quests = week.quests.map { if (it.metric == metric && it.complete && !it.claimed) it.copy(claimed = true) else it })
}
