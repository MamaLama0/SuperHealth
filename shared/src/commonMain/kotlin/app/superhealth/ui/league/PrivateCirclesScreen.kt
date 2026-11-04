package app.superhealth.ui.league

import app.superhealth.platform.*
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import app.superhealth.league.*
import app.superhealth.social.*
import app.superhealth.training.WorkoutApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object CircleShareHandoff { var draft:TimelineDraft?=null }

@Composable
fun PrivateCirclesScreen(nav: NavHostController,initialCircle:String?=null,initialPost:String?=null) {
    val ctx = LocalContext.current
    val handoff=remember { CircleShareHandoff.draft.also { CircleShareHandoff.draft=null } }
    val media=handoff?.media?.let{item->SocialSpaces.repository(ctx).state.value.own?.let { own->SpaceAttachment(item.id,item.key,item.mime,item.hash,item.bytes,own.endpoint) }}
    if(SocialLifecycle.pending(ctx)){androidx.compose.material3.TextButton({nav.navigate("group/lifecycle")}){androidx.compose.material3.Text(app.superhealth.ui.tr("Poursuivre la révocation"))};return}

    val league by League.state.collectAsState()
    val workouts by WorkoutApp.store.state.collectAsState()
    val sources by produceState<List<SpaceSource>>(emptyList(), workouts.history) {
        value = withContext(Dispatchers.IO) {
            workouts.history.map { SpaceSource("workout:" + it.id, it.name, it.startTime) } +
                app.superhealth.bridge.Bridge.activities(0).filter { it.gymId == null }.map { SpaceSource("activity:" + it.key, it.title, it.start) }
        }
    }
    SpacesScreen(SocialSpaces.repository(ctx), league.friends.filterNot { it.key in league.blockedSocialKeys }.mapNotNull { it.space },
        sources, refresh={Relay.sync(ctx);SocialSpaces.sync(ctx)},initialCircle=initialCircle,initialPost=initialPost, initialText=handoff?.text.orEmpty(),initialMedia=media,initialSnapshot=handoff?.source,onBack = { nav.popBackStack() }, now = System::currentTimeMillis,
        idHash = { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } },
        formatDate = { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy", app.superhealth.ui.Lang.locale)) },
        attachmentPicker = { selected -> FriendMediaPicker { media ->
            val endpoint = SocialSpaces.repository(ctx).state.value.own!!.endpoint
            selected(SpaceAttachment(media.id, media.key, media.mime, media.hash, media.bytes, endpoint))
        } }, attachmentView = { media -> PortableMediaView(FriendMedia(media.id, media.key, media.mime, media.hash, media.bytes), media.endpoint) },
        startOfDay = { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() })
}
