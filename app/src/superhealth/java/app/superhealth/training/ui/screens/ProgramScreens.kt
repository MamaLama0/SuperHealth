package app.superhealth.training.ui.screens

import app.superhealth.training.ui.tr
import app.superhealth.training.ui.trf
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.training.domain.Deload
import app.superhealth.training.domain.ExerciseDb
import app.superhealth.training.domain.Experience
import app.superhealth.training.domain.Generator
import app.superhealth.training.domain.Goal
import app.superhealth.training.domain.MuscleGroup
import app.superhealth.training.domain.Periodization
import app.superhealth.training.domain.Program
import app.superhealth.training.domain.ProgramDay
import app.superhealth.training.domain.ProgramExercise
import app.superhealth.training.domain.Split
import app.superhealth.training.domain.TargetSet
import app.superhealth.training.ui.AppViewModel
import app.superhealth.training.ui.Picker
import app.superhealth.training.ui.components.ChipChoice
import app.superhealth.training.ui.components.ExerciseThumb
import app.superhealth.training.ui.components.GroupCard
import app.superhealth.training.ui.components.MfCard
import app.superhealth.training.ui.components.MuscleThumb
import app.superhealth.training.ui.components.PillButton
import app.superhealth.training.ui.components.RowDivider
import app.superhealth.training.ui.components.SectionLabel
import app.superhealth.training.ui.components.SettingRow
import app.superhealth.training.ui.components.TextInput
import app.superhealth.training.ui.restLabel
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.ProgramColors
import app.superhealth.ui.theme.WorkoutBlue
import kotlinx.coroutines.delay
import java.util.UUID

// ----- Assistant de création (CreateWorkoutProgramModuleSection) -----

private val DURATIONS = listOf(30 to "20 à 40 minutes", 50 to "40 à 60 minutes", 70 to "60 à 80 minutes", 90 to "80 à 100 minutes")

/** Étapes affichées pendant la génération (libellés observés dans l'APK). */
private val GEN_STEPS = listOf(tr("Prise en compte de votre expérience"), tr("Analyse de la priorisation musculaire"), tr("Vérification de l'équipement disponible"),
    tr("Sélection des exercices"), tr("Programmation de votre split"), tr("Optimisation de la durée des séances"), tr("Périodisation"), tr("Activation de la progression intelligente"))

