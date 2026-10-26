package app.superhealth.ui.widget

import app.superhealth.platform.*

import app.superhealth.SuperPrefs
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.HealthSources
import app.superhealth.health.data.Metric
import app.superhealth.health.data.NightVitals
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.macros
import app.superhealth.nutrition.ui.weightValue
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.Generator
import app.superhealth.ui.formatDuration
import app.superhealth.ui.formatNumber
import app.superhealth.ui.theme.color
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Widgets de l'écran d'accueil et de l'écran verrouillé (WidgetKit sur iOS) : l'app écrit un instantané JSON que l'extension
 * lit. Textes déjà traduits et nombres déjà formatés ici, pour que les widgets affichent exactement ce que montre l'app.
 */
object Widgets {
    @Serializable data class Ring(val label: String, val value: Double, val goal: Double, val color: String, val text: String)
    @Serializable data class Calories(val left: String, val leftLabel: String, val eaten: Double, val target: Double, val unit: String,
                                      val macros: List<Macro>)
    @Serializable data class Macro(val label: String, val value: Double, val target: Double, val color: String)
    @Serializable data class Weight(val value: String, val unit: String, val delta: String?, val points: List<Double>)
    @Serializable data class Next(val title: String, val subtitle: String, val action: String, val route: String, val week: List<Boolean>,
                                  val dayLetters: List<String>, val progress: Double? = null)
    @Serializable data class Vital(val label: String, val value: String, val status: String)
    @Serializable data class Vitals(val title: String, val summary: String, val ok: Boolean, val items: List<Vital>)
    @Serializable data class Snapshot(
        val updatedMs: Long, val date: String, val accent: String,
        val labels: Map<String, String>,
        val calories: Calories?, val rings: List<Ring>, val next: Next?, val weight: Weight?, val vitals: Vitals?,
        val steps: String?, val sleep: String?,
    )

