package app.superhealth.ui.cards

import app.superhealth.platform.*

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import app.superhealth.cards.*
import app.superhealth.ui.go
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.sin

private const val TEAR_LINE = 0.13f

/** Scène du biome à pleine richesse, pour la pochette et le décor plein écran. */
internal fun Booster.art() = CardDef("_pack_$name", 0, label, Rarity.RARE, element, Catalog.palettes[element]!![0], 2.7f + ordinal * 3.1f, Catalog.NATURE, Look(1 or 2 or 4, -0.1f, 1f))
private val Entries = Booster.entries

/** Décor plein écran : la scène du biome, assombrie, avec ses particules. */
@Composable
internal fun BoxScope.BiomeBackdrop(booster: Booster, time: () -> Float, dim: Float = 0.6f) {
    Crossfade(booster, Modifier.matchParentSize(), animationSpec = tween(450), label = "biome") { b ->
        Box(Modifier.fillMaxSize()) {
            // Le décor est calculé à basse résolution (4 à 9 fois moins de pixels), l'assombrissement masque le flou.
            CardArt(b.art(), Finish.NORMALE, Modifier.fillMaxSize(), time = time, holo = false, resolution = CardPerf.backdropScale)
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = dim * 0.6f), Color.Black.copy(alpha = dim * 0.9f), Color.Black.copy(alpha = (dim + 0.3f).coerceAtMost(1f)))))
            }
        }
    }
}

@Composable
fun PackShopScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val state by CardStore.state.collectAsState()
    // Un pack payé mais pas encore révélé reprend là où on l'avait laissé.
    var opening by remember { mutableStateOf<CardStore.Opening?>(run { CardStore.load(ctx); CardStore.state.value.pending?.opening() }) }
    LaunchedEffect(Unit) { CardStore.load(ctx); runCatching { CardStore.refresh(ctx) } }
    val pager = rememberPagerState(initialPage = state.booster.ordinal) { Entries.size }
    val booster = Entries[pager.currentPage]
    val haptics = app.superhealth.ui.rememberHaptics()
    LaunchedEffect(pager.currentPage) { haptics.tick() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Le tirage se fait sur le serveur (ou en local en mode démo) ; l'animation commence quand il a répondu.
    fun open(again: Boolean = false) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            runCatching { CardStore.open(ctx, booster) }.onSuccess { opening = it }.onFailure { error = it.message; if (again) opening = null }
            busy = false
        }
    }
    opening?.let { o ->
        key(o) { PackOpening(o, onDone = { opening = null }, onAgain = { open(again = true) }, onAlbum = { opening = null; nav.go("cards/album") },
            onShare = { g -> CardShare.open(g.pull, g.level, CardShare.Event.PULL); opening = null; nav.go("feed") }) }
        return
    }
    val tilt by rememberTilt()
    val time by rememberCardTime()
    CardScaffold("Packs", { nav.popBackStack() }, booster.element.outer.c(), action = {
        IconButton({ nav.go("cards/rates") }) { Icon(Icons.Rounded.Info, tr("Taux"), tint = Color.White.copy(alpha = 0.7f)) }
    }, backdrop = { BiomeBackdrop(booster, { time }) }) {
        Wallet(state)
        val float by rememberInfiniteTransition(label = "pf").animateFloat(-8f, 8f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "f")
        // Le carrousel coupe ce qui dépasse de ses bords : le halo des packs (glowReach) s'éteint avant.
        HorizontalPager(pager, Modifier.padding(top = 10.dp).fillMaxWidth().height(470.dp), contentPadding = PaddingValues(horizontal = 50.dp), pageSpacing = 0.dp) { page ->
            val off = (pager.currentPage - page + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PackVisual(Entries[page], Modifier.fillMaxHeight(0.95f).graphicsLayer {
                    val s = 1f - 0.18f * off; scaleX = s; scaleY = s; alpha = 1f - 0.45f * off
                    translationY = float * density * (1f - off); rotationY = tilt.x * 14f * (1f - off) + (page - pager.currentPage - pager.currentPageOffsetFraction) * -18f
                    rotationX = -tilt.y * 10f * (1f - off); cameraDistance = 14f * density
                }, { tilt }, { if (page == pager.currentPage) time else 0f }, glow = if (state.nextGold) Gold else Entries[page].element.mid.c(), glowBoost = 0.2f * (1f - off), gold = state.nextGold, glowReach = 0.46f)
            }
        }
        val e = booster.element
        Text(tr("Booster") + " " + tr(booster.label), color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Text(tr(e.tagline), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            val content = Catalog.ofBooster(booster)
            InfoPill("${content.count { state.owns(it) }}/${content.size} " + tr("fonds"), Color.White)
            InfoPill(tr("Bonus") + " " + e.stat.code, e.mid.c())
            InfoPill(if (state.nextGold) tr("Pack doré · chances ×3") else tr("3 à 4 cartes"), if (state.nextGold) Gold else Color.White)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            Entries.forEachIndexed { i, b -> Box(Modifier.padding(4.dp).size(if (i == pager.currentPage) 22.dp else 8.dp, 8.dp).clip(RoundedCornerShape(50)).background(if (i == pager.currentPage) b.element.mid.c() else Color.White.copy(alpha = 0.25f))) }
        }
        // Un seul bouton : il ouvre d'abord un pack offert, sinon dépense des éclats.
        val gift = state.packsAvailable > 0
        GlowButton(when { busy -> tr("Ouverture…"); gift -> tr("Ouvrir · pack offert"); else -> trf("Ouvrir · %1\$s éclats", CardStore.PACK_PRICE) }, Modifier.fillMaxWidth().padding(top = 14.dp),
            listOf(e.outer.c(), e.mid.c()), enabled = !busy && CardStore.canOpen(), badge = state.packsAvailable.takeIf { it > 0 }?.toString()) { open() }
        error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
        SectionTitle("Pack doré")
        GoldProgress(state)
        SectionTitle("Charte des raretés et effets")
        EffectChart(state.nextGold)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun InfoPill(text: String, color: Color) = Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 10.dp, vertical = 5.dp))