@Composable
fun ProgramWizardScreen(vm: AppViewModel, nav: NavHostController) {
    val st by vm.state.collectAsState()
    val db = vm.db.collectAsState().value ?: return
    var step by remember { mutableIntStateOf(0) }
    var goal by remember { mutableStateOf(Goal.HYPERTROPHY) }
    var days by remember { mutableIntStateOf(4) }
    var split by remember { mutableStateOf<Split?>(null) }
    var minutes by remember { mutableIntStateOf(70) }
    var period by remember { mutableStateOf(Periodization.LINEAR) }
    val focus = remember { mutableStateMapOf<MuscleGroup, Int>() }
    var gymId by remember { mutableStateOf(vm.activeGym()?.id) }
    var deload by remember { mutableStateOf(Deload.LAST) }
    var exp by remember { mutableStateOf(Experience.INTERMEDIATE) }
    var name by remember { mutableStateOf(tr("Mon programme")) }
    var color by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Program?>(null) }
    var exit by remember { mutableStateOf(false) }
    val last = 9

    if (result != null) { ProgramReview(vm, db, result!!, nav); return }
    if (step > last) { Generating { result = vm.generate(Generator.Input(goal, days, split ?: Split.recommended(days), minutes, period, deload, exp,
        focus.toMap(), (vm.gym(gymId) ?: vm.activeGym())?.equipmentIds ?: emptySet(), st.exerciseConfig.filter { it.value.doNotRecommend }.keys,
        name.ifBlank { tr("Mon programme") }, color, gymId)) }; return }

    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (step == 0) exit = true else step-- }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour")) }
            LinearProgressIndicator(progress = { (step + 1f) / (last + 1) }, Modifier.weight(1f).height(4.dp).clip(CircleShape), color = WorkoutBlue, trackColor = Mf.colors.chip)
            IconButton(onClick = { exit = true }) { Icon(Icons.Rounded.Close, tr("Quitter")) }
        }
        androidx.compose.animation.AnimatedContent(step, Modifier.weight(1f), label = "q", transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(360)) { dir * it / 3 } + androidx.compose.animation.fadeIn()) togetherWith
                (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(280)) { -dir * it / 4 } + androidx.compose.animation.fadeOut())
        }) { step ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            WIZARD_ART[step]?.let { art ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    app.superhealth.training.ui.components.RiveAsset(art, Modifier.size(130.dp).clip(RoundedCornerShape(28.dp)))
                }
            }
            when (step) {
                0 -> { Q(tr("Quel est votre objectif principal ?")); Goal.entries.forEach { g -> Choice(g.label.tr(), g.description.tr(), goal == g) { goal = g } } }
                1 -> {
                    Q(tr("Combien de séances par semaine ?"))
                    Row(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Round(Icons.Rounded.Remove) { if (days > 1) { days--; split = null } }
                        Column(Modifier.width(140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${days}", style = MaterialTheme.typography.displayLarge)
                            Text(tr("fois par semaine"), color = Mf.colors.textSecondary)
                        }
                        Round(Icons.Rounded.Add) { if (days < 7) { days++; split = null } }
                    }
                }
                2 -> {
                    Q(tr("Comment préférez-vous structurer vos séances ?"))
                    Choice(tr("Pas de préférence"), tr("Nous choisissons une structure recommandée pour vous."), split == null) { split = null }
                    Split.forDays(days).forEach { sp -> Choice(sp.label.tr(), sp.days.joinToString(" · ") { it.label.tr() }, split == sp) { split = sp } }
                }
                3 -> { Q(tr("Combien de temps par séance ?")); DURATIONS.forEach { (m, l) -> Choice(l, null, minutes == m) { minutes = m } } }
                4 -> { Q(tr("Comment vos plages de répétitions doivent-elles évoluer chaque semaine ?")); Periodization.entries.forEach { p -> Choice(p.label.tr(), p.description.tr(), period == p) { period = p } } }
                5 -> {
                    Q(tr("Quels muscles prioriser ou déprioriser ?"))
                    val up = focus.values.filter { it > 0 }.sum(); val down = -focus.values.filter { it < 0 }.sum()
                    Text(trf("%1\$s point(s) de priorité · %2\$s de dépriorisation restants", (5 - up), (5 - down)), color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    MuscleGroup.entries.filter { it != MuscleGroup.HIP_FLEXORS }.forEach { g ->
                        val v = focus[g] ?: 0
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(Mf.colors.card).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            MuscleThumb(g, st.settings.sex, 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(g.label.tr(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Round(Icons.Rounded.Remove, small = true) { if (v > -1 && (v > 0 || down < 5)) focus[g] = v - 1 }
                            Text(when { v > 0 -> "+${v}"; v < 0 -> "${v}"; else -> "0" }, Modifier.width(34.dp), color = when { v > 0 -> WorkoutBlue; v < 0 -> Color(0xFFE11900); else -> Mf.colors.textSecondary },
                                style = MaterialTheme.typography.titleSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Round(Icons.Rounded.Add, small = true) { if (v < 2 && (v < 0 || up < 5)) focus[g] = v + 1 }
                        }
                    }
                }
                6 -> { Q(tr("Quelle salle utiliserez-vous pendant ce programme ?")); st.gyms.forEach { g -> Choice(g.name, trf("%1\$s équipements", g.equipmentIds.size), gymId == g.id) { gymId = g.id } } }
                7 -> {
                    Q(tr("Ajouter une semaine de décharge ?"))
                    Choice(tr("Dernier cycle (recommandé)"), tr("Le bloc se termine par une semaine plus légère pour récupérer avant la phase suivante."), deload == Deload.LAST) { deload = Deload.LAST }
                    Choice(tr("Premier cycle"), tr("Le bloc commence par une semaine plus légère."), deload == Deload.FIRST) { deload = Deload.FIRST }
                    Choice(tr("Aucune"), tr("S'entraîner en continu sans réduction programmée de l'intensité ou du volume."), deload == Deload.NONE) { deload = Deload.NONE }
                }
                8 -> { Q(tr("Votre expérience en musculation")); Experience.entries.forEach { e -> Choice(e.label.tr(), null, exp == e) { exp = e } } }
                else -> {
                    Q(tr("Comment nommer ce programme ?"))
                    TextInput(tr("Nom"), name, { name = it })
                    Text(tr("Couleur"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProgramColors.forEachIndexed { i, c ->
                            Box(Modifier.size(36.dp).clip(CircleShape).background(c).clickable { color = i }, contentAlignment = Alignment.Center) {
                                if (color == i) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
        }
        PillButton(if (step == last) tr("Générer le programme") else tr("Suivant"), Modifier.fillMaxWidth().padding(20.dp)) { step++ }
    }
    if (exit) AlertDialog(onDismissRequest = { exit = false }, containerColor = Mf.colors.card,
        title = { Text(tr("Quitter l'assistant ?")) }, text = { Text(tr("Les réglages saisis seront perdus.")) },
        confirmButton = { TextButton(onClick = { nav.popBackStack() }) { Text(tr("Quitter"), color = Color(0xFFE11900)) } },
        dismissButton = { TextButton(onClick = { exit = false }) { Text(tr("Rester"), color = Mf.colors.text) } })
}

@Composable private fun Q(t: String) = Text(t, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp, bottom = 16.dp))

@Composable
private fun Choice(title: String, sub: String?, selected: Boolean, onClick: () -> Unit) =
    app.superhealth.training.ui.components.OptionTile(title, sub, selected, onClick = onClick)

/** Illustration animée (assets Rive de l'APK) en tête de chaque question de l'assistant. */
private val WIZARD_ART = mapOf(
    0 to "mf_planet_flag", 1 to "mf_rocket_launcher_v2", 2 to "mf_space_station_v2", 3 to "mf_cockpit_view", 4 to "mf_flying_rocket",
    6 to "mf_rover", 7 to "mf_landing_module", 8 to "mf_hologram_planet", 9 to "mf_flag_1",
)

@Composable
private fun Round(icon: androidx.compose.ui.graphics.vector.ImageVector, small: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(if (small) 32.dp else 52.dp).clip(CircleShape).background(Mf.colors.chip).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(if (small) 18.dp else 26.dp))
    }
}

/** « Generating your program based on the latest science » : étapes cochées une à une. */
@Composable
private fun Generating(onDone: () -> Unit) {
    var done by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (done < GEN_STEPS.size) { delay(480); done++ }; delay(400); onDone() }
    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            app.superhealth.training.ui.components.RiveAsset("mf_liftoff", Modifier.size(200.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(tr("Génération de votre programme selon les dernières données scientifiques"), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        GEN_STEPS.forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    i < done -> Icon(Icons.Rounded.CheckCircle, null, tint = WorkoutBlue, modifier = Modifier.size(22.dp))
                    i == done -> CircularProgressIndicator(Modifier.size(22.dp), color = WorkoutBlue, strokeWidth = 2.dp)
                    else -> Box(Modifier.size(22.dp).clip(CircleShape).background(Mf.colors.chip))
                }
                Spacer(Modifier.width(12.dp))
                Text(s, color = if (i <= done) Mf.colors.text else Mf.colors.textTertiary)
            }
        }
    }
}

@Composable
private fun ProgramReview(vm: AppViewModel, db: ExerciseDb, p: Program, nav: NavHostController) {
    DetailScaffold(p.name, nav) {
        Text(tr("Prochain cycle du programme"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        p.days.forEach { d -> DayCard(d, db, 0) }
        Spacer(Modifier.height(12.dp))
        PillButton(tr("Activer le programme"), Modifier.fillMaxWidth()) { vm.saveProgram(p, activate = true); nav.popBackStack() }
        PillButton(tr("Enregistrer dans la bibliothèque"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false) { vm.saveProgram(p); nav.popBackStack() }
    }
}

@Composable
private fun DayCard(d: ProgramDay, db: ExerciseDb, cycle: Int, onClick: (() -> Unit)? = null) {
    MfCard(Modifier.padding(vertical = 5.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(d.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (!d.isRest) Text(trf("≈ %1\$s min", (Generator.estimateMinutes(d, cycle))), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        if (d.isRest) Text(tr("Aucun exercice prévu ce jour"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        d.exercises.forEach { pe ->
            val e = db.exercise(pe.exerciseId)
            val t = Generator.targetsFor(pe, cycle)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ExerciseThumb(e, 36.dp, 8.dp)
                Spacer(Modifier.width(10.dp))
                Text(e?.name ?: "?", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(targetSummary(t), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
        }
    }
}

fun targetSummary(t: List<TargetSet>) = t.firstOrNull()?.let { trf("%1\$s × %2\$s-%3\$s · RIR %4\$s", t.size, it.minReps, it.maxReps, it.rir) } ?: "—"

// ----- Programme -----

@Composable
fun ProgramScreen(vm: AppViewModel, id: String, nav: NavHostController) {
    val st by vm.state.collectAsState()
    val db = vm.db.collectAsState().value ?: return
    val p = st.programs.firstOrNull { it.id == id } ?: run { LaunchedEffect(Unit) { nav.popBackStack() }; return }
    val active = st.settings.activeProgramId == id
    var confirm by remember { mutableStateOf<String?>(null) }
    var cycle by remember { mutableIntStateOf(p.cycleIndex.coerceIn(0, p.numCycles - 1)) }
    fun save(np: Program) = vm.saveProgram(np)
    DetailScaffold(p.name, nav) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(ProgramColors[p.color % ProgramColors.size]))
        Spacer(Modifier.height(10.dp))
        Text(trf("%1\$s séances par cycle de %2\$s jours · %3\$s", (p.days.count { !it.isRest }), p.days.size, p.goal.label.tr()), color = Mf.colors.textSecondary)
        if (active) Text(trf("Programme actif · cycle %1\$s/%2\$s", (p.cycleIndex + 1), p.numCycles), color = WorkoutBlue, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(10.dp))
        if (p.numCycles > 1) ChipChoice((0 until p.numCycles).toList(), cycle, { c -> trf("Cycle %1\$s", (c + 1)) + if ((p.deload == Deload.FIRST && c == 0) || (p.deload == Deload.LAST && c == p.numCycles - 1)) tr(" · décharge") else "" }) { cycle = it }
        p.days.forEachIndexed { i, d -> DayCard(d, db, cycle) { nav.navigate("programday/${p.id}/${i}") } }
        SectionLabel(tr("Réglages du programme"))
        GroupCard {
            SettingRow(tr("Cycles"), "${p.numCycles}") { save(p.copy(numCycles = (p.numCycles % 12) + 1, days = p.days.map { d -> d.copy(exercises = d.exercises.map { pe -> pe.copy(cycles = resize(pe.cycles, (p.numCycles % 12) + 1)) }) })) }
            RowDivider(); SettingRow(tr("Décharge"), p.deload.label.tr()) { save(p.copy(deload = Deload.entries[(p.deload.ordinal + 1) % 3])) }
            RowDivider(); SettingRow(tr("Périodisation"), p.periodization.label.tr(), subtitle = tr("Organise l'entraînement en phases qui font varier intensité et volume"))
            RowDivider(); SettingRow(tr("Répéter indéfiniment"), if (p.runIndefinitely) tr("Oui") else tr("Non")) { save(p.copy(runIndefinitely = !p.runIndefinitely)) }
            RowDivider(); SettingRow(tr("Couleur"), null) { save(p.copy(color = (p.color + 1) % ProgramColors.size)) }
            RowDivider(); SettingRow(tr("Ajouter un jour"), null) { save(p.copy(days = p.days + ProgramDay(UUID.randomUUID().toString(), trf("Jour %1\$s", (p.days.size + 1))))) }
        }
        Spacer(Modifier.height(14.dp))
        if (!active) PillButton(tr("Activer le programme"), Modifier.fillMaxWidth()) { confirm = "activate" }
        else PillButton(tr("Désactiver le programme"), Modifier.fillMaxWidth(), strong = false) { confirm = "deactivate" }
        PillButton(tr("Dupliquer"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false) {
            vm.saveProgram(p.copy(id = UUID.randomUUID().toString(), name = p.name + tr(" (copie)"), cycleIndex = 0, dayIndex = 0)); nav.popBackStack()
        }
        PillButton(if (p.archived) tr("Sortir de l'archive") else tr("Archiver"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false) { save(p.copy(archived = !p.archived)) }
        Text(tr("Supprimer le programme"), Modifier.fillMaxWidth().clickable { confirm = "delete" }.padding(14.dp), color = Color(0xFFE11900),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
    confirm?.let { c ->
        AlertDialog(onDismissRequest = { confirm = null }, containerColor = Mf.colors.card,
            title = { Text(when (c) { "activate" -> tr("Activer ce programme ?"); "deactivate" -> tr("Désactiver ce programme ?"); else -> tr("Supprimer ce programme ?") }) },
            text = { Text(if (c == "delete") tr("Le programme et ses séances seront supprimés. Votre historique est conservé.") else tr("Le programme actif retourne dans la bibliothèque et sa progression est réinitialisée.")) },
            confirmButton = { TextButton(onClick = {
                when (c) { "activate" -> vm.activate(id); "deactivate" -> vm.activate(null); else -> { vm.deleteProgram(id); nav.popBackStack() } }
                confirm = null
            }) { Text(tr("Confirmer"), color = Mf.colors.accent) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr("Annuler"), color = Mf.colors.text) } })
    }
}

private fun resize(cycles: List<List<TargetSet>>, n: Int): List<List<TargetSet>> =
    if (cycles.size == 1) cycles else List(n) { cycles.getOrElse(it) { cycles.last() } }

// ----- Jour de programme -----

@Composable
fun ProgramDayScreen(vm: AppViewModel, pid: String, dayIndex: Int, nav: NavHostController) {
    val st by vm.state.collectAsState()
    val db = vm.db.collectAsState().value ?: return
    val p = st.programs.firstOrNull { it.id == pid } ?: return
    val d = p.days.getOrNull(dayIndex) ?: return
    var cycle by remember { mutableIntStateOf(p.cycleIndex.coerceIn(0, p.numCycles - 1)) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var rename by remember { mutableStateOf(false) }
    fun setDay(nd: ProgramDay) = vm.saveProgram(p.copy(days = p.days.toMutableList().also { it[dayIndex] = nd }))
    DetailScaffold(d.name, nav, actions = { TextButton(onClick = { rename = true }) { Text(tr("Renommer"), color = Mf.colors.accent) } }) {
        if (p.numCycles > 1 && d.exercises.any { it.cycles.size > 1 }) ChipChoice((0 until p.numCycles).toList(), cycle, { trf("Cycle %1\$s", (it + 1)) }) { cycle = it }
        if (d.isRest) MfCard { Text(tr("Jour de repos. Ajoutez des exercices pour le transformer en séance."), color = Mf.colors.textSecondary) }
        d.exercises.forEachIndexed { i, pe ->
            val e = db.exercise(pe.exerciseId)
            val t = Generator.targetsFor(pe, cycle)
            MfCard(Modifier.padding(vertical = 5.dp), onClick = { editing = i }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExerciseThumb(e, 44.dp, 10.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e?.name ?: "?", style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        Text(trf("%1\$s · repos %2\$s", (targetSummary(t)), (restLabel(t.firstOrNull()?.restSec ?: 120))), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
                }
            }
        }
        PillButton(tr("Ajouter des exercices"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false, icon = Icons.Rounded.Add) { nav.navigate("picker/${Picker.DAY}?target=${pid}:${dayIndex}") }
        if (!d.isRest) {
            PillButton(tr("Commencer cette séance"), Modifier.fillMaxWidth().padding(top = 8.dp), icon = Icons.Rounded.PlayArrow) {
                if (st.session == null) vm.startProgramDay(p, dayIndex, cycle); nav.navigate("session")
            }
            Text(tr("Transformer en jour de repos"), Modifier.fillMaxWidth().clickable { setDay(d.copy(exercises = emptyList(), name = tr("Repos"))) }.padding(14.dp),
                color = Color(0xFFE11900), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    editing?.let { i ->
        TargetSheet(d.exercises[i], cycle, db.exercise(d.exercises[i].exerciseId)?.name ?: "", onDismiss = { editing = null },
            onSave = { ne -> setDay(d.copy(exercises = d.exercises.toMutableList().also { it[i] = ne })); editing = null },
            onRemove = { setDay(d.copy(exercises = d.exercises.filterIndexed { j, _ -> j != i })); editing = null },
            onUp = if (i > 0) ({ setDay(d.copy(exercises = d.exercises.toMutableList().also { java.util.Collections.swap(it, i, i - 1) })); editing = null }) else null)
    }
    if (rename) {
        var n by remember { mutableStateOf(d.name) }
        AlertDialog(onDismissRequest = { rename = false }, containerColor = Mf.colors.card, title = { Text(tr("Renommer le jour")) },
            text = { TextInput(tr("Nom"), n, { n = it }) },
            confirmButton = { TextButton(onClick = { setDay(d.copy(name = n.ifBlank { d.name })); rename = false }) { Text(tr("Enregistrer"), color = Mf.colors.accent) } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text(tr("Annuler"), color = Mf.colors.text) } })
    }
}

/** Éditeur de cibles (EditExerciseTargetSheet) : séries, plage de répétitions, RIR, repos ; « Enregistrer pour tous les cycles ». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetSheet(pe: ProgramExercise, cycle: Int, name: String, onDismiss: () -> Unit, onSave: (ProgramExercise) -> Unit, onRemove: () -> Unit, onUp: (() -> Unit)?) {
    val cur = Generator.targetsFor(pe, cycle)
    var sets by remember { mutableIntStateOf(cur.size) }
    var min by remember { mutableIntStateOf(cur.firstOrNull()?.minReps ?: 8) }
    var max by remember { mutableIntStateOf(cur.firstOrNull()?.maxReps ?: 12) }
    var rir by remember { mutableIntStateOf(cur.firstOrNull()?.rir ?: 2) }
    var rest by remember { mutableIntStateOf(cur.firstOrNull()?.restSec ?: 120) }
    fun built() = List(sets) { (cur.firstOrNull() ?: TargetSet()).copy(minReps = min, maxReps = max.coerceAtLeast(min), rir = rir, restSec = rest) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.card) {
        Column(Modifier.padding(20.dp).navigationBarsPadding()) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Text(trf("Cycle %1\$s", (cycle + 1)), color = Mf.colors.textSecondary)
            Stepper(tr("Séries"), sets, 1, 10) { sets = it }
            Stepper(tr("Répétitions min."), min, 1, 50) { min = it; if (max < it) max = it }
            Stepper(tr("Répétitions max."), max, 1, 60) { max = it.coerceAtLeast(min) }
            Stepper(tr("RIR (répétitions en réserve)"), rir, 0, 5) { rir = it }
            Stepper(tr("Repos"), rest, 30, 600, step = 15, label = { restLabel(it) }) { rest = it }
            Spacer(Modifier.height(12.dp))
            PillButton(tr("Enregistrer pour ce cycle"), Modifier.fillMaxWidth()) {
                onSave(pe.copy(cycles = pe.cycles.toMutableList().also { it[cycle.coerceIn(0, it.lastIndex)] = built() }))
            }
            PillButton(tr("Enregistrer pour tous les cycles"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false) { onSave(pe.copy(cycles = pe.cycles.map { built() })) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (onUp != null) TextButton(onClick = onUp) { Text(tr("Monter"), color = Mf.colors.text) } else Spacer(Modifier.width(1.dp))
                TextButton(onClick = onRemove) { Text(tr("Retirer l'exercice"), color = Color(0xFFE11900)) }
            }
        }
    }
}

@Composable
private fun Stepper(title: String, value: Int, min: Int, max: Int, step: Int = 1, label: (Int) -> String = { "${it}" }, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Round(Icons.Rounded.Remove, small = true) { if (value - step >= min) onChange(value - step) }
        Text(label(value), Modifier.width(56.dp), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Round(Icons.Rounded.Add, small = true) { if (value + step <= max) onChange(value + step) }
    }
}
