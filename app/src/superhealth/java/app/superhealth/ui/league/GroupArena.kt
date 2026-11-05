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

/** Onglets du Groupe : le fil des publications d'abord, puis l'Arène des cartes, puis les cercles et les amis. */
enum class GroupTab(val label: String) { FIL("Fil"), ARENE("Arène"), CERCLES("Cercles") }

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
            Text(bg?.let { it.card.name + " · " + tr("Niv.") + " ${st.level(it.card)} · " + Bonus.label(it.card, st.level(it.card)) } ?: tr("Fond de palier : aucun bonus"),
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

/** Répartition des camps dans le Groupe (moi et les amis qui partagent leur carte), avec la stat moyenne de chaque camp. */
@Composable
internal fun ArenaCamps(friends: List<League.Friend>) {
    val snap by CardStore.snapshot.collectAsState()
    val members = buildList {
        add(campOf(CardStore.background()?.card, snap?.now) to snap?.now)
        friends.forEach { f -> f.athlete?.let { add(campOf(f.cardBg?.let(Pull::of)?.card, it) to it) } }
    }.filter { it.first != null }
    if (members.isEmpty()) return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(InkCard).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Camps du Groupe"), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("${members.size} " + tr(if (members.size > 1) "membres" else "membre"), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50))) {
            Element.entries.forEach { e -> val k = members.count { it.first == e }
                if (k > 0) Box(Modifier.weight(k.toFloat()).fillMaxHeight().background(Brush.horizontalGradient(listOf(e.outer.c(), e.mid.c())))) }
        }
        Row(Modifier.padding(top = 8.dp).fillMaxWidth()) {
            Element.entries.forEach { e ->
                val group = members.filter { it.first == e }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(22.dp)) { with(Vfx) { emblem(e, center, size.minDimension * 0.42f, if (group.isEmpty()) Color.White.copy(alpha = 0.25f) else e.mid.c()) } }
                    Text(tr(e.label), color = Color.White.copy(alpha = if (group.isEmpty()) 0.35f else 0.9f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(if (group.isEmpty()) "–" else "${group.size} · ${e.stat.code} " + group.mapNotNull { it.second?.get(e.stat) }.average().let { if (it.isNaN()) "–" else it.toInt().toString() },
                        color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                }
            }
        }
    }
}

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

/** Raccourcis vers l'album, les quêtes et la carte. */
@Composable
internal fun ArenaShortcuts(nav: NavHostController) {
    val st by CardStore.state.collectAsState()
    val claimable = remember(st.claimed) { runCatching { CardStore.quests().count { it.complete && !it.claimed } }.getOrDefault(0) }
    // Ta carte s'ouvre depuis l'en-tête de l'Arène et les duels depuis « Tous les duels » : ici, seulement ce qui manque.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile(Icons.Rounded.CollectionsBookmark, "Album", "${Catalog.cards.count { st.owns(it) }}/${Catalog.cards.size}", Modifier.weight(1f), Color(0xFF9AA8FF)) { nav.go("cards/album") }
        Tile(Icons.Rounded.Flag, "Quêtes", tr("Gagne des éclats"), Modifier.weight(1f), Gold, claimable) { nav.go("cards/quests") }
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