/** Compteur du pack doré : dix crans, le dixième pack ouvert triple les chances de rare, d'épique et de duo. */
@Composable
internal fun GoldProgress(state: CardStore.State) {
    val done = PackEngine.GOLD_EVERY - state.untilGold
    val shine by rememberInfiniteTransition(label = "gold").animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "g")
    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(InkCard.copy(alpha = 0.85f)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.nextGold) tr("Le prochain pack est doré !") else tr("Pack doré dans") + " ${state.untilGold} " + tr(if (state.untilGold > 1) "packs" else "pack"),
                color = if (state.nextGold) Gold else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(tr("chances ×3"), color = Gold.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until PackEngine.GOLD_EVERY) {
                val last = i == PackEngine.GOLD_EVERY - 1; val filled = i < done || (last && state.nextGold)
                Box(Modifier.weight(if (last) 1.6f else 1f).height(if (last) 12.dp else 8.dp).align(Alignment.CenterVertically).clip(RoundedCornerShape(50)).background(when {
                    last -> Brush.horizontalGradient(listOf(Color(0xFFB8860B), Gold.copy(alpha = if (filled) 1f else 0.35f + 0.3f * shine), Color(0xFFB8860B)))
                    filled -> Brush.horizontalGradient(listOf(Gold.copy(alpha = 0.7f), Gold))
                    else -> Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f)))
                }))
            }
        }
        Text(tr("Un pack sur dix triple les chances de rare, d'épique, de duo, d'animée et de chromatique."), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Pourcentage à la française, avec assez de décimales pour les taux faibles : « 53 % », « 1,6 % », « 0,31 % ». */
internal fun percent(p: Double): String {
    val v = p * 100
    val d = when { v >= 10 -> 0; v >= 1 -> 1; v >= 0.1 -> 2; else -> 3 }
    return "%.${d}f %%".format(Locale.FRANCE, v)
}

private val ChromaColor = Color(0xFFFF8AD8)

/**
 * Charte des tirages : la part de chaque rareté parmi les cartes, puis la chance de chaque effet, en pack normal et en
 * pack doré. Calculée à partir des tables du tirage ([PackEngine]) : ce qui est affiché est exactement ce qui sort.
 */
@Composable
internal fun EffectChart(highlightGold: Boolean = false) {
    data class Line(val label: String, val color: Color, val p: (Boolean) -> Double, val note: String? = null)
    val sections = listOf(
        "Rareté de chaque carte" to Rarity.entries.map { r ->
            Line(r.symbol + " " + tr(r.label), r.color.c(), { PackEngine.share(r, it) }, tr("bonus") + " " + Bonus.label(Catalog.cards.first { c -> c.rarity == r && !c.duo }, 1).substringBefore(" %").removePrefix("+") + " %")
        },
        "Effets, sur chaque carte" to listOf(
            Line("✦ " + tr("Brillante"), Color.White, { PackEngine.effectShare(Finish.BRILLANTE, it) }, tr("reflet holographique · bonus × 1,1")),
            Line("✦✦ " + tr("Animée"), Color(0xFF9AE6FF), { PackEngine.effectShare(Finish.ANIMEE, it) }, tr("fond en mouvement · bonus × 1,25")),
            Line("✺ " + tr("Chromatique"), ChromaColor, { PackEngine.effectShare(Finish.CHROMATIQUE, it) }, tr("toute la scène irisée · bonus × 1,5")),
            Line("◈ " + tr("Duo"), Gold, { PackEngine.duoOdds(it) }, tr("deux biomes · bonus sur deux stats"))),
    )
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(InkCard.copy(alpha = 0.9f)).padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Spacer(Modifier.weight(1f))
            Text(tr("Normal"), color = Color.White.copy(alpha = if (highlightGold) 0.4f else 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(84.dp))
            Text(tr("Doré"), color = Gold.copy(alpha = if (highlightGold) 1f else 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(84.dp))
        }
        sections.forEach { (title, lines) ->
            Text(tr(title).uppercase(), color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp))
            lines.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.label, color = l.color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        l.note?.let { Text(it, color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp) }
                    }
                    Text(percent(l.p(false)), color = Color.White.copy(alpha = if (highlightGold) 0.55f else 1f), fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.width(84.dp))
                    Text(percent(l.p(true)), color = if (highlightGold) Gold else Gold.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(84.dp))
                }
            }
        }
    }
}

