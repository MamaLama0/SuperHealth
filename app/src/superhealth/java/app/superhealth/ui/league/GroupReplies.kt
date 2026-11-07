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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material.icons.rounded.Image
import app.superhealth.league.ReplyMedia
import app.superhealth.league.SocialReactions

/** A real conversation in the post: recent replies are visible without opening another panel. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    val field = rememberSaveable(author, post.id, saver = TextFieldState.Saver) { TextFieldState() }
    // Image ou GIF joint : envoyé dès qu'il est choisi (galerie ou clavier), la réponse ne porte que sa référence.
    var attachment by remember { mutableStateOf<Pair<SocialPosts.Media, String>?>(null) }
    var attaching by remember { mutableStateOf(false) }
    var reactingTo by remember { mutableStateOf<GroupHistory.Event?>(null) }
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
    fun attach(uri: android.net.Uri) {
        if (attaching || closed) return
        scope.launch {
            attaching = true; error = null
            try { attachment = SocialPosts.uploadReplyMedia(ctx, uri) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message?.let(::tr) ?: tr("Image impossible à joindre. Réessayez.") }
            finally { attaching = false }
        }
    }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::attach) }
    fun react(comment: GroupHistory.Event, kind: String) {
        reactingTo = null
        scope.launch {
            try { if (!SocialReactions.reactToComment(ctx, comment, kind)) error = tr("Réaction enregistrée. Elle sera envoyée à la prochaine synchronisation.") }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = tr("Réaction impossible. Réessayez.") }
        }
    }
    fun submit() {
        val text = field.text.toString()
        if ((text.isBlank() && attachment == null) || busy || attaching || closed) return
        if (parentId != null && parent == null) { error = tr("Le commentaire n’est plus disponible."); return }
        val submitted = text.trim()
        val media = attachment
        val submittedParent = parent
        scope.launch {
            busy = true; error = null
            try {
                val sent = GroupHistory.reply(ctx, author, post, media?.let { ReplyMedia.encode(submitted, it.first, it.second) } ?: submitted, submittedParent)
                if (field.text.toString().trim() == submitted) { field.clearText(); focus.clearFocus(); keyboard?.hide() }
                if (attachment == media) attachment = null
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
                        val body = event.reply!!.text
                        val attached = remember(body) { ReplyMedia.parse(body) }
                        val caption = remember(body) { ReplyMedia.caption(body) }
                        if (caption.isNotBlank()) Text(caption, style = MaterialTheme.typography.bodyMedium, color = Mf.colors.text)
                        if (attached != null) ReplyImage(attached, Modifier.padding(top = 6.dp))
                        else if (caption != body.trim()) Text(tr("Image indisponible"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary)
                        val replyAuthor = remember(event.key) { GroupHistory.author(event.key) }
                        val counts = remember(history, league.friends, event.id) { GroupHistory.reactionCounts(replyAuthor, event.id) }
                        val mine = GroupHistory.selectedReaction(replyAuthor, event.id)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                parentKey = event.key; parentId = event.id; error = null
                                replyFocus.requestFocus(); keyboard?.show()
                            }, enabled = !busy && !closed, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 36.dp)) {
                                Text(tr("Répondre"), style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(4.dp))
                            Surface(onClick = { reactingTo = event }, shape = RoundedCornerShape(50), color = if (mine != null) Mf.colors.accent.copy(alpha = .12f) else Color.Transparent) {
                                Box(Modifier.heightIn(min = 32.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                    if (mine == null) Icon(Icons.Rounded.AddReaction, tr("Réagir"), Modifier.size(16.dp), tint = Mf.colors.textTertiary)
                                    else Text(SocialReactions.emoji(mine), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            counts.entries.sortedByDescending { it.value }.take(4).forEach { (kind, count) ->
                                if (kind == mine && count == 1) return@forEach
                                Surface(onClick = { react(event, kind) }, shape = RoundedCornerShape(50), color = Mf.colors.elevated, modifier = Modifier.padding(start = 4.dp)) {
                                    Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(SocialReactions.emoji(kind), style = MaterialTheme.typography.labelLarge)
                                        if (count > 1) Text(" $count", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                                    }
                                }
                            }
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
        if (!closed && (attachment != null || attaching)) Row(Modifier.padding(start = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                attachment?.let { (m, e) -> ReplyImage(ReplyMedia.Attached(m, e), Modifier.heightIn(max = 120.dp).widthIn(max = 160.dp)) }
                    ?: Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(Mf.colors.elevated), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                if (attachment != null) Surface(onClick = { attachment = null }, shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = .6f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, tr("Retirer l'image"), Modifier.size(14.dp), tint = Color.White) }
                }
            }
        }
        if (!closed) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FriendAvatar(null, app.superhealth.ui.userName() ?: tr("Vous"), 28.dp)
            OutlinedTextField(field, enabled = !closed,
                inputTransformation = { if (length > 1000) revertAllChanges() },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                onKeyboardAction = { submit() },
                placeholder = { Text(tr("Ajouter une réponse…"), style = MaterialTheme.typography.bodyMedium) },
                // GIF et images du clavier (GIF de Gboard, autocollants…) : joints comme une image de la galerie.
                modifier = Modifier.weight(1f).focusRequester(replyFocus).contentReceiver { content ->
                    if (!content.hasMediaType(MediaType.Image)) return@contentReceiver content
                    content.consume { item -> item.uri?.let { attach(it); true } ?: false }
                }, shape = RoundedCornerShape(24.dp), lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Mf.colors.divider, focusedBorderColor = Mf.colors.accent),
                leadingIcon = {
                    IconButton(enabled = !busy && !attaching && attachment == null, onClick = {
                        picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Icon(Icons.Rounded.Image, tr("Joindre une image ou un GIF"), Modifier.size(20.dp)) }
                },
                trailingIcon = {
                    IconButton(enabled = (field.text.isNotBlank() || attachment != null) && !busy && !attaching && !closed, onClick = ::submit) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Rounded.Send, tr("Répondre"), Modifier.size(20.dp))
                    }
                })
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
    reactingTo?.let { comment ->
        val mine = GroupHistory.selectedReaction(GroupHistory.author(comment.key), comment.id)
        GroupReactionPicker(mine, onDismiss = { reactingTo = null }, onSelect = { react(comment, it) })
    }
    profile?.let { FriendSheet(it) { profile = null } }
    stranger?.let { StrangerSheet(it.key, it.name) { stranger = null } }
}

/** Image ou GIF d'une réponse : téléchargé et déchiffré à la demande, un GIF reste animé (Android 9 et plus). */
@Composable
internal fun ReplyImage(attached: ReplyMedia.Attached, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var full by remember { mutableStateOf(false) }
    val file by produceState<java.io.File?>(null, attached.m.id) {
        value = runCatching { SocialPosts.downloadMedia(ctx, attached.m, null, attached.e) }.getOrNull()
    }
    val shape = RoundedCornerShape(14.dp)
    val ratio = remember(file) { file?.let { f ->
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(f.path, o); if (o.outWidth > 0 && o.outHeight > 0) o.outWidth.toFloat() / o.outHeight else null
    } }
    Box(modifier.widthIn(max = 240.dp).heightIn(min = 60.dp, max = 260.dp).then(if (ratio != null) Modifier.aspectRatio(ratio.coerceIn(0.5f, 2.5f)) else Modifier.size(160.dp, 120.dp))
        .clip(shape).background(Mf.colors.elevated).clickable(enabled = file != null) { full = true }, contentAlignment = Alignment.Center) {
        val f = file
        if (f == null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else AnimatedImage(f, attached.m.mime == "image/gif", Modifier.fillMaxSize())
    }
    if (full && file != null) androidx.compose.ui.window.Dialog({ full = false }, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .92f)).clickable { full = false }, contentAlignment = Alignment.Center) {
            AnimatedImage(file!!, attached.m.mime == "image/gif", Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AnimatedImage(file: java.io.File, gif: Boolean, modifier: Modifier) {
    if (gif && android.os.Build.VERSION.SDK_INT >= 28) androidx.compose.ui.viewinterop.AndroidView({ c ->
        android.widget.ImageView(c).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true }
    }, modifier, update = { view ->
        if (view.tag != file.path) {
            view.tag = file.path
            val drawable = runCatching { android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(file)) }.getOrNull()
            view.setImageDrawable(drawable)
            (drawable as? android.graphics.drawable.AnimatedImageDrawable)?.start()
        }
    }) else {
        val bitmap = remember(file.path) { android.graphics.BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
        bitmap?.let { androidx.compose.foundation.Image(it, tr("Image de la réponse"), modifier, contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
    }
}
