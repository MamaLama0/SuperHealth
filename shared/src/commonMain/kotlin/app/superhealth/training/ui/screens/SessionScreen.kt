package app.superhealth.training.ui.screens

import app.superhealth.platform.*

import androidx.compose.material.icons.rounded.CheckCircle
import app.superhealth.ui.theme.container
import androidx.compose.ui.draw.shadow
import app.superhealth.training.ui.tr
import app.superhealth.training.ui.trf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.SwapVert
import app.superhealth.training.domain.Strength
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.training.domain.Analytics
import app.superhealth.training.domain.Exercise
import app.superhealth.training.domain.Plates
import app.superhealth.training.domain.Session
import app.superhealth.training.domain.SessionExercise
import app.superhealth.training.domain.SetLog
import app.superhealth.training.domain.SetType
import app.superhealth.training.domain.Settings
import app.superhealth.training.domain.Workout
import app.superhealth.training.ui.AppViewModel
import app.superhealth.training.ui.AsyncAsset
import app.superhealth.training.ui.Picker
import app.superhealth.training.ui.clean
import app.superhealth.training.ui.components.PillButton
import app.superhealth.training.ui.components.TextInput
import app.superhealth.training.ui.duration
import app.superhealth.training.ui.restLabel
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.WorkoutBlue
import app.superhealth.training.ui.toKg
import app.superhealth.training.ui.toNum
import app.superhealth.training.ui.w
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.superhealth.platform.UUID
import kotlin.math.roundToInt

internal val Danger = app.superhealth.ui.theme.Base.Red

/**
 * Séance en cours, sur le modèle de WorkoutSessionView de MacroFactor Workouts :
 * une page par exercice (PageView) synchronisée avec la rangée d'exercices (ExerciseRow), actions de l'exercice
 * (Info, Warm Up, Targets, Swap, Superset, Equipment, More), séries terminées repliées, minuteur de repos et
 * menu « Workout Options » (pause, réduire, terminer, abandonner).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionScreen(vm: AppViewModel, nav: NavHostController) {
    val st by vm.state.collectAsState()
    val db by vm.db.collectAsState()
    val s = st.session
    val completed by vm.completed.collectAsState()
    // fin de séance : la racine ouvre « Séance terminée » ; sinon (abandon) on revient simplement en arrière
    // séance terminée ailleurs : l'écran se retire, seulement s'il est encore en haut de la pile (après « Abandonner », déjà
    // dépilé, un second retour vidait la pile : page blanche et barre d'onglets en haut)
    if (s == null || db == null) { LaunchedEffect(Unit) { if (completed == null && nav.currentBackStackEntry?.destination?.route == "session") nav.popBackStack() }; return }
    // Horloge de la séance : lue seulement par la barre du haut et la carte de repos (voir Ticking), pour ne pas
    // recomposer les pages d'exercices deux fois par seconde (saccades au passage d'un exercice à l'autre).
    val clock = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(500); clock.longValue = System.currentTimeMillis() } }
    // notifications (séance en cours, fin du repos) : permission demandée à la première séance (Android 13+)
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { requestNotificationPermission() }
    // écran allumé pendant la séance (mains prises, téléphone posé)
    androidx.compose.runtime.DisposableEffect(Unit) { keepScreenOn(true); onDispose { keepScreenOn(false) } }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var plateFor by remember { mutableStateOf<Pair<Int, Double>?>(null) }
    var noteFor by remember { mutableStateOf<Int?>(null) }
    var restFor by remember { mutableStateOf<Int?>(null) }
    var sessionNote by remember { mutableStateOf(false) }
    var reorder by remember { mutableStateOf(false) }
    var targetsFor by remember { mutableStateOf<Int?>(null) }
    // « Terminer la séance ? » proposé une fois quand la dernière série est cochée
    var allDonePrompt by remember { mutableStateOf(false) }
    val previous = remember(st.history) { previousSets(st.history) }
    /** Meilleur 1RM estimé de chaque exercice avant cette séance : une série qui le dépasse est un record, signalé tout de suite. */
    val bests = remember(st.history, db) { bestE1rms(st.history, db!!) }
    val scope = rememberCoroutineScope()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val count = s.exercises.size + 1 // dernière page : ajouter des exercices / terminer
    val pager = rememberPagerState(initialPage = s.current.coerceIn(0, count - 1)) { count }
    // Exercice courant enregistré une fois la page posée, pas au milieu du glissement.
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { if (it < vm.state.value.session?.exercises?.size ?: 0) vm.setCurrent(it) } }
    fun goTo(i: Int) { scope.launch { pager.animateScrollToPage(i.coerceIn(0, count - 1)) } }

    Box(Modifier.fillMaxSize().background(Mf.colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Ticking(clock) { now -> TopBar(s, now, onMinimize = { nav.popBackStack() }, onOptions = { options = true }, onFinish = { confirmFinish = true }, onPause = { vm.togglePause() }) }
            ExerciseStrip(s, db!!::exercise, pager.currentPage, onSelect = { goTo(it) }, onAdd = { nav.navigate("picker/${Picker.SESSION}") })
            HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1, key = { if (it < s.exercises.size) s.exercises[it].id else "end" }) { page ->
                if (page >= s.exercises.size) EndPage(s, onAdd = { nav.navigate("picker/${Picker.SESSION}") }, onFinish = { confirmFinish = true })
                else {
                    val ex = s.exercises[page]
                    ExercisePage(vm, st.settings, s, page, ex, db!!.exercise(ex.exerciseId), previous[ex.exerciseId], bests[ex.exerciseId], nav,
                        onPlates = { plateFor = page to it }, onNote = { noteFor = page }, onRest = { restFor = page }, onReorder = { reorder = true }, onTargets = { targetsFor = page },
                        onToggle = { si ->
                            focus.clearFocus()
                            val wasDone = ex.sets[si].done
                            haptic.performHapticFeedback(if (wasDone) androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick
                                else androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                            vm.toggleSet(page, si)
                            if (!wasDone) vm.state.value.session?.let { ns ->
                                if (ns.exercises.isNotEmpty() && ns.exercises.all { e -> e.sets.all { it.done } }) allDonePrompt = true
                                else vm.nextFocus(ns, page)?.let { n -> scope.launch { delay(350); pager.animateScrollToPage(n) } }
                            }
                        })
                }
            }
        }
        val rest = s.restEndsAt
        // la carte reste composée pendant l'animation de sortie : on garde la dernière fin de repos connue
        var shownRest by remember { mutableLongStateOf(0L) }
        if (rest != null) shownRest = rest
        // repos réduit en pastille (pour voir les séries) ; chaque nouveau repos s'affiche en grand
        var restMini by remember { mutableStateOf(false) }
        LaunchedEffect(rest) { restMini = false }
        Ticking(clock) { now ->
            AnimatedVisibility(rest != null && rest > now && !restMini, Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                RestCard(s, shownRest, now, st.settings, db!!::exercise, vm, onCollapse = { restMini = true })
            }
            AnimatedVisibility(rest != null && rest > now && restMini, Modifier.align(Alignment.BottomEnd),
                enter = fadeIn() + androidx.compose.animation.scaleIn(), exit = fadeOut() + androidx.compose.animation.scaleOut()) {
                RestPill(s, shownRest, now) { restMini = false }
            }
        }
    }

    if (options) WorkoutOptionsSheet(s, onDismiss = { options = false },
        onPause = { options = false; vm.togglePause() },
        onMinimize = { options = false; nav.popBackStack() },
        onFinish = { options = false; confirmFinish = true },
        onSettings = { options = false; nav.navigate("t/settings") },
        onNote = { options = false; sessionNote = true },
        onReorder = { options = false; reorder = true },
        onGym = { options = false; s.gymId?.let { nav.navigate("gym/${it}") } ?: nav.navigate("gyms") },
        onDiscard = { options = false; confirmDiscard = true })
    if (confirmFinish) {
        val left = s.exercises.sumOf { ex -> ex.sets.count { !it.done && it.type != SetType.WARMUP } }
        // CompleteWorkoutDialog : « Log All Sets » / « Log Only Completed Sets » quand des séries restent à cocher
        val fillable = s.exercises.sumOf { ex -> ex.sets.count { !it.done && (it.reps ?: 0) > 0 } }
        // aucune série faite ni remplie : rien à enregistrer, le programme n'avance pas
        if (fillable == 0 && s.exercises.none { ex -> ex.sets.any { it.done } }) AlertDialog(onDismissRequest = { confirmFinish = false }, containerColor = Mf.colors.card,
            title = { Text(tr("Aucune série faite")) },
            text = { Text(tr("Rien ne sera enregistré.")) },
            confirmButton = { TextButton(onClick = { confirmFinish = false; vm.discardSession(); nav.popBackStack() }) { Text(tr("Fermer la séance"), color = Mf.colors.accent) } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text(tr("Continuer"), color = Mf.colors.text) } })
        else if (left > 0 && fillable > 0) AlertDialog(onDismissRequest = { confirmFinish = false }, containerColor = Mf.colors.card,
            title = { Text(tr("Terminer l'entraînement ?")) },
            text = { Text(trf("%1\$s série(s) de travail ne sont pas cochées.", left)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { confirmFinish = false; vm.finishSession(logAll = true) }) { Text(tr("Enregistrer toutes les séries"), color = Mf.colors.accent) }
                    TextButton(onClick = { confirmFinish = false; vm.finishSession() }) { Text(tr("Seulement les séries faites"), color = Mf.colors.accent) }
                    TextButton(onClick = { confirmFinish = false }) { Text(tr("Continuer la séance"), color = Mf.colors.text) }
                }
            })
        else AlertDialog(onDismissRequest = { confirmFinish = false }, containerColor = Mf.colors.card,
            title = { Text(tr("Terminer l'entraînement ?")) },
            text = { Text(if (left > 0) trf("%1\$s série(s) non validée(s) ne seront pas enregistrées.", left) else tr("Bravo ! La séance va être enregistrée.")) },
            confirmButton = { TextButton(onClick = { confirmFinish = false; vm.finishSession() }) { Text(tr("Terminer"), color = Mf.colors.accent) } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text(tr("Continuer"), color = Mf.colors.text) } })
    }
    if (allDonePrompt) AlertDialog(onDismissRequest = { allDonePrompt = false }, containerColor = Mf.colors.card,
        icon = { Icon(Icons.Rounded.Flag, null, tint = WorkoutBlue) },
        title = { Text(tr("Finir la séance ?")) },
        text = { Text(tr("Tous les exercices sont faits. Bravo !")) },
        confirmButton = { TextButton(onClick = { allDonePrompt = false; vm.finishSession() }) { Text(tr("Terminer"), color = Mf.colors.accent) } },
        dismissButton = { TextButton(onClick = { allDonePrompt = false }) { Text(tr("Continuer"), color = Mf.colors.text) } })
    targetsFor?.let { ei -> s.exercises.getOrNull(ei)?.let { ex -> TargetsSheet(ex, onDismiss = { targetsFor = null }) { ne -> vm.session { ss ->
        ss.copy(exercises = ss.exercises.toMutableList().also { if (ei in it.indices) it[ei] = ne }) }; targetsFor = null } } }
    if (reorder) app.superhealth.training.ui.components.ReorderSheet(s.exercises.map { it.id to db!!.exercise(it.exerciseId) }, s.exercises.map { it.supersetGroup },
        onMove = { from, to -> vm.moveExercise(from, to); scope.launch { pager.scrollToPage(to) } }, onDismiss = { reorder = false })
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, containerColor = Mf.colors.card,
        title = { Text(tr("Abandonner la séance ?")) },
        text = { Text(tr("Rien ne sera enregistré.")) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; vm.discardSession(); nav.popBackStack() }) { Text(tr("Abandonner"), color = Danger) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(tr("Annuler"), color = Mf.colors.text) } })
    plateFor?.let { (ei, wgt) ->
        val ex = s.exercises.getOrNull(ei)
        CalculatorSheet(vm, st.settings, wgt, ex, ex?.let { db!!.exercise(it.exerciseId) }, st.history) { plateFor = null }
    }
    noteFor?.let { ei ->
        NoteSheet(s.exercises.getOrNull(ei)?.note.orEmpty(), onDismiss = { noteFor = null }) { text ->
            vm.session { ss -> ss.copy(exercises = ss.exercises.toMutableList().also { it[ei] = it[ei].copy(note = text) }) }; noteFor = null
        }
    }
    if (sessionNote) NoteSheet(s.note, title = tr("Note de séance"), onDismiss = { sessionNote = false }) { text ->
        vm.session { it.copy(note = text) }; sessionNote = false
    }
    restFor?.let { ei ->
        RestSheet(s.exercises.getOrNull(ei)?.restSec ?: 120, onDismiss = { restFor = null }) { r ->
            vm.setRest(ei, r); restFor = null
        }
    }
}

