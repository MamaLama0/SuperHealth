package app.superhealth.ui.league

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import app.superhealth.league.League
import app.superhealth.league.SocialPreferences
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.tr
import app.superhealth.ui.trf

@Composable
fun SocialPreferencesScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val state by League.state.collectAsState()
    DetailScaffold(tr("Contenus masqués et sourdines"), { nav.popBackStack() }) {
        Text(tr("Ces choix concernent la lecture et les alertes sur ce téléphone."))
        Text(trf("Publications masquées · %1\$s", state.hiddenSocialPosts.size))
        TextButton(enabled = state.hiddenSocialPosts.isNotEmpty(), onClick = { SocialPreferences.restoreHidden(ctx) }) {
            Text(tr("Réafficher les publications"))
        }
        Text(trf("Discussions silencieuses · %1\$s", state.silentSocialThreads.size))
        TextButton(enabled = state.silentSocialThreads.isNotEmpty(), onClick = { SocialPreferences.restoreThreads(ctx) }) {
            Text(tr("Réactiver les alertes des discussions"))
        }
        if (state.pendingSocialRevocations.isNotEmpty()) Text(tr("Des accès attendent leur révocation. Une synchronisation réessaiera."))
        state.blockedSocialKeys.forEach { key ->
            Text(League.fingerprint(key))
            TextButton(onClick = { SocialPreferences.unblock(ctx, key) }) { Text(tr("Débloquer cette identité")) }
        }
        state.friends.forEach { friend ->
            SwitchRow(friend.shown, tr("Mettre en sourdine"), SocialPreferences.muted(friend.id)) { on ->
                SocialPreferences.mute(ctx, friend.id, on)
            }
        }
    }
}
