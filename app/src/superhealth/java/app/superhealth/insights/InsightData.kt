package app.superhealth.insights

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EggAlt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.Metric
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.ui.weightValue
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.Analytics as TrainingAnalytics
import app.superhealth.ui.formatDuration
import app.superhealth.ui.formatNumber
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.ProteinColor
import app.superhealth.ui.theme.WorkoutBlue
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Lecture des données (nutrition, entraînement, montre) pour les insights, la carte de corrélation et les widgets. */
object InsightData {
    /** Séries de jours qu'on peut superposer. [sparse] : seulement certains jours (séances), tracée en points. */
    enum class Source(val label: String, val color: Color, val icon: ImageVector, val sparse: Boolean = false, val metric: Metric? = null) {
        SLEEP("Sommeil", HealthHues.Sleep, Icons.Rounded.Bedtime, metric = Metric.SLEEP),
        STEPS("Pas", HealthHues.Activity, Icons.AutoMirrored.Rounded.DirectionsWalk, metric = Metric.STEPS),
        WEIGHT("Tendance du poids", app.superhealth.ui.theme.Base.Pink, Icons.Rounded.MonitorWeight),
        ENERGY("Body Battery", Color(0xFF9BE22A), Icons.Rounded.Bolt, metric = Metric.BODY_ENERGY),
        VOLUME("Volume d'entraînement", WorkoutBlue, Icons.Rounded.FitnessCenter, sparse = true),
        KCAL("Calories mangées", app.superhealth.ui.theme.CaloriesColor, Icons.Rounded.Restaurant),
        PROTEIN("Protéines", ProteinColor, Icons.Rounded.EggAlt),
        RESTING_HR("Pouls au repos", HealthHues.Heart, Icons.Rounded.Favorite, metric = Metric.RESTING_HR),
        STRESS("Stress", HealthHues.Mind, Icons.Rounded.SelfImprovement, metric = Metric.STRESS),
    }

    /** Sources dont une donnée peut exister (montre, Health Connect ou saisie). */
    fun available(): List<Source> = Source.entries.filter { s -> s.metric == null || Bridge.hasSource(s.metric) }

    /** Valeurs par jour de [src] sur les [days] derniers jours (aujourd'hui compris). */
    suspend fun series(ctx: Context, src: Source, days: Int, today: LocalDate = LocalDate.now()): Map<LocalDate, Double> = coroutineScope {
        val dates = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
        when (src) {
            Source.WEIGHT -> NutriApp.repository.analytics().first().trend
                .associate { LocalDate.ofEpochDay(it.epochDay) to it.trend }.filterKeys { it in dates }
            Source.KCAL -> NutriApp.repository.analytics().first().days
                .mapNotNull { d -> d.intakeKcal?.takeIf { it > 0 }?.let { LocalDate.ofEpochDay(d.epochDay) to it } }.toMap().filterKeys { it in dates }
            Source.PROTEIN -> protein(dates.first(), today).filterValues { it > 0 }
            Source.VOLUME -> volume(dates.first())
            else -> {
                val m = src.metric!!
                if (!Bridge.hasSource(m)) emptyMap()
                else dates.map { d -> async { runCatching { Bridge.healthValue(ctx, m, d) }.getOrNull()?.takeIf { it > 0 }?.let { d to it } } }
                    .awaitAll().filterNotNull().toMap()
            }
        }
    }

    private suspend fun protein(from: LocalDate, to: LocalDate): Map<LocalDate, Double> =
        NutriApp.repository.entries.rangeFlow(from.toEpochDay(), to.toEpochDay()).first()
            .groupBy { it.epochDay }.map { (d, es) -> LocalDate.ofEpochDay(d) to es.sumOf { it.protein } }.toMap()

    /** Volume (kg × reps des séries de travail) par jour de séance. */
    private fun volume(from: LocalDate): Map<LocalDate, Double> = WorkoutApp.store.state.value.history
        .groupBy { TrainingAnalytics.day(it.startTime) }.filterKeys { !it.isBefore(from) }
        .mapValues { (_, ws) -> ws.sumOf { w -> w.exercises.sumOf { TrainingAnalytics.volume(it.sets) } } }
        .filterValues { it > 0 }

    /** Entrée complète des règles d'insights. */
    suspend fun input(ctx: Context, today: LocalDate = LocalDate.now()): Insights.Input = coroutineScope {
        val sleep = async { series(ctx, Source.SLEEP, 44, today) }
        val steps = async { series(ctx, Source.STEPS, 36, today) }
        val rhr = async { series(ctx, Source.RESTING_HR, 36, today) }
        val repo = NutriApp.repository
        val an = repo.analytics().first()
        val days = (0L..8L).map { today.minusDays(it) }
        val prot = protein(today.minusDays(8), today)
        val history = WorkoutApp.store.state.value.history
        val db = withTimeoutOrNull(3000) { WorkoutApp.db.filterNotNull().first() }
        val since = today.minusDays(Insights.LIFT_WINDOW)
        val sinceMs = since.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val lifts = if (db == null) emptyList() else TrainingAnalytics.topBySets(history, sinceMs, 5).map { (id, _) ->
            Insights.Lift(id, db.exercise(id)?.name ?: "", TrainingAnalytics.history(id, history, db).map { TrainingAnalytics.day(it.time) to it.e1rm })
        }
        Insights.Input(
            today = today,
            sleepMin = sleep.await(), steps = steps.await(), restingHr = rhr.await(),
            trainingDays = history.map { TrainingAnalytics.day(it.startTime) }.toSet(),
            lifts = lifts,
            trendKg = an.trend.associate { LocalDate.ofEpochDay(it.epochDay) to it.trend },
            intakeKcal = an.days.mapNotNull { d -> d.intakeKcal?.let { LocalDate.ofEpochDay(d.epochDay) to it } }.toMap(),
            kcalTarget = days.associateWith { an.targetFor(it.toEpochDay()).macros.kcal },
            protein = prot,
            proteinTarget = days.associateWith { an.targetFor(it.toEpochDay()).macros.protein },
        )
    }

