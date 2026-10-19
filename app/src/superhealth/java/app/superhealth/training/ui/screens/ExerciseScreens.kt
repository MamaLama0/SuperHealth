package app.superhealth.training.ui.screens

import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Add
import app.superhealth.training.ui.tr
import app.superhealth.training.ui.trf
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.training.domain.Analytics
import app.superhealth.training.domain.Exercise
import app.superhealth.training.domain.ExerciseConfig
import app.superhealth.training.domain.Generator
import app.superhealth.training.domain.MuscleGroup
import app.superhealth.training.domain.ProgramExercise
import app.superhealth.training.domain.SetLog
import app.superhealth.training.domain.SetType
import app.superhealth.training.domain.TargetSet
import app.superhealth.training.ui.AppViewModel
import app.superhealth.training.ui.Assets
import app.superhealth.training.ui.AsyncAsset
import app.superhealth.training.ui.Picker
import app.superhealth.training.ui.clean
import app.superhealth.training.ui.components.ExerciseThumb
import app.superhealth.training.ui.components.GroupCard
import app.superhealth.training.ui.components.LineChart
import app.superhealth.training.ui.components.MarkdownText
import app.superhealth.training.ui.components.MfCard
import app.superhealth.training.ui.components.PillButton
import app.superhealth.training.ui.components.RowDivider
import app.superhealth.training.ui.components.SectionLabel
import app.superhealth.training.ui.components.Series
import app.superhealth.training.ui.components.StatCell
import app.superhealth.training.ui.components.SwitchRow
import app.superhealth.training.ui.components.Tag
import app.superhealth.training.ui.shortDate
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.WorkoutBlue
import app.superhealth.training.ui.weight
import app.superhealth.training.ui.estimate
import java.util.UUID

/** Sélecteur d'exercices (SelectExerciseView / AddExercisesView / SwapExerciseView). */
@Composable
fun ExercisePickerScreen(vm: AppViewModel, mode: String, target: String, nav: NavHostController) {
    val st by vm.state.collectAsState()
    val db = vm.db.collectAsState().value ?: return
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<MuscleGroup?>(null) }
    var gymOnly by remember { mutableStateOf(mode != Picker.BROWSE) }
    val chosen = remember { mutableStateListOf<String>() }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    // clavier ouvert à la première entrée seulement (pas au retour de la fiche ou de la création d'exercice)
    var focused by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (mode != Picker.SWAP && !focused) { focused = true; runCatching { focus.requestFocus() } } }
    val gym = vm.activeGym()
    val swapOf: Exercise? = if (mode == Picker.SWAP) st.session?.exercises?.getOrNull(target.toIntOrNull() ?: -1)?.let { db.exercise(it.exerciseId) } else null
    val list = remember(query, group, gymOnly, db, swapOf) {
        val base = db.exercises.filter { e ->
            (group == null || group!!.key in e.primaryGroups) && (!gymOnly || gym == null || db.isAvailable(e, gym.equipmentIds))
        }
        val found = db.search(query, base)
        if (swapOf != null && query.isBlank()) {
            // remplacements intelligents : même schéma de mouvement et mêmes muscles principaux d'abord
            found.filter { it.id != swapOf.id }.sortedByDescending { e ->
                e.movementPatterns.intersect(swapOf.movementPatterns.toSet()).size * 3 + e.primaryGroups.intersect(swapOf.primaryGroups.toSet()).size +
                    (if (e.groups.intersect(swapOf.groups.toSet()).isNotEmpty()) 5 else 0)
            }
        } else found
    }

    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour")) }
            Row(Modifier.weight(1f).height(46.dp).clip(CircleShape).background(Mf.colors.elevated).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = Mf.colors.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(if (swapOf != null) trf("Remplacer %1\$s", swapOf.name) else tr("Rechercher un exercice"), color = Mf.colors.textSecondary, maxLines = 1)
                    BasicTextField(query, { query = it }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Mf.colors.text), cursorBrush = SolidColor(Mf.colors.accent))
                }
            }
            IconButton(onClick = { nav.navigate("exerciseedit") }) { Icon(Icons.Rounded.Add, tr("Créer un exercice")) }
        }
        // exercice tout juste créé : sélectionné (ou remplaçant) directement
        val created = nav.currentBackStackEntry?.savedStateHandle?.getStateFlow<String?>("createdExercise", null)?.collectAsState()?.value
        androidx.compose.runtime.LaunchedEffect(created) {
            val cid = created ?: return@LaunchedEffect
            nav.currentBackStackEntry?.savedStateHandle?.set("createdExercise", null)
            when (mode) {
                Picker.SWAP -> { swap(vm, target.toInt(), cid); nav.popBackStack() }
                Picker.BROWSE -> nav.navigate("exercise/${cid}")
                else -> if (cid !in chosen) chosen.add(cid)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(if (gymOnly) tr("Ma salle ✓") else tr("Ma salle"), gymOnly) { gymOnly = !gymOnly }
            Chip(tr("Tous"), group == null) { group = null }
            MuscleGroup.entries.forEach { g -> Chip(g.label.tr(), group == g) { group = if (group == g) null else g } }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 90.dp)) {
            item { Text(trf("%1\$s exercices", list.size), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary) }
            items(list, key = { it.id }) { e ->
                val sel = e.id in chosen
                Row(Modifier.fillMaxWidth().background(if (sel) WorkoutBlue.copy(alpha = 0.10f) else Mf.colors.background).clickable {
                    when (mode) {
                        Picker.BROWSE -> nav.navigate("exercise/${e.id}")
                        Picker.SWAP -> { swap(vm, target.toInt(), e.id); nav.popBackStack() }
                        else -> if (sel) chosen.remove(e.id) else chosen.add(e.id)
                    }
                }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExerciseThumb(e, 52.dp, 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(e.muscleGroups.joinToString(", ") { it.label.tr() }, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
                    }
                    if (sel) Icon(Icons.Rounded.CheckCircle, null, tint = WorkoutBlue)
                }
            }
        }
    }
    if (chosen.isNotEmpty()) Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        PillButton(trf("Ajouter %1\$s exercice(s)", chosen.size), Modifier.fillMaxWidth()) {
            when (mode) {
                Picker.SESSION -> vm.addExercises(chosen.toList())
                Picker.DAY -> addToDay(vm, target, chosen.toList())
            }
            nav.popBackStack()
        }
    }
}

