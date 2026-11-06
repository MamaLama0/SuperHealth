package app.superhealth.ui.league

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.league.HealthIndex
import app.superhealth.league.League
import app.superhealth.nutrition.ui.components.*
import app.superhealth.ui.ProfilePhoto
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun Initial(name: String, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(LeagueHue.container()), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(app.superhealth.ui.Lang.locale), style = MaterialTheme.typography.titleMedium, color = LeagueHue.onContainer())
    }
}

/**
 * Photo d'une personne du Groupe. [key] (clé de groupe) sert pour quelqu'un qu'on n'a pas en ami : son visage public ;
 * sans ami ni clé, c'est ma propre photo.
 */
@Composable
internal fun FriendAvatar(friend: League.Friend?, name: String, size: Dp = 40.dp, key: String? = null) {
    val ctx = LocalContext.current
    val own by ProfilePhoto.bitmap
    val stranger = friend == null && key != null && key != League.publicKey()
    val photo by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, friend?.id, friend?.avatar, friend?.remote, key) {
        value = null
        if (stranger || (friend != null && friend.avatar == null)) {
            value = runCatching { app.superhealth.league.Faces.bitmap(ctx, key ?: friend!!.key)?.asImageBitmap() }.getOrNull()
            return@produceState
        }
        val avatar = friend?.avatar
        if (friend != null && avatar != null && app.superhealth.league.ProfileAvatars.valid(avatar)) {
            try {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val file = app.superhealth.league.SocialPosts.downloadMedia(ctx, avatar, friend)
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(file.path, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
                    android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* Initiale si le média est temporairement indisponible. */ }
        }
    }
    val image = if (friend == null && !stranger) own else photo
    if (image == null) Initial(name, size)
    else Image(image, name, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
}

/** Ajouter est toujours visible ; la liste conserve sa place et les réglages ont leur propre écran. */
@Composable
fun FriendsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { runCatching { app.superhealth.league.InvitationCodes.collectResponses(ctx) } }
    val st by League.state.collectAsState()
    var opened by remember { mutableStateOf<League.Friend?>(null) }
    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding()) {
        NavBar(tr("Amis"), { nav.popBackStack() }) {
            IconButton(onClick = { nav.go("league/settings") }) { Icon(Icons.Rounded.Settings, tr("Réglages du classement")) }
        }
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp)) {
            PillButton(tr("Ajouter un ami"), Modifier.fillMaxWidth(), icon = Icons.Rounded.PersonAdd) { nav.go("friends/add") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { FriendRequestsBanner() }
            GroupCard {
                SettingRow(tr("Classement et progression"), subtitle = tr("Performances, records et régularité"), icon = Icons.Rounded.EmojiEvents) { nav.go("league") }
            }
            SectionLabel(trf("Mes amis (%1\$s)", st.friends.size))
            if (st.friends.isEmpty()) MfCard {
                Text(tr("Vos amis apparaîtront ici après l'échange d'une invitation."), color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
            } else GroupCard {
                st.friends.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.shown }).forEachIndexed { i, f ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().clickable { opened = f }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        FriendAvatar(f, f.shown, 44.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(f.shown, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (f.time <= 0 && f.remote == null) tr("Nouvelle invitation nécessaire") else if (f.time <= 0) tr("En attente d'actualisation") else if (f.model != League.currentModel) tr("Ancienne carte : demander une mise à jour") else trf("Mis à jour : %1\$s", Instant.ofEpochSecond(f.time).atZone(ZoneId.systemDefault()).toLocalDate().toString()),
                                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                        }
                        f.athlete?.ovr?.let { Text(trf("Note %1\$s", it), style = MaterialTheme.typography.labelMedium, color = LeagueHue.onContainer()) }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.padding(start = 8.dp).size(20.dp), tint = Mf.colors.textTertiary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    opened?.let { FriendSheet(it) { opened = null } }
}

/** Les comparaisons sont au premier plan ; l'identité et la gestion restent disponibles à la demande. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendSheet(f0: League.Friend, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var revoking by remember { mutableStateOf(false) }
    var revokeError by remember { mutableStateOf<String?>(null) }
    val st by League.state.collectAsState()
    val f = st.friends.firstOrNull { it.id == f0.id } ?: f0
    var alias by remember(f.id) { mutableStateOf(f.alias.orEmpty()) }
    var removing by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }
    var management by remember(f.id) { mutableStateOf(false) }
    var metrics by remember(f.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { League.rename(ctx, f.id, alias); onDismiss() }, containerColor = Mf.colors.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FriendAvatar(f, f.shown, 48.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(f.shown, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (f.time <= 0 && f.remote == null) tr("Nouvelle invitation nécessaire") else if (f.time <= 0) tr("En attente d'actualisation") else trf("Mis à jour : %1\$s", Instant.ofEpochSecond(f.time).atZone(ZoneId.systemDefault()).toLocalDate().toString()), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
                if (f.model == League.currentModel && f.ready) Column(horizontalAlignment = Alignment.End) {
                    Text(f.score.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = LeagueHue.onContainer())
                    Text(tr("Indice"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                }
            }
            if (f.athlete != null) {
                var card by remember { mutableStateOf(false) }
                PillButton(tr("Voir sa carte Athlète"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.EmojiEvents) { card = true }
                if (card) app.superhealth.ui.cards.FriendCardViewer(app.superhealth.ui.cards.friendFace(f), tr("Ami"), { card = false }, onDuel = null)
            }
            Spacer(Modifier.height(12.dp))
            FriendProgressDetails(f)
            if (f.pillars.isNotEmpty()) {
                SectionLabel(tr("Indice global de santé"))
                if (f.endDay > 0) Text(trf("Jusqu'au %1\$s", LocalDate.ofEpochDay(f.endDay).toString()), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                League.Pillar.entries.forEachIndexed { i, p ->
                    val v = f.pillars.getOrNull(i)
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(p.label), style = MaterialTheme.typography.bodySmall, maxLines = 1, color = if (v == null) Mf.colors.textTertiary else Mf.colors.text, modifier = Modifier.width(124.dp))
                        Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(Mf.colors.chip)) {
                            if (v != null && v > 0) Box(Modifier.fillMaxWidth(v.coerceIn(0, 100) / 100f).height(8.dp).clip(CircleShape).background(LeagueHue))
                        }
                        Text(v?.let { "$it %" } ?: "—", style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 8.dp).width(44.dp))
                    }
                }
            }
            if (f.history.size > 1) {
                SectionLabel(tr("Indices reçus"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    f.history.entries.sortedBy { it.key }.takeLast(6).forEach { (date, score) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Mf.colors.card).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(score.toString(), style = MaterialTheme.typography.titleSmall)
                            val day = date.substringAfter(':').substringBefore(':').toLongOrNull()
                            Text(if (date.startsWith("v") && day != null) LocalDate.ofEpochDay(day).toString().takeLast(5) + " · " + date.substringBefore(':') else date.substringAfter('-'),
                                style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                        }
                    }
                }
            }
            if (f.metrics.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                GroupCard {
                    SettingRow(tr("Activités, courses et PR"), subtitle = tr("Voir les statistiques partagées")) { metrics = !metrics }
                    if (metrics) {
                        RowDivider()
                        Column(Modifier.padding(16.dp)) {
                            f.metrics.forEach { m -> Text(m.label + " : " + app.superhealth.nutrition.ui.num(m.value, 1) + " " + m.unit,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            GroupCard {
                SettingRow(tr("Gérer cet ami"), subtitle = tr("Surnom, identité et suppression")) { management = !management }
                if (management) {
                    RowDivider()
                    Column(Modifier.padding(16.dp)) {
                        SwitchRow(tr("Mettre en sourdine"), tr("Masquer ses publications dans le fil et couper ses nouvelles alertes sur ce téléphone."), app.superhealth.league.SocialPreferences.muted(f.id)) { on ->
                            app.superhealth.league.SocialPreferences.mute(ctx, f.id, on)
                        }
                        SwitchRow(tr("Couper uniquement les alertes"), tr("Conserver ses publications dans le fil."), f.id in st.quietSocialFriends) { on ->
                            app.superhealth.league.SocialPreferences.quiet(ctx, f.id, on)
                        }
                        TextButton(onClick = { blocking = true }) { Text(tr("Bloquer cette personne"), color = MaterialTheme.colorScheme.error) }
                        revokeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        TextInput(tr("Surnom"), alias, { alias = it.take(24) }, placeholder = f.name)
                        Text(trf("Empreinte : %1\$s", League.fingerprint(f.key)), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
                        TextButton(onClick = { removing = true }) { Text(tr("Retirer cet ami"), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    if (blocking) AlertDialog(onDismissRequest = { blocking = false }, title = { Text(tr("Bloquer cette personne ?")) },
        text = { Text(tr("Ses interventions sont masquées. Ses accès futurs sont révoqués. Vous quittez les cercles communs que vous ne gérez pas.")) },
        confirmButton = { TextButton(enabled = !revoking, onClick = { scope.launch {
            revoking = true
            try { app.superhealth.league.SocialPreferences.block(ctx, f); blocking = false; onDismiss() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { blocking = false; revokeError = tr("Blocage local actif. Révocation des accès à réessayer.") }
            finally { revoking = false }
        } }) { Text(tr("Bloquer")) } }, dismissButton = { TextButton({ blocking = false }) { Text(tr("Annuler")) } })
    if (removing) AlertDialog(onDismissRequest = { removing = false }, containerColor = Mf.colors.card,
        title = { Text(trf("Retirer %1\$s ?", f.shown)) },
        text = { Column {
            Text(tr("Son accès aux prochains partages sera révoqué. Les amis à jour suivront automatiquement le nouveau lien."))
            revokeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !revoking, onClick = { scope.launch {
            revoking = true; revokeError = null
            try { app.superhealth.league.Relay.rotate(ctx, setOf(f.id)); League.remove(ctx, f.id); removing = false; onDismiss() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { revokeError = tr("Révocation à réessayer. Aucun nouvel accès ne lui sera communiqué.") }
            finally { revoking = false }
        } }) { Text(tr(if (revoking) "Révocation…" else "Retirer et révoquer")) } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text(tr("Annuler")) } })
}

/** Fiche d'une personne croisée en commentaire qu'on n'a pas en ami : sa photo, son nom et la demande d'ami. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StrangerSheet(key: String, name: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sent by app.superhealth.league.FriendRequests.sent.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { app.superhealth.league.FriendRequests.load(ctx) }
    val pending = remember(sent) { app.superhealth.league.FriendRequests.pending(key) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Mf.colors.background, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FriendAvatar(null, name, 96.dp, key = key)
            Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tr("Vous n'êtes pas encore amis. Une demande lui envoie votre code ami ; il reste valable 24 h."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            PillButton(tr(if (pending) "Demande envoyée" else "Demander en ami"), Modifier.fillMaxWidth(), enabled = !busy && !pending, icon = Icons.Rounded.PersonAdd) {
                busy = true; error = null
                scope.launch {
                    try { app.superhealth.league.FriendRequests.send(ctx, key) }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: tr("Envoi impossible. Réessayez.") }
                    finally { busy = false }
                }
            }
            error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Demandes d'ami reçues depuis un commentaire : accepter ouvre l'ajout habituel, ignorer la retire. */
@Composable
internal fun FriendRequestsBanner() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val requests by app.superhealth.league.FriendRequests.inbox.collectAsState()
    LaunchedEffect(Unit) { runCatching { app.superhealth.league.FriendRequests.refresh(ctx) } }
    requests.forEach { r ->
        key(r.id) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Mf.colors.elevated).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FriendAvatar(null, r.name, 44.dp, key = r.key)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(r.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr("veut être votre ami"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
                TextButton({ scope.launch { app.superhealth.league.FriendRequests.dismiss(ctx, r) } }) { Text(tr("Ignorer")) }
                Button({ scope.launch { runCatching { app.superhealth.league.FriendRequests.accept(ctx, r) }.onFailure { League.incomingError.value = true } } }) { Text(tr("Accepter")) }
            }
        }
    }
}
