package app.superhealth.nutrition.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.superhealth.SuperPrefs
import app.superhealth.nutrition.domain.NutritionPlan
import app.superhealth.nutrition.domain.PlanWeek
import app.superhealth.nutrition.domain.PrepPhase
import app.superhealth.nutrition.domain.Settings
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.NumberField
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.num
import app.superhealth.nutrition.ui.toKcal
import app.superhealth.nutrition.ui.toNum
import app.superhealth.ui.charts.BarChart
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import kotlin.math.abs
import kotlin.math.roundToInt

/** Couleur repère de chaque phase (sélecteur de semaine, aperçus). */
fun phaseColor(p: PrepPhase) = when (p) {
    PrepPhase.BULK -> Color(0xFF34C759)
    PrepPhase.CUT -> Color(0xFFFF9500)
    PrepPhase.MAINTENANCE -> Color(0xFF5AC8FA)
    PrepPhase.DIET_BREAK -> Color(0xFF30B0C7)
    PrepPhase.REVERSE -> Color(0xFFAF52DE)
    PrepPhase.PEAK_WEEK -> Color(0xFFFF3B30)
}

// ---- Objectifs de base : eau, sodium, potassium, fibres ---------------------------------------------------------

/** Objectifs valables hors plan (ou quand une semaine laisse la valeur vide). Champs texte tels que saisis. */
data class BaseGoalsDraft(val waterAuto: Boolean, val water: String, val sodium: String, val potassium: String, val fiber: String) {
    companion object {
        fun current(s: Settings) = BaseGoalsDraft(
            SuperPrefs.settings.value.hydrationAuto,
            num(runCatching { ActivityUser().hydrationGoalMl.toDouble() }.getOrDefault(2000.0), 0),
            s.microTargets["sodium"]?.let { num(it, 0) } ?: "",
            s.microTargets["potassium"]?.let { num(it, 0) } ?: "",
            s.microTargets["fiber"]?.let { num(it, 0) } ?: "",
        )
    }

    /** Écrit l'eau (mode et quantité fixe) et renvoie les réglages nutrition avec les repères de micronutriments. */
    fun applyTo(s: Settings): Settings {
        SuperPrefs.update { it.copy(hydrationAuto = waterAuto) }
        water.toNum()?.takeIf { it in 500.0..8000.0 }?.let { ml ->
            GBApplication.getPrefs().preferences.edit().putString(ActivityUser.PREF_USER_GOAL_HYDRATION_ML, ml.roundToInt().toString()).apply()
        }
        var m = s.microTargets
        listOf("sodium" to sodium, "potassium" to potassium, "fiber" to fiber).forEach { (code, v) ->
            val x = v.toNum()
            m = if (x != null && x > 0) m + (code to x) else m - code
        }
        return s.copy(microTargets = m)
    }
}

