package app.superhealth.ui.stretch

import app.superhealth.platform.*

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.RowDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.stretch.Stretch
import app.superhealth.ui.coach.askCoach
import app.superhealth.ui.coach.coachAvailable
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.ui.wellbeing.KeepScreenOn
import kotlinx.coroutines.delay

internal val StretchHue = Color(0xFF3BBFA0)

/** Vignette d'un exercice, depuis le plugin. */
@Composable
internal fun StretchThumb(id: String, modifier: Modifier) {
    val bmp = remember(id) { thumbCache.getOrPut(id) { Stretch.thumb(id)?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() } } }
    if (bmp != null) Image(bmp, null, modifier.clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
    else Box(modifier.clip(RoundedCornerShape(12.dp)).background(Mf.colors.chip))
}
private val thumbCache = HashMap<String, ImageBitmap?>()

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) =
    Text(tr(label), style = MaterialTheme.typography.labelLarge, maxLines = 1, color = if (on) StretchHue.onContainer() else Mf.colors.text,
        modifier = Modifier.clip(CircleShape).background(if (on) StretchHue.container() else Mf.colors.chip).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp))

/** Tuile photo, comme GOWOD : l'image remplit la tuile, le titre se lit sur un dégradé. */
@Composable
private fun PhotoTile(thumb: String?, title: String, subtitle: String?, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(18.dp)).then(if (on) Modifier.border(2.5.dp, StretchHue, RoundedCornerShape(18.dp)) else Modifier).clickable(onClick = onClick)) {
        if (thumb != null) StretchThumb(thumb, Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize().background(Mf.colors.chip))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))))
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Situation du quotidien : une routine toute faite, d'un toucher. */
private class Situation(val label: String, val kind: Stretch.Kind, val groups: Set<Stretch.Group>)
private val situations = listOf(
    Situation("Bureau", Stretch.Kind.DAILY, setOf(Stretch.Group.SHOULDERS, Stretch.Group.BACK, Stretch.Group.HIPS)),
    Situation("Réveil", Stretch.Kind.DAILY, emptySet()),
    Situation("Sommeil", Stretch.Kind.POST, setOf(Stretch.Group.BACK, Stretch.Group.HIPS, Stretch.Group.THIGHS)),
    Situation("Course", Stretch.Kind.POST, setOf(Stretch.Group.THIGHS, Stretch.Group.LOWER_LEGS, Stretch.Group.HIPS)),
    Situation("Haut du corps", Stretch.Kind.DAILY, setOf(Stretch.Group.SHOULDERS, Stretch.Group.CHEST, Stretch.Group.ARMS)),
    Situation("Bas du corps", Stretch.Kind.DAILY, setOf(Stretch.Group.HIPS, Stretch.Group.THIGHS, Stretch.Group.LOWER_LEGS)),
)

@Composable
private fun ChipRow(content: @Composable () -> Unit) =
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }

