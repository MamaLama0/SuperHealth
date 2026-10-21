package app.superhealth.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.DashCard
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.Metric
import app.superhealth.nutrition.data.Analytics
import app.superhealth.nutrition.data.FoodEntryEntity
import app.superhealth.nutrition.domain.Balance
import app.superhealth.nutrition.domain.Habits
import app.superhealth.nutrition.domain.Settings as NutritionSettings
import app.superhealth.nutrition.ui.components.ProgressBar
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.macros
import app.superhealth.nutrition.ui.weightValue
import app.superhealth.training.domain.Analytics as TrainingAnalytics
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.formatNumber
import app.superhealth.ui.formatValue
import app.superhealth.ui.go
import app.superhealth.ui.localDate
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Valeur principale d'une carte, pour sa bulle (S) ou sa tuile (M). [progress] : anneau ou barre, null sans objectif. */
internal class CompactData(val value: String, val unit: String?, val label: String, val progress: Float?, val onClick: () -> Unit)

@Composable
internal fun compactOf(c: DashCard, vm: SuperViewModel, h: HealthSnap, an: Analytics, s: NutritionSettings, nav: NavHostController, tab: (String) -> Unit): CompactData? {
    val kcal = tr(s.energyUnit.label)
    fun goal(v: Double?, g: Double) = if (g > 0) ((v ?: 0.0) / g).toFloat() else null
    return when (c) {
        DashCard.CALORIES -> {
            val eaten by vm.nutrition.todayEntries.collectAsState()
            val consumed = eaten.sumOf { it.macros().kcal }
            val target = an.targetFor(an.today).macros.kcal
            val left = target - consumed
            CompactData(formatNumber(abs(s.energyValue(left))), kcal, if (left < 0) tr("en trop") else tr("Restantes"), goal(consumed, target)) {
                vm.nutrition.selectedDay.value = an.today; tab("log")
            }
        }
        DashCard.WEIGHT -> CompactData(an.currentTrendKg?.let { formatNumber(s.weightValue(it), 1) } ?: "—", tr(s.weightUnit.label), tr("Tendance"), null) { nav.go("weight") }
        DashCard.EXPENDITURE -> CompactData(s.energyValue(an.currentTdee).roundToInt().toString(), kcal, tr("Dépense"), null) { nav.go("expenditure") }
        DashCard.INTAKE -> {
            val byDay = an.days.associateBy { it.epochDay }
            val avg = (an.today - 6..an.today).mapNotNull { byDay[it]?.intakeKcal }.takeIf { it.isNotEmpty() }?.average()
            CompactData(avg?.let { s.energyValue(it).roundToInt().toString() } ?: "—", kcal, tr("Apports moy."), null) { nav.go("nutrition") }
        }
        DashCard.MACROS -> {
            val eaten by vm.nutrition.todayEntries.collectAsState()
            val p = eaten.sumOf { it.macros().protein }
            val target = an.targetFor(an.today).macros.protein
            CompactData(p.roundToInt().toString(), "g", tr("Protéines"), goal(p, target)) { nav.go("macros") }
        }
        DashCard.STEPS -> h.today(Metric.STEPS).let { v ->
            CompactData(v?.let { formatNumber(it) } ?: "—", null, tr("Pas"), goal(v, s.stepGoal.toDouble())) { nav.go("h/metric/STEPS") }
        }
        DashCard.HEART -> {
            val v = h.heartToday.lastOrNull()?.value ?: h.latest(Metric.HEART_RATE)?.second
            CompactData(v?.let { formatNumber(it) } ?: "—", tr("bpm"), tr("Cœur"), null) { nav.go("h/metric/HEART_RATE") }
        }
        DashCard.SLEEP -> h.latest(Metric.SLEEP)?.second.let { v ->
            CompactData(v?.let { formatValue(Metric.SLEEP, it) } ?: "—", null, tr("Sommeil"), goal(v, 480.0)) { nav.go("h/metric/SLEEP") }
        }
        DashCard.CARDIO -> CompactData(h.cardio?.total?.toString() ?: "—", "min", tr("Cardio (sem.)"), h.cardio?.progress) { nav.go("h/metric/CARDIO_WEEK") }
        DashCard.ENERGY -> h.latest(Metric.BODY_ENERGY)?.second.let { v ->
            CompactData(v?.let { formatNumber(it) } ?: "—", null, tr("Body Battery"), goal(v, 100.0)) { nav.go("h/metric/BODY_ENERGY") }
        }
        DashCard.HYDRATION -> h.today(Metric.HYDRATION).let { v ->
            CompactData(formatNumber(v ?: 0.0), "ml", tr("Hydratation"), goal(v, HealthRepository.goal(Metric.HYDRATION) ?: 2000.0)) { nav.go("h/metric/HYDRATION") }
        }
        DashCard.RUNNING -> CompactData(h.perf?.weeks?.lastOrNull()?.let { formatNumber(it.runKm, 1) } ?: "—", "km", tr("Course (sem.)"), null) { nav.go("h/performance") }
        DashCard.RECOVERY -> CompactData(h.perf?.recoveryLeftH?.let { formatNumber(it.toDouble()) } ?: "—", "h", tr("Récupération"), null) { nav.go("h/performance") }
        DashCard.TRAINING_WEEK -> {
            val st by vm.training.state.collectAsState()
            val ws = TrainingAnalytics.weekStart(LocalDate.now())
            CompactData(st.history.count { !localDate(it.startTime).isBefore(ws) }.toString(), null, tr("Séances (sem.)"), null) { nav.go("t/insights") }
        }
        DashCard.HABITS -> {
            val logged = an.days.filter { it.intakeKcal != null || it.fasted }.map { it.epochDay }.toSet()
            CompactData(Habits.streak(logged, an.today).current.toString(), tr("j."), tr("Série journal"), null) { nav.go("habits") }
        }
        DashCard.BALANCE -> {
            val avg = Balance.average(Balance.days(an.days, an::tdeeAt, an.today))
            CompactData(avg?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + formatNumber(abs(s.energyValue(it))) } ?: "—", kcal, tr("Bilan / jour"), null) { nav.go("expenditure") }
        }
        else -> null
    }
}

