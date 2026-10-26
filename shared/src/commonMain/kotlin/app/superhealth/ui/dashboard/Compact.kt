package app.superhealth.ui.dashboard

import app.superhealth.platform.*

import app.superhealth.ui.theme.dashCardBackground
import app.superhealth.ui.theme.dashChipColors
import app.superhealth.nutrition.domain.stepGoalFor
import app.superhealth.ui.charts.ChartStyle
import app.superhealth.ui.charts.smooth
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.superhealth.ui.theme.CardRadius
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import app.superhealth.ui.trf
import app.superhealth.vitalMetrics
import app.superhealth.platform.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Valeur principale d'une carte, pour ses tailles génériques (carré, haute, demi, bandeau, grande). [progress] : anneau ou
 * barre, null sans objectif. [history] : valeurs récentes, de la plus ancienne à aujourd'hui (barres si [bars], sinon
 * courbe), [goal] : objectif de chaque barre.
 */
internal data class CompactData(
    val value: String, val unit: String?, val label: String, val progress: Float?,
    val history: List<Double?> = emptyList(), val bars: Boolean = true, val goal: Double? = null, val onClick: () -> Unit,
)

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
            val byDay = an.days.associateBy { it.epochDay }
            CompactData(formatNumber(abs(s.energyValue(left))), kcal, if (left < 0) tr("en trop") else tr("Restantes"), goal(consumed, target),
                (an.today - 6 until an.today).map { byDay[it]?.intakeKcal?.let(s::energyValue) } + s.energyValue(consumed), goal = s.energyValue(target)) {
                vm.nutrition.selectedDay.value = an.today; tab("log")
            }
        }
        DashCard.WEIGHT -> CompactData(an.currentTrendKg?.let { formatNumber(s.weightValue(it), 1) } ?: "—", tr(s.weightUnit.label), tr("Tendance"), null,
            (an.today - 27..an.today).map { d -> an.trendByDay[d]?.let(s::weightValue) }, bars = false) { nav.go("weight") }
        DashCard.EXPENDITURE -> CompactData(s.energyValue(an.currentTdee).roundToInt().toString(), kcal, tr("Dépense"), null,
            an.expenditure.takeLast(28).map { s.energyValue(it.tdee) }, bars = false) { nav.go("expenditure") }
        DashCard.INTAKE -> {
            val byDay = an.days.associateBy { it.epochDay }
            val avg = (an.today - 6..an.today).mapNotNull { byDay[it]?.intakeKcal }.takeIf { it.isNotEmpty() }?.average()
            CompactData(avg?.let { s.energyValue(it).roundToInt().toString() } ?: "—", kcal, tr("Apports moy."), null,
                (an.today - 6..an.today).map { byDay[it]?.intakeKcal?.let(s::energyValue) }, goal = s.energyValue(an.targetFor(an.today).macros.kcal)) { nav.go("nutrition") }
        }
        DashCard.MACROS -> {
            val eaten by vm.nutrition.todayEntries.collectAsState()
            val p = eaten.sumOf { it.macros().protein }
            val target = an.targetFor(an.today).macros.protein
            CompactData(p.roundToInt().toString(), "g", tr("Protéines"), goal(p, target)) { nav.go("macros") }
        }
        DashCard.STEPS -> h.today(Metric.STEPS).let { v ->
            val g = s.stepGoalFor(LocalDate.now()).toDouble()
            CompactData(v?.let { formatNumber(it) } ?: "—", null, tr("Pas"), goal(v, g),
                h.series[Metric.STEPS].orEmpty().map { it.second }, goal = g.takeIf { it > 0 }) { nav.go("h/metric/STEPS") }
        }
        DashCard.HEART -> {
            val v = h.heartToday.lastOrNull()?.value ?: h.latest(Metric.HEART_RATE)?.second
            CompactData(v?.let { formatNumber(it) } ?: "—", tr("bpm"), tr("Cœur"), null,
                h.series[Metric.RESTING_HR].orEmpty().map { it.second }, bars = false) { nav.go("h/metric/HEART_RATE") }
        }
        DashCard.SLEEP -> h.latest(Metric.SLEEP)?.second.let { v ->
            CompactData(v?.let { formatValue(Metric.SLEEP, it) } ?: "—", null, tr("Sommeil"), goal(v, 480.0),
                h.series[Metric.SLEEP].orEmpty().map { it.second }, goal = 480.0) { nav.go("h/metric/SLEEP") }
        }
        DashCard.CARDIO -> CompactData(h.cardio?.total?.toString() ?: "—", "min", tr("Cardio (sem.)"), h.cardio?.progress) { nav.go("h/metric/CARDIO_WEEK") }
        DashCard.ENERGY -> h.latest(Metric.BODY_ENERGY)?.second.let { v ->
            CompactData(v?.let { formatNumber(it) } ?: "—", null, tr("Body Battery"), goal(v, 100.0),
                h.series[Metric.BODY_ENERGY].orEmpty().map { it.second }, bars = false) { nav.go("h/metric/BODY_ENERGY") }
        }
        DashCard.HYDRATION -> h.today(Metric.HYDRATION).let { v ->
            val g = HealthRepository.goal(Metric.HYDRATION) ?: 2000.0
            CompactData(formatNumber(v ?: 0.0), "ml", tr("Hydratation"), goal(v, g), h.series[Metric.HYDRATION].orEmpty().map { it.second }, goal = g) {
                nav.go("h/metric/HYDRATION")
            }
        }
        DashCard.RUNNING -> CompactData(h.perf?.weeks?.lastOrNull()?.let { formatNumber(it.runKm, 1) } ?: "—", "km", tr("Course (sem.)"), null,
            h.perf?.weeks.orEmpty().takeLast(8).map { it.runKm.takeIf { k -> k > 0 } }) { nav.go("h/performance") }
        DashCard.RECOVERY -> CompactData(h.perf?.recoveryLeftH?.let { formatNumber(it.toDouble()) } ?: "—", "h", tr("Récupération"), null) { nav.go("h/performance") }
        DashCard.TRAINING_WEEK -> {
            val st by vm.training.state.collectAsState()
            val ws = TrainingAnalytics.weekStart(LocalDate.now())
            val weeks = androidx.compose.runtime.remember(st.history) { TrainingAnalytics.weeklyTotals(st.history, 8, LocalDate.now()) }
            CompactData(st.history.count { !localDate(it.startTime).isBefore(ws) }.toString(), null, tr("Séances (sem.)"), null,
                weeks.map { it.sessions.toDouble().takeIf { v -> v > 0 } }) { nav.go("t/insights") }
        }
        DashCard.HABITS -> {
            val logged = an.days.filter { it.intakeKcal != null || it.fasted }.map { it.epochDay }.toSet()
            CompactData(Habits.streak(logged, an.today).current.toString(), tr("j."), tr("Série journal"), null) { nav.go("habits") }
        }
        DashCard.BALANCE -> {
            val days = Balance.days(an.days, an::tdeeAt, an.today)
            val avg = Balance.average(days)
            CompactData(avg?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + formatNumber(abs(s.energyValue(it))) } ?: "—", kcal, tr("Bilan / jour"), null,
                days.map { it.second?.let(s::energyValue) }, bars = false) { nav.go("expenditure") }
        }
        DashCard.VITALS -> {
            val m = app.superhealth.SuperPrefs.settings.value.vitalMetrics.firstOrNull { h.latest(it) != null } ?: return null
            CompactData(formatValue(m, h.latest(m)!!.second), app.superhealth.ui.unitOf(m), tr(m.label), null,
                h.series[m].orEmpty().map { it.second }, bars = false) { nav.go("h/metric/${m.name}") }
        }
        DashCard.ACTIVITIES -> {
            val act = h.activities.firstOrNull()
            CompactData(act?.let { trf("%1\$s min", it.minutes) } ?: "—", null, act?.title ?: tr("Dernières activités"), null) { nav.go("h/activities") }
        }
        DashCard.RECORDS -> {
            val st by vm.training.state.collectAsState()
            val db = vm.training.db.collectAsState().value ?: return null
            val n = androidx.compose.runtime.remember(st.history) {
                val sorted = st.history.sortedBy { it.startTime }
                val since = System.currentTimeMillis() - 30L * 86_400_000
                sorted.withIndex().filter { it.value.startTime >= since }.sumOf { (i, w) -> TrainingAnalytics.newRecords(w, sorted.take(i), db).size }
            }
            CompactData(n.toString(), null, tr("Records (30 j)"), null) { nav.go("t/insights") }
        }
        DashCard.BODY -> {
            // tour de taille (la mesure la plus parlante), ou la première mesure suivie qui a des valeurs
            val metrics by remember { vm.nutrition.repo.body.metricsFlow() }.collectAsState(initial = emptyList())
            val codes = listOf("w") + app.superhealth.SuperPrefs.settings.value.bodyMetrics
            val code = codes.firstOrNull { k -> metrics.any { it.metric == k } }
            val l = metrics.filter { it.metric == code }
            CompactData(l.lastOrNull()?.value?.let { formatNumber(it, 1) } ?: "—", "cm",
                app.superhealth.nutrition.domain.BodyMetric.of(code ?: "w")?.label?.let(::tr) ?: tr("Mesures"), null,
                l.takeLast(8).map { it.value }, bars = false) { nav.go("body") }
        }
        DashCard.STRATEGY -> CompactData(formatNumber(s.energyValue(an.targetFor(an.today).macros.kcal)), kcal, tr("Objectif"), null) { nav.go("strategy") }
        DashCard.FOCUS -> {
            val items = focusItems(vm, an, h, nav, tab)
            val n = items.size
            // toucher : la première chose à faire (avant, « home » empilait un second tableau de bord)
            CompactData(if (n == 0) "✓" else n.toString(), null, tr("À faire"), null) { items.firstOrNull()?.third?.second?.invoke() }
        }
        DashCard.NEXT_WORKOUT -> {
            val st by vm.training.state.collectAsState()
            val p = st.programs.firstOrNull { it.id == st.settings.activeProgramId }
            val session = st.session
            val day = p?.days?.getOrNull(p.dayIndex)
            val done = session?.let { ss -> ss.exercises.sumOf { e -> e.sets.count { it.done } }.toFloat() / ss.exercises.sumOf { it.sets.size }.coerceAtLeast(1) }
            CompactData(session?.name ?: day?.name ?: "—", null, tr("Prochaine séance"), done) {
                if (session != null) nav.go("session") else tab("activities")
            }
        }
        DashCard.TRAINING_TRENDS -> {
            val st by vm.training.state.collectAsState()
            val weeks = androidx.compose.runtime.remember(st.history) { TrainingAnalytics.weeklyTotals(st.history, 8, LocalDate.now()) }
            CompactData(weeks.last().sets.toString(), tr("séries"), tr("Cette semaine"), null, weeks.map { it.sets.toDouble().takeIf { v -> v > 0 } }) { nav.go("t/insights") }
        }
        DashCard.MUSCLE_SETS -> {
            val st by vm.training.state.collectAsState()
            val db = vm.training.db.collectAsState().value ?: return null
            val sets = androidx.compose.runtime.remember(st.history, db) { TrainingAnalytics.weeklySetsByMuscle(st.history, db, 4, System.currentTimeMillis()) }
            val inZone = sets.values.count { it >= TrainingAnalytics.SETS_LOW }
            CompactData("$inZone", "/ ${sets.size}", tr("Muscles dans la zone"), if (sets.isEmpty()) null else inZone.toFloat() / sets.size) { nav.go("levels") }
        }
        DashCard.LIFTS -> {
            val st by vm.training.state.collectAsState()
            val db = vm.training.db.collectAsState().value ?: return null
            val since = System.currentTimeMillis() - 84L * 86_400_000
            val pts = androidx.compose.runtime.remember(st.history, db) {
                TrainingAnalytics.topBySets(st.history, since, 1).firstOrNull()?.let { (id, _) ->
                    TrainingAnalytics.history(id, st.history, db).filter { it.time >= since && it.e1rm > 0 }
                }.orEmpty()
            }
            val change = if (pts.size >= 2) (pts.last().e1rm / pts.first().e1rm - 1) * 100 else null
            CompactData(change?.let { (if (it >= 0) "+" else "") + formatNumber(it, 1) } ?: "—", "%", tr("1RM estimé"), null,
                pts.map { it.e1rm }, bars = false) { nav.go("t/insights") }
        }
        else -> if (c.isWatchChart) watchCompact(c, h.watch, nav) else null
    }
}