private fun bestE1rms(history: List<Workout>, db: app.superhealth.training.domain.ExerciseDb): Map<String, Double> {
    val out = mutableMapOf<String, Double>()
    history.forEach { w ->
        w.exercises.forEach { ex ->
            val upper = db.exercise(ex.exerciseId)?.upperBody == true
            Analytics.workingSets(ex).filter { (it.weight ?: 0.0) > 0 && (it.reps ?: 0) > 0 }.forEach { set ->
                val v = Strength.e1rm(set.weight!!, set.reps!!, set.rir, upper, set.partialReps)
                if (v > (out[ex.exerciseId] ?: 0.0)) out[ex.exerciseId] = v
            }
        }
    }
    return out
}

private fun previousSets(history: List<Workout>): Map<String, List<SetLog>> {
    val out = mutableMapOf<String, List<SetLog>>()
    history.sortedByDescending { it.startTime }.forEach { w ->
        w.exercises.forEach { ex -> if (ex.exerciseId !in out) Analytics.workingSets(ex).takeIf { it.isNotEmpty() }?.let { out[ex.exerciseId] = it } }
    }
    return out
}

// ----- Barre du haut -----

/** Lit l'horloge ici : seul ce contenu se recompose à chaque tic. */
@Composable
private fun Ticking(clock: androidx.compose.runtime.MutableLongState, content: @Composable (Long) -> Unit) = content(clock.longValue)

@Composable
private fun TopBar(s: Session, now: Long, onMinimize: () -> Unit, onOptions: () -> Unit, onFinish: () -> Unit, onPause: () -> Unit) {
    val done = s.exercises.sumOf { e -> e.sets.count { it.done } }
    val total = s.exercises.sumOf { it.sets.size }.coerceAtLeast(1)
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinimize) { Icon(Icons.Rounded.KeyboardArrowDown, tr("Réduire")) }
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(CircleShape).clickable(onClick = onPause)) {
                    Icon(if (s.pausedAt != null) Icons.Rounded.PlayArrow else Icons.Rounded.Timer, null, Modifier.size(14.dp),
                        tint = if (s.pausedAt != null) Mf.colors.accent else Mf.colors.textSecondary)
                    Spacer(Modifier.width(3.dp))
                    Text(if (s.pausedAt != null) trf("En pause · %1\$s", (duration(s.elapsedSec(now)))) else duration(s.elapsedSec(now)),
                        style = MaterialTheme.typography.bodySmall, color = if (s.pausedAt != null) Mf.colors.accent else Mf.colors.textSecondary)
                }
            }
            IconButton(onClick = onOptions) { Icon(Icons.Rounded.MoreHoriz, tr("Options de la séance")) }
            Text(tr("Terminer"), Modifier.padding(end = 10.dp).clip(CircleShape).background(Mf.colors.strong).clickable(onClick = onFinish)
                .padding(horizontal = 16.dp, vertical = 8.dp), color = Mf.colors.onStrong, style = MaterialTheme.typography.labelLarge)
        }
        val frac by animateFloatAsState(done.toFloat() / total, tween(400), label = "progress")
        Box(Modifier.fillMaxWidth().height(3.dp).background(Mf.colors.chip)) {
            Box(Modifier.fillMaxWidth(frac).height(3.dp).background(WorkoutBlue))
        }
    }
}

