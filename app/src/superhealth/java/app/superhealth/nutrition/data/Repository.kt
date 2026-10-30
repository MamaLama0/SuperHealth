package app.superhealth.nutrition.data

import androidx.room.withTransaction
import app.superhealth.nutrition.domain.Bmr
import app.superhealth.nutrition.domain.WeightTrendV2
import app.superhealth.nutrition.domain.TrendVersion
import app.superhealth.nutrition.domain.TdeeMode
import app.superhealth.nutrition.domain.ProgramStyle
import app.superhealth.nutrition.domain.ExpenditureVersion
import app.superhealth.nutrition.domain.ExpenditureV3
import app.superhealth.nutrition.domain.Goal
import app.superhealth.nutrition.domain.GoalStatus
import app.superhealth.nutrition.domain.CheckIn
import app.superhealth.nutrition.domain.DayData
import app.superhealth.nutrition.domain.Expenditure
import app.superhealth.nutrition.domain.Macros
import app.superhealth.nutrition.domain.Profile
import app.superhealth.nutrition.domain.Program
import app.superhealth.nutrition.domain.Settings
import app.superhealth.nutrition.domain.Targets
import app.superhealth.nutrition.domain.WeightTrend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Tendance d'un jour (V1 ou V2). */
data class TrendDay(val epochDay: Long, val scale: Double?, val trend: Double, val tween: Double, val sd: Double)

/** Dépense d'un jour (V1 ou V3). */
data class ExpDay(
    val epochDay: Long, val tdee: Double, val short: Double, val long: Double,
    val status: Expenditure.Status, val goalAdjustment: Double = 0.0, val missingDays: Double = 0.0,
)

enum class CheckInState { NONE, FUTURE, NOW }

