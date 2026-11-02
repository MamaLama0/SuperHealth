package app.superhealth.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.ui.theme.CardRadius
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.dashCardBackground
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.ui.dayLetter
import app.superhealth.ui.league.trackingProgress
import app.superhealth.ui.league.trackingGoal
import app.superhealth.ui.league.GameArt
import app.superhealth.ui.league.GameArtKind
import java.time.LocalDate

/** Les accès sociaux restent consultables sans plugin ni configuration de serveur. */
@Composable
internal fun WProgression(size: CardSize, icon: ImageVector, modifier: Modifier, refresh: Int, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val current by ProgressRepository.current.collectAsState()
    val score by League.mine.collectAsState()
    LaunchedEffect(refresh) { runCatching { ProgressRepository.compute(ctx) } }
    val summary = current?.summary ?: score?.progress
    val hue = DashCard.PROGRESSION.hue()
    val level = summary?.level?.coerceAtLeast(1)
    val xp = summary?.totalXp ?: 0L
    val into = level?.let { (xp - Progression.threshold(it)).coerceAtLeast(0) } ?: 0L
    val span = level?.let { (Progression.threshold(it + 1) - Progression.threshold(it)).coerceAtLeast(1) } ?: 1L
    val fraction = (into.toDouble() / span).toFloat().coerceIn(0f, 1f)
    val priority = dashboardPriorityStreak(summary?.streaks.orEmpty())
    val week = summary?.weekXp
    val levelText = level?.let { trf("Niv. %1\$s", it) } ?: "—"
    val weekText = week?.let { trf("%1\$s XP cette semaine", it) } ?: tr("Chargement de la progression…")
    when (size) {
        CardSize.S -> RingTile(modifier, onClick, hue, fraction) {
            RingCenter(icon, hue, level?.toString() ?: "—", tr("Niveau"))
        }
        CardSize.M -> MediumShell(modifier, onClick, icon, tr("Progression"), hue, value = levelText, caption = weekText) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                XpBar(fraction, hue)
                StreakLine(priority, hue, short = true)
            }
        }
        CardSize.W -> Wide(modifier, onClick, icon, hue, tr("Progression"), { V(levelText, null, 20.sp) }, 126.dp) {
            Column(horizontalAlignment = Alignment.End) {
                Tq(week?.let { trf("%1\$s XP", it) } ?: "—", 13.sp, hue.onContainer(), FontWeight.SemiBold)
                XpBar(fraction, hue, Modifier.width(110.dp).padding(top = 6.dp))
            }
        }
        CardSize.L -> Large(modifier, onClick, icon, tr("Progression"), hue, week?.let { trf("%1\$s XP cette semaine", it) }) {
            V(levelText, null, 30.sp)
            Spacer(Modifier.height(8.dp))
            XpBar(fraction, hue)
            Tq(if (level == null) tr("Chargement de la progression…") else trf("%1\$s / %2\$s XP vers le niveau %3\$s", into, span, level + 1),
                12.sp, Mf.colors.textSecondary, modifier = Modifier.padding(top = 5.dp))
            Spacer(Modifier.height(12.dp))
            WeekDots(summary?.days.orEmpty().map { it.xp > 0 }.ifEmpty { List(7) { false } }, hue)
            Spacer(Modifier.height(10.dp))
            StreakLine(priority, hue)
            if (summary != null) Tq(trf("%1\$s XP au total", summary.totalXp), 12.sp, Mf.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun XpBar(fraction: Float, hue: Color, modifier: Modifier = Modifier) {
    val displayed by animateFloatAsState(fraction.coerceIn(0f, 1f), animationSpec = tween(550), label = "xp-progress")
    Box(modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Mf.colors.chip)) {
        if (displayed > 0) Box(Modifier.fillMaxWidth(displayed).fillMaxHeight().clip(CircleShape).background(hue))
    }
}

@Composable
private fun StreakLine(streak: Progression.Streak?, hue: Color, short: Boolean = false) {
    val track = streak?.track?.let { name -> Progression.Track.entries.firstOrNull { it.name == name } }
    if (streak == null || track == null) {
        Tq(tr("Vos suivis construisent vos séries"), 12.sp, Mf.colors.textSecondary)
        return
    }
    val count = streak.current.toString() + if (streak.capped) "+" else ""
    val unit = if (streak.weekly) tr("sem.") else tr("j")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(16.dp), tint = hue.onContainer())
        Text(if (short) trf("%1\$s · %2\$s %3\$s", tr(track.label), count, unit)
            else trf("%1\$s · série de %2\$s %3\$s", tr(track.label), count, unit),
            style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (!short && streak.weekly) Tq(trackingProgress(streak),
        12.sp, Mf.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
}

@Composable
internal fun WFriends(size: CardSize, icon: ImageVector, modifier: Modifier, onFriends: () -> Unit, onAdd: () -> Unit) {
    val state by League.state.collectAsState()
    val count = state.friends.size
    val hue = DashCard.FRIENDS.hue()
    when (size) {
        CardSize.S -> Column(modifier.square().clip(androidx.compose.foundation.shape.RoundedCornerShape(CardRadius))
            .dashCardBackground(hue).cardClick(onFriends).padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, null, Modifier.size(15.dp), tint = hue.onContainer())
                Tq(tr("Amis"), 12.sp, Mf.colors.textSecondary)
            }
            V(count.toString(), null, 23.sp)
            AddFriendButton(hue, Modifier.fillMaxWidth(), onAdd)
        }
        CardSize.M -> MediumShell(modifier, onFriends, icon, tr("Amis"), hue, value = count.toString(),
            caption = tr(if (count == 0) "Commencez à vous comparer" else "Comparez vos progrès")) {
            AddFriendButton(hue, Modifier.fillMaxWidth(), onAdd)
        }
        CardSize.W -> Wide(modifier, onFriends, icon, hue, tr("Amis"), { V(count.toString(), null, 20.sp) }, 118.dp) {
            AddFriendButton(hue, Modifier, onAdd)
        }
        CardSize.L -> Large(modifier, onFriends, icon, tr("Amis"), hue) {
            V(count.toString(), null, 30.sp)
            if (count == 0) Tq(tr("Ajoutez un ami pour comparer vos progrès"), 13.sp, Mf.colors.textSecondary)
            else state.friends.sortedBy { it.shown.lowercase() }.take(3).forEach { friend ->
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Mf.colors.chip), contentAlignment = Alignment.Center) {
                        Tq(friend.shown.take(1).uppercase(), 12.sp, hue.onContainer(), FontWeight.SemiBold)
                    }
                    Text(friend.shown, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    friend.progress?.let { Tq(trf("Niv. %1\$s", it.level), 12.sp, Mf.colors.textSecondary) }
                }
            }
            Spacer(Modifier.height(12.dp))
            PillButton(tr("Ajouter un ami"), Modifier.fillMaxWidth(), icon = Icons.Rounded.PersonAdd, onClick = onAdd)
            TextButton(onClick = onFriends, modifier = Modifier.align(Alignment.End)) { Text(tr("Voir mes amis"), color = hue.onContainer()) }
        }
    }
}