// ----- Rangée d'exercices (ExerciseRow) -----

@Composable
private fun ExerciseStrip(s: Session, exercise: (String) -> Exercise?, selected: Int, onSelect: (Int) -> Unit, onAdd: () -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(selected) { if (selected < s.exercises.size) list.animateScrollToItem((selected - 2).coerceAtLeast(0)) }
    LazyRow(state = list, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        val letters = s.exercises.mapNotNull { it.supersetGroup }.distinct().mapIndexed { n, g -> g to ('A' + n) }.toMap()
        itemsIndexed(s.exercises, key = { _, e -> e.id }) { i, ex ->
            // Superset : les vignettes du groupe partagent une capsule bleue, avec sa lettre au début.
            val g = ex.supersetGroup
            val linkedNext = g != null && s.exercises.getOrNull(i + 1)?.supersetGroup == g
            val linkedPrev = g != null && s.exercises.getOrNull(i - 1)?.supersetGroup == g
            val grouped = linkedNext || linkedPrev
            val r = 32.dp
            Row(Modifier.padding(end = if (linkedNext) 0.dp else 8.dp)
                .background(if (grouped) WorkoutBlue.copy(alpha = 0.18f) else Color.Transparent,
                    RoundedCornerShape(topStart = if (linkedPrev) 0.dp else r, bottomStart = if (linkedPrev) 0.dp else r,
                        topEnd = if (linkedNext) 0.dp else r, bottomEnd = if (linkedNext) 0.dp else r))
                .padding(start = if (grouped && !linkedPrev) 8.dp else 0.dp, end = if (grouped) 6.dp else 0.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (grouped && !linkedPrev) Text("${letters[g] ?: 'A'}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WorkoutBlue,
                    modifier = Modifier.padding(end = 4.dp))
                StripItem(i, ex, exercise(ex.exerciseId), i == selected) { onSelect(i) }
            }
        }
        item {
            Box(Modifier.size(52.dp).clip(CircleShape).border(1.5.dp, Mf.colors.divider, CircleShape).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, tr("Ajouter des exercices"), tint = Mf.colors.textSecondary) }
        }
    }
}

