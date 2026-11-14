package app.superhealth.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.CardSize
import app.superhealth.DashCard
import app.superhealth.league.League
import app.superhealth.league.Progression
import app.superhealth.league.ProgressRepository
import app.superhealth.league.WeeklyQuests
import app.superhealth.ui.league.GameArt
import app.superhealth.ui.league.GameArtKind
import app.superhealth.ui.theme.CardRadius
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.dashCardBackground
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.time.LocalDate

/** Une carte = un sujet et une destination. Les petits formats ne contiennent aucune action imbriquée. */
@Composable
internal fun SocialCard(size: CardSize, modifier: Modifier, icon: ImageVector, title: String, hue: Color,
                        onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(CardRadius)
    Column(modifier.then(if (size == CardSize.S) Modifier.square() else Modifier.fillMaxWidth())
        .then(if (size == CardSize.M) Modifier.heightIn(min = MEDIUM_H) else Modifier)
        .clip(shape).dashCardBackground(hue).cardClick(onClick).padding(if (size == CardSize.S) 10.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (size == CardSize.S) 6.dp else 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(if (size == CardSize.S) 15.dp else 18.dp), tint = hue.onContainer())
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (size != CardSize.S) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(16.dp), tint = Mf.colors.textTertiary)
        }
        content()
    }
}

@Composable
internal fun SocialValue(value: String, caption: String, hue: Color, small: Boolean = false) {
    Text(value, color = hue.onContainer(), fontWeight = FontWeight.Bold, fontSize = if (small) 26.sp else 32.sp,
        lineHeight = if (small) 30.sp else 38.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(caption, style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
        color = Mf.colors.textSecondary, maxLines = if (small) 1 else 2, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun SocialBar(fraction: Float, hue: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Mf.colors.chip)) {
        if (fraction > 0) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(hue))
    }
}

@Composable
internal fun SocialStrip(modifier: Modifier, icon: ImageVector, title: String, detail: String, value: String, hue: Color, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).dashCardBackground(hue).cardClick(onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = hue.onContainer())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, style = MaterialTheme.typography.titleLarge, color = hue.onContainer(), fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp))
    }
}

@Composable
internal fun WProgression(size: CardSize, icon: ImageVector, modifier: Modifier, refresh: Int, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val current by ProgressRepository.current.collectAsState()
    LaunchedEffect(refresh) { runCatching { ProgressRepository.compute(ctx) } }
    val p = current?.summary
    val hue = DashCard.PROGRESSION.hue()
    val level = p?.let { trf("Niv. %1\$s", it.level) } ?: "—"
    val into = p?.let { it.totalXp - Progression.threshold(it.level) } ?: 0L
    val span = p?.let { Progression.threshold(it.level + 1) - Progression.threshold(it.level) } ?: 1L
    val week = p?.let { trf("%1\$s XP cette semaine", it.weekXp) } ?: tr("Chargement de la progression…")
    if (size == CardSize.W) SocialStrip(modifier, icon, tr("Progression"), week, level, hue, onClick)
    else SocialCard(size, modifier, icon, tr("Progression"), hue, onClick) {
        SocialValue(if (size == CardSize.S) p?.level?.toString() ?: "—" else level,
            if (size == CardSize.S) tr("Niveau") else week, hue, size == CardSize.S)
        if (size != CardSize.S) {
            SocialBar(into.toFloat() / span.coerceAtLeast(1), hue)
            if (p != null) Text(trf("%1\$s XP avant le niveau %2\$s", (span - into).coerceAtLeast(0), p.level + 1),
                style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
        }
    }
}

@Composable
internal fun WFriends(size: CardSize, icon: ImageVector, modifier: Modifier, onFriends: () -> Unit) {
    val state by League.state.collectAsState()
    val hue = DashCard.FRIENDS.hue()
    val count = state.friends.size
    val caption = tr(if (count == 0) "Ajouter un ami" else "Voir mes amis")
    if (size == CardSize.W) SocialStrip(modifier, icon, tr("Amis"), caption, count.toString(), hue, onFriends)
    else SocialCard(size, modifier, icon, tr("Amis"), hue, onFriends) {
        SocialValue(count.toString(), caption, hue, size == CardSize.S)
        if (size != CardSize.S && count > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.friends.sortedBy { it.shown.lowercase() }.take(if (size == CardSize.M) 3 else 5).forEach { friend ->
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Mf.colors.chip), contentAlignment = Alignment.Center) {
                        Text(friend.shown.take(1).uppercase(), style = MaterialTheme.typography.labelMedium, color = hue.onContainer())
                    }
                }
            }
        }
    }
}

internal data class DashboardScoreEntry(val name: String, val score: Int, val me: Boolean = false)
internal fun dashboardRank(entries: List<DashboardScoreEntry>, entry: DashboardScoreEntry) = 1 + entries.count { it.score > entry.score }

/** Une semaine de suivi sportif équivaut à sept jours ; en cas d'égalité, la série quotidienne est prioritaire. */
internal fun dashboardPriorityStreak(streaks: List<Progression.Streak>): Progression.Streak? = streaks
    .filter { s -> Progression.Track.entries.any { it.name == s.track } }
    .sortedWith(compareByDescending<Progression.Streak> { it.current.toLong() * if (it.weekly) 7 else 1 }.thenBy { it.weekly }.thenBy { it.track })
    .firstOrNull()

