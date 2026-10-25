package app.superhealth.training.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.training.domain.ExerciseDb
import app.superhealth.training.domain.PlanDiff
import app.superhealth.training.domain.SetLog
import app.superhealth.training.domain.SetType
import app.superhealth.training.domain.Settings
import app.superhealth.training.domain.Workout
import app.superhealth.training.ui.AppViewModel
import app.superhealth.training.ui.clean
import app.superhealth.training.ui.components.ExerciseThumb
import app.superhealth.training.ui.components.GroupCard
import app.superhealth.training.ui.components.MfCard
import app.superhealth.training.ui.components.PillButton
import app.superhealth.training.ui.components.RowDivider
import app.superhealth.training.ui.components.SectionLabel
import app.superhealth.training.ui.components.SettingRow
import app.superhealth.training.ui.components.TextInput
import app.superhealth.training.ui.components.Wheel
import app.superhealth.training.ui.durationWords
import app.superhealth.training.ui.longDate
import app.superhealth.ui.theme.Mf
import app.superhealth.training.ui.toKg
import app.superhealth.training.ui.toNum
import app.superhealth.training.ui.tr
import app.superhealth.training.ui.trf
import app.superhealth.training.ui.w
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** Durée maximale saisissable (« Confirm 9:59:59+ »). */
const val MAX_WORKOUT_SEC = 10 * 3600L - 1

/** Estimation de durée : de l'heure de début à la dernière série cochée, plus une minute. */
fun estimatedDuration(w: Workout): Long {
    val last = w.exercises.flatMap { it.sets }.mapNotNull { it.completedAt }.maxOrNull()
    val sets = w.exercises.sumOf { it.sets.size }
    return (if (last != null && last > w.startTime) (last - w.startTime) / 1000 + 60 else sets * 150L).coerceIn(60, MAX_WORKOUT_SEC)
}

