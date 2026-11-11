package app.superhealth.training.domain

import app.superhealth.adaptive.Gate
import app.superhealth.adaptive.LevelTrend
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Estimation unique de la force, par exercice et par salle (plan 3.5, moteur 2).
 *
 * Chaque séance devient une mesure du 1RM frais en logarithme : moyenne des séries de travail pondérée par la fiabilité de
 * leur RIR, après correction de la fatigue intra-séance ([Strength]). Des partielles ou une série « échec » prouvent RIR 0 et
 * servent d'ancre : elles mesurent l'écart entre le RIR annoncé et le RIR réel des autres séries ([rirBias]).
 * Un filtre niveau + pente ([LevelTrend]) suit ces mesures ; la pente devient la progression attendue de la personne.
 * Le filtre ne remplace la règle d'origine (dernière séance + 1 %) qu'à travers la porte de confiance commune ([Gate]).
 */
object StrengthModel {
    private const val DAY = 86_400_000.0
    internal const val MAX_REPS = 30

    data class Measurement(val time: Long, val z: Double, val r: Double, val sets: Int, val impliedBias: Double?)

    data class Estimate(
        /** 1RM frais attendu pour la séance (kg). */
        val oneRm: Double,
        /** Incertitude du niveau (log). */
        val sd: Double,
        /** Progression attendue par séance (fraction, 0,005 = +0,5 %). */
        val perSession: Double,
        val sessions: Int,
        val plateau: Boolean,
        /** Poids accordé au filtre par la porte de confiance (0 = règle d'origine). */
        val trust: Double,
    )

    private fun prior(experience: Experience) = when (experience) {
        Experience.BEGINNER -> 0.010; Experience.INTERMEDIATE -> 0.005; Experience.ADVANCED -> 0.0025
    }

    /** Écart-type (log) d'une estimation selon le RIR noté et le nombre de répétitions. */
    internal fun setSigma(reps: Int, rir: Int?, proven: Boolean): Double {
        var s = when {
            proven -> 0.025
            rir == null -> 0.08
            rir <= 1 -> 0.03
            rir <= 3 -> 0.045
            else -> 0.08
        }
        if (reps > 12) s += 0.015
        if (reps > 20) s += 0.025
        return s
    }

    private fun SetLog.proven() = partialReps > 0 || type == SetType.FAILURE

    /** RTF d'une série avec un RIR corrigé du biais (fractionnaire) ; les partielles prouvent l'échec. */
    private fun rtf(set: SetLog, bias: Double): Double {
        val reps = set.reps!!
        if (set.proven()) return Strength.rtf(reps, 0, set.partialReps)
        val stated = (set.rir ?: set.target?.rir ?: 2).toDouble()
        return reps + (stated - bias).coerceAtLeast(0.0)
    }

    internal fun observedOneRm(set: SetLog, upper: Boolean, bias: Double): Double =
        set.weight!! / Strength.pct(rtf(set, bias).coerceAtLeast(1.0), upper)

    /** Mesure d'une séance pour un exercice (un ou plusieurs blocs). Null sans série exploitable. */
    fun measure(blocks: List<SessionExercise>, upper: Boolean, bias: Double, time: Long): Measurement? {
        var num = 0.0; var den = 0.0; var count = 0
        val implied = mutableListOf<Double>()
        for (block in blocks) {
            val sets = Analytics.workingSets(block).filter { (it.weight ?: 0.0) > 0 && (it.reps ?: 0) in 1..MAX_REPS && it.weight!!.isFinite() }
            var fatigue = 0.0
            val fresh = mutableListOf<Triple<SetLog, Double, Double>>() // série, perte de fatigue, e1RM frais
            for ((i, set) in sets.withIndex()) {
                val drop = Strength.fatigueDrop(fatigue)
                if (drop < 0.5) {
                    val e = observedOneRm(set, upper, bias) / (1 - drop)
                    val sigma = setSigma(set.reps!!, set.rir, set.proven())
                    num += ln(e) / (sigma * sigma); den += 1 / (sigma * sigma); count++
                    fresh += Triple(set, drop, e)
                }
                fatigue = Strength.recover(fatigue + Strength.fatiguingReps(set.reps!!, set.partialReps),
                    Progression.restMinutes(set, sets.getOrNull(i + 1), block.restSec))
            }
            // Ancre : 1RM frais d'une série à l'échec, comparé au RIR annoncé des autres séries de la séance.
            val anchor = fresh.filter { it.first.proven() }.map { it.third }.takeIf { it.isNotEmpty() }?.average() ?: continue
            for ((set, drop, _) in fresh) {
                if (set.proven() || set.rir == null) continue
                val trueRtf = Strength.rtfAt(set.weight!!, anchor * (1 - drop), upper)
                implied += set.rir - (trueRtf - set.reps!!)
            }
        }
        if (count == 0) return null
        return Measurement(time, num / den, max(1 / den, 0.02 * 0.02), count, implied.takeIf { it.isNotEmpty() }?.let { Gate.median(it) })
    }

