package app.superhealth.nutrition.ui.screens

import app.superhealth.platform.*

import app.superhealth.nutrition.ui.trf
import app.superhealth.nutrition.ui.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import app.superhealth.nutrition.domain.ExpenditureVersion
import app.superhealth.nutrition.domain.GoalType
import app.superhealth.nutrition.domain.Macros
import app.superhealth.nutrition.domain.PredictionStyle
import app.superhealth.nutrition.domain.Targets
import app.superhealth.ui.charts.SignedBarChart
import app.superhealth.ui.charts.WaterfallChart
import kotlin.math.abs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.superhealth.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.superhealth.nutrition.data.WeighInEntity
import app.superhealth.nutrition.domain.Expenditure
import app.superhealth.nutrition.domain.Sex
import app.superhealth.nutrition.ui.AppViewModel
import app.superhealth.nutrition.ui.components.AssetImage
import app.superhealth.ui.charts.BarChart
import app.superhealth.nutrition.ui.components.BigStat
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.ui.charts.LineChart
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.NumberField
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.ProgressBar
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.ui.charts.Series
import app.superhealth.nutrition.ui.dayLabel
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.num
import app.superhealth.ui.theme.Base
import app.superhealth.ui.theme.CaloriesColor
import app.superhealth.ui.theme.CarbsColor
import app.superhealth.ui.theme.FatColor
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.ProteinColor
import app.superhealth.nutrition.ui.toKg
import app.superhealth.nutrition.ui.toNum
import app.superhealth.nutrition.ui.weight
import app.superhealth.nutrition.ui.weightValue
import app.superhealth.nutrition.data.FoodEntryEntity
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.platform.LocalDate
import kotlin.math.roundToInt

enum class Range(val label: String, val days: Int?) { W1("1S", 7), M1("1M", 30), M3("3M", 90), M6("6M", 182), Y1("1A", 365), ALL(tr("Tout"), null) }

@Composable
fun DetailScaffold(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding()) {
        app.superhealth.nutrition.ui.components.NavBar(tr(title), onBack) { action?.invoke() }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(), content = content)
    }
}

private fun <T> List<T>.inRange(range: Range, today: Long, day: (T) -> Long) = range.days?.let { n -> filter { day(it) > today - n } } ?: this

/** Tableau de variations sur 3/7/14/30/90 jours (ExpenditureViewModel change3…change90). */
@Composable
fun ChangeTable(values: Map<Long, Double>, today: Long, fmt: (Double) -> String) {
    val now = values.entries.lastOrNull { it.key <= today }?.value
    Row(Modifier.fillMaxWidth()) {
        listOf(3, 7, 14, 30, 90).forEach { k ->
            val then = values[today - k]
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (now != null && then != null) fmt(now - then) else "—", style = MaterialTheme.typography.titleSmall)
                Text(trf("%1\$s j", k), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
            }
        }
    }
}