/** Éditeur de séance passée (WorkoutHistoryEditView) : nom, début, durée, séries ; « Exit the editor? » si modifié. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditScreen(vm: AppViewModel, id: String, nav: NavHostController) {
    val st by vm.state.collectAsState()
    val db = vm.db.collectAsState().value ?: return
    val original = remember { st.history.firstOrNull { it.id == id } } ?: return
    var w by remember { mutableStateOf(original) }
    var confirmExit by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickDuration by remember { mutableStateOf(false) }
    fun exit() { if (w != original) confirmExit = true else nav.popBackStack() }
    BackHandler { exit() }

    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding().imePadding()) {
        app.superhealth.nutrition.ui.components.NavBar(tr("Modifier la séance"), ::exit, backIcon = Icons.Rounded.Close) {
            TextButton(onClick = { vm.saveWorkout(w.copy(exercises = w.exercises.filter { it.sets.isNotEmpty() })); nav.popBackStack() }, enabled = w != original) {
                Text(tr("Enregistrer"), color = if (w != original) Mf.colors.accent else Mf.colors.textTertiary, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            TextInput(tr("Nom de la séance"), w.name, { w = w.copy(name = it) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            GroupCard {
                SettingRow(tr("Date"), longDate(w.startTime), icon = Icons.Rounded.Today) { pickDate = true }
                RowDivider()
                SettingRow(tr("Heure de début"), timeOf(w.startTime), icon = Icons.Rounded.Schedule) { pickTime = true }
                RowDivider()
                SettingRow(tr("Durée"), durationWords(w.durationSec), icon = Icons.Rounded.Edit) { pickDuration = true }
            }
            w.exercises.forEachIndexed { ei, ex ->
                val e = db.exercise(ex.exerciseId)
                fun put(sets: List<SetLog>) { w = w.copy(exercises = w.exercises.toMutableList().also { it[ei] = ex.copy(sets = sets) }) }
                MfCard(Modifier.padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExerciseThumb(e, 40.dp, 10.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(e?.name ?: "?", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { w = w.copy(exercises = w.exercises.filterIndexed { j, _ -> j != ei }) }) {
                            Icon(Icons.Rounded.Delete, tr("Retirer l'exercice"), tint = Mf.colors.textSecondary)
                        }
                    }
                    var n = 0
                    ex.sets.forEachIndexed { si, set ->
                        val label = if (set.type == SetType.WARMUP) tr("É") else "${++n}"
                        key(set.id) {
                            SetEditRow(st.settings, set, label, e?.usesWeight != false,
                                onChange = { ns -> put(ex.sets.toMutableList().also { it[si] = ns }) },
                                onDelete = { put(ex.sets.filterIndexed { j, _ -> j != si }) })
                        }
                    }
                    TextButton(onClick = {
                        val last = ex.sets.lastOrNull { it.type != SetType.WARMUP } ?: ex.sets.lastOrNull()
                        put(ex.sets + (last?.copy(id = UUID.randomUUID().toString(), type = SetType.STANDARD) ?: SetLog(UUID.randomUUID().toString(), done = true, reps = 10)))
                    }) {
                        Icon(Icons.Rounded.Add, null, tint = Mf.colors.accent); Spacer(Modifier.width(4.dp))
                        Text(tr("Ajouter une série"), color = Mf.colors.accent)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false }, containerColor = Mf.colors.card,
        title = { Text(tr("Quitter l'éditeur ?")) },
        text = { Text(tr("Les modifications apportées à la séance seront perdues.")) },
        confirmButton = { TextButton(onClick = { confirmExit = false; nav.popBackStack() }) { Text(tr("Quitter"), color = Danger) } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text(tr("Rester"), color = Mf.colors.text) } })
    if (pickDate) {
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(w.startTime).atZone(zone)
        val state = rememberDatePickerState(start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        w = w.withStart(LocalDateTime.of(d, start.toLocalTime()).atZone(zone).toInstant().toEpochMilli())
                    }
                    pickDate = false
                }) { Text(tr("OK"), color = Mf.colors.accent) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(tr("Annuler"), color = Mf.colors.text) } }) { DatePicker(state) }
    }
    if (pickTime) {
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(w.startTime).atZone(zone)
        val state = rememberTimePickerState(start.hour, start.minute, true)
        AlertDialog(onDismissRequest = { pickTime = false }, containerColor = Mf.colors.card,
            title = { Text(tr("Heure de début")) }, text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    w = w.withStart(start.withHour(state.hour).withMinute(state.minute).withSecond(0).toInstant().toEpochMilli()); pickTime = false
                }) { Text(tr("OK"), color = Mf.colors.accent) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(tr("Annuler"), color = Mf.colors.text) } })
    }
    if (pickDuration) DurationDialog(w.durationSec, estimatedDuration(w), onDismiss = { pickDuration = false }) { w = w.copy(durationSec = it); pickDuration = false }
}

/** Décale l'heure de début et les heures de validation des séries d'autant. */
private fun Workout.withStart(ms: Long): Workout {
    val d = ms - startTime
    return copy(startTime = ms, exercises = exercises.map { ex -> ex.copy(sets = ex.sets.map { s -> s.copy(completedAt = s.completedAt?.plus(d)) }) })
}

private fun timeOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) }