@Composable
private fun AddFriendButton(hue: Color, modifier: Modifier, onAdd: () -> Unit) {
    TextButton(onClick = onAdd, modifier = modifier.heightIn(min = 40.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
        Icon(Icons.Rounded.PersonAdd, tr("Ajouter un ami"), Modifier.size(18.dp), tint = hue.onContainer())
        Spacer(Modifier.width(5.dp))
        Text(tr("Ajouter"), color = hue.onContainer(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Classement d'XP réel : même modèle, même semaine, cartes récentes seulement. */
internal data class DashboardXpEntry(val name: String, val xp: Int, val me: Boolean = false)
internal fun dashboardXpEntries(summary: Progression.Summary?, friends: List<League.Friend>, nowSeconds: Long, meLabel: String): List<DashboardXpEntry> {
    if (summary == null || summary.model != Progression.MODEL) return emptyList()
    val peers = friends.filter { f -> f.time >= nowSeconds - 48 * 3600 && f.progress?.let {
        it.model == summary.model && it.weekStart == summary.weekStart
    } == true }.map { DashboardXpEntry(it.shown, it.progress!!.weekXp) }
    return (peers + DashboardXpEntry(meLabel, summary.weekXp, me = true))
        .sortedWith(compareByDescending<DashboardXpEntry> { it.xp }.thenBy { !it.me }.thenBy { it.name.lowercase() })
}
internal fun dashboardXpRank(entries: List<DashboardXpEntry>, entry: DashboardXpEntry): Int = 1 + entries.count { it.xp > entry.xp }

/** Une semaine de suivi sportif équivaut à sept jours ; en cas d'égalité, la série quotidienne est prioritaire. */
internal fun dashboardPriorityStreak(streaks: List<Progression.Streak>): Progression.Streak? = streaks
    .filter { s -> Progression.Track.entries.any { it.name == s.track } }
    .sortedWith(compareByDescending<Progression.Streak> { it.current.toLong() * if (it.weekly) 7 else 1 }.thenBy { it.weekly }.thenBy { it.track })
    .firstOrNull()

@Composable
internal fun WStreaksHero(size: CardSize, modifier: Modifier, refresh: Int, onProgress: () -> Unit, onLeague: () -> Unit, onAdd: () -> Unit) {
    val ctx = LocalContext.current
    val current by ProgressRepository.current.collectAsState()
    val mine by League.mine.collectAsState()
    val state by League.state.collectAsState()
    LaunchedEffect(refresh) { runCatching { ProgressRepository.compute(ctx) } }
    val summary = current?.summary ?: mine?.progress
    val streak = dashboardPriorityStreak(summary?.streaks.orEmpty())
    val track = streak?.track?.let { name -> Progression.Track.entries.firstOrNull { it.name == name } }
    val hue = DashCard.STREAKS.hue()
    val count = streak?.let { it.current.toString() + if (it.capped) "+" else "" } ?: "—"
    val unit = tr(if (streak?.weekly == true) { if (streak.current == 1) "semaine" else "semaines" } else if (streak?.current == 1) "jour" else "jours")
    val completed = streak != null && streak.progress >= streak.target.coerceAtLeast(1)
    val illustratedCount = (streak?.current ?: 0) in 1..999
    val status = when {
        streak == null -> tr("Choisissez les suivis de votre série")
        streak.weekly && completed -> tr("Objectif de la semaine validé")
        streak.weekly -> tr("Votre rythme, avec des jours de repos")
        completed -> tr("Série protégée aujourd'hui")
        streak.current > 0 -> tr("À protéger aujourd'hui")
        else -> tr("Un suivi à la fois pour commencer")
    }
    val level = summary?.level?.coerceAtLeast(1)
    val into = level?.let { ((summary?.totalXp ?: 0L) - Progression.threshold(it)).coerceAtLeast(0) } ?: 0L
    val span = level?.let { (Progression.threshold(it + 1) - Progression.threshold(it)).coerceAtLeast(1) } ?: 1L
    val fraction = (into.toDouble() / span).toFloat().coerceIn(0f, 1f)
    val entries = dashboardXpEntries(summary, state.friends, System.currentTimeMillis() / 1000, tr("Vous"))
    when (size) {
        CardSize.S -> RingTile(modifier, onProgress, hue, null, ring = false) {
            RingCenter(Icons.Rounded.LocalFireDepartment, hue, count, unit)
            TextButton(onClick = onLeague, contentPadding = PaddingValues(0.dp)) { Text(tr("Classement"), color = hue.onContainer(), style = MaterialTheme.typography.labelSmall) }
        }
        CardSize.M -> MediumShell(modifier, onProgress, Icons.Rounded.LocalFireDepartment, tr("Mes séries"), hue,
            value = count, unit = unit, caption = track?.let { tr(it.label) } ?: tr("Vos suivis")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLeague, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                    Text(tr("Classement"), color = hue.onContainer(), style = MaterialTheme.typography.labelMedium)
                }
                androidx.compose.material3.IconButton(onClick = onAdd) { Icon(Icons.Rounded.PersonAdd, tr("Ajouter un ami"), tint = hue.onContainer()) }
            }
        }
        CardSize.W -> Wide(modifier, onProgress, Icons.Rounded.LocalFireDepartment, hue, tr("Mes séries"), { V(count, unit, 20.sp) }, 158.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLeague, contentPadding = PaddingValues(2.dp)) { Text(tr("Classement"), color = hue.onContainer(), style = MaterialTheme.typography.labelMedium) }
                androidx.compose.material3.IconButton(onClick = onAdd) { Icon(Icons.Rounded.PersonAdd, tr("Ajouter un ami"), tint = hue.onContainer()) }
            }
        }
        CardSize.L -> Column(modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).dashCardBackground(hue).cardClick(onProgress).padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Mes séries"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (completed) Icon(Icons.Rounded.CheckCircle, tr("Objectif atteint"), Modifier.size(22.dp), tint = app.superhealth.ui.theme.Base.Green)
                else Tq(level?.let { trf("Niv. %1\$s", it) } ?: "—", 13.sp, hue.onContainer(), FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(104.dp).semantics { contentDescription = "$count $unit" }.background(Brush.radialGradient(listOf(hue.copy(alpha = 0.22f), hue.copy(alpha = 0.01f))), CircleShape), contentAlignment = Alignment.Center) {
                    GameArt(if ((streak?.current ?: 0) > 0 || completed) GameArtKind.FLAME else GameArtKind.FLAME_INACTIVE, Modifier.size(100.dp), animated = illustratedCount, count = streak?.current ?: 0)
                }
                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (!illustratedCount) Text(count, color = hue.onContainer(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold,
                            fontSize = 54.sp, lineHeight = 58.sp, modifier = Modifier.weight(1f, fill = false).alignByBaseline(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (illustratedCount) unit else " $unit", style = if (illustratedCount) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyMedium,
                            color = Mf.colors.textSecondary, modifier = Modifier.alignByBaseline())
                    }
                    Text(track?.let { tr(it.label) } ?: tr("Votre prochaine série"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 3.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Tq(level?.let { trf("Niveau %1\$s", it) } ?: "—", 12.sp, Mf.colors.textSecondary, modifier = Modifier.weight(1f))
                Tq(if (summary == null) "—" else trf("%1\$s / %2\$s XP", into, span), 12.sp, hue.onContainer(), FontWeight.SemiBold)
            }
            XpBar(fraction, app.superhealth.ui.theme.Base.Green, Modifier.padding(top = 5.dp))
            HeroXpWeek(summary, hue)
            val missions = (listOfNotNull(streak) + summary?.streaks.orEmpty().filter { it.track != streak?.track }
                .sortedWith(compareBy<Progression.Streak> { it.progress >= it.target }.thenBy { it.weekly })).take(2)
            if (missions.isNotEmpty()) {
                Tq(tr("Vos missions"), 13.sp, Mf.colors.textSecondary, FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 3.dp))
                missions.forEach { HeroMission(it, hue) }
            }
            Spacer(Modifier.height(10.dp))
            HeroXpRanking(entries, hue)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(tr("Classement"), Modifier.weight(1f), icon = Icons.Rounded.EmojiEvents, onClick = onLeague)
                AddFriendButton(hue, Modifier.weight(1f), onAdd)
            }
        }
    }
}

