package app.superhealth.ui.league

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.cards.*
import app.superhealth.league.League
import app.superhealth.ui.cards.*
import app.superhealth.ui.go
import app.superhealth.ui.tr
import app.superhealth.ui.theme.Mf
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import kotlinx.coroutines.launch

/**
 * Couleurs du Groupe selon le thème : panneaux encre en sombre, cartes du thème en clair. Les écrans plein cadre des
 * cartes (packs, album, duel) restent sombres ; seuls les blocs posés sur la page du Groupe suivent le thème.
 */
internal class ArenaPalette(val dark: Boolean, val panel: Color, val text: Color, val muted: Color, val track: Color, val line: Color,
                            val gold: Color, val blue: Color, val chip: Color, val pack: Color, val eclat: Color, val green: Color)

@Composable
internal fun arenaPalette(): ArenaPalette {
    val c = Mf.colors
    return if (c.background.luminance() < 0.5f) ArenaPalette(true, InkCard, Color.White, Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f),
        Color.White.copy(alpha = 0.07f), Gold, Color(0xFF7FDBFF), Color.Black.copy(alpha = 0.35f), Color(0xFF9AA8FF), EclatColor, Color(0xFF6FD3A0))
    else ArenaPalette(false, c.card, c.text, c.textSecondary, c.text.copy(alpha = 0.08f), c.text.copy(alpha = 0.08f),
        Color(0xFFA86F00), Color(0xFF0A72B5), Color.White.copy(alpha = 0.75f), Color(0xFF4553D6), Color(0xFF0B85AD), Color(0xFF1E9A5E))
}

/** Onglets du Groupe : tout part des cartes (combat, quêtes, échanges), puis le fil des publications, puis les cercles et les amis. */
enum class GroupTab(val label: String) { ARENE("Cartes"), FIL("Fil"), CERCLES("Cercles") }

@Composable
internal fun GroupTabs(tab: GroupTab, onTab: (GroupTab) -> Unit) {
    val a = arenaPalette()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(if (a.dark) InkCard else a.track).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        GroupTab.entries.forEach { t ->
            val on = t == tab
            Text(tr(t.label), color = if (on) (if (a.dark) Ink else a.text) else a.text.copy(alpha = 0.7f), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) (if (a.dark) Color.White else a.panel) else Color.Transparent).clickable { onTab(t) }.padding(vertical = 9.dp))
        }
    }
}

/** Camp d'un membre : le biome de son fond, sinon celui de sa meilleure famille musculaire. */
internal fun campOf(card: CardDef?, stats: AthleteStats?): Element? =
    card?.takeIf { !it.neutral }?.element ?: stats?.let { s -> Stat.families.filter { s[it] != null }.maxByOrNull { s[it]!! }?.let(Element::of) }

/** Ma carte en tête de l'Arène : camp, fond et bonus, packs à ouvrir. */
@Composable
internal fun ArenaHero(nav: NavHostController) {
    val ctx = LocalContext.current
    val snap by CardStore.snapshot.collectAsState()
    val st by CardStore.state.collectAsState()
    LaunchedEffect(Unit) { CardStore.load(ctx); CardPhoto.load(ctx); runCatching { CardStore.refresh(ctx) } }
    val bg = CardStore.background()
    val stats = snap?.now
    val camp = campOf(bg?.card, stats)
    val tint = camp?.outer?.c() ?: Color(0xFF3C4BFF)
    val a = arenaPalette()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(a.panel, tint.copy(alpha = if (a.dark) 0.55f else 0.22f))))
        .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(24.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AthleteCard(myFace(stats, bg), Modifier.width(112.dp).clickable { nav.go("social") }, interactive = false)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text("@" + handle(), color = a.text, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1)
            Text(camp?.let { tr("Camp") + " " + tr(it.label) } ?: tr("Sans camp pour l'instant"), color = camp?.let { if (a.dark) it.mid.c() else it.outer.c() } ?: a.muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Text(bg?.let { it.card.name + " · " + tr("Niv.") + " ${st.level(it.card)} · " + Bonus.label(it, st.level(it.card)) } ?: tr("Fond de palier : aucun bonus"),
                color = a.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (st.packsAvailable > 0) Pill("${st.packsAvailable} " + tr(if (st.packsAvailable > 1) "packs offerts" else "pack offert"), a.pack, a.chip)
                Pill("${st.eclats} " + tr("or"), a.eclat, a.chip)
            }
            GlowButton(tr("Ouvrir un pack"), Modifier.padding(top = 10.dp).fillMaxWidth().height(44.dp), listOf(tint, camp?.mid?.c() ?: Color(0xFF8AE0FF)),
                badge = st.packsAvailable.takeIf { it > 0 }?.toString()) { nav.go("cards/packs") }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, bg: Color) = Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp))