    /** Constats du jour, gardés le temps d'une session de l'app (relus au plus toutes les 10 minutes). */
    private var cached: Pair<Long, List<Insights.Insight>>? = null

    suspend fun insights(ctx: Context, force: Boolean = false): List<Insights.Insight> {
        val now = System.currentTimeMillis()
        cached?.takeIf { !force && now - it.first < 10 * 60_000L }?.let { return it.second }
        return Insights.compute(input(ctx)).also { cached = now to it }
    }

    fun invalidate() { cached = null }
}

/** Phrase du constat, courte (tutoiement comme MacroFactor en français). */
fun Insights.Insight.text(s: app.superhealth.nutrition.domain.Settings): String {
    val v = abs(value)
    fun pct() = formatNumber(v, if (v < 10) 1 else 0)
    return when (type) {
        Insights.Type.LIFT -> trf(if (value > 0) "%1\$s : ton 1RM a pris %2\$s %% en %3\$s semaines" else "%1\$s : ton 1RM a perdu %2\$s %% en %3\$s semaines",
            subject ?: "", pct(), count)
        Insights.Type.SLEEP_TRAINING -> trf(if (value > 0) "Tu dors %1\$s de plus après les jours sans séance" else "Tu dors %1\$s de moins après les jours sans séance",
            formatDuration(v.roundToInt().toDouble()))
        Insights.Type.STEPS_WEEK -> trf(if (value > 0) "Tu marches %1\$s %% de plus que d'habitude cette semaine" else "Tu marches %1\$s %% de moins que d'habitude cette semaine",
            formatNumber(v))
        Insights.Type.WEIGHT_RATE -> if (v < Insights.WEIGHT_STABLE) tr("Ton poids est stable depuis 4 semaines")
            else trf(if (value < 0) "Ta tendance baisse de %1\$s par semaine depuis 4 semaines" else "Ta tendance monte de %1\$s par semaine depuis 4 semaines",
                formatNumber(s.weightValue(v), 2) + " " + tr(s.weightUnit.label))
        Insights.Type.KCAL_WEEK -> if (v < Insights.KCAL_MIN_PCT) tr("Tes calories sont dans l'objectif cette semaine")
            else trf(if (value < 0) "Tu manges %1\$s %% sous ton objectif cette semaine" else "Tu manges %1\$s %% au-dessus de ton objectif cette semaine", formatNumber(v))
        Insights.Type.PROTEIN_WEEK -> trf("Protéines atteintes %1\$s jours sur %2\$s cette semaine", count, total)
        Insights.Type.RESTING_HR -> trf(if (value < 0) "Ton pouls au repos a baissé de %1\$s bpm cette semaine" else "Ton pouls au repos a monté de %1\$s bpm cette semaine",
            formatNumber(v, 1))
        Insights.Type.TRAINING_MONTH -> trf(if (value > 0) "%1\$s séances en 4 semaines, %2\$s de plus qu'avant" else "%1\$s séances en 4 semaines, %2\$s de moins qu'avant",
            count, v.roundToInt())
    }
}

/** Écran ouvert en touchant le constat. */
val Insights.Insight.route: String get() = when (type) {
    Insights.Type.LIFT -> "exercise/$subjectId"
    Insights.Type.SLEEP_TRAINING -> "h/metric/SLEEP"
    Insights.Type.STEPS_WEEK -> "h/metric/STEPS"
    Insights.Type.WEIGHT_RATE -> "weight"
    Insights.Type.KCAL_WEEK -> "nutrition"
    Insights.Type.PROTEIN_WEEK -> "macros"
    Insights.Type.RESTING_HR -> "h/metric/RESTING_HR"
    Insights.Type.TRAINING_MONTH -> "t/insights"
}

val Insights.Insight.source: InsightData.Source get() = when (type) {
    Insights.Type.LIFT, Insights.Type.TRAINING_MONTH -> InsightData.Source.VOLUME
    Insights.Type.SLEEP_TRAINING -> InsightData.Source.SLEEP
    Insights.Type.STEPS_WEEK -> InsightData.Source.STEPS
    Insights.Type.WEIGHT_RATE -> InsightData.Source.WEIGHT
    Insights.Type.KCAL_WEEK -> InsightData.Source.KCAL
    Insights.Type.PROTEIN_WEEK -> InsightData.Source.PROTEIN
    Insights.Type.RESTING_HR -> InsightData.Source.RESTING_HR
}

/** Icône de la phrase : flèche de progression pour la force, sinon celle de la mesure. */
val Insights.Insight.icon: ImageVector get() = if (type == Insights.Type.LIFT) Icons.AutoMirrored.Rounded.TrendingUp else source.icon
