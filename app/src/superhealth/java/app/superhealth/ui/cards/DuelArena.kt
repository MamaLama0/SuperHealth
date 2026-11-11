package app.superhealth.ui.cards

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.superhealth.SuperPrefs
import app.superhealth.cards.*
import app.superhealth.nutrition.ui.components.NavBar
import app.superhealth.ui.rememberHaptics
import app.superhealth.ui.tr
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay

private val arenaBlue = Color(0xFF9FCBFF)
private val arenaCoral = Color(0xFFFFB69E)
private val arenaInk = Color(0xFF101923)
private val arenaMuted = Color(0xFFB6C5D4)

internal fun combatAvatar(stats: AthleteStats): DungeonCreature = when (Stat.entries.maxByOrNull { stats[it] ?: 0 }) {
    Stat.PEC -> DungeonCreature.KNIGHT
    Stat.DOS, Stat.CAR -> DungeonCreature.RANGER
    Stat.BRA -> DungeonCreature.BARBARIAN
    Stat.JAM -> DungeonCreature.ROGUE
    else -> DungeonCreature.MAGE
}

@Composable
internal fun DuelArena(mine: AthleteStats, opponent: Opponent, battle: CharacterCombat.Battle, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val settings by SuperPrefs.settings.collectAsState()
    val me = DungeonCreature.adventurers.firstOrNull { it.name == settings.arsenalAvatar } ?: combatAvatar(mine)
    val them = combatAvatar(opponent.stats)
    val myFace = myFace(mine)
    val theirFace = opponent.friend?.let { friendFace(it) }
    val myPhoto = myFace.photo.takeIf { settings.arsenalAvatar == null }
    val theirPhoto = theirFace?.photo ?: myPhoto.takeIf { opponent.id == "past" }
    val mySprite = duelPortrait(myPhoto)
    val theirSprite = duelPortrait(theirPhoto)
    val portraitsReady = (myPhoto == null || mySprite != null) && (theirPhoto == null || theirSprite != null)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val haptics = rememberHaptics()
    var replay by remember { mutableIntStateOf(0) }
    var round by remember { mutableIntStateOf(0) }
    var myHealth by remember { mutableIntStateOf(CharacterCombat.HEALTH) }
    var theirHealth by remember { mutableIntStateOf(CharacterCombat.HEALTH) }
    var finished by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var speed by remember { mutableIntStateOf(1) }
    var skip by remember { mutableStateOf(false) }
    var lastTarget by remember { mutableStateOf<String?>(null) }
    var lastDamage by remember { mutableIntStateOf(0) }
    LaunchedEffect(lastTarget, lastDamage, round) {
        if (lastTarget != null) { delay(350); lastTarget = null }
    }
    val number = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 } }
    val receipt = remember(battle, me, them, replay) {
        JSONObject().put("key", "$replay|${opponent.id}|${me.name}|${them.name}")
            .put("me", me.name.lowercase(Locale.ROOT)).put("them", them.name.lowercase(Locale.ROOT))
            .put("rounds", JSONArray().apply {
                battle.exchanges.forEach { exchange -> put(JSONObject().put("stat", exchange.stat.name)
                    .put("mine", exchange.myDamage).put("theirs", exchange.theirDamage)
                    .put("myHealth", exchange.myHealth).put("theirHealth", exchange.theirHealth)) }
            })
    }
    val portraits = JSONObject().put("me", mySprite ?: JSONObject.NULL).put("them", theirSprite ?: JSONObject.NULL)
        .put("meCutout", myFace.photoMode == PhotoMode.DETOUREE)
        .put("themCutout", (theirFace?.photoMode ?: myFace.photoMode) == PhotoMode.DETOUREE)
    val script = "duel.pause(${!active || paused});duel.speed($speed);" +
        (if (portraitsReady) "duel.photos($portraits);duel.start($receipt);" else "") +
        (if (skip) "duel.skip();" else "")
    val exchange = battle.exchanges.getOrNull(round)
    fun restart() {
        round = 0; myHealth = CharacterCombat.HEALTH; theirHealth = CharacterCombat.HEALTH
        finished = false; loaded = false; paused = false; skip = false; lastTarget = null; replay++
    }
    if (finished) {
        DuelResult(battle, if (opponent.id == "past") tr("Toi avant") else opponent.name, onBack, ::restart)
        return
    }
    Column(Modifier.fillMaxSize().background(arenaInk).statusBarsPadding().navigationBarsPadding()) {
        NavBar("Duel", onBack) {
            IconButton({ paused = !paused }) {
                    Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        tr(if (paused) "Reprendre" else "Pause"), tint = arenaBlue)
                }
            TextButton({ speed = if (speed == 1) 2 else 1 }) { Text("×$speed", color = arenaBlue) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FighterHealth(tr("Toi"), myHealth, arenaBlue, Modifier.weight(1f))
            FighterHealth(opponent.name, theirHealth, arenaCoral, Modifier.weight(1f))
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(22.dp))) {

            EquipmentWeb("duel.html", script, Modifier.fillMaxSize(), enabled = active && !paused, action = { kind, value ->
                when (kind) {
                    "loaded" -> loaded = true
                    "round" -> { round = value.toIntOrNull()?.coerceIn(0, (battle.exchanges.size - 1).coerceAtLeast(0)) ?: 0; lastTarget = null }
                    "hit" -> runCatching { JSONObject(value) }.getOrNull()?.let { hit ->
                        lastTarget = hit.optString("target")
                        lastDamage = hit.optInt("damage")
                        if (lastTarget == "me") myHealth = hit.optInt("health").coerceIn(0, CharacterCombat.HEALTH)
                        else theirHealth = hit.optInt("health").coerceIn(0, CharacterCombat.HEALTH)
                        if (active) haptics.tick()
                    }
                    "finish" -> {
                        myHealth = battle.myHealth; theirHealth = battle.theirHealth; finished = true; lastTarget = null
                        if (active) haptics.confirm()
                    }
                }
            })
            val status = when {
                !loaded -> tr("Entrée dans l’arène…")
                paused -> tr("Combat en pause")
                else -> tr("Échange") + " ${round + 1}/${battle.exchanges.size}"
            }
            Text(status, Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
                .clip(CircleShape).background(Color(0xDA182535)).padding(horizontal = 16.dp, vertical = 7.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (lastTarget != null) Text("−" + number.format(lastDamage / 100.0),
                Modifier.align(if (lastTarget == "me") Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 42.dp),
                color = if (lastTarget == "me") arenaCoral else arenaBlue,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            battle.exchanges.forEachIndexed { i, e ->
                val done = i < round
                val tint = if (done) { if (e.myDamage >= e.theirDamage) arenaBlue else arenaCoral } else if (i == round) Color.White else Color(0xFF354353)
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(tint))
            }
        }
        Surface(color = Color(0xFF1B2A38), shape = RoundedCornerShape(18.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Bolt, null, Modifier.size(21.dp), tint = arenaBlue)
                    Text(exchange?.let { tr(it.stat.attack) } ?: tr("Préparation…"), Modifier.weight(1f),
                        color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(exchange?.let { number.format(it.myDamage / 100.0) } ?: "—", color = arenaBlue,
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text(exchange?.let { tr(it.stat.label) } ?: "", Modifier.weight(1f), textAlign = TextAlign.Center,
                        color = arenaMuted, style = MaterialTheme.typography.labelLarge)
                    Text(exchange?.let { number.format(it.theirDamage / 100.0) } ?: "—", color = arenaCoral,
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
                Text(tr("Trois caractéristiques, trois échanges. Aucun hasard ni bonus de vitesse."),
                    color = arenaMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
        TextButton({ skip = true }, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp)) {
            Text(tr("Voir le bilan"), color = arenaMuted)
        }
    }
}

@Composable
private fun FighterHealth(name: String, health: Int, tint: Color, modifier: Modifier) {
    val fraction by animateFloatAsState(health.toFloat() / CharacterCombat.HEALTH, tween(260), label = "duel-health")
    val number = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(name, color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Rounded.Favorite, tr("Points de vie"), Modifier.size(16.dp), tint = tint)
            Text(number.format(health / 100.0), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("/100", color = arenaMuted, style = MaterialTheme.typography.labelSmall)
        }
        LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth().height(7.dp), color = tint, trackColor = Color(0xFF344150))
    }
}
