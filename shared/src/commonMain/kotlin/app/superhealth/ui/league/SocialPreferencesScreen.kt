package app.superhealth.ui.league
import app.superhealth.platform.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import app.superhealth.league.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.tr
import kotlinx.coroutines.launch
@Composable fun SocialPreferencesScreen(nav:NavHostController) {
 val ctx=LocalContext.current;val state by League.state.collectAsState();val scope=rememberCoroutineScope()
 val timeline=FriendSocial.repository(ctx);val reading by timeline.state.collectAsState()
 DetailScaffold(tr("Contenus masqués et sourdines"),{nav.popBackStack()}) {
  Text(tr("Ces choix concernent la lecture et les alertes sur ce téléphone."))
  TextButton(enabled=reading.hidden.isNotEmpty(),onClick={scope.launch{timeline.restoreHidden()}}){Text(tr("Réafficher les publications"))}
  TextButton(enabled=reading.silent.isNotEmpty(),onClick={scope.launch{timeline.restoreSilent()}}){Text(tr("Réactiver les alertes des discussions"))}
  if(state.pendingSocialRevocations.isNotEmpty())Text(tr("Des accès attendent leur révocation. Une synchronisation réessaiera."))
  state.blockedSocialKeys.forEach{key->Text(League.fingerprint(key));TextButton({League.configure(ctx){it.copy(blockedSocialKeys=it.blockedSocialKeys-key)}}){Text(tr("Débloquer cette identité"))}}
  reading.muted.forEach{id->TextButton({scope.launch{timeline.mute(id,false)}}){Text(tr("Réactiver")+" · "+(state.friends.firstOrNull{it.id==id}?.shown?:id.take(8)))}}
 }
}
