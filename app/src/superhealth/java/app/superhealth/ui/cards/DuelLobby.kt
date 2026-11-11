package app.superhealth.ui.cards

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.superhealth.SuperPrefs
import app.superhealth.cards.*
import app.superhealth.nutrition.ui.components.NavBar
import app.superhealth.ui.tr
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private val lobbyGold = Color(0xFFE8C584)
private val lobbyInk = Color(0xFF13232C)

@Composable
internal fun DuelLobby(mine: AthleteStats?, character: CharacterStats, opponents: List<Opponent>, ready: Boolean,
    initial: String?,
    onBack: () -> Unit, invite: () -> Unit, inbox: () -> Unit, pending: Int, notice: String?, compare: (Opponent) -> Unit, challenge: (Opponent) -> Unit) {
    var choice by rememberSaveable { mutableStateOf<String?>(initial) }
    val selected = opponents.firstOrNull { it.id == choice } ?: opponents.firstOrNull()
    val settings by SuperPrefs.settings.collectAsState()
    val mineFace = myFace(mine)
    val ownAvatar = DungeonCreature.adventurers.firstOrNull { it.name == settings.arsenalAvatar }
    val myPhoto = mineFace.photo.takeIf { ownAvatar == null }
    val selectedFace = selected?.friend?.let { friendFace(it) }
        ?: mineFace.takeIf { selected?.id == "past" }
    val comparedMine = if (selected?.stats?.character == null) mine?.let(CharacterStats::legacy) ?: character else character
    Column(Modifier.fillMaxSize().background(lobbyInk).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
        NavBar("L’arène", onBack) {
            TextButton(inbox) { Text(tr("Mes défis") + if (pending > 0) " · $pending" else "", color = lobbyGold) }
            IconButton(invite) { Icon(Icons.Rounded.PersonAdd, tr("Inviter un ami"), tint = lobbyGold) }
        }
        Text(tr("Avec qui tu te mesures ?"), Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        LazyRow(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(opponents, key = { it.id }) { o ->
                val face = o.friend?.let { friendFace(it) } ?: mineFace.takeIf { o.id == "past" }
                val picked = o.id == selected?.id
                Column(Modifier.width(82.dp).clickable { choice = o.id }, horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(68.dp).clip(CircleShape).background(if (picked) Color(0xFF3C4D4C) else Color(0xFF263B44))
                        .border(if (picked) 2.dp else 1.dp, if (picked) lobbyGold else Color(0xFF53676B), CircleShape),
                        contentAlignment = Alignment.Center) {
                        if (face?.photo != null) Image(face.photo, null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                        else CreatureThumbnail(combatAvatar(o.stats), Modifier.size(62.dp))
                        if (picked) Icon(Icons.Rounded.CheckCircle, null, Modifier.align(Alignment.BottomEnd).padding(5.dp).size(19.dp), tint = lobbyGold)
                    }
                    Text(if (o.id == "past") tr("Toi avant") else o.name, color = if (picked) lobbyGold else Color.White,
                        style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                Column(Modifier.width(74.dp).clickable(onClick = invite), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(68.dp).border(1.dp, Color(0xFF53676B), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, null, tint = lobbyGold)
                    }
                    Text(tr("Inviter"), color = lobbyGold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (selected != null) {
            Box(Modifier.height(300.dp).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF34565C))) {
                DuelPreview(ownAvatar ?: mine?.let(::combatAvatar) ?: DungeonCreature.KNIGHT, combatAvatar(selected.stats),
                    myPhoto, selectedFace?.photo, mineFace.photoMode, selectedFace?.photoMode ?: PhotoMode.DETOUREE,
                    selected.id, Modifier.fillMaxSize())
                Row(Modifier.fillMaxWidth().background(Color(0xB313232C)).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Toi"), color = Color(0xFFAED4EF), style = MaterialTheme.typography.labelLarge)
                        Text("${comparedMine.strength} · ${comparedMine.dexterity} · ${comparedMine.constitution}", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    }
                    Text("VS", color = lobbyGold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(if (selected.id == "past") tr("Toi avant") else selected.name, color = Color(0xFFFFC0A5),
                            style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tr(if (selected.friend?.cardTrade != null) "Comparaison sportive" else "Entraînement"), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    }
                }
                Text(selected.note, Modifier.align(Alignment.BottomCenter).padding(12.dp).clip(CircleShape)
                    .background(Color(0xDA13232C)).padding(horizontal = 14.dp, vertical = 6.dp),
                    color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                notice?.let { Text(it, color = lobbyGold, style = MaterialTheme.typography.bodySmall) }
                val rival = selected.stats.character ?: CharacterStats.legacy(selected.stats)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CharacterAttribute.entries.forEach { stat ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(tr(stat.label), color = lobbyGold, style = MaterialTheme.typography.labelSmall)
                            Text("${comparedMine[stat]} / ${rival[stat]}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Text(tr("Trois caractéristiques, trois échanges. Aucun hasard ni bonus de vitesse."),
                    color = Color(0xFFB9CDD0), style = MaterialTheme.typography.bodySmall)
                Button({ compare(selected) }, enabled = ready && (selected.stats.values.isNotEmpty() || selected.stats.character != null),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = lobbyGold, contentColor = lobbyInk)) {
                    Icon(Icons.Rounded.Bolt, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Comparer nos stats"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (selected.friend?.cardTrade != null) TextButton(onClick = { challenge(selected) }, enabled = ready,
                    modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(tr("Envoyer un défi"), color = lobbyGold)
                }
                Text(tr("Comparaison avec les dernières stats partagées."), color = Color(0xFFB9CDD0), style = MaterialTheme.typography.labelSmall)

            }
        } else {
            Column(Modifier.heightIn(min = 300.dp).fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
                Icon(Icons.Rounded.Groups, null, Modifier.size(60.dp), tint = lobbyGold)
                Text(tr("Invite un ami pour entrer dans l’arène."), color = Color.White,
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
                Button(invite) { Text(tr("Inviter un ami")) }
            }
        }
    }
}

@Composable
private fun DuelPreview(me: DungeonCreature, them: DungeonCreature, myPhoto: androidx.compose.ui.graphics.ImageBitmap?,
    theirPhoto: androidx.compose.ui.graphics.ImageBitmap?, myMode: PhotoMode, theirMode: PhotoMode, id: String, modifier: Modifier) {
    val mine = duelPortrait(myPhoto)
    val theirs = duelPortrait(theirPhoto)
    val ready = (myPhoto == null || mine != null) && (theirPhoto == null || theirs != null)
    val data = remember(me, them, id) { JSONObject().put("key", "preview|$id|$me|$them")
        .put("me", me.name.lowercase(Locale.ROOT)).put("them", them.name.lowercase(Locale.ROOT)).put("preview", true)
        .put("rounds", JSONArray().put(JSONObject().put("stat", "PEC").put("mine", 0).put("theirs", 0).put("myHealth", 10000).put("theirHealth", 10000))) }
    val photos = JSONObject().put("me", mine ?: JSONObject.NULL).put("them", theirs ?: JSONObject.NULL)
        .put("meCutout", myMode == PhotoMode.DETOUREE).put("themCutout", theirMode == PhotoMode.DETOUREE)
    EquipmentWeb("duel.html", "duel.pause(true);" + if (ready) "duel.photos($photos);duel.start($data);" else "",
        modifier, action = { _, _ -> })
}