    private val json = Json { encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    private fun hex(c: androidx.compose.ui.graphics.Color): String {
        fun b(f: Float) = (f * 255).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
        return "#" + b(c.red) + b(c.green) + b(c.blue)
    }

    /** Recalcule l'instantané après les changements (regroupés sur une seconde) et recharge les widgets. */
    fun publish() {
        if (!SuperPrefs.settings.value.onboarded) return
        pending?.cancel()
        pending = scope.launch {
            delay(1000)
            runCatching { writeWidgetSnapshot(json.encodeToString(Snapshot.serializer(), build())) }
        }
    }

    suspend fun build(): Snapshot {
        val repo = NutriApp.repository
        val s = repo.prefs.settings.value
        val today = LocalDate.now()
        val an = runCatching { repo.analytics().first() }.getOrNull()
        val entries = repo.entries.all().filter { it.epochDay == today.toEpochDay() }

        val calories = an?.let { a ->
            val target = a.targetFor(a.today).macros
            val eaten = entries.fold(app.superhealth.nutrition.domain.Macros.ZERO) { acc, e -> acc + e.macros() }
            val left = target.kcal - eaten.kcal
            Calories(formatNumber(kotlin.math.abs(s.energyValue(left))), if (left < 0) tr("en trop") else tr("Restantes"),
                s.energyValue(eaten.kcal), s.energyValue(target.kcal), tr(s.energyUnit.label), listOf(
                    Macro(tr("Protéines"), eaten.protein, target.protein, hex(app.superhealth.ui.theme.ProteinColor)),
                    Macro(tr("Lipides"), eaten.fat, target.fat, hex(app.superhealth.ui.theme.FatColor)),
                    Macro(tr("Glucides"), eaten.carbs, target.carbs, hex(app.superhealth.ui.theme.CarbsColor)),
                ))
        }

        val apple = runCatching { if (app.superhealth.bridge.Watch.devices().isNotEmpty()) HealthSources.current.activitySummary(today) else null }.getOrNull()
        val rings = buildList {
            apple?.let { a ->
                add(Ring(tr("Bouger"), a.move, a.moveGoal.takeIf { it > 0 } ?: 500.0, "#FA114F", "${a.move.toInt()}/${a.moveGoal.toInt()} kcal"))
                add(Ring(tr("M'entraîner"), a.exercise, a.exerciseGoal.takeIf { it > 0 } ?: 30.0, "#92E82A", "${a.exercise.toInt()}/${a.exerciseGoal.toInt()} min"))
            }
            calories?.let { add(Ring(tr("Calories mangées"), it.eaten, it.target, "#14D8F0", "${kotlin.math.round(it.eaten).toInt()}/${kotlin.math.round(it.target).toInt()} ${it.unit}")) }
        }

        val weight = an?.currentTrendKg?.let { kg ->
            val week = an.trend.takeLast(8)
            Weight(formatNumber(s.weightValue(kg), 1), tr(s.weightUnit.label),
                if (week.size >= 2) (s.weightValue(week.last().trend - week.first().trend)).let { d -> (if (d >= 0) "+" else "−") + formatNumber(kotlin.math.abs(d), 2) + tr(" / sem.") } else null,
                an.trend.takeLast(28).map { s.weightValue(it.trend) })
        }

        val ctx = appContext
        val steps = runCatching { app.superhealth.bridge.Bridge.steps(ctx, today) }.getOrNull()?.let { formatNumber(it) }
        val sleep = runCatching { HealthRepository.dayValue(ctx, Metric.SLEEP, today) }.getOrNull()?.let { formatDuration(it) }
        val vitals = runCatching { if (app.superhealth.bridge.Watch.devices().isNotEmpty()) NightVitals.of(ctx) else null }.getOrNull()
            ?.takeIf { it.measured > 0 }?.let { n ->
                Vitals(tr("Signes vitaux"), if (n.outliers == 0) tr("Dans votre plage habituelle") else if (n.outliers == 1) tr("1 mesure hors de l'habituel")
                    else trf("%1\$s mesures hors de l'habituel", n.outliers), n.outliers == 0,
                    n.items.filter { it.value != null }.map { Vital(tr(it.metric.label), app.superhealth.ui.formatWithUnit(it.metric, it.value!!), it.status.name) })
            }

        return Snapshot(nowMs(), today.toString(), hex(s.accent.color()),
            mapOf("app" to "SuperHealth", "calories" to tr("Calories"), "next" to tr("Prochaine séance"), "session" to tr("Séance en cours"),
                "weight" to tr("Tendance du poids"), "steps" to tr("Pas"), "sleep" to tr("Sommeil"), "rings" to tr("Anneaux du jour"),
                "open" to tr("Ouvrez l'app pour commencer.")),
            calories, rings, next(), weight, vitals, steps, sleep)
    }

    /** Prochaine séance (ou séance en cours) et jours entraînés cette semaine, comme le widget Android. */
    private fun next(): Next? {
        val st = WorkoutApp.store.state.value
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val monday = today.minusDays(today.dayOfWeek.value - 1L)
        val trained = st.history.map { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }.filter { !it.isBefore(monday) }.map { it.dayOfWeek.value }.toSet()
        val week = (1..7).map { it in trained }
        val letters = (0..6).map { app.superhealth.ui.dayLetter(monday.plusDays(it.toLong())) }
        st.session?.let { s ->
            val done = s.exercises.sumOf { e -> e.sets.count { it.done } }; val total = s.exercises.sumOf { it.sets.size }
            return Next(s.name, trf("%1\$d/%2\$d séries faites", done, total), tr("Reprendre"), "training:session", week, letters,
                done.toDouble() / total.coerceAtLeast(1))
        }
        val p = st.programs.firstOrNull { it.id == st.settings.activeProgramId }
        val day = p?.days?.getOrNull(p.dayIndex)
        return when {
            p == null || day == null -> Next(tr("Aucun programme actif"), tr("Séance libre ou nouveau programme"), tr("Séance vide"), "training:startempty", week, letters)
            day.isRest -> Next(day.name, tr("Jour de repos"), tr("Ouvrir"), "training:workout", week, letters)
            else -> Next(day.name, trf("%1\$d exercices · ≈ %2\$d min", day.exercises.size, Generator.estimateMinutes(day, p.cycleIndex)),
                tr("Commencer"), "training:startnext", week, letters)
        }
    }
}