/**
 * Section Étirements, d'après l'organisation de GOWOD : le moment en tuiles photo, puis tout le réglage sur la même carte, en rangées qui défilent
 * au lieu de s'empiler ; les situations d'un toucher ; le test et la bibliothèque côte à côte.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.StretchSection(nav: NavHostController) {
    val sh by SuperPrefs.settings.collectAsState()
    val presetGroups by preset.collectAsState()
    val results by Stretch.results.collectAsState()
    var kind by rememberSaveable { mutableStateOf(Stretch.Kind.DAILY) }
    var minutes by rememberSaveable { mutableIntStateOf(10) }
    var groups by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var gear by remember { mutableStateOf(false) }
    LaunchedEffect(presetGroups) { presetGroups?.let { p -> kind = Stretch.Kind.POST; groups = p.map { it.name }; preset.value = null } }
    val chosen = groups.mapNotNull { n -> Stretch.Group.entries.firstOrNull { it.name == n } }.toSet()
    val routine = remember(kind, minutes, groups, sh.stretchGears) { Stretch.routine(kind, chosen, minutes, sh.stretchGears) }
    val last = results.lastOrNull()
    val saved by Stretch.saved.collectAsState()
    val ctx = LocalContext.current
    var allSaved by remember { mutableStateOf(false) }
    var deletingSaved by remember { mutableStateOf<String?>(null) }
    PillButton(tr("Composer ma séance"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.Add) {
        Stretch.prepare(); nav.go("stretch/edit")
    }
    Spacer(Modifier.height(10.dp))
    if (saved.isNotEmpty()) {
        SectionLabel(tr("Mes séances"))
        GroupCard {
            (if (allSaved) saved else saved.take(4)).forEachIndexed { i, personal ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { Stretch.prepare(personal); nav.go("stretch/edit") }.padding(vertical = 10.dp)) {
                        Text(personal.name, style = MaterialTheme.typography.titleSmall)
                        Text(trf("%1\$s exercices · %2\$s", personal.items.size, stretchTime(personal.seconds)), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                    IconButton(onClick = { Stretch.current.value = personal; nav.go("stretch/play") }) { Icon(Icons.Rounded.PlayArrow, tr("Lancer")) }
                    IconButton(onClick = { deletingSaved = personal.id }) { Icon(Icons.Rounded.DeleteOutline, tr("Supprimer")) }
                }
            }
            if (saved.size > 4) TextButton(onClick = { allSaved = !allSaved }) { Text(tr(if (allSaved) "Réduire" else "Tout voir")) }
        }
        Spacer(Modifier.height(12.dp))
    }
    deletingSaved?.let { id -> AlertDialog(onDismissRequest = { deletingSaved = null }, title = { Text(tr("Supprimer cette séance ?")) },
        confirmButton = { TextButton(onClick = { Stretch.removeSaved(ctx, id); deletingSaved = null }) { Text(tr("Supprimer")) } },
        dismissButton = { TextButton(onClick = { deletingSaved = null }) { Text(tr("Annuler")) } }) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stretch.Kind.entries.forEach { k ->
            val cover = remember(k, sh.stretchGears) { Stretch.routine(k, emptySet(), 5, sh.stretchGears).items.firstOrNull()?.id }
            PhotoTile(cover, tr(k.label), null, k == kind, Modifier.weight(1f).height(112.dp)) { kind = k }
        }
    }
    Spacer(Modifier.height(10.dp))
    MfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (chosen.isEmpty()) tr("Tout le corps") else chosen.joinToString(", ") { tr(it.label) }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(trf("%1\$s exercices · %2\$s min", routine.items.size, routine.seconds / 60), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
            Text(tr("Matériel"), Modifier.clip(CircleShape).clickable { gear = true }.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, color = Mf.colors.accent)
        }
        Spacer(Modifier.height(10.dp))
        ChipRow { listOf(5, 10, 15, 20, 30).forEach { m -> Chip(trf("%1\$s min", m), m == minutes) { minutes = m } } }
        Spacer(Modifier.height(6.dp))
        ChipRow {
            Chip("Tout", groups.isEmpty()) { groups = emptyList() }
            Stretch.Group.entries.forEach { g -> Chip(g.label, g.name in groups) { groups = if (g.name in groups) groups - g.name else groups + g.name } }
        }
        if (routine.items.isEmpty()) Text(tr("Aucun exercice pour ce choix : retirez une zone ou ajoutez du matériel."), color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
        else {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                routine.items.forEach { e -> StretchThumb(e.id, Modifier.size(62.dp).clickable { nav.go("stretch/ex/${e.id}") }) }
            }
            Spacer(Modifier.height(12.dp))
            PillButton(tr("Personnaliser cette sélection"), Modifier.fillMaxWidth(), icon = Icons.Rounded.Tune) { Stretch.prepare(routine); nav.go("stretch/edit") }
            TextButton(onClick = { Stretch.current.value = routine; nav.go("stretch/play") }, modifier = Modifier.align(Alignment.End)) { Text(tr("Lancer directement")) }
        }
    }
    SectionLabel(tr("Situations"))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        situations.forEach { s ->
            val r = remember(s, minutes, sh.stretchGears) { Stretch.routine(s.kind, s.groups, minutes, sh.stretchGears) }
            PhotoTile(r.items.firstOrNull()?.id, tr(s.label), trf("%1\$s min", r.seconds / 60), false, Modifier.width(138.dp).height(92.dp)) {
                if (r.items.isNotEmpty()) { Stretch.prepare(r.copy(name = tr(s.label))); nav.go("stretch/edit") }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MfCard(Modifier.weight(1f), onClick = { nav.go("stretch/test") }) {
            Text(tr("Test de mobilité"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(last?.let { "${Stretch.globalScore(it) ?: 0}/100" } ?: tr("8 tests, 5 min"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
        }
        MfCard(Modifier.weight(1f), onClick = { nav.go("stretch/all") }) {
            Text(tr("Bibliothèque"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(trf("%1\$s exercices", Stretch.exercises().size), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
        }
    }
    if (coachAvailable()) {
        Spacer(Modifier.height(10.dp))
        PillButton(tr("Demander au coach"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.AutoAwesome) {
            nav.askCoach(tr("Regarde mes dernières séances et mon sommeil, puis propose-moi une routine d'étirements adaptée à aujourd'hui."))
        }
    }
    if (gear) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { gear = false }, containerColor = Mf.colors.background) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Text(tr("Matériel"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Stretch.Gear.entries.forEach { g ->
                    Chip(g.label, g.key in sh.stretchGears) {
                        SuperPrefs.update { it.copy(stretchGears = if (g.key in it.stretchGears) it.stretchGears - g.key else it.stretchGears + g.key) }
                    }
                }
            }
        }
    }
}

/** Zones préremplies en venant d'une séance terminée. */
val preset = kotlinx.coroutines.flow.MutableStateFlow<Set<Stretch.Group>?>(null)

