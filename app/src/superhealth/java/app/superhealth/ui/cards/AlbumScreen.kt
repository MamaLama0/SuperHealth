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
import app.superhealth.ui.tr
import kotlinx.coroutines.launch

@Composable
fun AlbumScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val state by CardStore.state.collectAsState()
    var filter by remember { mutableStateOf<Rarity?>(null) }
    var biome by remember { mutableStateOf<Element?>(null) }
    var selected by remember { mutableStateOf<CardDef?>(null) }
    LaunchedEffect(Unit) { CardStore.load(ctx) }
    val owned = Catalog.cards.count { state.owns(it) }
    CardScaffold("Album", { nav.popBackStack() }, Color(0xFF7C5CFF), scroll = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Série Nature"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text("$owned / ${Catalog.cards.size} " + tr("fonds"), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
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
        }
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 40.dp)) {
            items(Catalog.cards.filter { (filter == null || it.rarity == filter) && (biome == null || biome in it.elements) }, key = { it.id }) { card ->
                val finishes = Finish.entries.filter { (state.owned["${card.id}:${it.name}"] ?: 0) > 0 }
                CollectibleCard(card, finishes.maxOrNull() ?: Finish.NORMALE, Modifier.clickable { selected = card }, owned = finishes.isNotEmpty(),
                    level = state.level(card).takeIf { finishes.isNotEmpty() })
            }
            item(span = { GridItemSpan(3) }) {
                Text(tr("Les fonds se gagnent dans les packs de leur biome. Les doublons les font monter de niveau, puis rendent des éclats ; un fond qui manque s'achète aussi en éclats."), color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    selected?.let { CardDetail(it) { selected = null } }
}

@Composable
private fun Chip(text: String, on: Boolean, color: Color, onClick: () -> Unit) = Text(text, color = if (on) Ink else color, fontWeight = FontWeight.Bold, fontSize = 13.sp,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) color else Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp))

/** Fiche d'un fond : aperçu vivant, finitions possédées, application à la carte ou fabrication. */
@Composable
private fun CardDetail(card: CardDef, onClose: () -> Unit) {
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
            Text(card.rarity.symbol + " " + tr(card.rarity.label) + " · " + card.code + " · " + card.elements.joinToString(" + ") { tr(it.label) }, color = card.rarity.color.c(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            val level = state.level(card)
            Text(tr("Niv.") + " ${if (finishes.isEmpty()) 1 else level} · " + Bonus.label(card, if (finishes.isEmpty()) 1 else level) + " " + tr("en duel") +
                (Levels.progress(state.copies(card))?.takeIf { finishes.isNotEmpty() }?.let { (h, n) -> " · $h/$n " + tr("doublons") } ?: ""),
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
            } else {
                Text(tr("Pas encore dans ta collection."), color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp))
                val scope = rememberCoroutineScope()
                var error by remember { mutableStateOf<String?>(null) }
                GlowButton(tr("Obtenir · ${card.rarity.craft} éclats"), Modifier.fillMaxWidth().padding(top = 16.dp), listOf(Color(0xFF2F6FDE), EclatColor),
                    enabled = state.eclats >= card.rarity.craft) { scope.launch {
                        runCatching { CardStore.craft(ctx, card) }.onSuccess { Rumble.reveal(ctx); finish = Finish.NORMALE }.onFailure { error = it.message }
                    } }
                error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}
