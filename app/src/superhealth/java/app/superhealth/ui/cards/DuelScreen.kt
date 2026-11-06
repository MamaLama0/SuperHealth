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

private const val INTRO = 0.9f
/** Durée d'une stat : bras de fer ([TUG]) puis un temps d'arrêt sur le résultat. */
private const val STEP = 1.55f
private const val TUG = 1.15f
/** Allers-retours de la jonction pendant le bras de fer. */
private const val SWINGS = 5
private const val FINISH = 2.4f

/** Avancée du bras de fer de la stat [i] à l'instant [t], de 0 (pas commencée) à 1 (tranchée). */
private fun tugOf(t: Float, i: Int) = ((t - INTRO - i * STEP) / TUG).coerceIn(0f, 1f)
/** Instant où la stat [i] est tranchée. */
private fun settleAt(i: Int) = INTRO + i * STEP + TUG

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
    val statsEnd = INTRO + pairs.size * STEP
    val strike = statsEnd + 0.9f
    val total = strike + FINISH
    var t by remember { mutableFloatStateOf(0f) }
    var showResult by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        val fired = mutableSetOf<Int>()
        while (true) withFrameNanos { now ->
            t = (t + (now - last) / 1e9f).coerceAtMost(total + 0.5f); last = now
            if (t > 0.35f && fired.add(-1)) Rumble.thud(ctx)
            // Pendant le bras de fer, un grondement de petits coups qui enfle ; chaque aller-retour claque, de plus en plus
            // fort, puis la stat tranchée frappe d'autant plus que l'écart est grand.
            if (t < statsEnd) pairs.indices.forEach { i ->
                val u = tugOf(t, i)
                if (u > 0f && u < 1f) {
                    val beat = (u * TUG / 0.06f).toInt()
                    if (fired.add(100_000 + i * 1000 + beat)) Rumble.tick(ctx, 0.12f + 0.4f * u)
                }
                (0 until SWINGS).forEach { k -> if (u >= (k + 0.5f) / SWINGS && u < 1f && fired.add(1000 + i * 10 + k)) Rumble.clash(ctx, 0.5f + 0.5f * k / (SWINGS - 1)) }
                if (u >= 1f && fired.add(i)) {
                    val gap = abs(pairs[i].second - pairs[i].third)
                    when { gap >= 5 -> Rumble.impact(ctx); gap > 0 -> Rumble.slam(ctx); else -> Rumble.clash(ctx, 0.7f) }
                }
            }
            if (t > statsEnd + 0.15f && fired.add(100)) Rumble.reveal(ctx)
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
        // Secousse à l'impact du coup final, et une plus courte à chaque stat tranchée (selon l'écart).
        val justSettled = pairs.indices.lastOrNull { t >= settleAt(it) && t < settleAt(it) + 0.3f }
        val statShake = justSettled?.let { i -> val d = t - settleAt(i); sin(d * 110f) * (4f + 1.2f * abs(pairs[i].second - pairs[i].third)).coerceAtMost(14f) * density.density * (1f - d / 0.3f) } ?: 0f
        val shake = (if (f in 0.85f..1.45f) sin(f * 95f) * 22f * (1.45f - f) / 0.6f else 0f) + statShake
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
                        // Pendant le bras de fer, les cartes tremblent ; quand une stat tombe, sa gagnante charge et l'autre encaisse.
                        val i = ((t - INTRO) / STEP).toInt()
                        if (t in INTRO..statsEnd && i in pairs.indices) {
                            val u = tugOf(t, i)
                            if (u < 1f) translationX += sin(t * 70f) * 1.6f * density.density * u
                            val d = t - settleAt(i)
                            if (d in 0f..0.4f) {
                                val (_, a, b) = pairs[i]
                                val wonStat = if (meSide) a > b else b > a
                                val hit = sin(d / 0.4f * PI.toFloat())
                                if (wonStat) { translationY += hit * (if (meSide) -1f else 1f) * h * 0.035f; val sc = 1f + 0.06f * hit; scaleX = sc; scaleY = sc }
                                else if (a != b) { translationX += sin(d * 90f) * 9f * density.density * (1f - d / 0.4f); rotationZ += hit * (if (meSide) 3f else -3f) }
                            }
                        }
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
                // Manches gagnées, mises à jour à chaque stat tranchée.
                val settled = pairs.indices.filter { t >= settleAt(it) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                    Text("${settled.count { pairs[it].second > pairs[it].third }}", color = Red, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(tr("MANCHES"), color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 10.dp))
                    Text("${settled.count { pairs[it].third > pairs[it].second }}", color = Blue, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
                pairs.forEachIndexed { i, (s, a, b) ->
                    val appear = ((t - INTRO - i * STEP + 0.25f) / 0.25f).coerceIn(0f, 1f)
                    val u = tugOf(t, i)
                    val active = appear > 0f && t < settleAt(i) + 0.35f && t < statsEnd
                    val pop = if (t >= settleAt(i)) sin(((t - settleAt(i)) / 0.35f).coerceIn(0f, 1f) * PI.toFloat()) else 0f
                    Box(Modifier.graphicsLayer {
                        alpha = appear * (if (active || t >= statsEnd) 1f else 0.7f)
                        translationY = (1f - appear) * 24f * density.density
                        val sc = (if (active) 1.03f else 1f) + 0.04f * pop; scaleX = sc; scaleY = sc
                    }) { DuelBar(s, a, b, u, active, t - settleAt(i)) }
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
private fun DuelBar(s: Stat, a: Int, b: Int, u: Float, active: Boolean, since: Float) {
    // L'écart est amplifié : 10 points d'écart déplacent la jonction d'un quart de la barre.
    val left = (0.5f + (a - b) / 40f).coerceIn(0.08f, 0.92f)
    // Bras de fer : la jonction part d'abord vers le perdant de la stat, fait des allers-retours de moins en moins amples
    // et finit sur l'écart réel. Les chiffres montent avec elle.
    val k = u * u * (3f - 2f * u)
    val dir = if (a >= b) -1f else 1f
    val swing = 0.28f * (1f - u) * sin(u * SWINGS * PI.toFloat()) * dir
    val k2 = if (u >= 1f) 1f else (k + swing * 0.6f).coerceIn(0f, 1f)
    val done = u >= 1f
    // Juste après la stat tranchée : éclat, chiffre du gagnant qui gonfle, « +écart » qui s'envole.
    val burst = if (done && since in 0f..0.6f) since / 0.6f else -1f
    val winnerCol = if (a > b) Red else if (b > a) Blue else Color.White
    @Composable fun Score(v: Int, mine: Boolean) {
        val won = if (mine) a > b else b > a
        Box(Modifier.width(36.dp), contentAlignment = if (mine) Alignment.CenterStart else Alignment.CenterEnd) {
            Text("$v", color = if (!done || (if (mine) a >= b else b >= a)) (if (mine) Red else Blue) else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Black, fontSize = 18.sp,
                modifier = Modifier.graphicsLayer { if (won && burst >= 0f) { val sc = 1f + 0.45f * sin(burst * PI.toFloat()); scaleX = sc; scaleY = sc } })
            if (won && burst >= 0f) Text("+${abs(a - b)}", color = if (mine) Red else Blue, fontWeight = FontWeight.Black, fontSize = 14.sp,
                modifier = Modifier.graphicsLayer { translationY = -(16.dp.toPx() + burst * 28.dp.toPx()); alpha = 1f - burst })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Score((a * k2).roundToInt(), true)
        Box(Modifier.weight(1f).height(if (active) 18.dp else 14.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val mid = size.width / 2
                val split = (mid + (size.width * left - mid) * k + size.width * swing).coerceIn(size.height, size.width - size.height)
                val c = Offset(split, size.height / 2)
                val radius = androidx.compose.ui.geometry.CornerRadius(size.height)
                // Ligne en lumière derrière la barre active.
                if (active) drawRoundRect(Color.White.copy(alpha = 0.10f), topLeft = Offset(-12f, -size.height * 0.6f), size = Size(size.width + 24f, size.height * 2.2f), cornerRadius = radius, blendMode = BlendMode.Plus)
                drawRoundRect(Color.White.copy(alpha = 0.06f), cornerRadius = radius)
                drawRoundRect(Brush.horizontalGradient(listOf(Red.copy(alpha = 0.7f), Red), 0f, split), size = Size(split, size.height), cornerRadius = radius)
                drawRoundRect(Brush.horizontalGradient(listOf(Blue, Blue.copy(alpha = 0.7f)), split, size.width), topLeft = Offset(split, 0f),
                    size = Size(size.width - split, size.height), cornerRadius = radius)
                if (active && !done) {
                    // Étincelles qui jaillissent du point de contact, d'autant plus que le bras de fer s'emballe.
                    val heat = 0.5f + 0.5f * u
                    repeat(10) { n ->
                        val ang = n * 0.628f + u * 23f + sin(u * 61f + n) * 0.6f
                        val len = size.height * (0.8f + 1.6f * abs(sin(u * 37f + n * 1.7f))) * heat
                        val from = c + Offset(kotlin.math.cos(ang), sin(ang)) * size.height * 0.7f
                        drawLine(if (n % 2 == 0) Color.White else Gold, from, from + Offset(kotlin.math.cos(ang), sin(ang)) * len, strokeWidth = 2.2f, alpha = 0.85f, blendMode = BlendMode.Plus)
                    }
                    drawCircle(Color.White.copy(alpha = 0.35f), radius = size.height * (1.1f + 0.5f * abs(sin(u * 40f))), center = c, blendMode = BlendMode.Plus)
                    drawCircle(Color.White, radius = size.height * 0.55f, center = c)
                } else if (done && abs(a - b) > 0) drawCircle(Color.White, radius = size.height * 0.75f, center = c)
                if (burst >= 0f) {
                    // La moitié gagnante s'illumine, une onde et des éclats partent de la jonction.
                    val glow = (1f - burst) * 0.6f
                    if (a > b) drawRoundRect(Color.White.copy(alpha = glow), size = Size(split, size.height), cornerRadius = radius, blendMode = BlendMode.Plus)
                    else if (b > a) drawRoundRect(Color.White.copy(alpha = glow), topLeft = Offset(split, 0f), size = Size(size.width - split, size.height), cornerRadius = radius, blendMode = BlendMode.Plus)
                    drawCircle(winnerCol.copy(alpha = 1f - burst), radius = size.height * (0.8f + 4.5f * burst), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f * (1f - burst)))
                    repeat(16) { n ->
                        val ang = n * 0.3927f + 0.2f
                        val r = size.height * (0.6f + 5.5f * burst) * (0.7f + 0.3f * abs(sin(n * 2.3f)))
                        drawCircle(if (n % 3 == 0) Color.White else winnerCol, radius = 3.2f * (1f - burst) + 0.8f, center = c + Offset(kotlin.math.cos(ang), sin(ang) * 0.6f) * r, alpha = 1f - burst)
                    }
                }
            }
        }
        Score((b * (if (done) 1f else (k - swing * 0.6f).coerceIn(0f, 1f))).roundToInt(), false)
        Text(s.code, color = Color.White.copy(alpha = if (active) 1f else 0.7f), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(34.dp).padding(start = 6.dp))
    }
}