@Composable
private fun ExRow(e: Stretch.Ex, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        StretchThumb(e.id, Modifier.size(62.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(zoneLabel(e).ifEmpty { null }, if (e.uni) tr("par côté") else null).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
    }
}

private fun zoneLabel(e: Stretch.Ex) = Stretch.Group.entries.filter { g -> e.zones.any { it in g.zones } }.take(2).joinToString(", ") { tr(it.label) }

/** Bibliothèque : recherche et filtres fixes en haut, la liste défile dessous. */
@Composable
fun StretchAllScreen(nav: NavHostController) {
    var q by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    val sh by SuperPrefs.settings.collectAsState()
    val list = remember(q, group, favOnly, sh.stretchFavorites) {
        val g = Stretch.Group.entries.firstOrNull { it.name == group }
        Stretch.exercises().filter { e ->
            (g == null || e.zones.any { it in g.zones }) && (q.isBlank() || e.name.contains(q, true) || e.steps.any { it.contains(q, true) }) && (!favOnly || e.id in sh.stretchFavorites)
        }
    }
    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding()) {
        app.superhealth.nutrition.ui.components.NavBar(tr("Bibliothèque"), { nav.popBackStack() }) {}
        Column(Modifier.padding(horizontal = 16.dp)) {
            app.superhealth.nutrition.ui.components.TextInput(tr("Rechercher un exercice"), q, { q = it })
            Spacer(Modifier.height(8.dp))
            ChipRow {
                Chip("Favoris", favOnly) { favOnly = !favOnly }
                Stretch.Group.entries.forEach { g -> Chip(g.label, g.name == group) { group = if (g.name == group) null else g.name } }
            }
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            items(list, key = { it.id }) { e -> ExRow(e) { nav.go("stretch/ex/${e.id}") } }
        }
    }
}

