package app.superhealth.ui.league

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.MoreHoriz
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
internal fun GroupPostReplies(post: SocialPosts.Post, author: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val history by GroupHistory.state.collectAsState()
    val league by League.state.collectAsState()
    var text by rememberSaveable(author, post.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var visible by rememberSaveable(author, post.id) { mutableIntStateOf(2) }
    var profile by remember { mutableStateOf<League.Friend?>(null) }
    val replies = remember(history, author, post.id, league.friends) { GroupHistory.replies(author, post.id) }
    fun submit() {
        if (text.isBlank() || busy) return
        val submitted = text.trim()
        scope.launch {
            busy = true; error = null
            try {
                val sent = GroupHistory.reply(ctx, author, post, submitted)
                if (text.trim() == submitted) { text = ""; focus.clearFocus(); keyboard?.hide() }
                if (!sent) error = tr("Réponse enregistrée. Elle sera envoyée à la prochaine synchronisation.")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = tr("Réponse impossible. Réessayez.") }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (replies.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(androidx.compose.ui.res.painterResource(nodomain.freeyourgadget.gadgetbridge.R.drawable.ic_group_comments),
                null, Modifier.size(16.dp), tint = Mf.colors.textSecondary)
            Text(tr("Réponses") + " · " + replies.size, style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
        }
        if (replies.size > visible) TextButton(onClick = { visible += 10 }, contentPadding = PaddingValues(0.dp)) {
            Text(trf("Afficher %1\$s autres réponses", replies.size - visible), style = MaterialTheme.typography.labelMedium)
        }
        replies.takeLast(visible).forEach { event ->
            key(event.key, event.id) {
                val own = event.key == League.publicKey()
                val person = league.friends.firstOrNull { it.key == event.key }
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.padding(top = 2.dp).clickable(enabled = person != null) { profile = person }) {
                        FriendAvatar(person, event.name, 28.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (own) tr("Vous") else person?.shown ?: event.name,
                                Modifier.clickable(enabled = person != null) { profile = person },
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            Text(if (System.currentTimeMillis() - event.reply!!.created < 60000) tr("À l’instant") else android.text.format.DateUtils.getRelativeTimeSpanString(event.reply.created,
                                System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString(),
                                style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary)
                        }
                        Text(event.reply!!.text, style = MaterialTheme.typography.bodyMedium, color = Mf.colors.text)
                        if (event.id in history.pending) Text(tr("En attente d'envoi"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                    }
                    if (own) Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.MoreHoriz, tr("Options de la réponse"), Modifier.size(18.dp), tint = Mf.colors.textTertiary)
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(tr("Supprimer")) }, onClick = {
                                menu = false
                                scope.launch {
                                    try { GroupHistory.removeReply(ctx, event) }
                                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                    catch (_: Exception) { error = tr("Suppression enregistrée. Elle sera envoyée à la prochaine synchronisation.") }
                                }
                            })
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FriendAvatar(null, app.superhealth.ui.userName() ?: tr("Vous"), 28.dp)
            OutlinedTextField(text, { text = it.take(1000) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { submit() }),
                placeholder = { Text(tr("Ajouter une réponse…"), style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Mf.colors.divider, focusedBorderColor = Mf.colors.accent),
                trailingIcon = {
                    IconButton(enabled = text.isNotBlank() && !busy, onClick = ::submit) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Rounded.Send, tr("Répondre"), Modifier.size(20.dp))
                    }
                })
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
    profile?.let { FriendSheet(it) { profile = null } }
}
