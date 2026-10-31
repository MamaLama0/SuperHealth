package app.superhealth.ui.stretch

import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.Segmented
import app.superhealth.nutrition.ui.screens.DetailScaffold
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
    val bmp = remember(id) { thumbCache.getOrPut(id) { Stretch.thumb(id)?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() } } }
    if (bmp != null) Image(bmp, null, modifier.clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
    else Box(modifier.clip(RoundedCornerShape(12.dp)).background(Mf.colors.chip))
}
private val thumbCache = HashMap<String, ImageBitmap?>()

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) =
    Text(tr(label), style = MaterialTheme.typography.labelLarge, maxLines = 1, color = if (on) StretchHue.onContainer() else Mf.colors.text,
        modifier = Modifier.clip(CircleShape).background(if (on) StretchHue.container() else Mf.colors.chip).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp))

/**
 * Section Étirements, comme l'accueil de GOWOD : la routine du jour d'abord, un seul bouton pour la lancer. Moment, durée, zones et matériel
 * sont dans une feuille ; le test de mobilité et le catalogue tiennent en deux lignes.
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
    var settings by remember { mutableStateOf(false) }
    LaunchedEffect(presetGroups) { presetGroups?.let { p -> kind = Stretch.Kind.POST; groups = p.map { it.name }; preset.value = null } }
    val chosen = groups.mapNotNull { n -> Stretch.Group.entries.firstOrNull { it.name == n } }.toSet()
    val routine = remember(kind, minutes, groups, sh.stretchGears) { Stretch.routine(kind, chosen, minutes, sh.stretchGears) }
    val last = results.lastOrNull()

    MfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (chosen.isEmpty()) tr(kind.label) else chosen.joinToString(", ") { tr(it.label) }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(trf("%1\$s exercices · %2\$s min", routine.items.size, routine.seconds / 60), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
            Text(tr("Modifier"), Modifier.clip(CircleShape).clickable { settings = true }.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, color = Mf.colors.accent)
        }
        if (routine.items.isEmpty()) Text(tr("Aucun exercice pour ce choix : retirez une zone ou ajoutez du matériel."), color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
        else {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                routine.items.take(5).forEach { e -> StretchThumb(e.id, Modifier.weight(1f).aspectRatio(1f).clickable { nav.go("stretch/ex/${e.id}") }) }
            }
            Spacer(Modifier.height(12.dp))
            PillButton(tr("Lancer"), Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow) { Stretch.current.value = routine; nav.go("stretch/play") }
        }
    }
    Spacer(Modifier.height(10.dp))
    GroupCard {
        app.superhealth.nutrition.ui.components.SettingRow(tr("Test de mobilité"), last?.let { "${Stretch.globalScore(it) ?: 0}/100" }, subtitle = if (last == null) tr("8 tests, 5 minutes : votre score et vos points faibles.") else null) { nav.go("stretch/test") }
        RowDivider()
        app.superhealth.nutrition.ui.components.SettingRow(tr("Tous les exercices")) { nav.go("stretch/all") }
        if (coachAvailable()) {
            RowDivider()
            app.superhealth.nutrition.ui.components.SettingRow(tr("Demander au coach"), icon = Icons.Rounded.AutoAwesome) {
                nav.askCoach(tr("Regarde mes dernières séances et mon sommeil, puis propose-moi une routine d'étirements adaptée à aujourd'hui."))
            }
        }
    }
    if (settings) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { settings = false }, containerColor = Mf.colors.background) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Segmented(Stretch.Kind.entries.map { tr(it.label) }, kind.ordinal) { kind = Stretch.Kind.entries[it] }
            SectionLabel(tr("Durée"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 10, 15, 20, 30).forEach { m -> Chip(trf("%1\$s min", m), m == minutes) { minutes = m } }
            }
            SectionLabel(tr("Zones"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Stretch.Group.entries.forEach { g -> Chip(g.label, g.name in groups) { groups = if (g.name in groups) groups - g.name else groups + g.name } }
            }
            SectionLabel(tr("Matériel"))
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
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        StretchThumb(e.id, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(zoneLabel(e), if (e.uni) tr("par côté") else null).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(trf("%1\$s min", e.seconds / 60), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
    }
}

private fun zoneLabel(e: Stretch.Ex) = Stretch.Group.entries.filter { g -> e.zones.any { it in g.zones } }.take(2).joinToString(", ") { tr(it.label) }

/** Tous les exercices : recherche et filtre par zone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StretchAllScreen(nav: NavHostController) {
    var q by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    val sh by SuperPrefs.settings.collectAsState()
    val list = remember(q, group, favOnly, sh.stretchFavorites) {
        val g = Stretch.Group.entries.firstOrNull { it.name == group }
        Stretch.catalog().exercises.filter { e ->
            (g == null || e.zones.any { it in g.zones }) && (q.isBlank() || e.name.contains(q, true) || e.steps.any { it.contains(q, true) }) && (!favOnly || e.id in sh.stretchFavorites)
        }
    }
    DetailScaffold(tr("Exercices"), onBack = { nav.popBackStack() }) {
        app.superhealth.nutrition.ui.components.TextInput(tr("Rechercher"), q, { q = it })
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Favoris", favOnly) { favOnly = !favOnly }
            Stretch.Group.entries.forEach { g -> Chip(g.label, g.name == group) { group = if (g.name == group) null else g.name } }
        }
        Spacer(Modifier.height(10.dp))
        GroupCard {
            list.take(120).forEachIndexed { i, e ->
                if (i > 0) RowDivider()
                ExRow(e) { nav.go("stretch/ex/${e.id}") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Fiche d'un exercice : vidéo, ressenti, consignes. */
