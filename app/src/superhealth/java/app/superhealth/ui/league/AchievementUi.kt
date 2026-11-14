package app.superhealth.ui.league

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.cards.CardStore
import app.superhealth.league.*
import app.superhealth.ui.cards.*
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.launch

/**
 * Page des badges : tout finit en or. En tête, ce qu'il reste à récupérer ; puis les séries de la semaine et leur
 * bonus qui grandit ; puis chaque type de badge sur le fond de son biome, avec son palier actuel, le prochain et
 * l'échelle complète de ses paliers.
 */
@Composable
fun ProfileBadgesScreen(nav: NavHostController, initialCategory: String = "") {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by CardStore.state.collectAsState()
    val league by League.state.collectAsState()
    val progress by ProgressRepository.current.collectAsState()
    LaunchedEffect(Unit) { CardStore.load(ctx); runCatching { ProgressRepository.compute(ctx) } }
    val payout = remember(st.badgesPaid, st.streakPaid, st.demo, league.earnedBadges, progress) { runCatching { CardStore.payout() }.getOrNull() }
    val earned = league.earnedBadges.toSet()
    val summary = progress?.summary
    var busy by remember { mutableStateOf(false) }
    val types = BADGE_TYPES
    CardScaffold("Badges", { nav.popBackStack() }, Color(0xFF2F6FDE)) {
        // En tête : paliers atteints, or déjà gagnés, et le bouton qui récupère le reste.
        val visible = Achievements.catalog
        val won = visible.filter { it.id in st.badgesPaid }.sumOf(Achievements::reward)
        val total = payout?.let { p -> p.badges.sumOf(Achievements::reward) + p.streaks.filter { it.claimable }.sumOf { it.reward } } ?: 0
        Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1B2C5A), Color(0xFF2A1240))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp)).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${visible.count { it.id in earned }}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 40.sp)
                    Text(trf("paliers sur %1\$s", visible.size), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$won ✦", color = Gold, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    Text(tr("or gagné en badges"), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
            Text(tr("Chaque palier rapporte de l'or une seule fois, de plus en plus haut. Chaque semaine où une série tient, son bonus grandit de 10 or."),
                color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            if (total > 0) GlowButton(if (busy) "…" else trf("Récupérer +%1\$s or", total), Modifier.fillMaxWidth().padding(top = 14.dp), listOf(Gold, Color(0xFFFFB238))) {
                if (!busy) { busy = true; scope.launch { runCatching { CardStore.collect(ctx) }.onSuccess { Rumble.reveal(ctx) }; busy = false } }
            }
        }

        payout?.streaks?.takeIf { it.isNotEmpty() }?.let { streaks ->
            Section(tr("Séries de la semaine"), tr("Une semaine compte dès 4 jours sur 7 (ou ton objectif de séances). +10 or par semaine d'affilée, jusqu'à 100. Les paliers de série paient en plus : 200 à 10 semaines, 250 à 30."))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(InkCard), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                streaks.forEach { s -> StreakRow(s, earned) }
            }
        }

        Section(tr("Paliers"), tr("Un palier n'est payé qu'une fois"))
        types.forEach { (metric, label) ->
            val tiers = Achievements.catalog.filter { it.metric == metric }
            if (tiers.isNotEmpty()) TypeCard(label, tiers, earned, st.badgesPaid, summary?.let { s -> Achievements.value(tiers.first(), s) } ?: 0L)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Types de badges affichés, dans l'ordre des biomes : Magma, Glacier, Tempête, Montagne. */
private val BADGE_TYPES = listOf(
    "sessions" to "Musculation", "records" to "Records", "mindMinutes" to "Esprit et mobilité",
    "quests" to "Quêtes", "distanceKm" to "Distance parcourue", "foodDays" to "Nutrition",
)

@Composable
private fun Section(title: String, detail: String) = Column(Modifier.padding(top = 22.dp, bottom = 10.dp)) {
    Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
    Text(detail, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
}

private fun amount(metric: String, n: Long) = when (metric) {
    "distanceKm" -> "$n km"; "mindMinutes" -> "$n min"
    "foodDays" -> "$n j"; else -> "$n"
}

@Composable
private fun TypeCard(label: String, tiers: List<Achievements.Badge>, earned: Set<String>, paid: Set<String>, value: Long) {
    val metric = tiers.first().metric
    val e = BadgeTheme.element(metric)
    val reached = tiers.count { it.id in earned }
    val next = tiers.firstOrNull { it.id !in earned }
    val current = tiers.lastOrNull { it.id in earned }
    val deep = Color(e.outer).copy(alpha = 0.35f).compositeOver(InkCard)
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(24.dp))
        .background(Brush.verticalGradient(listOf(deep, InkCard))).border(1.dp, Color(e.mid).copy(alpha = 0.25f), RoundedCornerShape(24.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BadgeEmblem(current ?: tiers.first(), 64.dp, locked = current == null)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(tr(label), color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text(tr(e.label) + " · " + trf("palier %1\$s/%2\$s", reached, tiers.size), color = Color(e.mid), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(tr(BadgeTheme.explain(metric)), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                if (next != null) {
                    Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
                        Box(Modifier.fillMaxWidth((value / next.threshold.toFloat()).coerceIn(0.03f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(Color(e.mid), Color(e.core)))))
                    }
                    Row(Modifier.padding(top = 4.dp)) {
                        Text(amount(metric, value) + " / " + amount(metric, next.threshold), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text("+${Achievements.reward(next)} ✦", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                } else Text(tr("Tous les paliers atteints"), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp))
            }
        }
        // L'échelle complète : chaque palier, son seuil et son or.
        Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            tiers.forEach { b ->
                val on = b.id in earned
                Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BadgeEmblem(b, 42.dp, locked = !on)
                    Text(amount(metric, b.threshold), color = Color.White.copy(alpha = if (on) 0.9f else 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
                    Text(when { b.id in paid -> "✓"; on -> "+${Achievements.reward(b)}"; else -> "${Achievements.reward(b)}" },
                        color = when { b.id in paid -> Color(0xFF6FD3A0); on -> Gold; else -> Color.White.copy(alpha = 0.35f) }, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun StreakRow(s: CardStore.StreakBonus, earned: Set<String>) {
    val metric = "streak:${s.track.name}"
    val tiers = Achievements.catalog.filter { it.metric == metric }
    val next = tiers.firstOrNull { it.id !in earned }
    val color = Color(BadgeTheme.element(metric).mid)
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        BadgeEmblem(metric, (tiers.count { it.id in earned } - 1).coerceAtLeast(0), 44.dp, locked = s.weeks == 0)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(tr(s.track.label) + " · " + trf(if (s.weeks > 1) "%1\$s semaines" else "%1\$s semaine", s.weeks), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(trf("%1\$s/%2\$s cette semaine", s.days.coerceAtMost(s.need), s.need) + " · " + trf("record %1\$s sem.", s.best) +
                (next?.let { " · " + trf("palier %1\$s sem. : +%2\$s", it.threshold, Achievements.reward(it)) } ?: ""),
                color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 2)
            // Jours tenus cette semaine, puis le bonus des semaines d'affilée (10, 20… 100).
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(s.need) { d -> Box(Modifier.size(width = 14.dp, height = 6.dp).clip(RoundedCornerShape(50)).background(if (d < s.days) color else Color.White.copy(alpha = 0.1f))) }
            }
            Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..CardStore.STREAK_WEEKS).forEach { w ->
                    val on = w <= s.weeks
                    Text("${CardStore.streakReward(w)}", color = if (on) Ink else Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) color else Color.White.copy(alpha = 0.06f)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
        Text(when { s.claimable -> "+${s.reward} ✦"; s.done -> "✓"; else -> "—" },
            color = when { s.claimable -> Gold; s.done -> Color(0xFF6FD3A0); else -> Color.White.copy(alpha = 0.3f) }, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}