@Composable
internal fun WStreaksHero(size: CardSize, modifier: Modifier, refresh: Int, onProgress: () -> Unit) {
    val ctx = LocalContext.current
    val current by ProgressRepository.current.collectAsState()
    LaunchedEffect(refresh) { runCatching { ProgressRepository.compute(ctx) } }
    val streak = dashboardPriorityStreak(current?.summary?.streaks.orEmpty())
    val hue = app.superhealth.ui.league.streakColor(streak?.current ?: 0)
    val count = streak?.let { it.current.toString() + if (it.capped) "+" else "" } ?: "—"
    val unit = tr(if (streak?.weekly == true) "sem." else "j")
    val track = streak?.track?.let { id -> Progression.Track.entries.firstOrNull { it.name == id } }
    val label = track?.let { tr(it.label) } ?: tr("Choisir mes suivis")
    val completed = streak != null && streak.progress >= streak.target.coerceAtLeast(1)
    val status = tr(when {
        completed && streak?.weekly == true -> "Objectif de la semaine validé"
        completed -> "Série protégée aujourd'hui"
        streak?.weekly == true -> "Votre rythme, avec des jours de repos"
        (streak?.current ?: 0) > 0 -> "À protéger aujourd'hui"
        else -> "Un suivi à la fois pour commencer"
    })
    if (size == CardSize.W) SocialStrip(modifier, Icons.Rounded.LocalFireDepartment, tr("Mes séries"), label, "$count $unit", hue, onProgress)
    else SocialCard(size, modifier, Icons.Rounded.LocalFireDepartment, tr("Mes séries"), hue, onProgress) {
        if (size == CardSize.L) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GameArt(if ((streak?.current ?: 0) > 0 || completed) GameArtKind.FLAME else GameArtKind.FLAME_INACTIVE,
                    Modifier.size(76.dp), animated = (streak?.current ?: 0) in 1..999, count = streak?.current ?: 0)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    SocialValue("$count $unit", label, hue)
                }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        } else {
            SocialValue(if (size == CardSize.S) count else "$count $unit",
                if (size == CardSize.S) tr(if (streak?.weekly == true) "semaines" else "jours") else label,
                hue, size == CardSize.S)
            if (size == CardSize.M) Text(status, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
        }
    }
}

internal fun dashboardScoreEntries(score: League.Score?, friends: List<League.Friend>, nowSeconds: Long, meLabel: String): List<DashboardScoreEntry> {
    if (score?.ready != true) return emptyList()
    return (friends.filter { score.comparable(it, nowSeconds) }.map { DashboardScoreEntry(it.shown, it.score) } +
        DashboardScoreEntry(meLabel, score.total, true)).sortedWith(compareByDescending<DashboardScoreEntry> { it.score }.thenBy { !it.me }.thenBy { it.name.lowercase() })
}

/** Une quête à reprendre ou une récompense à récupérer, sans répéter tout l'écran des quêtes. */
@Composable
internal fun WQuests(size: CardSize, icon: ImageVector, modifier: Modifier, refresh: Int, onClick: () -> Unit) {
    // Les quêtes des cartes Athlète : celles du jour et de la semaine, qui rapportent l'or des packs.
    val ctx = LocalContext.current
    val st by app.superhealth.cards.CardStore.state.collectAsState()
    LaunchedEffect(refresh) { app.superhealth.cards.CardStore.load(ctx) }
    val quests = remember(st.claimed, st.quests, st.demo, refresh, app.superhealth.league.League.state.collectAsState().value.questWeeks) { runCatching { app.superhealth.cards.CardStore.quests() }.getOrDefault(emptyList()) }
    val quest = quests.firstOrNull { it.complete && !it.claimed } ?: quests.firstOrNull { !it.complete } ?: quests.firstOrNull()
    val ready = quests.count { it.complete && !it.claimed }
    val hue = DashCard.QUESTS.hue()
    val title = quest?.let { tr(it.title) } ?: tr("Tes quêtes arrivent")
    val progress = if (ready > 0) trf("%1\$s à récupérer", ready) else "${quests.count { it.claimed || it.complete }} / ${quests.size}"
    if (size == CardSize.W) SocialStrip(modifier, icon, tr("Quêtes"), title, progress, hue, onClick)
    else SocialCard(size, modifier, icon, tr("Quêtes"), hue, onClick) {
        SocialValue(progress, if (size == CardSize.S) tr("Aujourd'hui") else title, hue, size == CardSize.S)
        if (size != CardSize.S && quest != null) {
            SocialBar(quest.progress.toFloat() / quest.target.coerceAtLeast(1), hue)
            Text(if (quest.complete && !quest.claimed) tr("Récompense à récupérer") else "${quest.progress} / ${quest.target} · " + app.superhealth.cards.CardStore.prize(quest),
                style = MaterialTheme.typography.labelSmall, color = hue.onContainer())
        }
    }
}

/** La récompense prête à être récupérée est prioritaire, puis la prochaine quête non terminée. */
internal fun dashboardQuest(quests: List<WeeklyQuests.Quest>): WeeklyQuests.Quest? =
    quests.firstOrNull { it.complete && !it.claimed } ?: quests.firstOrNull { !it.complete } ?: quests.firstOrNull()
