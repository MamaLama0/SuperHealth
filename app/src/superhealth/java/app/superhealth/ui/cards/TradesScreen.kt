package app.superhealth.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.cards.*
import app.superhealth.league.League
import app.superhealth.ui.tr
import kotlinx.coroutines.launch

/** Un joueur avec qui échanger : un ami qui partage son identifiant d'échange (ou le rival du mode démo). */
private data class Partner(val tradeId: String, val name: String)

@Composable
private fun partners(): List<Partner> {
    val league by League.state.collectAsState()
    val st by CardStore.state.collectAsState()
    return buildList {
        league.friends.forEach { f -> f.cardTrade?.let { add(Partner(it, f.shown)) } }
        if (st.demo) add(Partner(CardStore.Demo.TRADE_ID, "lohan"))
    }
}

@Composable
private fun nameOf(tradeId: String?, partners: List<Partner>) = partners.firstOrNull { it.tradeId == tradeId }?.name ?: tr("un joueur")

/**
 * Échanges entre joueurs : pas de boutique, les cartes ne changent de main qu'entre amis. Carte contre carte de même
 * rareté, ou carte contre or (dans un sens ou dans l'autre). Ce qu'on propose est mis de côté jusqu'à la réponse.
 */
@Composable
fun TradesScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by CardStore.state.collectAsState()
    val people = partners()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf<Partner?>(null) }
    LaunchedEffect(Unit) { CardStore.load(ctx); runCatching { CardStore.refresh(ctx) }; CardStore.seedDemoTrades(ctx) }
    fun act(block: suspend () -> Unit) { if (!busy) scope.launch { busy = true; error = null; runCatching { block() }.onFailure { error = it.message }; busy = false } }

    var zoom by remember { mutableStateOf<Pull?>(null) }
    zoom?.let { CardZoom(it) { zoom = null }; return }
    composing?.let { p -> TradeComposer(p, onZoom = { zoom = it }, onClose = { composing = null }) { give, want -> act { CardStore.offer(ctx, p.tradeId, give, want); composing = null; Rumble.reveal(ctx) } }; return }

    CardScaffold("Échanges", { nav.popBackStack() }, Color(0xFF1FA67A)) {
        Wallet(st)
        Text(tr("Pas de boutique : les cartes se gagnent à l'entraînement et ne changent de main qu'entre amis. Carte contre carte de même rareté, ou carte contre or."),
            color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }

        SectionTitle("Proposer un échange")
        if (people.isEmpty()) Text(tr("Tes amis apparaîtront ici dès qu'ils auront ouvert un pack avec la version 3.6."), color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
        else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            people.forEach { p ->
                Column(Modifier.width(96.dp).clip(RoundedCornerShape(20.dp)).background(InkCard).border(1.dp, Color(0xFF1FA67A).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .clickable { composing = p }.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(50)).background(Brush.linearGradient(listOf(Color(0xFF1FA67A), Color(0xFF8AE0FF)))), contentAlignment = Alignment.Center) {
                        Text(p.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                    Text("@" + p.name.lowercase().replace(" ", ""), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp))
                }
            }
        }

        SectionTitle("Reçus")
        if (st.trades.incoming.isEmpty()) Text(tr("Aucune proposition pour l'instant."), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        st.trades.incoming.forEach { t ->
            TradeRow("@" + nameOf(t.from, people) + " " + tr("te propose"), t.give, t.want, { zoom = it }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlowButton(tr("Accepter"), Modifier.weight(1f), listOf(Color(0xFF1FA67A), Color(0xFF6FD3A0)), enabled = !busy) {
                        act { if (CardStore.answer(ctx, t, "accept")) Rumble.reveal(ctx) else error = tr("Cet échange n'est plus disponible.") }
                    }
                    SmallAction(tr("Refuser"), Modifier.weight(1f)) { act { CardStore.answer(ctx, t, "decline") } }
                }
            }
        }

        SectionTitle("Envoyés")
        if (st.trades.out.isEmpty()) Text(tr("Ce que tu proposes est mis de côté jusqu'à la réponse, sept jours au plus."), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        st.trades.out.forEach { t ->
            TradeRow(tr("Pour") + " @" + nameOf(t.to, people) + " · " + tr("en attente"), t.give, t.want, { zoom = it }) {
                SmallAction(tr("Annuler et récupérer"), Modifier.fillMaxWidth()) { act { CardStore.answer(ctx, t, "cancel") } }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SmallAction(text: String, modifier: Modifier, onClick: () -> Unit) = Text(text, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
    modifier = modifier.height(54.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick).wrapContentHeight())

/** Une proposition : ce qui est donné à gauche, ce qui est demandé à droite, du point de vue de celui qui propose. */
@Composable
private fun TradeRow(title: String, give: Economy.Stake, want: Economy.Stake, onZoom: (Pull) -> Unit, actions: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(InkCard).padding(14.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Row(Modifier.padding(vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StakeView(give, Modifier.weight(1f), onZoom)
            Icon(Icons.Rounded.SwapHoriz, null, Modifier.padding(horizontal = 8.dp).size(30.dp), tint = Color(0xFF6FD3A0))
            StakeView(want, Modifier.weight(1f), onZoom)
        }
        actions()
    }
}

@Composable
private fun StakeView(s: Economy.Stake, modifier: Modifier, onZoom: (Pull) -> Unit = {}) {
    val pull = s.pull
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (pull != null) {
            CollectibleCard(pull.card, pull.finish, Modifier.fillMaxWidth(0.8f).clickable { onZoom(pull) }, live = pull.card.rarity.big)
            Text(pull.card.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
            Text(pull.card.rarity.symbol + " " + pull.card.rarityText() + if (pull.finish != Finish.NORMALE) " · " + tr(pull.finish.label) else "", color = pull.card.rarity.color.c(), fontSize = 11.sp, maxLines = 1)
        } else Box(Modifier.fillMaxWidth(0.8f).aspectRatio(0.714f).clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0E2A3A), Color(0xFF1B5D7A)))).border(1.dp, EclatColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✦", color = EclatColor, fontSize = 30.sp)
                Text("${s.eclats ?: 0}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text(tr("or"), color = EclatColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Composer une proposition : ce que je donne (une carte ou de l'or), puis ce que je demande. */
@Composable
private fun TradeComposer(partner: Partner, onZoom: (Pull) -> Unit, onClose: () -> Unit, onSend: (Economy.Stake, Economy.Stake) -> Unit) {
    val ctx = LocalContext.current
    val st by CardStore.state.collectAsState()
    var theirs by remember { mutableStateOf<Map<String, Int>?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(partner) { runCatching { CardStore.browse(ctx, partner.tradeId) }.onSuccess { theirs = it }.onFailure { failed = it.message } }
    var give by remember { mutableStateOf<Economy.Stake?>(null) }
    var want by remember { mutableStateOf<Economy.Stake?>(null) }
    val giveRarity = give?.pull?.card?.rarity
    CardScaffold("Échange avec @" + partner.name.lowercase().replace(" ", ""), onClose, Color(0xFF1FA67A)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("JE DONNE"), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                give?.let { StakeView(it, Modifier.fillMaxWidth().padding(top = 6.dp), onZoom) } ?: EmptyStake()
            }
            Icon(Icons.Rounded.SwapHoriz, null, Modifier.padding(horizontal = 8.dp).size(32.dp), tint = Color(0xFF6FD3A0))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("JE DEMANDE"), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                want?.let { StakeView(it, Modifier.fillMaxWidth().padding(top = 6.dp), onZoom) } ?: EmptyStake()
            }
        }
        val g = give; val w = want
        val valid = g != null && w != null && (g.card != null || w.card != null) &&
            (g.card == null || w.card == null || g.pull?.card?.rarity == w.pull?.card?.rarity)
        if (give?.card != null || want?.card != null) Text(tr("Touche une carte pour la voir en grand"), color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        GlowButton(tr("Envoyer la proposition"), Modifier.fillMaxWidth().padding(top = 16.dp), listOf(Color(0xFF1FA67A), Color(0xFF6FD3A0)), enabled = valid) { if (g != null && w != null) onSend(g, w) }
        if (g?.card != null && w?.card != null && !valid) Text(tr("Deux cartes s'échangent à rareté égale."), color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

        SectionTitle("Je donne")
        EclatPicker(st.eclats, (give?.eclats)) { give = it?.let { n -> Economy.Stake(eclats = n) }; if (it != null && want?.card == null) want = null }
        val mine = st.owned.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).mapNotNull { (k, n) -> Pull.of(k)?.let { it to n } }
        CardStrip(mine, selected = give?.card, hint = { (p, n) -> if (n > 1) "×$n · " + tr("doublon") else tr("dernier exemplaire") }) { give = Economy.Stake(card = it.key) }

        SectionTitle("Je demande")
        EclatPicker(5000, want?.eclats) { want = it?.let { n -> Economy.Stake(eclats = n) } }
        when {
            failed != null -> Text(failed!!, color = Color(0xFFFF8A80), fontSize = 13.sp)
            theirs == null -> Text(tr("Chargement de sa collection…"), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            else -> {
                val list = theirs!!.entries.mapNotNull { (k, n) -> Pull.of(k)?.let { it to n } }.filter { giveRarity == null || it.first.card.rarity == giveRarity }
                    .sortedWith(compareByDescending<Pair<Pull, Int>> { !st.owns(it.first.card) }.thenByDescending { it.first.card.rarity }.thenBy { it.first.key })
                if (list.isEmpty()) Text(tr("Aucune carte de cette rareté chez ton ami."), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                CardStrip(list, selected = want?.card, hint = { (p, n) -> if (!st.owns(p.card)) tr("il te manque") else "×$n" }) { want = Economy.Stake(card = it.key) }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun EmptyStake() = Box(Modifier.padding(top = 6.dp).fillMaxWidth(0.8f).aspectRatio(0.714f).clip(RoundedCornerShape(14.dp))
    .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
    Text("?", color = Color.White.copy(alpha = 0.3f), fontSize = 34.sp, fontWeight = FontWeight.Black)
}

/** De l'or en mise : interrupteur puis curseur (par 10). */
@Composable
private fun EclatPicker(max: Int, value: Int?, onChange: (Int?) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(InkCard).clickable { onChange(if (value == null) minOf(50, max).coerceAtLeast(1) else null) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✦ " + tr("De l'or"), color = EclatColor, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(value?.let { "$it" } ?: tr("non"), color = Color.White, fontWeight = FontWeight.Black)
        }
        if (value != null && max >= 10) Slider(value.toFloat(), { onChange((it / 10).toInt().coerceAtLeast(1) * 10) }, valueRange = 10f..max.coerceAtMost(5000).toFloat(),
            colors = SliderDefaults.colors(thumbColor = EclatColor, activeTrackColor = EclatColor))
    }
}

@Composable
private fun CardStrip(cards: List<Pair<Pull, Int>>, selected: String?, hint: (Pair<Pull, Int>) -> String, onPick: (Pull) -> Unit) {
    Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.take(80).forEach { pair ->
            val (p, _) = pair; val on = p.key == selected
            Column(Modifier.width(92.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Color(0xFF1FA67A).copy(alpha = 0.35f) else Color.Transparent)
                .border(if (on) 2.dp else 0.dp, if (on) Color(0xFF6FD3A0) else Color.Transparent, RoundedCornerShape(14.dp)).clickable { onPick(p) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                CollectibleCard(p.card, p.finish, Modifier.fillMaxWidth())
                Text(hint(pair), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

/** Une carte en grand : vivante et inclinable, avec sa rareté, son type (biome, élément, duo), son effet et son bonus. */
@Composable
private fun CardZoom(pull: Pull, onClose: () -> Unit) {
    val st by CardStore.state.collectAsState()
    val tilt by rememberTilt()
    val card = pull.card
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Ink).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onClick = onClose)
        .statusBarsPadding().navigationBarsPadding().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CollectibleCard(card, pull.finish, Modifier.fillMaxWidth(0.78f).graphicsLayer { rotationY = tilt.x * 10f; rotationX = -tilt.y * 8f; cameraDistance = 14f * density },
                live = true, tilt = { tilt })
            Text(card.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.padding(top = 18.dp))
            Text(card.rarity.symbol + " " + card.rarityText() + " · " + card.code, color = card.rarity.color.c(), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            // Type : biome(s) et élément(s) ; un duo en a deux.
            Text(card.elements.joinToString(" + ") { tr(it.label) + " (" + tr(it.magic) + ", " + it.stat.code + ")" } + if (card.duo) " · " + tr("duo") else "",
                color = card.element.mid.c(), fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            val effect = when (pull.finish) {
                Finish.NORMALE -> tr("Sans effet")
                Finish.BRILLANTE -> tr("Brillante : reflet holographique, bonus × 1,1")
                Finish.ANIMEE -> tr("Animée : fond en mouvement, bonus × 1,25")
                Finish.CHROMATIQUE -> tr("Chromatique : toute la scène irisée, bonus × 1,5")
            }
            Text(effect, color = if (pull.finish == Finish.NORMALE) Color.White.copy(alpha = 0.6f) else Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            Text(Bonus.label(pull, 1) + " " + tr("en duel"), color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            val mine = st.owned[pull.key] ?: 0
            Text(if (mine > 0) tr("Tu en as") + " $mine" else if (st.owns(card)) tr("Tu as ce fond dans une autre finition") else tr("Il te manque"),
                color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            Text(tr("Touche pour fermer"), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp))
        }
    }
}
