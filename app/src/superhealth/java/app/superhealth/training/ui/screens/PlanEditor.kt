package app.superhealth.training.ui.screens

import app.superhealth.training.ui.tr
import app.superhealth.training.ui.trf
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.training.domain.CustomWorkout
import app.superhealth.training.domain.ExerciseDb
import app.superhealth.training.domain.Generator
import app.superhealth.training.domain.Program
import app.superhealth.training.domain.ProgramDay
import app.superhealth.training.domain.ProgramExercise
import app.superhealth.training.domain.Supersets
import app.superhealth.training.domain.TargetSet
import app.superhealth.training.ui.AppViewModel
import app.superhealth.training.ui.Picker
import app.superhealth.training.ui.components.GroupCard
import app.superhealth.training.ui.components.MfCard
import app.superhealth.training.ui.components.PillButton
import app.superhealth.training.ui.components.ExerciseThumb
import app.superhealth.training.ui.components.SectionLabel
import app.superhealth.training.ui.components.SwitchRow
import app.superhealth.training.ui.components.TextInput
import app.superhealth.training.ui.restLabel
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.WorkoutBlue
import java.util.UUID

/*
 * Édition manuelle, sans le générateur : jours de programme et séances de la bibliothèque partagent le même éditeur
 * (cibles, supersets, ordre, remplacement). Une liste est désignée par une cible texte, comme pour le sélecteur d'exercices :
 * « idProgramme:jour » ou « custom:idSéance ».
 */

private const val CUSTOM = "custom:"

/** Exercices de la liste [target], ou null si elle n'existe plus. */
fun AppViewModel.planExercises(target: String): List<ProgramExercise>? {
    val s = state.value
    if (target.startsWith(CUSTOM)) return s.customWorkouts.firstOrNull { it.id == target.removePrefix(CUSTOM) }?.exercises
    val (pid, di) = target.split(':').let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: return null) }
    return s.programs.firstOrNull { it.id == pid }?.days?.getOrNull(di)?.exercises
}

/** Nombre de cycles de la liste (1 pour une séance de bibliothèque). */
fun AppViewModel.planCycles(target: String): Int =
    if (target.startsWith(CUSTOM)) 1 else state.value.programs.firstOrNull { it.id == target.substringBefore(':') }?.numCycles?.coerceAtLeast(1) ?: 1

/** Modifie la liste [target] (un superset réduit à un seul exercice est défait). */
fun AppViewModel.updatePlan(target: String, f: (List<ProgramExercise>) -> List<ProgramExercise>) {
    fun clean(l: List<ProgramExercise>): List<ProgramExercise> {
        val counts = l.groupingBy { it.supersetGroup }.eachCount()
        // Un superset n'a de sens qu'entre exercices voisins : un groupe séparé par un autre exercice est défait aussi.
        return l.mapIndexed { i, pe ->
            val g = pe.supersetGroup ?: return@mapIndexed pe
            val neighbour = l.getOrNull(i - 1)?.supersetGroup == g || l.getOrNull(i + 1)?.supersetGroup == g
            if (counts[g] == 1 || !neighbour) pe.copy(supersetGroup = null) else pe
        }
    }
    update { s ->
        if (target.startsWith(CUSTOM)) {
            val id = target.removePrefix(CUSTOM)
            s.copy(customWorkouts = s.customWorkouts.map { if (it.id == id) it.copy(exercises = clean(f(it.exercises))) else it })
        } else {
            val (pid, di) = target.split(':').let { it[0] to it[1].toInt() }
            s.copy(programs = s.programs.map { p ->
                if (p.id != pid) p else p.copy(days = p.days.mapIndexed { i, d -> if (i != di) d else d.copy(exercises = clean(f(d.exercises))) })
            })
        }
    }
}

/** Ajoute des exercices à la liste [target] : 3 séries selon l'objectif du programme (8–12 répétitions pour une séance libre). */
fun AppViewModel.addToPlan(target: String, ids: List<String>) {
    val program = if (target.startsWith(CUSTOM)) null else state.value.programs.firstOrNull { it.id == target.substringBefore(':') }
    val cycles = planCycles(target)
    updatePlan(target) { list ->
        list + ids.map { id ->
            val e = db.value?.exercise(id)
            val t = if (program != null) Generator.baseTarget(program.goal, if (e?.isCompound == true) Generator.Template.SECONDARY else Generator.Template.ACCESSORY)
            else TargetSet(minReps = if (e?.isCompound == true) 6 else 8, maxReps = if (e?.isCompound == true) 10 else 12, restSec = state.value.settings.restDefaultSec)
            ProgramExercise(UUID.randomUUID().toString(), id, List(cycles) { List(3) { t } })
        }
    }
}

