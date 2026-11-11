package app.superhealth.nutrition.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.superhealth.nutrition.data.Analytics
import app.superhealth.nutrition.domain.DayTarget
import app.superhealth.nutrition.domain.NutritionPlan
import app.superhealth.nutrition.domain.PlanDay
import app.superhealth.nutrition.domain.PlanWeek
import app.superhealth.nutrition.domain.PrepPhase
import app.superhealth.nutrition.domain.Program
import app.superhealth.nutrition.domain.Settings
import app.superhealth.nutrition.ui.AppViewModel
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.NumberField
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.num
import app.superhealth.nutrition.ui.toKcal
import app.superhealth.nutrition.ui.toNum
import app.superhealth.ui.charts.BarChart
import app.superhealth.ui.health.RoundButton
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val shortDate = DateTimeFormatter.ofPattern("d MMM")
private fun short(day: Long) = LocalDate.ofEpochDay(day).format(shortDate.withLocale(app.superhealth.ui.Lang.locale))

/**
 * Plan nutrition semaine par semaine : objectifs de base (eau, sodium, potassium, fibres), puis un programme de
 * plusieurs semaines qui change d'une semaine à l'autre. Chaque semaine a sa phase, ses sept jours réglés un par un
 * (macros, recharge, note) et ses repères d'eau, de sodium et de fibres ; un sélecteur passe d'une semaine à l'autre.
 * Une semaine programmée ne s'applique qu'à partir de son lundi.
 */