/** Résultat agrégé de toutes les analyses, recalculé à chaque changement de données. */
data class Analytics(
    val today: Long,
    val days: List<DayData>,
    val trend: List<TrendDay>,
    val expenditure: List<ExpDay>,
    val initialTdee: Double,
    val currentTrendKg: Double?,
    val lbmKg: Double?,
    val profile: Profile,
    val program: Program,
    val settings: Settings,
    val goals: List<Goal>,
    val checkIns: List<CheckIn>,
) {
    val trendByDay: Map<Long, Double> = trend.associate { it.epochDay to it.trend }
    val expenditureByDay: Map<Long, Double> = expenditure.associate { it.epochDay to it.tdee }
    val currentTdee: Double get() = tdeeAt(today)

    fun tdeeAt(day: Long): Double = when (program.tdeeMode) {
        TdeeMode.STATIC -> program.staticTdee
        else -> expenditure.lastOrNull { it.epochDay <= day }?.tdee ?: initialTdee
    }

    /** Objectif actif à une date (le dernier commencé). */
    fun goalAt(day: Long): Goal? = goals.lastOrNull { it.startDay <= day && (it.closedDay == null || it.closedDay >= day) }
        ?: goals.lastOrNull { it.startDay <= day }
    val currentGoal: Goal? get() = goalAt(today)

    fun lbmAt(day: Long): Double {
        val w = trendByDay[day] ?: currentTrendKg
        val bf = days.lastOrNull { it.epochDay <= day && it.bodyFatPct != null }?.bodyFatPct ?: profile.bodyFatPct
        return Bmr.leanMassOrDefault(w, bf, profile.sex, profile.heightCm, LocalDate.ofEpochDay(day).year - profile.birthYear)
    }

    /** Cibles de la semaine en cours : le programme en vigueur aujourd'hui, appliqué à chaque jour (jours hauts compris). */
    fun weekTarget(epochDay: Long): Targets.DailyTarget {
        val c = checkInFor(today) ?: checkIns.firstOrNull() ?: return targetFor(epochDay)
        if (program.style == ProgramStyle.MANUAL) return targetFor(epochDay)
        val t = c.forDayOfWeek(LocalDate.ofEpochDay(epochDay).dayOfWeek.value)
        return Targets.DailyTarget(Macros(t.kcal, t.protein, t.fat, t.carbs), c.tdee, c.limitedByFloor, c.day)
    }

    /** Check-in en vigueur à une date. */
    fun checkInFor(day: Long): CheckIn? = checkIns.lastOrNull { it.day <= day }

    /** Cible du jour : celle du dernier check-in ; sinon calcul provisoire. */
    /**
     * Cible du jour : celle du check-in en vigueur. Avant le premier check-in (jours de la semaine d'inscription),
     * on reprend ce premier check-in plutôt qu'un calcul provisoire jour par jour — sinon le jour du premier
     * check-in ressort avec une cible différente des autres.
     */
    fun targetFor(epochDay: Long): Targets.DailyTarget {
        val c = checkInFor(epochDay) ?: checkIns.firstOrNull()
        val dow = LocalDate.ofEpochDay(epochDay).dayOfWeek.value
        if (c != null && program.style != ProgramStyle.MANUAL) {
            val t = c.forDayOfWeek(dow)
            return Targets.DailyTarget(Macros(t.kcal, t.protein, t.fat, t.carbs), c.tdee, c.limitedByFloor, c.day)
        }
        val ci = provisionalCheckIn(epochDay)
        val t = ci.forDayOfWeek(dow)
        return Targets.DailyTarget(Macros(t.kcal, t.protein, t.fat, t.carbs), ci.tdee, ci.limitedByFloor, null)
    }

    /** Ce que donnerait un check-in calculé ce jour-là. */
    fun provisionalCheckIn(day: Long): CheckIn {
        val w = trendByDay[day] ?: currentTrendKg ?: DEFAULT_WEIGHT
        return Targets.checkIn(day, tdeeAt(day), w, lbmAt(day), program, goalAt(day), profile.heightCm,
            java.time.LocalDate.ofEpochDay(day).year - profile.birthYear, profile.sex == app.superhealth.nutrition.domain.Sex.FEMALE)
    }

    /** Prochain check-in : prochain jour de check-in choisi, strictement après le dernier check-in (ou le début du programme). */
    val nextCheckInDay: Long get() {
        val from = checkIns.lastOrNull()?.day ?: program.startEpochDay ?: today
        var d = LocalDate.ofEpochDay(from + 1)
        while (d.dayOfWeek.value != program.checkInDay) d = d.plusDays(1)
        return d.toEpochDay()
    }

    val checkInState: CheckInState get() = when {
        program.style == ProgramStyle.MANUAL -> CheckInState.NONE
        today >= nextCheckInDay -> CheckInState.NOW
        else -> CheckInState.FUTURE
    }

    companion object { const val DEFAULT_WEIGHT = 75.0 }
}

@Serializable
data class Backup(
    val version: Int = 3,
    val exportedAt: Long = System.currentTimeMillis(),
    val profile: Profile,
    val program: Program,
    val settings: Settings,
    val foods: List<FoodEntity>,
    val entries: List<FoodEntryEntity>,
    val weighIns: List<WeighInEntity>,
    val dayFlags: List<DayFlagEntity>,
    val goals: List<Goal> = emptyList(),
    val checkIns: List<CheckIn> = emptyList(),
    val recipeItems: List<RecipeItemEntity> = emptyList(),
    val bodyMetrics: List<BodyMetricEntity> = emptyList(),
)

class Repository(private val db: AppDatabase, val prefs: Prefs) {
    val foods = db.foods()
    val entries = db.entries()
    val weighIns = db.weighIns()
    val dayFlags = db.dayFlags()
    val recipes = db.recipes()
    val body = db.body()

    fun entriesForDay(day: Long) = entries.forDay(day)

    /** Journalise [grams] de [food] ; renvoie l'id de l'entrée (pour l'annuler). */
    suspend fun logFood(day: Long, minute: Int, food: FoodEntity, grams: Double): Long {
        val f = grams / 100.0
        val id = entries.insert(
            FoodEntryEntity(
                epochDay = day, meal = mealFor(minute), minuteOfDay = minute, foodId = food.id.takeIf { it != 0L }, name = food.name,
                grams = grams, kcal = food.kcal100 * f, protein = food.protein100 * f, fat = food.fat100 * f, carbs = food.carbs100 * f,
                fiber = food.fiber100 * f,
                microsJson = Micros.encode(Micros.of(food.microsJson, food.name, food.sugars100, food.satFat100, food.sodiumMg100).mapValues { it.value * f }),
            )
        )
        if (food.id != 0L) foods.markUsed(food.id)
        return id
    }

