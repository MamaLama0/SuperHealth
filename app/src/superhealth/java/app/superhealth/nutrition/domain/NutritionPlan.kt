package app.superhealth.nutrition.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/** Phase d'une semaine du plan (préparation physique). */
@Serializable enum class PrepPhase(val label: String) {
    BULK("Prise de masse"), CUT("Sèche"), MAINTENANCE("Maintien"), DIET_BREAK("Pause diète"),
    REVERSE("Reverse diet"), PEAK_WEEK("Semaine de pic"),
}

/**
 * Un jour du plan : ses macros et des repères facultatifs (fibres, sodium, eau) qui remplacent, ce jour-là, ceux calculés
 * par l'app (objectif d'eau automatique, plafond de sodium…).
 */
@Serializable data class PlanDay(
    val target: DayTarget,
    val fiberG: Double? = null,
    val sodiumMg: Double? = null,
    val waterMl: Double? = null,
    /** Jour de recharge (refeed) : glucides relevés, simple étiquette. */
    val refeed: Boolean = false,
    val note: String = "",
)

/**
 * Une semaine du plan, du lundi [startDay] au dimanche : sept jours réglés un par un. Une semaine future est enregistrée
 * mais ne s'applique qu'à partir de son lundi ; elle prime alors sur les check-ins et sur les cibles manuelles (seuls les
 * remplacements datés passent avant).
 */
@Serializable data class PlanWeek(
    val startDay: Long,
    val phase: PrepPhase = PrepPhase.CUT,
    val days: List<PlanDay>,
    val note: String = "",
) {
    fun includes(day: Long) = day in startDay until startDay + 7
    fun day(epochDay: Long): PlanDay? = days.getOrNull((epochDay - startDay).toInt())
}

/**
 * Plan semaine par semaine (3.8). Repères de la préparation des culturistes naturels (Helms, Aragon et Fitschen 2014,
 * JISSN) : perte de 0,5 à 1 % du poids par semaine, protéines 2,3 à 3,1 g/kg de masse maigre, lipides 15 à 30 % de
 * l'énergie, glucides pour le reste ; jours de recharge et pauses diète pour tenir la durée. Semaine de pic (Escalante et
 * al. 2021, BMC Sports Sci Med Rehabil) : recharge en glucides les derniers jours, sans couper l'eau ni le sodium — la
 * déshydratation volontaire n'améliore pas l'apparence et expose à des malaises.
 */
object NutritionPlan {
    fun monday(day: Long) = day - LocalDate.ofEpochDay(day).dayOfWeek.value + 1
    /** Lundi de la semaine prochaine. */
    fun nextMonday(today: Long) = monday(today) + 7

    fun weekAt(p: Program, day: Long): PlanWeek? = p.plan.lastOrNull { it.includes(day) }
    fun dayAt(p: Program, day: Long): PlanDay? = weekAt(p, day)?.day(day)

    /** Ajoute ou remplace la semaine qui commence au même lundi. */
    fun put(p: Program, w: PlanWeek): Program = p.copy(plan = (p.plan.filterNot { it.startDay == w.startDay } + w).sortedBy { it.startDay })
    fun remove(p: Program, startDay: Long): Program = p.copy(plan = p.plan.filterNot { it.startDay == startDay })

    /** Semaine reprenant sept cibles données (celles en vigueur, ou une autre semaine). */
    fun weekFrom(startDay: Long, targets: List<DayTarget>, phase: PrepPhase = PrepPhase.CUT) =
        PlanWeek(startDay, phase, (0 until 7).map { PlanDay(targets.getOrElse(it) { targets.last() }) })

    /** Même énergie déplacée : ±[deltaKcal] pris sur les glucides de chaque jour (protéines et lipides gardés). */
    fun shift(w: PlanWeek, deltaKcal: Double): PlanWeek = w.copy(days = w.days.map { d ->
        val t = d.target
        val carbs = (t.carbs + deltaKcal / 4).coerceAtLeast(0.0)
        d.copy(target = ManualTargets.fromMacros(t.protein, t.fat, carbs))
    })