/** Pochette du booster. [tear] de 0 à 1 arrache la bande du haut ; [cut] trace la ligne incandescente sous le doigt. */
@Composable
fun PackVisual(booster: Booster, modifier: Modifier, tilt: () -> Offset, time: () -> Float, glow: Color? = null, tear: Float = 0f, glowBoost: Float = 0f, cut: Float = 0f, gold: Boolean = false, glowReach: Float = 0.7f) {
    val density = LocalDensity.current
    Box(modifier.aspectRatio(0.62f).drawBehind {
        if (glow != null) drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = (0.3f + glowBoost * 0.5f + 0.08f * sin(time() * 3f)).coerceIn(0f, 1f)), Color.Transparent)),
            radius = size.maxDimension * (glowReach + glowBoost * 0.3f))
    }) {
        Box(Modifier.fillMaxSize().drawWithContent { clipRect(top = size.height * TEAR_LINE) { this@drawWithContent.drawContent() } }) { PackFace(booster, tilt, time, gold) }
        Box(Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = TransformOrigin(0f, TEAR_LINE)
            translationX = tear * size.width * 1.25f; translationY = -tear * size.height * 0.22f; rotationZ = tear * 32f; alpha = 1f - tear * 0.5f
        }.drawWithContent { clipRect(bottom = size.height * TEAR_LINE) { this@drawWithContent.drawContent() } }) { PackFace(booster, tilt, time, gold) }
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * TEAR_LINE
            if (tear < 0.02f && cut < 0.01f) drawLine(Color.White.copy(alpha = 0.6f), Offset(size.width * 0.05f, y), Offset(size.width * 0.95f, y), strokeWidth = with(density) { 1.2.dp.toPx() },
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), -time() * 40f))
            if (cut > 0f && tear < 1f) {
                val x = size.width * cut
                drawLine(booster.element.mid.c(), Offset(0f, y), Offset(x, y), strokeWidth = with(density) { 6.dp.toPx() }, alpha = 0.6f, blendMode = BlendMode.Plus)
                drawLine(Color.White, Offset(0f, y), Offset(x, y), strokeWidth = with(density) { 2.dp.toPx() }, blendMode = BlendMode.Plus)
                with(Vfx) { aura(booster.element, Offset(x, y), size.width * 0.12f, time(), 1f, 18) }
            }
        }
    }
}

/** Face de la pochette : la scène du biome en pleine page, sertie de bandes métalliques à sa couleur. */
@Composable
private fun PackFace(booster: Booster, tilt: () -> Offset, time: () -> Float, gold: Boolean = false) {
    val e = booster.element
    // Pack doré : bandes, médaillon et nom passent à l'or.
    val core = if (gold) Color(0xFFFFF4C8) else e.core.c(); val mid = if (gold) Gold else e.mid.c(); val outer = if (gold) Color(0xFF8A5A00) else e.outer.c()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val u = maxWidth / 100
        val su = with(LocalDensity.current) { u.toSp() }
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(5))) {
            CardArt(booster.art(), Finish.BRILLANTE, Modifier.fillMaxSize(), tilt, time)
            Canvas(Modifier.fillMaxSize()) {
                val step = size.width / 14
                for (i in -14..28) drawLine(Color.White.copy(alpha = 0.04f), Offset(i * step, 0f), Offset(i * step - size.height * 0.5f, size.height), strokeWidth = step * 0.12f)
                drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.3f to Color.Transparent, 0.55f to Color.Transparent, 0.8f to Color.Black.copy(alpha = 0.6f), 1f to Color.Black.copy(alpha = 0.8f)))
                // Médaillon de l'élément, sous le nom.
                val c = Offset(size.width / 2, size.height * 0.875f); val r = size.width * 0.075f
                drawCircle(Brush.radialGradient(listOf(mid, outer)), r, c)
                drawCircle(Brush.sweepGradient(listOf(Color.White, core, outer, Color.White), c), r, c, style = Stroke(size.width * 0.012f))
                with(Vfx) { magicCircle(c, r * 1.5f, time(), mid, 1f, alpha = 0.7f); emblem(e, c, r * 0.5f, Color.White) }
                for (top in listOf(true, false)) {
                    val h = size.height * 0.06f; val y0 = if (top) 0f else size.height - h
                    drawRect(Brush.verticalGradient(listOf(core, outer, mid), y0, y0 + h), Offset(0f, y0), Size(size.width, h))
                    val teeth = 26; val tw = size.width / teeth
                    val edge = if (top) 0f else size.height
                    val path = Path().apply { moveTo(0f, edge)
                        for (i in 0 until teeth) { lineTo(i * tw + tw / 2, if (top) h * 0.35f else edge - h * 0.35f); lineTo((i + 1) * tw, edge) }; close() }
                    drawPath(path, Color.Black.copy(alpha = 0.35f))
                }
                val gx = size.width * (0.5f + tilt().x * 0.6f)
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent), Offset(gx - size.width * 0.3f, 0f), Offset(gx + size.width * 0.3f, size.height * 0.4f)), blendMode = BlendMode.Plus)
                if (gold) {
                    drawRect(Brush.verticalGradient(listOf(Gold.copy(alpha = 0.22f), Color.Transparent, Gold.copy(alpha = 0.3f))), blendMode = BlendMode.Plus)
                    val sx = (time() * 0.3f % 1f) * size.width * 2f - size.width * 0.5f
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color(0xFFFFF4C8).copy(alpha = 0.35f), Color.Transparent), Offset(sx - size.width * 0.15f, 0f), Offset(sx + size.width * 0.15f, size.height)), blendMode = BlendMode.Plus)
                }
            }
            Column(Modifier.fillMaxSize().padding(top = u * 15, bottom = u * 33), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SUPERHEALTH · NATURE", style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Black, fontSize = su * 4f, letterSpacing = su * 1.2f, shadow = Shadow(Color.Black, Offset(0f, 2f), 8f)))
                Spacer(Modifier.weight(1f))
                val name = tr(booster.label).uppercase()
                if (gold) Text(tr("PACK DORÉ"), style = TextStyle(color = Color(0xFF3A2400), fontWeight = FontWeight.Black, fontSize = su * 4.2f, letterSpacing = su * 1f),
                    modifier = Modifier.padding(bottom = u * 1.5f).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFFB8860B), Gold, Color(0xFFFFF4C8), Gold))).padding(horizontal = u * 4, vertical = u * 0.8f))
                Text(name, style = TextStyle(brush = Brush.verticalGradient(listOf(Color.White, core, mid)), fontWeight = FontWeight.Black,
                    fontSize = su * (100f / name.length).coerceIn(10f, 15f), letterSpacing = su * 0.3f, shadow = Shadow(Color.Black, Offset(0f, 4f), 16f)))
                Text(if (gold) tr("CHANCES ×3") + " · " + tr("BONUS") + " " + e.stat.code else "3–4 " + tr("CARTES") + " · " + tr("BONUS") + " " + e.stat.code, style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, fontSize = su * 3.8f,
                    letterSpacing = su * 0.8f, shadow = Shadow(Color.Black, Offset(0f, 2f), 8f)), modifier = Modifier.padding(top = u * 1.5f))
            }
        }
    }
}

