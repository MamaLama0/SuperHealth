package app.superhealth.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.superhealth.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@kotlinx.serialization.Serializable
data class TimelineDraft(val id: String, val text: String = "", val source: FriendPost? = null, val media: FriendMedia? = null)

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
fun FriendTimelineScreen(repository: FriendTimeline, ownId: String, onBack: () -> Unit, onFriends: () -> Unit, onCircles: () -> Unit,
    refresh: suspend () -> Unit, now: () -> Long, randomId: () -> String, sources: List<FriendPost>,
    savedDraft: TimelineDraft, saveDraft: (TimelineDraft) -> Unit, clearDraft: () -> Unit,
    picker: @Composable ((FriendMedia) -> Unit) -> Unit, mediaView: @Composable (FriendMedia, TimelinePeer) -> Unit,
    block: suspend (TimelinePeer) -> Unit, initialOwner:String?=null,initialPost:String?=null, notificationBell:@Composable ()->Unit={}, spaces: SpaceRepository?=null, shareCircle: (TimelineDraft)->Unit = {}, formatDate: (Long) -> String) {
    val state by repository.state.collectAsState()
    val controls = spaces?.state?.collectAsState()?.value
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var composing by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf(savedDraft.text) }
    var source by remember { mutableStateOf(savedDraft.source) }
    var media by remember { mutableStateOf(savedDraft.media) }
    var draftId by rememberSaveable { mutableStateOf(savedDraft.id) }
    var detailed by rememberSaveable { mutableStateOf(false) }
    var shareRoute by rememberSaveable { mutableStateOf(false) }
    var chooseSource by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf<TimelinePeer?>(null) }
    var edited by remember { mutableStateOf<Pair<FriendPost,String>?>(null) }
    fun run(operation:suspend ()->Unit) { scope.launch {
        if(busy)return@launch
        busy=true;error=null
        try { withContext(Dispatchers.Default){operation();refresh()} }
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){error=e.message?.takeIf { !it.contains("://") }?:tr("Synchronisation indisponible. Réessayez.")}
        finally{busy=false}
    } }
    val latestDraft by rememberUpdatedState(TimelineDraft(draftId,text,source,media))
    DisposableEffect(Unit) { onDispose { saveDraft(latestDraft) } }
    LaunchedEffect(text,source,media,draftId){ kotlinx.coroutines.delay(250);saveDraft(TimelineDraft(draftId,text,source,media)) }
    LaunchedEffect(Unit){run{}}
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal=16.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onBack){Text(tr("Retour"))}
            TextButton(enabled=!busy,onClick={run{}}){Text(tr("Actualiser"))}
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        (error?:state.error)?.let { Text(tr(it),color=MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            item {
                Text(tr("Groupe"),style=MaterialTheme.typography.headlineLarge)
                notificationBell()
                FlowRow { TextButton(onFriends){Text(tr("Amis"))};TextButton(onCircles){Text(tr("Cercles et défis"))} }
                Button({composing=!composing}){Text(tr("Créer une publication"))}
            }
            if(composing)item {
                OutlinedTextField(text,{text=it.take(500)},label={Text(tr("Votre message"))},modifier=Modifier.fillMaxWidth())
                picker { media=it }
                media?.let { Text(tr("Média conservé pour l’envoi"));TextButton({media=null}){Text(tr("Retirer le média"))} }
                TextButton({chooseSource=true}){Text(tr("Partager une activité"))}
                source?.let {
                    Text(it.title); Text(it.detail)
                    Row { Checkbox(detailed, { on -> detailed = on }); Text(tr("Statistiques détaillées")) }
                    if (it.activity?.get("route")?.let { route -> route !is kotlinx.serialization.json.JsonNull } == true) Row {
                        Checkbox(shareRoute, { on -> shareRoute = on }); Text(tr("Partager le parcours"))
                    }
                    FriendSnapshotView(it.forSharing(detailed, shareRoute), preview = true)
                    TextButton({ source = null }) { Text(tr("Retirer")) }
                }
                TextButton(enabled=!busy,onClick={shareCircle(TimelineDraft(draftId,text,source?.forSharing(detailed,shareRoute),media));onCircles()}) { Text(tr("Choisir un cercle privé")) }
                Text(tr("Visible par vos amis · seules les informations choisies sont partagées"))
                Button(enabled=!busy&&(text.isNotBlank()||media!=null||source!=null),onClick={
                    val post=(source?.forSharing(detailed, shareRoute)?:FriendPost(draftId,now())).copy(id=draftId,created=now(),text=text,media=media,
                        kind=source?.kind?:media?.let { if(it.mime.startsWith("video/"))"VIDEO" else "PHOTO" }?:"TEXT")
                    run{repository.publish(post);text="";source=null;media=null;draftId=randomId();clearDraft();composing=false}
                }){Text(tr("Publier dans le Groupe"))}
            }
            if(state.hidden.isNotEmpty())item{TextButton(enabled=!busy,onClick={run{repository.restoreHidden()}}){Text(tr("Réafficher les publications"))}}
            items(repository.posts().filter{initialPost==null || (it.first.id==initialPost && it.second.id==initialOwner)},key={it.second.id+":"+it.first.id}) { (post,peer)->
                var reply by rememberSaveable(post.id,peer.id){mutableStateOf("")}
                var visible by rememberSaveable(post.id,peer.id){mutableStateOf(2)}
                var replyingTo by remember { mutableStateOf<FriendEvent?>(null) }
                val closed=controls?.let { spaces.legacyClosed(peer.id,post.id) }==true
                var menu by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                        Text(peer.name,style=MaterialTheme.typography.titleMedium)
                        TextButton({menu=!menu}){Text(tr("Options"))}
                    }
                    if(menu) {
                        TextButton(enabled=!busy,onClick={run{repository.silence(peer.id,post.id,"${peer.id}/${post.id}" !in state.silent)}}){Text(tr(if("${peer.id}/${post.id}" in state.silent)"Réactiver les alertes" else "Rendre la discussion silencieuse"))}
                        if(peer.id==ownId)FlowRow {
                            TextButton({edited=post to post.text;menu=false}){Text(tr("Modifier"))}
                            TextButton(enabled=!busy,onClick={run{repository.delete(post)}}){Text(tr("Supprimer"))}
                        }else FlowRow {
                            TextButton(enabled=!busy,onClick={run{repository.hide(peer.id,post.id)}}){Text(tr("Masquer"))}
                            TextButton(enabled=!busy,onClick={run{repository.mute(peer.id,true)}}){Text(tr("Mettre en sourdine"))}
                            TextButton(enabled=!busy,onClick={blocking=peer;menu=false}){Text(tr("Bloquer"))}
                        }
                    }
                    if(peer.id==ownId && spaces!=null)TextButton(enabled=!busy,onClick={run{spaces.moderateLegacy(post.id,"CLOSE",active=!closed);spaces.sync()}}){Text(tr(if(closed)"Rouvrir les réponses" else "Fermer les réponses"))}
                    if(controls?.let{spaces.legacyModified(peer.id,post.id)}==true)Text(tr("Modifié"))
                    Text(formatDate(post.created),style=MaterialTheme.typography.labelSmall)
                    if(post.title.isNotBlank())Text(post.title,style=MaterialTheme.typography.titleLarge)
                    if(post.detail.isNotBlank())Text(post.detail)
                    FriendSnapshotView(post)
                    if(post.text.isNotBlank())Text(post.text)
                    post.media?.let { mediaView(it,peer) }
                    if(post.id in state.pending)Text(tr("En attente d’envoi"))
                    Row {
                        val counts=repository.reactions(peer.id,post.id)
                        listOf("CLAP" to "👏","HEART" to "❤️","LIKE" to "👍").forEach { (kind,emoji)->
                            TextButton(enabled=!busy,onClick={run{repository.react(peer.id,post.id,kind)}}){Text(emoji+" "+(counts[kind]?:0))}
                        }
                    }
                    val replies=repository.replies(peer.id,post.id)
                    if(replies.size>visible)TextButton({visible+=10}){Text(tr("Afficher les réponses précédentes"))}
                    replies.takeLast(visible).forEach { event->
                        Text(event.name+" · "+event.reply!!.text)
                        event.reply?.to?.let { target->Text(tr("En réponse à")+" "+(replies.firstOrNull{it.id==target.id}?.name?:tr("Commentaire indisponible")),style=MaterialTheme.typography.labelSmall) }
                        if(!closed)TextButton({replyingTo=event}){Text(tr("Répondre"))}
                        if(peer.id==ownId && spaces!=null)TextButton(enabled=!busy,onClick={run{spaces.moderateLegacy(post.id,"HIDE",event.id);spaces.sync()}}){Text(tr("Masquer cette réponse"))}
                        if(repository.isOwn(event)){
                            TextButton(enabled=!busy,onClick={run{repository.deleteReply(event)}}){Text(tr("Supprimer ma réponse"))}
                        }
                    }
                    Text(tr("Votre réponse est visible par les personnes qui peuvent lire cette publication."),style=MaterialTheme.typography.labelSmall)
                    if(closed)Text(tr("Les réponses sont fermées."))
                    replyingTo?.let{Text(it.name);TextButton({replyingTo=null}){Text(tr("Annuler la réponse"))}}
                    OutlinedTextField(reply,{reply=it.take(1000)},enabled=!closed,label={Text(tr("Ajouter une réponse…"))},modifier=Modifier.fillMaxWidth())
                    TextButton(enabled=!busy&&!closed&&reply.isNotBlank(),onClick={val body=reply;run{repository.reply(peer.id,post.id,body,replyingTo);reply="";replyingTo=null}}){Text(tr("Répondre"))}
                } }
            }
            if(repository.posts().isEmpty())item{Text(tr("Votre Groupe commence ici"))}
            if(initialPost!=null && repository.posts().none{it.first.id==initialPost && it.second.id==initialOwner})item{Text(tr("Publication indisponible. Actualisez pour réessayer."))}
            if(state.cursors.values.any{it!=null})item{TextButton(enabled=!busy,onClick={run{repository.sync(older=true)}}){Text(tr("Charger les publications plus anciennes"))}}
            if (state.muted.isNotEmpty()) item {
                Column {
                    Text(tr("Amis en sourdine"))
                    state.muted.forEach { id ->
                        TextButton(enabled = !busy, onClick = { run { repository.mute(id, false) } }) {
                            Text(tr("Réactiver") + " · " + id.take(8))
                        }
                    }
                }
            }
        }
    }
    if(chooseSource)AlertDialog(onDismissRequest={chooseSource=false},title={Text(tr("Choisir une activité"))},text={
        Column(Modifier.verticalScroll(rememberScrollState())){sources.forEach { item->TextButton({source=item;chooseSource=false}){Text(item.title+" · "+formatDate(item.created))} }}
    },confirmButton={TextButton({chooseSource=false}){Text(tr("Fermer"))}})
    edited?.let { (post,value)->AlertDialog(onDismissRequest={edited=null},title={Text(tr("Modifier le texte"))},
        text={OutlinedTextField(value,{edited=post to it.take(500)})},confirmButton={TextButton(enabled=!busy,onClick={edited=null;run{repository.edit(post,value);spaces?.moderateLegacy(post.id,"EDIT");spaces?.sync()}}){Text(tr("Enregistrer"))}},
        dismissButton={TextButton({edited=null}){Text(tr("Annuler"))}}) }
    blocking?.let { peer->AlertDialog(onDismissRequest={blocking=null},title={Text(tr("Bloquer cette personne ?"))},text={Text(tr("Ses interventions sont masquées. Ses accès futurs sont révoqués. Vous quittez les cercles communs que vous ne gérez pas."))},confirmButton={TextButton(enabled=!busy,onClick={blocking=null;run{block(peer)}}){Text(tr("Bloquer"))}},dismissButton={TextButton({blocking=null}){Text(tr("Annuler"))}}) }

}
