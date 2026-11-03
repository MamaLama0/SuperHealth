package app.superhealth.ui.stretch

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.stretch.Stretch
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.coach.askCoach
import app.superhealth.ui.coach.coachAvailable
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.util.UUID

internal fun stretchTime(seconds: Int) = if (seconds < 60) trf("%1\$s s", seconds) else "%d:%02d".format(seconds / 60, seconds % 60)
internal fun stretchType(type: String) = tr(when (type) {
    "ActiveStretch" -> "Étirement actif"; "PassiveStretch" -> "Étirement passif"; "MuscleActivation" -> "Activation"; "Massage" -> "Auto-massage"; else -> type
})
internal fun stretchZone(zone: String) = tr(when (zone) {
    "Shoulders" -> "Épaules"; "Rotators" -> "Rotateurs"; "Traps" -> "Trapèzes"; "Pecs" -> "Pectoraux"; "Lats" -> "Grand dorsal"
    "LumbarMuscles" -> "Lombaires"; "Abs" -> "Abdominaux"; "Obliques" -> "Obliques"; "Biceps" -> "Biceps"; "Triceps" -> "Triceps"
    "Flexors" -> "Fléchisseurs du poignet"; "Extensors" -> "Extenseurs du poignet"; "Hips" -> "Hanches"; "Glutes" -> "Fessiers"
    "Adductors" -> "Adducteurs"; "Quads" -> "Quadriceps"; "Hamstrings" -> "Ischio-jambiers"; "ItBands" -> "Face externe de la cuisse"
    "Calves" -> "Mollets"; "Ankles" -> "Chevilles"; "Feet" -> "Pieds"; "Shins", "PostShins" -> "Tibias"; else -> zone
})

