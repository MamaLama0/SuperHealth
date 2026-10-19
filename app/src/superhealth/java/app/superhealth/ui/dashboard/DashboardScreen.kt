package app.superhealth.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import app.superhealth.DashCard
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.CardioWeek
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.Metric
import app.superhealth.health.data.Point
import app.superhealth.nutrition.data.CheckInState
import app.superhealth.nutrition.domain.Macros
import app.superhealth.nutrition.ui.components.BarChart
import app.superhealth.nutrition.ui.components.LineChart
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.ProgressBar
import app.superhealth.nutrition.ui.components.Ring
import app.superhealth.nutrition.ui.components.ScreenTitle
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.Series
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.macros
import app.superhealth.nutrition.ui.screens.ExpenditureCard
import app.superhealth.nutrition.ui.screens.InsightCard
import app.superhealth.nutrition.ui.screens.IntakeCard
import app.superhealth.nutrition.ui.screens.RemainingRing
import app.superhealth.nutrition.ui.screens.StrategySummaryCard
import app.superhealth.nutrition.ui.screens.WeightCard
import app.superhealth.training.domain.Analytics as TrainingAnalytics
import app.superhealth.training.ui.components.BodyMap
import app.superhealth.training.ui.components.StatCell
import app.superhealth.training.ui.screens.RecordCard
import app.superhealth.training.ui.screens.WeekHabit
import app.superhealth.training.ui.screens.intensity
import app.superhealth.training.ui.volume
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.dayLetter
import app.superhealth.ui.formatNumber
import app.superhealth.ui.formatValue
import app.superhealth.ui.formatWithUnit
import app.superhealth.ui.go
import app.superhealth.ui.health.ActivityRow
import app.superhealth.ui.health.openActivity
import app.superhealth.ui.longToday
import app.superhealth.ui.localDate
import app.superhealth.ui.theme.CaloriesColor
import app.superhealth.ui.theme.CarbsColor
import app.superhealth.ui.theme.FatColor
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.ProteinColor
import app.superhealth.ui.theme.WorkoutBlue
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext

/** Cartes affichées sur une demi-largeur (rangées deux par deux quand elles se suivent). */
private val HALF = setOf(DashCard.WEIGHT, DashCard.EXPENDITURE, DashCard.HEART, DashCard.SLEEP, DashCard.CARDIO, DashCard.ENERGY, DashCard.HYDRATION)

/** Mesures santé lues pour le tableau de bord. */
private class HealthSnap(
    val series: Map<Metric, List<Pair<LocalDate, Double?>>> = emptyMap(),
    val cardio: CardioWeek? = null,
    val energy: List<Point> = emptyList(),
    val activities: List<Bridge.Activity> = emptyList(),
) {
    fun latest(m: Metric): Pair<LocalDate, Double>? = series[m]?.lastOrNull { it.second != null }?.let { it.first to it.second!! }
    fun today(m: Metric): Double? = series[m]?.lastOrNull()?.takeIf { it.first == LocalDate.now() }?.second
}

private fun Metric.dashMetrics(card: DashCard): Boolean = when (card) {
    DashCard.STEPS -> this == Metric.STEPS
    DashCard.HEART -> this == Metric.HEART_RATE || this == Metric.RESTING_HR
    DashCard.SLEEP -> this == Metric.SLEEP
    DashCard.ENERGY -> this == Metric.BODY_ENERGY || this == Metric.STRESS
    DashCard.HYDRATION -> this == Metric.HYDRATION
    DashCard.VITALS -> this in VITALS
    else -> false
}

private val VITALS = listOf(Metric.RESTING_HR, Metric.HRV, Metric.SPO2, Metric.RESPIRATORY_RATE, Metric.VO2MAX, Metric.TEMPERATURE, Metric.READINESS)

