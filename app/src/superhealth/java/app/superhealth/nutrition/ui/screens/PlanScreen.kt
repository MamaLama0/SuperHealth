package app.superhealth.nutrition.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.superhealth.nutrition.domain.DayTarget
import app.superhealth.nutrition.domain.NutritionPlan
import app.superhealth.nutrition.domain.PlanDay
import app.superhealth.nutrition.domain.PlanWeek
import app.superhealth.nutrition.domain.PrepPhase
import app.superhealth.nutrition.domain.Settings
import app.superhealth.nutrition.ui.AppViewModel
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.NumberField
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.nutrition.ui.num
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.toKcal
import app.superhealth.nutrition.ui.toNum
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlin.math.roundToInt

/**
 * Plan nutrition semaine par semaine (3.8) : chaque semaine a ses sept jours réglés un par un (macros, fibres, sodium,
 * eau, recharge, note). On peut préparer la semaine prochaine ou toute une préparation : rien ne change avant le lundi
 * de la semaine concernée.
 */
@Composable
fun PlanScreen(vm: AppViewModel, onBack: () -> Unit) {
    val a by vm.analytics.collectAsState()
    val s by vm.settings.collectAsState()
    val an = a ?: return
    val p = an.program
    val today = an.today
    var editing by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var generating by remember { mutableStateOf(false) }
    var openWeek by remember { mutableStateOf<Long?>(NutritionPlan.monday(today)) }
    fun save(f: (app.superhealth.nutrition.domain.Program) -> app.superhealth.nutrition.domain.Program) = vm.repo.prefs.setProgram(f(vm.repo.prefs.program.value))
    fun current(start: Long) = (0 until 7).map { an.weekTarget(start + it).macros.let { m -> DayTarget(m.kcal, m.protein, m.fat, m.carbs) } }

    DetailScaffold(tr("Plan semaine par semaine"), onBack = onBack) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Text(tr("Préparez vos semaines à l'avance, jour par jour. Une semaine programmée ne s'applique qu'à partir de son lundi ; elle passe alors avant le coaching et les cibles manuelles."),
                style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
            val next = NutritionPlan.nextMonday(today)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(tr("Semaine prochaine"), Modifier.weight(1f), strong = false) {
                    val last = p.plan.maxByOrNull { it.startDay }
                    val start = if (last != null && last.startDay >= next) last.startDay + 7 else next
                    save { NutritionPlan.put(it, last?.takeIf { l -> l.startDay >= NutritionPlan.monday(today) }?.copy(startDay = start)
                        ?: NutritionPlan.weekFrom(start, current(start))) }
                    openWeek = start
                }
                PillButton(tr("Générer une préparation"), Modifier.weight(1f), strong = false) { generating = true }
            }
            val weeks = p.plan.filter { it.startDay + 7 > NutritionPlan.monday(today) - 7 }
            if (weeks.isEmpty()) Text(tr("Aucune semaine programmée : les cibles habituelles s'appliquent."),
                color = Mf.colors.textSecondary, modifier = Modifier.padding(vertical = 16.dp))
            weeks.forEach { w ->
                val active = w.includes(today)
                val past = w.startDay + 7 <= today
                SectionLabel((if (active) "● " else "") + trf("Semaine du %1\$s", fmtDate(w.startDay)) + " · " + tr(w.phase.label) +
                    if (!active && !past) " · " + tr("programmée") else "")
                GroupCard {
                    val avg = w.days.map { it.target.kcal }.average()
                    Row(Modifier.fillMaxWidth().clickable { openWeek = if (openWeek == w.startDay) null else w.startDay }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(trf("Moyenne %1\$s %2\$s", s.energyValue(avg).roundToInt(), s.energyUnit.label), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(trf("P %1\$s · L %2\$s · G %3\$s g/j", w.days.map { it.target.protein }.average().roundToInt(), w.days.map { it.target.fat }.average().roundToInt(),
                                w.days.map { it.target.carbs }.average().roundToInt()), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                            if (w.note.isNotBlank()) Text(tr(w.note), style = MaterialTheme.typography.bodySmall, color = Mf.colors.accent)
                        }
                        Text(if (openWeek == w.startDay) "▲" else "▼", color = Mf.colors.textSecondary)
                    }
                    if (openWeek == w.startDay) {
                        w.days.forEachIndexed { i, d ->
                            RowDivider()
                            Row(Modifier.fillMaxWidth().clickable { editing = w.startDay to i }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(DAY_NAMES[i] + (if (d.refeed) " · " + tr("recharge") else ""), style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (w.startDay + i == today) FontWeight.Bold else FontWeight.Normal)
                                    val extras = listOfNotNull(d.fiberG?.let { trf("fibres %1\$s g", it.roundToInt()) }, d.sodiumMg?.let { trf("sodium %1\$s mg", it.roundToInt()) },
                                        d.waterMl?.let { trf("eau %1\$s ml", it.roundToInt()) }, d.note.takeIf { it.isNotBlank() }?.let { tr(it) })
                                    if (extras.isNotEmpty()) Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                                }
                                Text("${s.energyValue(d.target.kcal).roundToInt()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp))
                                Text("P ${d.target.protein.roundToInt()} L ${d.target.fat.roundToInt()} G ${d.target.carbs.roundToInt()}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        RowDivider()
                        Column(Modifier.padding(12.dp)) {
                            ChipChoice(PrepPhase.entries, w.phase, { it.label }) { ph -> save { NutritionPlan.put(it, w.copy(phase = ph)) } }
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillButton("−100", Modifier.weight(1f), strong = false) { save { NutritionPlan.put(it, NutritionPlan.shift(w, -100.0)) } }
                                PillButton("+100", Modifier.weight(1f), strong = false) { save { NutritionPlan.put(it, NutritionPlan.shift(w, 100.0)) } }
                                PillButton(tr("Dupliquer"), Modifier.weight(1.4f), strong = false) {
                                    val start = (p.plan.maxOf { it.startDay }) + 7
                                    save { NutritionPlan.put(it, w.copy(startDay = start)) }; openWeek = start
                                }
                            }
                            Text(tr("Supprimer la semaine"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 10.dp).clickable { save { NutritionPlan.remove(it, w.startDay) } })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(tr("Repères (Helms 2014) : perte de 0,5 à 1 % du poids par semaine, protéines 2,3 à 3,1 g/kg de masse maigre, lipides 15 à 30 % de l'énergie. Semaine de pic : recharge en glucides, sans couper l'eau ni le sel (Escalante 2021)."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            Spacer(Modifier.height(32.dp))
        }
    }
    editing?.let { (start, i) ->
        val w = p.plan.firstOrNull { it.startDay == start }
        if (w == null) editing = null
        else DaySheet(w, i, s, onDismiss = { editing = null }) { nw -> save { NutritionPlan.put(it, nw) }; editing = null }
    }
    if (generating) GenerateSheet(an, s, onDismiss = { generating = false }) { weeks ->
        save { prog -> weeks.fold(prog) { acc, w -> NutritionPlan.put(acc, w) } }
        openWeek = weeks.firstOrNull()?.startDay; generating = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(w: PlanWeek, index: Int, s: Settings, onDismiss: () -> Unit, onSave: (PlanWeek) -> Unit) {
    val d = w.days[index]
    var target by remember { mutableStateOf<DayTarget?>(d.target) }
    var fiber by remember { mutableStateOf(d.fiberG?.let { num(it, 0) } ?: "") }
    var sodium by remember { mutableStateOf(d.sodiumMg?.let { num(it, 0) } ?: "") }
    var water by remember { mutableStateOf(d.waterMl?.let { num(it, 0) } ?: "") }
    var refeed by remember { mutableStateOf(d.refeed) }
    var note by remember { mutableStateOf(d.note) }
    var copyTo by remember { mutableStateOf(emptySet<Int>()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.background) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(DAY_NAMES[index] + " · " + fmtDate(w.startDay + index), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            TargetEditor(d.target, s) { target = it }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField(tr("Fibres"), fiber, { fiber = it }, Modifier.weight(1f), suffix = "g")
                NumberField(tr("Sodium"), sodium, { sodium = it }, Modifier.weight(1f), suffix = "mg")
                NumberField(tr("Eau"), water, { water = it }, Modifier.weight(1f), suffix = "ml")
            }
            Text(tr("Vides : repères calculés par l'app."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            SwitchRow(tr("Jour de recharge"), tr("Glucides relevés, à l'énergie de maintien"), refeed) { refeed = it }
            androidx.compose.material3.OutlinedTextField(note, { note = it.take(120) }, label = { Text(tr("Note du jour")) }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            Text(tr("Appliquer aussi à"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            ChipChoice((1..7).filter { it != index + 1 }, 0, { dd -> (if (dd in copyTo) "✓ " else "") + DAY_NAMES[dd - 1].take(3) }) { dd ->
                copyTo = if (dd in copyTo) copyTo - dd else copyTo + dd
            }
            Spacer(Modifier.height(12.dp))
            PillButton(tr("Enregistrer"), Modifier.fillMaxWidth(), enabled = target != null) {
                val day = PlanDay(target!!, fiber.toNum(), sodium.toNum(), water.toNum(), refeed, note.trim())
                onSave(w.copy(days = w.days.mapIndexed { i, old -> if (i == index || (i + 1) in copyTo) day else old }))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerateSheet(an: app.superhealth.nutrition.data.Analytics, s: Settings, onDismiss: () -> Unit, onDone: (List<PlanWeek>) -> Unit) {
    val weight = an.currentTrendKg ?: 75.0
    val tdee = an.currentTdee
    var weeks by remember { mutableStateOf("12") }
    var startKcal by remember { mutableStateOf(num(s.energyValue(((tdee - weight * 7700 * 0.0075 / 7) / 10).roundToInt() * 10.0), 0)) }
    var step by remember { mutableStateOf("50") }
    var maint by remember { mutableStateOf(num(s.energyValue(tdee), 0)) }
    var protein by remember { mutableStateOf("2.6") }
    var fatPct by remember { mutableStateOf("22") }
    var refeeds by remember { mutableStateOf(setOf(6)) }
    var breakEvery by remember { mutableStateOf("6") }
    var peak by remember { mutableStateOf(true) }
    var reverse by remember { mutableStateOf("4") }
    var startNow by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.background) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(tr("Générer une préparation"), style = MaterialTheme.typography.titleLarge)
            Text(trf("Poids de tendance %1\$s kg · dépense %2\$s %3\$s", num(weight, 1), s.energyValue(tdee).roundToInt(), s.energyUnit.label),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            ChipChoice(listOf(false, true), startNow, { if (it) tr("Dès cette semaine") else tr("À partir de lundi prochain") }) { startNow = it }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField(tr("Semaines de sèche"), weeks, { weeks = it }, Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
                NumberField(tr("Départ"), startKcal, { startKcal = it }, Modifier.weight(1f), suffix = s.energyUnit.label)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField(tr("Baisse par semaine"), step, { step = it }, Modifier.weight(1f), suffix = s.energyUnit.label)
                NumberField(tr("Maintien"), maint, { maint = it }, Modifier.weight(1f), suffix = s.energyUnit.label)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField(tr("Protéines"), protein, { protein = it }, Modifier.weight(1f), suffix = "g/kg MM")
                NumberField(tr("Lipides"), fatPct, { fatPct = it }, Modifier.weight(1f), suffix = "%")
            }
            Text(tr("Jours de recharge"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            ChipChoice((1..7).toList(), 0, { dd -> (if (dd in refeeds) "✓ " else "") + DAY_NAMES[dd - 1].take(3) }) { dd -> refeeds = if (dd in refeeds) refeeds - dd else refeeds + dd }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField(tr("Pause diète toutes les"), breakEvery, { breakEvery = it }, Modifier.weight(1f), suffix = tr("sem."), keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
                NumberField(tr("Reverse diet"), reverse, { reverse = it }, Modifier.weight(1f), suffix = tr("sem."), keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
            }
            SwitchRow(tr("Semaine de pic"), tr("Recharge en glucides les trois derniers jours"), peak) { peak = it }
            val prep = runCatching {
                NutritionPlan.Prep(
                    start = if (startNow) NutritionPlan.monday(an.today) else NutritionPlan.nextMonday(an.today),
                    weeks = weeks.toNum()!!.toInt().coerceIn(1, 52), startKcal = s.toKcal(startKcal.toNum()!!), stepKcal = s.toKcal(step.toNum()!!),
                    maintenanceKcal = s.toKcal(maint.toNum()!!), weightKg = weight, leanKg = an.lbmAt(an.today),
                    proteinPerKgLean = protein.toNum()!!.coerceIn(1.2, 3.5), fatPct = fatPct.toNum()!!.coerceIn(10.0, 45.0), refeedDays = refeeds,
                    dietBreakEvery = (breakEvery.toNum() ?: 0.0).toInt().coerceIn(0, 20), peakWeek = peak, reverseWeeks = (reverse.toNum() ?: 0.0).toInt().coerceIn(0, 16),
                    floorKcal = if (an.profile.sex == app.superhealth.nutrition.domain.Sex.FEMALE) 1200.0 else 1500.0,
                )
            }.getOrNull()
            val plan = prep?.let { NutritionPlan.generate(it) }.orEmpty()
            plan.lastOrNull()?.let { last ->
                Text(trf("%1\$s semaines, jusqu'au %2\$s. Vous pourrez ensuite modifier chaque jour.", plan.size, fmtDate(last.startDay + 6)),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(12.dp))
            PillButton(tr("Créer le plan"), Modifier.fillMaxWidth(), enabled = plan.isNotEmpty()) { onDone(plan) }
        }
    }
}
