package app.superhealth.ui.cards

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalContext
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

private val Booster.packKey get() = name.lowercase()

/**
 * Les packs : le sachet 3D du biome choisi (glisser de haut en bas pour changer de biome, sur le corps pour le faire
 * tourner, sur la bande du haut pour le déchirer). Le tirage se fait sur le serveur ; le sachet finit de se déchirer
 * une fois la réponse reçue, puis les cartes se révèlent.
 */
@Composable
fun PackShopScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val state by CardStore.state.collectAsState()
    // Un pack payé mais pas encore révélé reprend là où on l'avait laissé.
    var opening by remember { mutableStateOf<CardStore.Opening?>(run { CardStore.load(ctx); CardStore.state.value.pending?.opening() }) }
    var torn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { CardStore.load(ctx); runCatching { CardStore.refresh(ctx) } }
    var index by rememberSaveable { mutableIntStateOf(state.booster.ordinal) }
    val booster = Entries[index.mod(Entries.size)]
    val haptics = app.superhealth.ui.rememberHaptics()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var side by remember { mutableIntStateOf(1) }
    var tearAt by remember { mutableFloatStateOf(0f) }
    var serial by remember { mutableIntStateOf(0) }
    // Le tirage se fait sur le serveur (ou en local en mode démo) ; le sachet s'ouvre quand il a répondu.
    fun open(direction: Int = 1, progress: Float = 0f, again: Boolean = false) {
        if (busy || (opening != null && !again)) return
        busy = true; error = null; side = direction; tearAt = progress
        scope.launch {
            runCatching { CardStore.open(ctx, booster) }
                .onSuccess { serial++; torn = false; opening = it }
                .onFailure { error = it.message ?: tr("Ouverture impossible. Réessaie."); if (again) { opening = null; torn = false } }
            busy = false
        }
    }
    fun step(offset: Int) {
        if (busy || opening != null) return
        error = null; index = (index + offset).mod(Entries.size); haptics.tick()
    }
    val o = opening
    if (o != null && torn) {
        key(o) { PackOpening(o, onDone = { opening = null; torn = false }, onAgain = { open(again = true) }, onAlbum = { opening = null; torn = false; nav.go("cards/album") },
            onShare = { g -> CardShare.open(g.pull, g.level, CardShare.Event.PULL); opening = null; torn = false; nav.go("social/compose") }) }
        return
    }
    // Filet de sécurité : si le sachet ne signale pas la fin de la déchirure, les cartes arrivent quand même.
    LaunchedEffect(o) { if (o != null) { delay(9000); torn = true } }
    val shown = o?.booster ?: booster
    val gold = o?.result?.gold ?: state.nextGold
    val best = o?.result?.pulls?.maxOf { it.card.rarity }
    val announce = o != null && (best!! >= Rarity.RARE || o.result.prism)
    LaunchedEffect(o) { if (o != null && best!!.big) { delay(250); Rumble.rise(ctx) } }
    val allowed = CardStore.canOpen() && o == null
    val time by rememberCardTime()
    val chrome by animateFloatAsState(if (o == null) 1f else 0f, tween(260), label = "chrome")
    val halo by animateFloatAsState(if (announce) 1f else 0f, tween(900), label = "halo")
    val script = "arena.language(${app.superhealth.ui.Lang.english});arena.pause(false);" +
        (if (o == null) "arena.clearIfOpened();arena.select('${shown.packKey}',$gold,$allowed,0,'');" +
            (if (busy) "arena.prepareOpen();" else "") +
            (error?.let { "arena.openingFailed(" + org.json.JSONObject.quote(it) + ");" } ?: "") +
            (if (!busy && error == null) "arena.releaseWaiting();" else "")
        else "arena.showTear({id:'$serial-${o.result.pulls.size}-${shown.packKey}',slot:'${shown.packKey}',gold:$gold,count:${o.result.pulls.size},seed:$serial},$side,$tearAt);")
    BackHandler(o != null || busy) {}
    CardScaffold("Packs", { if (o == null && !busy) nav.popBackStack() }, shown.element.outer.c(), scroll = false, action = {
        IconButton({ if (o == null) nav.go("cards/rates") }) { Icon(Icons.Rounded.Info, tr("Taux"), tint = Color.White.copy(alpha = 0.7f * chrome)) }
    }, backdrop = { BiomeBackdrop(shown, { time }, dim = 0.66f) }) {
        Wallet(state, Modifier.graphicsLayer { alpha = chrome })
        Box(Modifier.weight(1f).fillMaxWidth().drawBehind {
            // Halo du biome derrière le sachet ; il vire à la couleur de la meilleure carte quand elle est rare.
            val tone = if (gold) Gold else shown.element.mid.c()
            val color = if (halo > 0f && best != null) androidx.compose.ui.graphics.lerp(tone, rarityColors(best)[1], halo) else tone
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.34f + 0.3f * halo + 0.05f * sin(time * 2.4f)), Color.Transparent),
                center, size.minDimension * (0.62f + 0.2f * halo)), size.minDimension * (0.62f + 0.2f * halo))
        }) {
            // Pendant la déchirure, les illustrations tirées se dessinent une première fois en tout petit : leurs shaders
            // sont prêts quand les cartes sortent, sans à-coup sur les premières images.
            if (o != null) Row(Modifier.align(Alignment.TopStart).zIndex(-1f).graphicsLayer { alpha = 0.01f }) {
                o.result.pulls.forEach { CardArt(it.card, it.finish, Modifier.size(4.dp), resolution = 1f) }
            }
            // Tant que le premier sachet n'est pas dessiné, sa silhouette aux couleurs du biome tient la place.
            val drawn by PackWeb.rendered.collectAsState()
            val wait by animateFloatAsState(if (drawn) 0f else 1f, tween(280), label = "wait")
            if (wait > 0f) Canvas(Modifier.align(Alignment.Center).fillMaxHeight(0.62f).aspectRatio(0.62f).graphicsLayer { alpha = wait * (0.55f + 0.25f * sin(time * 4f)) }) {
                val r = androidx.compose.ui.geometry.CornerRadius(size.width * 0.06f)
                drawRoundRect(Brush.verticalGradient(listOf(shown.element.mid.c().copy(alpha = 0.55f), shown.element.outer.c().copy(alpha = 0.35f))), cornerRadius = r)
                drawRoundRect(Color.White.copy(alpha = 0.35f), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                with(Vfx) { emblem(shown.element, center, size.width * 0.16f, Color.White.copy(alpha = 0.8f)) }
            }
            EquipmentWeb(PackWeb.PAGE, script, Modifier.fillMaxSize(), ownsGestures = true, keepAlive = true) { kind, value ->
                when (kind) {
                    "step" -> if (value == "1" || value == "-1") step(value.toInt())
                    "open" -> {
                        val gesture = value.split('|', limit = 3)
                        if (gesture.firstOrNull() == booster.packKey) open(if (gesture.getOrNull(1) == "-1") -1 else 1,
                            gesture.getOrNull(2)?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f)
                    }
                    "torn" -> torn = true
                    "haptic" -> when (value) {
                        "tear-start" -> haptics.press(); "tear-tick" -> haptics.tick(); "tear-end" -> haptics.release()
                        "eject" -> haptics.confirm(); "card" -> haptics.toggle()
                    }
                }
            }
            if (o != null && (o.result.prism || o.result.gold)) Text(tr(if (o.result.prism) "PACK PRISME" else "PACK DORÉ"), Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
                style = TextStyle(brush = Brush.horizontalGradient(if (o.result.prism) rarityColors(Rarity.EPIQUE) else listOf(Color(0xFFB8860B), Gold, Color(0xFFFFF4C8), Gold)),
                    fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 4.sp, shadow = Shadow(Gold, Offset.Zero, 24f)))
            // Les biomes, à portée de pouce : un point par booster, et les flèches pour qui ne glisse pas.
            Column(Modifier.align(Alignment.CenterEnd).graphicsLayer { alpha = chrome }, horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton({ step(-1) }, Modifier.size(40.dp)) { Icon(Icons.Rounded.ExpandLess, tr("Booster précédent"), tint = Color.White.copy(alpha = 0.8f)) }
                Entries.forEachIndexed { i, b ->
                    Box(Modifier.padding(vertical = 4.dp).size(8.dp, if (b == booster) 22.dp else 8.dp).clip(RoundedCornerShape(50))
                        .background(if (b == booster) b.element.mid.c() else Color.White.copy(alpha = 0.25f)).clickable { step(i - index.mod(Entries.size)) })
                }
                IconButton({ step(1) }, Modifier.size(40.dp)) { Icon(Icons.Rounded.ExpandMore, tr("Booster suivant"), tint = Color.White.copy(alpha = 0.8f)) }
            }
        }
        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = chrome }.padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val e = booster.element
            AnimatedContent(booster, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "booster") { b ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("Booster") + " " + tr(b.label), color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp, textAlign = TextAlign.Center)
                    Text(tr(b.element.tagline), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                val content = Catalog.ofBooster(booster)
                InfoPill("${content.count { state.owns(it) }}/${content.size} " + tr("fonds"), Color.White)
                InfoPill(tr("Bonus") + " " + e.stat.code, e.mid.c())
                InfoPill(if (state.nextGold) tr("Pack doré · chances ×3") else tr("3 à 4 cartes"), Gold)
            }
            // Un seul bouton : il ouvre d'abord un pack offert, sinon dépense de l'or.
            val gift = state.packsAvailable > 0
            GlowButton(when { busy -> tr("Ouverture…"); gift -> tr("Ouvrir · pack offert"); else -> trf("Ouvrir · %1\$s or", state.price) }, Modifier.fillMaxWidth().padding(top = 14.dp),
                listOf(e.outer.c(), e.mid.c()), enabled = !busy && allowed, badge = state.packsAvailable.takeIf { it > 0 }?.toString()) { open() }
            Text(error ?: if (allowed) tr("Ou déchire la bande du haut du sachet.") else if (o == null && !busy) tr("Termine des quêtes pour gagner de l'or et des packs.") else "",
                color = if (error != null) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.55f), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
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
    return "%.${d}f %%".format(java.util.Locale.FRANCE, v)
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