@Composable
fun DashboardScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val sh by vm.settings.collectAsState()
    val a by vm.nutrition.analytics.collectAsState()
    val ns by vm.nutrition.settings.collectAsState()
    val todayEntries by vm.nutrition.todayEntries.collectAsState()
    val tst by vm.training.state.collectAsState()
    val refresh by vm.refresh.collectAsState()
    val devices by vm.devices.collectAsState()
    val cards = sh.dashboard

    val health by produceState(HealthSnap(), refresh, cards, sh.watchModule, devices.size) {
        val today = LocalDate.now()
        val metrics = Metric.entries.filter { m -> cards.any { m.dashMetrics(it) } && Bridge.hasSource(m) }
        val series = metrics.associateWith { m ->
            val days = if (m == Metric.STEPS || m == Metric.SLEEP || m == Metric.HYDRATION) 7L else 14L
            (days - 1 downTo 0).map { today.minusDays(it) }.map { d -> d to runCatching { Bridge.healthValue(ctx, m, d) }.getOrNull() }
        }
        val cardio = if (DashCard.CARDIO in cards && Bridge.hasSource(Metric.CARDIO_WEEK)) runCatching { HealthRepository.cardioWeek(today) }.getOrNull() else null
        val energy = if (DashCard.ENERGY in cards && Bridge.hasSource(Metric.BODY_ENERGY)) runCatching { HealthRepository.intraday(Metric.BODY_ENERGY, today) }.getOrDefault(emptyList()) else emptyList()
        val acts = if (DashCard.ACTIVITIES in cards) runCatching { Bridge.activities(System.currentTimeMillis() - 30 * 86_400_000L) }.getOrDefault(emptyList()) else emptyList()
        value = HealthSnap(series, cardio, energy, acts)
    }

    fun tab(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true
    }

    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding().verticalScroll(rememberScrollState())) {
        val name = remember(refresh) { app.superhealth.ui.userName() }
        ScreenTitle(tr("Tableau de bord"), subtitle = (name?.let { trf("Bonjour %1\$s", it) + " · " } ?: "") + longToday(), actions = {
            IconButton(onClick = { nav.go("dash/edit") }) { Icon(Icons.Rounded.Tune, tr("Personnaliser")) }
        })
        val an = a ?: return@Column
        Column(Modifier.padding(horizontal = 16.dp)) {
            // Rappels contextuels (non personnalisables : ce sont des actions à faire).
            if (an.checkInState == CheckInState.NOW) {
                Reminder(Icons.Rounded.EventAvailable, tr("Check-in disponible"), tr("Mettez à jour votre programme pour la semaine.")) { nav.go("checkin") }
                Spacer(Modifier.height(12.dp))
            }
            if (an.days.lastOrNull()?.weightKg == null) {
                Reminder(Icons.Rounded.MonitorWeight, tr("Pesée du jour"), tr("Pesez-vous le matin pour une tendance fiable."), action = tr("Ajouter")) { nav.go("weight") }
                Spacer(Modifier.height(12.dp))
            }
            // Cartes « montre » choisies mais aucune source : ni montre associée, ni Health Connect.
            val noSource = cards.any { it.watch } && !(sh.watchModule && devices.isNotEmpty()) && !sh.healthConnect
            if (noSource) {
                Reminder(Icons.Rounded.Watch, tr("Constantes indisponibles"),
                    tr("Associez une montre ou connectez Health Connect pour voir votre cœur, votre sommeil et vos constantes.")) { tab("settings") }
                Spacer(Modifier.height(12.dp))
            }

            val visible = cards.filter { !it.watch || hasData(it, health) }
            var i = 0
            while (i < visible.size) {
                val c = visible[i]
                if (c in HALF && i + 1 < visible.size && visible[i + 1] in HALF) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(visible[i], vm, nav, health, Modifier.weight(1f), ::tab)
                        Card(visible[i + 1], vm, nav, health, Modifier.weight(1f), ::tab)
                    }
                    i += 2
                } else {
                    Card(c, vm, nav, health, Modifier.fillMaxWidth(), ::tab)
                    i += 1
                }
                Spacer(Modifier.height(12.dp))
            }
            if (visible.isEmpty()) {
                MfCard(onClick = { nav.go("dash/edit") }) {
                    Text(tr("Aucune carte affichée"), style = MaterialTheme.typography.titleMedium)
                    Text(tr("Touchez pour choisir vos graphiques."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
            }
            Spacer(Modifier.height(4.dp))
            PillButton(tr("Personnaliser le tableau de bord"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.Tune) { nav.go("dash/edit") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun hasData(c: DashCard, h: HealthSnap): Boolean = when (c) {
    DashCard.HEART -> h.series[Metric.HEART_RATE] != null || h.series[Metric.RESTING_HR] != null
    DashCard.SLEEP -> h.series[Metric.SLEEP] != null
    DashCard.CARDIO -> h.cardio != null
    DashCard.ENERGY -> h.series[Metric.BODY_ENERGY] != null || h.series[Metric.STRESS] != null
    DashCard.VITALS -> VITALS.any { h.series[it] != null }
    else -> true
}

@Composable
private fun Reminder(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, action: String? = null, onClick: () -> Unit) {
    MfCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(28.dp), tint = Mf.colors.accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
            Spacer(Modifier.width(8.dp))
            if (action != null) PillButton(action, strong = true, onClick = onClick)
            else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
        }
    }
}

@Composable
private fun CardHeader(title: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary, maxLines = 1) }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
    }
}