/** Relie l'exercice au suivant (superset, triset…) ou le sort de son groupe. */
private fun regroup(list: List<ProgramExercise>, f: (List<Int?>) -> List<Int?>): List<ProgramExercise> {
    val g = f(list.map { it.supersetGroup })
    return list.mapIndexed { k, pe -> pe.copy(supersetGroup = g[k]) }
}

/** Lettre d'un superset (A, B…) dans l'ordre d'apparition. */
private fun supersetLetters(list: List<ProgramExercise>): Map<Int, Char> =
    list.mapNotNull { it.supersetGroup }.distinct().mapIndexed { i, g -> g to ('A' + i) }.toMap()

/**
 * Exercices d'une liste avec, pour chacun, cibles et actions (fiche). Les supersets sont reliés par une barre de couleur
 * et une lettre ; « Superset avec le suivant » se règle dans la fiche de l'exercice.
 */
@Composable
fun PlanExercises(vm: AppViewModel, db: ExerciseDb, target: String, exercises: List<ProgramExercise>, cycle: Int, nav: NavHostController) {
    var editing by remember { mutableStateOf<Int?>(null) }
    var reorder by remember { mutableStateOf(false) }
    val letters = supersetLetters(exercises)
    val sizes = exercises.groupingBy { it.supersetGroup }.eachCount()
    exercises.forEachIndexed { i, pe ->
        val e = db.exercise(pe.exerciseId)
        val t = Generator.targetsFor(pe, cycle)
        val g = pe.supersetGroup
        val linkedNext = g != null && exercises.getOrNull(i + 1)?.supersetGroup == g
        val linkedPrev = g != null && exercises.getOrNull(i - 1)?.supersetGroup == g
        MfCard(Modifier.padding(top = if (linkedPrev) 0.dp else 5.dp, bottom = if (linkedNext) 0.dp else 5.dp), onClick = { editing = i }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (g != null) Box(Modifier.width(4.dp).height(44.dp).clip(CircleShape).background(WorkoutBlue))
                if (g != null) Spacer(Modifier.width(8.dp))
                ExerciseThumb(e, 44.dp, 10.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    if (g != null) Text(Supersets.label(sizes[g] ?: 2).tr() + " " + (letters[g] ?: 'A'), style = MaterialTheme.typography.labelSmall, color = WorkoutBlue,
                        fontWeight = FontWeight.Bold)
                    Text(e?.name ?: "?", style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Text(trf("%1\$s · repos %2\$s", targetSummary(t), restLabel(t.firstOrNull()?.restSec ?: 120)), style = MaterialTheme.typography.bodySmall,
                        color = Mf.colors.textSecondary)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
            }
        }
        // Lien visuel entre deux exercices du même superset.
        if (linkedNext) Box(Modifier.fillMaxWidth().height(10.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Link, null, Modifier.size(14.dp), tint = WorkoutBlue)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(tr("Ajouter des exercices"), Modifier.weight(1f), strong = false, icon = Icons.Rounded.Add) {
            nav.navigate("picker/${Picker.DAY}?target=${target}")
        }
        if (exercises.size > 1) PillButton(tr("Réorganiser"), Modifier.weight(1f), strong = false, icon = Icons.Rounded.SwapVert) { reorder = true }
    }
    if (reorder) app.superhealth.training.ui.components.ReorderSheet(exercises.map { it.id to db.exercise(it.exerciseId) }, exercises.map { it.supersetGroup },
        onMove = { from, to -> vm.updatePlan(target) { l -> regroup(l.toMutableList().also { it.add(to, it.removeAt(from)) }) { g -> Supersets.normalize(g) } } },
        onDismiss = { reorder = false })
    editing?.let { i ->
        val pe = exercises.getOrNull(i) ?: run { editing = null; return }
        // le groupe s'étend depuis son dernier membre, quel que soit l'exercice ouvert
        var groupEnd = i
        while (pe.supersetGroup != null && exercises.getOrNull(groupEnd + 1)?.supersetGroup == pe.supersetGroup) groupEnd++
        PlanExerciseSheet(pe, cycle, db.exercise(pe.exerciseId)?.name ?: "", inSuperset = pe.supersetGroup != null, canLink = groupEnd < exercises.lastIndex,
            onDismiss = { editing = null },
            onSave = { ne -> vm.updatePlan(target) { l -> l.toMutableList().also { it[i] = ne } }; editing = null },
            onSaveAll = { ne -> vm.updatePlan(target) { l -> l.toMutableList().also { it[i] = ne } }; editing = null },
            onLink = { vm.updatePlan(target) { regroup(it) { g -> Supersets.link(g, groupEnd) } }; editing = null },
            onUnlink = { vm.updatePlan(target) { regroup(it) { g -> Supersets.unlink(g, i) } }; editing = null },
            onReorder = { editing = null; reorder = true },
            onUp = if (i > 0) ({ vm.updatePlan(target) { l -> l.toMutableList().also { java.util.Collections.swap(it, i, i - 1) } }; editing = null }) else null,
            onDown = if (i < exercises.lastIndex) ({ vm.updatePlan(target) { l -> l.toMutableList().also { java.util.Collections.swap(it, i, i + 1) } }; editing = null }) else null,
            onSwap = { editing = null; nav.navigate("picker/${Picker.SWAP_PLAN}?target=${target}|${i}") },
            onRemove = { vm.updatePlan(target) { l -> l.filterIndexed { j, _ -> j != i } }; editing = null })
    }
}

/**
 * Fiche d'un exercice de la liste (EditExerciseTargetSheet) : séries, plage de répétitions, RIR, repos, puis superset,
 * ordre, remplacement et retrait.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanExerciseSheet(
    pe: ProgramExercise, cycle: Int, name: String, inSuperset: Boolean, canLink: Boolean,
    onDismiss: () -> Unit, onSave: (ProgramExercise) -> Unit, onSaveAll: (ProgramExercise) -> Unit,
    onLink: () -> Unit, onUnlink: () -> Unit, onReorder: () -> Unit, onUp: (() -> Unit)?, onDown: (() -> Unit)?, onSwap: () -> Unit, onRemove: () -> Unit,
) {
    val cur = Generator.targetsFor(pe, cycle)
    var sets by remember { mutableIntStateOf(cur.size) }
    var min by remember { mutableIntStateOf(cur.firstOrNull()?.minReps ?: 8) }
    var max by remember { mutableIntStateOf(cur.firstOrNull()?.maxReps ?: 12) }
    var rir by remember { mutableIntStateOf(cur.firstOrNull()?.rir ?: 2) }
    var rest by remember { mutableIntStateOf(cur.firstOrNull()?.restSec ?: 120) }
    fun built() = List(sets) { (cur.firstOrNull() ?: TargetSet()).copy(minReps = min, maxReps = max.coerceAtLeast(min), rir = rir, restSec = rest) }
    val multiCycle = pe.cycles.size > 1
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.card) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            if (multiCycle) Text(trf("Cycle %1\$s", cycle + 1), color = Mf.colors.textSecondary)
            PlanStepper(tr("Séries"), sets, 1, 10) { sets = it }
            PlanStepper(tr("Répétitions min."), min, 1, 50) { min = it; if (max < it) max = it }
            PlanStepper(tr("Répétitions max."), max, 1, 60) { max = it.coerceAtLeast(min) }
            PlanStepper(tr("RIR (répétitions en réserve)"), rir, 0, 5) { rir = it }
            PlanStepper(tr("Repos"), rest, 0, 600, step = 15, label = { restLabel(it) }) { rest = it }
            Spacer(Modifier.height(14.dp))
            if (multiCycle) {
                PillButton(tr("Enregistrer pour ce cycle"), Modifier.fillMaxWidth()) {
                    onSave(pe.copy(cycles = pe.cycles.toMutableList().also { it[cycle.coerceIn(0, it.lastIndex)] = built() }))
                }
                PillButton(tr("Enregistrer pour tous les cycles"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false) {
                    onSaveAll(pe.copy(cycles = pe.cycles.map { built() }))
                }
            } else PillButton(tr("Enregistrer"), Modifier.fillMaxWidth()) { onSaveAll(pe.copy(cycles = pe.cycles.map { built() }.ifEmpty { listOf(built()) })) }
            Spacer(Modifier.height(14.dp))
            GroupCard {
                if (canLink) SheetAction(Icons.Rounded.Link, if (inSuperset) tr("Ajouter l'exercice suivant au groupe (triset)") else tr("Superset avec l'exercice suivant"), onLink)
                if (inSuperset) SheetAction(Icons.Rounded.LinkOff, tr("Retirer du superset"), onUnlink)
                SheetAction(Icons.Rounded.SwapVert, tr("Réorganiser les exercices"), onReorder)
                SheetAction(Icons.Rounded.SwapHoriz, tr("Remplacer l'exercice"), onSwap)
                onUp?.let { SheetAction(Icons.Rounded.KeyboardArrowUp, tr("Monter"), it) }
                onDown?.let { SheetAction(Icons.Rounded.KeyboardArrowDown, tr("Descendre"), it) }
            }
            TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(tr("Retirer l'exercice"), color = Color(0xFFE11900)) }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = WorkoutBlue)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun PlanStepper(title: String, value: Int, min: Int, max: Int, step: Int = 1, label: (Int) -> String = { "$it" }, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Round(Icons.Rounded.Remove, small = true) { if (value - step >= min) onChange(value - step) }
        Text(label(value), Modifier.width(56.dp), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Round(Icons.Rounded.Add, small = true) { if (value + step <= max) onChange(value + step) }
    }
}

// ----- Séance de la bibliothèque, construite à la main -----

/** Séance de la bibliothèque : nom, exercices (cibles, supersets, ordre), puis la commencer. */
@Composable
fun CustomWorkoutScreen(vm: AppViewModel, id: String, nav: NavHostController) {
    val st by vm.state.collectAsState()
    val db = vm.db.collectAsState().value ?: return
    val w = st.customWorkouts.firstOrNull { it.id == id } ?: run { LaunchedEffect(Unit) { nav.popBackStack() }; return }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    DetailScaffold(w.name, nav, actions = { TextButton(onClick = { rename = true }) { Text(tr("Renommer"), color = Mf.colors.accent) } }) {
        Text(tr("Vos exercices, séries et supersets. Les charges proposées suivent votre historique."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        if (w.exercises.isEmpty()) MfCard { Text(tr("Aucun exercice pour l'instant."), color = Mf.colors.textSecondary) }
        PlanExercises(vm, db, CUSTOM + w.id, w.exercises, 0, nav)
        if (w.exercises.isNotEmpty()) PillButton(tr("Commencer cette séance"), Modifier.fillMaxWidth().padding(top = 8.dp), icon = Icons.Rounded.PlayArrow) {
            if (st.session == null) vm.startCustom(w); nav.navigate("session")
        }
        SectionLabel(tr("Progression"))
        GroupCard {
            SwitchRow(tr("Progression intelligente"), tr("Désactivée : reprend vos dernières charges et répétitions."),
                st.settings.smartProgression) { on -> vm.settings { it.copy(smartProgression = on) } }
        }
        PillButton(tr("Dupliquer"), Modifier.fillMaxWidth().padding(top = 12.dp), strong = false) {
            vm.saveCustom(w.copy(id = UUID.randomUUID().toString(), name = w.name + tr(" (copie)"))); nav.popBackStack()
        }
        Text(tr("Supprimer la séance"), Modifier.fillMaxWidth().clickable { confirmDelete = true }.padding(14.dp), color = Color(0xFFE11900),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
    if (rename) {
        var n by remember { mutableStateOf(w.name) }
        AlertDialog(onDismissRequest = { rename = false }, containerColor = Mf.colors.card, title = { Text(tr("Renommer la séance")) },
            text = { TextInput(tr("Nom"), n, { n = it }) },
            confirmButton = { TextButton(onClick = { vm.saveCustom(w.copy(name = n.ifBlank { w.name })); rename = false }) { Text(tr("Enregistrer"), color = Mf.colors.accent) } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text(tr("Annuler"), color = Mf.colors.text) } })
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, containerColor = Mf.colors.card,
        title = { Text(tr("Supprimer cette séance ?")) }, text = { Text(tr("Votre historique est conservé.")) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteCustom(w.id); nav.popBackStack() }) { Text(tr("Supprimer"), color = Color(0xFFE11900)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Annuler"), color = Mf.colors.text) } })
}

// ----- Création -----

/** Choix de création : programme généré, programme manuel, séance de bibliothèque. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTrainingSheet(vm: AppViewModel, nav: NavHostController, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.card) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Text(tr("Créer"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            CreateOption(Icons.Rounded.AutoAwesome, tr("Programme généré"), tr("Construit selon vos objectifs, votre emploi du temps et votre équipement.")) {
                onDismiss(); nav.navigate("wizard")
            }
            CreateOption(Icons.Rounded.EditNote, tr("Programme manuel"), tr("Vos jours et vos exercices, sans l'algorithme de génération.")) {
                onDismiss()
                val p = Program(UUID.randomUUID().toString(), tr("Mon programme"), days = listOf(
                    ProgramDay(UUID.randomUUID().toString(), trf("Jour %1\$s", 1)),
                    ProgramDay(UUID.randomUUID().toString(), trf("Jour %1\$s", 2)),
                    ProgramDay(UUID.randomUUID().toString(), trf("Jour %1\$s", 3)),
                ), numCycles = 1, runIndefinitely = true)
                vm.saveProgram(p); nav.navigate("program/${p.id}")
            }
            CreateOption(Icons.Rounded.FitnessCenter, tr("Séance perso"), tr("Une séance à refaire quand vous voulez, rangée dans la bibliothèque.")) {
                onDismiss()
                val w = CustomWorkout(UUID.randomUUID().toString(), tr("Ma séance"), emptyList(), vm.state.value.settings.activeGymId)
                vm.saveCustom(w); nav.navigate("custom/${w.id}")
            }
        }
    }
}

@Composable
private fun CreateOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(Mf.colors.background).clickable(onClick = onClick)
        .padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(WorkoutBlue.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = WorkoutBlue)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
    }
}
