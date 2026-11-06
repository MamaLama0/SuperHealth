package app.superhealth.ui.league

import app.superhealth.platform.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.league.*
import app.superhealth.social.*
import app.superhealth.training.WorkoutApp
import app.superhealth.ui.go
import app.superhealth.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@Composable
fun FriendsFeedScreen(nav:NavHostController,initialOwner:String?=null,initialPost:String?=null) {
    val ctx=LocalContext.current
    if(SocialLifecycle.pending(ctx)){TextButton({nav.go("group/lifecycle")}){Text(tr("Poursuivre la révocation"))};return}
    val league by League.state.collectAsState()
    val training by WorkoutApp.store.state.collectAsState()
    if(Relay.base()==null){Column{Text(tr("Activez le serveur dans les préférences sociales."));TextButton({nav.go("league/settings")}){Text(tr("Réglages"))}};return}
    val repository=remember { FriendSocial.repository(ctx) }
    val draftFile=remember { File(ctx.filesDir,"league/friend-draft.json") }
    val json=remember { Json { ignoreUnknownKeys=true;encodeDefaults=true } }
    val saved=remember { (runCatching { json.decodeFromString(TimelineDraft.serializer(),draftFile.readText()) }.getOrElse { TimelineDraft(UUID.randomUUID().toString().replace("-","")) })
        // Carte Athlète partagée depuis les cartes : le brouillon reprend son texte.
        .let { d -> app.superhealth.cards.CardShare.draft.value?.let { text -> app.superhealth.cards.CardShare.draft.value=null; d.copy(text=text) } ?: d } }
    val sources by produceState<List<FriendPost>>(emptyList(),training.history){
        value=withContext(Dispatchers.Default){training.history.map { FriendPost(UUID.randomUUID().toString().replace("-",""),it.startTime,kind="WORKOUT",title=it.name,detail="${it.durationSec/60} min",workout=Json.encodeToJsonElement(SharedActivity.Strength.serializer(),SharedActivity.strength(it,WorkoutApp.db.value)).jsonObject) }+
            app.superhealth.bridge.Bridge.activities(0).filter { it.gymId==null }.map { FriendPost(UUID.randomUUID().toString().replace("-",""),it.start,kind=if(it.kind.name=="RUN")"RUN" else "WORKOUT",title=it.title,detail="${it.minutes} min",activity=Json.encodeToJsonElement(SharedActivity.Outdoor.serializer(),SharedActivity.Outdoor(it.kind.name,it.start,((it.end-it.start)/1000.0).coerceAtLeast(1.0),it.distanceM)).jsonObject) }+GroupRecords.recent().map { GroupRecords.draft(it) }}
    }
    FriendTimelineScreen(repository,League.myId(),{nav.popBackStack()},{nav.go("friends")},{nav.go("group/circles")},
        refresh={Relay.sync(ctx)},now={System.currentTimeMillis()},randomId={UUID.randomUUID().toString().replace("-","")},sources=sources,savedDraft=saved,
        saveDraft={draft->val f=AtomicFile(draftFile);val out=f.startWrite();try{out.write(json.encodeToString(TimelineDraft.serializer(),draft).toByteArray());f.finishWrite(out)}catch(e:Exception){f.failWrite(out);throw e}},
        clearDraft={draftFile.delete()},picker={selected->FriendMediaPicker(selected)},mediaView={media,peer->PortableMediaView(media,peer.endpoint)},
        initialOwner=initialOwner,initialPost=initialPost,notificationBell={NotificationBell(nav)},block={peer->SocialPrivacy.block(ctx,peer.key)},spaces=SocialSpaces.repository(ctx),
        shareCircle={draft->CircleShareHandoff.draft=draft.copy(source=draft.source?.boundedForCircle())},
        formatDate={Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy",app.superhealth.ui.Lang.locale))})
}

@Composable
internal fun FriendMediaPicker(selected:(FriendMedia)->Unit) {
    val ctx=LocalContext.current;val scope=rememberCoroutineScope();var error by remember { mutableStateOf<String?>(null) };var busy by remember{mutableStateOf(false)}
    val picker=rememberFilePicker { files->files.firstOrNull()?.let { file->scope.launch{
        busy=true;error=null
        try{selected(withContext(Dispatchers.Default){FriendSocial.prepareMedia(ctx,file)})}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){error=tr("Média illisible ou trop volumineux.")}
        finally{busy=false}
    } } }
    TextButton(enabled=!busy,onClick=picker){Text(tr("Photo ou vidéo"))}
    if(busy)LinearProgressIndicator()
    error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    Text(tr("Les photos sont réduites et leurs métadonnées retirées. Les vidéos conservent leur fichier d’origine."),style=MaterialTheme.typography.labelSmall)
}
@Composable
internal fun PortableMediaView(media:FriendMedia,endpoint:String) {
    val ctx=LocalContext.current;var requested by remember(media.id){mutableStateOf(false)};var error by remember{mutableStateOf(false)}
    val file by produceState<File?>(null,media.id,requested){value=null;if(requested)try{value=withContext(Dispatchers.Default){FriendSocial.download(ctx,media,endpoint)}}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){error=true}}
    val loaded=file
    if(loaded==null)TextButton(onClick={error=false;requested=!requested}){Text(tr(if(error)"Réessayer" else "Afficher le média"))}
    else if(media.mime.startsWith("video/"))NativeVideo("file://"+loaded.path,true,modifier=Modifier.fillMaxWidth().height(240.dp))
    else { val image=remember(loaded.path){BitmapFactory.decodeByteArray(loaded.readBytes())?.asImageBitmap()};image?.let { Image(it,tr("Photo partagée"),Modifier.fillMaxWidth().heightIn(max=400.dp)) } }
}
