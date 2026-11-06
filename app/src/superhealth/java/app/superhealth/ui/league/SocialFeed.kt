package app.superhealth.ui.league

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.draw.clip
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
import app.superhealth.social.boundedForCircle
import app.superhealth.league.*
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.dashboard.DashboardScreen
import app.superhealth.ui.go
import app.superhealth.ui.theme.tabBackground
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
fun HomeWithFeed(vm: SuperViewModel, nav: NavHostController, pager: androidx.compose.foundation.pager.PagerState) {
    val settings by SuperPrefs.settings.collectAsState()
    val scope = rememberCoroutineScope()
    // Les cartes de l'Arène utilisent un gros shader : il se compile en arrière-plan dès l'accueil.
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { app.superhealth.ui.cards.CardPerf.init(appCtx) }
    if (settings.socialFeedEnabled && settings.leagueEnabled) {
        BackHandler(pager.currentPage == 0) { scope.launch { pager.animateScrollToPage(1) } }
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize(), key = { it }) { page ->
            if (page == 0) SocialFeed(nav, active = pager.settledPage == 0) else DashboardScreen(vm, nav)
        }
    } else DashboardScreen(vm, nav)
    if (!settings.socialFeedIntroSeen) AlertDialog(onDismissRequest = { SuperPrefs.update { it.copy(socialFeedIntroSeen = true) } },
        icon = { GameArt(GameArtKind.TROPHY, Modifier.size(64.dp)) },
        title = { Text(tr("Bienvenue dans le Groupe")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Depuis le dashboard, glissez vers la droite : le fil se trouve juste à gauche."))
            Text(tr("Vos cartes, vos quêtes et vos combats, puis les photos, vidéos et sorties de vos amis. Glissez vers la gauche pour revenir."))
            Text(tr("Vous pouvez désactiver ce fil dans Réglages → Groupe. Vos widgets sociaux restent disponibles sur le dashboard."), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton({ SuperPrefs.update { it.copy(socialFeedIntroSeen = true) } }) { Text(tr("Compris")) } })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SocialFeed(nav: NavHostController, active: Boolean) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val state by League.state.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(GroupTab.ARENE) }
    fun refresh(userInitiated: Boolean = false) { scope.launch {
        if (busy) return@launch
        busy = true; if (userInitiated) error = null
        try { League.compute(ctx); Relay.sync(ctx); if (userInitiated) error = League.state.value.syncError }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { if (userInitiated) error = tr("Actualisation impossible. Réessayez.") }
        finally { busy = false }
    } }
    LaunchedEffect(active) { if (active && System.currentTimeMillis() - state.lastSync > 60000) refresh() }
    val history by GroupHistory.state.collectAsState()
    val posts = remember(state, history) { GroupHistory.posts(ctx).filterNot { (post, friend) ->
        val author = friend?.id ?: League.myId()
        SocialPreferences.hidden(author, post.id) || SocialPreferences.muted(author) || (friend != null && SocialPreferences.blocked(friend.key))
    } }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Actualiser en tirant le fil vers le bas, comme les autres listes Android, plutôt qu'avec un bouton d'en-tête.
    PullToRefreshBox(isRefreshing = busy && state.lastSync > 0L, onRefresh = { refresh(userInitiated = true) },
        modifier = Modifier.fillMaxSize().tabBackground(Mf.colors.accent).statusBarsPadding()) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = app.superhealth.ui.LocalTabBarInset.current + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // En-tête au format des onglets (TabHeader + boutons ronds) ; l'avatar ouvre le profil social comme sur le dashboard.
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    app.superhealth.ui.theme.Overline(tr("Vos efforts, ensemble"))
                    Text(tr("Groupe"), style = MaterialTheme.typography.headlineLarge, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NotificationBell(nav)
                    CircleAction(Icons.Rounded.Add, tr("Publier")) { showCreate = true }
                    SettingsAvatar { nav.go("social") }
                }
            }
        }
        item(key = "group-tabs") { GroupTabs(tab) { tab = it } }
        item(key = "friend-requests") { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { FriendRequestsBanner() } }
        // Cartes : ta carte et tes packs, le combat, les quêtes, l'album et les échanges, puis les duels du jour.
        if (tab == GroupTab.ARENE) {
            item(key = "arena-hero") { ArenaHero(nav) }
            item(key = "arena-combat") { CombatButton(nav, state.friends) }
            item(key = "arena-quests") { ArenaQuests(nav) }
            item(key = "arena-badges") { ArenaBadges(nav) }
            item(key = "arena-shortcuts") { ArenaShortcuts(nav) }
            item(key = "arena-duels") { ArenaDuels(nav, state.friends) }
        }
        if (tab == GroupTab.CERCLES) item(key = "circles") { CirclesPanel(nav, state.friends.size) }
        if (tab == GroupTab.FIL) item(key = "compose") { ComposeSurface(onClick = { showCreate = true }, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Mf.colors.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = Mf.colors.onStrong)
                }
                Text(tr("Créer une publication"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            }
            // raccourcis colorés : chaque type de partage s'ouvre directement, avec la teinte de son domaine
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fun open(route: String) { SocialPosts.draft.value = null; nav.go(route) }
                // Les cartes d'abord : ma carte Athlète (ou l'album pour choisir un fond).
                ShareShortcut(Icons.Rounded.Style, tr("Carte"), app.superhealth.ui.cards.Gold, Modifier.weight(1f)) {
                    val bg = app.superhealth.cards.CardStore.background()
                    if (bg == null) nav.go("cards/album") else {
                        app.superhealth.cards.CardShare.open(bg, app.superhealth.cards.CardStore.state.value.level(bg.card), app.superhealth.cards.CardShare.Event.ATHLETE, app.superhealth.cards.CardStore.snapshot.value?.now?.ovr)
                        nav.go("social/compose")
                    }
                }
                ShareShortcut(Icons.Rounded.AddPhotoAlternate, tr("Photo"), app.superhealth.ui.theme.Hues.pack.pink, Modifier.weight(1f)) { open("social/compose") }
                ShareShortcut(Icons.Rounded.FitnessCenter, tr("Séance"), app.superhealth.ui.theme.HealthHues.Fitness, Modifier.weight(1f)) { open("social/share/workout") }
                ShareShortcut(Icons.Rounded.DirectionsRun, tr("Activité"), app.superhealth.ui.theme.HealthHues.Activity, Modifier.weight(1f)) { open("social/share/activity") }
                ShareShortcut(Icons.Rounded.EmojiEvents, tr("PR"), RankGold, Modifier.weight(1f)) { open("social/share/pr") }
            }
        } }
        if (tab == GroupTab.FIL && (history.pending.isNotEmpty())) item(key = "pending") {
            GroupSurface {
                Text(tr("Des publications ou réponses attendent leur envoi"), style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !busy, onClick = { refresh(userInitiated = true) }) { Text(tr("Réessayer")) }
                TextButton({ nav.go("social/outbox") }) { Text(tr("Envois en attente")) }
            }
        }
        if (tab == GroupTab.FIL && (busy && posts.isEmpty() && state.lastSync == 0L)) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { message -> item(key = "error") { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
        if (state.friends.isEmpty() && tab != GroupTab.CERCLES) item(key = "invite") { GroupSurface {
            Text(tr("Invitez votre premier ami"), style = MaterialTheme.typography.titleLarge)
            Text(tr("Partagez vos activités et encouragez-vous. Vous choisissez les informations publiées."), color = Mf.colors.textSecondary)
            TextButton({ nav.go("friends/add") }) { Text(tr("Inviter ou saisir un code")) }
        } }
        if (tab == GroupTab.FIL && (state.lastSuccessfulSync > 0)) item(key = "last-sync") {
            Text(trf("Dernière actualisation · %1\$s", android.text.format.DateUtils.getRelativeTimeSpanString(
                state.lastSuccessfulSync, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()),
                style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
        }
        if (tab == GroupTab.FIL && (posts.isEmpty() && state.friends.isNotEmpty())) item(key = "empty") { GroupSurface {
            Text(tr(if (state.hiddenSocialPosts.isNotEmpty() || state.mutedSocialFriends.isNotEmpty()) "Votre fil est vide" else "Votre Groupe commence ici"), style = MaterialTheme.typography.titleLarge)
            Text(tr("Partagez votre première séance ou ajoutez vos amis. Leurs publications apparaîtront ici."), modifier = Modifier.padding(top = 8.dp), color = Mf.colors.textSecondary)
            TextButton({ nav.go("social/compose") }) { Text(tr("Écrire un message")) }
            TextButton({ nav.go("social/share/workout") }) { Text(tr("Partager une séance")) }
        } }
        if (tab == GroupTab.FIL && (state.hiddenSocialPosts.isNotEmpty() || state.mutedSocialFriends.isNotEmpty())) item(key = "reading-preferences") {
            TextButton({ nav.go("group/preferences") }) { Text(tr("Contenus masqués et sourdines")) }
        }
        if (tab == GroupTab.FIL) items(posts, key = { (post, friend) -> (friend?.id ?: "me") + ":" + post.id }) { (post, friend) ->
            SocialPostCard(post, friend)
        }
        if (tab == GroupTab.FIL && (GroupHistory.hasOlder())) item(key = "older") {
            TextButton(enabled = !busy, onClick = { scope.launch {
                busy = true; error = null
                try { GroupHistory.refresh(ctx, older = true) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { error = tr("Historique du Groupe indisponible. Réessayez.") }
                finally { busy = false }
            } }) { Text(tr("Charger les publications plus anciennes")) }
        }
    }
    }
    if (showCreate) ModalBottomSheet(onDismissRequest = { showCreate = false }, containerColor = Mf.colors.background) {
        fun open(route: String) {
            showCreate = false; SocialPosts.draft.value = null; nav.go(route)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(tr("Créer une publication"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
            SettingRow(tr("Partager ma carte ou un fond"), icon = Icons.Rounded.Style) { showCreate = false; nav.go("cards/album") }
            SettingRow(tr("Cercle privé"), icon = Icons.Rounded.Groups) { open("group/circles") }
            SettingRow(tr("Message, photo ou vidéo"), icon = Icons.Rounded.AddPhotoAlternate) { open("social/compose") }
            SettingRow(tr("Partager une séance"), icon = Icons.Rounded.FitnessCenter) { open("social/share/workout") }
            SettingRow(tr("Partager une activité"), icon = Icons.Rounded.DirectionsRun) { open("social/share/activity") }
            SettingRow(tr("Partager un PR"), icon = Icons.Rounded.EmojiEvents) { open("social/share/pr") }
        }
    }
}

/** Raccourci de partage : icône sur pastille teintée, libellé dessous. */
@Composable
private fun ShareShortcut(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, hue: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(hue.copy(alpha = .12f)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(20.dp), tint = hue)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Mf.colors.text, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Pastille de navigation du Groupe : largeur donnée par la rangée, libellé raccourci si la police est grande. */
@Composable
private fun GroupNavChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, hue: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.height(38.dp).clip(androidx.compose.foundation.shape.CircleShape).background(hue.copy(alpha = .14f)).clickable(onClick = onClick)
        .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(16.dp), tint = hue)
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
internal fun SocialPostCard(post: SocialPosts.Post, friend: League.Friend?, highlightedReply: String? = null) {
    val ctx = LocalContext.current
    val spaces = SocialSpaces.repository(ctx)
    val controls by spaces.state.collectAsState(); val scope = rememberCoroutineScope()
    var media by remember(post.id) { mutableStateOf(SocialPosts.localMedia(ctx, post)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var play by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editedText by remember(post.text) { mutableStateOf(post.text) }
    var showProfile by remember { mutableStateOf(false) }
    val history by GroupHistory.state.collectAsState()
    val league by League.state.collectAsState()
    val author = friend?.id ?: remember { League.myId() }
    val sharedWorkout = remember(post) { post.workout ?: SharedActivity.legacyStrength(post) }
    val sharedActivity = remember(post) { post.activity ?: SharedActivity.legacyActivity(post) }
    val counts = remember(league.reactions, league.friends, history, author, post.id) { GroupHistory.reactionCounts(author, post.id) }
    val selected = GroupHistory.selectedReaction(author, post.id)
    var reacting by remember { mutableStateOf(false) }
    var showReactions by rememberSaveable(post.id) { mutableStateOf(false) }
    // Les réponses restent repliées dans le fil ; une notification ciblée les ouvre directement.
    var repliesOpen by rememberSaveable(post.id) { mutableStateOf(highlightedReply != null) }
    val replyCount = remember(history, controls, author, post.id) { GroupHistory.replies(author, post.id).count { !spaces.legacyHidden(author, post.id, it.id) } }
    val closed = spaces.legacyClosed(author, post.id)
    val pending = friend == null && post.id in history.pending
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
    PostSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable { if (friend != null) showProfile = true }) { FriendAvatar(friend, friend?.shown ?: app.superhealth.ui.userName() ?: tr("Vous"), 38.dp) }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(friend?.shown ?: tr("Vous"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { if (friend != null) showProfile = true })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Instant.ofEpochMilli(post.created).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if (Instant.ofEpochMilli(post.created).atZone(ZoneId.systemDefault()).year == java.time.LocalDate.now().year) "d MMM · HH:mm" else "d MMM yyyy · HH:mm", app.superhealth.ui.Lang.locale)),
                        style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                    // état d'envoi : une coche discrète une fois envoyé, un libellé seulement s'il reste en attente
                    if (friend == null) {
                        Spacer(Modifier.width(6.dp))
                        if (pending) Text("· " + tr("En attente d'envoi"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.accent)
                        else Icon(Icons.Rounded.CloudDone, tr("Envoyé au Groupe"), Modifier.size(13.dp), tint = Mf.colors.textTertiary)
                    }
                }
            }
            Box {
                IconButton({ options = true }) { Icon(Icons.Rounded.MoreHoriz, tr("Options de la publication")) }
                DropdownMenu(options, { options = false }) {
                    if (friend == null) DropdownMenuItem(text = { Text(tr("Modifier le texte")) }, onClick = { options = false; editing = true })
                    DropdownMenuItem(text = { Text(tr(if (SocialPreferences.silent(author, post.id)) "Réactiver les alertes" else "Rendre la discussion silencieuse")) }, onClick = {
                        options = false; SocialPreferences.silence(ctx, author, post.id, !SocialPreferences.silent(author, post.id))
                    })
                    if (friend != null) DropdownMenuItem(text = { Text(tr("Masquer cette publication")) }, onClick = {
                        options = false; SocialPreferences.hide(ctx, author, post.id)
                    })
                    if (friend == null) DropdownMenuItem(text = { Text(tr(if (closed) "Rouvrir les réponses" else "Fermer les réponses")) }, enabled = !busy, onClick = {
                        options = false
                        scope.launch {
                            busy = true
                            try { spaces.moderateLegacy(post.id, "CLOSE", active = !closed); spaces.sync() }
                            catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (_: Exception) { error = tr("Changement enregistré. Actualisez pour réessayer.") }
                            finally { busy = false }
                        }
                    })
                    if (friend == null) DropdownMenuItem(text = { Text(tr("Supprimer"), color = MaterialTheme.colorScheme.error) }, enabled = !busy,
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { options = false; confirmDelete = true })
                }
            }
        }
        // badge de type coloré : on repère d'un coup d'œil une séance, une sortie ou un record
        val sharedCard = remember(post.detail) { app.superhealth.cards.CardShare.parse(post.detail) }
        val kind = when {
            post.record != null -> Triple(Icons.Rounded.EmojiEvents, tr("Record"), RankGold)
            sharedCard != null -> Triple(Icons.Rounded.Style, tr("Carte"), androidx.compose.ui.graphics.Color(sharedCard.pull.card.rarity.color.toInt()))
            sharedWorkout != null -> Triple(Icons.Rounded.FitnessCenter, tr("Séance"), app.superhealth.ui.theme.HealthHues.Fitness)
            sharedActivity != null -> Triple(Icons.Rounded.DirectionsRun, tr("Activité"), app.superhealth.ui.theme.HealthHues.Activity)
            else -> null
        }
        kind?.let { (icon, label, hue) ->
            Row(Modifier.padding(top = 14.dp).clip(androidx.compose.foundation.shape.CircleShape).background(hue.copy(alpha = .14f))
                .padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(14.dp), tint = hue)
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = hue, fontWeight = FontWeight.SemiBold)
            }
        }
        if (post.title.isNotBlank()) Text(post.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = if (kind != null) 8.dp else 16.dp))
        if (post.record != null || sharedWorkout != null || sharedActivity != null) {
            SharedPostVisual(post.record, sharedWorkout, sharedActivity)
        } else if (sharedCard != null) {
            app.superhealth.ui.cards.SharedCardVisual(sharedCard)
            app.superhealth.cards.CardShare.caption(post.detail).takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
            }
        } else {
        var expanded by rememberSaveable(post.id) { mutableStateOf(false) }
        if (post.detail.isNotBlank()) {
            Text(post.detail, style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp))
            if (post.detail.lines().size > 3 || post.detail.length > 180) TextButton({ expanded = !expanded }) {
                Text(tr(if (expanded) "Réduire" else "Voir le détail"))
            }
        }
        }
        if (controls.legacy.any{it.owner==author && it.post==post.id && it.kind=="EDIT"}) Text(tr("Modifié"),style=MaterialTheme.typography.labelSmall)
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
        HorizontalDivider(Modifier.padding(top = 12.dp), color = Mf.colors.divider.copy(alpha = .6f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showReactions = true }, enabled = !reacting) {
                if (selected == null) Icon(Icons.Rounded.AddReaction, null, Modifier.size(20.dp))
                else Text(SocialReactions.emoji(selected), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                Text(tr(if (selected == null) "Réagir" else "Ma réaction"))
            }
            if (!closed || replyCount > 0) TextButton(onClick = { repliesOpen = !repliesOpen }) {
                Icon(androidx.compose.ui.res.painterResource(nodomain.freeyourgadget.gadgetbridge.R.drawable.ic_group_comments), null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (replyCount > 0) tr("Répondre") + " · " + replyCount else tr("Répondre"))
            }
            Spacer(Modifier.weight(1f))
            counts.entries.sortedByDescending { it.value }.take(4).forEach { (kind, count) ->
                Surface(onClick = { react(kind) }, enabled = !reacting, shape = androidx.compose.foundation.shape.CircleShape,
                    color = if (selected == kind) Mf.colors.accent.copy(alpha = .12f) else Color.Transparent) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(SocialReactions.emoji(kind), style = MaterialTheme.typography.bodyLarge)
                        if (count > 1) Text(" $count", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                    }
                }
            }
        }
        if (pending) TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true; error = null
            try { Relay.publishOnly(ctx) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = tr("Envoi impossible. Réessayez.") }
            finally { busy = false }
        } }) { Text(tr("Réessayer l'envoi")) }
        AnimatedVisibility(repliesOpen && (!closed || replyCount > 0 || highlightedReply != null)) { GroupPostReplies(post, author, highlightedReply) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (showReactions) GroupReactionPicker(selected, onDismiss = { showReactions = false }, onSelect = ::react)
    if (showProfile && friend != null) FriendSheet(friend) { showProfile = false }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text(tr("Modifier le texte")) },
        text = { OutlinedTextField(editedText, { editedText = it.take(500) }, label = { Text(tr("Votre message")) }) },
        confirmButton = { TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true
            try { SocialPosts.edit(ctx, post, editedText); editing = false }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = tr("Modification impossible. Réessayez.") }
            finally { busy = false }
        } }) { Text(tr("Enregistrer")) } }, dismissButton = { TextButton({ editing = false }) { Text(tr("Annuler")) } })
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
    val savedDraft = remember { GroupDraft.load(ctx) }
    var text by rememberSaveable { mutableStateOf(savedDraft.text) }
    var sourceJson by rememberSaveable { mutableStateOf(SocialPosts.draft.value?.let {
        kotlinx.serialization.json.Json.encodeToString(SocialPosts.Draft.serializer(), it)
    } ?: savedDraft.source) }
    val source = remember(sourceJson) { sourceJson.takeIf { it.isNotBlank() }?.let { kotlinx.serialization.json.Json.decodeFromString(SocialPosts.Draft.serializer(), it) } }
    fun selectSource(draft: SocialPosts.Draft?) { sourceJson = draft?.let { kotlinx.serialization.json.Json.encodeToString(SocialPosts.Draft.serializer(), it) }.orEmpty() }
    var uri by rememberSaveable { mutableStateOf(savedDraft.uri) }
    var shareRoute by rememberSaveable { mutableStateOf(if (SocialPosts.draft.value?.activity?.sport == "HIKE") false else savedDraft.shareRoute) }
    var shareDetails by rememberSaveable { mutableStateOf(savedDraft.shareDetails) }
    var sourceLoading by remember { mutableStateOf(false) }
    val sharedSource = source?.shared(shareRoute, shareDetails)
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var published by rememberSaveable { mutableStateOf(false) }
    var sent by rememberSaveable { mutableStateOf(false) }
    var choosing by rememberSaveable { mutableStateOf(initialChoice) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { selected ->
        selected?.let { runCatching { ctx.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        uri = selected?.toString()
    }
    LaunchedEffect(text, sourceJson, uri, published, shareRoute, shareDetails) {
        if (!published) {
            kotlinx.coroutines.delay(300)
            try { GroupDraft.save(ctx, GroupDraft.Draft(text, sourceJson, uri, shareRoute, shareDetails, savedDraft.publicationId)) }
            catch (_: Exception) { error = tr("Brouillon non enregistré. Vérifiez l’espace disponible.") }
        }
    }
    val latestDraft by rememberUpdatedState(GroupDraft.Draft(text, sourceJson, uri, shareRoute, shareDetails, savedDraft.publicationId))
    val latestPublished by rememberUpdatedState(published)
    DisposableEffect(Unit) {
        onDispose { if (!latestPublished) runCatching { GroupDraft.save(ctx, latestDraft) } }
    }
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
    DetailScaffold(tr("Publier dans le Groupe"), { if (!busy) nav.popBackStack() }, contentSpacing = 12.dp) {
        OutlinedTextField(text, { text = it.take(500) }, label = { Text(tr("Votre message")) },
            supportingText = { Text("${text.length}/500") }, minLines = 3, enabled = !busy && !published, modifier = Modifier.fillMaxWidth())
        if (!published) {
            PillButton(tr("Photo ou vidéo"), Modifier.fillMaxWidth(), strong = false, enabled = !busy, icon = Icons.Rounded.AddPhotoAlternate) {
                picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }
            preview?.let { Image(it, tr("Photo partagée"), Modifier.fillMaxWidth().heightIn(max = 240.dp), contentScale = ContentScale.Fit) }
            uri?.let {
                Text(tr("Média sélectionné · photo 15 Mo, vidéo 50 Mo maximum"), color = Mf.colors.accent)
                Text(tr("Les photos sont réduites et leurs métadonnées retirées. Les vidéos conservent leur fichier d’origine."),
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                TextButton({ uri = null }, enabled = !busy) { Text(tr("Retirer le média")) }
            }
            GroupCard {
                SettingRow(tr("Partager une séance"), subtitle = tr("Choisir une séance de musculation enregistrée"), icon = Icons.Rounded.FitnessCenter) { if (!busy) choosing = "WORKOUT" }
                RowDivider()
                SettingRow(tr("Partager une activité"), subtitle = tr("Course, vélo, marche et autres sports"), icon = Icons.Rounded.DirectionsRun) { if (!busy) choosing = "ACTIVITY" }
                RowDivider()
                SettingRow(tr("Partager un PR"), subtitle = tr("Record récent, avec une vidéo facultative"), icon = Icons.Rounded.EmojiEvents) { if (!busy) choosing = "PR" }
            }
            if (source?.activity?.route != null) SwitchRow(tr("Partager le parcours"), tr("Le tracé GPS sera visible par vos amis."), shareRoute) { shareRoute = it }
            if (source?.activity != null || source?.workout != null || source?.record != null)
                SwitchRow(tr("Statistiques détaillées"), tr("Séries, progression et mesures disponibles pour cette activité."), shareDetails) { shareDetails = it }
            sharedSource?.let { draft ->
                Text(tr("Aperçu de la publication"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
                PostSurface {
                    Text(draft.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val card = remember(draft.detail) { app.superhealth.cards.CardShare.parse(draft.detail) }
                    if (draft.record != null || draft.workout != null || draft.activity != null) SharedPostVisual(draft.record, draft.workout, draft.activity)
                    else if (card != null) { app.superhealth.ui.cards.SharedCardVisual(card); Text(app.superhealth.cards.CardShare.caption(draft.detail), modifier = Modifier.padding(top = 8.dp), color = Mf.colors.textSecondary) }
                    else Text(draft.detail, modifier = Modifier.padding(top = 8.dp), color = Mf.colors.textSecondary)
                    TextButton({ selectSource(null) }, enabled = !busy) { Text(tr("Retirer")) }
                }
            }
        }
        if (!published) TextButton(enabled=!busy && !sourceLoading,onClick={ scope.launch {
            busy=true;error=null
            try {
                val repo=SocialSpaces.repository(ctx);repo.initialize()
                val prepared=uri?.let { SocialPosts.prepareCircleMedia(ctx,Uri.parse(it)) }
                val post=sharedSource?.let { source -> SocialPosts.Post(savedDraft.publicationId,System.currentTimeMillis(),kind=source.kind,title=source.title,detail=source.detail,record=source.record,workout=source.workout,activity=source.activity) }
                val snapshot=post?.let { kotlinx.serialization.json.Json.decodeFromString(app.superhealth.social.FriendPost.serializer(),kotlinx.serialization.json.Json.encodeToString(SocialPosts.Post.serializer(),it)) }
                CircleShareHandoff.text=text;CircleShareHandoff.media=prepared;CircleShareHandoff.snapshot=snapshot?.boundedForCircle()
                nav.navigate("group/circles")
            } catch(e:kotlinx.coroutines.CancellationException){throw e} catch(e:Exception){error=tr(e.message?:"Partage indisponible.")} finally{busy=false}
        } }) { Text(tr("Choisir un cercle privé")) }
        Text(tr("Visible par vos amis · seules les informations choisies sont partagées"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy || sourceLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!published) PillButton(tr("Publier dans le Groupe"), Modifier.fillMaxWidth(), enabled = !busy && !sourceLoading && (text.isNotBlank() || uri != null || source != null)) {
            scope.launch {
                busy = true; error = null
                try {
                    sent = SocialPosts.publish(ctx, text, sharedSource, uri?.let(Uri::parse), savedDraft.publicationId).sent; published = true
                    SocialPosts.draft.value = null; GroupDraft.clear(ctx)
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { error = tr(e.message?.takeIf { !it.contains("://") } ?: "Publication impossible. Réessayez.") }
                finally { busy = false }
            }
        } else {
            Text(tr(if (sent) "Publication envoyée au Groupe" else "Publication enregistrée. Actualisez le fil pour réessayer l'envoi."), style = MaterialTheme.typography.titleMedium)
            PillButton(tr("Terminé"), Modifier.fillMaxWidth()) { nav.popBackStack() }
        }
        Text(tr("Seules les données de l’activité choisie et visibles dans l’aperçu seront partagées. Vous pouvez masquer le parcours et les statistiques détaillées. Les vidéos conservent leur fichier d’origine."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textTertiary)
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
                    SettingRow(draft.title, subtitle = "$date · ${draft.detail}") {
                        if (!sourceLoading) scope.launch {
                            sourceLoading = true
                            try { selectSource(SharedActivity.draft(ctx, run)); choosing = null }
                            catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (_: Exception) { error = tr("Détails de l’activité indisponibles. Réessayez.") }
                            finally { sourceLoading = false }
                        }
                    }
                }
            } else if (choosing == "PR") {
                val records = GroupRecords.recent()
                item { Text(tr("Choisir un PR récent"), style = MaterialTheme.typography.titleLarge) }
                item { Text(tr("Records des 90 derniers jours · ajoutez ensuite une photo ou une vidéo")) }
                if (records.isEmpty()) item { Text(tr("Aucun PR récent dans vos séances enregistrées")) }
                items(records, key = { it.exercise + it.metric }) { record ->
                    val draft = GroupRecords.draft(record)
                    SettingRow(record.name, subtitle = draft.detail) { selectSource(draft); choosing = null }
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
