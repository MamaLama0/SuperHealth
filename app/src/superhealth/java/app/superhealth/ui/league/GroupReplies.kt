package app.superhealth.ui.league

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.superhealth.league.GroupHistory
import app.superhealth.league.League
import app.superhealth.league.SocialPosts
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.launch

/** A real conversation in the post: recent replies are visible without opening another panel. */
@Composable
internal fun GroupPostReplies(post: SocialPosts.Post, author: String, highlightedReply: String? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val spaces=app.superhealth.league.SocialSpaces.repository(ctx)
    val controls by spaces.state.collectAsState()
    val closed=spaces.legacyClosed(author,post.id)
    val history by GroupHistory.state.collectAsState()
    val league by League.state.collectAsState()
    val replyFocus = remember { FocusRequester() }
    var parentKey by rememberSaveable(author, post.id) { mutableStateOf<String?>(null) }
    var parentId by rememberSaveable(author, post.id) { mutableStateOf<String?>(null) }
    var text by rememberSaveable(author, post.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var visible by rememberSaveable(author, post.id, highlightedReply) { mutableIntStateOf(if (highlightedReply != null) Int.MAX_VALUE else 2) }
    var profile by remember { mutableStateOf<League.Friend?>(null) }
    // Auteur d'un commentaire qu'on n'a pas en ami : sa fiche propose une demande d'ami.
    var stranger by remember { mutableStateOf<GroupHistory.Event?>(null) }
    val replies = remember(history, controls, author, post.id, league.friends) { GroupHistory.replies(author, post.id).filterNot { spaces.legacyHidden(author,post.id,it.id) } }
    val rows = remember(replies) { GroupHistory.replyRows(replies) }
    val roots = rows.filter { it.depth == 0 }.takeLast(visible).map { it.event.key to it.event.id }.toSet()
    val displayed = buildList {
        var include = false
        rows.forEach { row ->
            if (row.depth == 0) include = (row.event.key to row.event.id) in roots
            if (include) add(row)
        }
    }
    val parent = replies.firstOrNull { it.key == parentKey && it.id == parentId }
    fun name(event: GroupHistory.Event) = if (event.key == League.publicKey()) tr("Vous") else league.friends.firstOrNull { it.key == event.key }?.shown ?: event.name
    fun submit() {
        if (text.isBlank() || busy || closed) return
        if (parentId != null && parent == null) { error = tr("Le commentaire n’est plus disponible."); return }
        val submitted = text.trim()
        val submittedParent = parent
        scope.launch {
            busy = true; error = null
            try {
                val sent = GroupHistory.reply(ctx, author, post, submitted, submittedParent)
                if (text.trim() == submitted) { text = ""; focus.clearFocus(); keyboard?.hide() }
                if (parentId == submittedParent?.id && parentKey == submittedParent?.key) { parentId = null; parentKey = null }
                visible = replies.size + 1
                if (!sent) error = tr("Réponse enregistrée. Elle sera envoyée à la prochaine synchronisation.")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = tr("Réponse impossible. Réessayez.") }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if(closed) Text(tr("Les réponses sont fermées."), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
        if (rows.count { it.depth == 0 } > visible) TextButton(onClick = { visible += 10 }, contentPadding = PaddingValues(0.dp)) {
            Text(trf("Afficher %1\$s autres réponses", rows.count { it.depth == 0 } - visible), style = MaterialTheme.typography.labelMedium)
        }
        displayed.forEach { row ->
            val event = row.event
            key(event.key, event.id) {
                val own = event.key == League.publicKey()
                val person = league.friends.firstOrNull { it.key == event.key }
                var menu by remember { mutableStateOf(false) }
                val bringIntoView = remember { BringIntoViewRequester() }
                LaunchedEffect(highlightedReply) {
                    if (event.id == highlightedReply) {
                        kotlinx.coroutines.delay(200)
                        bringIntoView.bringIntoView()
                    }
                }
                Row(Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView).then(if (event.id == highlightedReply) Modifier.background(Mf.colors.accent.copy(alpha = .12f), RoundedCornerShape(12.dp)) else Modifier).padding(start = (row.depth * 18).dp)
                    .then(if (row.depth > 0) Modifier.background(Mf.colors.elevated, RoundedCornerShape(12.dp)).padding(8.dp) else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fun open() { if (person != null) profile = person else if (!own) stranger = event }
                    Box(Modifier.padding(top = 2.dp).clickable(enabled = !own) { open() }) {
                        FriendAvatar(person, event.name, 28.dp, key = event.key)
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (own) tr("Vous") else person?.shown ?: event.name,
                                Modifier.weight(1f, fill = false).clickable(enabled = !own) { open() },
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(8.dp))
                            Text(if (System.currentTimeMillis() - event.reply!!.created < 60000) tr("À l’instant") else android.text.format.DateUtils.getRelativeTimeSpanString(event.reply.created,
                                System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString(),
                                style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary)
                        }
                        if (event.reply?.to != null) {
                            val quoted = GroupHistory.replyParent(event, replies)
                            Text(if (quoted == null) tr("Commentaire indisponible") else trf("En réponse à %1\$s", name(quoted)),
                                style = MaterialTheme.typography.labelSmall, color = Mf.colors.accent,
                                modifier = Modifier.padding(vertical = 4.dp))
                        }
                        Text(event.reply!!.text, style = MaterialTheme.typography.bodyMedium, color = Mf.colors.text)
                        TextButton(onClick = {
                            parentKey = event.key; parentId = event.id; error = null
                            replyFocus.requestFocus(); keyboard?.show()
                        }, enabled = !busy && !closed, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 40.dp)) {
                            Text(tr("Répondre"), style = MaterialTheme.typography.labelMedium)
                        }
                        if (event.id in history.pending) Text(tr("En attente d'envoi"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                    }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.MoreHoriz, tr("Options de la réponse"), Modifier.size(18.dp), tint = Mf.colors.textTertiary)
                        }
                        DropdownMenu(menu, { menu = false }) {
                            if (own) DropdownMenuItem(text = { Text(tr("Supprimer")) }, onClick = {
                                menu = false
                                scope.launch {
                                    try { GroupHistory.removeReply(ctx, event) }
                                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                    catch (_: Exception) { error = tr("Suppression enregistrée. Elle sera envoyée à la prochaine synchronisation.") }
                                }
                            })
                            if(author==League.myId()) DropdownMenuItem(text={Text(tr("Masquer cette réponse"))},onClick={menu=false;scope.launch {
                                try{spaces.moderateLegacy(post.id,"HIDE",event.id);spaces.sync()} catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){error=tr("Changement enregistré. Actualisez pour réessayer.")}
                            }})
                            if (!own) DropdownMenuItem(text = { Text(tr("Bloquer cet auteur")) }, onClick = {
                                menu = false
                                scope.launch {
                                    try { app.superhealth.league.SocialPreferences.blockAuthor(ctx, event.key) }
                                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                    catch (_: Exception) { error = tr("Blocage local actif. Révocation des accès à réessayer.") }
                                }
                            })
                        }
                    }
                }
            }
        }
        if (parentId != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Mf.colors.elevated).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(parent?.let { trf("Répondre à %1\$s", name(it)) } ?: tr("Commentaire indisponible"),
                    style = MaterialTheme.typography.labelMedium, color = Mf.colors.accent)
                parent?.reply?.text?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            IconButton(onClick = { parentId = null; parentKey = null }) { Icon(Icons.Rounded.Close, tr("Annuler la réponse"), Modifier.size(18.dp)) }
        }
        // réponses fermées : le fil reste lisible, sans champ de saisie désactivé
        if (!closed) Text(tr("Votre réponse est visible par les personnes qui peuvent lire cette publication."),
            style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
        if (!closed) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FriendAvatar(null, app.superhealth.ui.userName() ?: tr("Vous"), 28.dp)
            OutlinedTextField(text, { text = it.take(1000) },enabled=!closed,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { submit() }),
                placeholder = { Text(tr("Ajouter une réponse…"), style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.weight(1f).focusRequester(replyFocus), shape = RoundedCornerShape(24.dp), maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Mf.colors.divider, focusedBorderColor = Mf.colors.accent),
                trailingIcon = {
                    IconButton(enabled = text.isNotBlank() && !busy && !closed, onClick = ::submit) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Rounded.Send, tr("Répondre"), Modifier.size(20.dp))
                    }
                })
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
    profile?.let { FriendSheet(it) { profile = null } }
    stranger?.let { StrangerSheet(it.key, it.name) { stranger = null } }
}