@Composable
private fun Chip(t: String, sel: Boolean, onClick: () -> Unit) {
    Text(t, Modifier.clip(CircleShape).background(if (sel) Mf.colors.strong else Mf.colors.chip).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        color = if (sel) Mf.colors.onStrong else Mf.colors.text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
}

/** Remplace l'exercice d'une séance en gardant le nombre de séries et les cibles. */
private fun swap(vm: AppViewModel, index: Int, newId: String) = vm.session { s ->
    val ex = s.exercises.getOrNull(index) ?: return@session s
    val sets = ex.sets.filter { it.type != SetType.WARMUP && !it.done }.map { SetLog(UUID.randomUUID().toString(), it.type, rir = it.rir, target = it.target) }
    s.copy(exercises = s.exercises.toMutableList().also { it[index] = ex.copy(id = UUID.randomUUID().toString(), exerciseId = newId, sets = ex.sets.filter { it.done } + sets) })
}

/** Ajoute des exercices à un jour de programme (cible « programId:jour »), 3 séries selon l'objectif du programme. */
private fun addToDay(vm: AppViewModel, target: String, ids: List<String>) {
    val (pid, di) = target.split(':').let { it[0] to it[1].toInt() }
    vm.update { s ->
        s.copy(programs = s.programs.map { p ->
            if (p.id != pid) p else p.copy(days = p.days.mapIndexed { i, d ->
                if (i != di) d else d.copy(exercises = d.exercises + ids.map { id ->
                    val e = vm.db.value?.exercise(id)
                    val t = Generator.baseTarget(p.goal, if (e?.isCompound == true) Generator.Template.SECONDARY else Generator.Template.ACCESSORY)
                    ProgramExercise(UUID.randomUUID().toString(), id, List(p.numCycles.coerceAtLeast(1)) { List(3) { t } })
                })
            })
        })
    }
}

/** Fiche exercice (exercise_info) : angles de caméra, muscles, équipement, technique, notes, historique et records. */
@Composable
fun ExerciseInfoScreen(vm: AppViewModel, id: String, nav: NavHostController) {
    val st by vm.state.collectAsState()
    val db = vm.db.collectAsState().value ?: return
    val e = db.exercise(id) ?: run { DetailScaffold(tr("Exercice introuvable"), nav) {}; return }
    val points = remember(st.history) { Analytics.history(id, st.history, db) }
    val records = Analytics.records(points)
    val cfg = st.exerciseConfig[id] ?: ExerciseConfig()
    var openNote by remember { mutableStateOf<String?>(null) }
    DetailScaffold(e.name, nav, actions = {
        if (db.isCustom(e.id)) IconButton(onClick = { nav.navigate("exerciseedit?id=${e.id}") }) { Icon(Icons.Rounded.Edit, tr("Modifier")) }
    }) {
        if (db.isCustom(e.id)) Text(tr("Exercice personnel"), style = MaterialTheme.typography.labelLarge, color = WorkoutBlue, modifier = Modifier.padding(bottom = 8.dp))
        if (e.images.isEmpty()) {
            val intensity = e.secondaryMuscleGroups.associate { it.key to 0.45f } + e.muscleGroups.associate { it.key to 1f }
            app.superhealth.training.ui.components.BodyMap(intensity, st.settings.sex, Modifier.fillMaxWidth().height(200.dp))
        }
        if (e.images.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOfNotNull(e.preferredImage) + e.images.filter { it != e.preferredImage }).forEach { k ->
                AsyncAsset("ex/${e.id}_${k}.webp", Modifier.width(170.dp).aspectRatio(360f / 640f).clip(RoundedCornerShape(16.dp)))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            e.muscleGroups.forEach { Tag(it.label.tr(), true) }
            e.secondaryMuscleGroups.filter { it !in e.muscleGroups }.forEach { Tag(it.label.tr()) }
        }
        if (points.isNotEmpty()) {
            SectionLabel(tr("Progression"))
            MfCard {
                Row {
                    StatCell(records.bestE1rm?.let { st.settings.estimate(it) } ?: "—", tr("1RM estimé"), Modifier.weight(1f))
                    StatCell(records.heaviest?.let { st.settings.weight(it) } ?: "—", tr("Charge max"), Modifier.weight(1f))
                    StatCell(records.mostReps?.toString() ?: "—", tr("Reps max"), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                LineChart(listOf(Series(points.map { it.time / 86_400_000 to it.e1rm }, WorkoutBlue, dots = points.size < 3)), height = 140.dp, format = { clean(it) })
            }
            GroupCard(Modifier.padding(top = 10.dp)) {
                points.reversed().take(6).forEachIndexed { i, p ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(shortDate(p.time), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(trf("%1\$s séries · max %2\$s · 1RM %3\$s", p.sets, (st.settings.weight(p.heaviest)), (st.settings.estimate(p.e1rm))), style = MaterialTheme.typography.bodySmall,
                            color = Mf.colors.textSecondary)
                    }
                }
            }
        }
        SectionLabel(tr("Caractéristiques"))
        GroupCard {
            InfoRow(tr("Type"), when (e.type) { "compound" -> tr("Polyarticulaire"); "isolation" -> tr("Isolation"); "core" -> tr("Gainage"); else -> e.type })
            RowDivider(); InfoRow(tr("Région"), when (e.region) { "upper" -> tr("Haut du corps"); "lower" -> tr("Bas du corps"); "core" -> tr("Tronc"); else -> tr("Corps entier") })
            RowDivider(); InfoRow(tr("Latéralité"), when (e.laterality) { "unilateral" -> tr("Unilatéral"); "asymmetrical" -> tr("Asymétrique"); else -> tr("Bilatéral") })
            if (e.movementPatterns.isNotEmpty()) { RowDivider(); InfoRow(tr("Mouvement"), e.movementPatterns.joinToString(", ")) }
            val eq = db.resistanceEquipment(e, vm.activeGym()?.equipmentIds)
            if (eq.isNotEmpty()) { RowDivider(); InfoRow(tr("Équipement"), eq.joinToString(", ") { it.name }) }
            if (e.primaryMuscles.isNotEmpty()) { RowDivider(); InfoRow(tr("Muscles"), e.primaryMuscles.joinToString(", ")) }
        }
        SectionLabel(tr("Réglages"))
        GroupCard {
            SwitchRow(tr("Ne pas recommander"), tr("Exclu de la génération de programmes et des remplacements"), cfg.doNotRecommend) { v ->
                vm.update { s -> s.copy(exerciseConfig = s.exerciseConfig + (id to cfg.copy(doNotRecommend = v))) }
            }
        }
        if (e.hasGuide) Assets.text("md/${e.id}.md")?.let { md ->
            SectionLabel(tr("Technique"))
            MfCard { MarkdownText(md) }
        }
        if (e.notes.isNotEmpty()) {
            SectionLabel(tr("Notes"))
            GroupCard {
                e.notes.forEachIndexed { i, n ->
                    if (i > 0) RowDivider()
                    Column(Modifier.fillMaxWidth().clickable { openNote = if (openNote == n[0]) null else n[0] }.padding(16.dp)) {
                        Text(n.getOrElse(1) { "" }, style = MaterialTheme.typography.titleSmall)
                        if (openNote == n[0]) Assets.text("md/${n[0]}.md")?.let { MarkdownText(it, Modifier.padding(top = 8.dp)) }
                    }
                }
            }
        }
        Text(tr("Contenu : MacroFactor Workouts (usage personnel)."), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
        Text(k, style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.width(110.dp))
        Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