private enum class Stage { TEAR, FAN, SUMMARY }

@Composable
private fun PackOpening(o: CardStore.Opening, onDone: () -> Unit, onAgain: () -> Unit, onAlbum: () -> Unit, onShare: (CardStore.Gain) -> Unit) {
    val ctx = LocalContext.current
    var stage by remember { mutableStateOf(Stage.TEAR) }
    val pulls = o.result.pulls
    val best = pulls.maxOf { it.card.rarity }
    val tiltS = rememberTilt()
    val timeS = rememberCardTime()
    Box(Modifier.fillMaxSize().background(Ink)) {
        BiomeBackdrop(o.booster, { timeS.value }, dim = if (stage == Stage.SUMMARY) 0.8f else 0.62f)
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            when (stage) {
                Stage.TEAR -> Tear(o.booster, best, o.result.prism, o.result.gold, { tiltS.value }, { timeS.value }) { stage = Stage.FAN }
                Stage.FAN -> Fan(o, { tiltS.value }, { timeS.value }) { stage = Stage.SUMMARY }
                Stage.SUMMARY -> { LaunchedEffect(Unit) { CardStore.seen(ctx) }; Summary(o, onDone, onAgain, onAlbum, onShare) }
            }
        }
    }
    LaunchedEffect(Unit) { if (best.big) { delay(250); Rumble.rise(ctx) } }
}