/**
 * Carte carrée (1 bloc) : même fond que les autres cartes, la mesure dans un anneau de progression au centre, son nom dessous.
 */
@Composable
internal fun CompactBubble(c: DashCard, d: CompactData, modifier: Modifier) {
    val hue = c.ink()
    val track = Mf.colors.chip
    val prog = d.progress
    Column(modifier.square().clip(RoundedCornerShape(CardRadius))
        .dashCardBackground(hue).cardClick(d.onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                // cercle sur le plus petit côté, centré : le bloc peut être plus haut que large
                val d = size.minDimension
                val stroke = d * 0.085f
                val box = Size(d - stroke, d - stroke)
                val tl = Offset((size.width - d) / 2 + stroke / 2, (size.height - d) / 2 + stroke / 2)
                drawArc(track, 0f, 360f, false, tl, box, style = Stroke(stroke))
                prog?.takeIf { it > 0f }?.let { p -> drawArc(hue, -90f, 360f * p.coerceAtMost(1f), false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round)) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(c.icon(), null, Modifier.size(14.dp), tint = hue)
                Text(d.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = hue.onContainer(), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
                d.unit?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1) }
            }
        }
    }
}

/** Petit graphique sans texte : barres (objectif atteint en plein) ou courbe avec aire ; rien sous 2 valeurs. */
@Composable
internal fun MiniChart(d: CompactData, hue: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val track = softTrack()
    // une courbe commence à la première valeur (premières pesées) ; les barres gardent tous leurs jours
    val vals = if (d.bars) d.history else d.history.dropWhile { it == null }
    if (vals.count { it != null } < 2) { Box(modifier); return }
    Canvas(modifier) {
        val n = vals.size
        val present = vals.filterNotNull()
        if (d.bars) {
            // barres de la charte commune : fond de barre, objectif atteint en plein, sinon atténuée
            val max = (present + listOfNotNull(d.goal)).max().coerceAtLeast(1e-9)
            val slot = size.width / n; val bw = (slot * ChartStyle.BAR_FILL).coerceAtMost(ChartStyle.barMax.toPx())
            vals.forEachIndexed { i, v ->
                val x = i * slot + (slot - bw) / 2
                drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), androidx.compose.ui.geometry.CornerRadius(bw / 2))
                val bh = ((v ?: 0.0) / max * size.height).toFloat()
                if (v != null && v > 0) drawRoundRect(if (d.goal != null && v >= d.goal) hue else hue.copy(alpha = ChartStyle.BELOW_GOAL_ALPHA),
                    Offset(x, size.height - bh.coerceAtLeast(bw)), Size(bw, bh.coerceAtLeast(bw)), androidx.compose.ui.geometry.CornerRadius(bw / 2))
            }
        } else {
            val lo = present.min(); val hi = present.max()
            val pad = ChartStyle.endDot.toPx() + 1.dp.toPx()
            fun x(i: Int) = pad + (size.width - 2 * pad) * i / (n - 1).coerceAtLeast(1)
            fun y(v: Double) = pad + (size.height - 2 * pad) * (1 - if (hi > lo) ((v - lo) / (hi - lo)).toFloat() else 0.5f)
            val pts = vals.withIndex().mapNotNull { (i, v) -> v?.let { Offset(x(i), y(it)) } }
            val line = androidx.compose.ui.graphics.Path().apply { smooth(pts) }
            val area = androidx.compose.ui.graphics.Path().apply { addPath(line); lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close() }
            drawPath(area, Brush.verticalGradient(listOf(hue.copy(alpha = ChartStyle.AREA_ALPHA), hue.copy(alpha = 0f))))
            drawPath(line, hue, style = Stroke(ChartStyle.line.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawCircle(hue, ChartStyle.endDot.toPx(), pts.last())
        }
    }
}

