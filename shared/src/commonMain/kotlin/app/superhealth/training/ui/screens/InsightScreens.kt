package app.superhealth.training.ui.screens

import app.superhealth.platform.*

import app.superhealth.training.ui.tr
import app.superhealth.training.ui.trf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.training.domain.Analytics
import app.superhealth.training.domain.ExerciseDb
import app.superhealth.training.domain.MuscleGroup
import app.superhealth.training.domain.SetType
import app.superhealth.training.domain.Settings
import app.superhealth.training.domain.Workout
import app.superhealth.training.ui.AppViewModel
import app.superhealth.training.ui.clean
import app.superhealth.ui.charts.BarChart
import app.superhealth.training.ui.components.BodyMap
import app.superhealth.training.ui.components.ChipChoice
import app.superhealth.training.ui.components.ExerciseThumb
import app.superhealth.training.ui.components.GroupCard
import app.superhealth.training.ui.components.MfCard
import app.superhealth.training.ui.components.MuscleThumb
import app.superhealth.training.ui.components.ProgressBar
import app.superhealth.training.ui.components.RowDivider
import app.superhealth.training.ui.components.ScreenTitle
import app.superhealth.training.ui.components.SectionLabel
import app.superhealth.training.ui.components.StatCell
import app.superhealth.training.ui.durationWords
import app.superhealth.training.ui.localDate
import app.superhealth.training.ui.longDate
import app.superhealth.training.ui.shortDate
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.WorkoutBlue
import app.superhealth.training.ui.volume
import app.superhealth.training.ui.weight
import app.superhealth.training.ui.estimate
import app.superhealth.platform.LocalDate

internal fun intensity(sets: Map<MuscleGroup, Double>, scale: Double) = sets.entries.associate { it.key.key to (it.value / scale).toFloat().coerceIn(0f, 1f) }