    /** Aliment estimé (coach) : quantité facultative, valeurs déjà pour la portion. */
    suspend fun logEstimated(day: Long, minute: Int, name: String, grams: Double?, m: Macros): Long =
        entries.insert(FoodEntryEntity(epochDay = day, meal = mealFor(minute), minuteOfDay = minute, name = name, grams = grams,
            kcal = m.kcal, protein = m.protein, fat = m.fat, carbs = m.carbs))

    suspend fun quickAdd(day: Long, minute: Int, name: String, m: Macros): Long =
        entries.insert(FoodEntryEntity(epochDay = day, meal = mealFor(minute), minuteOfDay = minute, name = name,
            kcal = m.kcal, protein = m.protein, fat = m.fat, carbs = m.carbs))

    /** Copie les entrées d'un jour vers un autre en conservant les heures. */
    suspend fun copyDay(from: Long, to: Long) {
        entries.all().filter { it.epochDay == from }.forEach { entries.insert(it.copy(id = 0, epochDay = to, createdAt = System.currentTimeMillis())) }
    }

    /** Colle des entrées (presse-papiers) sur un jour ; [minute] < 0 = heures d'origine conservées. */
    suspend fun paste(items: List<FoodEntryEntity>, day: Long, minute: Int = -1) {
        val now = System.currentTimeMillis()
        items.forEach { entries.insert(it.copy(id = 0, epochDay = day, minuteOfDay = if (minute >= 0) minute else it.minuteOfDay, createdAt = now)) }
    }

    // ----- Recettes -----

    /** Enregistre une recette : valeurs pour 100 g = somme des ingrédients / poids total (poids cuit si saisi). */
    suspend fun saveRecipe(recipe: FoodEntity, items: List<RecipeItemEntity>, servings: Double, totalGrams: Double?): FoodEntity {
        val raw = items.sumOf { it.grams }
        val total = (totalGrams?.takeIf { it > 0 } ?: raw).coerceAtLeast(1.0)
        val k = 100.0 / total
        val micros = Micros.sum(items.map { it.microsJson })
        val food = recipe.copy(
            source = RECIPE, servingName = "portion", recipeServings = servings, servingGrams = total / servings.coerceAtLeast(1.0),
            kcal100 = items.sumOf { it.kcal } * k, protein100 = items.sumOf { it.protein } * k, fat100 = items.sumOf { it.fat } * k,
            carbs100 = items.sumOf { it.carbs } * k, fiber100 = items.sumOf { it.fiber } * k,
            microsJson = Micros.encode(micros.mapValues { it.value * k }),
        )
        return db.withTransaction {
            val id = foods.insert(food)
            recipes.deleteFor(id)
            recipes.insertAll(items.mapIndexed { i, it -> it.copy(id = 0, recipeId = id, position = i) })
            food.copy(id = id)
        }
    }

    suspend fun deleteRecipe(id: Long) = db.withTransaction { recipes.deleteFor(id); foods.delete(id) }

    /** « Exploser » une recette : journalise chaque ingrédient, au prorata de la quantité choisie. */
    suspend fun logRecipeIngredients(day: Long, minute: Int, recipe: FoodEntity, grams: Double): List<Long> {
        val items = recipes.itemsFor(recipe.id)
        val total = recipe.servingGrams * (recipe.recipeServings ?: 1.0)
        val f = grams / total.coerceAtLeast(1.0)
        val ids = items.map {
            entries.insert(FoodEntryEntity(epochDay = day, meal = mealFor(minute), minuteOfDay = minute, foodId = it.foodId, name = it.name,
                grams = it.grams * f, kcal = it.kcal * f, protein = it.protein * f, fat = it.fat * f, carbs = it.carbs * f, fiber = it.fiber * f,
                microsJson = Micros.scale(it.microsJson, f)))
        }
        foods.markUsed(recipe.id)
        return ids
    }

    /** Repas déduit de l'heure (pour l'export CSV, compatible avec la v1). */
    private fun mealFor(minute: Int) = when (minute / 60) {
        in 0..10 -> 0
        in 11..14 -> 1
        in 18..23 -> 2
        else -> 3
    }