    /**
     * Biais de RIR de la personne (RIR annoncé − RIR réel), appris sur les séances qui contiennent une série à l'échec.
     * Positif : les séries sont plus proches de l'échec qu'annoncé. Moyenne exponentielle bornée à ±2, nulle avant trois
     * séances ancrées ou si l'écart reste sous un quart de répétition.
     */
    fun rirBias(history: List<Workout>, db: ExerciseDb): Double {
        var bias = 0.0; var n = 0
        for (w in history.distinctBy { it.id }.sortedBy { it.startTime }) {
            for ((id, blocks) in w.exercises.groupBy { it.exerciseId }) {
                val e = db.exercise(id) ?: continue
                if (!e.usesReps || !e.usesWeight || "aw" in e.metrics) continue
                val implied = measure(blocks, e.upperBody, 0.0, w.startTime)?.impliedBias ?: continue
                bias = if (n == 0) implied else 0.9 * bias + 0.1 * implied
                n++
            }
        }
        if (n < 3) return 0.0
        return bias.coerceIn(-2.0, 2.0).takeIf { kotlin.math.abs(it) >= 0.25 } ?: 0.0
    }

    private fun Workout.matches(gym: Gym?) = if (gymId != null) gymId == gym?.id else gymName == gym?.name

    private fun filterFor(gapDays: Double) = LevelTrend(
        qLevel = 1.5e-5, qSlope = 4e-8, slopeSd0 = 0.004 / gapDays,
        slopeMin = ln(0.998) / gapDays, slopeMax = ln(1.015) / gapDays,
    )

    private fun seriesIn(exercise: Exercise, workouts: Sequence<Workout>, bias: Double): List<Measurement> = workouts.mapNotNull { w ->
        val blocks = w.exercises.filter { it.exerciseId == exercise.id }
        if (blocks.isEmpty()) null else measure(blocks, exercise.upperBody, bias, w.startTime)
    }.toList()

    private fun series(exercise: Exercise, history: List<Workout>, gym: Gym?, bias: Double, before: Long): List<Measurement> {
        val all = history.asSequence().distinctBy { it.id }.filter { it.startTime < before }.sortedBy { it.startTime }
        val here = seriesIn(exercise, all.filter { it.matches(gym) }, bias)
        // Exercice jamais fait dans cette salle : on part des autres salles, avec une incertitude élargie (autres machines).
        return here.ifEmpty { seriesIn(exercise, all, bias).map { it.copy(r = it.r + 0.05 * 0.05) } }
    }

    /** Erreurs (règle d'origine, filtre) de chaque séance prédite sans la connaître, pour un exercice. */
    private fun replay(ms: List<Measurement>, experience: Experience): Pair<LevelTrend.State?, List<Pair<Double, Double>>> {
        if (ms.isEmpty()) return null to emptyList()
        val gap = gapDays(ms)
        val f = filterFor(gap)
        var s = f.start(ms[0].time / DAY, ms[0].z, ms[0].r, ln(1 + prior(experience)) / gap)
        val errors = mutableListOf<Pair<Double, Double>>()
        for (k in 1 until ms.size) {
            val day = ms[k].time / DAY
            val base = ms[k - 1].z + ln(Strength.SESSION_PROGRESS)
            val personal = f.predict(detrain(s, day), day).level
            errors += kotlin.math.abs(ms[k].z - base) to kotlin.math.abs(ms[k].z - personal)
            s = f.update(detrain(s, day), day, ms[k].z, ms[k].r)
        }
        return s to errors
    }

