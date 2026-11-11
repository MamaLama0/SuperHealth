package app.superhealth.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Castle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.cards.*
import app.superhealth.ui.go
import app.superhealth.ui.tr
import kotlinx.coroutines.CancellationException

/** Two quiet shortcuts; the feed remains the main content. */
@Composable
internal fun ArenaTeaser(nav: NavHostController, active: Boolean) {
    val ctx = LocalContext.current
    val remote by ArsenalGame.remote.collectAsState()
    val inventory by EquipmentStore.state.collectAsState()
    LaunchedEffect(active) {
        if (active) try {
            EquipmentStore.load(ctx)
            ArsenalGame.advance(ctx, ArsenalGame.quests(ctx))
        } catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }
    val pending = remote?.duels.orEmpty().count { it.incoming && it.status == "pending" }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple("L’arène", if (pending > 0) "$pending " + tr("défis reçus") else tr("Comparer nos stats"), "cards/duel"),
            Triple("Donjon", tr("Étage") + " ${inventory.dungeon.floor}", "cards/dungeon")).forEachIndexed { index, (title, detail, route) ->
            val color = if (index == 0) Color(0xFFE6C38E) else Color(0xFF9ACABF)
            Surface(onClick = { nav.go(route) }, shape = RoundedCornerShape(18.dp), color = color.copy(alpha = .10f), modifier = Modifier.weight(1f)) {
                Row(Modifier.heightIn(min = 64.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (index == 0) Icons.Rounded.LocalFireDepartment else Icons.Rounded.Castle, null, tint = color, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(tr(title), style = MaterialTheme.typography.titleSmall)
                        Text(detail, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
