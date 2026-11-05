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
        Text(tr("Trois manches : haut du corps, jambes, constance. Chaque carte frappe avec l'élément de son fond, bonus compris."), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
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


/** Trois manches : le haut du corps, les jambes, puis la constance (régularité et cardio). */
private enum class Arena(val label: String, val stats: List<Stat>) {
    HAUT("Haut du corps", listOf(Stat.PEC, Stat.DOS, Stat.BRA)),
    BAS("Jambes", listOf(Stat.JAM)),
    CONSTANCE("Constance", listOf(Stat.REG, Stat.CAR)),
}

/** Une manche : la moyenne des stats de l'arène (bonus du fond compris) ; l'attaquant frappe avec l'élément de sa carte. */
private data class Round(val arena: Arena, val mine: Double, val theirs: Double, val myElement: Element, val theirElement: Element) {
    val winner get() = if (abs(mine - theirs) < 0.05) 0 else mine.compareTo(theirs)
    val damage get() = if (winner == 0) 0 else (14 + 5 * abs(mine - theirs)).roundToInt().coerceAtMost(48)
    val element get() = if (winner >= 0) myElement else theirElement
    /** L'élément de l'attaquant renforce une stat de cette arène : coup d'affinité, plus spectaculaire. */
    val affinity get() = winner != 0 && element.stat in arena.stats
}

/** Score d'une manche : entier quand il l'est, sinon au dixième (« 84,3 »). */
private fun score(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(java.util.Locale.FRANCE, v)

/** Élément(s) d'une carte : ceux de son fond, sinon celui de sa meilleure stat (fond de palier). */
private fun elementsOf(art: CardDef, stats: AthleteStats): List<Element> =
    if (!art.neutral) art.elements else listOf(Element.of(Stat.families.maxByOrNull { stats[it] ?: 0 } ?: Stat.PEC)!!)

private const val INTRO = 0.7f
private const val ROUND = 1.5f
private const val FINISH = 1.8f

/**
 * Le duel, en trois manches courtes : les deux cartes s'avancent sur le décor de leurs biomes, puis à chaque manche
 * la meilleure lance l'élément de son fond (une mythique alterne ses deux biomes) : grand cercle, projectile, impact
 * plein écran. Le vainqueur achève. Le résultat ne dépend que des stats et des bonus de fond. Un appui passe au bilan.
 */
@Composable
private fun DuelScene(mine: AthleteStats, o: Opponent, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val face = myFace(mine)
    val myArt = face.background?.card ?: Catalog.defaultFor(mine.tier)
    val theirArt = o.bg?.card ?: Catalog.defaultFor(o.stats.tier)
    val mb = face.bonus; val ob = o.face.bonus
    val rounds = remember(mine, o, mb) {
        val myEl = elementsOf(myArt, mine); val thEl = elementsOf(theirArt, o.stats)
        Arena.entries.mapIndexedNotNull { i, a ->
            val ss = a.stats.filter { mine[it] != null && o.stats[it] != null }.ifEmpty { return@mapIndexedNotNull null }
            fun avg(v: List<Double>) = Math.round(v.average() * 10) / 10.0
            Round(a, avg(ss.map { mine[it]!! + (mb[it] ?: 0.0) }), avg(ss.map { o.stats[it]!! + (ob[it] ?: 0.0) }), myEl[i % myEl.size], thEl[i % thEl.size])
        }
    }
    var t by remember { mutableFloatStateOf(0f) }
    var showResult by remember { mutableStateOf(false) }
    val total = INTRO + rounds.size * ROUND + FINISH
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        val fired = mutableSetOf<Int>()
        while (true) withFrameNanos { now ->
            t = (t + (now - last) / 1e9f).coerceAtMost(total + 0.5f); last = now
            if (t > 0.35f && fired.add(-1)) Rumble.thud(ctx)
            rounds.forEachIndexed { i, r ->
                val s = INTRO + i * ROUND
                if (t > s + 0.2f && fired.add(i * 10)) Rumble.rise(ctx)
                if (t > s + 0.8f && fired.add(i * 10 + 1)) { if (r.winner != 0) Rumble.reveal(ctx) else Rumble.thud(ctx) }
            }
            if (t > total - FINISH + 0.9f && fired.add(999)) Rumble.reveal(ctx)
            if (t >= total && !showResult) showResult = true
        }
    }
    fun hp(meSide: Boolean): Float {
        var hp = 100f
        rounds.forEachIndexed { i, r ->
            val k = ((t - INTRO - i * ROUND - 0.8f) / 0.25f).coerceIn(0f, 1f)
            if ((r.winner == -1 && meSide) || (r.winner == 1 && !meSide)) hp -= r.damage * k
        }
        return hp.coerceAtLeast(0f)
    }
    val myHp = hp(true); val theirHp = hp(false)
    val myDealt = rounds.sumOf { if (it.winner == 1) it.damage else 0 }; val theirDealt = rounds.sumOf { if (it.winner == -1) it.damage else 0 }
    val iWin = myDealt > theirDealt || (myDealt == theirDealt && (mine.ovr10 ?: 0) >= (o.stats.ovr10 ?: 0))
    val finishT = t - INTRO - rounds.size * ROUND
    val round = ((t - INTRO) / ROUND).toInt().takeIf { t >= INTRO }
    val local = if (round != null) t - INTRO - round * ROUND else 0f
    val r = round?.let { rounds.getOrNull(it) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink).clickable(remember { MutableInteractionSource() }, null) { if (showResult) onBack() else t = total }) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val oppCenter = Offset(w * 0.66f, h * 0.28f); val myCenter = Offset(w * 0.34f, h * 0.64f)
        val cardW = w * 0.5f
        // Secousse de l'écran à chaque impact, plus forte pour un coup d'affinité et le coup final.
        val shake = when {
            r != null && r.winner != 0 && local in 0.8f..1.15f -> sin(local * 95f) * (if (r.affinity) 16f else 9f) * (1.15f - local) / 0.35f
            finishT in 0.9f..1.3f -> sin(finishT * 95f) * 20f * (1.3f - finishT) / 0.4f
            else -> 0f
        }
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = shake; translationY = shake * 0.5f }) {
            // Décor : le biome de l'adversaire en haut, le tien en bas, séparés par une diagonale.
            CardArt(theirArt, Finish.NORMALE, Modifier.fillMaxSize().drawWithContent {
                clipPath(Path().apply { moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h * 0.42f); lineTo(0f, h * 0.56f); close() }) { this@drawWithContent.drawContent() } }, time = { t }, holo = false)
            CardArt(myArt, Finish.NORMALE, Modifier.fillMaxSize().drawWithContent {
                clipPath(Path().apply { moveTo(0f, h * 0.56f); lineTo(w, h * 0.42f); lineTo(w, h); lineTo(0f, h); close() }) { this@drawWithContent.drawContent() } }, time = { t }, holo = false)
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.Black.copy(alpha = 0.55f))
                drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, h * 0.56f), Offset(w, h * 0.42f), 3f * density.density, blendMode = BlendMode.Plus)
                // Éclair de couleur de l'élément qui frappe.
                if (r != null && r.winner != 0) drawRect(r.element.mid.c().copy(alpha = (0.35f * (1f - abs(local - 0.85f) / 0.25f)).coerceIn(0f, 0.35f)), blendMode = BlendMode.Plus)
            }
            val loserFall = if (finishT > 1.1f) ((finishT - 1.1f) / 0.5f).coerceIn(0f, 1f) else 0f
            @Composable fun Fighter(f: CardFace, center: Offset, meSide: Boolean) {
                val lost = loserFall > 0f && (meSide != iWin)
                val attacking = r != null && ((r.winner == 1 && meSide) || (r.winner == -1 && !meSide)) && local in 0.2f..0.8f
                val hit = r != null && ((r.winner == -1 && meSide) || (r.winner == 1 && !meSide)) && local in 0.8f..1.1f
                Box(Modifier.offset { androidx.compose.ui.unit.IntOffset((center.x - cardW / 2).toInt(), (center.y - cardW / 0.714f / 2).toInt()) }
                    .size(with(density) { cardW.toDp() }, with(density) { (cardW / 0.714f).toDp() })
                    .graphicsLayer {
                        val enter = (t / 0.45f).coerceIn(0f, 1f); val ease = 1f - (1f - enter) * (1f - enter)
                        translationX = (1 - ease) * (if (meSide) -w else w)
                        rotationZ = (if (meSide) -5f else 5f) + if (lost) loserFall * (if (meSide) -24f else 24f) else 0f
                        // L'attaquant s'élance vers l'autre carte, la cible recule sous le choc.
                        val lunge = if (attacking) sin(((local - 0.2f) / 0.6f) * PI.toFloat()) else 0f
                        translationX += lunge * (if (meSide) 1f else -1f) * w * 0.06f; translationY += lunge * (if (meSide) -1f else 1f) * h * 0.03f
                        if (hit) { val k = 1f - (local - 0.8f) / 0.3f; translationX += (if (meSide) -1f else 1f) * k * w * 0.04f; rotationY = (if (meSide) 1f else -1f) * k * 18f }
                        val s = 1f + if (attacking) 0.06f else 0f; scaleX = s; scaleY = s
                        translationY += if (lost) loserFall * 90f * density.density else 0f; alpha = if (lost) 1f - loserFall * 0.6f else 1f
                        cameraDistance = 14f * density.density
                    }) { AthleteCard(f, Modifier.fillMaxSize(), interactive = false) }
            }
            Fighter(o.friend?.let { friendFace(it) } ?: o.face, oppCenter, false)
            Fighter(face, myCenter, true)
            Canvas(Modifier.fillMaxSize()) {
                with(Vfx) {
                    if (r != null) {
                        val e = r.element; val col = e.mid.c()
                        val from = if (r.winner >= 0) myCenter else oppCenter; val to = if (r.winner >= 0) oppCenter else myCenter
                        val big = if (r.affinity) 1.35f else 1f
                        val draw = ((local - 0.1f) / 0.3f).coerceIn(0f, 1f) * (1f - ((local - 1.1f) / 0.3f).coerceIn(0f, 1f))
                        magicCircle(from, w * 0.42f * big, t * 1.5f, col, draw, alpha = draw * 0.9f)
                        aura(e, from, w * 0.3f * big, t, draw, 50)
                        if (r.winner == 0) {
                            // Égalité : les deux sorts se percutent au milieu.
                            val mid = Offset((myCenter.x + oppCenter.x) / 2, (myCenter.y + oppCenter.y) / 2)
                            val k = ((local - 0.45f) / 0.35f).coerceIn(0f, 1f)
                            if (k in 0.001f..0.999f) { projectile(r.myElement, myCenter, mid, k, t, w * 0.2f); projectile(r.theirElement, oppCenter, mid, k, t, w * 0.2f) }
                            impact(r.myElement, mid, local - 0.8f, w * 0.3f); impact(r.theirElement, mid, local - 0.85f, w * 0.3f)
                        } else {
                            val k = ((local - 0.45f) / 0.35f).coerceIn(0f, 1f)
                            if (k in 0.001f..0.999f) projectile(e, from, to, k, t, w * 0.24f * big)
                            impact(e, to, local - 0.8f, w * 0.42f * big)
                            if (r.affinity) impact(e, to, local - 0.95f, w * 0.6f)
                        }
                    }
                    if (finishT > 0f) {
                        val winC = if (iWin) myCenter else oppCenter; val loseC = if (iWin) oppCenter else myCenter
                        val els = elementsOf(if (iWin) myArt else theirArt, if (iWin) mine else o.stats)
                        val col = rarityColors((if (iWin) myArt else theirArt).rarity)[1]
                        val draw = (finishT / 0.6f).coerceIn(0f, 1f)
                        magicCircle(winC, w * 0.55f, t * 1.8f, col, draw, alpha = 0.9f * (1f - ((finishT - 1.5f) / 0.3f).coerceIn(0f, 1f)))
                        els.forEachIndexed { k, e ->
                            aura(e, winC, w * 0.4f, t, draw, 70)
                            pillar(Offset(loseC.x, loseC.y + cardW * 0.7f), w * 0.16f, h, e.mid.c(), ((finishT - 0.7f) / 0.2f).coerceIn(0f, 1f) * (1f - ((finishT - 1.4f) / 0.4f).coerceIn(0f, 1f)))
                            impact(e, loseC, finishT - 0.9f - k * 0.2f, w * 0.65f)
                        }
                    }
                }
            }
        }
        HpBar("@" + o.name, theirHp, Color(0xFF6E7BFF), Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 14.dp).fillMaxWidth(0.42f))
        HpBar(tr("Toi"), myHp, Color(0xFFFF4F5E), Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 22.dp).fillMaxWidth(0.42f))
        // Bandeau de la manche : arène, valeurs et élément lancé, en grand.
        if (r != null) {
            val a = (local / 0.15f).coerceIn(0f, 1f) * (1f - ((local - 1.25f) / 0.2f).coerceIn(0f, 1f))
            Column(Modifier.align(Alignment.Center).offset(y = (-8).dp).graphicsLayer { alpha = a; val s = 1.4f - 0.4f * a; scaleX = s; scaleY = s }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("Manche") + " ${round!! + 1} · " + tr(r.arena.label).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 2.sp,
                    style = TextStyle(shadow = Shadow(Color.Black, Offset.Zero, 12f)))
                Row(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(r.element.outer.c().copy(alpha = 0.95f), Color.Black.copy(alpha = 0.75f), r.element.outer.c().copy(alpha = 0.95f))))
                    .padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(score(r.mine), color = if (r.winner >= 0) Color.White else Color.White.copy(alpha = 0.45f), fontWeight = FontWeight.Black, fontSize = 30.sp)
                    Canvas(Modifier.padding(horizontal = 12.dp).size(30.dp)) { with(Vfx) { emblem(r.element, center, size.minDimension * 0.45f, r.element.core.c()) } }
                    Text(score(r.theirs), color = if (r.winner <= 0) Color.White else Color.White.copy(alpha = 0.45f), fontWeight = FontWeight.Black, fontSize = 30.sp)
                }
                Text(if (r.winner == 0) tr("ÉGALITÉ") else tr(r.element.magic).uppercase() + if (r.affinity) " · " + tr("AFFINITÉ !") else "",
                    color = r.element.core.c(), fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 6.dp),
                    style = TextStyle(shadow = Shadow(r.element.outer.c(), Offset.Zero, 16f)))
            }
            if (r.winner != 0 && local > 0.8f) {
                val k = ((local - 0.8f) / 0.55f).coerceIn(0f, 1f)
                val at = if (r.winner == 1) oppCenter else myCenter
                Text("-${r.damage}", style = TextStyle(color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Black, shadow = Shadow(r.element.outer.c(), Offset.Zero, 24f)),
                    modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(at.x.toInt() - 90, (at.y - k * 180f).toInt()) }.graphicsLayer { alpha = 1f - k * k; val s = 1.8f - 0.8f * (k * 3).coerceAtMost(1f); scaleX = s; scaleY = s })
            }
        }
        if (finishT > 1.1f && !showResult) {
            val k = ((finishT - 1.1f) / 0.25f).coerceIn(0f, 1f)
            val ko = (if (iWin) theirHp else myHp) <= 0.5f
            Text(if (ko) "K.O." else if (iWin) tr("VICTOIRE") else tr("DÉFAITE"), style = TextStyle(brush = Brush.verticalGradient(listOf(Color.White, Gold, Color(0xFFFF9A3C))), fontSize = 72.sp,
                fontWeight = FontWeight.Black, shadow = Shadow(Color(0xFFFF9A3C), Offset.Zero, 30f)), modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = k; val s = 2.4f - 1.4f * k; scaleX = s; scaleY = s })
        }
        if (!showResult) Text(tr("Touchez pour passer"), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 16.dp, bottom = 30.dp))
        if (showResult) DuelResult(mine, o, mb, ob, rounds, iWin, myHp, theirHp, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun HpBar(name: String, hp: Float, color: Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${hp.toInt()} PV", color = color, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth((hp / 100f).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(if (hp < 30f) Color(0xFFFF4F5E) else color.copy(alpha = 0.7f), if (hp < 30f) Color(0xFFFF9A3C) else color))))
        }
    }
}

