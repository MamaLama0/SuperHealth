package app.superhealth.ui.cards

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlin.math.sin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.cards.*
import app.superhealth.league.League
import app.superhealth.ui.tr
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath

private val Red = Color(0xFFFF4F5E)
private val Blue = Color(0xFF6E7BFF)

data class Opponent(val id: String, val name: String, val stats: AthleteStats, val bg: Pull?, val note: String, val level: Int = 1, val friend: League.Friend? = null) {
    val face get() = CardFace(name, stats, bg, level = level)
}

@Composable
fun DuelScreen(nav: NavHostController, friendId: String? = null) {
    val ctx = LocalContext.current
    val snap by CardStore.snapshot.collectAsState()
    val state by CardStore.state.collectAsState()
    val league by League.state.collectAsState()
    LaunchedEffect(Unit) { CardStore.load(ctx); CardPhoto.load(ctx); if (CardStore.snapshot.value == null) runCatching { CardStore.refresh(ctx) } }
    val opponents = buildList {
        league.friends.forEach { f -> f.athlete?.let { add(Opponent(f.id, f.shown, it, f.cardBg?.let(Pull::of), tr("Ami"), f.cardLevel ?: 1, f)) } }
        snap?.month?.takeIf { it.ovr10 != null }?.let { add(Opponent("past", handle(), it, CardStore.background(), tr("Toi, il y a 4 semaines"), CardStore.background()?.let { b -> state.level(b.card) } ?: 1)) }
        if (state.demo) add(Opponent("demo", "lohan", CardStore.Demo.rival, Pull.of("N043:BRILLANTE"), tr("Rival de démo"), 3))
    }
    var chosen by remember { mutableStateOf(opponents.firstOrNull { it.id == friendId }) }
    val mine = snap?.now
    val c = chosen
    if (c != null && mine != null) { DuelScene(mine, c) { chosen = null }; return }
    CardScaffold("Duel", { nav.popBackStack() }, Red) {
        Text(tr("Les stats s'affrontent une à une, bonus de fond compris. La meilleure carte achève l'autre avec le pouvoir de son biome."), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        if (mine?.ovr10 == null) Text(tr("Ta carte doit d'abord être évaluée (quelques séances)."), color = Gold, modifier = Modifier.padding(top = 16.dp))
        SectionTitle("Adversaires")
        if (opponents.isEmpty()) Text(tr("Ajoute un ami, ou reviens dans quelques semaines pour affronter ta carte passée."), color = Color.White.copy(alpha = 0.55f))
        opponents.forEach { o ->
            Row(Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(InkCard).clickable(enabled = mine?.ovr10 != null) { chosen = o }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AthleteCard(o.friend?.let { friendFace(it) } ?: o.face, Modifier.width(64.dp), interactive = false, animate = false)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text("@" + o.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(o.note, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                }
                Text(o.stats.ovr?.toString() ?: "–", color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
            }
        }
    }
}


/** Coup final propre à chaque biome. */
private val Element.finisher get() = when (this) { Element.FEU -> "Éruption"; Element.GLACE -> "Blizzard"; Element.FOUDRE -> "Foudroiement"; Element.TERRE -> "Séisme" }

/** Élément(s) d'une carte : ceux de son fond, sinon celui de sa meilleure stat (fond de palier). */
private fun elementsOf(art: CardDef, stats: AthleteStats): List<Element> =
    if (!art.neutral) art.elements else listOf(Element.of(Stat.families.maxByOrNull { stats[it] ?: 0 } ?: Stat.PEC)!!)

private const val INTRO = 0.6f
private const val STEP = 0.28f
private const val FILL = 0.45f
private const val FINISH = 2.0f

/**
 * Le duel, simple : les deux cartes s'avancent, leurs stats (bonus de fond compris) s'affrontent une à une au centre,
 * puis le total désigne la gagnante, qui achève l'autre avec l'élément de son fond (une épique lance ses deux biomes).
 * Un appui passe au bilan.
 */
@Composable
private fun DuelScene(mine: AthleteStats, o: Opponent, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val face = myFace(mine)
    val myArt = face.background?.card ?: Catalog.defaultFor(mine.tier)
    val theirArt = o.bg?.card ?: Catalog.defaultFor(o.stats.tier)
    val mb = face.bonus; val ob = o.face.bonus
    val pairs = remember(mine, o, mb) {
        Stat.entries.mapNotNull { s ->
            val a = mine[s] ?: return@mapNotNull null; val b = o.stats[s] ?: return@mapNotNull null
            Triple(s, (a + (mb[s] ?: 0.0)).roundToInt(), (b + (ob[s] ?: 0.0)).roundToInt())
        }
    }
    val myTotal = pairs.sumOf { it.second }; val theirTotal = pairs.sumOf { it.third }
    val iWin = myTotal > theirTotal || (myTotal == theirTotal && (mine.ovr10 ?: 0) >= (o.stats.ovr10 ?: 0))
    val statsEnd = INTRO + pairs.size * STEP + FILL
    val strike = statsEnd + 0.6f
    val total = strike + FINISH
    var t by remember { mutableFloatStateOf(0f) }
    var showResult by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        val fired = mutableSetOf<Int>()
        while (true) withFrameNanos { now ->
            t = (t + (now - last) / 1e9f).coerceAtMost(total + 0.5f); last = now
            if (t > 0.35f && fired.add(-1)) Rumble.thud(ctx)
            pairs.indices.forEach { i -> if (t > INTRO + i * STEP + FILL && t < statsEnd + 0.2f && fired.add(i)) Rumble.tick(ctx, 0.5f) }
            if (t > statsEnd && fired.add(100)) Rumble.thud(ctx)
            if (t > strike && fired.add(101)) Rumble.rise(ctx)
            if (t > strike + 0.85f && fired.add(102)) Rumble.reveal(ctx)
            if (t >= total && !showResult) showResult = true
        }
    }
    val f = t - strike
    val winArt = if (iWin) myArt else theirArt
    val els = elementsOf(winArt, if (iWin) mine else o.stats)
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink).clickable(remember { MutableInteractionSource() }, null) { if (showResult) onBack() else t = total }) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val cardW = w * 0.36f; val cardH = cardW / 0.714f
        val oppCenter = Offset(w * 0.5f, h * 0.06f + cardH / 2 + with(density) { 24.dp.toPx() }); val myCenter = Offset(w * 0.5f, h * 0.93f - cardH / 2)
        val winC = if (iWin) myCenter else oppCenter; val loseC = if (iWin) oppCenter else myCenter
        // Secousse à l'impact du coup final.
        val shake = if (f in 0.85f..1.45f) sin(f * 95f) * 22f * (1.45f - f) / 0.6f else 0f
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = shake; translationY = shake * 0.5f }) {
            // Décor : le biome de l'adversaire en haut, le tien en bas, séparés par une diagonale.
            CardArt(theirArt, Finish.NORMALE, Modifier.fillMaxSize().drawWithContent {
                clipPath(Path().apply { moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h * 0.44f); lineTo(0f, h * 0.56f); close() }) { this@drawWithContent.drawContent() } }, time = { t }, holo = false)
            CardArt(myArt, Finish.NORMALE, Modifier.fillMaxSize().drawWithContent {
                clipPath(Path().apply { moveTo(0f, h * 0.56f); lineTo(w, h * 0.44f); lineTo(w, h); lineTo(0f, h); close() }) { this@drawWithContent.drawContent() } }, time = { t }, holo = false)
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.Black.copy(alpha = 0.62f))
                // Éclair de la couleur du coup final.
                if (f > 0.8f) drawRect(els.first().mid.c().copy(alpha = (0.45f * (1f - abs(f - 0.9f) / 0.35f)).coerceIn(0f, 0.45f)), blendMode = BlendMode.Plus)
            }
            val fall = ((f - 1.05f) / 0.5f).coerceIn(0f, 1f)
            @Composable fun Fighter(fc: CardFace, center: Offset, meSide: Boolean) {
                val winner = meSide == iWin
                Box(Modifier.offset { androidx.compose.ui.unit.IntOffset((center.x - cardW / 2).toInt(), (center.y - cardH / 2).toInt()) }
                    .size(with(density) { cardW.toDp() }, with(density) { cardH.toDp() })
                    .graphicsLayer {
                        val enter = (t / 0.45f).coerceIn(0f, 1f); val ease = 1f - (1f - enter) * (1f - enter)
                        translationX = (1 - ease) * (if (meSide) -w else w)
                        rotationZ = if (meSide) -4f else 4f
                        if (winner) {
                            // La gagnante se charge puis s'élance vers l'autre carte.
                            val charge = (f / 0.5f).coerceIn(0f, 1f) * (1f - ((f - 1.2f) / 0.4f).coerceIn(0f, 1f))
                            val lunge = if (f in 0.45f..0.95f) sin(((f - 0.45f) / 0.5f) * PI.toFloat()) else 0f
                            val s = 1f + 0.12f * charge; scaleX = s; scaleY = s
                            translationY += lunge * (if (meSide) -1f else 1f) * h * 0.05f
                        } else if (f > 0.85f) {
                            val k = (1f - (f - 0.85f) / 0.4f).coerceIn(0f, 1f)
                            rotationY = (if (meSide) 1f else -1f) * k * 22f
                            rotationZ += fall * (if (meSide) -26f else 26f)
                            translationY += fall * 70f * density.density * (if (meSide) 1f else -1f)
                            alpha = 1f - fall * 0.65f
                        }
                        cameraDistance = 14f * density.density
                    }) { AthleteCard(fc, Modifier.fillMaxSize(), interactive = false) }
            }
            Fighter(o.friend?.let { friendFace(it) } ?: o.face, oppCenter, false)
            Fighter(face, myCenter, true)
            // Les stats s'affrontent au centre, une à une ; elles s'effacent au coup final.
            val fade = 1f - (f / 0.4f).coerceIn(0f, 1f) * 0.75f
            Column(Modifier.align(Alignment.Center).padding(horizontal = 20.dp).graphicsLayer { alpha = fade }, verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                pairs.forEachIndexed { i, (s, a, b) ->
                    val k = ((t - INTRO - i * STEP) / FILL).coerceIn(0f, 1f)
                    val e = 1f - (1f - k) * (1f - k)
                    Box(Modifier.graphicsLayer { alpha = (k * 3f).coerceAtMost(1f); translationY = (1f - e) * 24f * density.density }) { DuelBar(s, a, b, e) }
                }
                val tk = ((t - statsEnd) / 0.4f).coerceIn(0f, 1f)
                Row(Modifier.padding(top = 6.dp).graphicsLayer { alpha = tk; val sc = 1.5f - 0.5f * tk; scaleX = sc; scaleY = sc }, verticalAlignment = Alignment.CenterVertically) {
                    Text("$myTotal", color = if (myTotal >= theirTotal) Red else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Black, fontSize = 34.sp)
                    Text(tr("TOTAL"), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 14.dp))
                    Text("$theirTotal", color = if (theirTotal >= myTotal) Blue else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Black, fontSize = 34.sp)
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                with(Vfx) {
                    if (f > 0f) {
                        val col = rarityColors(winArt.rarity)[1]
                        val draw = (f / 0.5f).coerceIn(0f, 1f); val gone = 1f - ((f - 1.5f) / 0.4f).coerceIn(0f, 1f)
                        magicCircle(winC, w * 0.6f, t * 1.8f, col, draw, alpha = 0.9f * gone)
                        els.forEachIndexed { k, e ->
                            aura(e, winC, w * 0.45f, t, draw * gone, 70)
                            // Le sort part de la gagnante et explose sur l'autre carte.
                            val p = ((f - 0.5f - k * 0.08f) / 0.35f).coerceIn(0f, 1f)
                            if (p in 0.001f..0.999f) projectile(e, winC, loseC, p, t, w * 0.34f)
                            impact(e, loseC, f - 0.85f - k * 0.12f, w * 0.75f)
                            impact(e, loseC, f - 1.0f - k * 0.12f, w * 0.5f)
                            pillar(Offset(loseC.x, loseC.y + cardH * 0.5f), w * 0.22f, h, e.mid.c(), ((f - 0.85f) / 0.15f).coerceIn(0f, 1f) * (1f - ((f - 1.4f) / 0.4f).coerceIn(0f, 1f)))
                        }
                    }
                }
            }
        }
        if (f > 0.3f && !showResult) {
            // Nom du coup final, puis le verdict.
            val k = ((f - 0.3f) / 0.25f).coerceIn(0f, 1f) * (1f - ((f - 1.1f) / 0.2f).coerceIn(0f, 1f))
            Text(els.joinToString(" + ") { tr(it.finisher).uppercase() } + " !", style = TextStyle(color = els.first().core.c(), fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                shadow = Shadow(els.first().outer.c(), Offset.Zero, 24f)), textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = k; val s = 1.6f - 0.6f * k; scaleX = s; scaleY = s })
        }
        if (f > 1.2f && !showResult) {
            val k = ((f - 1.2f) / 0.25f).coerceIn(0f, 1f)
            Text(if (iWin) tr("VICTOIRE") else tr("DÉFAITE"), style = TextStyle(brush = Brush.verticalGradient(listOf(Color.White, Gold, Color(0xFFFF9A3C))), fontSize = 64.sp,
                fontWeight = FontWeight.Black, shadow = Shadow(Color(0xFFFF9A3C), Offset.Zero, 30f)), modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = k; val s = 2.4f - 1.4f * k; scaleX = s; scaleY = s })
        }
        Text("@" + o.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp))
        if (!showResult) Text(tr("Touchez pour passer"), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 16.dp, bottom = 12.dp))
        if (showResult) DuelResult(o, pairs, myTotal, theirTotal, iWin, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun DuelResult(o: Opponent, pairs: List<Triple<Stat, Int, Int>>, myTotal: Int, theirTotal: Int, iWin: Boolean, modifier: Modifier) {
    val worst = pairs.maxByOrNull { it.third - it.second }
    val bestGap = pairs.minByOrNull { it.third - it.second }
    val line = when {
        worst != null && worst.third > worst.second -> "@${o.name} " + tr("te met") + " ${worst.third - worst.second} " + tr(if (worst.third - worst.second > 1) "points" else "point") + " " + tr(worst.first.at) + "."
        bestGap != null -> tr("Tu domines partout. Ton plus gros avantage") + " : " + tr(bestGap.first.label).lowercase() + " (+${bestGap.second - bestGap.third})."
        else -> ""
    }
    Column(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Ink, Ink))).navigationBarsPadding().padding(16.dp).padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (iWin) tr("Victoire !") else tr("Défaite… pour l'instant"), color = if (iWin) Gold else Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
        Text("$myTotal " + tr("contre") + " $theirTotal", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Text(line, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Text(tr("Stats avec le bonus des fonds. Touchez pour revenir."), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun DuelBar(s: Stat, a: Int, b: Int, k: Float) {
    // L'écart est amplifié : 10 points d'écart déplacent la jonction d'un quart de la barre.
    val left = (0.5f + (a - b) / 40f).coerceIn(0.08f, 0.92f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${(a * k).roundToInt()}", color = if (a >= b) Red else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.width(36.dp))
        Box(Modifier.weight(1f).height(14.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val mid = size.width / 2
                val split = mid + (size.width * left - mid) * k
                drawRoundRect(Color.White.copy(alpha = 0.06f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
                drawRoundRect(Brush.horizontalGradient(listOf(Red.copy(alpha = 0.7f), Red), 0f, split), size = androidx.compose.ui.geometry.Size(split, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
                drawRoundRect(Brush.horizontalGradient(listOf(Blue, Blue.copy(alpha = 0.7f)), split, size.width), topLeft = Offset(split, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width - split, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
                if (k > 0.98f && abs(a - b) > 0) drawCircle(Color.White, radius = size.height * 0.75f, center = Offset(split, size.height / 2))
            }
        }
        Text("${(b * k).roundToInt()}", color = if (b >= a) Blue else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
        Text(s.code, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(34.dp).padding(start = 6.dp))
    }
}