internal data class Rival(val id: String, val face: CardFace, val note: String, val friend: League.Friend? = null)

/** Adversaires possibles : les amis les plus proches de ta note d'abord, puis ta carte d'il y a quatre semaines. */
@Composable
private fun rivalsOf(friends: List<League.Friend>): List<Rival> {
    val snap by CardStore.snapshot.collectAsState()
    val st by CardStore.state.collectAsState()
    val mine = snap?.now?.ovr ?: 0
    return buildList {
        friends.filter { it.athlete?.ovr10 != null }.sortedBy { kotlin.math.abs((it.athlete!!.ovr ?: 0) - mine) }.forEach { f ->
            val gap = (f.athlete!!.ovr ?: 0) - mine
            add(Rival(f.id, CardFace(f.shown.lowercase().replace(" ", ""), f.athlete, f.cardBg?.let(Pull::of), level = f.cardLevel ?: 1),
                when { gap > 0 -> tr("devant de") + " $gap"; gap < 0 -> tr("derrière de") + " ${-gap}"; else -> tr("à égalité") }, f))
        }
        snap?.month?.takeIf { it.ovr10 != null }?.let { add(Rival("past", CardFace(handle(), it, CardStore.background()), tr("toi, il y a 4 sem."))) }
        if (st.demo) add(Rival("demo", CardFace("lohan", CardStore.Demo.rival, Pull.of("N043:BRILLANTE"), level = 3), tr("rival de démo")))
    }
}

/** Défis du jour : les amis les plus proches de ta note d'abord, puis ta carte d'il y a quatre semaines. */
@Composable
internal fun ArenaDuels(nav: NavHostController, friends: List<League.Friend>) {
    val rivals = rivalsOf(friends)
    val a = arenaPalette()
    // Un appui sur la carte l'ouvre en grand ; le bouton « Défier » (ici ou dans la vue) lance le duel.
    var viewing by remember { mutableStateOf<Rival?>(null) }
    viewing?.let { r -> FriendCardViewer(r.friend?.let { friendFace(it) } ?: r.face, r.note, { viewing = null },
        r.friend?.cardTrade?.let { { viewing = null; nav.go("cards/trades") } }, account = r.friend?.cardTrade) { viewing = null; nav.go("cards/duel?friend=${r.id}") } }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Défis du jour"), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f), color = app.superhealth.ui.theme.Mf.colors.text)
            Text(tr("Tous les duels"), color = app.superhealth.ui.theme.Mf.colors.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { nav.go("cards/duel") })
        }
        if (rivals.isEmpty()) Text(tr("Ajoute un ami pour le défier, ou reviens après quelques séances pour affronter ta carte passée."),
            color = app.superhealth.ui.theme.Mf.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(4.dp))
        else Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rivals.forEach { r ->
                Column(Modifier.width(118.dp).clip(RoundedCornerShape(18.dp)).background(a.panel).border(1.dp, a.line, RoundedCornerShape(18.dp)).clickable { viewing = r }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    AthleteCard(r.friend?.let { friendFace(it) } ?: r.face, Modifier.fillMaxWidth(), interactive = false, animate = false)
                    Text("@" + r.face.name, color = a.text, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                    Text(r.note, color = a.muted, fontSize = 11.sp, maxLines = 1)
                    Row(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFFE53935), Color(0xFFFF8A1F))))
                        .clickable { nav.go("cards/duel?friend=${r.id}") }.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⚔ " + tr("Défier"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** Raccourcis vers l'album et les échanges (les quêtes ont leur propre carte au-dessus). */