@Composable
fun ExpenditureScreen(vm: AppViewModel, onBack: () -> Unit) {
    val a by vm.analytics.collectAsState()
    val s by vm.settings.collectAsState()
    var range by remember { mutableStateOf(Range.M3) }
    DetailScaffold(tr("Dépense énergétique"), onBack) {
        val an = a ?: return@DetailScaffold
        val pts = an.expenditure.inRange(range, an.today) { it.epochDay }
        val last = an.expenditure.lastOrNull()
        MfCard {
            BigStat(s.energyValue(an.currentTdee).roundToInt().toString(), tr("Dépense estimée actuelle"), unit = s.energyUnit.label.tr())
            Spacer(Modifier.height(16.dp))
            LineChart(listOf(
                Series(pts.map { it.epochDay to s.energyValue(it.short) }, Base.Purple.copy(alpha = 0.35f), width = 1.5f),
                Series(pts.map { it.epochDay to s.energyValue(it.long) }, Base.Purple.copy(alpha = 0.35f), width = 1.5f, dashed = true),
                Series(pts.map { it.epochDay to s.energyValue(it.tdee) }, Base.Purple),
            ), height = 220.dp, format = { it.roundToInt().toString() })
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 14.dp, height = 3.dp).background(Base.Purple)); Text(tr("  Dépense   "), style = MaterialTheme.typography.labelSmall)
                Box(Modifier.size(width = 14.dp, height = 2.dp).background(Base.Purple.copy(alpha = 0.35f))); Text(tr("  Lissages court / long"), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(12.dp))
            ChipChoice(Range.entries, range, { it.label.tr() }) { range = it }
        }
        SectionLabel(tr("Variations"))
        MfCard { ChangeTable(an.expenditure.associate { it.epochDay to s.energyValue(it.tdee) }, an.today) { signed(it, 0) } }
        SectionLabel(tr("Statut"))
        MfCard {
            val (title, text) = when (last?.status) {
                Expenditure.Status.HOLDING -> tr("En pause") to tr("Pas assez de jours complets (3 sur 7 minimum). Reprenez la journalisation.")
                Expenditure.Status.UPDATING -> tr("Mise à jour en continu") to tr("Recalculée chaque jour à partir des apports et du poids. Fiable après 3 à 4 semaines.")
                else -> tr("Estimation initiale") to tr("Basée sur votre profil. S'ajuste dès le 3e jour.")
            }
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            run {
                val adj = an.expenditure.filter { it.epochDay > an.today - 14 }.sumOf { it.goalAdjustment }
                if (abs(adj) > 0.5) Text(trf("Ajustement prédictif (changement d'objectif) sur 14 j : %1\$s %2\$s", (signed(s.energyValue(adj), 0)), s.energyUnit.label.tr()),
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.accent, modifier = Modifier.padding(top = 8.dp))
            }
            last?.let { Text(trf("Jours de nutrition manquants (7 j) : %1\$s", (it.missingDays.roundToInt())), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 6.dp)) }
        }
        SectionLabel(tr("Statistiques"))
        MfCard {
            val first = pts.firstOrNull()?.tdee
            val avg = pts.map { it.tdee }.takeIf { it.isNotEmpty() }?.average()
            Row {
                BigStat(avg?.let { s.energyValue(it).roundToInt().toString() } ?: "—", tr("Moyenne"), Modifier.weight(1f), unit = s.energyUnit.label.tr())
                BigStat(first?.let { signed(s.energyValue(an.currentTdee - it), 0) } ?: "—", trf("Variation (%1\$s)", range.label.tr()), Modifier.weight(1f), unit = s.energyUnit.label.tr())
            }
            Spacer(Modifier.height(8.dp))
            Text(trf("Dépense initiale : %1\$s %2\$s", (s.energyValue(an.initialTdee).roundToInt()), s.energyUnit.label.tr()), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Pente de la tendance (kg/jour) sur les [days] derniers jours. */
fun trendSlope(trend: List<app.superhealth.nutrition.data.TrendDay>, today: Long, days: Int = 14): Double? {
    val pts = trend.filter { it.epochDay in (today - days)..today }
    if (pts.size < 2) return null
    return (pts.last().trend - pts.first().trend) / (pts.last().epochDay - pts.first().epochDay)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(vm: AppViewModel, onBack: () -> Unit) {
    val a by vm.analytics.collectAsState()
    val s by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    var range by remember { mutableStateOf(Range.M1) }
    var editor by remember { mutableStateOf<WeighInEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    DetailScaffold(tr("Tendance du poids"), onBack, action = {
        IconButton(onClick = { adding = true }) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Mf.colors.strong), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, tr("Ajouter une pesée"), tint = Mf.colors.onStrong, modifier = Modifier.size(20.dp))
            }
        }
    }) {
        val an = a ?: return@DetailScaffold
        val pts = an.trend.filter { it.epochDay <= an.today }.inRange(range, an.today) { it.epochDay }
        val goal = an.currentGoal
        // Prédiction à 30 jours selon le style choisi.
        val dataSlope = trendSlope(an.trend, an.today)
        val goalSlope = goal?.let { Targets.weeklyRateKg(it, an.currentTrendKg ?: it.startWeightKg) / 7 }
        val slope = when (s.predictionStyle) {
            PredictionStyle.WEIGHT_DATA -> dataSlope ?: goalSlope
            PredictionStyle.GOAL_RATE -> goalSlope
            PredictionStyle.BLENDED -> listOfNotNull(dataSlope, goalSlope).takeIf { it.isNotEmpty() }?.average()
        }
        val proj = an.currentTrendKg?.let { t -> slope?.let { (0..30).map { d -> (an.today + d) to s.weightValue(t + it * d) } } }
        MfCard {
            Row {
                BigStat(an.currentTrendKg?.let { num(s.weightValue(it), 1) } ?: "—", tr("Tendance"), Modifier.weight(1f), unit = s.weightUnit.label.tr())
                BigStat(dataSlope?.let { signed(s.weightValue(it * 7), 2) } ?: "—", tr("par semaine"), Modifier.weight(1f), unit = s.weightUnit.label.tr())
            }
            Spacer(Modifier.height(16.dp))
            LineChart(listOfNotNull(
                Series(pts.filter { it.scale != null }.map { it.epochDay to s.weightValue(it.scale!!) }, Mf.colors.textSecondary, dots = true),
                Series(pts.map { it.epochDay to s.weightValue(it.trend) }, Mf.colors.accent,
                    band = pts.filter { it.sd > 0 }.map { Triple(it.epochDay, s.weightValue(it.trend - 2 * it.sd), s.weightValue(it.trend + 2 * it.sd)) }),
                proj?.takeIf { range.days == null || range.days!! <= 90 }?.let { Series(it, Mf.colors.accent.copy(alpha = 0.6f), dashed = true) },
            ), height = 230.dp)
            Spacer(Modifier.height(12.dp))
            ChipChoice(Range.entries, range, { it.label.tr() }) { range = it }
        }
        SectionLabel(tr("Variations de la tendance"))
        MfCard { ChangeTable(an.trend.associate { it.epochDay to s.weightValue(it.trend) }, an.today) { signed(it, 1) } }
        proj?.let {
            SectionLabel(tr("Projection à 30 jours"))
            MfCard {
                BigStat(num(it.last().second, 1), tr("Poids projeté dans 30 jours"), unit = s.weightUnit.label.tr())
                Text(s.predictionStyle.description.tr(), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(8.dp))
                ChipChoice(PredictionStyle.entries, s.predictionStyle, { it.label.tr() }) { st -> vm.repo.prefs.setSettings(s.copy(predictionStyle = st)) }
            }
        }
        SectionLabel(tr("Pesées"))
        val list = an.days.filter { it.weightKg != null }.reversed().take(120)
        if (list.isEmpty()) Text(tr("Aucune pesée. Touchez + pour en ajouter."), color = Mf.colors.textSecondary, modifier = Modifier.padding(8.dp))
        else MfCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            list.forEachIndexed { i, d ->
                if (i > 0) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(Mf.colors.divider))
                Row(Modifier.fillMaxWidth().clickable { editor = WeighInEntity(d.epochDay, d.weightKg!!, d.bodyFatPct) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(dayLabel(d.epochDay).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    d.bodyFatPct?.let { Text("${num(it, 1)} %  ", style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary) }
                    Text(s.weight(d.weightKg), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("  " + (an.trendByDay[d.epochDay]?.let { num(s.weightValue(it), 1) } ?: ""), style = MaterialTheme.typography.bodySmall, color = Mf.colors.accent)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (adding || editor != null) {
        val existing = editor
        WeighInSheet(existing, s, profile.sex, onDismiss = { adding = false; editor = null },
            onDelete = existing?.let { e -> { vm.launch { weighIns.delete(e.epochDay) }; editor = null } },
            onSave = { w -> vm.launch { weighIns.upsert(w) }; adding = false; editor = null })
    }
}

/** Balance énergétique : apport − dépense par jour (surplus / déficit). */
@Composable
fun EnergyBalanceScreen(vm: AppViewModel, onBack: () -> Unit) {
    val a by vm.analytics.collectAsState()
    val s by vm.settings.collectAsState()
    var range by remember { mutableStateOf(Range.M1) }
    DetailScaffold(tr("Balance énergétique"), onBack) {
        val an = a ?: return@DetailScaffold
        val n = range.days ?: an.days.size
        val days = (an.today - n + 1..an.today).toList()
        val byDay = an.days.associateBy { it.epochDay }
        val bal = days.map { d -> byDay[d]?.intakeKcal?.let { it - an.tdeeAt(d) } }
        val logged = bal.filterNotNull()
        MfCard {
            BigStat(logged.takeIf { it.isNotEmpty() }?.average()?.let { signed(s.energyValue(it), 0) } ?: "—",
                tr("Balance moyenne par jour journalisé"), unit = s.energyUnit.label.tr())
            Spacer(Modifier.height(16.dp))
            SignedBarChart(bal.map { it?.let(s::energyValue) }, Base.Green, Base.Red, height = 180.dp)
            Spacer(Modifier.height(12.dp))
            ChipChoice(Range.entries, range, { it.label.tr() }) { range = it }
        }
        SectionLabel(tr("Lecture"))
        MfCard {
            val avg = logged.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            Text(tr("Barres vertes : surplus (apport > dépense) ; rouges : déficit."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            Text(trf("Variation théorique : %1\$s %2\$s par semaine.", (signed(s.weightValue(avg * 7 / 7700), 2)), s.weightUnit.label.tr()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Progression vers l'objectif : cascade des variations hebdomadaires de la tendance. */
@Composable
fun GoalProgressScreen(vm: AppViewModel, onBack: () -> Unit) {
    val a by vm.analytics.collectAsState()
    val s by vm.settings.collectAsState()
    DetailScaffold(tr("Progression de l'objectif"), onBack) {
        val an = a ?: return@DetailScaffold
        val g = an.currentGoal
        val target = g?.targetWeightKg
        if (g == null || target == null || g.type == GoalType.MAINTENANCE) {
            MfCard { Text(tr("Définissez un poids cible pour suivre votre progression."), style = MaterialTheme.typography.bodyMedium) }
            return@DetailScaffold
        }
        val weekly = (g.startDay..an.today step 7).mapNotNull { d -> an.trendByDay[d] }
        val deltas = weekly.zipWithNext { x, y -> y - x }
        val trend = an.currentTrendKg ?: g.startWeightKg
        val toward: (Double) -> Boolean = { d -> if (g.type == GoalType.CUT) d < 0 else d > 0 }
        MfCard {
            Row {
                BigStat(num(s.weightValue(abs(trend - g.startWeightKg)), 1), tr("Parcouru"), Modifier.weight(1f), unit = s.weightUnit.label.tr())
                BigStat(num(s.weightValue(abs(target - trend)), 1), tr("Restant"), Modifier.weight(1f), unit = s.weightUnit.label.tr())
                BigStat("${(an.today - g.startDay)}", tr("Jours"), Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            WaterfallChart(target - g.startWeightKg, deltas, target - trend, toward, height = 190.dp)
            Text(tr("Départ, variations par semaine (en couleur : vers l'objectif), reste."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Historique des macros journalisées contre les cibles. */
@Composable
fun MacrosScreen(vm: AppViewModel, onBack: () -> Unit) {
    val a by vm.analytics.collectAsState()
    var range by remember { mutableStateOf(Range.W1) }
    val allEntries by remember { vm.repo.entries.allFlow() }.collectAsState(initial = emptyList())
    DetailScaffold(tr("Macros"), onBack) {
        val an = a ?: return@DetailScaffold
        val n = range.days ?: an.days.size
        val days = (an.today - n + 1..an.today).toList()
        val byDay = allEntries.groupBy { it.epochDay }
        listOf(
            Triple(tr("Protéines"), ProteinColor) { e: app.superhealth.nutrition.data.FoodEntryEntity -> e.protein } to { m: Macros -> m.protein },
            Triple(tr("Lipides"), FatColor) { e: app.superhealth.nutrition.data.FoodEntryEntity -> e.fat } to { m: Macros -> m.fat },
            Triple(tr("Glucides"), CarbsColor) { e: app.superhealth.nutrition.data.FoodEntryEntity -> e.carbs } to { m: Macros -> m.carbs },
        ).forEach { (meta, tgt) ->
            val (label, color, get) = meta
            val vals = days.map { d -> byDay[d]?.sumOf(get) }
            SectionLabel(label)
            MfCard {
                BigStat(vals.filterNotNull().takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.toString() ?: "—", tr("g par jour en moyenne"))
                Spacer(Modifier.height(12.dp))
                BarChart(vals, days.map { tgt(an.targetFor(it).macros) }, color, height = 110.dp,
                    labels = if (days.size <= 7) days.map { app.superhealth.nutrition.ui.dayLetter(LocalDate.ofEpochDay(it).dayOfWeek.value) } else emptyList())
            }
        }
        Spacer(Modifier.height(12.dp))
        ChipChoice(Range.entries, range, { it.label.tr() }) { range = it }
        Spacer(Modifier.height(24.dp))
    }
}

/** Saisie d'une pesée. [onSkip] : bouton « Pas aujourd'hui » (demande de pesée quotidienne). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeighInSheet(existing: WeighInEntity?, s: app.superhealth.nutrition.domain.Settings, sex: Sex, onDismiss: () -> Unit, onDelete: (() -> Unit)?,
                 onSkip: (() -> Unit)? = null, onSave: (WeighInEntity) -> Unit) {
    var day by remember { mutableLongStateOf(existing?.epochDay ?: LocalDate.now().toEpochDay()) }
    var w by remember { mutableStateOf(existing?.let { num(s.weightValue(it.weightKg), 1) } ?: "") }
    var bf by remember { mutableStateOf(existing?.bodyFatPct?.let { num(it, 1) } ?: "") }
    val ctx = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.card, dragHandle = null) {
        Column(Modifier.padding(20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (existing == null) tr("Nouvelle pesée") else tr("Modifier la pesée"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(CircleShape).background(Mf.colors.chip).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, tr("Fermer"), Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(dayLabel(day).replaceFirstChar { it.uppercase() }, Modifier.clip(CircleShape).background(Mf.colors.chip).clickable(enabled = existing == null) {
                val d = LocalDate.ofEpochDay(day)
                DatePickerDialog(ctx, { _, y, m, dd -> day = LocalDate.of(y, m + 1, dd).toEpochDay() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
            }.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
            NumberField(tr("Poids"), w, { w = it }, suffix = s.weightUnit.label.tr())
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(tr("Masse grasse (facultatif)"), bf, { bf = it }, Modifier.weight(1f), suffix = "%")
                bf.toNum()?.let { pct -> BodyFatSilhouette(sex, pct, Modifier.padding(start = 12.dp).size(72.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                onDelete?.let { PillButton(tr("Supprimer"), Modifier.weight(1f), strong = false, onClick = it) }
                onSkip?.let { PillButton(tr("Pas aujourd'hui"), Modifier.weight(1f), strong = false, onClick = it) }
                PillButton(tr("Enregistrer"), Modifier.weight(1f), enabled = (w.toNum() ?: 0.0) > 0) {
                    onSave(WeighInEntity(day, s.toKg(w.toNum()!!), bf.toNum()?.takeIf { it in 2.0..70.0 }))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Silhouette MacroFactor la plus proche du % de masse grasse saisi. */
@Composable
fun BodyFatSilhouette(sex: Sex, pct: Double, modifier: Modifier = Modifier) {
    val steps = if (sex == Sex.MALE) listOf(3, 6, 10, 15, 20, 25, 30, 35, 40) else listOf(10, 15, 20, 25, 30, 35, 40, 45, 50)
    val nearest = steps.minBy { kotlin.math.abs(it - pct) }
    AssetImage("bodyfat/${if (sex == Sex.MALE) "m" else "f"}_${nearest}pct.webp", modifier.clip(RoundedCornerShape(12.dp)).background(Mf.colors.chip))
}

@Composable
fun NutritionScreen(vm: AppViewModel, onBack: () -> Unit) {
    val a by vm.analytics.collectAsState()
    val s by vm.settings.collectAsState()
    var range by remember { mutableStateOf(Range.W1) }
    val allEntries by remember { vm.repo.entries.allFlow() }.collectAsState(initial = emptyList())
    DetailScaffold(tr("Apports"), onBack) {
        val an = a ?: return@DetailScaffold
        val n = range.days ?: an.days.size
        val days = (an.today - n + 1..an.today).toList()
        val byDay = an.days.associateBy { it.epochDay }
        val logged = days.mapNotNull { byDay[it]?.intakeKcal }
        MfCard {
            BigStat(logged.takeIf { it.isNotEmpty() }?.average()?.let { s.energyValue(it).roundToInt().toString() } ?: "—",
                trf("Moyenne journalière (%1\$s j journalisés)", logged.size), unit = s.energyUnit.label.tr())
            Spacer(Modifier.height(16.dp))
            BarChart(days.map { byDay[it]?.intakeKcal?.let(s::energyValue) }, days.map { s.energyValue(an.targetFor(it).macros.kcal) }, CaloriesColor,
                height = 180.dp, labels = if (days.size <= 7) days.map { app.superhealth.nutrition.ui.dayLetter(LocalDate.ofEpochDay(it).dayOfWeek.value) } else emptyList())
            Spacer(Modifier.height(12.dp))
            ChipChoice(Range.entries, range, { it.label.tr() }) { range = it }
        }
        SectionLabel(tr("Respect des cibles"))
        MfCard {
            val inRange = allEntries.filter { it.epochDay in days.first()..days.last() }.groupBy { it.epochDay }.values
            fun avg(f: (app.superhealth.nutrition.data.FoodEntryEntity) -> Double) = inRange.map { d -> d.sumOf(f) }.takeIf { it.isNotEmpty() }?.average()
            val targets = days.map { an.targetFor(it).macros }
            AdherenceRow(tr("Calories"), logged.takeIf { it.isNotEmpty() }?.average(), targets.map { it.kcal }.average(), CaloriesColor) { s.energyValue(it).roundToInt().toString() }
            Spacer(Modifier.height(14.dp))
            AdherenceRow(tr("Protéines"), avg { it.protein }, targets.map { it.protein }.average(), ProteinColor) { trf("%1\$s g", (it.roundToInt())) }
            Spacer(Modifier.height(14.dp))
            AdherenceRow(tr("Lipides"), avg { it.fat }, targets.map { it.fat }.average(), FatColor) { trf("%1\$s g", (it.roundToInt())) }
            Spacer(Modifier.height(14.dp))
            AdherenceRow(tr("Glucides"), avg { it.carbs }, targets.map { it.carbs }.average(), CarbsColor) { trf("%1\$s g", (it.roundToInt())) }
            Text(tr("Moyennes sur les jours journalisés, comparées à la cible moyenne de la période."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AdherenceRow(label: String, avg: Double?, target: Double, color: androidx.compose.ui.graphics.Color, fmt: (Double) -> String) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${avg?.let(fmt) ?: "—"} / ${fmt(target)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(((avg ?: 0.0) / target).toFloat(), color, height = 6.dp)
    }
}

/** Valeur d'un nutriment (code Micros ou macro : kcal, protein, fat, carbs, fiber) dans une entrée. */
private fun FoodEntryEntity.nutrient(code: String): Double = when (code) {
    "kcal" -> kcal; "protein" -> protein; "fat" -> fat; "carbs" -> carbs; "fiber" -> fiber
    else -> app.superhealth.nutrition.data.Micros.parse(microsJson)[code] ?: 0.0
}

/**
 * Explorateur de nutriments : moyenne par jour journalisé contre la cible (référence ou cible personnalisée,
 * MicroGoalType auto/custom). Toucher un nutriment ouvre ses principaux contributeurs (Nutrition Contributors).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NutrientsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val a by vm.analytics.collectAsState()
    val s by vm.settings.collectAsState()
    var range by remember { mutableStateOf(Range.W1) }
    var open by remember { mutableStateOf<app.superhealth.nutrition.data.Micros.Def?>(null) }
    val allEntries by remember { vm.repo.entries.allFlow() }.collectAsState(initial = emptyList())
    val an = a
    val n = range.days ?: an?.days?.size ?: 7
    val inRange = if (an == null) emptyList() else allEntries.filter { it.epochDay > an.today - n }
    val loggedDays = inRange.map { it.epochDay }.distinct().size.coerceAtLeast(1)
    DetailScaffold(tr("Nutriments"), onBack) {
        if (an == null) return@DetailScaffold
        val totals = app.superhealth.nutrition.data.Micros.sum(inRange.map { it.microsJson }) + mapOf("fiber" to inRange.sumOf { it.fiber })
        ChipChoice(Range.entries, range, { it.label.tr() }) { range = it }
        Spacer(Modifier.height(8.dp))
        // cible moyenne du programme (les jours hauts ne faussent pas la comparaison des moyennes)
        val t = (an.checkInFor(an.today) ?: an.provisionalCheckIn(an.today)).let { Macros(it.kcal, it.protein, it.fat, it.carbs) }
        val macroDefs = listOf(
            app.superhealth.nutrition.data.Micros.Def("kcal", tr("Énergie"), s.energyUnit.label.tr(), tr("Macronutriments"), t.kcal),
            app.superhealth.nutrition.data.Micros.Def("protein", tr("Protéines"), "g", tr("Macronutriments"), t.protein),
            app.superhealth.nutrition.data.Micros.Def("fat", tr("Lipides"), "g", tr("Macronutriments"), t.fat),
            app.superhealth.nutrition.data.Micros.Def("carbs", tr("Glucides"), "g", tr("Macronutriments"), t.carbs),
        )
        val macroTotals = mapOf("kcal" to inRange.sumOf { it.kcal }, "protein" to inRange.sumOf { it.protein },
            "fat" to inRange.sumOf { it.fat }, "carbs" to inRange.sumOf { it.carbs })
        val defs = macroDefs + app.superhealth.nutrition.data.Micros.defs.filter { (totals[it.code] ?: 0.0) > 0 || it.code in s.microTargets }
        defs.groupBy { it.category }.forEach { (cat, list) ->
            SectionLabel(cat)
            GroupCard {
                list.forEachIndexed { i, d ->
                    val total = macroTotals[d.code] ?: totals[d.code] ?: 0.0
                    val avg = total / loggedDays
                    val target = s.microTargets[d.code] ?: (if (d.category == tr("Macronutriments")) d.reference else app.superhealth.nutrition.data.Micros.reference(d))
                    if (i > 0) RowDivider()
                    Column(Modifier.fillMaxWidth().clickable { open = d }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row {
                            Text(d.label.tr(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            val v = if (d.code == "kcal") s.energyValue(avg) else avg
                            Text("${num(v, if (v < 10) 1 else 0)} ${d.unit}" + (target?.let { " / ${num(if (d.code == "kcal") s.energyValue(it) else it, 0)}" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        target?.let {
                            Spacer(Modifier.height(4.dp))
                            ProgressBar((avg / it).toFloat(), if (avg >= it) Base.Green else Mf.colors.accent, height = 5.dp)
                        }
                    }
                }
            }
        }
        Text(trf("Moyenne sur %1\$s jour(s) journalisé(s). Touchez un nutriment pour ses sources et sa cible.", loggedDays),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(8.dp))
        Spacer(Modifier.height(24.dp))
    }

    open?.let { d ->
        val contributors = inRange.groupBy { it.name }.mapValues { (_, l) -> l.sumOf { it.nutrient(d.code) } }
            .filterValues { it > 0 }.entries.sortedByDescending { it.value }
        val sum = contributors.sumOf { it.value }.coerceAtLeast(1e-9)
        var custom by remember(d.code) { mutableStateOf(s.microTargets[d.code]?.let { num(it, 1) } ?: "") }
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { open = null }, containerColor = Mf.colors.card) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
                Text(d.label.tr(), style = MaterialTheme.typography.titleLarge)
                Text(trf("Principales sources · %1\$s", range.label.tr()), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                Spacer(Modifier.height(12.dp))
                if (contributors.isEmpty()) Text(tr("Aucune source sur la période."), color = Mf.colors.textSecondary)
                contributors.take(12).forEach { (name, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        app.superhealth.nutrition.ui.components.FoodIcon(name, 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            ProgressBar((v / sum).toFloat(), Mf.colors.accent, height = 4.dp)
                        }
                        Text("  ${num(if (d.code == "kcal") s.energyValue(v) else v, if (v < 10) 1 else 0)} ${d.unit}",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(" ${(v / sum * 100).roundToInt()} %", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary,
                            modifier = Modifier.width(40.dp))
                    }
                }
                if (d.category != "Macronutriments") {
                    Spacer(Modifier.height(12.dp))
                    NumberField(trf("Cible personnalisée (vide = automatique%1\$s)", (d.reference?.let { " : ${num(it, 0)}" } ?: "")), custom, { custom = it }, suffix = d.unit)
                    PillButton(tr("Enregistrer la cible"), Modifier.fillMaxWidth()) {
                        val v = custom.toNum()
                        vm.repo.prefs.setSettings(s.copy(microTargets = if (v != null && v > 0) s.microTargets + (d.code to v) else s.microTargets - d.code))
                        open = null
                    }
                }
            }
        }
    }
}