/** Anneau de progression seul, valeur au centre (bulles et cartes hautes). */
@Composable
private fun ProgressDisc(d: CompactData, c: DashCard, modifier: Modifier, small: Boolean = false) {
    val hue = c.ink()
    val track = Mf.colors.chip
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val dd = size.minDimension
            val stroke = dd * 0.09f
            val box = Size(dd - stroke, dd - stroke)
            val tl = Offset((size.width - dd) / 2 + stroke / 2, (size.height - dd) / 2 + stroke / 2)
            drawArc(track, 0f, 360f, false, tl, box, style = Stroke(stroke))
            d.progress?.takeIf { it > 0f }?.let { p -> drawArc(hue, -90f, 360f * p.coerceAtMost(1f), false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round)) }
        }
        Text(d.value, style = if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = hue.onContainer(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
private fun TintedBox(c: DashCard, modifier: Modifier, onClick: () -> Unit, padding: androidx.compose.ui.unit.Dp = 12.dp,
                      content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val hue = c.ink()
    Column(modifier.clip(RoundedCornerShape(CardRadius)).dashCardBackground(hue)
        .cardClick(onClick).padding(padding), content = content)
}

/**
 * Moyen (2 blocs) : la version à regarder d'un coup d'œil. Le symbole dit de quelle mesure il s'agit : pas de titre,
 * seulement la valeur et, dessous, la période en petit graphique (ou la barre d'objectif).
 */
@Composable
internal fun CompactMedium(c: DashCard, d: CompactData, modifier: Modifier) {
    val hue = c.ink()
    TintedBox(c, modifier.fillMaxWidth(), d.onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (chipBg, chipTint) = dashChipColors(hue)
            Box(Modifier.size(30.dp).clip(CircleShape).background(chipBg), contentAlignment = Alignment.Center) {
                Icon(c.icon(), null, Modifier.size(17.dp), tint = chipTint)
            }
            Spacer(Modifier.weight(1f))
            d.progress?.takeIf { d.history.count { it != null } >= 2 }?.let { ProgressDisc(d.copy(value = ""), c, Modifier.size(30.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(d.value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = hue.onContainer(), maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            d.unit?.let { Text(" $it", style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp)) }
        }
        val chart = d.history.count { it != null } >= 2
        if (chart || d.progress != null) {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(10.dp))
            if (chart) MiniChart(d, hue, Modifier.fillMaxWidth().height(40.dp))
            else ProgressBar(d.progress!!.coerceIn(0f, 1f), hue, Modifier.fillMaxWidth(), height = 8.dp, track = softTrack())
        }
    }
}

/** Bandeau (toute la largeur, une ligne) : symbole, valeur, et graphique ou barre d'objectif à droite. */
@Composable
internal fun CompactStrip(c: DashCard, d: CompactData, modifier: Modifier) {
    val hue = c.ink()
    TintedBox(c, modifier.fillMaxWidth(), d.onClick, padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (chipBg, chipTint) = dashChipColors(hue)
            Box(Modifier.size(34.dp).clip(CircleShape).background(chipBg), contentAlignment = Alignment.Center) {
                Icon(c.icon(), null, Modifier.size(19.dp), tint = chipTint)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(d.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = hue.onContainer(), maxLines = 1)
                    d.unit?.let { Text(" $it", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1,
                        modifier = Modifier.padding(bottom = 3.dp)) }
                }
            }
            if (d.history.count { it != null } >= 2) MiniChart(d, hue, Modifier.width(110.dp).height(34.dp))
            else d.progress?.let { ProgressBar(it.coerceIn(0f, 1f), hue, Modifier.width(96.dp), height = 8.dp, track = softTrack()) }
        }
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
    }, { k -> ringGoal(k, s.stepGoalFor(LocalDate.now()), target.kcal, target.protein, h.cardio?.target) })
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
