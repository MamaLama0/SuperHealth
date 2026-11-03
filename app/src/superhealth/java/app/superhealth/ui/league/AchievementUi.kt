package app.superhealth.ui.league

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import app.superhealth.league.*
import app.superhealth.nutrition.ui.components.*
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf

@Composable
internal fun ProfileDecor(title: String?, badges: List<String>, compact: Boolean = false) {
    if (compact) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Achievements.find(title)?.title?.let {
                Text(tr(it), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelSmall,
                    color = Mf.colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            badges.take(3).mapNotNull(Achievements::find).forEach { badge ->
                val description = badgeLabel(badge)
                AssetImage(badge.asset, Modifier.size(24.dp).semantics { contentDescription = description }, crop = false)
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Achievements.find(title)?.title?.let { Text(tr(it), style = MaterialTheme.typography.titleSmall, color = Mf.colors.accent) }
        if (badges.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            badges.mapNotNull(Achievements::find).forEach { b ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AssetImage(b.asset, Modifier.size(56.dp), crop = false)
                    Text(badgeLabel(b), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun AchievementProfileEditor() {
    val ctx = LocalContext.current
    val state by League.state.collectAsState()
    val result by ProgressRepository.current.collectAsState()
    var category by remember { mutableStateOf("xp") }
    var track by remember { mutableStateOf(Progression.Track.FOOD) }
    LaunchedEffect(Unit) { runCatching { ProgressRepository.compute(ctx) } }
    MfCard {
        Text(tr("Mon profil partagé"), style = MaterialTheme.typography.titleMedium)
        Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
            Text(Achievements.find(state.profileTitle)?.title?.let { tr(it) } ?: tr("Sans titre"),
                style = MaterialTheme.typography.titleSmall, color = Mf.colors.accent,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { index ->
                val badge = state.profileBadges.getOrNull(index)?.let(Achievements::find)
                Column(Modifier.weight(1f).height(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        if (badge != null) AssetImage(badge.asset, Modifier.size(56.dp), crop = false)
                        else Text("+", style = MaterialTheme.typography.headlineSmall, color = Mf.colors.textTertiary)
                    }
                    Text(badge?.let(::badgeLabel) ?: tr("Emplacement libre"), style = MaterialTheme.typography.labelSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
        Text(tr("Choisissez un titre et jusqu'à trois badges visibles par vos amis. Vos choix sont enregistrés immédiatement."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        Text(trf("%1\$s / 3 badges affichés", state.profileBadges.size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    }
    SectionLabel(tr("Mon titre"))
    MfCard {
        val titles = listOf<String?>(null) + Achievements.catalog.filter { it.title != null && it.id in state.earnedBadges }.map { it.id }
        ChipChoice(titles, state.profileTitle, { Achievements.find(it)?.title?.let { t -> tr(t) } ?: tr("Sans titre") }) { title -> League.configure(ctx) { it.copy(profileTitle = title) } }
    }
    SectionLabel(tr("Mes badges"))
    MfCard {
        ChipChoice(listOf("xp", "sessions", "records", "foodDays", "mindMinutes", "quests", "streak"), category, { tr(when(it) {
            "xp" -> "Progression"; "sessions" -> "Musculation"; "records" -> "Records"; "foodDays" -> "Nutrition"; "mindMinutes" -> "Esprit et mobilité"; "quests" -> "Quêtes"; else -> "Séries"
        }) }) { category = it }
        if (category == "streak") ChipChoice(Progression.Track.entries, track, { tr(it.label) }) { track = it }
    }
    GroupCard {
        val visible = Achievements.catalog.filter { if (category == "streak") it.metric == "streak:${track.name}" else it.metric == category }
        visible.forEachIndexed { index, badge ->
            if (index > 0) RowDivider()
            key(badge.id) {
            val unlocked = badge.id in state.earnedBadges
            val selected = badge.id in state.profileBadges
            val value = result?.summary?.let { Achievements.value(badge, it) } ?: 0
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                AssetImage(badge.asset, Modifier.size(48.dp), crop = false)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(badgeLabel(badge), style = MaterialTheme.typography.bodyMedium)
                    Text(if (unlocked) tr("Débloqué") else "$value / ${badge.threshold}", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                }
                TextButton(enabled = unlocked && (selected || state.profileBadges.size < 3), onClick = {
                    League.configure(ctx) { s -> s.copy(profileBadges = if (selected) s.profileBadges - badge.id else (s.profileBadges + badge.id).distinct().take(3)) }
                }, modifier = Modifier.width(98.dp)) {
                    if (unlocked) Text(tr(if (selected) "Retirer" else "Afficher"), maxLines = 1)
                    else Icon(Icons.Rounded.Lock, tr("Verrouillé"), Modifier.size(18.dp))
                }
            }
            }
        }
    }
}

private fun badgeLabel(b: Achievements.Badge): String = when {
    b.title != null -> tr(b.title)
    b.metric == "sessions" -> trf(if (b.threshold == 1L) "%1\$s séance de musculation" else "%1\$s séances de musculation", b.threshold)
    b.metric == "records" -> trf(if (b.threshold == 1L) "%1\$s record sportif" else "%1\$s records sportifs", b.threshold)
    b.metric == "foodDays" -> trf("%1\$s jours de nutrition suivis", b.threshold)
    b.metric == "mindMinutes" -> trf("%1\$s minutes pour l'esprit et la mobilité", b.threshold)
    b.metric == "quests" -> trf(if (b.threshold == 1L) "%1\$s quête accomplie" else "%1\$s quêtes accomplies", b.threshold)
    b.metric.startsWith("streak:") -> {
        val track = Progression.Track.entries.first { it.name == b.metric.substringAfter(':') }
        tr(track.label) + " · " + trf(if (track.weekly) "%1\$s sem." else "%1\$s j", b.threshold)
    }
    else -> tr(b.label)
}