    data class Prep(
        val start: Long,
        val weeks: Int,
        val startKcal: Double,
        /** Énergie retirée chaque semaine de sèche (ajoutée en reverse). */
        val stepKcal: Double,
        val maintenanceKcal: Double,
        val weightKg: Double,
        val leanKg: Double?,
        val proteinPerKgLean: Double = 2.6,
        val fatPct: Double = 22.0,
        /** Jours de recharge (1 = lundi … 7 = dimanche) à l'énergie de maintien, surplus en glucides. */
        val refeedDays: Set<Int> = setOf(6),
        /** Pause diète (une semaine au maintien) toutes les N semaines ; 0 = jamais. */
        val dietBreakEvery: Int = 0,
        val peakWeek: Boolean = false,
        val reverseWeeks: Int = 0,
        val floorKcal: Double = 1200.0,
    )

    private fun macros(kcal: Double, protein: Double, fatPct: Double): DayTarget {
        val fat = kcal * fatPct / 900
        val carbs = ((kcal - 4 * protein - 9 * fat) / 4).coerceAtLeast(0.0)
        return ManualTargets.fromMacros(protein.round1(), fat.round1(), carbs.round1())
    }

    private fun Double.round1() = (this * 10).roundToInt() / 10.0

    /** Prépare [Prep.weeks] semaines de sèche (avec pauses et recharges), puis la semaine de pic et le reverse demandés. */
    fun generate(g: Prep): List<PlanWeek> {
        val protein = (g.leanKg ?: (g.weightKg * 0.85)) * g.proteinPerKgLean
        val out = ArrayList<PlanWeek>()
        var kcal = g.startKcal
        var cutIndex = 0
        var week = 0
        while (cutIndex < g.weeks) {
            val start = g.start + 7L * week
            val pause = g.dietBreakEvery > 0 && cutIndex > 0 && cutIndex % g.dietBreakEvery == 0 &&
                out.lastOrNull()?.phase != PrepPhase.DIET_BREAK
            if (pause) {
                val t = macros(g.maintenanceKcal, protein, max(g.fatPct, 25.0))
                out += PlanWeek(start, PrepPhase.DIET_BREAK, List(7) { PlanDay(t) }, "Pause au maintien : récupération et adhésion")
            } else {
                val day = macros(max(kcal, g.floorKcal), protein, g.fatPct)
                val refeed = macros(g.maintenanceKcal, protein, 15.0)
                out += PlanWeek(start, PrepPhase.CUT, (1..7).map { dow ->
                    if (dow in g.refeedDays && g.maintenanceKcal > day.kcal) PlanDay(refeed, refeed = true) else PlanDay(day)
                })
                kcal -= g.stepKcal
                cutIndex++
            }
            week++
        }
        if (g.peakWeek) {
            val start = g.start + 7L * week++
            val base = macros(max(kcal + g.stepKcal, g.floorKcal), protein, g.fatPct)
            // Recharge en glucides sur les trois derniers jours (~8 g/kg), lipides bas ; eau et sodium habituels.
            val load = ManualTargets.fromMacros(protein.round1(), (g.weightKg * 0.6).round1(), (g.weightKg * 8).round1())
            out += PlanWeek(start, PrepPhase.PEAK_WEEK, (1..7).map { dow -> if (dow >= 5) PlanDay(load, refeed = true, note = "Recharge en glucides") else PlanDay(base) },
                "Garder l'eau et le sel habituels : les couper n'aide pas")
        }
        var r = kcal + g.stepKcal
        repeat(g.reverseWeeks) {
            val start = g.start + 7L * week++
            r = minOf(r + g.stepKcal, g.maintenanceKcal)
            val t = macros(r, protein, g.fatPct)
            out += PlanWeek(start, PrepPhase.REVERSE, List(7) { PlanDay(t) })
        }
        return out
    }
}