/** Fiche d'un exercice : la vidéo d'un bord à l'autre, le nom, la durée, le ressenti et les consignes, le bouton en bas. */
@Composable
fun StretchExerciseScreen(nav: NavHostController, id: String) {
    val e = remember(id) { Stretch.exercise(id) } ?: return
    val sh by SuperPrefs.settings.collectAsState()
    val fav = id in sh.stretchFavorites
    var seconds by rememberSaveable(id) { mutableIntStateOf(e.sideSeconds) }
    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            StretchVideo(e.id, Modifier.fillMaxSize())
            OverlayButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour"), Modifier.align(Alignment.TopStart).padding(8.dp)) { nav.popBackStack() }
            OverlayButton(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, tr("Favoris"), Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                SuperPrefs.update { it.copy(stretchFavorites = if (fav) it.stretchFavorites - id else it.stretchFavorites + id) }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(e.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(zoneLabel(e), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            Spacer(Modifier.height(10.dp))
            ChipRow { listOf(15, 30, 45, 60, 90, 120, 180).forEach { sec -> Chip(stretchTime(sec) + if (e.uni) " × 2" else "", sec == seconds) { seconds = sec } } }
            Spacer(Modifier.height(10.dp))
            Steps(e)
            TextButton(onClick = {
                val draft = Stretch.draft.value ?: Stretch.Routine("", Stretch.Kind.DAILY, emptyList())
                Stretch.prepare(draft.copy(items = draft.items + e.copy(secondsPerSide = seconds))); nav.go("stretch/edit")
            }, modifier = Modifier.fillMaxWidth()) { Text(tr("Ajouter à ma séance")) }
        }
        PillButton(tr("Lancer"), Modifier.padding(16.dp).fillMaxWidth(), icon = Icons.Rounded.PlayArrow) {
            Stretch.current.value = Stretch.Routine(e.name, Stretch.Kind.DAILY, listOf(e.copy(secondsPerSide = seconds))); nav.go("stretch/play")
        }
    }
}

/** Bouton rond posé sur la vidéo. */
@Composable
private fun OverlayButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Steps(e: Stretch.Ex) {
    e.feel?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = StretchHue.onContainer(), modifier = Modifier.padding(bottom = 8.dp)) }
    e.steps.forEachIndexed { i, s ->
        Row(Modifier.padding(vertical = 3.dp)) {
            Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary, modifier = Modifier.width(20.dp))
            Text(s, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Lecture d'une routine : vidéo en haut, temps restant, consignes, changement de côté signalé par une vibration. */
@Composable
fun StretchPlayScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val routine by Stretch.current.collectAsState()
    val r = routine?.takeIf { it.items.isNotEmpty() } ?: return
    val haptic = LocalHapticFeedback.current
    val lifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val foreground = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    var index by rememberSaveable { mutableIntStateOf(0) }
    var left by rememberSaveable { mutableIntStateOf(r.items.first().seconds) }
    var running by rememberSaveable { mutableStateOf(true) }
    var spent by rememberSaveable { mutableIntStateOf(0) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var swapping by remember { mutableStateOf(false) }
    var resumeAfterSwap by remember { mutableStateOf(false) }
    if (running && foreground && !finished) KeepScreenOn()
    val e = r.items[index.coerceIn(0, r.items.lastIndex)]
    // fermer ou revenir en arrière garde le temps déjà fait
    fun close() { if (!finished && spent >= 30) Stretch.record(ctx, r, spent); Stretch.current.value = null; nav.popBackStack() }
    BackHandler { close() }
    fun next() {
        if (index >= r.items.lastIndex) { finished = true; Stretch.record(ctx, r, spent) }
        else { index++; left = r.items[index].seconds; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    LaunchedEffect(running, finished, foreground, index) {
        while (running && !finished && foreground) {
            delay(1000)
            left--; spent++
            if (e.uni && left == e.seconds / 2) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (left <= 0) next()
        }
    }
    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding().navigationBarsPadding()) {
        if (finished) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(tr("Routine terminée"), style = MaterialTheme.typography.headlineSmall)
                Text(trf("%1\$s min", (spent + 30) / 60), style = MaterialTheme.typography.displaySmall, color = StretchHue.onContainer(), modifier = Modifier.padding(vertical = 12.dp))
                PillButton(tr("Terminer"), Modifier.fillMaxWidth()) { Stretch.current.value = null; nav.popBackStack() }
            }
            return@Column
        }
        // la vidéo d'un bord à l'autre, le temps de la routine juste dessous : rien ne se perd autour
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            StretchVideo(e.id, Modifier.fillMaxSize(), running, onPlayingChange = { running = it }, status = "%d:%02d".format(left / 60, left % 60))
            OverlayButton(Icons.Rounded.Close, tr("Fermer"), Modifier.align(Alignment.TopStart).padding(8.dp)) { close() }
            Text(trf("%1\$s sur %2\$s", index + 1, r.items.size), style = MaterialTheme.typography.labelLarge, color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 5.dp))
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(Mf.colors.chip)) {
            Box(Modifier.fillMaxWidth(((r.items.take(index).sumOf { it.seconds } + e.seconds - left).toFloat() / r.seconds.coerceAtLeast(1)).coerceIn(0f, 1f)).height(4.dp).background(StretchHue))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(e.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(if (e.uni) tr(if (left > e.seconds / 2) "Premier côté" else "Second côté") else null, zoneLabel(e).ifEmpty { null }).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge, color = StretchHue.onContainer(), modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            Steps(e)
            TextButton(onClick = { resumeAfterSwap = running; running = false; swapping = true }) { Text(tr("Remplacer cet exercice")) }
        }
        r.items.getOrNull(index + 1)?.let { n ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                StretchThumb(n.id, Modifier.size(36.dp))
                Spacer(Modifier.width(10.dp))
                Text(trf("Ensuite : %1\$s", n.name), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("%d:%02d".format(left / 60, left % 60), style = MaterialTheme.typography.displaySmall, fontSize = 38.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { left = e.seconds }) { Icon(Icons.Rounded.Refresh, tr("Recommencer"), tint = Mf.colors.text) }
            Box(Modifier.padding(horizontal = 8.dp).size(64.dp).clip(CircleShape).background(StretchHue).clickable { running = !running }, contentAlignment = Alignment.Center) {
                Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tr(if (running) "Pause" else "Reprendre"), tint = Color.White, modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = { next() }) { Icon(Icons.Rounded.SkipNext, tr("Suivant"), tint = Mf.colors.text) }
        }
    }
    if (swapping) StretchExercisePicker(r.kind, e, onDismiss = { swapping = false; running = resumeAfterSwap }) { replacement ->
        val updated = Stretch.replace(r, index, replacement)
        Stretch.current.value = updated
        left = updated.items[index].seconds
    }
}