@Composable
private fun DuelResult(mine: AthleteStats, o: Opponent, mb: Map<Stat, Double>, ob: Map<Stat, Double>, rounds: List<Round>, iWin: Boolean, myHp: Float, theirHp: Float, modifier: Modifier) {
    val pairs = Stat.entries.mapNotNull { s -> val a = mine[s] ?: return@mapNotNull null; val b = o.stats[s] ?: return@mapNotNull null; Triple(s, a, b) }
    val worst = pairs.maxByOrNull { it.third - it.second }
    val bestGap = pairs.minByOrNull { it.third - it.second }
    val line = when {
        worst != null && worst.third > worst.second -> "@${o.name} " + tr("te met") + " ${worst.third - worst.second} " + tr(if (worst.third - worst.second > 1) "points" else "point") + " " + tr(worst.first.at) + "."
        bestGap != null -> tr("Tu domines partout. Ton plus gros avantage") + " : " + tr(bestGap.first.label).lowercase() + " (+${bestGap.second - bestGap.third})."
        else -> ""
    }
    val wins = rounds.count { it.winner == 1 }; val losses = rounds.count { it.winner == -1 }
    Column(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Ink, Ink))).navigationBarsPadding().padding(16.dp).padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (iWin) tr("Victoire !") else tr("Défaite… pour l'instant"), color = if (iWin) Gold else Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
        Text("$wins – $losses " + tr("manches") + " · ${myHp.toInt()} " + tr("contre") + " ${theirHp.toInt()} PV", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Text(line, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { pairs.forEach { (s, a, b) -> DuelBar(s, a, b, 1f) } }
        Text(tr("Stats brutes ; les manches comptent aussi le bonus des fonds. Touchez pour revenir."), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun DuelBar(s: Stat, a: Int, b: Int, k: Float) {
    // L'écart est amplifié : 10 points d'écart déplacent la jonction d'un quart de la barre.
    val left = (0.5f + (a - b) / 40f).coerceIn(0.08f, 0.92f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$a", color = if (a >= b) Red else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.width(36.dp))
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
        Text("$b", color = if (b >= a) Blue else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
        Text(s.code, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(34.dp).padding(start = 6.dp))
    }
}
