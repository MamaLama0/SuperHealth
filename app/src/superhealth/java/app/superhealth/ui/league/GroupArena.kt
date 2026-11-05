package app.superhealth.ui.league

import androidx.compose.foundation.Canvas
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
import kotlinx.coroutines.launch

/** Onglets du Groupe : tout part des cartes (combat, quêtes, échanges), puis le fil des publications, puis les cercles et les amis. */
enum class GroupTab(val label: String) { ARENE("Cartes"), FIL("Fil"), CERCLES("Cercles") }

@Composable
internal fun GroupTabs(tab: GroupTab, onTab: (GroupTab) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(InkCard).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        GroupTab.entries.forEach { t ->
            val on = t == tab
            Text(tr(t.label), color = if (on) Ink else Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) Color.White else Color.Transparent).clickable { onTab(t) }.padding(vertical = 9.dp))
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(InkCard, tint.copy(alpha = 0.55f))))
        .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(24.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AthleteCard(myFace(stats, bg), Modifier.width(112.dp).clickable { nav.go("social") }, interactive = false)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text("@" + handle(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1)
            Text(camp?.let { tr("Camp") + " " + tr(it.label) } ?: tr("Sans camp pour l'instant"), color = camp?.mid?.c() ?: Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Text(bg?.let { it.card.name + " · " + tr("Niv.") + " ${st.level(it.card)} · " + Bonus.label(it, st.level(it.card)) } ?: tr("Fond de palier : aucun bonus"),
                color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (st.packsAvailable > 0) Pill("${st.packsAvailable} " + tr(if (st.packsAvailable > 1) "packs offerts" else "pack offert"), Color(0xFF9AA8FF))
                Pill("${st.eclats} " + tr("éclats"), EclatColor)
            }
            GlowButton(tr("Ouvrir un pack"), Modifier.padding(top = 10.dp).fillMaxWidth().height(44.dp), listOf(tint, camp?.mid?.c() ?: Color(0xFF8AE0FF)),
                badge = st.packsAvailable.takeIf { it > 0 }?.toString()) { nav.go("cards/packs") }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) = Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 9.dp, vertical = 3.dp))

/** Défis du jour : les amis les plus proches de ta note d'abord, puis ta carte d'il y a quatre semaines. */
@Composable
internal fun ArenaDuels(nav: NavHostController, friends: List<League.Friend>) {
    val snap by CardStore.snapshot.collectAsState()
    val st by CardStore.state.collectAsState()
    val mine = snap?.now?.ovr ?: 0
    data class Rival(val id: String, val face: CardFace, val note: String, val friend: League.Friend? = null)
    val rivals = buildList {
        friends.filter { it.athlete?.ovr10 != null }.sortedBy { kotlin.math.abs((it.athlete!!.ovr ?: 0) - mine) }.forEach { f ->
            val gap = (f.athlete!!.ovr ?: 0) - mine
            add(Rival(f.id, CardFace(f.shown.lowercase().replace(" ", ""), f.athlete, f.cardBg?.let(Pull::of), level = f.cardLevel ?: 1),
                when { gap > 0 -> tr("devant de") + " $gap"; gap < 0 -> tr("derrière de") + " ${-gap}"; else -> tr("à égalité") }, f))
        }
        snap?.month?.takeIf { it.ovr10 != null }?.let { add(Rival("past", CardFace(handle(), it, CardStore.background()), tr("toi, il y a 4 sem."))) }
        if (st.demo) add(Rival("demo", CardFace("lohan", CardStore.Demo.rival, Pull.of("N043:BRILLANTE"), level = 3), tr("rival de démo")))
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Défis du jour"), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f), color = app.superhealth.ui.theme.Mf.colors.text)
            Text(tr("Tous les duels"), color = app.superhealth.ui.theme.Mf.colors.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { nav.go("cards/duel") })
        }
        if (rivals.isEmpty()) Text(tr("Ajoute un ami pour le défier, ou reviens après quelques séances pour affronter ta carte passée."),
            color = app.superhealth.ui.theme.Mf.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(4.dp))
        else Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rivals.forEach { r ->
                Column(Modifier.width(118.dp).clip(RoundedCornerShape(18.dp)).background(InkCard).clickable { nav.go("cards/duel?friend=${r.id}") }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    AthleteCard(r.friend?.let { friendFace(it) } ?: r.face, Modifier.fillMaxWidth(), interactive = false, animate = false)
                    Text("@" + r.face.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                    Text(r.note, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1)
                    Row(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFFE53935), Color(0xFFFF8A1F))))
                        .padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile(Icons.Rounded.CollectionsBookmark, "Album", "${Catalog.cards.count { st.owns(it) }}/${Catalog.cards.size}" + if (fusions > 0) " · $fusions " + tr(if (fusions > 1) "fusions" else "fusion") else "",
            Modifier.weight(1f), Color(0xFF9AA8FF), fusions) { nav.go("cards/album") }
        Tile(Icons.Rounded.SwapHoriz, "Échanges", tr("Avec tes amis"), Modifier.weight(1f), Color(0xFF6FD3A0), st.pendingTrades) { nav.go("cards/trades") }
    }
}