    /** Enregistre un aliment OFF dans la bibliothèque (dédupliqué par code-barres). */
    suspend fun saveFood(food: FoodEntity): FoodEntity {
        val existing = food.barcode?.let { foods.byBarcode(it) }
            ?: if (food.source == SwissFoods.SOURCE) foods.byNameSource(food.name, food.source) else null
        if (existing != null && food.id == 0L) return existing
        val id = foods.insert(food)
        return food.copy(id = id)
    }

    /** Migration v2 → v3 : l'objectif stocké dans Program devient un Goal daté. */
    fun migrateGoals() {
        if (prefs.goals.value.isNotEmpty() || !prefs.settings.value.onboarded) return
        val p = prefs.program.value
        val start = p.startEpochDay ?: LocalDate.now().toEpochDay()
        prefs.setGoals(listOf(Goal(startDay = start, type = p.goalType, startWeightKg = p.targetWeightKg ?: Analytics.DEFAULT_WEIGHT,
            targetWeightKg = p.targetWeightKg, ratePctPerWeek = p.ratePctPerWeek)))
    }

    /** Démarre un nouvel objectif (l'objectif en cours est clos la veille). */
    fun startGoal(goal: Goal) {
        val closed = prefs.goals.value.map {
            if (it.status == GoalStatus.IN_PROGRESS && it.startDay < goal.startDay) it.copy(status = GoalStatus.COMPLETED, closedDay = goal.startDay - 1)
            else it
        }.filter { it.startDay != goal.startDay }
        prefs.setGoals(closed + goal)
    }

    fun updateGoal(goal: Goal) = prefs.setGoals(prefs.goals.value.map { if (it.id == goal.id) goal else it })

    fun deleteGoal(goal: Goal) = prefs.setGoals(prefs.goals.value.filter { it.id != goal.id })

    /** Enregistre un check-in (remplace un éventuel check-in du même jour). */
    fun saveCheckIn(c: CheckIn) = prefs.setCheckIns(prefs.checkIns.value.filter { it.day != c.day } + c)

    fun analytics(): Flow<Analytics> = combine(
        entries.allFlow(), weighIns.allFlow(), dayFlags.allFlow(),
        combine(prefs.profile, prefs.program, prefs.settings) { a, b, c -> Triple(a, b, c) },
        combine(prefs.goals, prefs.checkIns, body.metricsFlow()) { g, c, m -> Triple(g, c, m) },
    ) { e, w, f, (profile, program, settings), (goals, checkIns, metrics) ->
        val steps = metrics.filter { it.metric == STEPS }.associate { it.epochDay to it.value }
        computeAnalytics(e, w, f, profile, program, settings, goals, checkIns, LocalDate.now().toEpochDay(), steps)
    }.flowOn(Dispatchers.Default)

    suspend fun backup() = Backup(
        profile = prefs.profile.value, program = prefs.program.value, settings = prefs.settings.value,
        foods = foods.all(), entries = entries.all(), weighIns = weighIns.all(), dayFlags = dayFlags.all(),
        goals = prefs.goals.value, checkIns = prefs.checkIns.value,
        recipeItems = recipes.all(), bodyMetrics = body.metrics(),
    )

    suspend fun restore(b: Backup) {
        db.withTransaction {
            foods.clear(); entries.clear(); weighIns.clear(); dayFlags.clear(); recipes.clear(); body.clearMetrics()
            foods.upsertAll(b.foods); entries.upsertAll(b.entries); weighIns.upsertAll(b.weighIns); dayFlags.upsertAll(b.dayFlags)
            recipes.upsertAll(b.recipeItems); body.upsertMetrics(b.bodyMetrics)
        }
        prefs.setProfile(b.profile); prefs.setProgram(b.program); prefs.setSettings(b.settings)
        prefs.setGoals(b.goals); prefs.setCheckIns(b.checkIns)
    }

    suspend fun wipe() {
        db.withTransaction {
            foods.clear(); entries.clear(); weighIns.clear(); dayFlags.clear(); recipes.clear(); body.clearMetrics(); body.clearPhotos()
        }
        prefs.setProfile(Profile()); prefs.setProgram(Program()); prefs.setSettings(Settings())
        prefs.setGoals(emptyList()); prefs.setCheckIns(emptyList())
    }