private enum class Stage { FAN, SUMMARY }

@Composable
private fun PackOpening(o: CardStore.Opening, onDone: () -> Unit, onAgain: () -> Unit, onAlbum: () -> Unit, onShare: (CardStore.Gain) -> Unit) {
    val ctx = LocalContext.current
    var stage by remember { mutableStateOf(Stage.FAN) }
    val pulls = o.result.pulls
    val best = pulls.maxOf { it.card.rarity }
    val tiltS = rememberTilt()
    val timeS = rememberCardTime()
    // Un éclat de lumière fait la jonction entre le sachet 3D et les cartes : le changement de scène ne se voit pas.
    val flash = remember { Animatable(1f) }
    LaunchedEffect(Unit) { flash.animateTo(0f, tween(480, easing = LinearOutSlowInEasing)) }
    Box(Modifier.fillMaxSize().background(Ink)) {
        BiomeBackdrop(o.booster, { timeS.value }, dim = if (stage == Stage.SUMMARY) 0.8f else 0.62f)
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            when (stage) {
                Stage.FAN -> Fan(o, { tiltS.value }, { timeS.value }) { stage = Stage.SUMMARY }
                Stage.SUMMARY -> { LaunchedEffect(Unit) { CardStore.seen(ctx) }; Summary(o, onDone, onAgain, onAlbum, onShare) }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val f = flash.value
            if (f > 0f) drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f * f), o.booster.element.core.c().copy(alpha = 0.5f * f), Color.Transparent), Offset(size.width / 2, size.height * 0.42f), size.height * (0.9f - 0.4f * f)))
        }
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
    LaunchedEffect(Unit) { delay(400); reveal.animateTo(0f, tween(480, easing = FastOutSlowInEasing)) }
    // Un duo ou une chromatique a toujours droit à son invocation : ils sont rares à toutes les raretés.
    fun wantsWalkout(i: Int) = (pulls[i].card.rarity >= prefs.walkoutMin || pulls[i].card.duo || pulls[i].finish == Finish.CHROMATIQUE) && (!prefs.walkoutNewOnly || o.gains[i].fresh)
    // La pile sort de l'endroit où était le sachet et grandit jusqu'à sa place, sans rebond ; elle se déploie ensuite.
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(460, easing = FastOutSlowInEasing)) }
    // Chaque carte qui arrive au-dessus de la pile se révèle : impact de son biome, ou invocation pour les grosses.
    LaunchedEffect(index) {
        if (index >= n || index in shown) return@LaunchedEffect
        // La première attend d'être sortie et retournée avant son impact.
        if (index == 0) delay(if (wantsWalkout(0)) 480 else 780)
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
                translationY = ty(depth - k) * e - (1 - e) * h * 0.08f
                translationX = (tx(depth) + (tx(depth - 1) - tx(depth)) * k) * e
                rotationZ = (rz(depth) + (rz(depth - 1) - rz(depth)) * k) * e
                val s = (1f - (depth - k) * 0.06f) * (0.4f + 0.6f * e); scaleX = s; scaleY = s
                alpha = (e * 4f).coerceAtMost(1f)
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
    val ctx = LocalContext.current
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
                    // 3.8 : équiper le fond tout de suite, sans repasser par l'album.
                    val equipped = state.background == g.pull.key
                    Text(tr(if (equipped) "Équipé" else "Équiper"), color = if (equipped) g.pull.card.element.mid.c() else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        textAlign = TextAlign.Center, maxLines = 1,
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(50))
                            .background(if (equipped) Color.White.copy(alpha = 0.08f) else g.pull.card.element.outer.c().copy(alpha = 0.55f))
                            .clickable(enabled = !equipped) { CardStore.setBackground(ctx, g.pull.key); Rumble.reveal(ctx) }.padding(vertical = 7.dp))
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