@Composable
private fun BoxScope.Tear(booster: Booster, best: Rarity, prism: Boolean, gold: Boolean, tilt: () -> Offset, time: () -> Float, onOpened: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val tear = remember { Animatable(0f) }
    val slit = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    var lastTick by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val shake by rememberInfiniteTransition(label = "shake").animateFloat(-1f, 1f, infiniteRepeatable(tween(80), RepeatMode.Reverse), label = "s")
    val announce = best >= Rarity.RARE || prism
    val beam = if (announce) rarityColors(best)[1] else Color.White
    val dropIn = remember { Animatable(1f) }
    LaunchedEffect(Unit) { dropIn.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 120f)) }
    fun finish() = scope.launch {
        if (done) return@launch
        done = true
        progress.animateTo(1f, tween(120)); Rumble.thud(ctx)
        launch { tear.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
        slit.animateTo(1f, tween(650)); if (announce) Rumble.reveal(ctx)
        exit.animateTo(1f, tween(340, easing = FastOutLinearInEasing)); onOpened()
    }
    Column(Modifier.fillMaxSize().pointerInput(Unit) {
        detectDragGestures(onDragEnd = { scope.launch { if (progress.value > 0.55f) finish() else if (!done) progress.animateTo(0f, spring()) } }) { change, drag ->
            change.consume()
            if (done) return@detectDragGestures
            scope.launch { progress.snapTo((progress.value + drag.x / (size.width * 0.7f)).coerceIn(0f, 1f)) }
            val step = (progress.value * 12).toInt()
            if (step > lastTick) { lastTick = step; Rumble.tick(ctx, 0.2f + progress.value * 0.6f) }
            if (progress.value >= 0.98f) finish()
        }
    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (prism) Text(tr("PACK PRISME"), style = TextStyle(brush = Brush.horizontalGradient(rarityColors(Rarity.EPIQUE)), fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = 4.sp))
        else if (gold) Text(tr("PACK DORÉ"), style = TextStyle(brush = Brush.horizontalGradient(listOf(Color(0xFFB8860B), Gold, Color(0xFFFFF4C8), Gold)), fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = 4.sp,
            shadow = Shadow(Gold, Offset.Zero, 24f)), modifier = Modifier.padding(bottom = 10.dp))
        // Le pack reste immobile tant que le doigt suit la ligne ; il ne tremble qu'une fois ouvert.
        val shakeAmp = if (announce) 3f * slit.value * (1 - exit.value) else 0f
        Box(Modifier.fillMaxWidth(0.82f), contentAlignment = Alignment.Center) {
            PackVisual(booster, Modifier.fillMaxWidth().graphicsLayer {
                translationY = dropIn.value * size.height * 1.2f + exit.value * size.height * 1.3f
                rotationZ = shake * shakeAmp
            }, { Offset.Zero }, time, glow = if (announce) beam else if (gold) Gold else booster.element.mid.c(), tear = tear.value, glowBoost = if (announce) 0.6f else 0.1f, cut = progress.value, gold = gold)
            if (slit.value > 0f) Canvas(Modifier.matchParentSize()) {
                val y = size.height * TEAR_LINE + exit.value * size.height * 1.3f
                val k = slit.value * (1 - exit.value)
                for (i in 0 until 7) {
                    val spread = (i - 3) * size.width * 0.16f
                    val p = Path().apply { moveTo(size.width * 0.15f + i * size.width * 0.1f, y); lineTo(size.width / 2 + spread * 2.4f - size.width * 0.06f, y - size.height * 1.2f)
                        lineTo(size.width / 2 + spread * 2.4f + size.width * 0.06f, y - size.height * 1.2f); close() }
                    drawPath(p, Brush.verticalGradient(listOf(beam.copy(alpha = 0.6f * k), Color.Transparent), y, y - size.height * 1.2f), blendMode = BlendMode.Plus)
                }
                drawLine(Color.White.copy(alpha = k), Offset(0f, y), Offset(size.width, y), strokeWidth = 8f, blendMode = BlendMode.Plus)
                with(Vfx) { aura(booster.element, Offset(size.width / 2, y), size.width * 0.6f, time(), k, 40) }
            }
        }
        Text(tr("Glisse le long de la ligne pour ouvrir"), color = Color.White.copy(alpha = if (progress.value < 0.05f) 0.8f else 0f), fontSize = 14.sp, modifier = Modifier.padding(top = 34.dp))
    }
}

/**
 * Les cartes sortent en pile, en plein écran, la plus rare tout au fond. On glisse la carte du dessus pour passer à
 * la suivante (elle disparaît) ; un appui la retourne et la montre en fond de ta carte Athlète, avec tes stats et ta
 * photo. Les grosses cartes arrivent par une invocation.
 */
@Composable
private fun BoxScope.Fan(o: CardStore.Opening, tilt: () -> Offset, time: () -> Float, onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by CardStore.state.collectAsState()
    val snap by CardStore.snapshot.collectAsState()
    val pulls = o.result.pulls
    val n = pulls.size
    var index by remember { mutableIntStateOf(0) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var leaving by remember { mutableStateOf(false) }
    var walkout by remember { mutableStateOf<Int?>(null) }
    val shown = remember { mutableStateListOf<Int>() }
    var burstAt by remember { mutableFloatStateOf(-10f) }
    val flip = remember { Animatable(0f) }
    val enter = remember { Animatable(0f) }
    // La première carte se retourne d'un geste fluide à la sortie du pack ; les suivantes sont déjà face visible.
    val reveal = remember { Animatable(if (wantsWalkoutFirst(o, prefs)) 0f else 180f) }
    LaunchedEffect(Unit) { delay(120); reveal.animateTo(0f, tween(520, easing = FastOutSlowInEasing)) }
    // Un duo ou une chromatique a toujours droit à son invocation : ils sont rares à toutes les raretés.
    fun wantsWalkout(i: Int) = (pulls[i].card.rarity >= prefs.walkoutMin || pulls[i].card.duo || pulls[i].finish == Finish.CHROMATIQUE) && (!prefs.walkoutNewOnly || o.gains[i].fresh)
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 240f)) }
    // Chaque carte qui arrive au-dessus de la pile se révèle : impact de son biome, ou invocation pour les grosses.
    LaunchedEffect(index) {
        if (index >= n || index in shown) return@LaunchedEffect
        if (wantsWalkout(index)) walkout = index
        else { shown += index; burstAt = time(); if (pulls[index].card.rarity >= Rarity.RARE) Rumble.reveal(ctx) else Rumble.tick(ctx, 0.5f) }
    }
    fun next(dir: Float, width: Float) = scope.launch {
        if (leaving) return@launch
        leaving = true
        Rumble.tick(ctx, 0.45f)
        animate(dragX, dir * width * 1.4f, animationSpec = tween(220, easing = FastOutLinearInEasing)) { v, _ -> dragX = v }
        flip.snapTo(0f); dragX = 0f; index++; leaving = false
        if (index >= n) onFinished()
    }
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(index) {
        detectHorizontalDragGestures(
            onDragEnd = { if (kotlin.math.abs(dragX) > size.width * 0.22f) next(if (dragX < 0) -1f else 1f, size.width.toFloat())
                else scope.launch { animate(dragX, 0f, animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)) { v, _ -> dragX = v } } },
            onDragCancel = { scope.launch { animate(dragX, 0f) { v, _ -> dragX = v } } },
        ) { change, dx -> change.consume(); if (!leaving && walkout == null) dragX += dx }
    }) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        // À mi-chemin du geste, la carte qui monte en deuxième place se retourne déjà, au lieu d'apparaître d'un coup.
        val advanced by remember(w) { derivedStateOf { kotlin.math.abs(dragX) > w * 0.3f } }
        Row(Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter), verticalAlignment = Alignment.CenterVertically) {
            Text("${(index + 1).coerceAtMost(n)}/$n", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(tr("Tout voir"), color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onFinished).padding(horizontal = 14.dp, vertical = 7.dp))
        }
        val top = pulls.getOrNull(index)
        if (top != null) Canvas(Modifier.fillMaxSize()) {
            with(Vfx) { top.card.elements.forEachIndexed { k, e -> impact(e, Offset(w / 2 + dragX, h * 0.46f), time() - burstAt - k * 0.25f, w * 0.55f) } }
        }
        // La pile : la carte du dessus suit le doigt, celle d'en dessous est déjà face visible et avance à mesure qu'on
        // glisse (on la découvre simplement en écartant la première) ; plus loin, les dos en éventail. Seules les grosses
        // cartes (invocation) restent cachées jusqu'à leur tour.
        // Lu seulement dans graphicsLayer : glisser ne recompose pas les cartes, l'image suit le doigt à chaque trame.
        for (i in (n - 1) downTo index) {
            val p = pulls[i]; val depth = i - index
            val hidden = (depth >= 3 || (depth == 2 && !advanced)) || (wantsWalkout(i) && i !in shown)
            Box(Modifier.align(Alignment.Center).offset(y = (-12).dp).fillMaxWidth(0.9f).zIndex(-depth.toFloat()).graphicsLayer {
                val e = enter.value
                // Position en éventail d'une profondeur donnée ; la carte suivante glisse de sa place vers celle du dessus.
                fun ty(d: Float) = -d * 26f * density
                fun tx(d: Int) = d * (if (d % 2 == 1) 14f else -12f) * density
                fun rz(d: Int) = if (d == 0) 0f else d * (if (d % 2 == 1) 4f else -3f)
                // Toute la pile avance d'un cran pendant qu'on écarte la carte du dessus : rien ne saute au changement.
                val k = if (depth >= 1) (kotlin.math.abs(dragX) / (w * 0.6f)).coerceIn(0f, 1f) else 0f
                translationY = ty(depth - k) + (1 - e) * h * (1f + depth * 0.1f)
                translationX = tx(depth) + (tx(depth - 1) - tx(depth)) * k
                rotationZ = rz(depth) + (rz(depth - 1) - rz(depth)) * k
                val s = 1f - (depth - k) * 0.06f; scaleX = s; scaleY = s
                if (depth == 0) {
                    translationX = dragX; rotationZ = dragX / w * 18f
                    rotationY = flip.value + reveal.value + tilt().x * 6f; cameraDistance = 16f * density
                }
            }.then(if (depth == 0) Modifier.pointerInput(index) { detectTapGestures { pos ->
                if (leaving) return@detectTapGestures
                // La carte bascule du côté où l'on appuie : touchée à droite elle part vers la gauche, à gauche vers la droite.
                val dir = if (pos.x > size.width / 2) -1f else 1f
                scope.launch { Rumble.tick(ctx, 0.4f); flip.animateTo(flip.value + dir * 180f, spring(dampingRatio = 0.8f, stiffness = 260f)); if (kotlin.math.abs(flip.value) >= 359f) flip.snapTo(0f) }
            } } else Modifier)) {
                when {
                    hidden || (depth == 0 && reveal.value > 90f) -> Box(Modifier.graphicsLayer { if (depth == 0 && reveal.value > 90f) rotationY = 180f }) { Backside(p.card, o.booster, o.result.gold, time) }
                    depth > 0 || facesFront(flip.value) -> Box {
                        CollectibleCard(p.card, p.finish, Modifier.fillMaxWidth(), live = depth == 0, tilt = tilt, level = o.gains[i].level)
                        if (depth == 0) GainBadge(o.gains[i], Modifier.align(Alignment.TopCenter).offset(y = (-10).dp))
                    }
                    // Verso : ta carte Athlète avec ce fond, tes stats, ta photo et le bonus.
                    else -> Box(Modifier.graphicsLayer { rotationY = 180f }) {
                        AthleteCard(myFace(snap?.now, p, cutoutOverride = null).copy(level = o.gains[i].level), Modifier.fillMaxWidth(), interactive = false, flippable = false)
                    }
                }
            }
        }
        if (top != null) Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (index in shown) {
                Text(top.card.rarity.symbol + " " + top.card.rarityText().uppercase(), color = rarityColors(top.card.rarity)[1], fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp)
                Text(top.card.name + " · " + Bonus.label(top, o.gains[index].level), color = Color.White, fontSize = 15.sp)
            }
            Text(tr(if (facesFront(flip.value)) "Glisse pour la suivante · touche pour l'essayer" else "Touche pour revoir le fond"), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
    walkout?.let { i -> Invocation(pulls[i], o.gains[i].fresh, tilt) { shown += i; burstAt = time(); walkout = null } }
}

