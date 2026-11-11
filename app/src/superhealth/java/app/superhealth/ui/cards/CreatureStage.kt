package app.superhealth.ui.cards

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import app.superhealth.cards.EnemyAffinity
import app.superhealth.cards.EquipmentSchool
import app.superhealth.cards.DamageType
import app.superhealth.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val portraits = ConcurrentHashMap<DungeonCreature, ImageBitmap>()
private val DungeonCreature.asset get() = "creature-" + (modelId ?: name.lowercase(Locale.ROOT))

@Composable
internal fun CreatureThumbnail(creature: DungeonCreature, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState(portraits[creature], creature) {
        value = withContext(Dispatchers.IO) {
            portraits[creature] ?: runCatching {
                context.assets.open("arsenal/" + creature.asset + ".webp").use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }?.also { portraits[creature] = it }
            }.getOrNull()
        }
    }
    val image = bitmap
    if (image != null) Image(image, tr(creature.nameFr), modifier)
    else Icon(Icons.Rounded.Person, tr(creature.nameFr), modifier, tint = Color(creature.light))
}

@Composable
internal fun CreatureStage(creature: DungeonCreature, hit: Long?, modifier: Modifier = Modifier, inspect: Boolean = false, dungeon: Boolean = false, playing: Boolean = true) {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = playing && state.isAtLeast(Lifecycle.State.RESUMED)
    val id = creature.modelId ?: creature.name.lowercase(Locale.ROOT)
    val script = "beast.pause(" + !active + ");beast.inspect($inspect);beast.setDungeon($dungeon);beast.select(" + JSONObject.quote(id) + "," + (hit?.toString() ?: "null") + ");"
    EquipmentWeb("creature.html?model=$id&ground=$dungeon", script, modifier, enabled = active, ownsGestures = inspect, action = { _, _ -> })
}

@Composable
internal fun Matchup(rule: EnemyAffinity, mix: Map<DamageType, Double>, dark: Boolean = false) {
    val color = if (dark) Color(0xFFDDD0EF) else MaterialTheme.colorScheme.onSurfaceVariant
    val helpful = rule.weak in mix
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Faiblesse"), color = color, style = MaterialTheme.typography.labelMedium)
            DamageIcon(rule.weak, Modifier.size(22.dp))
            Text(tr("Résistance"), color = color, style = MaterialTheme.typography.labelMedium)
            DamageIcon(rule.resist, Modifier.size(22.dp))
        }
        if (rule.ward) Text(
            if (helpful) tr("Tu as une affinité qui perce sa garde.") else tr("Sa garde réduit les dégâts sans cette affinité."),
            color = color, style = MaterialTheme.typography.bodySmall,
        )
    }
}
