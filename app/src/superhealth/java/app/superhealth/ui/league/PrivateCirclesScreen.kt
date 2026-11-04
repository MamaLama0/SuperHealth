package app.superhealth.ui.league

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import app.superhealth.league.*
import app.superhealth.social.*
import app.superhealth.training.WorkoutApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object CircleShareHandoff {
    var text=""
    var media:SpaceAttachment?=null
    var snapshot:FriendPost?=null
}

@Composable
fun PrivateCirclesScreen(nav: NavHostController,initialCircle:String?=null,initialPost:String?=null) {
    val ctx = LocalContext.current
    val handoff=remember { Triple(CircleShareHandoff.text,CircleShareHandoff.media,CircleShareHandoff.snapshot).also { CircleShareHandoff.text="";CircleShareHandoff.media=null;CircleShareHandoff.snapshot=null } }
    if (SocialLifecycle.pending(ctx)) {
        androidx.compose.material3.Text(app.superhealth.ui.tr("Terminez la sécurisation du Groupe avant un nouveau partage."))
        androidx.compose.material3.TextButton(onClick = { nav.navigate("group/lifecycle") }) { androidx.compose.material3.Text(app.superhealth.ui.tr("Poursuivre la révocation")) }
        return
    }
    val league by League.state.collectAsState()
    val workouts by WorkoutApp.store.state.collectAsState()
    val sources by produceState<List<SpaceSource>>(emptyList(), workouts.history) {
        value = withContext(Dispatchers.IO) {
            workouts.history.map { SpaceSource("workout:" + it.id, it.name, it.startTime) } +
                app.superhealth.bridge.Bridge.activities(0).filter { it.gymId == null }.map { SpaceSource("activity:" + it.key, it.title, it.start) }
        }
    }
    SpacesScreen(SocialSpaces.repository(ctx), league.friends.filterNot { SocialPreferences.blocked(it.key) }.mapNotNull { it.space },
        sources, refresh={Relay.sync(ctx);SocialSpaces.sync(ctx)},initialCircle=initialCircle,initialPost=initialPost, initialText=handoff.first,initialMedia=handoff.second,initialSnapshot=handoff.third, onBack = { nav.popBackStack() }, now = System::currentTimeMillis,
        idHash = { java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } },
        formatDate = { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy", app.superhealth.ui.Lang.locale)) },
        attachmentPicker = { selected -> CircleMediaPicker(selected) }, attachmentView = { CircleMediaView(it) },
        startOfDay = { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() })
}
