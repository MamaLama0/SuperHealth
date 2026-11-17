package app.superhealth.notch

import android.content.Context
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.Metric
import app.superhealth.meditation.Meditation
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.domain.stepGoalFor
import app.superhealth.training.WorkoutApp
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.first

/**
 * Résumé du jour lu par Notch (son assistant, et les anneaux de l'île) : mêmes dépôts que les widgets « Ma journée »
 * et « Aperçu ». Les colonnes sont celles de SuperHealthSummary côté Notch ; une valeur absente (pas de montre,
 * rien de saisi) reste null plutôt que 0, pour que l'assistant ne dise pas « 0 pas ».
 */
object NotchSummary {
    const val DATE = "date"
    const val STEPS = "steps"
    const val STEP_GOAL = "step_goal"
    const val KCAL_IN = "kcal_in"
    const val KCAL_TARGET = "kcal_target"
    const val KCAL_OUT = "kcal_out"
    const val PROTEIN_G = "protein_g"
    const val PROTEIN_TARGET_G = "protein_target_g"
    const val WATER_ML = "water_ml"
    const val SLEEP_SCORE = "sleep_score"
    const val SLEEP_MIN = "sleep_min"
    const val RESTING_HR = "resting_hr"
    const val WORKOUT_DONE = "workout_done"
    const val WORKOUT_NAME = "workout_name"
    const val WORKOUT_MIN = "workout_min"
    const val MEDITATION_MIN = "meditation_min"

    /** Toutes les colonnes, dans l'ordre du curseur. */
    val COLUMNS = listOf(
        DATE, STEPS, STEP_GOAL, KCAL_IN, KCAL_TARGET, KCAL_OUT, PROTEIN_G, PROTEIN_TARGET_G, WATER_ML,
        SLEEP_SCORE, SLEEP_MIN, RESTING_HR, WORKOUT_DONE, WORKOUT_NAME, WORKOUT_MIN, MEDITATION_MIN,
    )

    /** Chaque lecture est isolée : une source en panne (montre, Health Connect) n'empêche pas les autres. */
    suspend fun today(ctx: Context): Map<String, Any?> {
        val today = LocalDate.now()
        val day = today.toEpochDay()
        val out = LinkedHashMap<String, Any?>()
        out[DATE] = today.toString()
        val repo = runCatching { NutriApp.repository }.getOrNull()
        out[STEPS] = runCatching { Bridge.steps(ctx, today) }.getOrNull()?.roundToLong()
        out[STEP_GOAL] = runCatching { repo?.prefs?.settings?.value?.stepGoalFor(today)?.toLong() }.getOrNull()
        if (repo != null) runCatching {
            val entries = repo.entriesForDay(day).first()
            val target = repo.analytics().first().targetFor(day).macros
            out[KCAL_IN] = entries.sumOf { it.kcal }.roundToLong()
            out[PROTEIN_G] = entries.sumOf { it.protein }.roundToLong()
            out[KCAL_TARGET] = target.kcal.roundToLong()
            out[PROTEIN_TARGET_G] = target.protein.roundToLong()
        }
        out[KCAL_OUT] = metric(ctx, Metric.CALORIES, today)
        out[WATER_ML] = metric(ctx, Metric.HYDRATION, today)
        out[SLEEP_SCORE] = metric(ctx, Metric.SLEEP_SCORE, today)
        out[SLEEP_MIN] = metric(ctx, Metric.SLEEP, today)
        out[RESTING_HR] = metric(ctx, Metric.RESTING_HR, today)
        runCatching {
            val zone = ZoneId.systemDefault()
            val workout = WorkoutApp.store.state.value.history
                .filter { java.time.Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() == today }
                .maxByOrNull { it.startTime }
            out[WORKOUT_DONE] = if (workout != null) 1L else 0L
            out[WORKOUT_NAME] = workout?.name
            out[WORKOUT_MIN] = workout?.let { it.durationSec / 60 }
        }
        out[MEDITATION_MIN] = runCatching { Meditation.minutes(day, day).toLong() }.getOrNull()?.takeIf { it > 0 }
        return out
    }

    /** Même règle que les widgets santé : seulement si une source fournit la mesure, et jamais un 0 « vide ». */
    private suspend fun metric(ctx: Context, m: Metric, d: LocalDate): Long? =
        if (Bridge.hasSource(m)) runCatching { Bridge.healthValue(ctx, m, d) }.getOrNull()?.takeIf { it > 0 }?.roundToLong() else null
}