    private fun gapDays(ms: List<Measurement>): Double {
        if (ms.size < 2) return 4.0
        val gaps = ms.zipWithNext { a, b -> (b.time - a.time) / DAY }.filter { it > 0.25 }
        return Gate.median(gaps).coerceIn(2.0, 14.0).takeIf { gaps.isNotEmpty() } ?: 4.0
    }

    /** Au-delà de deux semaines sans l'exercice : −0,4 % par semaine (−10 % au plus), pente divisée par deux chaque mois. */
    private fun detrain(s: LevelTrend.State, day: Double): LevelTrend.State {
        val idle = day - s.day
        if (idle <= 14) return s
        val weeks = idle / 7
        val loss = (0.004 * (weeks - 2)).coerceAtMost(0.10)
        return s.copy(level = s.level + ln(1 - loss), slope = s.slope * Math.pow(0.5, idle / 30))
    }

    /** Confiance accordée au filtre, mesurée sur toutes les séances de tous les exercices (blocs de 8 séances). */
    fun trust(history: List<Workout>, db: ExerciseDb, experience: Experience, bias: Double, before: Long = Long.MAX_VALUE): Double {
        val errors = mutableListOf<Pair<Long, Pair<Double, Double>>>()
        val past = history.asSequence().distinctBy { it.id }.filter { it.startTime < before }.sortedBy { it.startTime }.toList()
        val byGym = past.groupBy { it.gymId ?: it.gymName }.values
        for (id in past.flatMap { w -> w.exercises.map { it.exerciseId } }.distinct()) {
            val e = db.exercise(id)?.takeIf { it.usesReps && it.usesWeight && "aw" !in it.metrics } ?: continue
            for (ws in byGym) {
                val ms = seriesIn(e, ws.asSequence(), bias)
                if (ms.size < 2) continue
                replay(ms, experience).second.forEachIndexed { k, pair -> errors += ms[k + 1].time to pair }
            }
        }
        return Gate(blockSize = 8).evaluate(errors.sortedBy { it.first }.map { it.second }).weight
    }

    /**
     * Progrès par séance et son incertitude (fractions), toutes salles confondues, pour l'indice du Groupe. Null sous
     * quatre séances de l'exercice avant [before].
     */
    fun trend(exercise: Exercise, history: List<Workout>, experience: Experience, bias: Double, before: Long): Pair<Double, Double>? {
        if (!exercise.usesReps || !exercise.usesWeight || "aw" in exercise.metrics) return null
        val ms = seriesIn(exercise, history.asSequence().distinctBy { it.id }.filter { it.startTime < before }.sortedBy { it.startTime }, bias)
        if (ms.size < 4) return null
        val state = replay(ms, experience).first ?: return null
        val gap = gapDays(ms)
        return state.slope * gap to state.slopeSd * gap
    }

    /** Fusion bayésienne de l'estimation d'ouverture et des séries déjà faites dans la séance (log, variances). */
    fun combine(openingLog: Double?, openingVar: Double, session: Measurement): Double =
        if (openingLog == null) session.z
        else (openingLog / openingVar + session.z / session.r) / (1 / openingVar + 1 / session.r)

    /**
     * Force d'ouverture attendue à [now]. [trust] vient de [trust] (calculé une fois par séance). Null sans historique.
     */
    fun estimate(exercise: Exercise, history: List<Workout>, gym: Gym?, experience: Experience, bias: Double, trust: Double,
                 now: Long = System.currentTimeMillis()): Estimate? {
        val ms = series(exercise, history, gym, bias, now)
        if (ms.isEmpty()) return null
        val (state, _) = replay(ms, experience)
        state ?: return null
        val gap = gapDays(ms)
        val f = filterFor(gap)
        val day = now / DAY
        val predicted = f.predict(detrain(state, day), day)
        val base = ms.last().z + ln(Strength.SESSION_PROGRESS)
        val level = Gate.blend(base, predicted.level, trust)
        val perSession = exp(state.slope * gap) - 1
        val plateau = ms.size >= 6 && perSession < 0.0005 && state.slopeSd * gap < 0.003
        return Estimate(exp(level), predicted.levelSd, perSession, ms.size, plateau, trust)
    }
}
