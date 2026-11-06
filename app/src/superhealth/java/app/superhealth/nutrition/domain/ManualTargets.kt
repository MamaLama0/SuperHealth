package app.superhealth.nutrition.domain

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
            val day = an.today + ((dow - java.time.LocalDate.ofEpochDay(an.today).dayOfWeek.value + 7) % 7)
            an.targetFor(day).macros.let { DayTarget(it.kcal, it.protein, it.fat, it.carbs) }
        }
        val current = an.targetFor(an.today).macros
        return CheckIn(an.today, an.currentTdee, an.checkInFor(an.today)?.trendKg, current.kcal, current.protein, current.fat, current.carbs,
            an.currentGoal?.type ?: GoalType.MAINTENANCE, an.currentGoal?.ratePctPerWeek ?: 0.0, days = days)
    }

    fun base(p: Program) = DayTarget(p.manualKcal, p.manualProtein, p.manualFat, p.manualCarbs)
    fun forDay(p: Program, dow: Int) = p.manualDays.getOrNull(dow - 1) ?: base(p)
    /** Les 7 jours du mode ON / OFF : cible ON les jours [on], cible [off] les autres. */
    fun onOffWeek(on: DayTarget, off: DayTarget, onDays: Set<Int>) = (1..7).map { if (it in onDays) on else off }

    /** Répartition des cibles manuelles sur la semaine. */
    enum class Split { SAME, ON_OFF, DAYS }

    fun split(p: Program) = when { p.manualOff != null -> Split.ON_OFF; p.manualDays.size == 7 -> Split.DAYS; else -> Split.SAME }

    /** Jour OFF proposé : mêmes protéines et lipides, 15 % d'énergie en moins prise sur les glucides. */
    fun defaultOff(on: DayTarget): DayTarget {
        val cut = kotlin.math.round(on.kcal * 0.15 / 10) * 10
        return fromMacros(on.protein, on.fat, (on.carbs - cut / 4).coerceAtLeast(0.0))
    }

    /** Passe d'une répartition à l'autre en gardant ce qui a été saisi ; les 7 jours sont toujours matérialisés dans manualDays. */
    fun withSplit(p: Program, split: Split): Program = when (split) {
        Split.SAME -> p.copy(manualDays = emptyList(), manualOff = null)
        Split.ON_OFF -> {
            val off = p.manualOff ?: defaultOff(base(p))
            val on = p.manualOnDays.ifEmpty { p.highDays.ifEmpty { setOf(1, 3, 5) } }
            p.copy(manualOff = off, manualOnDays = on, manualDays = onOffWeek(base(p), off, on))
        }
        Split.DAYS -> p.copy(manualDays = (1..7).map { forDay(p, it) }, manualOff = null)
    }

    /** Cible commune (ou des jours ON) ; en mode ON / OFF la semaine suit. */
    fun withBase(p: Program, t: DayTarget): Program = p.copy(manualKcal = t.kcal, manualProtein = t.protein, manualFat = t.fat, manualCarbs = t.carbs).let {
        if (it.manualOff != null) it.copy(manualDays = onOffWeek(t, it.manualOff, it.manualOnDays)) else it
    }

    fun withOff(p: Program, t: DayTarget) = p.copy(manualOff = t, manualDays = onOffWeek(base(p), t, p.manualOnDays))

    fun withOnDays(p: Program, days: Set<Int>) = p.copy(manualOnDays = days, manualDays = onOffWeek(base(p), p.manualOff ?: base(p), days))

    fun withDay(p: Program, dow: Int, t: DayTarget) = p.copy(manualDays = (1..7).map { if (it == dow) t else forDay(p, it) })
    /** Un nouveau remplacement ferme celui en cours, sans réécrire les jours passés. */
    fun replaceOverride(p: Program, next: TargetOverride): Program = p.copy(overrides = p.overrides.mapNotNull {
        if (!it.includes(next.startDay)) it else if (it.startDay < next.startDay) it.copy(endDay = next.startDay - 1) else null
    } + next)

    fun overrideAt(p: Program, day: Long) = p.overrides.lastOrNull { it.includes(day) }?.target
}
