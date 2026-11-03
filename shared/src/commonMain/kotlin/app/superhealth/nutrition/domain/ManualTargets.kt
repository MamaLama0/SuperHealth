package app.superhealth.nutrition.domain

import app.superhealth.platform.*

/** Conversions des cibles saisies. Les pourcentages sont des parts d'énergie, pas de grammes. */
object ManualTargets {
    fun fromMacros(protein: Double, fat: Double, carbs: Double): DayTarget {
        require(listOf(protein, fat, carbs).all { it.isFinite() && it >= 0 })
        return DayTarget(4 * protein + 9 * fat + 4 * carbs, protein, fat, carbs)
    }

    fun fromCalories(kcal: Double, proteinPct: Double, fatPct: Double, carbsPct: Double): DayTarget {
        require(kcal.isFinite() && kcal > 0)
        val shares = listOf(proteinPct, fatPct, carbsPct)
        require(shares.all { it.isFinite() && it >= 0 } && kotlin.math.abs(shares.sum() - 100) < 0.01)
        return DayTarget(kcal, kcal * proteinPct / 400, kcal * fatPct / 900, kcal * carbsPct / 400)
    }

    fun scale(kcal: Double, base: DayTarget): DayTarget {
        val energy = fromMacros(base.protein, base.fat, base.carbs).kcal
        return if (energy > 0) DayTarget(kcal, base.protein * kcal / energy, base.fat * kcal / energy, base.carbs * kcal / energy)
        else fromCalories(kcal, 30.0, 30.0, 40.0)
    }

    fun keepCurrent(an: app.superhealth.nutrition.data.Analytics): CheckIn {
        val days = (1..7).map { dow ->
            val day = an.today + ((dow - app.superhealth.platform.LocalDate.ofEpochDay(an.today).dayOfWeek.value + 7) % 7)
            an.targetFor(day).macros.let { DayTarget(it.kcal, it.protein, it.fat, it.carbs) }
        }
        val current = an.targetFor(an.today).macros
        return CheckIn(an.today, an.currentTdee, an.checkInFor(an.today)?.trendKg, current.kcal, current.protein, current.fat, current.carbs,
            an.currentGoal?.type ?: GoalType.MAINTENANCE, an.currentGoal?.ratePctPerWeek ?: 0.0, days = days)
    }

    fun base(p: Program) = DayTarget(p.manualKcal, p.manualProtein, p.manualFat, p.manualCarbs)
    fun forDay(p: Program, dow: Int) = p.manualDays.getOrNull(dow - 1) ?: base(p)
    /** Un nouveau remplacement ferme celui en cours, sans réécrire les jours passés. */
    fun replaceOverride(p: Program, next: TargetOverride): Program = p.copy(overrides = p.overrides.mapNotNull {
        if (!it.includes(next.startDay)) it else if (it.startDay < next.startDay) it.copy(endDay = next.startDay - 1) else null
    } + next)

    fun overrideAt(p: Program, day: Long) = p.overrides.lastOrNull { it.includes(day) }?.target
}
