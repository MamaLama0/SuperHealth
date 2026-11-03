package app.superhealth.ui.league

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.league.SocialInbox
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationBell(nav: NavHostController) {
    val ctx = LocalContext.current
    val notifications by SocialInbox.items.collectAsState()
    val unread = notifications.count { !it.read }
    var open by remember { mutableStateOf(false) }
    IconButton({ open = true }) {
        BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else "$unread") } }) {
            Icon(Icons.Rounded.NotificationsNone, tr("Notifications"))
        }
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }, containerColor = Mf.colors.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Notifications"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton({ open = false }) { Icon(Icons.Rounded.Close, tr("Fermer")) }
        }
        if (notifications.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            TextButton(onClick = { SocialInbox.read(ctx) }, enabled = unread > 0) { Text(tr("Tout marquer comme lu")) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { SocialInbox.clear(ctx) }) { Text(tr("Effacer")) }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 560.dp).navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp)) {
            if (notifications.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.NotificationsNone, null, Modifier.size(40.dp), tint = Mf.colors.textTertiary)
                    Text(tr("Vous êtes à jour"), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                    Text(tr("Les nouvelles activités de vos amis apparaîtront ici."), color = Mf.colors.textSecondary)
                }
            }
            items(notifications, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().background(if (item.read) Mf.colors.background else Mf.colors.accent.copy(alpha = .08f))
                    .clickable {
                        SocialInbox.read(ctx, item.id); open = false
                        if (item.route != "feed") nav.go(item.route)
                    }.padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (item.read) Icons.Rounded.NotificationsNone else Icons.Rounded.NotificationsActive, null,
                        tint = if (item.read) Mf.colors.textTertiary else Mf.colors.accent)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (item.read) FontWeight.Normal else FontWeight.Bold)
                        Text(item.text, style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
                        Text(android.text.format.DateUtils.getRelativeTimeSpanString(item.time, System.currentTimeMillis(),
                            android.text.format.DateUtils.MINUTE_IN_MILLIS).toString(), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary)
                    }
                }
            }
        }
    }
}