/** Même bibliothèque pour l'ajout, la préparation après séance et le remplacement pendant la lecture. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StretchExercisePicker(kind: Stretch.Kind, replacement: Stretch.Ex? = null, onDismiss: () -> Unit, onSelect: (Stretch.Ex) -> Unit) {
    val sh by SuperPrefs.settings.collectAsState()
    val custom by Stretch.customExercises.collectAsState()
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<Stretch.Group?>(null) }
    var sameType by remember { mutableStateOf(replacement != null) }
    var sameZone by remember { mutableStateOf(replacement != null) }
    var availableOnly by remember { mutableStateOf(true) }
    var favoritesOnly by remember { mutableStateOf(false) }
    val list = remember(query, group, sameType, sameZone, availableOnly, favoritesOnly, sh.stretchGears, sh.stretchFavorites, custom) {
        Stretch.exercises().filter { e ->
            e.id != replacement?.id && (query.isBlank() || e.name.contains(query, true) || e.steps.any { it.contains(query, true) }) &&
                (group == null || e.zones.any { it in group!!.zones }) && (!availableOnly || e.gears.all { it in sh.stretchGears }) &&
                (!favoritesOnly || e.id in sh.stretchFavorites) && (!sameType || replacement == null || e.type == replacement.type) &&
                (!sameZone || replacement == null || e.zones.any { it in replacement.zones })
        }.sortedWith(compareByDescending<Stretch.Ex> { it.id in sh.stretchFavorites }.thenBy { it.name })
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Mf.colors.background, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            Text(tr(if (replacement == null) "Choisir un exercice" else "Remplacer l'exercice"), style = MaterialTheme.typography.titleLarge)
            TextInput(tr("Rechercher un exercice"), query, { query = it })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = group == null, onClick = { group = null }, label = { Text(tr("Tout le corps")) })
                Stretch.Group.entries.forEach { g -> FilterChip(selected = group == g, onClick = { group = if (group == g) null else g }, label = { Text(tr(g.label)) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(availableOnly, { availableOnly = !availableOnly }, label = { Text(tr("Mon matériel")) })
                FilterChip(favoritesOnly, { favoritesOnly = !favoritesOnly }, label = { Text(tr("Favoris")) })
                if (replacement != null) {
                    FilterChip(sameType, { sameType = !sameType }, label = { Text(tr("Même type")) })
                    FilterChip(sameZone, { sameZone = !sameZone }, label = { Text(tr("Mêmes muscles")) })
                }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                if (list.isEmpty()) item { Text(tr("Aucun exercice : élargissez les filtres ou le matériel."), color = Mf.colors.textSecondary, modifier = Modifier.padding(12.dp)) }
                items(list, key = { it.id }) { e ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(e); onDismiss() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StretchThumb(e.id, Modifier.size(48.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(e.name, style = MaterialTheme.typography.bodyLarge)
                            Text(stretchType(e.type) + " · " + e.zones.take(2).joinToString { stretchZone(it) }, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                        }
                        Icon(Icons.Rounded.Add, tr("Choisir"), Modifier.size(22.dp), tint = Mf.colors.accent)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StretchEditorScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val sh by SuperPrefs.settings.collectAsState()
    val draft by Stretch.draft.collectAsState()
    val custom by Stretch.customExercises.collectAsState()
    val r = draft ?: Stretch.Routine("", Stretch.Kind.DAILY, emptyList())
    var minutes by remember { mutableIntStateOf(r.plannedMinutes ?: sh.stretchRoutineMinutes) }
    var seconds by remember { mutableIntStateOf(r.defaultSeconds ?: sh.stretchSecondsPerSide) }
    var groups by remember { mutableStateOf(r.focusGroups) }
    var zones by remember { mutableStateOf(r.focusZones) }
    var choosing by remember { mutableStateOf(false) }
    var swap by remember { mutableStateOf<Int?>(null) }
    var duration by remember { mutableStateOf<Int?>(null) }
    var gear by remember { mutableStateOf(false) }
    var confirmGenerate by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Int?>(null) }
    fun update(next: Stretch.Routine) { Stretch.draft.value = next }
    fun generate() {
        val next = Stretch.routine(r.kind, groups, minutes, sh.stretchGears, zones = zones, favorites = sh.stretchFavorites, disliked = sh.stretchDisliked, secondsPerSide = seconds)
        update(next.copy(name = r.name.ifBlank { next.name }, id = r.id))
    }
    DetailScaffold(tr("Ma séance d'étirements"), { nav.popBackStack() }) {
        MfCard {
            TextInput(tr("Nom de la séance"), r.name, { update(r.copy(name = it.take(80))) })
            ChipChoice(Stretch.Kind.entries, r.kind, { tr(it.label) }) { update(r.copy(kind = it)) }
            Text(trf("%1\$s exercices · %2\$s", r.items.size, stretchTime(r.seconds)), style = MaterialTheme.typography.titleMedium)
        }
        SectionLabel(tr("Créer à partir de mes besoins"))
        MfCard {
            Text(tr("Choisissez une zone, puis éventuellement des muscles précis."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                FilterChip(groups.isEmpty(), { groups = emptySet(); zones = emptySet() }, label = { Text(tr("Tout le corps")) })
                Stretch.Group.entries.forEach { g -> FilterChip(g in groups, {
                    groups = if (g in groups) groups - g else groups + g
                    zones = zones.intersect(groups.flatMap { it.zones }.toSet())
                }, label = { Text(tr(g.label)) }) }
            }
            if (groups.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                groups.flatMap { it.zones }.distinct().forEach { zone -> FilterChip(zone in zones, { zones = if (zone in zones) zones - zone else zones + zone }, label = { Text(stretchZone(zone)) }) }
            }
            Text(tr("Durée de la séance"), style = MaterialTheme.typography.labelLarge)
            ChipChoice(listOf(1, 3, 5, 10, 15, 20, 30), minutes, { trf("%1\$s min", it) }) {
                minutes = it; SuperPrefs.update { s -> s.copy(stretchRoutineMinutes = it) }
            }
            Text(tr("Durée par exercice et par côté"), style = MaterialTheme.typography.labelLarge)
            ChipChoice(listOf(15, 30, 45, 60, 90, 120), seconds, { stretchTime(it) }) {
                seconds = it; SuperPrefs.update { s -> s.copy(stretchSecondsPerSide = it) }
            }
            SettingRow(tr("Mon matériel"), subtitle = sh.stretchGears.mapNotNull { key -> Stretch.Gear.entries.firstOrNull { it.key == key }?.label?.let(::tr) }.joinToString().ifBlank { tr("Sans matériel") }) { gear = true }
            PillButton(tr("Générer ma sélection"), Modifier.fillMaxWidth(), strong = false) { if (r.items.isEmpty()) generate() else confirmGenerate = true }
        }
        val preview = Stretch.musclesFor(r.items.flatMap { it.zones }.ifEmpty { zones.ifEmpty { groups.flatMap { it.zones }.toSet() }.toList() })
        if (preview.isNotEmpty()) MfCard {
            Text(tr("Muscles ciblés"), style = MaterialTheme.typography.titleSmall)
            val training by app.superhealth.training.WorkoutApp.store.state.collectAsState()
            app.superhealth.training.ui.components.BodyMap(preview.associate { it.key to 1f }, training.settings.sex,
                Modifier.fillMaxWidth().height(160.dp))
            Text(preview.joinToString { tr(it.label) }, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        SectionLabel(tr("Mes exercices"))
        if (r.items.isEmpty()) Text(tr("Ajoutez vos exercices ou générez une première sélection."), color = Mf.colors.textSecondary)
        r.items.forEachIndexed { i, e ->
            MfCard(Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StretchThumb(e.id, Modifier.size(46.dp).clickable { nav.go("stretch/ex/${e.id}") })
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text("${i + 1}. ${e.name}", style = MaterialTheme.typography.titleSmall)
                        Text(stretchType(e.type), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { duration = i }) { Text(stretchTime(e.sideSeconds) + if (e.uni) " × 2" else "") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { swap = i }) { Icon(Icons.Rounded.SwapHoriz, tr("Remplacer l'exercice")) }
                    Box {
                        IconButton(onClick = { menu = i }) { Icon(Icons.Rounded.MoreVert, tr("Options de l'exercice")) }
                        DropdownMenu(menu == i, { menu = null }) {
                            if (i > 0) DropdownMenuItem({ Text(tr("Monter")) }, onClick = { update(r.copy(items = r.items.toMutableList().also { java.util.Collections.swap(it, i, i - 1) })); menu = null })
                            if (i < r.items.lastIndex) DropdownMenuItem({ Text(tr("Descendre")) }, onClick = { update(r.copy(items = r.items.toMutableList().also { java.util.Collections.swap(it, i, i + 1) })); menu = null })
                            DropdownMenuItem({ Text(tr("Dupliquer")) }, onClick = { update(r.copy(items = r.items.toMutableList().also { it.add(i + 1, e) })); menu = null })
                            DropdownMenuItem({ Text(tr("Retirer")) }, onClick = { update(r.copy(items = r.items.filterIndexed { index, _ -> index != i })); menu = null })
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(tr("Ajouter un exercice"), Modifier.weight(1f), strong = false) { choosing = true }
            PillButton(tr("Créer mon exercice"), Modifier.weight(1f), strong = false) { nav.go("stretch/custom") }
        }
        if (r.items.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PillButton(tr("Lancer ma séance"), Modifier.fillMaxWidth()) { Stretch.current.value = r.copy(name = r.name.ifBlank { tr("Ma séance d'étirements") }); nav.go("stretch/play") }
            TextButton(enabled = r.name.isNotBlank(), onClick = {
                update(Stretch.save(ctx, r)); Toast.makeText(ctx, tr("Séance enregistrée"), Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth()) { Text(tr("Enregistrer dans mes séances")) }
        }
        if (coachAvailable()) PillButton(tr("Adapter avec le coach"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.AutoAwesome) {
            Stretch.draft.value = r
            nav.askCoach(tr("Aide-moi à personnaliser ma séance d'étirements en préparation. Consulte ma sélection, mon matériel et mes dernières séances si je les partage. Demande les précisions utiles, puis propose une version modifiable. Respecte mes durées et les exercices que je veux conserver."))
        }
    }
    if (choosing || swap != null) StretchExercisePicker(r.kind, swap?.let { r.items.getOrNull(it) }, onDismiss = { choosing = false; swap = null }) { e ->
        val index = swap
        update(if (index != null) Stretch.replace(r, index, e) else r.copy(items = r.items + e.copy(secondsPerSide = seconds)))
    }
    duration?.let { i ->
        var value by remember(i) { mutableStateOf(r.items[i].sideSeconds.toString()) }
        AlertDialog(onDismissRequest = { duration = null }, title = { Text(tr("Durée par côté")) }, text = {
            NumberField(tr("Secondes"), value, { value = it }, suffix = "s")
        }, confirmButton = { TextButton(enabled = (value.toIntOrNull() ?: 0) in 15..600, onClick = {
            update(r.copy(items = r.items.toMutableList().also { it[i] = it[i].copy(secondsPerSide = value.toInt()) })); duration = null
        }) { Text(tr("Enregistrer")) } }, dismissButton = { TextButton(onClick = { duration = null }) { Text(tr("Annuler")) } })
    }
    if (gear) ModalBottomSheet(onDismissRequest = { gear = false }, containerColor = Mf.colors.background) {
        Column(Modifier.padding(16.dp).navigationBarsPadding()) {
            Text(tr("Mon matériel"), style = MaterialTheme.typography.titleLarge)
            Stretch.Gear.entries.forEach { g -> SwitchRow(tr(g.label), null, g.key in sh.stretchGears) { on -> SuperPrefs.update { it.copy(stretchGears = if (on) it.stretchGears + g.key else it.stretchGears - g.key) } } }
        }
    }
    if (confirmGenerate) AlertDialog(onDismissRequest = { confirmGenerate = false }, title = { Text(tr("Remplacer la sélection ?")) },
        text = { Text(tr("Vos exercices actuels seront remplacés par la nouvelle sélection. Vos séances enregistrées restent conservées.")) },
        confirmButton = { TextButton(onClick = { generate(); confirmGenerate = false }) { Text(tr("Générer")) } },
        dismissButton = { TextButton(onClick = { confirmGenerate = false }) { Text(tr("Annuler")) } })
}

@Composable
fun CustomStretchScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(Stretch.Group.SHOULDERS) }
    var selectedZones by remember { mutableStateOf(group.zones) }
    var type by remember { mutableStateOf("PassiveStretch") }
    var uni by remember { mutableStateOf(false) }
    DetailScaffold(tr("Créer mon exercice"), { nav.popBackStack() }) {
        MfCard {
            TextInput(tr("Nom de l'exercice"), name, { name = it.take(100) })
            ChipChoice(Stretch.Group.entries, group, { tr(it.label) }) { group = it; selectedZones = it.zones }
            ChipChoice(group.zones.toList(), "", { (if (it in selectedZones) "✓ " else "") + stretchZone(it) }) { zone -> selectedZones = if (zone in selectedZones) selectedZones - zone else selectedZones + zone }
            ChipChoice(listOf("ActiveStretch", "PassiveStretch", "MuscleActivation", "Massage"), type, ::stretchType) { type = it }
            SwitchRow(tr("Deux côtés à faire"), null, uni) { uni = it }
            OutlinedTextField(instructions, { instructions = it.take(3000) }, label = { Text(tr("Mes consignes")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
        }
        PillButton(tr("Ajouter à ma séance"), Modifier.fillMaxWidth(), enabled = name.isNotBlank() && instructions.isNotBlank() && selectedZones.isNotEmpty()) {
            val e = Stretch.Ex("custom-" + UUID.randomUUID(), name.trim(), type, zones = selectedZones.toList(), uni = uni,
                daily = true, pre = type != "PassiveStretch", post = true, fr = instructions.lines().filter { it.isNotBlank() }, en = instructions.lines().filter { it.isNotBlank() }, secondsPerSide = SuperPrefs.settings.value.stretchSecondsPerSide)
            Stretch.saveExercise(ctx, e)
            val r = Stretch.draft.value ?: Stretch.Routine("", Stretch.Kind.DAILY, emptyList())
            Stretch.draft.value = r.copy(items = r.items + e)
            nav.popBackStack()
        }
    }
}