@Composable
internal fun ArenaShortcuts(nav: NavHostController) {
    val st by CardStore.state.collectAsState()
    val fusions = remember(st.owned, st.merged, st.eclats) { Catalog.cards.count { st.canFuse(it) } }
    val a = arenaPalette()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile(Icons.Rounded.CollectionsBookmark, "Album", "${Catalog.cards.count { st.owns(it) }}/${Catalog.cards.size}" + if (fusions > 0) " · $fusions " + tr(if (fusions > 1) "fusions" else "fusion") else "",
            Modifier.weight(1f), a.pack, fusions, a.panel, a.text) { nav.go("cards/album") }
        Tile(Icons.Rounded.SwapHoriz, "Échanges", tr("Avec tes amis"), Modifier.weight(1f), a.green, st.pendingTrades, a.panel, a.text) { nav.go("cards/trades") }
    }
}

/**
 * Bouton Combat : une bannière « VS » où ta carte fait face à l'adversaire le plus proche de ta note. Un reflet la
 * traverse en boucle, l'emblème des épées bat comme un cœur ; un appui lance le duel contre cet adversaire.
 */
@Composable
internal fun CombatButton(nav: NavHostController, friends: List<League.Friend>) {
    val snap by CardStore.snapshot.collectAsState()
    val rivals = rivalsOf(friends)
    val rival = rivals.firstOrNull()
    val ready = snap?.now?.ovr10 != null
    val loop = rememberInfiniteTransition(label = "combat")
    val sweep by loop.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "sweep")
    val beat by loop.animateFloat(0f, 1f, infiniteRepeatable(keyframes { durationMillis = 1400; 0f at 0; 1f at 160; 0.2f at 320; 0.8f at 460; 0f at 900 }), label = "beat")
    val friendsCount = friends.count { it.athlete?.ovr10 != null }
    Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(26.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF5A0712), Color(0xFFD7262E), Color(0xFFFF7A1A)), Offset.Zero, Offset(1400f, 300f)))
        .border(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFFFFB37A), Color(0xFFFF4F5E), Color(0xFFFFD36B))), RoundedCornerShape(26.dp))
        .drawWithContent {
            drawContent()
            // Reflet diagonal qui balaie la bannière.
            val x = size.width * sweep
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                Offset(x - size.width * 0.18f, 0f), Offset(x + size.width * 0.18f, size.height)), blendMode = BlendMode.Plus)
        }
        .clickable { nav.go(if (rival != null && ready) "cards/duel?friend=${rival.id}" else "cards/duel") }) {
        // Grandes lettres VS en filigrane.
        Text("VS", style = TextStyle(color = Color.White.copy(alpha = 0.08f), fontSize = 120.sp, fontWeight = FontWeight.Black),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).graphicsLayer { translationY = 8.dp.toPx() })
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            // Les deux cartes en éventail, l'emblème au milieu.
            Box(Modifier.width(112.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                AthleteCard(myFace(snap?.now), Modifier.width(46.dp).offset(x = (-24).dp).graphicsLayer { rotationZ = -10f }, interactive = false, animate = false)
                rival?.let { r -> AthleteCard(r.friend?.let { friendFace(it) } ?: r.face, Modifier.width(46.dp).offset(x = 24.dp).graphicsLayer { rotationZ = 10f }, interactive = false, animate = false) }
                Box(Modifier.size(38.dp).graphicsLayer { val k = 1f + 0.14f * beat; scaleX = k; scaleY = k }
                    .clip(RoundedCornerShape(50)).background(Brush.radialGradient(listOf(Color(0xFFFFF2B0), Color(0xFFFFB020), Color(0xFFE5541A))))
                    .border(2.dp, Color.White, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    Text("⚔", color = Color(0xFF5A0712), fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            }
            Column(Modifier.padding(start = 6.dp).weight(1f)) {
                Text(tr("COMBAT"), style = TextStyle(color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 3.sp,
                    shadow = Shadow(Color(0xFF5A0712), Offset(0f, 4f), 10f)))
                Text(when {
                    !ready -> tr("Ta carte doit d'abord être évaluée")
                    rival?.friend != null -> tr("Défie") + " @" + rival.face.name + " · " + rival.note
                    rival != null -> tr("Contre") + " " + rival.note
                    else -> tr("Ajoute un ami pour combattre")
                }, color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (friendsCount > 1) Text("$friendsCount " + tr("adversaires disponibles"), color = Color(0xFFFFE0B0), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(28.dp), tint = Color.White)
        }
    }
}

/** Onglet Cercles : cercles et défis, amis, records et partage (les quêtes sont dans l'Arène). */
@Composable
internal fun CirclesPanel(nav: NavHostController, friends: Int) {
    val a = arenaPalette()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(a.panel).border(1.dp, a.line, RoundedCornerShape(20.dp))) {
        LinkRow(Icons.Rounded.Groups, "Cercles et défis", null, a.text) { nav.go("group/circles") }
        LinkRow(Icons.Rounded.People, "Mes amis", "$friends", a.text) { nav.go("friends") }
        LinkRow(Icons.Rounded.PersonAdd, "Ajouter un ami", tr("Code ou QR"), a.text) { nav.go("friends/add") }
        LinkRow(Icons.Rounded.EmojiEvents, "PR récents", null, a.text) { nav.go("group/records") }
        LinkRow(Icons.Rounded.Visibility, "Ce que je partage", null, a.text) { nav.go("group/sharing") }
        LinkRow(Icons.Rounded.Settings, "Réglages du Groupe", null, a.text) { nav.go("league/settings") }
    }
}

/** Les quêtes au cœur du Groupe : celles du jour et de la semaine, avec leur jauge, à récupérer d'un geste. */
@Composable
internal fun ArenaQuests(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by CardStore.state.collectAsState()
    val league by League.state.collectAsState()
    LaunchedEffect(Unit) { runCatching { app.superhealth.league.ProgressRepository.compute(ctx) } }
    val quests = remember(st.claimed, st.quests, st.demo, league.questWeeks) { runCatching { CardStore.quests() }.getOrDefault(emptyList()) }
    if (quests.isEmpty()) return
    val ready = quests.count { it.complete && !it.claimed }
    val a = arenaPalette()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(if (a.dark) Color(0xFF231A05) else Color(0xFFFFF4D6), a.panel)))
        .border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(24.dp)).clickable { nav.go("cards/quests") }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚑ " + tr("Quêtes"), color = a.gold, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(if (ready > 0) "$ready " + tr(if (ready > 1) "à récupérer" else "à récupérer") else "${quests.count { it.claimed }}/${quests.size}",
                color = if (ready > 0) Ink else a.muted, fontWeight = FontWeight.Black, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (ready > 0) Gold else a.track).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        quests.sortedWith(compareBy<CardStore.Quest> { it.claimed }.thenByDescending { it.complete }.thenByDescending { it.challenge != null }.thenBy { it.weekly }).take(4).forEach { q ->
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr(q.title), color = a.text.copy(alpha = if (q.claimed) 0.4f else 1f), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2)
                    if (q.weekly && !q.claimed) Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        app.superhealth.ui.cards.ModePill(q.mode)
                        q.note?.let { Text(tr(it), color = a.muted, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    }
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(a.track)) {
                        Box(Modifier.fillMaxWidth((q.progress / q.target.coerceAtLeast(1).toFloat()).coerceIn(0.03f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(q.difficulty.color.c(), Gold))))
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (q.complete && !q.claimed) Text(CardStore.prize(q, short = true), color = Ink, fontWeight = FontWeight.Black, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Gold, Color(0xFFFFF2B0)))).clickable {
                        scope.launch { runCatching { CardStore.claim(ctx, q) }.onSuccess { Rumble.reveal(ctx) } } }.padding(horizontal = 12.dp, vertical = 7.dp))
                else if (q.claimed) Text("✓", color = a.green, fontWeight = FontWeight.Black, fontSize = 13.sp)
                else Column(horizontalAlignment = Alignment.End) {
                    Text("${q.progress}/${q.target}", color = a.muted, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    if (q.packs > 0) Text(CardStore.prize(q), color = a.gold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }
    }
}