private fun wantsWalkoutFirst(o: CardStore.Opening, prefs: CardStore.State) = o.result.pulls.firstOrNull()?.let { p ->
    (p.card.rarity >= prefs.walkoutMin || p.card.duo || p.finish == Finish.CHROMATIQUE) && (!prefs.walkoutNewOnly || o.gains[0].fresh) } == true

/** Vrai quand une carte tournée de [angle] degrés montre sa face (et non son verso). */
internal fun facesFront(angle: Float) = ((angle % 360f + 360f) % 360f).let { it < 90f || it > 270f }

/** Dos de carte au thème du pack ouvert ; le halo trahit un peu la rareté (rien pour une commune). */
@Composable
private fun Backside(card: CardDef, booster: Booster, gold: Boolean, time: () -> Float) {
    val r = card.rarity
    val glow = if (r >= Rarity.PEU_COMMUNE) rarityColors(r)[1] else null
    Box(Modifier.fillMaxWidth().drawBehind {
        if (r >= Rarity.EPIQUE) {
            drawCircle(Brush.radialGradient(listOf((glow ?: Color.White).copy(alpha = 0.45f + 0.15f * sin(time() * 4f)), Color.Transparent)), size.maxDimension * 0.85f)
            with(Vfx) { card.elements.forEach { aura(it, center, size.width * 0.75f, time(), 1f, 36) } }
        }
    }) {
        CardBackside(Modifier.fillMaxWidth(), glow = glow, time = time, element = booster.element, gold = gold)
    }
}

