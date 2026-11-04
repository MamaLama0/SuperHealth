package app.superhealth.ui.league

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.superhealth.nutrition.ui.components.*
import app.superhealth.ui.theme.Mf
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import app.superhealth.league.League
import app.superhealth.league.SocialPreferences
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.tr
import app.superhealth.ui.trf

/** Lecture sur ce téléphone : publications masquées, discussions silencieuses, identités bloquées et amis en sourdine. */
@Composable
fun SocialPreferencesScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val state by League.state.collectAsState()
    DetailScaffold(tr("Contenus masqués et sourdines"), { nav.popBackStack() }, contentSpacing = 12.dp) {
        Text(tr("Ces choix concernent la lecture et les alertes sur ce téléphone."), style = MaterialTheme.typography.bodySmall,
            color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
        GroupCard {
            SettingRow(tr("Publications masquées"), state.hiddenSocialPosts.size.toString(), icon = Icons.Rounded.VisibilityOff)
            if (state.hiddenSocialPosts.isNotEmpty()) TextButton({ SocialPreferences.restoreHidden(ctx) }, Modifier.padding(start = 48.dp)) { Text(tr("Réafficher les publications")) }
            RowDivider(56.dp)
            SettingRow(tr("Discussions silencieuses"), state.silentSocialThreads.size.toString(), icon = Icons.Rounded.NotificationsOff)
            if (state.silentSocialThreads.isNotEmpty()) TextButton({ SocialPreferences.restoreThreads(ctx) }, Modifier.padding(start = 48.dp)) { Text(tr("Réactiver les alertes des discussions")) }
        }
        if (state.pendingSocialRevocations.isNotEmpty()) Text(tr("Des accès attendent leur révocation. Une synchronisation réessaiera."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
        if (state.blockedSocialKeys.isNotEmpty()) {
            GroupHeader(tr("Identités bloquées"), Modifier.padding(start = 0.dp))
            GroupCard {
                state.blockedSocialKeys.forEachIndexed { i, key ->
                    if (i > 0) RowDivider(56.dp)
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Block, null, Modifier.size(21.dp), tint = Mf.colors.textSecondary)
                        Text(League.fingerprint(key), Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.bodyMedium)
                        TextButton({ SocialPreferences.unblock(ctx, key) }) { Text(tr("Débloquer")) }
                    }
                }
            }
        }
        if (state.friends.isNotEmpty()) {
            GroupHeader(tr("Amis en sourdine"), Modifier.padding(start = 0.dp))
            GroupCard {
                state.friends.sortedBy { it.shown.lowercase() }.forEachIndexed { i, friend ->
                    if (i > 0) RowDivider()
                    SwitchRow(friend.shown, tr("Masquer ses publications et alertes"), SocialPreferences.muted(friend.id)) { on ->
                        SocialPreferences.mute(ctx, friend.id, on)
                    }
                }
            }
        }
    }
}
