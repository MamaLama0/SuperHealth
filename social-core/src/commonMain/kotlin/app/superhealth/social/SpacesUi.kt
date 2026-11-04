package app.superhealth.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sources are prepared locally by the host. Only a chosen title and a non-reversible source id are sent. */
data class SpaceSource(val id: String, val title: String, val time: Long)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpacesScreen(repository: SpaceRepository, contacts: List<SpaceContact>, sources: List<SpaceSource>, onBack: () -> Unit,
    refresh: suspend ()->Unit = {repository.sync()}, initialCircle: String? = null, initialPost: String? = null, initialText: String = "", initialMedia: SpaceAttachment? = null, initialSnapshot: FriendPost? = null, now: () -> Long, idHash: (String) -> String, formatDate: (Long) -> String,
    attachmentPicker: @Composable ((SpaceAttachment) -> Unit) -> Unit = {},
    attachmentView: @Composable (SpaceAttachment) -> Unit = {}, startOfDay: (Long) -> Long = { it }) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val listState=rememberLazyListState()
    val composerBring=remember{androidx.compose.foundation.relocation.BringIntoViewRequester()}
    var selected by rememberSaveable { mutableStateOf<String?>(initialCircle) }
    LaunchedEffect(selected){listState.scrollToItem(0)}
    var name by rememberSaveable { mutableStateOf("") }
    val saved = remember(selected) { selected?.let(repository::loadDraft) ?: CircleDraft() }
    var message by remember(selected) { mutableStateOf(saved.text.ifBlank { initialText }) }
    var attachment by remember(selected) { mutableStateOf(saved.media ?: initialMedia) }
    var snapshot by remember(selected) { mutableStateOf(saved.snapshot ?: initialSnapshot) }
    var draftId by remember(selected) { mutableStateOf(saved.id) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var inviting by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }
    var challenge by remember { mutableStateOf(false) }
    var goal by rememberSaveable { mutableStateOf("6") }
    var days by rememberSaveable { mutableStateOf("7") }
    var challengeName by rememberSaveable { mutableStateOf("") }
    var contributing by remember { mutableStateOf<SpaceRecord?>(null) }
    var replyTo by remember(selected) { mutableStateOf(saved.reply) }
    val draftRef = remember(selected) { mutableStateOf(CircleDraft()) }
    draftRef.value=CircleDraft(draftId,message,replyTo,attachment,snapshot)
    val currentDraft=draftRef.value
    LaunchedEffect(selected,message,replyTo,attachment,snapshot,draftId) {
        selected?.let { circle -> kotlinx.coroutines.delay(300); repository.saveDraft(circle,currentDraft) }
    }
    DisposableEffect(selected) { val circle=selected; onDispose { if(circle!=null) repository.saveDraft(circle,draftRef.value) } }

    var edit by remember { mutableStateOf<SpaceRecord?>(null) }
    var edited by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }
    fun run(operation: suspend () -> Unit) { scope.launch {
        if (busy) return@launch
        busy = true; error = null
        try { withContext(Dispatchers.Default) { operation(); refresh() } }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = e.message?.takeIf { !it.contains("://") } ?: tr("Opération enregistrée ou indisponible. Actualisez pour réessayer.") }
        finally { busy = false }
    } }
    LaunchedEffect(Unit) { run { repository.initialize() } }
    val access = state.circles.firstOrNull { it.circle.id == selected }
    val me = state.own
    var targetFocused by remember(initialCircle,initialPost){mutableStateOf(false)}
    LaunchedEffect(selected,initialPost,state.records.size) {
        if(!targetFocused && initialPost!=null && selected==initialCircle) {
            val index=SpaceRules.latest(state.records).filter{it.circle==selected && it.action.kind=="POST"}.sortedByDescending{it.action.created}.indexOfFirst{it.action.id==initialPost}
            if(index>=0){listState.scrollToItem(index+1);targetFocused=true}
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton({ if (selected == null) onBack() else selected = null }) { Text(tr("Retour")) }
            TextButton(enabled = !busy, onClick = { run { } }) { Text(tr("Actualiser")) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        (error ?: state.error)?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=32.dp)) {
            item { Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if (access == null) {
                Text(tr("Cercles privés"), style = MaterialTheme.typography.headlineMedium)
                Text(tr("Choisissez les participants. Les publications d’un cercle sont chiffrées avec ses propres clés."))
                OutlinedTextField(name, { name = it.take(60) }, label = { Text(tr("Nom du cercle")) }, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy && name.isNotBlank(), onClick = { run { val id = repository.createCircle(name); name = ""; selected = id } }) { Text(tr("Créer un cercle")) }
                state.invitations.forEach { invitation ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                        Text(invitation.name, style = MaterialTheme.typography.titleLarge)
                        Text(trf("Invitation de %1\$s", invitation.owner.name))
                        Text(tr("Les nouveaux membres accèdent aux prochains partages. Les anciens messages restent réservés aux membres qui les ont reçus."))
                        if (invitation.accepted) Text(tr("Acceptation en attente de confirmation par le créateur."))
                        else FlowRow {
                            TextButton(enabled = !busy, onClick = { run { repository.accept(invitation.circle, true) } }) { Text(tr("Accepter")) }
                            TextButton(enabled = !busy, onClick = { run { repository.accept(invitation.circle, false) } }) { Text(tr("Refuser")) }
                        }
                    } }
                }
                state.circles.forEach { circle ->
                    OutlinedButton(onClick = { selected = circle.circle.id }, modifier = Modifier.fillMaxWidth()) {
                        Text(circle.circle.name + " · " + circle.circle.members.size + " " + tr(if(circle.circle.members.size==1)"membre" else "membres") + if (circle.departed) " · " + tr("Quitté") else "")
                    }
                }
                if (state.cursors.values.any { it != null }) TextButton(enabled = !busy, onClick = { run { repository.sync(older = true) } }) { Text(tr("Charger l’historique des cercles")) }
            } else {
                val circle = access.circle
                val owner = circle.owner.key == me?.key
                val writable = !access.departed && !circle.closed
                val records = SpaceRules.latest(state.records).filter { it.circle == circle.id }
                Text(circle.name, style = MaterialTheme.typography.headlineMedium)
                Text(trf("%1\$s %2\$s · publications privées", circle.members.size,tr(if(circle.members.size==1)"membre" else "membres")))
                TextButton(enabled=!busy,onClick={run{repository.silence(circle.id,circle.id !in state.silentCircles)}}){Text(tr(if(circle.id in state.silentCircles)"Réactiver les alertes" else "Rendre le cercle silencieux"))}
                TextButton({ showMembers = !showMembers }) { Text(tr("Voir les participants")) }
                if (showMembers) circle.members.forEach { member ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(member.name + if (member.key == circle.owner.key) " · " + tr("Créateur") else "")
                        if (owner && member.key != me?.key) TextButton(enabled = !busy, onClick = { confirm = member.id }) { Text(tr("Retirer")) }
                    }
                }
                if (owner) state.invited[circle.id].orEmpty().forEach { peer ->
                    FlowRow { Text(peer.name + " · " + tr("Invitation en attente"))
                        TextButton(enabled = !busy, onClick = { run { repository.cancelInvitation(circle.id, peer.id) } }) { Text(tr("Annuler")) }
                    }
                }
                if (access.departed) Text(tr("Vous avez quitté ce cercle. Les contenus déjà reçus restent disponibles."))
                if (circle.closed) Text(tr("Ce cercle est fermé."))
                if (writable) {
                    FlowRow {
                        if (owner) TextButton(enabled = !busy && circle.members.size < 8, onClick = { inviting = true }) { Text(tr("Inviter un ami")) }
                        TextButton(enabled = !busy, onClick = { challenge = true }) { Text(tr("Créer un défi")) }
                        TextButton(enabled = !busy, onClick = { confirm = if (owner) "close" else "leave" }) { Text(tr(if (owner) "Fermer le cercle" else "Quitter")) }
                    }
                    replyTo?.let { Text(tr("Réponse à une publication")); TextButton({ replyTo = null }) { Text(tr("Annuler la réponse")) } }
                    OutlinedTextField(message, { message = it.take(1000) }, label = { Text(tr("Votre message")) }, modifier = Modifier.fillMaxWidth().bringIntoViewRequester(composerBring))
                    attachmentPicker { attachment = it }
                    snapshot?.let { chosen ->
                        Text(tr("Aperçu de la publication"));Text(chosen.title);FriendSnapshotView(chosen,preview=true)
                        TextButton({ snapshot=null }){ Text(tr("Retirer l’activité")) }
                    }
                    attachment?.let { Text(tr("Pièce jointe prête")); TextButton({ attachment = null }) { Text(tr("Retirer le média")) } }
                    Text(tr("Visible uniquement par les participants de ce cercle."), style = MaterialTheme.typography.labelSmall)
                    Button(enabled = !busy && (message.isNotBlank() || attachment != null || snapshot != null), onClick = {
                        val text = message; val parent = replyTo; val media = attachment; val chosen=snapshot
                        if(draftId.isBlank()) draftId=repository.actionId()
                        val id=draftId
                        repository.saveDraft(circle.id,CircleDraft(id,text,parent,media,chosen))
                        run { repository.publish(circle.id, SpaceAction(if (parent == null) "POST" else "REPLY", id, now(), text, parent, media = media, snapshot=chosen)); message = ""; replyTo = null; attachment = null; snapshot=null; draftId=""; repository.saveDraft(circle.id,CircleDraft()) }
                    }) { Text(tr("Publier dans ce cercle")) }
                }
                records.filter { it.action.kind == "CHALLENGE" }.sortedByDescending { it.action.created }.forEach { record ->
                    val item = record.action
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                        Text(item.text, style = MaterialTheme.typography.titleLarge)
                        Text(if (item.deleted) tr("Défi annulé") else trf("%1\$s / %2\$s activités", SpaceRules.total(records, record, now()), item.target))
                        Text(trf("Jusqu’au %1\$s", formatDate(item.end)))
                        if (writable && !item.deleted && now() in item.start..item.end) TextButton(enabled = !busy, onClick = { contributing = record }) { Text(tr("Ajouter une activité")) }
                        records.filter { it.action.kind == "CONTRIBUTION" && it.action.parent == item.id && !it.action.deleted }.forEach { contribution ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(contribution.author.name + " · " + (contribution.action.text.ifBlank { tr("1 activité") }))
                                if (writable && contribution.author.key == me?.key) TextButton(enabled = !busy, onClick = { run { repository.publish(circle.id, contribution.action.copy(deleted = true)) } }) { Text(tr("Retirer")) }
                            }
                        }
                        if (writable && !item.deleted && record.author.key == me?.key) TextButton(enabled = !busy, onClick = { run { repository.publish(circle.id, item.copy(deleted = true)) } }) { Text(tr("Annuler le défi")) }
                    } }
                }
            }
            } }
            if(access!=null) {
                val circle=access.circle
                val writable=!access.departed && !circle.closed
                val records=SpaceRules.latest(state.records).filter{it.circle==circle.id}
                items(records.filter { it.action.kind == "POST" }.sortedByDescending { it.action.created },key={it.author.id+":"+it.action.id}) { record ->
                    var replyLimit by rememberSaveable(record.action.id){mutableIntStateOf(2)}
                    val post = record.action
                    val closed = records.any { it.action.kind == "CLOSE_REPLIES" && it.action.parent == post.id && !it.action.deleted }
                    key(record.circle,record.author.id,post.id) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if(post.id in state.rejected) Text(tr("Envoi annulé après la fermeture ou le départ du cercle."),color=MaterialTheme.colorScheme.error)
                        if (post.id in state.pending) Text(tr("En attente d’envoi"), style = MaterialTheme.typography.labelSmall)
                        val bring=remember{androidx.compose.foundation.relocation.BringIntoViewRequester()}
                        LaunchedEffect(initialPost){if(initialPost==post.id){kotlinx.coroutines.delay(200);bring.bringIntoView()}}
                        Text(record.author.name,modifier=Modifier.bringIntoViewRequester(bring), style = MaterialTheme.typography.titleSmall)
                        Text(if (post.deleted) tr("Publication supprimée") else post.text)
                        if (!post.deleted) {
                            post.snapshot?.let { Text(it.title);FriendSnapshotView(it) }
                            post.media?.let { attachmentView(it) }
                            if (post.modified != null) Text(tr("Modifié"), style = MaterialTheme.typography.labelSmall)
                            if (writable) FlowRow {
                                listOf("👏", "💪", "❤️").forEach { emoji ->
                                    val id = me!!.id + idHash(circle.id + "reaction" + post.id).take(16)
                                    val mine = records.firstOrNull { it.author.key == me.key && it.action.id == id }
                                    val count = records.count { it.action.kind == "REACTION" && it.action.parent == post.id && it.action.text == emoji && !it.action.deleted }
                                    TextButton(enabled = !busy, onClick = { run { repository.publish(circle.id,
                                        SpaceAction("REACTION", id, now(), emoji, post.id, deleted = mine?.action?.text == emoji && !mine.action.deleted)) } }) { Text("$emoji $count") }
                                }
                            }
                            val visibleReplies=records.filter { reply -> reply.action.kind == "REPLY" && reply.action.parent == post.id && !reply.action.deleted && records.none { it.action.kind == "HIDE_REPLY" && it.action.parent == reply.action.id && !it.action.deleted } }
                                .sortedBy { it.action.created }
                            if(visibleReplies.size>replyLimit)TextButton({replyLimit+=10}){Text(tr("Afficher les réponses précédentes"))}
                            if(replyLimit>2)TextButton({replyLimit=2}){Text(tr("Réduire"))}
                            visibleReplies.takeLast(replyLimit).forEach { reply -> key(reply.author.id,reply.action.id) {
                                    Text(reply.author.name + " · " + reply.action.text)
                                    reply.action.media?.let { attachmentView(it) }
                                    if (writable && record.author.key == me?.key) TextButton(enabled = !busy, onClick = { run { repository.publish(circle.id,
                                        SpaceAction("HIDE_REPLY", repository.actionId(), now(), parent = reply.action.id)) } }) { Text(tr("Masquer cette réponse")) }
                                    if (writable && reply.author.key == me?.key) TextButton(enabled = !busy, onClick = { run { repository.publish(circle.id, reply.action.copy(deleted = true)) } }) { Text(tr("Supprimer ma réponse")) }
                                } }
                            if (writable && !closed) TextButton({ replyTo = post.id;scope.launch{listState.animateScrollToItem(0);kotlinx.coroutines.delay(100);composerBring.bringIntoView()} }) { Text(tr("Répondre")) }
                            if (closed) Text(tr("Les réponses sont fermées."))
                            if (writable && record.author.key == me?.key) FlowRow {
                                TextButton({ edit = record; edited = post.text }) { Text(tr("Modifier")) }
                                TextButton(enabled = !busy, onClick = { run { repository.publish(circle.id, post.copy(deleted = true)) } }) { Text(tr("Supprimer")) }
                                TextButton(enabled = !busy, onClick = { run { repository.publish(circle.id,
                                    SpaceAction("CLOSE_REPLIES", me!!.id + idHash("close:" + post.id).take(16), now(), parent = post.id, deleted = closed)) } }) { Text(tr(if (closed) "Rouvrir les réponses" else "Fermer les réponses")) }
                            }
                        }
                    } } }
                }
                item {
                if (state.cursors.values.any { it != null }) TextButton(enabled = !busy, onClick = { run { repository.sync(older = true) } }) { Text(tr("Charger l’historique des cercles")) }
                }
            }
        }
    }
    if (inviting && access != null) AlertDialog(onDismissRequest = { inviting = false }, title = { Text(tr("Inviter un ami")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(tr("Une version compatible est nécessaire. Actualisez les amis pour recevoir leurs capacités."))
            contacts.filter { peer -> access.circle.members.none { it.id == peer.id } }.forEach { peer ->
                TextButton(enabled = !busy, onClick = { inviting = false; run { repository.invite(access.circle.id, peer) } }) { Text(peer.name) }
            }
        } }, confirmButton = { TextButton({ inviting = false }) { Text(tr("Fermer")) } })
    if (challenge && access != null) AlertDialog(onDismissRequest = { challenge = false }, title = { Text(tr("Créer un défi")) },
        text = { Column {
            OutlinedTextField(challengeName, { challengeName = it.take(100) }, label = { Text(tr("Objectif commun")) })
            OutlinedTextField(goal, { goal = it.filter(Char::isDigit).take(4) }, label = { Text(tr("Nombre d’activités")) })
            OutlinedTextField(days, { days = it.filter(Char::isDigit).take(2) }, label = { Text(tr("Durée en jours")) })
        } }, confirmButton = { TextButton(enabled = !busy && challengeName.isNotBlank() && (goal.toIntOrNull() ?: 0) in 1..1000 && (days.toIntOrNull() ?: 0) in 1..90,
            onClick = { challenge = false; run { val created = now(); val start = startOfDay(created); repository.publish(access.circle.id,
                SpaceAction("CHALLENGE", repository.actionId(), created, challengeName, target = goal.toInt(), start = start, end = start + days.toLong() * 86400000)) } }) { Text(tr("Créer")) } },
        dismissButton = { TextButton({ challenge = false }) { Text(tr("Annuler")) } })
    contributing?.let { target -> AlertDialog(onDismissRequest = { contributing = null }, title = { Text(tr("Choisir une activité")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(tr("Seul le compteur est partagé. Vos statistiques et votre parcours restent privés."))
            val available = sources.filter { it.time in target.action.start..target.action.end }
            if (available.isEmpty()) Text(tr("Aucune activité enregistrée sur cette période."))
            available.forEach { source -> TextButton(enabled = !busy, onClick = {
                contributing = null
                run { val author = repository.state.value.own!!; val token=repository.sourceToken(target.circle,source.id);val id = author.id + idHash(target.circle + target.action.id + token).take(16)
                    repository.publish(target.circle, SpaceAction("CONTRIBUTION", id, now(), parent = target.action.id, source = token)) }
            }) { Text(source.title) } }
        } }, confirmButton = { TextButton({ contributing = null }) { Text(tr("Fermer")) } }) }
    edit?.let { record -> AlertDialog(onDismissRequest = { edit = null }, title = { Text(tr("Modifier le texte")) },
        text = { OutlinedTextField(edited, { edited = it.take(1000) }) },
        confirmButton = { TextButton(enabled = !busy && edited.isNotBlank(), onClick = { edit = null; run { repository.publish(record.circle, record.action.copy(text = edited, modified = now())) } }) { Text(tr("Enregistrer")) } },
        dismissButton = { TextButton({ edit = null }) { Text(tr("Annuler")) } }) }
    if (confirm != null && access != null) AlertDialog(onDismissRequest = { confirm = null }, title = { Text(tr("Confirmer ce changement ?")) },
        text = { Text(tr("Les clés des prochains partages sont renouvelées lors d’un retrait. Les contenus déjà reçus peuvent être conservés.")) },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val action = confirm; confirm = null
            run { when (action) { "close" -> repository.closeCircle(access.circle.id); "leave" -> repository.leave(access.circle.id); else -> repository.removeMember(access.circle.id, action!!) } }
        }) { Text(tr("Confirmer")) } }, dismissButton = { TextButton({ confirm = null }) { Text(tr("Annuler")) } })
}
