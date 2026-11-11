package app.superhealth.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.cards.*
import app.superhealth.league.League
import app.superhealth.ui.go
import app.superhealth.ui.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class DuelMatch(val mine: AthleteStats, val opponent: Opponent, val battle: CharacterCombat.Battle)

data class Opponent(val id: String, val name: String, val stats: AthleteStats, val bg: Pull?, val note: String, val level: Int = 1, val friend: League.Friend? = null) {
    val face get() = CardFace(name, stats, bg, level = level)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuelScreen(nav: NavHostController, friendId: String? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snap by CardStore.snapshot.collectAsState()
    val state by CardStore.state.collectAsState()
    val league by League.state.collectAsState()
    val remote by ArsenalGame.remote.collectAsState()
    var character by remember { mutableStateOf(CharacterStats()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var inbox by rememberSaveable { mutableStateOf(false) }
    var request by rememberSaveable { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var match by remember { mutableStateOf<DuelMatch?>(null) }
    fun task(action: suspend () -> Unit) {
        if (busy) return
        busy = true; notice = null
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: ArsenalGame.Upgrade) { notice = tr(e.message.orEmpty()) }
            catch (_: Exception) { notice = tr("Action indisponible. Actualise l’arène puis réessaie.") }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) {
        CardStore.load(ctx); CardPhoto.load(ctx); EquipmentStore.load(ctx)
        try {
            ArsenalGame.character(ctx)
            CardStore.refresh(ctx)
            character = ArsenalGame.character(ctx); loaded = true
            if (Economy.available()) ArsenalGame.act(ctx, "state")
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { notice = tr("Les défis en ligne sont indisponibles. L’entraînement reste accessible.") }
    }
    val mine = snap?.now ?: state.lastStats ?: AthleteStats()
    val opponents = buildList {
        league.friends.forEach { f -> add(Opponent(f.id, f.shown, f.athlete ?: AthleteStats(), f.cardBg?.let(Pull::of), tr("Ami"), f.cardLevel ?: 1, f)) }
        snap?.month?.let { add(Opponent("past", handle(), it, CardStore.background(), tr("Entraînement · anciennes stats"))) }
        if (state.demo) add(Opponent("demo", "Lohan", CardStore.Demo.rival, null, tr("Entraînement")))
    }
    fun watch(duel: ArsenalGame.Duel) {
        val me = duel.mine ?: return
        val them = duel.theirs ?: return
        val opponent = opponents.firstOrNull { it.friend?.cardTrade == duel.opponent }
            ?: Opponent(duel.opponent, tr("Ami"), AthleteStats(), null, tr("Duel terminé"))
        match = DuelMatch(mine, opponent, CharacterCombat.battle(me, them))
        inbox = false
    }
    match?.let { duel ->
        DuelArena(duel.mine, duel.opponent, duel.battle) { match = null }
        return
    }
    DuelLobby(mine, character, opponents, loaded && !busy, friendId,
        { nav.popBackStack() }, { nav.go("friends/add") },
        { inbox = true }, remote?.duels.orEmpty().count { it.incoming && it.status == "pending" }, notice, compare = { opponent ->
            val other = opponent.stats.character ?: CharacterStats.legacy(opponent.stats)
            match = DuelMatch(mine, opponent, CharacterCombat.battle(if (opponent.stats.character == null) CharacterStats.legacy(mine) else character, other))
        }) { opponent ->
        if (opponent.friend?.cardTrade != null) task {
            ArsenalGame.duel(ctx, "invite", opponent.friend.cardTrade, request)
            request = java.util.UUID.randomUUID().toString()
            notice = tr("Défi envoyé ! Ton ami pourra accepter la comparaison.")
            inbox = true
        } else {
            val other = CharacterStats.legacy(opponent.stats)
            match = DuelMatch(mine, opponent, CharacterCombat.battle(character, other))
        }
    }
    if (inbox) ModalBottomSheet(onDismissRequest = { inbox = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Mes défis"), style = MaterialTheme.typography.headlineSmall)
            Text(tr("Défis valables 3 jours. Résultats conservés 7 jours. Aucun objet ni or misé."), style = MaterialTheme.typography.bodySmall)
            if (remote?.duels.isNullOrEmpty()) Text(tr("Aucun défi pour le moment. Choisis un ami dans l’arène."))
            remote?.duels.orEmpty().sortedBy { if (it.incoming && it.status == "pending") 0 else 1 }.forEach { duel ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(opponents.firstOrNull { it.friend?.cardTrade == duel.opponent }?.name ?: tr("Ami"), style = MaterialTheme.typography.titleMedium)
                        Text(tr(when (duel.status) {
                            "complete" -> "Duel terminé"
                            "declined" -> "Défi décliné"
                            "cancelled" -> "Défi annulé"
                            else -> if (duel.incoming) "À toi de jouer" else "En attente de ton ami"
                        }))
                        if (duel.status == "complete") Button({ watch(duel) }) { Text(tr("Voir le combat")) }
                        else if (duel.status == "pending") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (duel.incoming) Button(enabled = !busy, onClick = { task {
                                val view = ArsenalGame.duel(ctx, "accept", duel.id, request)
                                view.duels.firstOrNull { it.id == duel.id }?.let(::watch)
                            } }) { Text(tr("Accepter")) }
                            TextButton(enabled = !busy, onClick = { task {
                                ArsenalGame.duel(ctx, if (duel.incoming) "decline" else "cancel", duel.id, request)
                            } }) { Text(tr(if (duel.incoming) "Décliner" else "Annuler")) }
                        }
                    }
                }
            }
            notice?.let { Text(it) }
            TextButton(enabled = !busy, onClick = { task { ArsenalGame.act(ctx, "state") } }) { Text(tr("Actualiser")) }
        }
    }
}