@Composable
fun StretchExerciseScreen(nav: NavHostController, id: String) {
    val e = remember(id) { Stretch.exercise(id) } ?: return
    val sh by SuperPrefs.settings.collectAsState()
    val fav = id in sh.stretchFavorites
    DetailScaffold(e.name, onBack = { nav.popBackStack() }, action = {
        IconButton(onClick = { SuperPrefs.update { it.copy(stretchFavorites = if (fav) it.stretchFavorites - id else it.stretchFavorites + id) } }) {
            Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, tr("Favoris"), tint = if (fav) StretchHue else Mf.colors.textSecondary)
        }
    }) {
        StretchVideo(e.id, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)))
        Spacer(Modifier.height(12.dp))
        Steps(e)
        Spacer(Modifier.height(10.dp))
        PillButton(tr("Lancer"), Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow) {
            Stretch.current.value = Stretch.Routine(e.name, Stretch.Kind.DAILY, listOf(e)); nav.go("stretch/play")
        }
        Spacer(Modifier.height(24.dp))
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

/**
 * Vidéo de l'exercice, en flux et en boucle, sans le son (consignes à l'écran). Sans réseau : la vignette reste, avec un message.
 * VideoView : une seule vue à la fois, recréée à chaque exercice.
 */
@Composable
internal fun StretchVideo(id: String, modifier: Modifier, playing: Boolean = true) {
    var failed by remember(id) { mutableStateOf(false) }
    var ready by remember(id) { mutableStateOf(false) }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (!failed) key(id) {
            var view by remember { mutableStateOf<VideoView?>(null) }
            AndroidView({ c ->
                VideoView(c).apply {
                    setOnPreparedListener { mp -> mp.isLooping = true; mp.setVolume(0f, 0f); ready = true; start() }
                    setOnErrorListener { _, _, _ -> failed = true; true }
                    setVideoURI(android.net.Uri.parse(Stretch.videoUrl(id)))
                    view = this
                }
            }, Modifier.fillMaxSize())
            LaunchedEffect(playing, ready) { view?.let { v -> if (ready) { if (playing) v.start() else v.pause() } } }
            DisposableEffect(Unit) { onDispose { view?.stopPlayback() } }
        }
        if (!ready) StretchThumb(id, Modifier.fillMaxSize())
        if (failed) Text(tr("Vidéo indisponible hors connexion"), color = Color.White, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp))
    }
}

/** Lecture d'une routine : vidéo en haut, temps restant, consignes, changement de côté signalé par une vibration. */
@Composable
fun StretchPlayScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val routine by Stretch.current.collectAsState()
    val r = routine ?: return
    val haptic = LocalHapticFeedback.current
    KeepScreenOn()
    var index by rememberSaveable { mutableIntStateOf(0) }
    var left by rememberSaveable { mutableIntStateOf(r.items.first().seconds) }
    var running by rememberSaveable { mutableStateOf(true) }
    var spent by rememberSaveable { mutableIntStateOf(0) }
    var finished by rememberSaveable { mutableStateOf(false) }
    val e = r.items[index.coerceIn(0, r.items.lastIndex)]
    // fermer ou revenir en arrière garde le temps déjà fait
    fun close() { if (!finished && spent >= 30) Stretch.record(ctx, r, spent); Stretch.current.value = null; nav.popBackStack() }
    androidx.activity.compose.BackHandler { close() }
    fun next() {
        if (index >= r.items.lastIndex) { finished = true; Stretch.record(ctx, r, spent) }
        else { index++; left = r.items[index].seconds; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    LaunchedEffect(running, finished) {
        while (running && !finished) {
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { close() }) { Icon(Icons.Rounded.Close, tr("Fermer"), tint = Mf.colors.text) }
            Text(trf("%1\$s sur %2\$s", index + 1, r.items.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(6.dp).padding(horizontal = 16.dp).clip(CircleShape).background(Mf.colors.chip)) {
            Box(Modifier.fillMaxWidth(((r.items.take(index).sumOf { it.seconds } + e.seconds - left).toFloat() / r.seconds.coerceAtLeast(1)).coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(StretchHue))
        }
        StretchVideo(e.id, Modifier.fillMaxWidth().padding(16.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)), running)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(e.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (e.uni) Text(tr(if (left > e.seconds / 2) "Premier côté" else "Second côté"), style = MaterialTheme.typography.labelLarge, color = StretchHue.onContainer(), modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(8.dp))
            Steps(e)
        }
        r.items.getOrNull(index + 1)?.let { n ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                StretchThumb(n.id, Modifier.size(36.dp))
                Spacer(Modifier.width(10.dp))
                Text(trf("Ensuite : %1\$s", n.name), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("%d:%02d".format(left / 60, left % 60), style = MaterialTheme.typography.displaySmall, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { left = e.seconds }) { Icon(Icons.Rounded.Refresh, tr("Recommencer"), tint = Mf.colors.text) }
            Box(Modifier.padding(horizontal = 8.dp).size(60.dp).clip(CircleShape).background(StretchHue).clickable { running = !running }, contentAlignment = Alignment.Center) {
                Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tr(if (running) "Pause" else "Reprendre"), tint = Color.White, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = { next() }) { Icon(Icons.Rounded.SkipNext, tr("Suivant"), tint = Mf.colors.text) }
        }
    }
}