/** Bouton Combat : tuile sobre teintée du rouge des duels. */
@Composable
internal fun CombatButton(nav: NavHostController, rivals: Int) {
    val red = Color(0xFFFF5E5E)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(androidx.compose.ui.graphics.lerp(InkCard, Color(0xFFB3261E), 0.22f))
        .border(1.dp, red.copy(alpha = 0.3f), RoundedCornerShape(20.dp)).clickable { nav.go("cards/duel") }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(red.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.SportsMma, null, Modifier.size(20.dp), tint = red)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(tr("Combat"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(if (rivals > 0) "$rivals " + tr(if (rivals > 1) "adversaires disponibles" else "adversaire disponible") else tr("Contre ta carte d'il y a 4 semaines"),
                color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.5f))
    }
}

/** Onglet Cercles : cercles et défis, amis, records et partage (les quêtes sont dans l'Arène). */
@Composable
internal fun CirclesPanel(nav: NavHostController, friends: Int) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(InkCard)) {
        LinkRow(Icons.Rounded.Groups, "Cercles et défis", null) { nav.go("group/circles") }
        LinkRow(Icons.Rounded.People, "Mes amis", "$friends") { nav.go("friends") }
        LinkRow(Icons.Rounded.PersonAdd, "Ajouter un ami", tr("Code ou QR")) { nav.go("friends/add") }
        LinkRow(Icons.Rounded.EmojiEvents, "PR récents", null) { nav.go("group/records") }
        LinkRow(Icons.Rounded.Visibility, "Ce que je partage", null) { nav.go("group/sharing") }
        LinkRow(Icons.Rounded.Settings, "Réglages du Groupe", null) { nav.go("league/settings") }
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
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Color(0xFF231A05), InkCard)))
        .border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(24.dp)).clickable { nav.go("cards/quests") }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚑ " + tr("Quêtes"), color = Gold, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(if (ready > 0) "$ready " + tr(if (ready > 1) "à récupérer" else "à récupérer") else "${quests.count { it.claimed }}/${quests.size}",
                color = if (ready > 0) Ink else Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Black, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (ready > 0) Gold else Color.White.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        quests.sortedWith(compareBy<CardStore.Quest> { it.claimed }.thenByDescending { it.complete }.thenByDescending { it.challenge != null }.thenBy { it.weekly }).take(4).forEach { q ->
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr(q.title), color = Color.White.copy(alpha = if (q.claimed) 0.4f else 1f), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2)
                    q.note?.let { Text("⚔ " + tr(it), color = Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2) }
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
                        Box(Modifier.fillMaxWidth((q.progress / q.target.coerceAtLeast(1).toFloat()).coerceIn(0.03f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(q.difficulty.color.c(), Gold))))
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (q.complete && !q.claimed) Text("+${q.reward} ✦", color = Ink, fontWeight = FontWeight.Black, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Gold, Color(0xFFFFF2B0)))).clickable {
                        scope.launch { runCatching { CardStore.claim(ctx, q) }.onSuccess { Rumble.reveal(ctx) } } }.padding(horizontal = 12.dp, vertical = 7.dp))
                else Text(if (q.claimed) "✓" else "${q.progress}/${q.target}", color = if (q.claimed) Color(0xFF6FD3A0) else Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
    }
}

/**
 * Badges et séries dans l'Arène : le prochain palier de chaque type (avec ses éclats), les séries tenues et leur bonus de
 * la semaine, et un bouton qui récupère tout d'un coup. Plus secondaire que les quêtes : tout finit en éclats, donc en packs.
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
    val blue = Color(0xFF7FDBFF)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Color(0xFF0A1A2A), InkCard)))
        .border(1.dp, blue.copy(alpha = 0.3f), RoundedCornerShape(24.dp)).clickable { nav.go("social/badges") }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Badges et séries"), color = blue, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(tr("Chaque palier et chaque semaine tenue rapportent des éclats"), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
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
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
                        Box(Modifier.fillMaxWidth((value / b.threshold.toFloat()).coerceIn(0.04f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(Color(BadgeTheme.element(b.metric).mid)))
                    }
                    Text("$value/${b.threshold}" + if (b.metric == "distanceKm") " km" else "", color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("+${app.superhealth.league.Achievements.reward(b)} ✦", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        if (streaks.isNotEmpty()) Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            streaks.forEach { s ->
                Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.07f)).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    BadgeEmblem("streak:${s.track.name}", (app.superhealth.league.Achievements.STREAK_WEEKS.count { s.best >= it } - 1).coerceAtLeast(0), 28.dp, locked = s.weeks == 0)
                    Column(Modifier.padding(start = 6.dp)) {
                        Text(tr(s.track.label) + " · " + s.weeks + " " + tr("sem."), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(when { s.claimable -> "+${s.reward} ✦ " + tr("cette semaine"); s.done -> "✓ " + tr("semaine touchée"); else -> "${s.days}/${s.need} " + tr("jours cette semaine") },
                            color = when { s.claimable -> Gold; s.done -> Color(0xFF6FD3A0); else -> Color.White.copy(alpha = 0.55f) }, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}
