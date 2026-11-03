package app.superhealth.ui.league

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.league.*
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.dashboard.DashboardScreen
import app.superhealth.ui.dashboard.dashboardScoreEntries
import app.superhealth.ui.dashboard.dashboardRank
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Left page is the feed, right page is the existing dashboard. No feed navigation button. */
@Composable
fun HomeWithFeed(vm: SuperViewModel, nav: NavHostController) {
    val settings by SuperPrefs.settings.collectAsState()
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = 1) { 2 }
    val scope = rememberCoroutineScope()
    if (settings.socialFeedEnabled && settings.leagueEnabled) {
        BackHandler(pager.currentPage == 0) { scope.launch { pager.animateScrollToPage(1) } }
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize(), key = { it }) { page ->
            if (page == 0) SocialFeed(nav, active = pager.settledPage == 0) else DashboardScreen(vm, nav)
        }
    } else DashboardScreen(vm, nav)
    if (!settings.socialFeedIntroSeen) AlertDialog(onDismissRequest = { SuperPrefs.update { it.copy(socialFeedIntroSeen = true) } },
        icon = { GameArt(GameArtKind.TROPHY, Modifier.size(64.dp)) },
        title = { Text(tr("Nouveau · le fil de vos amis")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Depuis le dashboard, glissez vers la droite : le fil se trouve juste à gauche."))
            Text(tr("Retrouvez le classement, puis les photos, vidéos, séries et sorties de vos amis. Glissez vers la gauche pour revenir."))
            Text(tr("Vous pouvez désactiver ce fil dans Réglages → Social. Vos widgets sociaux restent disponibles sur le dashboard."), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton({ SuperPrefs.update { it.copy(socialFeedIntroSeen = true) } }) { Text(tr("Compris")) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialFeed(nav: NavHostController, active: Boolean) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val state by League.state.collectAsState(); val score by League.mine.collectAsState()
    val progression by ProgressRepository.current.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    fun refresh() { scope.launch {
        busy = true; error = null
        try { League.compute(ctx); Relay.sync(ctx); error = League.state.value.syncError }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error = tr("Actualisation impossible. Réessayez.") }
        finally { busy = false }
    } }
    LaunchedEffect(active) { if (active) refresh() }
    val posts = remember(state.posts, state.friends) {
        (SocialPosts.visible(state.posts).map { it to null } + state.friends.flatMap { f -> SocialPosts.visible(f.posts).map { it to f } })
            .sortedByDescending { it.first.created }
    }
    LazyColumn(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = app.superhealth.ui.LocalTabBarInset.current + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Le club"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(tr("Vos efforts, ensemble"), color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
                IconButton({ refresh() }, enabled = !busy) { Icon(Icons.Rounded.Refresh, tr("Actualiser")) }
                IconButton({ nav.go("friends/add") }) { Icon(Icons.Rounded.PersonAdd, tr("Ajouter un ami")) }
            }
        }
        item(key = "bricks") {
            val entries = dashboardScoreEntries(score, state.friends, System.currentTimeMillis() / 1000, tr("Vous"))
            val me = entries.firstOrNull { it.me }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MfCard(Modifier.weight(1f).fillMaxHeight(), onClick = { nav.go("league") }, padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameArt(GameArtKind.TROPHY, Modifier.size(32.dp)); Text(tr("Classement"), style = MaterialTheme.typography.labelMedium)
                    }
                    Text(me?.let { "#${dashboardRank(entries, it)}" } ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(me?.let { trf("%1\$s / 1000", it.score) } ?: tr("3 domaines requis"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                }
                MfCard(Modifier.weight(1f).fillMaxHeight(), onClick = { nav.go("progress") }, padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameArt(GameArtKind.XP, Modifier.size(32.dp)); Text(tr("Progression"), style = MaterialTheme.typography.labelMedium)
                    }
                    Text(trf("Niv. %1\$s", progression?.summary?.level ?: 1), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(trf("%1\$s XP cette semaine", progression?.summary?.weekXp ?: 0), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                }
            }
        }
        item(key = "compose") { MfCard(onClick = { showCreate = true }, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.AddCircleOutline, null, tint = Mf.colors.accent)
                Text(tr("Créer une publication"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(Icons.Rounded.ChevronRight, null, tint = Mf.colors.textTertiary)
            }
        } }
        if (busy) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { message -> item(key = "error") { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
        if (posts.isEmpty()) item(key = "empty") { MfCard {
            Text(tr("Le début d'une belle équipe"), style = MaterialTheme.typography.titleLarge)
            Text(tr("Partagez votre première séance ou ajoutez vos amis. Leurs publications apparaîtront ici."), modifier = Modifier.padding(top = 8.dp), color = Mf.colors.textSecondary)
        } }
        items(posts, key = { (post, friend) -> (friend?.id ?: "me") + ":" + post.id }) { (post, friend) ->
            SocialPostCard(post, friend)
        }
    }
    if (showCreate) ModalBottomSheet(onDismissRequest = { showCreate = false }, containerColor = Mf.colors.background) {
        fun open(route: String) {
            showCreate = false; SocialPosts.draft.value = null; nav.go(route)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(tr("Créer une publication"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
            SettingRow(tr("Message, photo ou vidéo"), icon = Icons.Rounded.AddPhotoAlternate) { open("social/compose") }
            SettingRow(tr("Partager une séance"), icon = Icons.Rounded.FitnessCenter) { open("social/share/workout") }
            SettingRow(tr("Partager une activité"), icon = Icons.Rounded.DirectionsRun) { open("social/share/activity") }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
internal fun SocialPostCard(post: SocialPosts.Post, friend: League.Friend?) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var media by remember(post.id) { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var play by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val league by League.state.collectAsState()
    val author = friend?.id ?: remember { League.myId() }
    val counts = remember(league.reactions, league.friends, author, post.id) { SocialReactions.counts(league.reactions, league.friends, author, post.id) }
    val selected = SocialReactions.selected(league.reactions, author, post.id)
    var reacting by remember { mutableStateOf(false) }
    var showReactions by rememberSaveable(post.id) { mutableStateOf(false) }
    fun react(kind: String) {
        showReactions = false
        scope.launch {
            reacting = true; error = null
            try { if (!SocialReactions.react(ctx, author, post, kind)) error = tr("Réaction enregistrée. Elle sera envoyée à la prochaine synchronisation.") }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = tr("Réaction impossible. Réessayez.") }
            finally { reacting = false }
        }
    }
    val video = post.media?.mime?.startsWith("video/") == true
    LaunchedEffect(post.id) {
        if (post.media != null && !video && media == null) {
            busy = true
            try { media = SocialPosts.download(ctx, post, friend) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = tr("Média indisponible. Réessayez.") }
            finally { busy = false }
        }
    }
    val photo by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, media) {
        val file = media
        if (file != null && !video) value = withContext(Dispatchers.IO) {
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.path, options)
            var sample = 1; while (maxOf(options.outWidth, options.outHeight) / sample > 1600) sample *= 2
            android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
    MfCard(padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FriendAvatar(friend, friend?.shown ?: app.superhealth.ui.userName() ?: tr("Vous"), 38.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(friend?.shown ?: tr("Vous"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(Instant.ofEpochMilli(post.created).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM · HH:mm", app.superhealth.ui.Lang.locale)),
                    style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
            }
            if (friend == null) IconButton({ confirmDelete = true }, enabled = !busy) { Icon(Icons.Rounded.DeleteOutline, tr("Supprimer"), tint = Mf.colors.textTertiary) }
        }
        if (post.title.isNotBlank()) Text(post.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        if (post.detail.isNotBlank()) Text(post.detail, style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
        if (post.text.isNotBlank()) Text(post.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
        photo?.let { Image(it, tr("Agrandir la photo"), Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(max = 400.dp)
            .clickable { viewPhoto = true }, contentScale = ContentScale.Fit) }
        if (post.media != null && (media == null || video)) TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true; error = null
            try { media = SocialPosts.download(ctx, post, friend); if (video) play = true }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = tr("Média indisponible. Réessayez.") }
            finally { busy = false }
        } }) { Icon(if (video) Icons.Rounded.PlayCircle else Icons.Rounded.Image, null); Spacer(Modifier.width(8.dp)); Text(tr(if (video) "Lire la vidéo" else "Afficher la photo")) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.padding(top = 8.dp)) {
            TextButton(onClick = { showReactions = true }, enabled = !reacting) {
                Icon(Icons.Rounded.AddReaction, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr(if (selected == null) "Réagir" else "Modifier ma réaction"))
            }
            SocialReactions.choices.filterKeys { (counts[it] ?: 0) > 0 }.forEach { (kind, emoji) ->
                FilterChip(selected = selected == kind, enabled = !reacting, onClick = { react(kind) },
                    label = { Text(emoji + " " + counts.getValue(kind)) })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (showReactions) ModalBottomSheet(onDismissRequest = { showReactions = false }, containerColor = Mf.colors.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(tr("Choisir une réaction"), style = MaterialTheme.typography.titleLarge)
            val labels = listOf("J’aime", "Je n’aime pas", "J’adore", "Bravo", "Applaudir", "Rire")
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SocialReactions.choices.entries.forEachIndexed { index, (kind, emoji) ->
                    FilterChip(selected = selected == kind, enabled = !reacting, onClick = { react(kind) },
                        label = { Text("$emoji ${tr(labels[index])}") })
                }
            }
            if (selected != null) TextButton(onClick = { react(selected) }, enabled = !reacting) { Text(tr("Retirer ma réaction")) }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(tr("Supprimer cette publication ?")) },
        confirmButton = { TextButton({ confirmDelete = false; scope.launch { busy = true
            try { SocialPosts.delete(ctx, post.id) } catch (_: Exception) { error = tr("Suppression distante à réessayer.") } finally { busy = false }
        } }) { Text(tr("Supprimer")) } }, dismissButton = { TextButton({ confirmDelete = false }) { Text(tr("Annuler")) } })
    if (viewPhoto && photo != null) Dialog(onDismissRequest = { viewPhoto = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var zoom by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        val transform = rememberTransformableState { scale, pan, _ ->
            zoom = (zoom * scale).coerceIn(1f, 5f)
            val limitX = size.width * (zoom - 1) / 2
            val limitY = size.height * (zoom - 1) / 2
            val moved = offset + pan
            offset = androidx.compose.ui.geometry.Offset(moved.x.coerceIn(-limitX, limitX), moved.y.coerceIn(-limitY, limitY))
        }
        Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            Box(Modifier.fillMaxSize().onSizeChanged { size = it }.transformable(transform), contentAlignment = Alignment.Center) {
                Image(photo!!, tr("Photo partagée"), Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y
                }, contentScale = ContentScale.Fit)
            }
            IconButton({ viewPhoto = false }, Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = .5f), RoundedCornerShape(50))) {
                Icon(Icons.Rounded.Close, tr("Fermer"), tint = Color.White)
            }
        }
    }
    if (play && media != null) Dialog(onDismissRequest = { play = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var player by remember { mutableStateOf<VideoView?>(null) }
        DisposableEffect(Unit) { onDispose { player?.stopPlayback() } }
        Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            AndroidView(factory = { context -> VideoView(context).apply {
                player = this; val view = this; setMediaController(MediaController(context).apply { setAnchorView(view) })
                setVideoPath(media!!.path); setOnPreparedListener { start() }
                setOnErrorListener { _, _, _ -> error = tr("Vidéo illisible sur cet appareil."); play = false; true }
            } }, modifier = Modifier.fillMaxSize().align(Alignment.Center))
            IconButton({ play = false }, Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = .5f), RoundedCornerShape(50))) {
                Icon(Icons.Rounded.Close, tr("Fermer"), tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialComposer(nav: NavHostController, initialChoice: String? = null) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var sourceJson by rememberSaveable { mutableStateOf(SocialPosts.draft.value?.let {
        kotlinx.serialization.json.Json.encodeToString(SocialPosts.Draft.serializer(), it)
    }.orEmpty()) }
    val source = remember(sourceJson) { sourceJson.takeIf { it.isNotBlank() }?.let { kotlinx.serialization.json.Json.decodeFromString(SocialPosts.Draft.serializer(), it) } }
    fun selectSource(draft: SocialPosts.Draft?) { sourceJson = draft?.let { kotlinx.serialization.json.Json.encodeToString(SocialPosts.Draft.serializer(), it) }.orEmpty() }
    var uri by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var published by rememberSaveable { mutableStateOf(false) }
    var sent by rememberSaveable { mutableStateOf(false) }
    var choosing by rememberSaveable { mutableStateOf(initialChoice) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri = it?.toString() }
    val preview by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri) {
        value = null
        uri?.let { raw ->
            try { value = SocialPosts.previewPhoto(ctx, Uri.parse(raw))?.asImageBitmap() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = tr(e.message ?: "Image illisible.") }
        }
    }
    val training by app.superhealth.training.WorkoutApp.store.state.collectAsState()
    var loadingActivities by remember { mutableStateOf(true) }
    val runs by produceState<List<app.superhealth.bridge.Bridge.Activity>>(emptyList()) {
        try {
            value = withContext(Dispatchers.IO) { app.superhealth.bridge.Bridge.activities(0).filter { it.gymId == null }.sortedByDescending { it.start } }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error = tr("Historique indisponible. Rouvrez cet écran pour réessayer.") }
        finally { loadingActivities = false }
    }
    DetailScaffold(tr("Partager avec mes amis"), { if (!busy) nav.popBackStack() }, contentSpacing = 12.dp) {
        OutlinedTextField(text, { text = it.take(500) }, label = { Text(tr("Votre message")) },
            supportingText = { Text("${text.length}/500") }, minLines = 3, enabled = !busy && !published, modifier = Modifier.fillMaxWidth())
        if (!published) {
            PillButton(tr("Photo ou vidéo"), Modifier.fillMaxWidth(), strong = false, enabled = !busy, icon = Icons.Rounded.AddPhotoAlternate) {
                picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }
            preview?.let { Image(it, tr("Photo partagée"), Modifier.fillMaxWidth().heightIn(max = 240.dp), contentScale = ContentScale.Fit) }
            uri?.let { Text(tr("Média sélectionné · photo 15 Mo, vidéo 50 Mo maximum"), color = Mf.colors.accent); TextButton({ uri = null }, enabled = !busy) { Text(tr("Retirer le média")) } }
            GroupCard {
                SettingRow(tr("Partager une séance"), subtitle = tr("Choisir une séance de musculation enregistrée"), icon = Icons.Rounded.FitnessCenter) { if (!busy) choosing = "WORKOUT" }
                RowDivider()
                SettingRow(tr("Partager une activité"), subtitle = tr("Course, vélo, marche et autres sports"), icon = Icons.Rounded.DirectionsRun) { if (!busy) choosing = "ACTIVITY" }
                RowDivider()
                SettingRow(tr("Partager une série"), subtitle = tr("Montrer votre régularité"), icon = Icons.Rounded.LocalFireDepartment) { if (!busy) choosing = "STREAK" }
            }
            source?.let { draft -> MfCard {
                Text(draft.title, style = MaterialTheme.typography.titleMedium); Text(draft.detail, modifier = Modifier.padding(top = 8.dp), color = Mf.colors.textSecondary)
                TextButton({ selectSource(null) }, enabled = !busy) { Text(tr("Retirer")) }
            } }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!published) PillButton(tr("Publier pour mes amis"), Modifier.fillMaxWidth(), enabled = !busy && (text.isNotBlank() || uri != null || source != null)) {
            scope.launch {
                busy = true; error = null
                try {
                    sent = SocialPosts.publish(ctx, text, source, uri?.let(Uri::parse)).sent; published = true
                    SocialPosts.draft.value = null
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { error = tr(e.message?.takeIf { !it.contains("://") } ?: "Publication impossible. Réessayez.") }
                finally { busy = false }
            }
        } else {
            Text(tr(if (sent) "Publication partagée avec vos amis !" else "Publication enregistrée. Actualisez le fil pour réessayer l'envoi."), style = MaterialTheme.typography.titleMedium)
            PillButton(tr("Terminé"), Modifier.fillMaxWidth()) { nav.popBackStack() }
        }
        Text(tr("Les photos sont redimensionnées et leurs métadonnées retirées. Les vidéos conservent leur fichier d'origine. Les cartes de séance et de course ne contiennent aucun tracé GPS."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textTertiary)
    }
    if (choosing != null) ModalBottomSheet(onDismissRequest = { choosing = null }, containerColor = Mf.colors.background) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp).navigationBarsPadding(), contentPadding = PaddingValues(16.dp)) {
            if (choosing == "WORKOUT") {
                if (training.history.isEmpty()) item { Text(tr("Aucune séance enregistrée")) }
                item { Text(tr("Choisir une séance"), style = MaterialTheme.typography.titleLarge) }
                items(training.history.sortedByDescending { it.startTime }, key = { it.id }) { workout ->
                    val date = Instant.ofEpochMilli(workout.startTime).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy", app.superhealth.ui.Lang.locale))
                    SettingRow(workout.name, subtitle = "$date · ${workout.durationSec / 60} min") { selectSource(SocialPosts.workoutDraft(workout)); choosing = null }
                }
            } else if (choosing == "ACTIVITY") {
                item { Text(tr("Choisir une activité"), style = MaterialTheme.typography.titleLarge) }
                if (loadingActivities) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                else if (runs.isEmpty()) item { Text(tr("Aucune activité enregistrée. Importez une activité ou synchronisez votre montre.")) }
                items(runs, key = { it.key }) { run ->
                    val draft = SocialPosts.activityDraft(run)
                    val date = Instant.ofEpochMilli(run.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy", app.superhealth.ui.Lang.locale))
                    SettingRow(draft.title, subtitle = "$date · ${draft.detail}") { selectSource(draft); choosing = null }
                }
            } else {
                val streaks = ProgressRepository.current.value?.summary?.streaks.orEmpty().filter { it.current > 0 }
                if (streaks.isEmpty()) item { Text(tr("Aucune série active pour le moment")) }
                items(streaks, key = { it.track }) { streak ->
                    val label = Progression.Track.entries.firstOrNull { it.name == streak.track }?.label ?: streak.track
                    val detail = "${streak.current} " + tr(if (streak.weekly) "semaines" else "jours")
                    SettingRow(tr(label), detail) { selectSource(SocialPosts.Draft(tr(label), detail, "STREAK")); choosing = null }
                }
            }
        }
    }
}