@Composable
private fun StripItem(index: Int, ex: SessionExercise, e: Exercise?, selected: Boolean, onClick: () -> Unit) {
    val total = ex.sets.size.coerceAtLeast(1)
    val done = ex.sets.count { it.done }
    val frac by animateFloatAsState(done.toFloat() / total, tween(500), label = "ring")
    val scale by animateFloatAsState(if (selected) 1f else 0.9f, spring(dampingRatio = 0.6f), label = "scale")
    val ring = Mf.colors.chip
    Box(Modifier.size(52.dp).scale(scale).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(52.dp)) {
            drawArc(ring, 0f, 360f, false, style = Stroke(3.dp.toPx()))
            if (frac > 0f) drawArc(WorkoutBlue, -90f, 360f * frac, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        AsyncAsset(e?.thumbnail(), Modifier.size(42.dp).clip(CircleShape), fallback = e?.name ?: "?", sample = 2)
        if (done == ex.sets.size && ex.sets.isNotEmpty()) Box(Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(WorkoutBlue),
            contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
        else Text("${index + 1}", Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(if (selected) Mf.colors.strong else Mf.colors.card),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = if (selected) Mf.colors.onStrong else Mf.colors.text)
    }
}

// ----- Page d'un exercice -----

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExercisePage(
    vm: AppViewModel, settings: Settings, s: Session, index: Int, ex: SessionExercise, e: Exercise?, previous: List<SetLog>?, best: Double?,
    nav: NavHostController, onPlates: (Double) -> Unit, onNote: () -> Unit, onRest: () -> Unit, onReorder: () -> Unit, onTargets: () -> Unit, onToggle: (Int) -> Unit,
) {
    fun edit(f: (SessionExercise) -> SessionExercise) = vm.session { ss -> ss.copy(exercises = ss.exercises.toMutableList().also { it[index] = f(it[index]) }) }
    var more by remember { mutableStateOf(false) }
    /** Séries validées rouvertes pour correction. */
    val expanded = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val inSuperset = ex.supersetGroup != null
    val groupSize = if (inSuperset) s.exercises.count { it.supersetGroup == ex.supersetGroup } else 0

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 160.dp)) {
        Hero(e, settings, index, s.exercises.size, groupSize,
            ex.supersetGroup?.let { g -> s.exercises.mapNotNull { it.supersetGroup }.distinct().indexOf(g).takeIf { it >= 0 }?.let { 'A' + it } }) { nav.navigate("exercise/${ex.exerciseId}") }
        // Actions de l'exercice (workout_exercise_builder)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // les plus utilisés en salle d'abord
            ActionChip(Icons.Rounded.Timer, trf("Repos %1\$s", (restLabel(ex.restSec))), onClick = onRest)
            // échauffements calculés (réglage d'échauffement) depuis la charge de travail
            if (ex.sets.none { it.type == SetType.WARMUP && !it.done }) ActionChip(Icons.Rounded.LocalFireDepartment, tr("Échauffement")) { vm.addWarmUps(index) }
            ActionChip(Icons.Rounded.SwapHoriz, tr("Remplacer")) { nav.navigate("picker/${Picker.SWAP}?target=${index}") }
            // superset, puis triset en reliant aussi l'exercice suivant
            // depuis n'importe quel membre du groupe : on relie le dernier du groupe à l'exercice qui le suit
            var groupEnd = index
            while (inSuperset && s.exercises.getOrNull(groupEnd + 1)?.supersetGroup == ex.supersetGroup) groupEnd++
            if (s.exercises.size > 1 && (!inSuperset || groupEnd < s.exercises.size - 1))
                ActionChip(Icons.Rounded.Link, if (!inSuperset) tr("Superset") else if (groupSize == 2) tr("Triset") else tr("Lier au suivant")) { vm.linkNext(groupEnd) }
            if (inSuperset) ActionChip(Icons.Rounded.LinkOff, tr("Détacher"), active = true) { vm.unlink(index) }
            ActionChip(Icons.Rounded.FitnessCenter, tr("Calculateur")) { onPlates(ex.sets.firstOrNull { !it.done && it.type != SetType.WARMUP }?.let { it.weight ?: it.autoWeight } ?: 0.0) }
            ActionChip(Icons.Rounded.Flag, tr("Objectifs"), onClick = onTargets)
            ActionChip(Icons.Rounded.StickyNote2, tr("Note"), onClick = onNote)
            ActionChip(Icons.Rounded.Info, tr("Info")) { nav.navigate("exercise/${ex.exerciseId}") }
            Box {
                ActionChip(Icons.Rounded.MoreHoriz, tr("Plus")) { more = true }
                DropdownMenu(more, { more = false }, containerColor = Mf.colors.card) {
                    if (s.exercises.size > 1) DropdownMenuItem({ Text(tr("Réorganiser les exercices")) }, leadingIcon = { Icon(Icons.Rounded.SwapVert, null) },
                        onClick = { more = false; onReorder() })
                    if (index > 0) DropdownMenuItem({ Text(tr("Monter")) }, leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, null) },
                        onClick = { more = false; vm.moveExercise(index, index - 1) })
                    if (index < s.exercises.size - 1) DropdownMenuItem({ Text(tr("Descendre")) }, leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null) },
                        onClick = { more = false; vm.moveExercise(index, index + 1) })
                    DropdownMenuItem({ Text(tr("Retirer de la séance"), color = Danger) }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = Danger) },
                        onClick = { more = false; vm.removeExercise(index) })
                }
            }
        }
        if (ex.note.isNotBlank()) Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFC447).copy(alpha = 0.18f)).clickable(onClick = onNote).padding(10.dp)) {
            Icon(Icons.Rounded.StickyNote2, null, Modifier.size(16.dp), tint = Mf.colors.textSecondary)
            Spacer(Modifier.width(8.dp))
            Text(ex.note, style = MaterialTheme.typography.bodySmall)
        }
        TargetLine(ex, settings)

        // Séries dans l'ordre : une série validée se rétracte en ligne compacte (touche = la rouvrir), la suivante est mise en avant
        val indexed = ex.sets.withIndex().toList()
        var workNo = 0
        val labels = indexed.associate { (i, set) -> i to if (set.type == SetType.WARMUP) tr("É") else { workNo++; "${workNo}${set.type.short}" } }
        val prevByIndex = run {
            var k = 0
            indexed.associate { (i, set) -> i to if (set.type == SetType.WARMUP) null else previous?.getOrNull(k++) }
        }
        val todo = indexed.filter { !it.value.done }
        val firstTodo = todo.firstOrNull()?.index
        // après une validation, la série suivante défile au-dessus de la carte du minuteur de repos
        val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
        var currentSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        val restPad = with(androidx.compose.ui.platform.LocalDensity.current) { 230.dp.toPx() }
        val doneCount = ex.sets.count { it.done }
        LaunchedEffect(doneCount) {
            if (doneCount == 0 || firstTodo == null) return@LaunchedEffect
            delay(320)
            bring.bringIntoView(androidx.compose.ui.geometry.Rect(0f, 0f, currentSize.width.toFloat(), currentSize.height + restPad))
        }
        indexed.forEach { (si, set) ->
            androidx.compose.runtime.key(set.id) {
                val compact = set.done && set.id !in expanded
                val tracked = if (si == firstTodo) Modifier.bringIntoViewRequester(bring).onSizeChanged { currentSize = it } else Modifier
                Box(tracked.animateContentSize(spring(dampingRatio = 0.85f, stiffness = 500f))) {
                    if (compact) DoneSetRow(settings, set, labels[si]!!, e?.usesWeight != false,
                        record = best != null && set.type != SetType.WARMUP && (set.weight ?: 0.0) > 0 && (set.reps ?: 0) > 0 &&
                            Strength.e1rm(set.weight!!, set.reps!!, set.rir, e?.upperBody == true, set.partialReps) > best * 1.001,
                        onOpen = { expanded.add(set.id) }, onToggle = { onToggle(si) })
                    else SetCard(settings, set, labels[si]!!, prevByIndex[si], e?.usesWeight != false, current = si == firstTodo,
                        showTarget = set.target != null && set.target != ex.sets.getOrNull(si - 1)?.target,
                        onChange = { ns ->
                            // charge seule modifiée : répétitions recalculées en direct
                            if (ns.weight != set.weight && ns.copy(weight = set.weight) == set) vm.setWeight(index, si, ns.weight)
                            else edit { it.copy(sets = it.sets.toMutableList().also { l -> l[si] = ns }) }
                        },
                        onToggle = { expanded.remove(set.id); onToggle(si) }, onPlates = onPlates, onNudge = { up -> vm.nudgeWeight(index, si, up) },
                        onType = { t -> edit { it.copy(sets = it.sets.toMutableList().also { l -> l[si] = set.copy(type = t) }) } },
                        onDelete = { edit { it.copy(sets = it.sets.filterIndexed { j, _ -> j != si }) } })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(tr("Ajouter une série"), Modifier.weight(1f), strong = false, icon = Icons.Rounded.Add) {
                edit { cur ->
                    val last = cur.sets.lastOrNull { it.type != SetType.WARMUP }
                    cur.copy(sets = cur.sets + SetLog(UUID.randomUUID().toString(), last?.type ?: SetType.STANDARD, weight = last?.weight ?: last?.autoWeight,
                        reps = last?.reps ?: last?.autoReps, rir = last?.rir ?: last?.target?.rir, target = last?.target))
                }
            }
        }
        if (todo.isEmpty() && index < s.exercises.size - 1) {
            val next = s.exercises[index + 1]
            Text(tr("Exercice terminé ✓"), Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium, color = WorkoutBlue)
            Text(trf("Ensuite : %1\$s", (vm.db.value?.exercise(next.exerciseId)?.name ?: "")), Modifier.fillMaxWidth().padding(4.dp), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
    }
}

/** En-tête : photo de l'exercice (angles a/b/c, en touchant), dégradé, nom et muscles. */
@Composable
private fun Hero(e: Exercise?, settings: Settings, index: Int, count: Int, groupSize: Int, supersetLetter: Char? = null, onInfo: () -> Unit) {
    val angles = e?.images.orEmpty()
    var angle by remember(e?.id) { mutableIntStateOf(0) }
    val path = angles.getOrNull(angle)?.let { "ex/${e!!.id}_${it}.webp" } ?: e?.thumbnail()
    val heroStyle = app.superhealth.ui.theme.StyleLab.hero()
    if (heroStyle == app.superhealth.ui.theme.StyleLab.Hero.NONE) {
        // labo de style : en-tête sans photo, vignette ronde et nom
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onInfo).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AsyncAsset(e?.thumbnail(), Modifier.size(56.dp).clip(CircleShape).background(Mf.colors.chip), fallback = e?.name ?: "?", sample = 2)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e?.name ?: tr("Exercice inconnu"), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (e != null) Text(e.muscleGroups.joinToString(" · ") { it.label.tr() }, color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${index + 1} / ${count}", style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
                if (groupSize > 0) Text(app.superhealth.training.domain.Supersets.label(groupSize).tr() + (supersetLetter?.let { " $it" } ?: ""),
                    Modifier.padding(top = 4.dp).clip(CircleShape).background(WorkoutBlue).padding(horizontal = 8.dp, vertical = 2.dp),
                    color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        return
    }
    val heroHeight = if (heroStyle == app.superhealth.ui.theme.StyleLab.Hero.COMPACT) 150.dp else 230.dp
    Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(heroHeight).clip(RoundedCornerShape(24.dp))
        .background(Mf.colors.chip)
        .clickable { if (angles.size > 1) angle = (angle + 1) % angles.size else onInfo() }) {
        if (path != null && angles.isNotEmpty()) AsyncAsset(path, Modifier.fillMaxSize(), fallback = e?.name ?: "?")
        else if (e != null) {
            // Pas de photo dans l'APK : silhouette des muscles travaillés (principaux en plein, secondaires atténués)
            val intensity = e.secondaryMuscleGroups.associate { it.key to 0.45f } + e.muscleGroups.associate { it.key to 1f }
            app.superhealth.training.ui.components.BodyMap(intensity, settings.sex, Modifier.fillMaxSize().padding(top = 16.dp, bottom = 70.dp))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))))
        Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${index + 1} / ${count}", Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color.White, style = MaterialTheme.typography.labelMedium)
            if (groupSize > 0) Text(app.superhealth.training.domain.Supersets.label(groupSize).tr() + (supersetLetter?.let { " $it" } ?: ""), Modifier.clip(CircleShape).background(WorkoutBlue).padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        if (angles.size > 1) Row(Modifier.align(Alignment.TopEnd).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            angles.indices.forEach { Box(Modifier.size(if (it == angle) 8.dp else 6.dp).clip(CircleShape).background(Color.White.copy(alpha = if (it == angle) 1f else 0.5f))) }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(e?.name ?: tr("Exercice inconnu"), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (e != null) Text(e.muscleGroups.joinToString(" · ") { it.label.tr() }, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.clip(CircleShape).background(if (active) WorkoutBlue.copy(alpha = 0.14f) else Mf.colors.card).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = if (active) WorkoutBlue else Mf.colors.text)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) WorkoutBlue else Mf.colors.text, maxLines = 1)
    }
}