/**
 * Badges et séries dans l'Arène : le prochain palier de chaque type (avec son or), les séries tenues et leur bonus de
 * la semaine, et un bouton qui récupère tout d'un coup. Plus secondaire que les quêtes : tout finit en or, donc en packs.
 */
@Composable
internal fun ArenaBadges(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by CardStore.state.collectAsState()
    val league by League.state.collectAsState()
    val progress by app.superhealth.league.ProgressRepository.current.collectAsState()
    val payout = remember(st.badgesPaid, st.streakPaid, st.demo, league.earnedBadges, progress) { runCatching { CardStore.payout() }.getOrNull() } ?: return
    val summary = progress?.summary
    val earned = league.earnedBadges.toSet()
    val types = listOf("sessions", "records", "mindMinutes", "quests", "distanceKm", "foodDays")
    val nexts = types.mapNotNull { m -> app.superhealth.league.Achievements.next(m, earned) }
    val streaks = payout.streaks.filter { it.weeks > 0 || it.days > 0 }
    var busy by remember { mutableStateOf(false) }
    val a = arenaPalette()
    val blue = a.blue
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(if (a.dark) Color(0xFF0A1A2A) else Color(0xFFE3F4FF), a.panel)))
        .border(1.dp, blue.copy(alpha = 0.3f), RoundedCornerShape(24.dp)).clickable { nav.go("social/badges") }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Badges et séries"), color = blue, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(tr("Chaque palier et chaque semaine tenue rapportent de l'or"), color = a.muted, fontSize = 11.sp)
            }
            val total = payout.badges.sumOf(app.superhealth.league.Achievements::reward) + payout.streaks.filter { it.claimable }.sumOf { it.reward }
            if (total > 0) Text(if (busy) "…" else "+$total ✦", color = Ink, fontWeight = FontWeight.Black, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Gold, Color(0xFFFFF2B0)))).clickable(enabled = !busy) {
                    busy = true
                    scope.launch { runCatching { CardStore.collect(ctx) }.onSuccess { Rumble.reveal(ctx) }; busy = false } }.padding(horizontal = 12.dp, vertical = 7.dp))
        }
        Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            nexts.forEach { b ->
                val value = summary?.let { app.superhealth.league.Achievements.value(b, it) } ?: 0L
                Column(Modifier.width(74.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BadgeEmblem(b, 56.dp, locked = true)
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(a.track)) {
                        Box(Modifier.fillMaxWidth((value / b.threshold.toFloat()).coerceIn(0.04f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(Color(BadgeTheme.element(b.metric).mid)))
                    }
                    Text("$value/${b.threshold}" + if (b.metric == "distanceKm") " km" else "", color = a.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("+${app.superhealth.league.Achievements.reward(b)} ✦", color = a.gold, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        if (streaks.isNotEmpty()) Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            streaks.forEach { s ->
                Row(Modifier.clip(RoundedCornerShape(50)).background(a.track).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    BadgeEmblem("streak:${s.track.name}", (app.superhealth.league.Achievements.STREAK_WEEKS.count { s.best >= it } - 1).coerceAtLeast(0), 28.dp, locked = s.weeks == 0)
                    Column(Modifier.padding(start = 6.dp)) {
                        Text(tr(s.track.label) + " · " + s.weeks + " " + tr("sem."), color = a.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(when { s.claimable -> "+${s.reward} ✦ " + tr("cette semaine"); s.done -> "✓ " + tr("semaine touchée"); else -> "${s.days}/${s.need} " + tr("jours cette semaine") },
                            color = when { s.claimable -> a.gold; s.done -> a.green; else -> a.muted }, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}