@Composable
private fun Card(c: DashCard, vm: SuperViewModel, nav: NavHostController, h: HealthSnap, modifier: Modifier, tab: (String) -> Unit) {
    val an = vm.nutrition.analytics.collectAsState().value ?: return
    val s by vm.nutrition.settings.collectAsState()
    when (c) {
        DashCard.CALORIES -> {
            val todayEntries by vm.nutrition.todayEntries.collectAsState()
            val consumed = todayEntries.fold(Macros.ZERO) { acc, e -> acc + e.macros() }
            val target = an.targetFor(an.today).macros
            val left = s.energyValue(target.kcal - consumed.kcal)
            MfCard(modifier, onClick = { vm.nutrition.selectedDay.value = an.today; tab("log") }) {
                CardHeader(tr("Calories restantes"))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatNumber(abs(left)), style = MaterialTheme.typography.displaySmall)
                    Text(" " + s.energyUnit.label + if (left < 0) " " + tr("en trop") else "",
                        style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 5.dp), maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                ProgressBar(if (target.kcal > 0) (consumed.kcal / target.kcal).toFloat() else 0f, CaloriesColor, height = 6.dp)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(trf("Consommé %1\$s", formatNumber(s.energyValue(consumed.kcal))), style = MaterialTheme.typography.bodySmall,
                        color = Mf.colors.textSecondary, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(trf("Objectif %1\$s", formatNumber(s.energyValue(target.kcal))), style = MaterialTheme.typography.bodySmall,
                        color = Mf.colors.textSecondary, maxLines = 1)
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RemainingRing(tr("Protéines"), consumed.protein, target.protein, ProteinColor)
                    RemainingRing(tr("Lipides"), consumed.fat, target.fat, FatColor)
                    RemainingRing(tr("Glucides"), consumed.carbs, target.carbs, CarbsColor)
                }
            }
        }
        DashCard.WEIGHT -> WeightCard(an, s, modifier) { nav.go("weight") }
        DashCard.EXPENDITURE -> ExpenditureCard(an, s, modifier) { nav.go("expenditure") }
        DashCard.INTAKE -> Box(modifier) { IntakeCard(an, s) { nav.go("nutrition") } }
        DashCard.MACROS -> {
            val days = (an.today - 6..an.today)
            val entries by vm.nutrition.repo.entries.allFlow().collectAsState(emptyList())
            val logged = entries.filter { it.epochDay in days }.groupBy { it.epochDay }
            val n = logged.size.coerceAtLeast(1)
            val avg = logged.values.flatten().fold(Macros.ZERO) { acc, e -> acc + e.macros() }.let { Macros(it.kcal / n, it.protein / n, it.fat / n, it.carbs / n) }
            val tgt = days.map { an.targetFor(it).macros }.let { l -> Macros(l.sumOf { it.kcal } / 7, l.sumOf { it.protein } / 7, l.sumOf { it.fat } / 7, l.sumOf { it.carbs } / 7) }
            MfCard(modifier, onClick = { nav.go("macros") }) {
                CardHeader(tr("Macros de la semaine"), trf("%1\$s j journalisés", logged.size))
                Spacer(Modifier.height(10.dp))
                MacroBar(tr("Protéines"), avg.protein, tgt.protein, ProteinColor)
                MacroBar(tr("Lipides"), avg.fat, tgt.fat, FatColor)
                MacroBar(tr("Glucides"), avg.carbs, tgt.carbs, CarbsColor)
            }
        }
        DashCard.STRATEGY -> Box(modifier) { StrategySummaryCard(an, s) { nav.go("strategy") } }
        DashCard.STEPS -> {
            val series = h.series[Metric.STEPS].orEmpty()
            val today = h.today(Metric.STEPS)
            val goal = s.stepGoal
            MfCard(modifier, onClick = { nav.go("h/metric/STEPS") }) {
                CardHeader(tr("Pas"), series.mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.let { trf("Moy. %1\$s", formatNumber(it.average())) })
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(today?.let { formatNumber(it) } ?: "—", style = MaterialTheme.typography.headlineMedium)
                    if (goal > 0) Text(" / " + formatNumber(goal.toDouble()), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                if (goal > 0) { Spacer(Modifier.height(6.dp)); ProgressBar(((today ?: 0.0) / goal).toFloat(), HealthHues.Activity, height = 6.dp) }
                Spacer(Modifier.height(12.dp))
                BarChart(series.map { it.second }, series.map { goal.takeIf { g -> g > 0 }?.toDouble() }, HealthHues.Activity, height = 80.dp,
                    labels = series.map { dayLetter(it.first) })
            }
        }
        DashCard.HEART -> {
            val rest = h.series[Metric.RESTING_HR]
            val main = if (rest?.any { it.second != null } == true) Metric.RESTING_HR else Metric.HEART_RATE
            val pts = h.series[main].orEmpty().mapNotNull { (d, v) -> v?.let { d.toEpochDay() to it } }
            InsightCard(tr(if (main == Metric.RESTING_HR) "FC au repos" else "Fréquence cardiaque"), h.latest(main)?.second?.let { formatNumber(it) } ?: "—", tr("bpm"),
                h.latest(Metric.HEART_RATE)?.takeIf { main == Metric.RESTING_HR }?.let { trf("Moyenne %1\$s bpm", formatNumber(it.second)) },
                modifier, { nav.go("h/metric/${main.name}") }) {
                LineChart(listOf(Series(pts, HealthHues.Heart, dots = true)), height = 56.dp, showAxis = false)
            }
        }
        DashCard.SLEEP -> {
            val series = h.series[Metric.SLEEP].orEmpty()
            val last = h.latest(Metric.SLEEP)
            InsightCard(tr("Sommeil"), last?.let { formatValue(Metric.SLEEP, it.second) } ?: "—", "",
                series.mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.let { trf("Moy. %1\$s", formatValue(Metric.SLEEP, it.average())) },
                modifier, { nav.go("h/metric/SLEEP") }) {
                BarChart(series.map { it.second?.div(60) }, series.map { 8.0 }, HealthHues.Sleep, height = 56.dp)
            }
        }
        DashCard.CARDIO -> {
            val cw = h.cardio ?: return
            MfCard(modifier, onClick = { nav.go("h/metric/CARDIO_WEEK") }, padding = PaddingValues(14.dp)) {
                Text(tr("Minutes cardio"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Ring(cw.progress, HealthHues.Activity, 84.dp, stroke = 8.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${cw.total}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("/ ${cw.target}", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(trf("+%1\$s aujourd'hui", cw.today), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
            }
        }
        DashCard.ENERGY -> {
            val energy = h.latest(Metric.BODY_ENERGY)
            val stress = h.latest(Metric.STRESS)
            val main = if (energy != null) Metric.BODY_ENERGY else Metric.STRESS
            InsightCard(tr(main.label), (energy ?: stress)?.second?.let { formatNumber(it) } ?: "—", "/ 100",
                if (energy != null && stress != null) trf("Stress %1\$s", formatNumber(stress.second)) else null,
                modifier, { nav.go("h/metric/${main.name}") }) {
                val pts = if (h.energy.size >= 2) h.energy.map { it.timestampMs / 60000 to it.value }
                    else h.series[main].orEmpty().mapNotNull { (d, v) -> v?.let { d.toEpochDay() to it } }
                LineChart(listOf(Series(pts, if (main == Metric.BODY_ENERGY) HealthHues.Activity else HealthHues.Mind)), height = 56.dp, showAxis = false)
            }
        }
        DashCard.HYDRATION -> {
            val today = h.today(Metric.HYDRATION) ?: 0.0
            val goal = HealthRepository.goal(Metric.HYDRATION) ?: 2000.0
            MfCard(modifier, onClick = { nav.go("h/metric/HYDRATION") }, padding = PaddingValues(14.dp)) {
                Text(tr("Hydratation"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatNumber(today), style = MaterialTheme.typography.headlineMedium)
                    Text(" ml", style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(trf("Objectif %1\$s ml", formatNumber(goal)), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                ProgressBar((today / goal).toFloat(), HealthHues.Respiration, height = 6.dp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().clip(CircleShape).background(Mf.colors.chip).clickable { vm.log(Metric.HYDRATION, 250.0) }.padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                    Text("250 ml", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
        DashCard.VITALS -> {
            val tiles = VITALS.filter { h.series[it] != null }
            MfCard(modifier) {
                CardHeader(tr("Constantes"))
                Spacer(Modifier.height(10.dp))
                tiles.chunked(3).forEachIndexed { i, row ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { m -> VitalTile(m, h.latest(m), Modifier.weight(1f)) { nav.go("h/metric/${m.name}") } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        DashCard.ACTIVITIES -> {
            val ctx = LocalContext.current
            MfCard(modifier, padding = PaddingValues(vertical = 12.dp)) {
                Row(Modifier.padding(horizontal = 16.dp).clickable { nav.go("h/activities") }) { CardHeader(tr("Dernières activités")) }
                if (h.activities.isEmpty()) Text(tr("Aucune activité ces 30 derniers jours."), style = MaterialTheme.typography.bodyMedium,
                    color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                h.activities.take(4).forEach { act -> ActivityRow(act) { openActivity(ctx, nav, act) } }
            }
        }
        DashCard.TRAINING_WEEK -> {
            val st by vm.training.state.collectAsState()
            val weekStart = TrainingAnalytics.weekStart(LocalDate.now())
            val thisWeek = st.history.filter { !localDate(it.startTime).isBefore(weekStart) }
            MfCard(modifier, onClick = { nav.go("t/insights") }) {
                CardHeader(tr("Semaine d'entraînement"))
                Spacer(Modifier.height(8.dp))
                Row {
                    StatCell("${thisWeek.size}", tr("Séances"), Modifier.weight(1f))
                    StatCell("${thisWeek.sumOf { w -> w.exercises.sumOf { TrainingAnalytics.workingSets(it).size } }}", tr("Séries"), Modifier.weight(1f))
                    StatCell(st.settings.volume(thisWeek.sumOf { w -> w.exercises.sumOf { TrainingAnalytics.volume(it.sets) } }), tr("Volume"), Modifier.weight(1.3f))
                }
                Spacer(Modifier.height(14.dp))
                WeekHabit(st.history)
            }
        }
        DashCard.MUSCLES -> {
            val st by vm.training.state.collectAsState()
            val db = vm.training.db.collectAsState().value ?: return
            val weekStart = TrainingAnalytics.weekStart(LocalDate.now())
            val sets = TrainingAnalytics.setsByMuscle(st.history.filter { !localDate(it.startTime).isBefore(weekStart) }, db)
            MfCard(modifier, onClick = { nav.go("levels") }) {
                CardHeader(tr("Muscles travaillés cette semaine"))
                Spacer(Modifier.height(8.dp))
                BodyMap(intensity(sets, TrainingAnalytics.SETS_HIGH), st.settings.sex, Modifier.fillMaxWidth().height(220.dp))
            }
        }
        DashCard.RECORDS -> {
            val st by vm.training.state.collectAsState()
            val db = vm.training.db.collectAsState().value ?: return
            val records = remember(st.history) {
                val sorted = st.history.sortedBy { it.startTime }
                sorted.flatMapIndexed { i, w -> TrainingAnalytics.newRecords(w, sorted.take(i), db).map { w to it } }.takeLast(6).reversed()
            }
            Column(modifier) {
                SectionLabel(tr("Records récents"), Modifier.padding(top = 0.dp))
                if (records.isEmpty()) MfCard { Text(tr("Vos records apparaîtront après vos premières séances."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary) }
                else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    records.forEach { (w, r) -> RecordCard(db, st.settings, r, app.superhealth.training.ui.shortDate(w.startTime)) { nav.go("exercise/${r.exerciseId}") } }
                }
            }
        }
    }
}

@Composable
private fun MacroBar(label: String, value: Double, target: Double, color: Color) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${value.roundToInt()} / ${target.roundToInt()} g", style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        ProgressBar(if (target > 0) (value / target).toFloat() else 0f, color, height = 5.dp)
    }
}

@Composable
private fun VitalTile(m: Metric, latest: Pair<LocalDate, Double>?, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(Mf.colors.chip).clickable(onClick = onClick).padding(10.dp)) {
        Icon(m.icon, null, Modifier.size(18.dp), tint = m.color)
        Spacer(Modifier.height(6.dp))
        Text(latest?.let { formatWithUnit(m, it.second) } ?: "—", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(tr(m.label), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