/** Ligne des cibles : nombre de séries, plage, RIR, repos ; badge de progression intelligente. */
@Composable
private fun TargetLine(ex: SessionExercise, settings: Settings) {
    val work = ex.sets.filter { it.type != SetType.WARMUP }
    val t = work.firstNotNullOfOrNull { it.target }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(listOfNotNull(
            trf("%1\$s série%2\$s", work.size, (if (work.size > 1) "s" else "")),
            t?.let { trf("%1\$s–%2\$s reps", it.minReps, it.maxReps) }, t?.let { trf("RIR %1\$s", it.rir) },
        ).joinToString(" · "), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (work.any { it.autoWeight != null }) Row(Modifier.clip(CircleShape).background(WorkoutBlue.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(13.dp), tint = WorkoutBlue)
            Spacer(Modifier.width(4.dp))
            Text(tr("Progression"), style = MaterialTheme.typography.labelSmall, color = WorkoutBlue)
        }
    }
}

/** Carte d'une série : numéro, charge, répétitions, RIR, précédent ; la série en cours est mise en avant. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetCard(
    settings: Settings, set: SetLog, label: String, prev: SetLog?, usesWeight: Boolean, current: Boolean, showTarget: Boolean = false,
    onChange: (SetLog) -> Unit, onToggle: () -> Unit, onPlates: (Double) -> Unit, onNudge: (Boolean) -> Unit, onType: (SetType) -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var partials by remember { mutableStateOf(set.partialReps > 0) }
    val warm = set.type == SetType.WARMUP
    val look = app.superhealth.ui.theme.StyleLab.sets()
    val rows = look == app.superhealth.ui.theme.StyleLab.Sets.ROWS
    // contrastées : la série en cours est pleine, dans la couleur de l'entraînement
    val bold = look == app.superhealth.ui.theme.StyleLab.Sets.BOLD && current
    val border by animateColorAsState(if (current && !rows && !bold) WorkoutBlue else Color.Transparent, label = "border")
    val alpha by animateFloatAsState(if (current || set.done) 1f else 0.6f, label = "alpha")
    val shape = RoundedCornerShape(if (rows) 14.dp else 18.dp)
    val bg = when { bold -> WorkoutBlue; rows && current -> WorkoutBlue.container(); rows -> Color.Transparent; else -> Mf.colors.card }
    Column(Modifier.padding(horizontal = 12.dp, vertical = if (rows) 1.dp else 4.dp).fillMaxWidth().clip(shape).background(bg)
        .border(if (current && !rows && !bold) 2.dp else 0.dp, border, shape)
        .padding(horizontal = 10.dp, vertical = if (rows) 6.dp else if (current) 14.dp else 10.dp)
        .graphicsLayer { this.alpha = alpha }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(Modifier.size(34.dp).clip(CircleShape).background(if (bold) Color.White.copy(alpha = 0.22f) else if (warm) Mf.colors.chip else if (current) WorkoutBlue.copy(alpha = 0.14f) else Mf.colors.chip)
                    .combinedClickable(onClick = { menu = true }, onLongClick = { menu = true }), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = if (bold) Color.White else if (warm) Mf.colors.textSecondary else if (current) WorkoutBlue else Mf.colors.text)
                }
                DropdownMenu(menu, { menu = false }, containerColor = Mf.colors.card) {
                    SetType.entries.forEach { t -> DropdownMenuItem({ Text(t.label.tr()) }, onClick = { menu = false; onType(t) }) }
                    if (!warm) DropdownMenuItem({ Text(if (partials) tr("Retirer les partielles") else tr("Répétitions partielles")) }, onClick = {
                        menu = false; partials = !partials; if (!partials) onChange(set.copy(partialReps = 0))
                    })
                    DropdownMenuItem({ Text(tr("Supprimer la série"), color = Danger) }, onClick = { menu = false; onDelete() })
                }
            }
            Spacer(Modifier.width(8.dp))
            if (usesWeight) BigField(set.weight?.let { clean(settings.w(it)) } ?: "", set.autoWeight?.let { clean(settings.w(it)) }, settings.unit.label.tr(),
                Modifier.weight(1.2f), onLongPress = { onPlates(set.weight ?: set.autoWeight ?: 0.0) }, next = true) { onChange(set.copy(weight = it.toNum()?.let { v -> settings.toKg(v) })) }
            else Text(tr("Poids du corps"), Modifier.weight(1.2f), textAlign = TextAlign.Center, color = Mf.colors.textTertiary, style = MaterialTheme.typography.bodySmall)
            BigField(set.reps?.toString() ?: "", set.autoReps?.toString() ?: set.target?.let { "${it.minReps}-${it.maxReps}" }, "reps", Modifier.weight(1f)) {
                onChange(set.copy(reps = it.toNum()?.roundToInt()))
            }
            if (!warm) RirField(set.rir, set.target?.rir, Modifier.weight(0.75f)) { onChange(set.copy(rir = it)) }
            val scale by animateFloatAsState(if (set.done) 1f else 0.92f, spring(dampingRatio = 0.45f), label = "check")
            Box(Modifier.padding(start = 6.dp).size(44.dp).scale(scale).clip(CircleShape)
                .background(if (bold) Color.White else if (set.done) WorkoutBlue else if (current) WorkoutBlue.container(1.6f) else Mf.colors.chip).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, tr("Valider"), tint = if (set.done && !bold) Color.White else if (current) WorkoutBlue else Mf.colors.textTertiary, modifier = Modifier.size(24.dp))
            }
        }
        // série en cours : ajuster sans clavier (charge au pas du matériel, répétitions une à une)
        if (current && !set.done) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(42.dp))
            if (usesWeight) Row(Modifier.weight(1.2f), horizontalArrangement = Arrangement.SpaceEvenly) {
                NudgeButton("−", bold) { onNudge(false) }; NudgeButton("+", bold) { onNudge(true) }
            } else Spacer(Modifier.weight(1.2f))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                val reps = set.reps ?: set.autoReps ?: set.target?.minReps ?: 0
                NudgeButton("−", bold) { onChange(set.copy(reps = (reps - 1).coerceAtLeast(0))) }
                NudgeButton("+", bold) { onChange(set.copy(reps = reps + 1)) }
            }
            if (!warm) Spacer(Modifier.weight(0.75f))
            Spacer(Modifier.width(50.dp))
        }
        // répétitions partielles (après l'échec, amplitude réduite) : comptées à part
        if ((partials || set.rir == 0) && !warm) Row(Modifier.padding(start = 42.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Partielles"), style = MaterialTheme.typography.labelLarge, color = WorkoutBlue, modifier = Modifier.weight(1f))
            RestButtonSmall("−") { onChange(set.copy(partialReps = (set.partialReps - 1).coerceAtLeast(0))) }
            Text("${set.partialReps}", Modifier.width(36.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            // des partielles = série menée à l'échec : RIR 0
            RestButtonSmall("+") { onChange(set.copy(partialReps = set.partialReps + 1, rir = 0)) }
        }
        val t = set.target
        if (showTarget && t != null && !warm) Text(trf("Objectif %1\$s–%2\$s reps · RIR %3\$s · repos %4\$s", t.minReps, t.maxReps, t.rir, restLabel(t.restSec)),
            Modifier.padding(start = 42.dp, top = 6.dp), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
        if (prev != null || set.type != SetType.STANDARD && !warm) Row(Modifier.padding(start = 42.dp, top = 4.dp)) {
            if (set.type != SetType.STANDARD && !warm) Text(set.type.label.tr(), style = MaterialTheme.typography.labelSmall, color = WorkoutBlue,
                modifier = Modifier.padding(end = 8.dp))
            // toucher la série précédente recopie sa charge et ses répétitions
            if (prev != null) Text(trf("Précédent %1\$s × %2\$s%3\$s", (clean(settings.w(prev.weight ?: 0.0))), (prev.reps ?: 0), (prev.rir?.let { trf(" · RIR %1\$s", it) } ?: "")) +
                (if (prev.partialReps > 0) trf(" + %1\$s part.", prev.partialReps) else ""),
                style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary,
                modifier = Modifier.clip(CircleShape).clickable(enabled = !set.done) { onChange(set.copy(weight = prev.weight ?: set.weight, reps = prev.reps ?: set.reps)) }
                    .padding(horizontal = 4.dp, vertical = 2.dp))
        }
    }
}

/** Série validée, rétractée : coche pleine, résultat ; touche = rouvrir pour corriger, coche = dévalider. */
@Composable
private fun DoneSetRow(settings: Settings, set: SetLog, label: String, usesWeight: Boolean, record: Boolean = false, onOpen: () -> Unit, onToggle: () -> Unit) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen)
        .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(34.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
        Spacer(Modifier.width(8.dp))
        Text(listOfNotNull(
            if (usesWeight) "${clean(settings.w(set.weight ?: 0.0))} ${settings.unit.label.tr()}" else null,
            trf("%1\$s reps", (set.reps ?: 0)) + (if (set.partialReps > 0) trf(" + %1\$s part.", set.partialReps) else ""), set.rir?.let { trf("RIR %1\$s", it) },
        ).joinToString("  ·  "), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
        if (record) {
            // Nouveau record (1RM estimé) : pastille dorée qui apparaît en rebondissant.
            val pop = remember { androidx.compose.animation.core.Animatable(0.4f) }
            LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 300f)) }
            Row(Modifier.scale(pop.value).padding(end = 8.dp).clip(CircleShape).background(Color(0xFFFFB300)).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EmojiEvents, null, Modifier.size(14.dp), tint = Color.White)
                Spacer(Modifier.width(4.dp))
                Text(tr("Record"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Box(Modifier.size(30.dp).clip(CircleShape).background(WorkoutBlue).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, tr("Dévalider"), tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun NudgeButton(t: String, onBold: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(width = 40.dp, height = 30.dp).clip(CircleShape).background(if (onBold) Color.White.copy(alpha = 0.22f) else WorkoutBlue.copy(alpha = 0.12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Text(t, style = MaterialTheme.typography.titleMedium, color = if (onBold) Color.White else WorkoutBlue, fontWeight = FontWeight.SemiBold) }
}

/** Champ numérique large ; la valeur de la progression intelligente sert d'indication. [next] : la touche du clavier passe au champ suivant. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BigField(value: String, hint: String?, unit: String, modifier: Modifier, onLongPress: (() -> Unit)? = null, next: Boolean = false, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    Column(modifier.padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Mf.colors.background)
            .let { if (onLongPress != null) it.combinedClickable(onClick = {}, onLongClick = onLongPress) else it }, contentAlignment = Alignment.Center) {
            if (text.isEmpty() && hint != null) Text(hint, color = Mf.colors.textTertiary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            BasicTextField(text, { text = it; onChange(it) }, Modifier.fillMaxWidth().padding(horizontal = 4.dp), singleLine = true,
                textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Mf.colors.text, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Mf.colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal,
                    imeAction = if (next) androidx.compose.ui.text.input.ImeAction.Next else androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) }, onDone = { focus.clearFocus() }))
        }
        Text(unit, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary)
    }
}

/** RIR : touche → échelle de MacroFactor (« No reps in reserve » … « Many reps in reserve »). */
@Composable
private fun RirField(rir: Int?, hint: Int?, modifier: Modifier, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Mf.colors.background).clickable { open = true },
            contentAlignment = Alignment.Center) {
            Text(rir?.toString() ?: hint?.toString() ?: "–", fontSize = 18.sp, fontWeight = if (rir != null) FontWeight.Bold else FontWeight.SemiBold,
                color = if (rir != null) Mf.colors.text else Mf.colors.textTertiary)
        }
        Text("RIR", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary)
    }
    if (open) RirSheet(rir ?: hint, onDismiss = { open = false }) { onPick(it); open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RirSheet(current: Int?, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Mf.colors.background) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text(tr("Répétitions en réserve"), style = MaterialTheme.typography.titleLarge)
            Text(tr("Combien de répétitions auriez-vous encore pu faire ?"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary,
                modifier = Modifier.padding(bottom = 12.dp))
            val rows = listOf(
                0 to "Aucune : échec", 1 to "Une de plus, au maximum", 2 to "Deux de plus", 3 to "Trois de plus",
                4 to "Quatre de plus", 5 to "Cinq ou plus : facile",
            )
            rows.forEach { (v, label) ->
                val sel = v == current
                val heat = app.superhealth.ui.theme.Base.Red.copy(alpha = (0.85f - v * 0.15f).coerceAtLeast(0.1f))
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (sel) WorkoutBlue.copy(alpha = 0.12f) else Mf.colors.card).clickable { onPick(v) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(heat), contentAlignment = Alignment.Center) {
                        Text(if (v == 5) "5+" else "${v}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(tr(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (sel) Icon(Icons.Rounded.Check, null, tint = WorkoutBlue)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(tr("Plus dur"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary, modifier = Modifier.weight(1f))
                Text(tr("Plus facile"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary)
            }
        }
    }
}

/** Dernière page : ajouter des exercices ou terminer. */
@Composable
private fun EndPage(s: Session, onAdd: () -> Unit, onFinish: () -> Unit) {
    val left = s.exercises.sumOf { ex -> ex.sets.count { !it.done && it.type != SetType.WARMUP } }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val allDone = left == 0 && s.exercises.isNotEmpty()
        Icon(if (allDone) Icons.Rounded.CheckCircle else Icons.Rounded.Flag, null, Modifier.size(64.dp),
            tint = if (allDone) app.superhealth.ui.theme.Base.Green else Mf.colors.textTertiary)
        Spacer(Modifier.height(16.dp))
        // séance encore vide : ajouter un exercice est l'action attendue, pas terminer
        val empty = s.exercises.isEmpty()
        Text(when { allDone -> tr("Tout est fait !"); empty -> tr("Séance vide"); else -> tr("Fin de la séance") },
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(when { left > 0 -> trf("%1\$s série(s) restante(s)", left); empty -> tr("Ajoutez un premier exercice."); else -> tr("Ajoutez un exercice ou terminez.") },
            color = Mf.colors.textSecondary)
        Spacer(Modifier.height(20.dp))
        if (empty) {
            PillButton(tr("Ajouter des exercices"), Modifier.fillMaxWidth(), icon = Icons.Rounded.Add, onClick = onAdd)
            Spacer(Modifier.height(10.dp))
            PillButton(tr("Terminer la séance"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.Flag, onClick = onFinish)
        } else {
            PillButton(tr("Terminer la séance"), Modifier.fillMaxWidth(), icon = Icons.Rounded.Flag, onClick = onFinish)
            Spacer(Modifier.height(10.dp))
            PillButton(tr("Ajouter des exercices"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.Add, onClick = onAdd)
        }
    }
}

// ----- Minuteur de repos -----

@Composable
private fun RestCard(s: Session, endsAt: Long, now: Long, settings: Settings, exercise: (String) -> Exercise?, vm: AppViewModel, onCollapse: () -> Unit) {
    val left = ((endsAt - now) / 1000).coerceAtLeast(0)
    val frac = if (s.restDuration > 0) left.toFloat() / s.restDuration else 0f
    val anim by animateFloatAsState(frac, tween(500), label = "rest")
    // prochaine série à faire (à partir de l'exercice affiché)
    val next = (s.exercises.indices.drop(s.current) + s.exercises.indices.take(s.current)).firstNotNullOfOrNull { i ->
        s.exercises[i].sets.firstOrNull { !it.done }?.let { i to it }
    }
    // carte flottante : fond surélevé, minuteur en grands chiffres, anneau d'accent
    val bg = if (Mf.colors.dark) Mf.colors.elevated else Mf.colors.card
    Column(Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding()
        .shadow(20.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.2f))
        .clip(RoundedCornerShape(28.dp)).background(bg).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val accent = Mf.colors.accent
            // toucher le minuteur : réduire la carte en pastille
            Row(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable(onClick = onCollapse), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                val track = Mf.colors.chip
                Canvas(Modifier.size(64.dp)) {
                    drawArc(track, 0f, 360f, false, style = Stroke(5.dp.toPx()))
                    drawArc(accent, -90f, 360f * anim, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                }
                Icon(Icons.Rounded.Timer, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Repos"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
                Text(duration(left), style = MaterialTheme.typography.displaySmall, fontSize = 34.sp, color = Mf.colors.text)
            }
            Icon(Icons.Rounded.KeyboardArrowDown, tr("Réduire"), tint = Mf.colors.textTertiary, modifier = Modifier.padding(end = 4.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Row {
                    RestButton("−15") { vm.adjustRest(-15) }
                    RestButton("+15") { vm.adjustRest(15) }
                }
                Spacer(Modifier.height(6.dp))
                RestButton(tr("Passer")) { vm.skipRest() }
            }
        }
        next?.let { (i, set) ->
            val e = exercise(s.exercises[i].exerciseId)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (Mf.colors.dark) Color(0xFF3A3A3C) else Mf.colors.chip).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AsyncAsset(e?.thumbnail(), Modifier.size(34.dp).clip(CircleShape), fallback = e?.name ?: "?", sample = 2)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Ensuite"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                    Text(e?.name ?: "", style = MaterialTheme.typography.bodyMedium, color = Mf.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val wgt = set.weight ?: set.autoWeight
                val reps = set.reps ?: set.autoReps
                Text(listOfNotNull(wgt?.let { "${clean(settings.w(it))} ${settings.unit.label.tr()}" }, reps?.let { trf("× %1\$s", it) }).joinToString(" "),
                    style = MaterialTheme.typography.titleSmall, color = Mf.colors.text)
            }
        }
    }
}

/** Repos réduit : anneau et temps restant, en bas à droite ; toucher le rouvre. */
@Composable
private fun RestPill(s: Session, endsAt: Long, now: Long, onExpand: () -> Unit) {
    val left = ((endsAt - now) / 1000).coerceAtLeast(0)
    val frac = if (s.restDuration > 0) left.toFloat() / s.restDuration else 0f
    val anim by animateFloatAsState(frac, tween(500), label = "restpill")
    val accent = Mf.colors.accent
    val track = Mf.colors.chip
    Row(Modifier.padding(16.dp).navigationBarsPadding()
        .shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.2f))
        .clip(CircleShape).background(if (Mf.colors.dark) Mf.colors.elevated else Mf.colors.card).clickable(onClick = onExpand)
        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(30.dp)) {
            drawArc(track, 0f, 360f, false, style = Stroke(3.5.dp.toPx()))
            drawArc(accent, -90f, 360f * anim, false, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
        }
        Spacer(Modifier.width(10.dp))
        Text(duration(left), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = Mf.colors.text)
    }
}

@Composable
private fun RestButtonSmall(t: String, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(Mf.colors.chip).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(t, style = MaterialTheme.typography.titleMedium, color = Mf.colors.text)
    }
}

@Composable
private fun RestButton(t: String, onClick: () -> Unit) {
    Text(t, Modifier.padding(start = 6.dp).clip(CircleShape).background(Mf.colors.accent.copy(alpha = 0.16f)).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 7.dp), color = Mf.colors.accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
}

// ----- Feuilles -----

/** « Workout Options » : pause, réduire, terminer, réglages, salle, abandonner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutOptionsSheet(
    s: Session, onDismiss: () -> Unit, onPause: () -> Unit, onMinimize: () -> Unit, onFinish: () -> Unit,
    onSettings: () -> Unit, onNote: () -> Unit, onReorder: () -> Unit, onGym: () -> Unit, onDiscard: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Mf.colors.background) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text(tr("Options de la séance"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Mf.colors.card)) {
                OptionRow(if (s.pausedAt != null) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (s.pausedAt != null) tr("Reprendre la séance") else tr("Mettre en pause"), onClick = onPause)
                OptionRow(Icons.Rounded.KeyboardArrowDown, tr("Réduire la séance"), onClick = onMinimize)
                OptionRow(Icons.Rounded.Flag, tr("Terminer la séance"), onClick = onFinish)
                OptionRow(Icons.Rounded.StickyNote2, if (s.note.isBlank()) tr("Note de séance") else tr("Modifier la note de séance"), onClick = onNote)
                if (s.exercises.size > 1) OptionRow(Icons.Rounded.SwapVert, tr("Réorganiser les exercices"), onClick = onReorder)
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Mf.colors.card)) {
                OptionRow(Icons.Rounded.Tune, tr("Réglages d'entraînement"), onClick = onSettings)
                OptionRow(Icons.Rounded.FitnessCenter, tr("Réglages de la salle"), onClick = onGym)
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Mf.colors.card)) {
                OptionRow(Icons.AutoMirrored.Rounded.Logout, tr("Abandonner la séance"), danger = true, onClick = onDiscard)
            }
        }
    }
}

@Composable
private fun OptionRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (danger) Danger else Mf.colors.text)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (danger) Danger else Mf.colors.text)
    }
}

/** Objectifs de l'exercice pour la suite de la séance (« Targets ») : séries, plage de répétitions, RIR, repos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetsSheet(ex: SessionExercise, onDismiss: () -> Unit, onSave: (SessionExercise) -> Unit) {
    val work = ex.sets.filter { it.type != SetType.WARMUP }
    val t0 = work.firstNotNullOfOrNull { it.target } ?: app.superhealth.training.domain.TargetSet(restSec = ex.restSec)
    var sets by remember { mutableIntStateOf(work.size.coerceAtLeast(1)) }
    var min by remember { mutableIntStateOf(t0.minReps) }
    var max by remember { mutableIntStateOf(t0.maxReps) }
    var rir by remember { mutableIntStateOf(t0.rir) }
    var rest by remember { mutableIntStateOf(ex.restSec) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Mf.colors.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Text(tr("Objectifs"), style = MaterialTheme.typography.titleLarge)
            Text(tr("S'appliquent aux séries pas encore faites de cette séance."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            PlanStepper(tr("Séries"), sets, work.count { it.done }.coerceAtLeast(1), 10) { sets = it }
            PlanStepper(tr("Répétitions min."), min, 1, 50) { min = it; if (max < it) max = it }
            PlanStepper(tr("Répétitions max."), max, 1, 60) { max = it.coerceAtLeast(min) }
            PlanStepper(tr("RIR (répétitions en réserve)"), rir, 0, 5) { rir = it }
            PlanStepper(tr("Repos"), rest, 0, 600, step = 15, label = { restLabel(it) }) { rest = it }
            PillButton(tr("Appliquer"), Modifier.fillMaxWidth().padding(top = 16.dp)) {
                val t = t0.copy(minReps = min, maxReps = max.coerceAtLeast(min), rir = rir, restSec = rest)
                val warm = ex.sets.filter { it.type == SetType.WARMUP }
                var kept = work.map { if (it.done) it else it.copy(target = t, rir = if (it.rir == null || it.rir == t0.rir) rir else it.rir) }
                // séries en plus (copie de la dernière) ou en moins (seulement parmi celles pas faites)
                while (kept.size < sets) { val last = kept.lastOrNull(); kept = kept + SetLog(UUID.randomUUID().toString(), last?.type ?: SetType.STANDARD,
                    weight = last?.weight ?: last?.autoWeight, reps = last?.reps ?: last?.autoReps, rir = rir, target = t) }
                while (kept.size > sets) { val k = kept.indexOfLast { !it.done }; if (k < 0) break; kept = kept.filterIndexed { j, _ -> j != k } }
                onSave(ex.copy(sets = warm + kept, restSec = rest))
            }
        }
    }
}

/** Durée du repos de l'exercice (edit_rest_timer_sheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestSheet(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Mf.colors.background) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text(tr("Minuteur de repos"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            val values = listOf(30, 45, 60, 90, 120, 150, 180, 240, 300)
            values.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { r ->
                        Text(restLabel(r), Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (r == current) Mf.colors.strong else Mf.colors.card)
                            .clickable { onPick(r) }.padding(vertical = 14.dp), textAlign = TextAlign.Center,
                            color = if (r == current) Mf.colors.onStrong else Mf.colors.text, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/**
 * Calculateurs de l'exercice : disques (Plate Calculator) et série (Set Calculator : charge pour des répétitions
 * et un RIR donnés, à partir du 1RM estimé de la séance ou de l'historique — modèle de force de la note 03).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculatorSheet(vm: AppViewModel, settings: Settings, weight: Double, ex: SessionExercise?, e: Exercise?, history: List<Workout>, onDismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.card) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding()) {
            app.superhealth.training.ui.components.ChipChoice(listOf(0, 1), tab, { if (it == 0) tr("Disques") else tr("Série") }) { tab = it }
            Spacer(Modifier.height(8.dp))
            if (tab == 0) PlateCalc(vm, settings, weight) else SetCalc(settings, ex, e, history)
        }
    }
}

@Composable
private fun SetCalc(settings: Settings, ex: SessionExercise?, e: Exercise?, history: List<Workout>) {
    val upper = e?.upperBody ?: true
    // 1RM : séries faites dans cette séance, sinon dernière séance de l'exercice
    val done = ex?.sets?.filter { it.done && it.type != SetType.WARMUP && it.weight != null && it.reps != null }.orEmpty()
    val past = done.ifEmpty {
        history.sortedByDescending { it.startTime }.firstNotNullOfOrNull { w -> w.exercises.firstOrNull { it.exerciseId == ex?.exerciseId } }
            ?.let { Analytics.workingSets(it) }.orEmpty()
    }
    val oneRm = app.superhealth.training.domain.Progression.currentOneRm(
        past.filter { it.weight != null && it.reps != null }.map { app.superhealth.training.domain.PastSet(it.weight!!, it.reps!!, it.rir, 0, it.partialReps) }, upper)
    var reps by remember { mutableStateOf("8") }
    var rir by remember { mutableStateOf("2") }
    Text(tr("Calculateur de série"), style = MaterialTheme.typography.titleLarge)
    if (oneRm == null) {
        Text(tr("Faites une série de cet exercice pour estimer votre 1RM."), color = Mf.colors.textSecondary, modifier = Modifier.padding(vertical = 16.dp))
        return
    }
    Text(trf("1RM estimé %1\$s %2\$s", (clean(settings.w(Math.round(oneRm * 2) / 2.0))), settings.unit.label), color = Mf.colors.textSecondary)
    Row {
        TextInput(tr("Répétitions"), reps, { reps = it }, Modifier.weight(1f).padding(end = 6.dp))
        TextInput("RIR", rir, { rir = it }, Modifier.weight(1f).padding(start = 6.dp))
    }
    val r = reps.toNum()?.roundToInt()?.coerceIn(1, 50)
    val x = rir.toNum()?.roundToInt()?.coerceIn(0, 10) ?: 0
    if (r != null) {
        val w = app.superhealth.training.domain.Strength.weightFor(oneRm, (r + x).toDouble(), upper)
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(16.dp)).background(Mf.colors.background).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Charge conseillée"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
                Text("${clean(settings.w(Math.round(w * 2) / 2.0))} ${settings.unit.label}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Text(trf("%1\$s %% du 1RM", ((w / oneRm * 100).roundToInt())), style = MaterialTheme.typography.titleMedium, color = WorkoutBlue)
        }
    }
    // repères : répétitions jusqu'à l'échec → % du 1RM (courbe de force)
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(1, 3, 5, 8, 10, 12, 15).forEach { n ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${n}", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary)
                Text("${(app.superhealth.training.domain.Strength.pct(n.toDouble(), upper) * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Calculateur de disques (Plate Calculator : disques par côté pour la charge, barre de la salle). */
@Composable
private fun PlateCalc(vm: AppViewModel, settings: Settings, weight: Double) {
    val gym = vm.activeGym()
    val bar = gym?.barWeight ?: 20.0
    val plates = gym?.plates ?: listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
    var target by remember { mutableStateOf(clean(settings.w(if (weight > 0) weight else bar))) }
    val kg = target.toNum()?.let { settings.toKg(it) } ?: 0.0
    val side = Plates.perSide(kg, bar, plates)
    run {
        Column {
            Text(tr("Calculateur de disques"), style = MaterialTheme.typography.titleLarge)
            Text(trf("Barre %1\$s %2\$s", (clean(settings.w(bar))), settings.unit.label.tr()), color = Mf.colors.textSecondary)
            TextInput(trf("Charge totale (%1\$s)", settings.unit.label.tr()), target, { target = it })
            Spacer(Modifier.height(12.dp))
            if (side == null) Text(tr("Charge impossible exactement avec les disques de cette salle."), color = Danger)
            else {
                Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(60.dp).height(14.dp).background(Color(0xFF9E9E9E)))
                    Box(Modifier.width(8.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF757575)))
                    side.forEach { p ->
                        val h = (44 + p * 2.6).coerceAtMost(116.0)
                        Box(Modifier.padding(horizontal = 1.dp).width((10 + p / 3).dp).height(h.dp).clip(RoundedCornerShape(4.dp)).background(plateColor(p)))
                    }
                    Box(Modifier.width(30.dp).height(14.dp).background(Color(0xFF9E9E9E)))
                }
                Spacer(Modifier.height(10.dp))
                Text(if (side.isEmpty()) tr("Barre seule") else tr("Par côté : ") + side.joinToString(" + ") { clean(settings.w(it)) }, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Couleurs de disques olympiques (convention IWF). */
private fun plateColor(p: Double) = when {
    p >= 25 -> Color(0xFFE53935); p >= 20 -> Color(0xFF1E88E5); p >= 15 -> Color(0xFFFDD835); p >= 10 -> Color(0xFF43A047)
    p >= 5 -> Color(0xFFEEEEEE); p >= 2.5 -> Color(0xFF424242); else -> Color(0xFFBDBDBD)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSheet(initial: String, title: String = tr("Note de l'exercice"), onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Mf.colors.card) {
        Column(Modifier.padding(20.dp).navigationBarsPadding().imePadding()) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(tr("Enregistrée dans l'historique."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            TextInput(tr("Note"), t, { t = it })
            PillButton(tr("Enregistrer"), Modifier.fillMaxWidth().padding(top = 10.dp)) { onSave(t) }
        }
    }
}