/** Tableau de bord (DashboardView + sections Train : habitudes, records, muscles, exercices). */
@Composable
fun DashboardScreen(vm: AppViewModel, nav: NavHostController) {
    val st by vm.state.collectAsState()
    val db = vm.db.collectAsState().value ?: return
    val today = LocalDate.now()
    val weekStart = Analytics.weekStart(today)
    val thisWeek = st.history.filter { !localDate(it.startTime).isBefore(weekStart) }
    val sets = Analytics.setsByMuscle(thisWeek, db)
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        ScreenTitle(tr("Tableau de bord"), onBack = { nav.popBackStack() })
        Column(Modifier.padding(horizontal = 16.dp)) {
            MfCard {
                Text(tr("Cette semaine"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row {
                    StatCell("${thisWeek.size}", tr("Séances"), Modifier.weight(1f))
                    StatCell("${thisWeek.sumOf { w -> w.exercises.sumOf { Analytics.workingSets(it).size } }}", tr("Séries"), Modifier.weight(1f))
                    StatCell(st.settings.volume(thisWeek.sumOf { w -> w.exercises.sumOf { Analytics.volume(it.sets) } }), tr("Volume"), Modifier.weight(1.3f))
                }
                Spacer(Modifier.height(14.dp))
                val weeks = (7 downTo 0).map { weekStart.minusWeeks(it.toLong()) }
                BarChart(weeks.map { ws -> st.history.count { localDate(it.startTime).let { d -> !d.isBefore(ws) && d.isBefore(ws.plusWeeks(1)) } }.toDouble() },
                    weeks.map { null }, WorkoutBlue, height = 80.dp, labels = weeks.map { "${it.dayOfMonth}/${it.monthValue}" })
            }
            SectionLabel(tr("Muscles travaillés cette semaine")) { Text(tr("Niveaux"), Modifier.clickable { nav.navigate("levels") }, color = Mf.colors.accent, style = MaterialTheme.typography.labelLarge) }
            MfCard { BodyMap(intensity(sets, Analytics.SETS_HIGH), st.settings.sex, Modifier.fillMaxWidth().height(260.dp)) }

            val records = remember(st.history) {
                val sorted = st.history.sortedBy { it.startTime }
                sorted.flatMapIndexed { i, w -> Analytics.newRecords(w, sorted.take(i), db).map { w to it } }.takeLast(6).reversed()
            }
            if (records.isNotEmpty()) {
                SectionLabel(tr("Records récents"))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    records.forEach { (w, r) -> RecordCard(db, st.settings, r, shortDate(w.startTime)) { nav.navigate("exercise/${r.exerciseId}") } }
                }
            }

            val top = remember(st.history) {
                st.history.flatMap { w -> w.exercises.map { it.exerciseId } }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6).map { it.key }
            }
            if (top.isNotEmpty()) {
                SectionLabel(tr("Exercices"))
                GroupCard {
                    top.forEachIndexed { i, id ->
                        if (i > 0) RowDivider()
                        val e = db.exercise(id)
                        val pts = Analytics.history(id, st.history, db)
                        val last = pts.lastOrNull(); val prev = pts.dropLast(1).lastOrNull()
                        Row(Modifier.fillMaxWidth().clickable { nav.navigate("exercise/${id}") }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ExerciseThumb(e, 40.dp, 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e?.name ?: "?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(trf("1RM estimé %1\$s", (last?.e1rm?.takeIf { it > 0 }?.let { st.settings.estimate(it) } ?: "—")), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                            }
                            if (last != null && prev != null && last.e1rm > 0 && prev.e1rm > 0) {
                                val d = (last.e1rm / prev.e1rm - 1) * 100
                                Text("${if (d >= 0) "+" else ""}${clean(Math.round(d * 10) / 10.0)} %", color = if (d >= 0) Color(0xFF4CB179) else Color(0xFFF46B5A), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            SectionLabel(tr("Habitude d'entraînement"))
            MfCard { WeekHabit(st.history) }
            if (st.history.isEmpty()) Text(tr("Terminez une première séance pour voir vos statistiques."), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary, modifier = Modifier.padding(8.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Calendrier de 12 semaines et série de semaines actives (workout_habit_view). */
@Composable
internal fun WeekHabit(history: List<Workout>) {
    val today = LocalDate.now()
    val days = history.map { localDate(it.startTime) }.toSet()
    val ws = Analytics.weekStart(today)
    var streak = 0
    var w = ws
    if ((0..6).none { days.contains(w.plusDays(it.toLong())) }) w = w.minusWeeks(1)
    while ((0..6).any { days.contains(w.plusDays(it.toLong())) }) { streak++; w = w.minusWeeks(1) }
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${streak}", style = MaterialTheme.typography.displaySmall)
        Text(tr(" semaine(s) active(s) de suite"), color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (11 downTo 0).forEach { k ->
            val start = ws.minusWeeks(k.toLong())
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                (0..6).forEach { d ->
                    val day = start.plusDays(d.toLong())
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(when {
                        day.isAfter(today) -> Color.Transparent
                        day in days -> WorkoutBlue
                        else -> Mf.colors.chip
                    }))
                }
            }
        }
    }
}

@Composable
internal fun RecordCard(db: ExerciseDb, settings: Settings, r: Analytics.NewRecord, date: String, onClick: () -> Unit) {
    val e = db.exercise(r.exerciseId)
    Column(Modifier.width(170.dp).clip(RoundedCornerShape(16.dp)).border(2.dp, Mf.colors.divider, RoundedCornerShape(16.dp)).background(Mf.colors.card)
        .clickable(onClick = onClick).padding(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(if (r.kind == "Volume") Icons.Rounded.EmojiEvents else Icons.Rounded.Stars, null, Modifier.size(30.dp))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(if (r.kind == "Répétitions") trf("%1\$s reps", (r.value.toInt())) else if (r.kind == "Volume") settings.volume(r.value) else settings.estimate(r.value), style = MaterialTheme.typography.titleSmall)
                Text(tr("Nouveau !"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(e?.name ?: "?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(trf("Record %1\$s · %2\$s", (r.kind.lowercase()), date), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
    }
}

/** Niveaux : séries hebdomadaires par muscle (1 principal, ½ secondaire) contre les repères 8–16 séries. */
@Composable
fun LevelsScreen(vm: AppViewModel, nav: NavHostController) {
    val st by vm.state.collectAsState()
    val db = vm.db.collectAsState().value ?: return
    var weeks by remember { mutableIntStateOf(1) }
    val since = System.currentTimeMillis() - weeks * 7L * 86_400_000
    val sets = Analytics.setsByMuscle(st.history.filter { it.startTime >= since }, db).mapValues { it.value / weeks }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        ScreenTitle(tr("Niveaux"), subtitle = tr("Séries de travail par muscle et par semaine"), onBack = { nav.popBackStack() })
        Column(Modifier.padding(horizontal = 16.dp)) {
            ChipChoice(listOf(1, 4, 12), weeks, { if (it == 1) tr("7 derniers jours") else trf("Moyenne %1\$s semaines", it) }) { weeks = it }
            Spacer(Modifier.height(10.dp))
            MfCard { BodyMap(intensity(sets, Analytics.SETS_HIGH), st.settings.sex, Modifier.fillMaxWidth().height(280.dp)) }
            listOf(true to tr("Haut du corps"), false to tr("Bas du corps et tronc")).forEach { (upper, title) ->
                SectionLabel(title)
                GroupCard {
                    MuscleGroup.entries.filter { it.upper == upper && it != MuscleGroup.HIP_FLEXORS }.sortedByDescending { sets[it] ?: 0.0 }.forEachIndexed { i, g ->
                        if (i > 0) RowDivider()
                        val v = sets[g] ?: 0.0
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            MuscleThumb(g, st.settings.sex, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(g.label.tr(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text(trf("%1\$s série%2\$s", (clean(Math.round(v * 2) / 2.0)), (if (v > 1.5) "s" else "")), style = MaterialTheme.typography.labelLarge,
                                        color = when { v >= Analytics.SETS_LOW -> WorkoutBlue; v > 0 -> Mf.colors.text; else -> Mf.colors.textTertiary })
                                }
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.fillMaxWidth()) {
                                    ProgressBar((v / 20.0).toFloat(), WorkoutBlue, height = 6.dp)
                                    // repères 8 et 16 séries
                                    Row(Modifier.fillMaxWidth()) {
                                        Spacer(Modifier.weight(8f)); Box(Modifier.width(2.dp).height(6.dp).background(Mf.colors.textTertiary))
                                        Spacer(Modifier.weight(8f)); Box(Modifier.width(2.dp).height(6.dp).background(Mf.colors.textTertiary)); Spacer(Modifier.weight(4f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text(tr("Repère : 8 à 16 séries par muscle et par semaine. Muscles secondaires : ½ série."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(8.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** « Workout Complete » : silhouettes des muscles travaillés, records, détails. */
@Composable
fun CompletedScreen(vm: AppViewModel, nav: NavHostController, onClose: () -> Unit) {
    // séance figée à l'ouverture : la remise à zéro de vm.completed à la fermeture ne doit pas redéclencher la navigation
    val w0 = remember { vm.completed.value } ?: return
    val st by vm.state.collectAsState()
    // la séance de l'historique fait foi (modifiable depuis cet écran) ; sinon l'instantané de fin
    val w = st.history.firstOrNull { it.id == w0.id } ?: w0
    val db = vm.db.collectAsState().value
    val change by vm.planChange.collectAsState()
    // états gardés au retour de l'éditeur (l'écran quitte la composition pendant l'édition)
    var longAsked by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(w0.durationSec >= MAX_WORKOUT_SEC) }
    var editDuration by remember { mutableStateOf(false) }
    // le retour système ferme aussi le résumé : sans cela la séance terminée restait en mémoire et l'écran de séance vide réapparaissait
    BackHandler { onClose() }
    // la feuille « Modifications de séance » attend la fin de l'entrée de l'écran (ouverte pendant la transition, seul son voile gris s'affichait)
    var sheetReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(700); sheetReady = true }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        WorkoutSummary(vm, w, tr("Séance terminée"), onClose, onEdit = { nav.navigate("workoutedit/${w.id}") },
            onDuration = { editDuration = true }, onDelete = { vm.deleteWorkout(w.id); onClose() }, rpeFirst = true, nav = nav)
        // Confettis de fin de séance, une seule fois
        var confetti by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
        LaunchedEffect(Unit) { if (confetti) { kotlinx.coroutines.delay(4500); confetti = false } }
        if (confetti) app.superhealth.training.ui.components.Confetti(Modifier.fillMaxSize())
    }
    if (longAsked) LongWorkoutDialog(w, onKeep = { longAsked = false; vm.saveWorkout(w.copy(durationSec = MAX_WORKOUT_SEC)) },
        onEstimate = { longAsked = false; vm.saveWorkout(w.copy(durationSec = it)) }, onEdit = { longAsked = false; editDuration = true })
    if (editDuration) DurationDialog(w.durationSec, estimatedDuration(w), onDismiss = { editDuration = false }) { editDuration = false; vm.saveWorkout(w.copy(durationSec = it)) }
    else if (sheetReady && !longAsked && change != null && db != null) PlanDiffSheet(vm, db, change!!)
}

@Composable
fun WorkoutDetailScreen(vm: AppViewModel, id: String, nav: NavHostController) {
    val st by vm.state.collectAsState()
    val w = st.history.firstOrNull { it.id == id } ?: return
    var editDuration by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        WorkoutSummary(vm, w, w.name, { nav.popBackStack() }, onEdit = { nav.navigate("workoutedit/$id") },
            onDuration = { editDuration = true }, onDelete = { vm.deleteWorkout(id); nav.popBackStack() }, nav = nav)
        // Refaire cette séance (mêmes exercices et séries, charges d'après l'historique).
        app.superhealth.training.ui.components.PillButton(if (st.session == null) tr("Refaire cette séance") else tr("Reprendre la séance en cours"),
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp), icon = Icons.Rounded.Repeat) {
            vm.repeatWorkout(w); nav.navigate("session")
        }
    }
    if (editDuration) DurationDialog(w.durationSec, estimatedDuration(w), onDismiss = { editDuration = false }) { editDuration = false; vm.saveWorkout(w.copy(durationSec = it)) }
}

@Composable
private fun WorkoutSummary(vm: AppViewModel, w: Workout, title: String, onClose: () -> Unit, onEdit: () -> Unit, onDuration: () -> Unit, onDelete: () -> Unit, rpeFirst: Boolean = false,
                           nav: NavHostController? = null) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    if (menu) WorkoutOptionsMenu(onDismiss = { menu = false }, onEdit = { menu = false; onEdit() }, onDuration = { menu = false; onDuration() },
        onLibrary = { menu = false; vm.saveToLibrary(w); Toast.makeText(ctx, tr("Ajoutée à la bibliothèque"), Toast.LENGTH_SHORT).show() },
        onDelete = { menu = false; confirmDelete = true })
    if (confirmDelete) androidx.compose.material3.AlertDialog(onDismissRequest = { confirmDelete = false }, containerColor = Mf.colors.card,
        title = { Text(tr("Supprimer cette séance ?")) }, text = { Text(tr("La séance sera définitivement supprimée.")) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(tr("Supprimer"), color = Danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Annuler"), color = Mf.colors.text) } })
    val st by vm.state.collectAsState()
    val db = vm.db.collectAsState().value ?: return
    val sets = Analytics.setsByMuscle(listOf(w), db)
    val max = (sets.values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val before = st.history.filter { it.startTime < w.startTime }
    val records = Analytics.newRecords(w, before, db)
    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding()) {
        app.superhealth.nutrition.ui.components.NavBar(title, onClose, backIcon = Icons.Rounded.Close) {
            app.superhealth.nutrition.ui.components.CircleAction(Icons.Rounded.MoreHoriz, tr("Options de la séance")) { menu = true }
        }
        val rpe: @Composable () -> Unit = { RpeCard(w) { r -> vm.saveWorkout(w.copy(rpe = r)); app.superhealth.health.data.Scores.invalidate() } }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            // fin de séance : l'effort ressenti en premier, avant la silhouette (il passait sous le pli)
            if (rpeFirst) rpe()
            BodyMap(intensity(sets, max), st.settings.sex, Modifier.fillMaxWidth().height(if (rpeFirst) 260.dp else 320.dp))
            if (records.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    records.forEach { RecordCard(db, st.settings, it, shortDate(w.startTime)) {} }
                }
            }
            if (w.note.isNotBlank()) MfCard(Modifier.padding(top = 12.dp)) {
                Text(tr("Note de séance"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
                Text(w.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            if (!rpeFirst) rpe()
            if (nav != null) app.superhealth.ui.coach.CoachChip(tr("Analyser avec le coach"),
                trf("Analyse ma séance « %1\$s » du %2\$s : progression par rapport aux précédentes, points forts, quoi changer la prochaine fois.", w.name, longDate(w.startTime)),
                nav, Modifier.padding(top = 12.dp))
            SectionLabel(tr("Détails de la séance"))
            GroupCard {
                Detail(Icons.Rounded.FitnessCenter, tr("Volume"), st.settings.volume(w.exercises.sumOf { Analytics.volume(it.sets) }))
                RowDivider(); Detail(Icons.Rounded.Schedule, tr("Durée"), durationWords(w.durationSec), onDuration)
                RowDivider(); Detail(Icons.Rounded.Repeat, tr("Séries de travail"), "${w.exercises.sumOf { Analytics.workingSets(it).size }}")
                RowDivider(); Detail(Icons.Rounded.Stars, tr("Date"), longDate(w.startTime))
                w.programName?.let { RowDivider(); Detail(Icons.AutoMirrored.Rounded.KeyboardArrowRight, tr("Programme"), it + (w.cycleIndex?.let { c -> trf(" · cycle %1\$s", (c + 1)) } ?: "")) }
            }
            SectionLabel(tr("Exercices"))
            w.exercises.forEach { ex ->
                val e = db.exercise(ex.exerciseId)
                MfCard(Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExerciseThumb(e, 40.dp, 10.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(e?.name ?: "?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    }
                    ex.sets.forEachIndexed { i, s ->
                        Text(trf("%1\$s  %2\$s × %3\$s%4\$s", (if (s.type == SetType.WARMUP) tr("É") else "${i + 1}"), (s.weight?.let { st.settings.weight(it) } ?: ""), (s.reps ?: 0), (s.rir?.let { trf(" · RIR %1\$s", it) } ?: "")),
                            style = MaterialTheme.typography.bodySmall, color = if (s.type == SetType.WARMUP) Mf.colors.textTertiary else Mf.colors.textSecondary,
                            modifier = Modifier.padding(top = 3.dp))
                    }
                    if (ex.note.isNotBlank()) Text(ex.note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Échelle CR-10 (Borg, utilisée par la session-RPE de Foster) : les repères nommés. */
private val RPE_WORDS = mapOf(1 to "Très facile", 2 to "Facile", 3 to "Modéré", 4 to "Un peu dur", 5 to "Dur", 7 to "Très dur", 9 to "Presque maximal", 10 to "Maximal")

/** Effort ressenti de la séance, une touche : sa charge ressentie (effort × minutes) entre dans l'Effort du jour. */
@Composable
private fun RpeCard(w: Workout, onPick: (Int) -> Unit) {
    MfCard(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Effort ressenti"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            w.rpe?.let { r -> Text(tr(RPE_WORDS[r] ?: RPE_WORDS.filterKeys { it < r }.maxBy { it.key }.value), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..10).forEach { n ->
                val on = w.rpe == n
                Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Mf.colors.accent.container(1.4f) else Mf.colors.chip)
                    .clickable { onPick(n) }, contentAlignment = Alignment.Center) {
                    Text("$n", style = MaterialTheme.typography.labelLarge, color = if (on) Mf.colors.accent.onContainer() else Mf.colors.text)
                }
            }
        }
        w.rpe?.let { r ->
            Spacer(Modifier.height(8.dp))
            Text(trf("Charge ressentie : %1\$s × %2\$s min = %3\$s", r, w.durationSec / 60, r * (w.durationSec / 60)),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
    }
}

@Composable
private fun Detail(icon: ImageVector, k: String, v: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column { Text(k, style = MaterialTheme.typography.bodyMedium); Text(v, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary) }
    }
}