@Composable
fun BaseGoalsForm(d: BaseGoalsDraft, onChange: (BaseGoalsDraft) -> Unit) {
    SwitchRow(tr("Eau : objectif automatique"), tr("Selon le poids, les séances et les protéines du jour"), d.waterAuto) { onChange(d.copy(waterAuto = it)) }
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp)) {
        if (!d.waterAuto) NumberField(tr("Eau par jour"), d.water, { onChange(d.copy(water = it)) }, suffix = "ml", keyboard = KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField(tr("Sodium max."), d.sodium, { onChange(d.copy(sodium = it)) }, Modifier.weight(1f), suffix = "mg", keyboard = KeyboardType.Number)
            NumberField(tr("Potassium"), d.potassium, { onChange(d.copy(potassium = it)) }, Modifier.weight(1f), suffix = "mg", keyboard = KeyboardType.Number)
            NumberField(tr("Fibres"), d.fiber, { onChange(d.copy(fiber = it)) }, Modifier.weight(1f), suffix = "g", keyboard = KeyboardType.Number)
        }
        Text(tr("Vides : repères officiels (sodium < 2 000 mg, OMS). Ils valent hors plan, ou quand une semaine ne fixe pas la valeur ; la sueur des séances relève l'eau et le sodium."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
    }
}

// ---- Générateur de semaines --------------------------------------------------------------------------------------

/** Données de départ du générateur (écran du plan ou questionnaire). */
data class PlanBasis(val weightKg: Double, val leanKg: Double?, val tdee: Double, val female: Boolean, val today: Long, val existing: List<PlanWeek>)

/**
 * Programme sur plusieurs semaines, sans feuille autour : bloc qui évolue d'une semaine à l'autre (prise de masse,
 * maintien, sèche, reverse) ou préparation complète (sèche, pauses diète, semaine de pic, reverse), avec les repères
 * d'eau et de sodium de chaque semaine. [onPlan] reçoit les semaines calculées à chaque changement (vide si invalide).
 */
@Composable
fun PlanBuilderForm(b: PlanBasis, s: Settings, initialPrep: Boolean = false, onPlan: (List<PlanWeek>) -> Unit) {
    val thisMonday = NutritionPlan.monday(b.today)
    val lastStart = b.existing.maxOfOrNull { it.startDay }?.takeIf { it >= thisMonday }
    var prep by remember { mutableStateOf(initialPrep) }
    var phase by remember { mutableStateOf(PrepPhase.BULK) }
    fun defaults(ph: PrepPhase) = when (ph) {
        PrepPhase.BULK -> Triple(b.tdee + 250, 1, 50.0)
        PrepPhase.CUT -> Triple(b.tdee - 500, -1, 50.0)
        PrepPhase.REVERSE -> Triple(b.tdee - 300, 1, 75.0)
        else -> Triple(b.tdee, 0, 0.0)
    }
    var startKcal by remember { mutableStateOf(num(s.energyValue((defaults(phase).first / 10).roundToInt() * 10.0), 0)) }
    var dir by remember { mutableStateOf(defaults(phase).second) }
    var step by remember { mutableStateOf(num(s.energyValue(defaults(phase).third), 0)) }
    var weeks by remember { mutableStateOf("8") }
    var startAt by remember { mutableStateOf(if (lastStart != null) 2 else 1) } // 0 cette semaine, 1 lundi prochain, 2 après la dernière
    var protein by remember { mutableStateOf("2.0") }
    var fatPct by remember { mutableStateOf("25") }
    var refeeds by remember { mutableStateOf(emptySet<Int>()) }
    // préparation
    var prepWeeks by remember { mutableStateOf("12") }
    var prepStart by remember { mutableStateOf(num(s.energyValue(((b.tdee - b.weightKg * 7700 * 0.0075 / 7) / 10).roundToInt() * 10.0), 0)) }
    var prepStep by remember { mutableStateOf("50") }
    var maint by remember { mutableStateOf(num(s.energyValue(b.tdee), 0)) }
    var prepProtein by remember { mutableStateOf("2.6") }
    var prepFat by remember { mutableStateOf("22") }
    var prepRefeeds by remember { mutableStateOf(setOf(6)) }
    var breakEvery by remember { mutableStateOf("6") }
    var peak by remember { mutableStateOf(true) }
    var reverse by remember { mutableStateOf("4") }
    // repères des semaines
    var water by remember { mutableStateOf("") }
    var sodium by remember { mutableStateOf("") }
    var potassium by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }
    var addTraining by remember { mutableStateOf(true) }
    val floorKcal = if (b.female) 1200.0 else 1500.0

    ChipChoice(listOf(false, true), prep, { if (it) tr("Préparation") else tr("Bloc de semaines") }) { prep = it }
    Text(tr(if (prep) "Sèche avec recharges et pauses diète, semaine de pic, puis reverse diet." else "Calories qui montent, baissent ou restent stables d'une semaine à l'autre."),
        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
    Spacer(Modifier.height(4.dp))
    ChipChoice(listOfNotNull(0, 1, if (lastStart != null) 2 else null), startAt, {
        when (it) { 0 -> tr("Cette semaine"); 1 -> tr("Lundi prochain"); else -> tr("Après la dernière semaine") }
    }) { startAt = it }
    val start = when (startAt) { 0 -> thisMonday; 2 -> (lastStart ?: thisMonday) + 7; else -> NutritionPlan.nextMonday(b.today) }

    if (!prep) {
        Spacer(Modifier.height(6.dp))
        ChipChoice(listOf(PrepPhase.BULK, PrepPhase.MAINTENANCE, PrepPhase.CUT, PrepPhase.REVERSE), phase, { it.label }) { ph ->
            phase = ph
            val (k, d, st) = defaults(ph)
            startKcal = num(s.energyValue((k / 10).roundToInt() * 10.0), 0); dir = d; step = num(s.energyValue(st), 0)
            protein = if (ph == PrepPhase.CUT) "2.3" else "2.0"
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField(tr("Semaines"), weeks, { weeks = it }, Modifier.weight(1f), keyboard = KeyboardType.Number)
            NumberField(tr("Départ"), startKcal, { startKcal = it }, Modifier.weight(1f), suffix = s.energyUnit.label, keyboard = KeyboardType.Number)
        }
        Text(tr("D'une semaine à l'autre"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
        ChipChoice(listOf(-1, 0, 1), dir, { when (it) { -1 -> tr("Baisser"); 1 -> tr("Monter"); else -> tr("Identique") } }) { dir = it }
        if (dir != 0) NumberField(tr("Par semaine"), step, { step = it }, suffix = s.energyUnit.label, keyboard = KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField(tr("Protéines"), protein, { protein = it }, Modifier.weight(1f), suffix = "g/kg")
            NumberField(tr("Lipides"), fatPct, { fatPct = it }, Modifier.weight(1f), suffix = "%")
        }
        if (phase == PrepPhase.CUT) {
            Text(tr("Jours de recharge (au maintien)"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            ChipChoice((1..7).toList(), 0, { dd -> (if (dd in refeeds) "✓ " else "") + DAY_NAMES[dd - 1].take(3) }) { dd -> refeeds = if (dd in refeeds) refeeds - dd else refeeds + dd }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField(tr("Semaines de sèche"), prepWeeks, { prepWeeks = it }, Modifier.weight(1f), keyboard = KeyboardType.Number)
            NumberField(tr("Départ"), prepStart, { prepStart = it }, Modifier.weight(1f), suffix = s.energyUnit.label)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField(tr("Baisse par semaine"), prepStep, { prepStep = it }, Modifier.weight(1f), suffix = s.energyUnit.label)
            NumberField(tr("Maintien"), maint, { maint = it }, Modifier.weight(1f), suffix = s.energyUnit.label)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField(tr("Protéines"), prepProtein, { prepProtein = it }, Modifier.weight(1f), suffix = "g/kg MM")
            NumberField(tr("Lipides"), prepFat, { prepFat = it }, Modifier.weight(1f), suffix = "%")
        }
        Text(tr("Jours de recharge"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        ChipChoice((1..7).toList(), 0, { dd -> (if (dd in prepRefeeds) "✓ " else "") + DAY_NAMES[dd - 1].take(3) }) { dd -> prepRefeeds = if (dd in prepRefeeds) prepRefeeds - dd else prepRefeeds + dd }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField(tr("Pause diète toutes les"), breakEvery, { breakEvery = it }, Modifier.weight(1f), suffix = tr("sem."), keyboard = KeyboardType.Number)
            NumberField(tr("Reverse diet"), reverse, { reverse = it }, Modifier.weight(1f), suffix = tr("sem."), keyboard = KeyboardType.Number)
        }
        SwitchRow(tr("Semaine de pic"), tr("Recharge en glucides les trois derniers jours"), peak) { peak = it }
    }

    Text(tr("Hydratation et micronutriments"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField(tr("Eau"), water, { water = it }, Modifier.weight(1f), suffix = "ml", keyboard = KeyboardType.Number)
        NumberField(tr("Sodium"), sodium, { sodium = it }, Modifier.weight(1f), suffix = "mg", keyboard = KeyboardType.Number)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField(tr("Potassium"), potassium, { potassium = it }, Modifier.weight(1f), suffix = "mg", keyboard = KeyboardType.Number)
        NumberField(tr("Fibres"), fiber, { fiber = it }, Modifier.weight(1f), suffix = "g", keyboard = KeyboardType.Number)
    }
    Text(tr("Vides : objectifs de base."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
    SwitchRow(tr("Ajouter la sueur des séances"), tr("L'eau et le sodium montent les jours d'entraînement"), addTraining) { addTraining = it }

    val plan = runCatching {
        val raw = if (!prep) NutritionPlan.block(NutritionPlan.Block(
            start = start, weeks = weeks.toNum()!!.toInt().coerceIn(1, 52), phase = phase, startKcal = s.toKcal(startKcal.toNum()!!),
            stepKcal = dir * s.toKcal(step.toNum() ?: 0.0), weightKg = b.weightKg, proteinPerKg = protein.toNum()!!.coerceIn(1.0, 3.5),
            fatPct = fatPct.toNum()!!.coerceIn(10.0, 45.0), refeedDays = if (phase == PrepPhase.CUT) refeeds else emptySet(),
            refeedKcal = b.tdee, floorKcal = floorKcal,
        )) else NutritionPlan.generate(NutritionPlan.Prep(
            start = start, weeks = prepWeeks.toNum()!!.toInt().coerceIn(1, 52), startKcal = s.toKcal(prepStart.toNum()!!), stepKcal = s.toKcal(prepStep.toNum()!!),
            maintenanceKcal = s.toKcal(maint.toNum()!!), weightKg = b.weightKg, leanKg = b.leanKg,
            proteinPerKgLean = prepProtein.toNum()!!.coerceIn(1.2, 3.5), fatPct = prepFat.toNum()!!.coerceIn(10.0, 45.0), refeedDays = prepRefeeds,
            dietBreakEvery = (breakEvery.toNum() ?: 0.0).toInt().coerceIn(0, 20), peakWeek = peak, reverseWeeks = (reverse.toNum() ?: 0.0).toInt().coerceIn(0, 16),
            floorKcal = floorKcal,
        ))
        raw.map { it.copy(waterMl = water.toNum()?.takeIf { v -> v > 0 }, sodiumMg = sodium.toNum()?.takeIf { v -> v > 0 },
            potassiumMg = potassium.toNum()?.takeIf { v -> v > 0 }, fiberG = fiber.toNum()?.takeIf { v -> v > 0 }, addTraining = addTraining) }
    }.getOrNull().orEmpty()
    val report by rememberUpdatedState(onPlan)
    LaunchedEffect(plan) { report(plan) }

    if (plan.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        PlanPreview(plan, s)
        val overwritten = plan.count { nw -> b.existing.any { it.startDay == nw.startDay } }
        if (overwritten > 0) Text(trf("%1\$s semaine(s) déjà programmée(s) seront remplacées.", overwritten),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        val wk = (b.tdee - plan.map { NutritionPlan.average(it).kcal }.average()) * 7 / 7700
        if (abs(wk) > 0.05) Text(trf("≈ %1\$s kg par semaine en moyenne (estimation)", (if (wk > 0) "−" else "+") + num(abs(wk), 2)),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
    }
}

/** Aperçu d'un programme : calories par semaine, bande des phases et première ligne de chaque semaine. */
@Composable
fun PlanPreview(plan: List<PlanWeek>, s: Settings, maxRows: Int = 6) {
    val kcal = plan.map { s.energyValue(NutritionPlan.average(it).kcal) }
    Text(trf("%1\$s semaines, du %2\$s au %3\$s : %4\$s → %5\$s %6\$s/j", plan.size, fmtDate(plan.first().startDay), fmtDate(plan.last().startDay + 6),
        kcal.first().roundToInt(), kcal.last().roundToInt(), s.energyUnit.label), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(6.dp))
    val every = ((plan.size + 7) / 8).coerceAtLeast(1)
    BarChart(kcal, plan.map { null }, Mf.colors.accent, height = 90.dp, labels = plan.mapIndexed { i, _ -> if (i % every == 0) "S${i + 1}" else "" },
        valueFormat = { "${it.roundToInt()} ${s.energyUnit.label}" }, pointLabel = { i -> trf("Semaine %1\$s", i + 1) + " · " + tr(plan[i].phase.label) },
        onPick = {})
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        plan.forEach { w -> Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(phaseColor(w.phase))) }
    }
    Spacer(Modifier.height(6.dp))
    plan.take(maxRows).forEachIndexed { i, w ->
        val avg = NutritionPlan.average(w)
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(phaseColor(w.phase)))
            Spacer(Modifier.width(8.dp))
            Text("S${i + 1} · " + tr(w.phase.label), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${s.energyValue(avg.kcal).roundToInt()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text("  P ${avg.protein.roundToInt()} L ${avg.fat.roundToInt()} G ${avg.carbs.roundToInt()}", style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
    }
    if (plan.size > maxRows) Text(trf("… et %1\$s autres semaines", plan.size - maxRows), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textTertiary)
}