    companion object {
        const val RECIPE = "recipe"
        /** Pas quotidiens : rangés dans body_metrics sous ce code (pas de migration). */
        const val STEPS = "steps"

        fun computeAnalytics(
            entries: List<FoodEntryEntity>, weighIns: List<WeighInEntity>, flags: List<DayFlagEntity>,
            profile: Profile, program: Program, settings: Settings, goals: List<Goal>, checkIns: List<CheckIn>, today: Long,
            steps: Map<Long, Double> = emptyMap(),
        ): Analytics {
            val intake = entries.groupBy { it.epochDay }.mapValues { (_, v) -> v.sumOf { it.kcal } }
            val weights = weighIns.associate { it.epochDay to it.weightKg }
            val bodyFat = weighIns.filter { it.bodyFatPct != null }.associate { it.epochDay to it.bodyFatPct!! }
            val flagMap = flags.associateBy { it.epochDay }
            val firstData = listOfNotNull(intake.keys.minOrNull(), weights.keys.minOrNull()).minOrNull()
            val start = program.startEpochDay ?: firstData ?: today
            val days = (minOf(start, today)..today).map { d ->
                DayData(d, intake[d], weights[d], bodyFat[d], flagMap[d]?.fasted == true, flagMap[d]?.partial == true, steps[d])
            }
            val firstWeigh = weights.keys.minOrNull()
            // Algorithmes les plus précis seulement : tendance V2 (Kalman) et dépense V3 (plus de choix dans les réglages).
            val trend: List<TrendDay> = if (weights.isEmpty()) emptyList()
                else WeightTrendV2.compute(weights, today).map { TrendDay(it.epochDay, it.scale, it.trend, it.tween, it.trendSd) }
            val trendMap = trend.associate { it.epochDay to it.trend }
            val age = LocalDate.ofEpochDay(today).year - profile.birthYear
            val refWeight = weighIns.firstOrNull()?.weightKg ?: Analytics.DEFAULT_WEIGHT
            val refBf = bodyFat.values.firstOrNull() ?: profile.bodyFatPct
            val goalEvents = goals.map { ExpenditureV3.GoalEvent(it.startDay, it.type, it.ratePctPerWeek) }
            val firstGoal = goalEvents.firstOrNull()
            val initial = program.initialTdeeOverride ?: ExpenditureV3.initialTdee(
                Bmr.bmr(profile, refWeight, refBf, age), profile.activity.factor, profile.exercise.bonus, firstGoal,
                settings.predictiveGoalAdjustment,
            )
            val exp: List<ExpDay> = when {
                weights.isEmpty() -> emptyList()
                else -> {
                    val tweenByDay = trend.associate { it.epochDay to it.tween }
                    val v3Days = days.filter { it.epochDay >= firstWeigh!! }.map { d ->
                        ExpenditureV3.Day(d.epochDay, tweenByDay[d.epochDay] ?: refWeight, d.weightKg, d.intakeKcal, d.fasted, d.partial, d.steps)
                    }
                    ExpenditureV3.compute(v3Days, initial, goalEvents, settings.predictiveGoalAdjustment,
                        useSteps = settings.useSteps && steps.isNotEmpty()).mapIndexed { i, p ->
                        val st = when { i < 3 -> Expenditure.Status.INITIAL; p.paused -> Expenditure.Status.HOLDING; else -> Expenditure.Status.UPDATING }
                        ExpDay(p.epochDay, p.tdee, p.short, p.long, st, p.goalAdjustment, p.missingDays)
                    }
                }
            }
            val currentTrend = trend.lastOrNull { it.epochDay <= today }?.trend
            val lastBf = bodyFat.entries.maxByOrNull { it.key }?.value ?: profile.bodyFatPct
            val lbm = currentTrend?.let { Bmr.leanMassOrDefault(it, lastBf, profile.sex, profile.heightCm, age) }
            return Analytics(today, days, trend, exp, initial, currentTrend, lbm, profile, program, settings, goals, checkIns)
        }
    }
}