@Composable
private fun HeroXpWeek(summary: Progression.Summary?, hue: Color) {
    val start = summary?.weekStart?.takeIf { it > 0 } ?: Progression.week(LocalDate.now().toEpochDay())
    val days = summary?.days.orEmpty().associate { it.day to it.xp }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Tq(tr("XP de la semaine"), 12.sp, Mf.colors.textSecondary, modifier = Modifier.weight(1f))
        Tq(summary?.let { trf("%1\$s XP", it.weekXp) } ?: "—", 13.sp, hue.onContainer(), FontWeight.Bold)
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        repeat(7) { i ->
            val day = start + i; val xp = days[day] ?: 0
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Tq(dayLetter(LocalDate.ofEpochDay(day)), 10.sp, Mf.colors.textSecondary)
                Box(Modifier.padding(top = 3.dp).size(25.dp).clip(CircleShape).background(if (xp > 0) hue else Mf.colors.chip), contentAlignment = Alignment.Center) {
                    if (xp > 0) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(17.dp), tint = Color.White)
                    else Text("·", color = Mf.colors.textTertiary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun HeroMission(streak: Progression.Streak, hue: Color) {
    val track = Progression.Track.entries.firstOrNull { it.name == streak.track } ?: return
    val complete = streak.progress >= streak.target.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (complete) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            tr(if (complete) "Objectif atteint" else "Objectif en cours"), Modifier.size(21.dp),
            tint = if (complete) app.superhealth.ui.theme.Base.Green else Mf.colors.textTertiary)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Tq(tr(track.label), 13.sp, weight = FontWeight.SemiBold)
            Tq(trackingGoal(track), 11.sp, Mf.colors.textSecondary)
        }
        Tq(trackingProgress(streak), 12.sp, if (complete) app.superhealth.ui.theme.Base.Green else hue.onContainer(), FontWeight.SemiBold)
    }
}

/** Montre les premiers et ma position réelle si elle est plus loin, sans inventer d'adversaires. */
@Composable
internal fun HeroXpRanking(entries: List<DashboardXpEntry>, hue: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GameArt(GameArtKind.TROPHY, Modifier.size(28.dp))
        Tq(tr("Classement XP de la semaine"), 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = 7.dp))
    }
    if (entries.isEmpty()) {
        Tq(tr("Vos suivis alimentent votre progression"), 12.sp, Mf.colors.textSecondary, modifier = Modifier.padding(top = 5.dp))
        return
    }
    val me = entries.firstOrNull { it.me }
    val top = entries.take(3)
    val shown = if (me != null && me !in top) entries.take(2) + me else top
    shown.forEach { entry ->
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Tq(dashboardXpRank(entries, entry).toString(), 12.sp, Mf.colors.textSecondary, modifier = Modifier.width(23.dp))
            Text(entry.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = if (entry.me) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Tq(trf("%1\$s XP", entry.xp), 12.sp, hue.onContainer(), FontWeight.SemiBold)
        }
    }
    if (entries.size == 1) Tq(tr("Invitez un ami pour lancer la comparaison"), 11.sp, Mf.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
}
