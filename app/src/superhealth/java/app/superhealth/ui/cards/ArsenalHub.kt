package app.superhealth.ui.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.cards.*
import app.superhealth.league.League
import app.superhealth.league.ProgressRepository
import app.superhealth.nutrition.ui.components.NavBar
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.edge
import app.superhealth.ui.theme.tabBackground
import app.superhealth.ui.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dungeonViolet = Color(0xFFC0A4FF)
private val mintedGold = Color(0xFFF4CE77)

@Composable
internal fun GoldBadge(value: Int, modifier: Modifier = Modifier) {
    Row(modifier.clip(CircleShape).background(mintedGold.copy(alpha = .15f)).padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Toll, null, Modifier.size(15.dp), tint = mintedGold)
        Text("$value " + tr("or"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Mf.colors.text)
    }
}

@Composable
internal fun DungeonTeaser(nav: NavHostController, active: Boolean) {
    val ctx = LocalContext.current
    val inventory by EquipmentStore.state.collectAsState()
    val remote by ArsenalGame.remote.collectAsState()
    val result by ProgressRepository.current.collectAsState()
    val selected = ProgressRepository.selectedTracks(SuperPrefs.settings.collectAsState().value)
    val quests = result?.let { ArsenalGame.quests(it, selected) }
    LaunchedEffect(active) {
        if (active) try {
            val progress = ArsenalGame.quests(ctx)
            ArsenalGame.advance(ctx, progress)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {}
    }
    val dungeon = inventory.dungeon
    val raidWeek = ArsenalGame.raidWeek(LocalDate.now(ZoneOffset.UTC).toEpochDay())
    val raid = remote?.raid?.takeIf { it.week == raidWeek }
    val status = when {
        remote?.raidInvites.orEmpty().isNotEmpty() -> tr("Invitation au boss")
        dungeon.health == 0 -> tr("Récupération")
        quests?.canAttack == true && dungeon.lastAttack != quests?.today -> tr("Attaque prête")
        else -> tr(DungeonCreature.floor(dungeon.floor).nameFr)
    }
    val room = DungeonRoom.floor(dungeon.floor)
    val shape = RoundedCornerShape(22.dp)
    Surface(onClick = { nav.go("cards/dungeon") }, shape = shape, color = Color(0xFF211B34),
        modifier = Modifier.fillMaxWidth().border(1.dp, room.light.copy(alpha = .30f), shape)) {
        Row(Modifier.dungeonMasonry(room).heightIn(min = 148.dp)
            .padding(end = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CreatureStage(DungeonCreature.floor(dungeon.floor), null, Modifier.width(112.dp).height(148.dp), dungeon = true, playing = active)
            Column(Modifier.weight(1f).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tr("DONJON · ÉTAGE") + " ${dungeon.floor}", style = MaterialTheme.typography.labelSmall, color = room.light)
                Text(tr(room.label), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text(status, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE4DDD1))
                Text(when {
                    raid?.won == true -> tr("Boss vaincu")
                    else -> tr("Donjon solo · Boss entre amis")
                },
                    style = MaterialTheme.typography.labelSmall, color = Color(0xFFCEC4DF))
            }
            Icon(Icons.Rounded.Castle, tr("Entrer dans le donjon"), tint = room.light, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DungeonRoute(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by EquipmentStore.state.collectAsState()
    val remote by ArsenalGame.remote.collectAsState()
    var quests by remember { mutableStateOf<ArsenalGame.Quests?>(null) }
    var busy by remember { mutableStateOf(false) }
    var combatResult by remember { mutableStateOf<ArsenalGame.Dungeon?>(null) }
    var issue by remember { mutableStateOf<String?>(null) }
    val league by League.state.collectAsState()
    val friends = league.friends.filter { it.cardTrade != null }
    var inviteFriends by rememberSaveable { mutableStateOf(false) }
    var giftDialog by rememberSaveable { mutableStateOf(false) }
    var giftTo by rememberSaveable { mutableStateOf<String?>(null) }
    var giftKind by rememberSaveable { mutableStateOf("GOLD") }
    var giftId by rememberSaveable { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var notice by remember { mutableStateOf<String?>(null) }
    fun task(action: suspend () -> Unit) {
        if (busy) return
        busy = true; issue = null; notice = null
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { issue = e.message ?: tr("Action indisponible") }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) {
        task {
            val progress = ArsenalGame.quests(ctx)
            quests = progress
            ArsenalGame.advance(ctx, progress)
        }
    }
    val dungeon = saved.dungeon
    val raidWeek = ArsenalGame.raidWeek(LocalDate.now(ZoneOffset.UTC).toEpochDay())
    val raid = remote?.raid?.takeIf { it.week == raidWeek }
    val online = !EquipmentStore.unlimitedPacks && Economy.available() && (remote?.rules ?: 0) >= 3
    val ice = if (online) remote?.ice ?: 0 else saved.ice
    val packs = saved.freePacks + (if (online) remote?.packCredits.orEmpty().size else 0)
    val today = quests?.today ?: java.time.LocalDate.now().toEpochDay()
    val frozen = dungeon.frozenDay == today
    val cards by EquipmentStore.cards.collectAsState()
    val mix = remember(saved.equipped, saved.owned, cards) { EquipmentStore.damageMix(saved, cards) }
    ArsenalPage("Donjon", nav, remote?.gold ?: saved.gold) {
        FilledTonalButton({ nav.go("cards/equipment?edit=true") }, Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Shield, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("Changer mon équipement"))
        }
        val room = DungeonRoom.floor(dungeon.floor)
        val creature = DungeonCreature.floor(dungeon.floor)
        val iceDrop = ArsenalGame.iceDrop(dungeon.floor)
        val canAttack = !busy && quests?.canAttack == true && (dungeon.lastAttack ?: Long.MIN_VALUE) < today
        val shape = RoundedCornerShape(26.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(Color(0xFF161D22))
            .border(1.dp, room.light.copy(alpha = .35f), shape)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(tr(room.label), color = room.light, style = MaterialTheme.typography.labelLarge)
                    Text(tr(creature.nameFr), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("ÉTAGE"), color = room.light, style = MaterialTheme.typography.labelSmall)
                    Text(dungeon.floor.toString().padStart(2, '0'), color = Color.White,
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                }
            }
            Box(Modifier.fillMaxWidth().height(275.dp).dungeonMasonry(room, chamber = true)) {
                CreatureStage(creature, dungeon.lastAttack, Modifier.fillMaxSize(), dungeon = true)
                if (dungeon.elite) Text(tr("Élite"), Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .clip(CircleShape).background(Color(0xD9181B21)).padding(horizontal = 12.dp, vertical = 6.dp),
                    color = mintedGold, style = MaterialTheme.typography.labelLarge)
            }
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DungeonVital(tr("Monstre"), dungeon.hp, dungeon.maximum, Icons.Rounded.PestControl, room.light, Modifier.weight(1f))
                    DungeonVital(tr("Toi"), dungeon.health, 100, Icons.Rounded.Favorite, Color(0xFFED9B9D), Modifier.weight(1f))
                }
                DungeonAffinities(EnemyAffinity.floor(dungeon.floor), mix)
                val power = (EquipmentStore.bonuses(saved, cards).values.sum() / 100).coerceIn(0, 5)
                val character = quests?.character ?: CharacterStats()
                val attributePower = character.dungeonAttack
                val setDamage = if (EquipmentStore.completeSet(saved, cards, serverOnly = online) != null) 1 else 0
                val damage = EnemyAffinity.floor(dungeon.floor).hit(48 + attributePower + ((quests?.rank ?: 0) - 1).coerceAtLeast(0) * 9 + power, mix) + setDamage
                Text(tr("Dégâts prévus") + " · $damage  /  " + tr("Riposte") + " · " +
                    (if (damage >= dungeon.hp || frozen) 0 else ((if (dungeon.elite) 18 else 12) - character.dungeonDefense).coerceAtLeast(1)),
                    color = Color(0xFFCBD4DF), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { task {
                    val progress = ArsenalGame.quests(ctx)
                    quests = progress
                    ArsenalGame.advance(ctx, progress, attack = true)
                    combatResult = EquipmentStore.state.value.dungeon
                } }, enabled = canAttack, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(
                        containerColor = room.light, contentColor = Color(0xFF1C211F),
                        disabledContainerColor = Color(0xFF354044), disabledContentColor = Color(0xFFCBD2D0))) {
                    Icon(if (canAttack) Icons.Rounded.Bolt else Icons.Rounded.Schedule, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr(when {
                        busy -> "Préparation…"
                        dungeon.health == 0 -> "Reprendre des forces"
                        dungeon.lastAttack == today -> "Attaque effectuée aujourd’hui"
                        quests?.canAttack != true -> "Valide tes suivis pour attaquer"
                        else -> "Lancer l’attaque"
                    }), fontWeight = FontWeight.Bold)
                }
                issue?.let { Text(it, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Butin"), style = MaterialTheme.typography.labelMedium, color = Color(0xFFBFC8CA))
                    DungeonReward(Icons.Rounded.Toll, (if (dungeon.elite) 35 else 15).toString(), tr("or"))
                    if (iceDrop > 0) DungeonReward(Icons.Rounded.AcUnit, "+1", tr("Glaçon"))
                }
            }
        }
        notice?.let { Text(it, color = Mf.colors.accent) }
        Surface(color = Mf.colors.card, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                dungeon.log?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.TaskAlt, null, tint = Mf.colors.accent, modifier = Modifier.size(22.dp))
                    Text(tr("Un suivi validé = une attaque dans la journée."), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
                FilledTonalButton(onClick = { task { ArsenalGame.freeze(ctx, ArsenalGame.quests(ctx)) } },
                    enabled = !busy && !frozen && ice > 0 && dungeon.health > 0, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AcUnit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("$ice  ·  " + tr(if (frozen) "Monstre gelé aujourd’hui" else "Geler pour la journée"))
                }
                Text(tr("Le gel bloque la riposte du monstre."), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { nav.go("cards/packs") }, label = { Text("$packs " + tr("Paquets disponibles")) })
            if (online) TextButton({ giftDialog = true }) { Text(tr("Offrir")) }
        }
        run {
            val week = raidWeek
            val boss = DungeonCreature.weekly(week)
            val bossShape = RoundedCornerShape(20.dp)
            Row(Modifier.fillMaxWidth().clip(bossShape)
                .background(Brush.horizontalGradient(listOf(Color(0xFF30263E), Color(0xFF191D31))))
                .border(1.dp, Color(boss.light).copy(alpha = .30f), bossShape).padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CreatureStage(boss, raid?.hp?.toLong(), Modifier.width(156.dp).height(167.dp), dungeon = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Boss de la semaine"), style = MaterialTheme.typography.labelSmall, color = Color(boss.light))
                    Text(tr(boss.nameFr), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Matchup(EnemyAffinity.weekly(week), mix)
            if (raid == null) {
                Text(tr("Monte une équipe de 2 à 8. Le boss gagne des points de vie pour chaque membre."))
                if (!Economy.available()) Text(tr("Configure le relais du Groupe pour créer ou rejoindre une équipe."), color = Mf.colors.textSecondary)
                else {
                    FilledTonalButton({ task { ArsenalGame.act(ctx, "raid-create") } }, enabled = !busy) { Text(tr("Créer une équipe")) }
                    remote?.raidInvites.orEmpty().forEach { invitation ->
                        val name = friends.firstOrNull { it.cardTrade == invitation.from }?.shown ?: tr("Un ami")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Invitation au boss") + " · " + name, Modifier.weight(1f))
                            TextButton({ task { ArsenalGame.act(ctx, "v3-raid-accept") { put("code", invitation.code) } } },
                                enabled = !busy) { Text(tr("Rejoindre")) }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Équipe") + " · ${raid.members}/8", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (raid.canInvite && raid.members < 8) TextButton({ inviteFriends = true }, enabled = !busy) {
                        Text(tr("Inviter des amis"))
                    }
                }
                ArsenalMeter(tr("Boss"), raid.hp, raid.max, dungeonViolet)
                ArsenalMeter(tr("Membres ayant réussi le défi"), raid.qualifiers, raid.required, mintedGold)
                Text(tr("Frappe après une journée de suivis complète. Pour vaincre le boss, ramène ses PV à zéro et aide la majorité de l’équipe à valider trois journées cette semaine."),
                    color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                Text(tr("Tes journées complètes") + " · ${raid.mine.coerceAtMost(3)}/3")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Butin par membre"), style = MaterialTheme.typography.labelLarge)
                    GoldBadge(raid.goldReward)
                    DungeonReward(Icons.Rounded.AcUnit, "+${raid.iceReward}", tr("Glaçons"))
                }
                FilledTonalButton({ task {
                    val progress = ArsenalGame.quests(ctx)
                    quests = progress
                    ArsenalGame.hit(ctx, progress)
                } }, enabled = !busy && !raid.won && !raid.hit && raid.hp > 0 && raid.members >= 2 && quests?.fullToday == true) {
                    Text(tr(when {
                        raid.won -> "Boss vaincu"
                        raid.hp == 0 -> "Défi de groupe en cours"
                        raid.hit -> "Attaque effectuée aujourd’hui"
                        quests?.fullToday != true -> "Termine tes suivis pour frapper"
                        else -> "Frapper le boss"
                    }))
                }
                TextButton({ task {
                    val progress = ArsenalGame.quests(ctx)
                    quests = progress
                    ArsenalGame.sync(ctx, progress)
                } }, enabled = !busy) { Text(tr("Actualiser l’équipe")) }
            }
        }
        issue?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (inviteFriends && raid != null) ModalBottomSheet(onDismissRequest = { inviteFriends = false }) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(20.dp)) {
            item { Text(tr("Inviter des amis"), style = MaterialTheme.typography.titleLarge) }
            if (friends.isEmpty()) item { Text(tr("Ajoute un ami qui a synchronisé son Arsenal.")) }
            items(friends, key = { it.id }) { friend ->
                val sent = friend.cardTrade in raid.invited
                ListItem(headlineContent = { Text(friend.shown) }, trailingContent = {
                    TextButton({ task { ArsenalGame.act(ctx, "v3-raid-invite") { put("to", friend.cardTrade!!) } } },
                        enabled = !busy && !sent) { Text(tr(if (sent) "Invitation envoyée" else "Inviter")) }
                })
            }
            issue?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        }
    }
    if (giftDialog) ModalBottomSheet(onDismissRequest = { if (!busy) giftDialog = false }) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(20.dp)) {
            item {
                Text(tr("Offrir une récompense"), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    for ((key, label) in listOf("GOLD" to "25 or", "PACK" to "Paquet", "ICE" to "Glaçon")) {
                        FilterChip(giftKind == key, {
                            giftKind = key
                            giftId = java.util.UUID.randomUUID().toString()
                        }, { Text(tr(label)) }, enabled = !busy)
                    }
                }
            }
            if (friends.isEmpty()) item { Text(tr("Ajoute un ami qui a synchronisé son Arsenal.")) }
            items(friends, key = { it.id }) { friend ->
                FilterChip(giftTo == friend.cardTrade, {
                    giftTo = friend.cardTrade
                    giftId = java.util.UUID.randomUUID().toString()
                }, { Text(friend.shown) }, enabled = !busy)
            }
            issue?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            item {
                val available = when (giftKind) {
                    "GOLD" -> (remote?.gold ?: 0) >= 25
                    "PACK" -> remote?.packCredits.orEmpty().isNotEmpty()
                    else -> (remote?.ice ?: 0) > 0
                }
                Button(onClick = {
                    val recipient = giftTo ?: return@Button
                    task {
                        ArsenalGame.act(ctx, "v3-gift") { put("request", giftId); put("to", recipient); put("kind", giftKind) }
                        giftId = java.util.UUID.randomUUID().toString()
                        giftTo = null
                        giftDialog = false
                        notice = tr("Cadeau envoyé")
                    }
                }, enabled = !busy && available && giftTo != null) { Text(tr("Offrir")) }
            }
        }
    }

    combatResult?.let { result ->
        AlertDialog(onDismissRequest = { combatResult = null },
            title = { Text(tr(if (result.defeatedFloor != null) "Monstre vaincu !" else "Le bilan du combat")) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Dégâts infligés") + " · ${result.dealt}", style = MaterialTheme.typography.headlineSmall)
                Text(tr("Riposte") + " · ${result.received}   /   " + tr("Vie restante") + " · ${result.health}")
                result.defeatedFloor?.let { floor ->
                    val iceDrop = ArsenalGame.iceDrop(floor)
                    Text("+${if (floor % 4 == 0) 35 else 15} " + tr("or") +
                        (if (iceDrop > 0) " · +1 " + tr("Glaçon") else ""))
                    Text(tr("Prochain étage") + " · ${result.floor}")
                }
            } }, confirmButton = { TextButton({ combatResult = null }) { Text(tr("Continuer")) } })
    }

}

@Composable
private fun DungeonVital(label: String, value: Int, total: Int, icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = tint)
            Text(label, Modifier.weight(1f), color = Color(0xFFBFC8CA), style = MaterialTheme.typography.labelMedium)
            Text("$value/$total", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(progress = { value.toFloat() / total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(6.dp),
            color = tint, trackColor = Color.White.copy(alpha = .12f))
    }
}

@Composable
private fun DungeonAffinities(rule: EnemyAffinity, mix: Map<DamageType, Double>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((type, weak) in listOf(rule.weak to true, rule.resist to false)) {
            val tint = Color(type.tint)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = .09f)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(if (weak) Icons.Rounded.GpsFixed else Icons.Rounded.Shield, null, Modifier.size(14.dp), tint = tint)
                    Text(tr(if (weak) "Faiblesse" else "Résistance"), color = Color(0xFFBFC8CA), style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    DamageIcon(type, Modifier.size(28.dp))
                }
                Text(tr(if (weak) "Dégâts augmentés" else "Dégâts réduits"), color = Color(0xFFBFC8CA), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (rule.ward) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val pierces = rule.weak in mix
        Icon(if (pierces) Icons.Rounded.Verified else Icons.Rounded.Lock, null, Modifier.size(18.dp), tint = mintedGold)
        Text(tr(if (pierces) "Ton équipement perce sa garde." else "Équipe sa faiblesse pour percer sa garde."),
            color = mintedGold, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DungeonReward(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, label, Modifier.size(19.dp), tint = mintedGold)
        Text(value, style = MaterialTheme.typography.labelLarge, color = mintedGold, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun ArsenalMeter(label: String, value: Int, total: Int, tint: Color, dark: Boolean = false) {
    val fraction by animateFloatAsState((value / total.coerceAtLeast(1).toFloat()).coerceIn(0f,1f), spring(), label = "arsenal-meter")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (dark) Color.White else Mf.colors.text)
            Text("$value/$total", style = MaterialTheme.typography.labelMedium, color = if (dark) Color.White else Mf.colors.text)
        }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = tint,
            trackColor = if (dark) Color.White.copy(alpha = .13f) else Mf.colors.edge)
    }
}

@Composable
internal fun ArsenalPage(title: String, nav: NavHostController, gold: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().tabBackground(Mf.colors.accent).statusBarsPadding()) {
        NavBar(title, { nav.popBackStack() }) { GoldBadge(gold, Modifier.padding(end = 8.dp)) }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 740.dp).fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
internal fun ArsenalQuestContent(quests: ArsenalGame.Quests?) {
    if (quests == null) {
        Text(tr("Lecture des suivis…"))
        return
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Quêtes du jour"), style = MaterialTheme.typography.titleMedium)
        Text("${quests.rank}/3", color = Mf.colors.accent, style = MaterialTheme.typography.labelLarge)
    }
    LinearProgressIndicator(progress = { quests.rank / 3f }, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
    val tiers = listOf(
        Triple("Premier sceau", "Valide la majorité de tes suivis.", 25),
        Triple("Grand sceau", "Valide tous les suivis visés et au moins deux domaines distincts.", 45),
        Triple("Sceau héroïque", "Valide tous les suivis visés et trois domaines, dont une activité.", 75),
    )
    tiers.forEachIndexed { i, (title, description, prize) ->
        val done = quests.rank > i
        Surface(color = Mf.colors.card, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (done) Icons.Rounded.CheckCircle else Icons.Rounded.Stars, null, tint = if (done) Mf.colors.accent else Mf.colors.textSecondary)
                Column(Modifier.weight(1f)) {
                    Text(tr(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(tr(description), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    GoldBadge(prize)

                }
            }
        }
    }
    Text(tr("Quêtes de la semaine"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 5.dp))
    ArsenalGame.weeklyGoals.forEach { threshold ->
        val count = quests.weekCount.coerceAtMost(threshold)
        ListItem(headlineContent = { Text(tr("Valider") + " $threshold " + tr("jours")) },
            supportingContent = { Text("$count / $threshold") },
            leadingContent = { Icon(if (count == threshold) Icons.Rounded.CheckCircle else Icons.Rounded.Stars, null) },
            trailingContent = { GoldBadge(ArsenalGame.WEEKLY_GOLD) },
            colors = ListItemDefaults.colors(containerColor = Mf.colors.card))
    }
    Text(tr("Les jours validés peuvent être espacés."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArsenalMarket(nav: NavHostController, startTab: Int = 0) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog by EquipmentStore.cards.collectAsState()
    val saved by EquipmentStore.state.collectAsState()
    val remote by ArsenalGame.remote.collectAsState()
    val league by League.state.collectAsState()
    val friends = league.friends.filter { it.cardTrade != null }
    var busy by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf<String?>(null) }
    var purchase by rememberSaveable { mutableStateOf<String?>(null) }
    var purchaseVisible by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("") }
    var mine by rememberSaveable { mutableStateOf(false) }
    val gold = remote?.gold ?: saved.gold
    val packPrice = remote?.packPrice ?: ArsenalGame.PACK_PRICE
    var composer by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var bidFor by remember { mutableStateOf<ArsenalGame.Listing?>(null) }
    var accept by remember { mutableStateOf<ArsenalGame.Listing?>(null) }
    var amount by rememberSaveable { mutableStateOf("") }
    fun task(action: suspend () -> Unit) {
        if (busy) return
        busy = true; issue = null
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { issue = e.message ?: tr("Action indisponible") }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) {
        EquipmentStore.load(ctx)
        if (Economy.available()) task { ArsenalGame.sync(ctx, ArsenalGame.quests(ctx)) }
    }
    Column(Modifier.fillMaxSize().tabBackground(Mf.colors.accent).statusBarsPadding()) {
        NavBar("Marché de l’Arsenal", { nav.popBackStack() }) { GoldBadge(gold, Modifier.padding(end = 8.dp)) }
        TabRow(selectedTabIndex = tab) {
            listOf("Boutique", "Échanges").forEachIndexed { i, label -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(tr(label)) }) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                if (tab == 0) FilledTonalButton({
                    if (purchase == null) purchase = java.util.UUID.randomUUID().toString()
                    purchaseVisible = true
                }, Modifier.fillMaxWidth(), enabled = !busy && (!Economy.available() || remote != null)) {
                    Icon(Icons.Rounded.Style, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Acheter un paquet"), Modifier.weight(1f))
                    GoldBadge(packPrice)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ composer = true }, enabled = remote != null && !busy) { Text(tr(if (tab == 0) "Mettre en vente" else "Proposer à un ami")) }
                    IconButton({ task { ArsenalGame.sync(ctx, ArsenalGame.quests(ctx)) } }, enabled = !busy && Economy.available()) {
                        Icon(Icons.Rounded.Refresh, tr("Actualiser"))
                    }
                }
                if (!Economy.available()) Text(tr("Configure le relais du Groupe pour rejoindre le marché."), color = Mf.colors.textSecondary)
                issue?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (remote?.listings?.isNotEmpty() == true) {
                    OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth(), label = { Text(tr("Chercher une carte")) }, singleLine = true)
                    FilterChip(mine, { mine = !mine }, label = { Text(tr(if (tab == 0) "Mes ventes et enchères" else "Mes propositions")) })
                }
            }
            val rows = remote?.listings.orEmpty().filter { (it.recipient == null) == (tab == 0) }
                .filter { !mine || it.mine || it.leading }
                .filter { filter.isBlank() || (catalog[it.piece.id]?.title ?: it.piece.id).contains(filter.trim(), ignoreCase = true) }
                .sortedWith(compareByDescending<ArsenalGame.Listing> { it.incoming }.thenBy { it.end })
            if (rows.isEmpty() && remote != null) item { Text(tr(if (tab == 0) "Aucune vente trouvée." else "Aucune proposition en cours.")) }
            items(rows, key = { it.key }) { listing ->
                val card = catalog[listing.piece.id]
                val tier = card?.let { EquipmentRarity.entries[it.tier] }
                Surface(shape = RoundedCornerShape(16.dp), color = Mf.colors.card, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(Color(tier?.tint ?: 0xFFBDB8CC), CircleShape))
                            Text(card?.title ?: listing.piece.id, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            if (listing.wanted == null) GoldBadge(maxOf(listing.price, listing.bid))
                        }
                        Text(listOfNotNull(tier?.title?.let(::tr), card?.let { it.type }, listing.piece.finish.title.takeIf { listing.piece.finish != EquipmentFinish.NORMAL }?.let(::tr),
                            when {
                                listing.incoming -> friends.firstOrNull { it.cardTrade == listing.seller }?.shown ?: tr("Proposition reçue")
                                listing.mine -> tr("Ta proposition")
                                listing.leading -> tr("En tête")
                                listing.fixed -> tr("Prix fixe")
                                listing.recipient == null -> tr("Enchère")
                                else -> null
                            }).joinToString(" · "), color = Mf.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                        listing.wanted?.let { Text(tr("En échange de") + " " + (catalog[it]?.title ?: it), style = MaterialTheme.typography.bodySmall) }
                        val date = Instant.ofEpochMilli(listing.end).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM · HH:mm"))
                        Text(tr("Fin") + " · " + date, style = MaterialTheme.typography.bodySmall)
                        if (listing.mine && listing.bid == 0) TextButton({ task { ArsenalGame.act(ctx, "cancel") { put("key", listing.key) } } }, enabled = !busy) { Text(tr("Annuler")) }
                        if (listing.incoming) TextButton({ accept = listing }, enabled = !busy) { Text(tr("Voir la proposition")) }
                        if (listing.incoming) TextButton({ task {
                            ArsenalGame.act(ctx, "decline") { put("key", listing.key) }
                        } }, enabled = !busy) { Text(tr("Refuser")) }
                        if (!listing.mine && listing.fixed) TextButton({ accept = listing }, enabled = !busy) { Text(tr("Acheter")) }
                        if (!listing.mine && !listing.fixed && listing.recipient == null) TextButton({
                            amount = maxOf(listing.price, listing.bid+1).toString(); bidFor = listing
                        }, enabled = !busy) { Text(tr("Enchérir")) }
                    }
                }
            }
        }
    }
    if (purchaseVisible) AlertDialog(
        onDismissRequest = { if (!busy) purchaseVisible = false },
        title = { Text(tr("Acheter un paquet")) },
        text = { Column {
            GoldBadge(packPrice)
            Text(tr("Le paquet rejoint tes paquets disponibles."))
            if (gold < packPrice) Text(tr("Il te manque de l’or."), color = MaterialTheme.colorScheme.error)
            issue?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton({
            val request = purchase ?: return@TextButton
            task {
                ArsenalGame.purchasePack(ctx, request)
                purchase = null
                purchaseVisible = false
                nav.go("cards/packs")
            }
        }, enabled = !busy && gold >= packPrice) { Text(tr("Acheter")) } },
        dismissButton = { TextButton({ purchaseVisible = false }, enabled = !busy) { Text(tr("Fermer")) } },
    )
    bidFor?.let { listing -> AlertDialog(onDismissRequest = { bidFor = null }, title = { Text(tr("Ton enchère")) },
        text = {
            Column {
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(5) }, label = { Text(tr("Pièces d’or")) }, singleLine = true)
                Text(tr("La mise est réservée. Elle est rendue si quelqu’un surenchérit."), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = {
            val n = amount.toIntOrNull()
            TextButton({ bidFor = null; task { ArsenalGame.act(ctx, "bid") { put("key", listing.key); put("amount", n!!) } } },
                enabled = !busy && n != null && n in maxOf(listing.price, listing.bid+1)..minOf(10_000, gold + if (listing.leading) listing.bid else 0)) { Text(tr("Confirmer")) }
        }, dismissButton = { TextButton({ bidFor = null }) { Text(tr("Annuler")) } }) }
    accept?.let { listing ->
        val choices = saved.owned.filter { it.serverKey != null && it.id == listing.wanted &&
            it.serial !in saved.equipped.values && saved.pending?.pieces.orEmpty().none { pending -> pending.serial == it.serial } }
        var copy by remember(listing.key) { mutableStateOf<EquipmentPiece?>(null) }
        AlertDialog(
            onDismissRequest = { accept = null },
            title = { Text(tr(if (listing.fixed) "Confirmer l’achat" else "Confirmer l’échange")) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(catalog[listing.piece.id]?.title ?: listing.piece.id, style = MaterialTheme.typography.titleMedium)
                if (listing.wanted == null) GoldBadge(listing.price)
                else {
                    Text(tr("Choisis l’exemplaire que tu cèdes."))
                    if (choices.isEmpty()) Text(tr("Tu n’as pas d’exemplaire libre pour cet échange."), color = Mf.colors.textSecondary)
                    choices.forEach { piece ->
                        val definition = catalog[piece.id]
                        FilterChip(selected = copy == piece, onClick = { copy = piece },
                            label = { Text(tr(piece.finish.title) + " · " + tr("Niv.") + " ${piece.level}" +
                                (definition?.let { " · +" + (piece.boost(it) / 100f) + " %" } ?: "")) })
                    }
                }
            } },
            confirmButton = { TextButton({
                val piece = copy
                accept = null
                task { ArsenalGame.act(ctx, "buy") { put("key", listing.key); piece?.serverKey?.let { put("give", it) } } }
            }, enabled = !busy && (if (listing.wanted == null) gold >= listing.price else copy != null)) { Text(tr("Confirmer")) } },
            dismissButton = { TextButton({ accept = null }) { Text(tr("Annuler")) } },
        )
    }
    if (composer) {
        var selected by remember { mutableStateOf<EquipmentPiece?>(null) }
        var friend by remember { mutableStateOf<League.Friend?>(null) }
        var wanted by remember { mutableStateOf("") }
        var price by remember { mutableStateOf("25") }
        var fixed by remember { mutableStateOf(true) }
        var query by remember { mutableStateOf("") }
        val eligible = saved.owned.filter { it.serverKey != null && it.serial !in saved.equipped.values &&
            saved.pending?.pieces.orEmpty().none { pending -> pending.serial == it.serial } }
        val match = catalog.values.filter { wanted.length >= 2 && it.title.contains(wanted, ignoreCase = true) }.take(5)
        var wantedCard by remember { mutableStateOf<EquipmentCard?>(null) }
        ModalBottomSheet(onDismissRequest = { composer = false }, containerColor = Mf.colors.background) {
            LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item {
                    Text(tr(if (tab == 0) "Nouvelle vente" else "Nouvel échange"), style = MaterialTheme.typography.titleLarge)
                    if (eligible.isEmpty()) Text(tr("Ouvre un paquet avec le marché connecté pour obtenir une pièce échangeable."))
                }
                if (selected == null && eligible.isNotEmpty()) item {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(tr("Chercher dans ma collection")) }, singleLine = true)
                }
                items(selected?.let(::listOf) ?: eligible.filter { (catalog[it.id]?.title ?: it.id).contains(query.trim(), ignoreCase = true) }, key = { it.serial }) { piece ->
                    FilterChip(selected == piece, { selected = if (selected == piece) null else piece }, label = {
                        Text((catalog[piece.id]?.title ?: piece.id) + " · " + tr(piece.finish.title) + " · " + tr("Niv.") + " ${piece.level}")
                    })
                }
                if (tab == 0) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(fixed, { fixed = true }, label = { Text(tr("Prix fixe")) })
                        FilterChip(!fixed, { fixed = false }, label = { Text(tr("Enchère")) })
                    }
                }
                if (tab == 1) {
                    item { Text(tr("Destinataire")) }
                    if (friends.isEmpty()) item { Text(tr("Ajoute un ami qui a synchronisé son Arsenal.")) }
                    items(friends, key = { it.id }) { person ->
                        FilterChip(selected = friend == person, onClick = { friend = person }, label = { Text(person.shown) })
                    }
                    item {
                        OutlinedTextField(wanted, { wanted = it; wantedCard = null }, label = { Text(tr("Carte demandée (facultatif)")) }, singleLine = true)
                    }
                    items(match, key = { it.id }) { card ->
                        FilterChip(selected = wantedCard == card, onClick = { wantedCard = card }, label = { Text(card.title) })
                    }
                }
                if (wantedCard == null) item {
                    OutlinedTextField(price, { price = it.filter(Char::isDigit).take(5) }, label = { Text(tr("Prix en or")) }, singleLine = true)
                }
                item {
                    val gold = if (wantedCard != null) 1 else price.toIntOrNull() ?: 0
                    Button({
                        val piece = selected ?: return@Button
                        composer = false
                        task {
                            ArsenalGame.act(ctx, "list") {
                                put("key", piece.serverKey!!); put("price", gold); put("mode", if (tab != 0) "offer" else if (fixed) "fixed" else "auction")
                                friend?.cardTrade?.let { put("to", it) }; wantedCard?.id?.let { put("wanted", it) }
                            }
                        }
                    }, enabled = !busy && selected != null && gold in 1..10_000 && (tab == 0 || friend != null)) {
                        Text(tr(if (tab == 0) "Publier pour 24 h" else "Envoyer la proposition"))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArsenalQuestRoute(nav: NavHostController) {
    val ctx = LocalContext.current
    val saved by EquipmentStore.state.collectAsState()
    val wallet by ArsenalGame.remote.collectAsState()
    var progress by remember { mutableStateOf<ArsenalGame.Quests?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var syncing by remember { mutableStateOf(false) }
    LaunchedEffect(retry) {
        syncing = true; error = null
        try {
            val current = ArsenalGame.quests(ctx)
            progress = current
            ArsenalGame.advance(ctx, current)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
        finally { syncing = false }
    }
    ArsenalPage("Quêtes de l’Arsenal", nav, wallet?.gold ?: saved.gold) {
        ArsenalQuestContent(progress)
        val ready = saved.freePacks > 0 || saved.pending != null ||
            saved.rewardPending != null || (wallet?.packCredits ?: saved.packCredits).isNotEmpty()
        if (ready) Button({ nav.go("cards/packs") }, Modifier.fillMaxWidth()) { Text(tr("Ouvrir mes paquets disponibles")) }
        Text(tr("L’or des quêtes est crédité à la synchronisation. Les paquets s’achètent en boutique."), style = MaterialTheme.typography.bodySmall)
        if (error != null) TextButton({ retry++ }, enabled = !syncing) { Text(tr("Réessayer la synchronisation")) }
        progress?.gate?.let { gate ->
            gate.targets.forEach { track ->
                ListItem(headlineContent = { Text(tr(track.label)) },
                    leadingContent = { Icon(if (track in gate.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null) },
                    colors = ListItemDefaults.colors(containerColor = Mf.colors.card), modifier = Modifier.clip(RoundedCornerShape(14.dp)))
            }
        }
        TextButton({ nav.go("group/score") }) { Text(tr("Choisir mes suivis")) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