/** Ligne de série éditable : charge, répétitions, RIR (texte libre gardé tant qu'il n'est pas un nombre valide). */
@Composable
private fun SetEditRow(settings: Settings, set: SetLog, label: String, usesWeight: Boolean, onChange: (SetLog) -> Unit, onDelete: () -> Unit) {
    var wt by remember { mutableStateOf(set.weight?.let { clean(settings.w(it)) } ?: "") }
    var reps by remember { mutableStateOf(set.reps?.toString() ?: "") }
    var rir by remember { mutableStateOf(set.rir?.toString() ?: "") }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, Modifier.width(22.dp), style = MaterialTheme.typography.labelLarge,
            color = if (set.type == SetType.WARMUP) Mf.colors.textTertiary else Mf.colors.textSecondary)
        if (usesWeight) SmallField(wt, settings.unit.label, Modifier.weight(1.2f), KeyboardType.Decimal) {
            wt = it; if (it.isBlank()) onChange(set.copy(weight = null)) else it.toNum()?.let { v -> onChange(set.copy(weight = settings.toKg(v))) }
        }
        SmallField(reps, tr("rép."), Modifier.weight(1f), KeyboardType.Number) {
            reps = it; it.toIntOrNull()?.let { v -> onChange(set.copy(reps = v)) }
        }
        SmallField(rir, tr("RIR"), Modifier.weight(0.8f), KeyboardType.Number) {
            rir = it; if (it.isBlank()) onChange(set.copy(rir = null)) else it.toIntOrNull()?.let { v -> onChange(set.copy(rir = v.coerceIn(0, 10))) }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Close, tr("Supprimer"), tint = Mf.colors.textTertiary, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun SmallField(value: String, suffix: String, modifier: Modifier, keyboard: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, modifier, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium,
        suffix = { Text(suffix, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Mf.colors.accent, unfocusedBorderColor = Mf.colors.divider, cursorColor = Mf.colors.accent))
}

/** « Edit Duration » : heures et minutes, plafonnées à 9:59 ; l'estimation est proposée. */
@Composable
fun DurationDialog(current: Long, estimate: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val cur = current.coerceAtMost(MAX_WORKOUT_SEC)
    var h by remember { mutableIntStateOf((cur / 3600).toInt()) }
    var m by remember { mutableIntStateOf(((cur % 3600) / 60).toInt()) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Mf.colors.card,
        title = { Text(tr("Modifier la durée")) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Wheel((0..9).map { trf("%1\$s h", it) }, h, Modifier.weight(1f), height = 160) { h = it }
                    Wheel((0..59).map { trf("%1\$s min", it) }, m, Modifier.weight(1f), height = 160) { m = it }
                }
                Text(trf("Estimation : %1\$s", durationWords(estimate)), Modifier.padding(top = 8.dp).clip(CircleShape).clickable {
                    h = (estimate / 3600).toInt(); m = ((estimate % 3600) / 60).toInt()
                }.padding(6.dp), color = Mf.colors.accent, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = { onSave((h * 3600L + m * 60L).coerceAtLeast(60)) }) { Text(tr("Confirmer"), color = Mf.colors.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annuler"), color = Mf.colors.text) } })
}

/** « Was your workout longer than 10 hours? » : proposé à la fin d'une séance oubliée. */
@Composable
fun LongWorkoutDialog(w: Workout, onKeep: () -> Unit, onEstimate: (Long) -> Unit, onEdit: () -> Unit) {
    val est = estimatedDuration(w)
    AlertDialog(onDismissRequest = onKeep, containerColor = Mf.colors.card,
        title = { Text(tr("Votre séance a-t-elle duré plus de 10 heures ?")) },
        text = { Text(trf("Vous pouvez utiliser notre estimation (%1\$s) ou saisir la durée vous-même.", durationWords(est))) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onEstimate(est) }) { Text(tr("Utiliser l'estimation"), color = Mf.colors.accent) }
                TextButton(onClick = onEdit) { Text(tr("Modifier la durée"), color = Mf.colors.accent) }
                TextButton(onClick = onKeep) { Text(tr("Garder"), color = Mf.colors.text) }
            }
        })
}

/** Options d'une séance enregistrée (« Workout Options ») : modifier, durée, bibliothèque, suppression. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutOptionsMenu(onDismiss: () -> Unit, onEdit: () -> Unit, onDuration: () -> Unit, onLibrary: () -> Unit, onDelete: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.card) {
        Column(Modifier.padding(bottom = 16.dp).navigationBarsPadding()) {
            Text(tr("Options de la séance"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
            SettingRow(tr("Modifier la séance"), icon = Icons.Rounded.Edit, onClick = onEdit)
            SettingRow(tr("Modifier la durée"), icon = Icons.Rounded.Schedule, onClick = onDuration)
            SettingRow(tr("Enregistrer dans la bibliothèque"), subtitle = tr("Ajoute la séance à votre bibliothèque d'entraînements"), icon = Icons.Rounded.LibraryAdd, onClick = onLibrary)
            SettingRow(tr("Supprimer la séance"), icon = Icons.Rounded.Delete, danger = true, onClick = onDelete)
        }
    }
}

/** « Workout Changes » (PlanDiffSheet) : report des écarts de la séance dans le programme, avec annulation par écart. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDiffSheet(vm: AppViewModel, db: ExerciseDb, change: AppViewModel.PlanChange) {
    val d = change.diff
    var sel by remember { mutableStateOf(d.all()) }
    val cycle = change.session.cycleIndex ?: 0
    val programName = vm.state.collectAsState().value.programs.firstOrNull { it.id == change.programId }?.name ?: ""
    ModalBottomSheet(onDismissRequest = { vm.planChange.value = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.card) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Text(tr("Modifications de séance"), style = MaterialTheme.typography.titleLarge)
            Text(trf("Reporter ces changements dans « %1\$s » (%2\$s) ?", programName, change.day.name),
                style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
            fun toggle(set: Set<String>, id: String) = if (id in set) set - id else set + id
            if (d.added.isNotEmpty()) {
                SectionLabel(tr("Exercices ajoutés"))
                d.added.forEach { ex ->
                    DiffRow(db.exercise(ex.exerciseId)?.name ?: "?", trf("%1\$s séries", ex.sets.count { it.type != SetType.WARMUP }), ex.id in sel.added) {
                        sel = sel.copy(added = toggle(sel.added, ex.id))
                    }
                }
            }
            if (d.removed.isNotEmpty()) {
                SectionLabel(tr("Exercices retirés"))
                d.removed.forEach { pe ->
                    DiffRow(db.exercise(pe.exerciseId)?.name ?: "?", trf("Retiré de %1\$s", change.day.name), pe.id in sel.removed) {
                        sel = sel.copy(removed = toggle(sel.removed, pe.id))
                    }
                }
            }
            if (d.modified.isNotEmpty()) {
                SectionLabel(tr("Exercices modifiés"))
                d.modified.forEach { (pe, ex) ->
                    val before = app.superhealth.training.domain.Generator.targetsFor(pe, cycle).size
                    val after = ex.sets.count { it.type != SetType.WARMUP }
                    val parts = buildList {
                        if (before != after) add(trf("Avant : %1\$s séries · Après : %2\$s séries", before, after))
                        if ((pe.supersetGroup == null) != (ex.supersetGroup == null)) add(if (ex.supersetGroup != null) tr("Ajouté à un superset") else tr("Retiré du superset"))
                    }
                    DiffRow(db.exercise(pe.exerciseId)?.name ?: "?", parts.joinToString(" · "), pe.id in sel.modified) {
                        sel = sel.copy(modified = toggle(sel.modified, pe.id))
                    }
                }
            }
            if (d.reordered) {
                SectionLabel(tr("Ordre des exercices"))
                DiffRow(tr("Nouvel ordre"), change.session.exercises.joinToString(" → ") { db.exercise(it.exerciseId)?.name ?: "?" }, sel.order) {
                    sel = sel.copy(order = !sel.order)
                }
            }
            Spacer(Modifier.height(16.dp))
            val any = sel.added.isNotEmpty() || sel.removed.isNotEmpty() || sel.modified.isNotEmpty() || sel.order
            PillButton(tr("Mettre à jour le programme"), Modifier.fillMaxWidth(), enabled = any) { vm.applyPlanChange(sel) }
            PillButton(tr("Ne pas modifier le programme"), Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), strong = false) { vm.planChange.value = null }
        }
    }
}

@Composable
private fun DiffRow(title: String, detail: String, kept: Boolean, onUndo: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (kept) null else TextDecoration.LineThrough, color = if (kept) Mf.colors.text else Mf.colors.textTertiary)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onUndo) { Text(if (kept) tr("Défaire") else tr("Rétablir"), color = if (kept) app.superhealth.ui.theme.Base.Red else Mf.colors.accent) }
    }
}