@Composable
fun PlanScreen(vm: AppViewModel, onBack: () -> Unit) {
    val a by vm.analytics.collectAsState()
    val s by vm.settings.collectAsState()
    val an = a ?: return
    val p = an.program
    val today = an.today
    val thisMonday = NutritionPlan.monday(today)
    var editing by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var weekSheet by remember { mutableStateOf<Long?>(null) }
    var copySheet by remember { mutableStateOf<Long?>(null) }
    var building by remember { mutableStateOf<Boolean?>(null) } // false : bloc de semaines, true : préparation
    var picked by remember { mutableStateOf<Long?>(null) }
    fun save(f: (Program) -> Program) = vm.repo.prefs.setProgram(f(vm.repo.prefs.program.value))
    fun current(start: Long) = (0 until 7).map { an.weekTarget(start + it).macros.let { m -> DayTarget(m.kcal, m.protein, m.fat, m.carbs) } }

    val weeks = p.plan.sortedBy { it.startDay }
    val sel = weeks.firstOrNull { it.startDay == picked } ?: weeks.firstOrNull { it.includes(today) }
        ?: weeks.firstOrNull { it.startDay > today } ?: weeks.lastOrNull()
    fun addWeek() {
        val last = weeks.lastOrNull()
        val start = if (last != null && last.startDay >= thisMonday) last.startDay + 7 else NutritionPlan.nextMonday(today)
        val w = last?.takeIf { it.startDay >= thisMonday - 7 }?.copy(startDay = start) ?: NutritionPlan.weekFrom(start, current(start), when (p.goalType) {
            app.superhealth.nutrition.domain.GoalType.CUT -> PrepPhase.CUT
            app.superhealth.nutrition.domain.GoalType.BULK -> PrepPhase.BULK
            app.superhealth.nutrition.domain.GoalType.MAINTENANCE -> PrepPhase.MAINTENANCE
        })
        save { NutritionPlan.put(it, w) }; picked = start
    }

    DetailScaffold(tr("Objectifs par semaine"), onBack = onBack) {
        Text(tr("Programmez votre alimentation sur plusieurs semaines : chaque semaine a sa phase, ses calories et macros jour par jour, et ses objectifs d'eau et de sodium. Une semaine programmée s'applique à partir de son lundi ; elle passe alors avant le coaching et les cibles manuelles."),
            style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(vertical = 8.dp))

        SectionLabel(tr("Objectifs de base"))
        BaseGoals(vm, s)

        SectionLabel(tr("Programme"))
        if (weeks.isEmpty()) MfCard {
            Text(tr("Aucune semaine programmée : les cibles habituelles s'appliquent."), color = Mf.colors.textSecondary)
        } else {
            Overview(weeks, sel, today, s) { picked = it }
            Spacer(Modifier.height(12.dp))
            WeekSelector(weeks, sel!!, p, today) { picked = it }
            Spacer(Modifier.height(12.dp))
            WeekDetail(sel, p, today, s,
                onDay = { i -> editing = sel.startDay to i },
                onSave = { w -> save { NutritionPlan.put(it, w) } },
                onEdit = { weekSheet = sel.startDay },
                onCopy = { copySheet = sel.startDay },
                onDelete = { save { NutritionPlan.remove(it, sel.startDay) }; picked = null })
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(tr("Ajouter une semaine"), Modifier.weight(1f), strong = false) { addWeek() }
            PillButton(tr("Programmer des semaines"), Modifier.weight(1f)) { building = false }
        }
        Spacer(Modifier.height(16.dp))
        Text(tr("Repères (Helms 2014) : perte de 0,5 à 1 % du poids par semaine, protéines 2,3 à 3,1 g/kg de masse maigre, lipides 15 à 30 % de l'énergie. Semaine de pic : recharge en glucides, sans couper l'eau ni le sel (Escalante 2021)."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        Spacer(Modifier.height(32.dp))
    }

    editing?.let { (start, i) ->
        val w = p.plan.firstOrNull { it.startDay == start }
        if (w == null) editing = null
        else DaySheet(w, i, s, onDismiss = { editing = null }) { nw -> save { NutritionPlan.put(it, nw) }; editing = null }
    }
    weekSheet?.let { start ->
        val w = p.plan.firstOrNull { it.startDay == start }
        if (w == null) weekSheet = null
        else WeekSheet(w, s, onDismiss = { weekSheet = null }) { nw -> save { NutritionPlan.put(it, nw) }; weekSheet = null }
    }
    copySheet?.let { start ->
        val w = p.plan.firstOrNull { it.startDay == start }
        if (w == null) copySheet = null
        else CopySheet(w, p, s, onDismiss = { copySheet = null }) { list ->
            save { prog -> list.fold(prog) { acc, nw -> NutritionPlan.put(acc, nw) } }
            picked = list.lastOrNull()?.startDay; copySheet = null
        }
    }
    building?.let { prep ->
        BuildSheet(an, s, p, prep, onDismiss = { building = null }) { list ->
            save { prog -> list.fold(prog) { acc, w -> NutritionPlan.put(acc, w) } }
            picked = list.firstOrNull()?.startDay; building = null
        }
    }
}

// ---- Objectifs de base -------------------------------------------------------------------------------------------

/**
 * Objectifs valables hors plan, ou quand une semaine laisse un repère vide : eau (automatique ou fixe), plafond de
 * sodium, potassium et fibres. Ils servent au journal, au panneau d'hydratation et aux nutriments du jour.
 */
@Composable
private fun BaseGoals(vm: AppViewModel, s: Settings) {
    var d by remember { mutableStateOf(BaseGoalsDraft.current(s)) }
    GroupCard {
        BaseGoalsForm(d) { nd ->
            d = nd
            vm.repo.prefs.setSettings(nd.applyTo(vm.repo.prefs.settings.value))
        }
    }
}

// ---- Vue d'ensemble et sélecteur ---------------------------------------------------------------------------------

@Composable
private fun Overview(weeks: List<PlanWeek>, sel: PlanWeek?, today: Long, s: Settings, onPick: (Long) -> Unit) {
    MfCard {
        val first = weeks.first(); val last = weeks.last()
        Text(trf("%1\$s semaines · du %2\$s au %3\$s", weeks.size, fmtDate(first.startDay), fmtDate(last.startDay + 6)),
            style = MaterialTheme.typography.titleSmall)
        val kcal = weeks.map { s.energyValue(NutritionPlan.average(it).kcal) }
        Text(trf("De %1\$s à %2\$s %3\$s par jour en moyenne", kcal.first().roundToInt(), kcal.last().roundToInt(), s.energyUnit.label),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        Spacer(Modifier.height(10.dp))
        val every = ((weeks.size + 7) / 8).coerceAtLeast(1)
        BarChart(kcal, weeks.map { null }, Mf.colors.accent, height = 110.dp,
            labels = weeks.mapIndexed { i, _ -> if (i % every == 0) "S${i + 1}" else "" },
            valueFormat = { "${it.roundToInt()} ${s.energyUnit.label}" },
            pointLabel = { i -> trf("Semaine %1\$s", i + 1) + " · " + tr(weeks[i].phase.label) },
            onPick = { i -> onPick(weeks[i].startDay) })
        Spacer(Modifier.height(6.dp))
        // bande des phases, semaine par semaine (la semaine choisie soulignée)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            weeks.forEach { w ->
                Box(Modifier.weight(1f).height(if (w == sel) 8.dp else 5.dp).clip(RoundedCornerShape(3.dp))
                    .background(phaseColor(w.phase).copy(alpha = if (w.startDay + 7 <= today) 0.45f else 1f)).clickable { onPick(w.startDay) })
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(tr("Touchez une semaine pour l'afficher"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textTertiary)
    }
}

@Composable
private fun WeekSelector(weeks: List<PlanWeek>, sel: PlanWeek, p: Program, today: Long, onPick: (Long) -> Unit) {
    val idx = weeks.indexOf(sel)
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, tr("Semaine précédente"), enabled = idx > 0) { weeks.getOrNull(idx - 1)?.let { onPick(it.startDay) } }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(trf("Semaine %1\$s", NutritionPlan.weekNumber(p, sel)) + " · " + short(sel.startDay) + " – " + short(sel.startDay + 6),
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1)
            Text(tr(sel.phase.label) + " · " + tr(when {
                sel.includes(today) -> "En cours"
                sel.startDay > today -> "Programmée"
                else -> "Passée"
            }), style = MaterialTheme.typography.bodySmall, color = phaseColor(sel.phase))
        }
        RoundButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, tr("Semaine suivante"), enabled = idx < weeks.size - 1) { weeks.getOrNull(idx + 1)?.let { onPick(it.startDay) } }
    }
    Spacer(Modifier.height(8.dp))
    val state = rememberLazyListState()
    LaunchedEffect(idx) { if (idx >= 0) state.animateScrollToItem((idx - 2).coerceAtLeast(0)) }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(weeks) { _, w ->
            val on = w == sel
            Column(Modifier.width(56.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Mf.colors.strong else Mf.colors.chip)
                .clickable { onPick(w.startDay) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("S${NutritionPlan.weekNumber(p, w)}", style = MaterialTheme.typography.titleSmall, color = if (on) Mf.colors.onStrong else Mf.colors.text,
                    fontWeight = if (w.includes(today)) FontWeight.Bold else FontWeight.Normal)
                Text(short(w.startDay), style = MaterialTheme.typography.labelSmall, color = if (on) Mf.colors.onStrong else Mf.colors.textSecondary, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.size(width = 18.dp, height = 4.dp).clip(CircleShape).background(phaseColor(w.phase)))
            }
        }
    }
}