/** Bulle ronde : anneau de progression sur le bord, valeur au centre (même famille que les constantes). */
@Composable
internal fun CompactBubble(c: DashCard, d: CompactData, modifier: Modifier) {
    val hue = c.hue()
    val track = Mf.colors.card.copy(alpha = if (Mf.colors.dark) 0.5f else 0.85f)
    BoxWithConstraints(modifier.aspectRatio(1f).clip(CircleShape).background(Brush.radialGradient(listOf(hue.container(1.8f), hue.container(0.9f))))
        .clickable(onClick = d.onClick), contentAlignment = Alignment.Center) {
        val small = maxWidth < 100.dp
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.06f
            val inset = stroke * 0.9f
            val box = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
            d.progress?.takeIf { it > 0f }?.let { p ->
                drawArc(hue, -90f, 360f * p.coerceAtMost(1f), false, Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = maxWidth * 0.14f)) {
            Icon(c.icon(), null, Modifier.size(if (small) 13.dp else 16.dp), tint = hue)
            Text(d.value, style = if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = hue.onContainer(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            d.unit?.takeIf { !small }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1) }
            Text(d.label, style = MaterialTheme.typography.labelSmall, color = hue.onContainer(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center)
        }
    }
}

/** Demi-tuile générique : titre, valeur, barre d'objectif. */
@Composable
internal fun CompactTile(c: DashCard, d: CompactData, modifier: Modifier) {
    Tile(c, tr(c.label), modifier, onClick = d.onClick) {
        Value(d.value, d.unit, color = c.hue().onContainer())
        Sub(d.label)
        d.progress?.let { Spacer(Modifier.height(8.dp)); ProgressBar(it.coerceIn(0f, 1f), c.hue(), Modifier.fillMaxWidth(), height = 6.dp, track = softTrack()) }
    }
}

/** Anneaux choisis du tableau de bord, valeurs du jour et objectifs. */
internal fun dashRings(names: List<String>, h: HealthSnap, eaten: List<FoodEntryEntity>, an: Analytics, s: NutritionSettings): List<Rings.Ring> {
    val target = an.targetFor(an.today).macros
    return Rings.build(Rings.kinds(names), { k ->
        when (k) {
            Rings.Kind.STEPS -> h.today(Metric.STEPS)
            Rings.Kind.ENERGY -> h.today(Metric.BODY_ENERGY)
            Rings.Kind.KCAL -> eaten.takeIf { it.isNotEmpty() }?.sumOf { it.macros().kcal }
            Rings.Kind.PROTEIN -> eaten.takeIf { it.isNotEmpty() }?.sumOf { it.macros().protein }
            Rings.Kind.WATER -> h.today(Metric.HYDRATION)
            Rings.Kind.SLEEP -> h.latest(Metric.SLEEP)?.takeIf { it.first == LocalDate.now() }?.second
            Rings.Kind.CARDIO -> h.cardio?.total?.toDouble()
        }
    }, { k -> ringGoal(k, s.stepGoal, target.kcal, target.protein, h.cardio?.target) })
}

/** Objectif de chaque anneau (commun au tableau de bord et au widget). */
internal fun ringGoal(k: Rings.Kind, stepGoal: Int, kcal: Double, protein: Double, cardioTarget: Int?): Double = when (k) {
    Rings.Kind.STEPS -> stepGoal.toDouble()
    Rings.Kind.ENERGY -> 100.0
    Rings.Kind.KCAL -> kcal
    Rings.Kind.PROTEIN -> protein
    Rings.Kind.WATER -> HealthRepository.goal(Metric.HYDRATION) ?: 2000.0
    Rings.Kind.SLEEP -> 480.0
    Rings.Kind.CARDIO -> (cardioTarget ?: 150).toDouble()
}
