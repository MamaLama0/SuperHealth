package app.superhealth.ui.cards

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.navigation.NavHostController
import app.superhealth.cards.*
import app.superhealth.ui.go
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.launch

@Composable
fun AlbumScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val state by CardStore.state.collectAsState()
    var filter by remember { mutableStateOf<Rarity?>(null) }
    var biome by remember { mutableStateOf<Element?>(null) }
    var duos by remember { mutableStateOf(false) }
    var chroma by remember { mutableStateOf(false) }
    fun ownsChroma(c: CardDef) = (state.owned["${c.id}:${Finish.CHROMATIQUE.name}"] ?: 0) > 0
    var selected by remember { mutableStateOf<CardDef?>(null) }
    LaunchedEffect(Unit) { CardStore.load(ctx) }
    val owned = Catalog.cards.count { state.owns(it) }
    CardScaffold("Album", { nav.popBackStack() }, Color(0xFF7C5CFF), scroll = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Série Nature"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text("$owned / ${Catalog.cards.size} " + tr("fonds") + " · ${Catalog.cards.count(::ownsChroma)} / ${Catalog.cards.size} " + tr("chromatiques"), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
            WalletChip(Icons.Rounded.AutoAwesome, "${state.eclats} " + tr("éclats"), EclatColor)
        }
        Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(owned / Catalog.cards.size.toFloat()).fillMaxHeight().clip(RoundedCornerShape(50)).background(Color(0xFF9AA8FF)))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(tr("Tous les biomes"), biome == null, Color.White) { biome = null }
            Element.entries.forEach { e ->
                val n = Catalog.cards.count { e in it.elements && state.owns(it) }
                Chip(tr(e.label) + " $n/${Catalog.cards.count { e in it.elements }}", biome == e, e.mid.c()) { biome = if (biome == e) null else e }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(tr("Toutes"), filter == null, Color.White) { filter = null }
            Rarity.entries.forEach { r -> Chip(r.symbol + " " + tr(r.label), filter == r, r.color.c()) { filter = if (filter == r) null else r } }
            Chip("✺ " + tr("Chromatiques"), chroma, Color(0xFFFF8AD8)) { chroma = !chroma }
            Chip("◈ " + tr("Duos") + " ${Catalog.cards.count { it.duo && state.owns(it) }}/${Catalog.cards.count { it.duo }}", duos, Gold) { duos = !duos }
        }
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 40.dp)) {
            items(Catalog.cards.filter { (filter == null || it.rarity == filter) && (biome == null || biome in it.elements) && (!duos || it.duo) }, key = { it.id }) { card ->
                val finishes = Finish.entries.filter { (state.owned["${card.id}:${it.name}"] ?: 0) > 0 }
                // Vue chromatique : la variante elle-même, possédée ou non.
                val shown = if (chroma) listOf(Finish.CHROMATIQUE).filter { it in finishes } else finishes
                Box {
                    CollectibleCard(card, if (chroma) Finish.CHROMATIQUE else finishes.maxOrNull() ?: Finish.NORMALE, Modifier.clickable { selected = card }, owned = shown.isNotEmpty(),
                        level = state.level(card).takeIf { finishes.isNotEmpty() })
                    // Exemplaires possédés, bien lisibles : ×N en bas de la carte dès qu'il y a un doublon.
                    val copies = state.copies(card)
                    if (copies > 1) Text("×$copies", color = Ink, fontWeight = FontWeight.Black, fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 7.dp, vertical = 1.dp))
                    if (state.canFuse(card)) Text("▲", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(50)).background(Color(0xFF1FA67A)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            item(span = { GridItemSpan(3) }) {
                Text(tr("Les fonds se gagnent dans les packs de leur biome. Fusionne les doublons pour renforcer un fond (2, 4, 16 puis 256 exemplaires, et 20 à 160 éclats), ou échange-les avec tes amis."), color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    selected?.let { CardDetail(it, onShare = { pull, level -> CardShare.open(pull, level, CardShare.Event.COLLECTION); selected = null; nav.go("social/compose") },
        onTrade = { selected = null; nav.go("cards/trades") }) { selected = null } }
}

@Composable
private fun Chip(text: String, on: Boolean, color: Color, onClick: () -> Unit) = Text(text, color = if (on) Ink else color, fontWeight = FontWeight.Bold, fontSize = 13.sp,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) color else Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp))

/** Fiche d'un fond : aperçu vivant, finitions possédées, application à la carte, fusion des doublons et échange. */
@Composable
private fun CardDetail(card: CardDef, onShare: (Pull, Int) -> Unit, onTrade: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val state by CardStore.state.collectAsState()
    val snap by CardStore.snapshot.collectAsState()
    val photo by CardPhoto.bitmap.collectAsState()
    val tilt by rememberTilt()
    val finishes = Finish.entries.filter { (state.owned["${card.id}:${it.name}"] ?: 0) > 0 }
    var finish by remember { mutableStateOf(finishes.maxOrNull() ?: Finish.NORMALE) }
    var preview by remember { mutableStateOf(false) }
    val haptics = app.superhealth.ui.rememberHaptics()
    val appear = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.6f)) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable(remember { MutableInteractionSource() }, null, onClick = onClose).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.fillMaxWidth(0.72f).graphicsLayer { scaleX = appear.value; scaleY = appear.value; rotationY = tilt.x * 10f; rotationX = -tilt.y * 8f; cameraDistance = 14f * density }
                .clickable(remember { MutableInteractionSource() }, null) { if (finishes.isNotEmpty()) preview = !preview }) {
                if (preview) AthleteCard(myFace(snap?.now, Pull(card, finish)), Modifier.fillMaxWidth(), interactive = false)
                else CollectibleCard(card, finish, Modifier.fillMaxWidth(), owned = finishes.isNotEmpty(), live = true, tilt = { tilt }, level = state.level(card).takeIf { finishes.isNotEmpty() })
            }
            Text(card.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(top = 18.dp))
            Text(card.rarity.symbol + " " + card.rarityText() + " · " + card.code + " · " + card.elements.joinToString(" + ") { tr(it.label) }, color = card.rarity.color.c(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            val level = state.level(card)
            Text(tr("Niv.") + " ${if (finishes.isEmpty()) 1 else level} · " + Bonus.label(card, if (finishes.isEmpty()) 1 else level, finish) + " " + tr("en duel") +
                (state.fuseCost(card)?.takeIf { finishes.isNotEmpty() }?.let { n -> " · ${state.spare(card).coerceAtMost(n)}/$n " + tr("doublons") } ?: ""),
                color = card.element.mid.c(), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            if (finishes.isNotEmpty()) {
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    finishes.forEach { f -> Chip(tr(f.label) + " ×${state.owned["${card.id}:${f.name}"]}", finish == f, Color.White) { finish = f; haptics.toggle() } }
                }
                Text(tr(if (preview) "Touchez la carte pour revoir le fond" else "Touchez la carte pour l'essayer sur ta carte Athlète"), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                val applied = state.background == "${card.id}:${finish.name}"
                GlowButton(tr(if (applied) "Fond actuel" else "Appliquer à ma carte"), Modifier.fillMaxWidth().padding(top = 16.dp), enabled = !applied) {
                    CardStore.setBackground(ctx, "${card.id}:${finish.name}"); Rumble.reveal(ctx); onClose()
                }
                val scope = rememberCoroutineScope()
                var error by remember { mutableStateOf<String?>(null) }
                // Fusion : les doublons partent, le fond gagne un niveau et 10 % d'effet.
                state.fuseCost(card)?.let { cost ->
                    val spare = state.spare(card)
                    val price = state.fusePrice(card) ?: 0
                    GlowButton(when {
                        spare < cost -> tr("Fusion") + " : $spare/$cost " + tr("doublons")
                        state.eclats < price -> tr("Fusion") + " : $price ✦ (" + trf("tu en as %1\$s", state.eclats) + ")"
                        else -> tr("Fusionner") + " · " + tr("niveau") + " ${level + 1} (+${level * 10} %) · $price ✦"
                    }, Modifier.fillMaxWidth().padding(top = 10.dp), listOf(Color(0xFF1FA67A), Color(0xFF6FD3A0)), enabled = spare >= cost && state.eclats >= price) { scope.launch {
                            runCatching { CardStore.fuse(ctx, card) }.onSuccess { Rumble.reveal(ctx); haptics.toggle() }.onFailure { error = it.message }
                        } }
                } ?: Text(tr("Niveau max : +40 % d'effet"), color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("Échanger"), color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(InkCard).clickable(onClick = onTrade).padding(14.dp))
                    Text(tr("Partager"), color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(InkCard).clickable { onShare(Pull(card, finish), level) }.padding(14.dp))
                }
            } else {
                Text(tr("Pas encore dans ta collection. Tire-le dans un pack ") + tr(Booster.of(card.element).label) + tr(", ou demande-le à un ami en échange."), color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                Text(tr("Proposer un échange"), color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(InkCard).clickable(onClick = onTrade).padding(14.dp))
            }
        }
    }
}