@Composable
private fun GainBadge(g: CardStore.Gain, modifier: Modifier = Modifier) {
    when {
        g.fresh -> NewBadge(modifier)
        g.fusable -> Text(tr("FUSION") + " ▲", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp,
            modifier = modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFF1FA67A), Color(0xFF6FD3A0)))).padding(horizontal = 8.dp, vertical = 3.dp))
        else -> Text("+1", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp,
            modifier = modifier.clip(RoundedCornerShape(50)).background(Color(0xFF2A2D38)).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
internal fun NewBadge(modifier: Modifier = Modifier) = Text(tr("NOUVEAU"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp,
    modifier = modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFFFF4F7B), Color(0xFFFF9A3C)))).padding(horizontal = 8.dp, vertical = 3.dp))

/**
 * Invocation des grosses cartes : un cercle runique se trace au sol, le biome de la carte se lève (les deux pour une
 * mythique), un pilier de lumière jaillit, la carte descend en tournant, onde de choc. Un appui passe à la révélation.
 */
@Composable
fun Invocation(pull: Pull, fresh: Boolean, tilt: () -> Offset, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val rc = rarityColors(pull.card.rarity)
    val color = rc[1]
    val elements = pull.card.elements
    var t by remember { mutableFloatStateOf(0f) }
    var jump by remember { mutableFloatStateOf(0f) }
    val label = (pull.card.rarityText() + if (pull.finish == Finish.CHROMATIQUE) " " + tr("Chromatique") else "").uppercase()
    val reveal = 3.0f
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        val fired = mutableSetOf<Int>()
        Rumble.rise(ctx)
        while (true) withFrameNanos { now ->
            t = (now - start) / 1e9f + jump
            label.indices.forEach { i -> if (t > 1.0f + i * 0.07f && fired.add(i)) Rumble.tick(ctx, 0.45f) }
            if (t > 1.9f && fired.add(50)) Rumble.thud(ctx)
            if (t > reveal && fired.add(100)) Rumble.reveal(ctx)
        }
    }
    val shake = if (t in reveal..(reveal + 0.4f)) (1f - (t - reveal) / 0.4f) * 14f * sin(t * 90f) else if (t in 1.9f..2.2f) 6f * sin(t * 80f) else 0f
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(remember { MutableInteractionSource() }, null) { if (t < reveal) jump += reveal - t else onDone() }
        .graphicsLayer { translationX = shake; translationY = shake * 0.6f }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val floor = Offset(w / 2, h * 0.8f)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.2f * (t / 1.2f).coerceIn(0f, 1f))), h * 0.4f, h))
            with(Vfx) {
                magicCircle(floor, w * 0.46f, t, color, ((t - 0.2f) / 1.0f).coerceIn(0f, 1f), squash = 0.3f)
                elements.forEachIndexed { k, e ->
                    magicCircle(floor, w * (0.3f - k * 0.08f), -t * (1.5f + k), e.mid.c(), ((t - 0.5f - k * 0.2f) / 0.9f).coerceIn(0f, 1f), squash = 0.3f, alpha = 0.8f)
                    aura(e, Offset(w / 2 + (k - (elements.size - 1) / 2f) * w * 0.25f, h * 0.66f), w * 0.5f, t, ((t - 0.4f) / 0.8f).coerceIn(0f, 1f), 70)
                }
                val pil = when { t < 1.9f -> 0f; t < 2.1f -> (t - 1.9f) / 0.2f; t < reveal + 0.3f -> 1f; else -> (1f - (t - reveal - 0.3f) / 0.8f).coerceIn(0.25f, 1f) }
                pillar(floor, w * (0.12f + 0.04f * sin(t * 9f)), h * 0.95f, color, pil * 0.85f)
                elements.forEachIndexed { k, e ->
                    if (t > 1.9f) impact(e, floor, t - 1.9f - k * 0.2f, w * 0.35f)
                    if (t > reveal) impact(e, Offset(w / 2, h * 0.42f), t - reveal - k * 0.25f, w * 0.6f)
                }
            }
        }
        Row(Modifier.align(Alignment.Center).offset(y = (-60).dp).graphicsLayer { alpha = if (t < 2.3f) 1f else (1f - (t - 2.3f) * 4f).coerceIn(0f, 1f) }) {
            label.forEachIndexed { i, ch ->
                val k = ((t - 1.0f - i * 0.07f) / 0.22f).coerceIn(0f, 1f)
                Text(ch.toString(), style = TextStyle(brush = Brush.verticalGradient(listOf(Color.White, rc[0], color)), fontSize = (300f / label.length).coerceIn(24f, 52f).sp, fontWeight = FontWeight.Black,
                    shadow = Shadow(color, Offset.Zero, 34f)), modifier = Modifier.graphicsLayer { alpha = k; val s = 2.4f - 1.4f * k; scaleX = s; scaleY = s; translationY = (1 - k) * -40f })
            }
        }
        Text(pull.card.rarity.symbol + "  " + elements.joinToString(" + ") { tr(it.label).uppercase() } + if (pull.finish != Finish.NORMALE) " · " + tr(pull.finish.label).uppercase() else "",
            color = elements.first().mid.c(), fontWeight = FontWeight.Black, letterSpacing = 3.sp,
            modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = ((t - 1.5f) * 3f).coerceIn(0f, 1f) * (1f - ((t - 2.3f) * 4f).coerceIn(0f, 1f)) })
        if (t > 2.1f) {
            val k = ((t - 2.1f) / (reveal - 2.1f)).coerceIn(0f, 1f)
            val ease = 1f - (1f - k) * (1f - k) * (1f - k)
            val rot = 720f * (1f - ease)
            Box(Modifier.align(Alignment.Center).offset(y = (-30).dp).fillMaxWidth(0.72f).graphicsLayer {
                translationY = -(1f - ease) * size.height * 1.4f; val s = 0.4f + 0.6f * ease; scaleX = s; scaleY = s
                rotationY = rot + if (k >= 1f) tilt().x * 8f else 0f; cameraDistance = 16f * density
                if (t > reveal) { val f = 1f + 0.04f * sin((t - reveal) * 2.2f); scaleX *= f; scaleY *= f; translationY = sin((t - reveal) * 1.6f) * 10f * density }
            }) {
                val facing = (rot % 360f).let { it < 90f || it > 270f }
                if (facing) CollectibleCard(pull.card, pull.finish, Modifier.fillMaxWidth(), live = true, tilt = tilt)
                else Box(Modifier.graphicsLayer { rotationY = 180f }) { CardBackside(Modifier.fillMaxWidth(), color, element = pull.card.element) }
                if (fresh && t > reveal + 0.3f) NewBadge(Modifier.align(Alignment.TopStart).padding(10.dp))
            }
        }
        val flash = if (t >= reveal) (1f - (t - reveal) / 0.45f).coerceIn(0f, 1f) else 0f
        if (flash > 0f) Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = flash), color.copy(alpha = flash * 0.6f), Color.Transparent))))
        if (t > reveal + 0.5f) Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(pull.card.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text(Bonus.label(pull, 1), color = elements.first().mid.c(), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            Text(tr("Touchez pour continuer"), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Bilan : chaque carte avec ce qu'elle a apporté (nouvelle, ou doublon qui remplit la jauge de fusion). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(o: CardStore.Opening, onDone: () -> Unit, onAgain: () -> Unit, onAlbum: () -> Unit, onShare: (CardStore.Gain) -> Unit) {
    val state by CardStore.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (o.result.prism) tr("PACK PRISME !") else if (o.result.gold) tr("PACK DORÉ") + " · " + tr(o.booster.label) else tr("Booster") + " " + tr(o.booster.label), color = if (o.result.prism || o.result.gold) Gold else Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp, modifier = Modifier.padding(top = 12.dp))
        val fresh = o.gains.count { it.fresh }; val ready = o.gains.filter { it.fusable }.distinctBy { it.pull.card.id }.size
        Text(listOfNotNull(if (fresh > 0) "$fresh " + tr(if (fresh > 1) "nouveaux fonds" else "nouveau fond") else null, if (ready > 0) "$ready " + tr(if (ready > 1) "fusions prêtes" else "fusion prête") else null)
            .joinToString(" · ").ifEmpty { tr("Doublons à fusionner ou à échanger") }, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        // Quatre cartes : deux rangées de deux ; trois : une rangée.
        FlowRow(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(14.dp), maxItemsInEachRow = if (o.gains.size == 4) 2 else 3) {
            o.gains.forEachIndexed { i, g ->
                val pop = remember { Animatable(0f) }
                val bar = remember { Animatable(0f) }
                LaunchedEffect(Unit) { delay(90L * i); pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 300f)); bar.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
                Column(Modifier.width(when (o.gains.size) { 4 -> 140.dp; 3 -> 108.dp; else -> 168.dp }).graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        CollectibleCard(g.pull.card, g.pull.finish, Modifier.fillMaxWidth(), live = g.pull.card.rarity >= Rarity.RARE, level = g.level)
                        GainBadge(g, Modifier.align(Alignment.TopCenter).offset(y = (-8).dp))
                    }
                    // Jauge de fusion : les doublons du fond face au prochain niveau.
                    val cost = Levels.cost(g.level)
                    val spare = state.spare(g.pull.card)
                    val to = cost?.let { (spare.toFloat() / it).coerceIn(0f, 1f) } ?: 1f
                    Text(tr("Niv.") + " ${g.level}" + (cost?.let { " · ${spare.coerceAtMost(it)}/$it" } ?: " · " + tr("max")), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp))
                    Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxWidth((to * bar.value).coerceIn(0.03f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(g.pull.card.element.outer.c(), g.pull.card.element.mid.c()))))
                    }
                    Text(Bonus.label(g.pull, g.level), color = g.pull.card.element.mid.c(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        // Partage de la meilleure carte du pack (la plus rare, chromatique et duo en tête).
        val best = o.gains.maxWith(compareBy<CardStore.Gain>({ it.pull.card.rarity }, { it.pull.finish == Finish.CHROMATIQUE }, { it.pull.card.duo }, { it.fresh }))
        Text(tr("Partager") + " " + best.pull.card.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.padding(top = 16.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable { onShare(best) }.padding(horizontal = 18.dp, vertical = 9.dp))
        Spacer(Modifier.height(24.dp))
        GoldProgress(state)
        GlowButton(tr("Ouvrir encore"), Modifier.fillMaxWidth().padding(top = 14.dp), listOf(o.booster.element.outer.c(), o.booster.element.mid.c()),
            enabled = CardStore.canOpen(), badge = state.packsAvailable.takeIf { it > 0 }?.toString(), onClick = onAgain)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Voir l'album"), color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(InkCard).clickable(onClick = onAlbum).padding(14.dp))
            Text(tr("Terminé"), color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(InkCard).clickable(onClick = onDone).padding(14.dp))
        }
    }
}