// ---- Détail d'une semaine ----------------------------------------------------------------------------------------

@Composable
private fun WeekDetail(w: PlanWeek, p: Program, today: Long, s: Settings, onDay: (Int) -> Unit, onSave: (PlanWeek) -> Unit,
                       onEdit: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    GroupCard {
        Column(Modifier.padding(16.dp)) {
            val avg = NutritionPlan.average(w)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${s.energyValue(avg.kcal).roundToInt()}", style = MaterialTheme.typography.headlineMedium)
                Text(" " + trf("%1\$s par jour en moyenne", s.energyUnit.label), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            Text(trf("P %1\$s · L %2\$s · G %3\$s g/j", avg.protein.roundToInt(), avg.fat.roundToInt(), avg.carbs.roundToInt()),
                style = MaterialTheme.typography.bodyMedium)
            val goals = listOfNotNull(w.waterMl?.let { trf("eau %1\$s ml", it.roundToInt()) }, w.sodiumMg?.let { trf("sodium %1\$s mg", it.roundToInt()) },
                w.potassiumMg?.let { trf("potassium %1\$s mg", it.roundToInt()) }, w.fiberG?.let { trf("fibres %1\$s g", it.roundToInt()) })
            Text(if (goals.isEmpty()) tr("Eau et sodium : objectifs de base") else goals.joinToString(" · ") +
                if (w.addTraining && (w.waterMl != null || w.sodiumMg != null)) " " + tr("(+ séances)") else "",
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
            if (w.note.isNotBlank()) Text(tr(w.note), style = MaterialTheme.typography.bodySmall, color = Mf.colors.accent, modifier = Modifier.padding(top = 4.dp))
        }
        w.days.forEachIndexed { i, d ->
            RowDivider()
            val day = w.startDay + i
            Row(Modifier.fillMaxWidth().clickable { onDay(i) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(DAY_NAMES[i] + " " + LocalDate.ofEpochDay(day).dayOfMonth + (if (d.refeed) " · " + tr("recharge") else ""),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                        color = if (day == today) Mf.colors.accent else Mf.colors.text)
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
            ChipChoice(PrepPhase.entries, w.phase, { it.label }) { ph -> onSave(w.copy(phase = ph)) }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("−100", Modifier.weight(1f), strong = false) { onSave(NutritionPlan.shift(w, -100.0)) }
                PillButton("+100", Modifier.weight(1f), strong = false) { onSave(NutritionPlan.shift(w, 100.0)) }
                PillButton(tr("Régler la semaine"), Modifier.weight(1.8f), strong = false) { onEdit() }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(tr("Copier sur les semaines suivantes"), Modifier.weight(1f), strong = false) { onCopy() }
            }
            Text(tr("Supprimer la semaine"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 10.dp).clickable { onDelete() })
        }
    }
}

// ---- Feuilles d'édition ------------------------------------------------------------------------------------------

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
            Text(tr("Vides : repères de la semaine, sinon objectifs de base."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            SwitchRow(tr("Jour de recharge"), tr("Glucides relevés, à l'énergie de maintien"), refeed) { refeed = it }
            OutlinedTextField(note, { note = it.take(120) }, label = { Text(tr("Note du jour")) }, singleLine = true,
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

/** Réglages de toute la semaine : phase, mêmes macros sur les sept jours, eau, sodium, potassium, fibres, note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekSheet(w: PlanWeek, s: Settings, onDismiss: () -> Unit, onSave: (PlanWeek) -> Unit) {
    var phase by remember { mutableStateOf(w.phase) }
    var same by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<DayTarget?>(NutritionPlan.average(w)) }
    var water by remember { mutableStateOf(w.waterMl?.let { num(it, 0) } ?: "") }
    var sodium by remember { mutableStateOf(w.sodiumMg?.let { num(it, 0) } ?: "") }
    var potassium by remember { mutableStateOf(w.potassiumMg?.let { num(it, 0) } ?: "") }
    var fiber by remember { mutableStateOf(w.fiberG?.let { num(it, 0) } ?: "") }
    var addTraining by remember { mutableStateOf(w.addTraining) }
    var note by remember { mutableStateOf(w.note) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.background) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(trf("Semaine du %1\$s", fmtDate(w.startDay)), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            ChipChoice(PrepPhase.entries, phase, { it.label }) { phase = it }
            SwitchRow(tr("Mêmes calories et macros les 7 jours"), tr("Remplace les réglages jour par jour"), same) { same = it }
            if (same) TargetEditor(NutritionPlan.average(w), s) { target = it }
            Text(tr("Hydratation et micronutriments"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField(tr("Eau par jour"), water, { water = it }, Modifier.weight(1f), suffix = "ml", keyboard = KeyboardType.Number)
                NumberField(tr("Sodium"), sodium, { sodium = it }, Modifier.weight(1f), suffix = "mg", keyboard = KeyboardType.Number)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField(tr("Potassium"), potassium, { potassium = it }, Modifier.weight(1f), suffix = "mg", keyboard = KeyboardType.Number)
                NumberField(tr("Fibres"), fiber, { fiber = it }, Modifier.weight(1f), suffix = "g", keyboard = KeyboardType.Number)
            }
            Text(tr("Vides : objectifs de base. Un jour peut garder sa propre valeur."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            SwitchRow(tr("Ajouter la sueur des séances"), tr("L'eau et le sodium montent les jours d'entraînement"), addTraining) { addTraining = it }
            OutlinedTextField(note, { note = it.take(160) }, label = { Text(tr("Note de la semaine")) }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            Spacer(Modifier.height(12.dp))
            PillButton(tr("Enregistrer"), Modifier.fillMaxWidth(), enabled = !same || target != null) {
                val base = if (same) NutritionPlan.setAll(w, target!!) else w
                onSave(base.copy(phase = phase, waterMl = water.toNum()?.takeIf { it > 0 }, sodiumMg = sodium.toNum()?.takeIf { it > 0 },
                    potassiumMg = potassium.toNum()?.takeIf { it > 0 }, fiberG = fiber.toNum()?.takeIf { it > 0 }, addTraining = addTraining, note = note.trim()))
            }
        }
    }
}

/** Copie d'une semaine sur les suivantes, avec une progression des calories d'une semaine à l'autre. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopySheet(w: PlanWeek, p: Program, s: Settings, onDismiss: () -> Unit, onDone: (List<PlanWeek>) -> Unit) {
    var count by remember { mutableStateOf("4") }
    var dir by remember { mutableStateOf(0) }
    var step by remember { mutableStateOf("50") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.background) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(tr("Copier sur les semaines suivantes"), style = MaterialTheme.typography.titleLarge)
            Text(trf("À partir de la semaine du %1\$s", fmtDate(w.startDay + 7)), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            NumberField(tr("Nombre de semaines"), count, { count = it }, keyboard = KeyboardType.Number)
            Text(tr("D'une semaine à l'autre"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
            ChipChoice(listOf(-1, 0, 1), dir, { when (it) { -1 -> tr("Baisser"); 1 -> tr("Monter"); else -> tr("Identique") } }) { dir = it }
            if (dir != 0) NumberField(tr("Par semaine"), step, { step = it }, suffix = s.energyUnit.label, keyboard = KeyboardType.Number)
            val n = (count.toNum() ?: 0.0).toInt().coerceIn(0, 52)
            val delta = dir * s.toKcal(step.toNum() ?: 0.0)
            val list = if (n > 0) NutritionPlan.extend(w, w.startDay, n, delta) else emptyList()
            val overwritten = list.count { nw -> p.plan.any { it.startDay == nw.startDay } }
            list.lastOrNull()?.let { last ->
                Text(trf("%1\$s semaines, jusqu'au %2\$s : %3\$s → %4\$s %5\$s/j", list.size, fmtDate(last.startDay + 6),
                    s.energyValue(NutritionPlan.average(w).kcal).roundToInt(), s.energyValue(NutritionPlan.average(last).kcal).roundToInt(), s.energyUnit.label),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            if (overwritten > 0) Text(trf("%1\$s semaine(s) déjà programmée(s) seront remplacées.", overwritten),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            PillButton(tr("Copier"), Modifier.fillMaxWidth(), enabled = list.isNotEmpty()) { onDone(list) }
        }
    }
}

/** Programmer plusieurs semaines d'un coup : bloc qui évolue ou préparation complète (voir [PlanBuilderForm]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildSheet(an: Analytics, s: Settings, p: Program, prep: Boolean, onDismiss: () -> Unit, onDone: (List<PlanWeek>) -> Unit) {
    var plan by remember { mutableStateOf(emptyList<PlanWeek>()) }
    val basis = PlanBasis(an.currentTrendKg ?: 75.0, an.lbmAt(an.today), an.currentTdee, an.profile.sex == app.superhealth.nutrition.domain.Sex.FEMALE, an.today, p.plan)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.background) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(tr("Programmer des semaines"), style = MaterialTheme.typography.titleLarge)
            Text(trf("Poids de tendance %1\$s kg · dépense %2\$s %3\$s", num(basis.weightKg, 1), s.energyValue(basis.tdee).roundToInt(), s.energyUnit.label),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            PlanBuilderForm(basis, s, initialPrep = prep) { plan = it }
            Spacer(Modifier.height(12.dp))
            PillButton(tr("Créer les semaines"), Modifier.fillMaxWidth(), enabled = plan.isNotEmpty()) { onDone(plan) }
        }
    }
}
