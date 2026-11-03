package app.superhealth.ui.hiking

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.hiking.Hiking
import app.superhealth.league.*
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.launch

@Composable
internal fun HikingSocial(nav:NavHostController) {
    val ctx=LocalContext.current;val scope=rememberCoroutineScope()
    val library by Hiking.library.collectAsState();val result by ProgressRepository.current.collectAsState()
    val league by League.state.collectAsState()
    LaunchedEffect(library.completed) { runCatching { ProgressRepository.compute(ctx) } }
    if(!League.available()) return
    val totals=result?.summary?.totals.orEmpty()
    val badges=Achievements.catalog.filter{it.metric in setOf("hikes","hikeMeters","hikeAscent","streak:HIKE")}
    val earned=badges.count{it.id in league.earnedBadges}
    MfCard {
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.EmojiEvents,null,tint=HikingHue)
            Column(Modifier.weight(1f)) {
                Text(tr("Votre carnet d’aventures"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text(trf("%1\$s randonnées · %2\$s km · %3\$s badges",totals["hikes"] ?: 0,
                    (totals["hikeMeters"] ?: 0)/1000,earned),style=MaterialTheme.typography.bodySmall,color=Mf.colors.textSecondary)
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick={nav.go("social/badges?category=hiking")}){Icon(Icons.Rounded.MilitaryTech,null);Text(tr("Mes badges"))}
            TextButton(onClick={
                if(WeeklyQuests.Focus.HIKING !in league.questFocus.orEmpty()) League.configure(ctx){it.copy(questFocus=it.questFocus.orEmpty()+WeeklyQuests.Focus.HIKING)}
                scope.launch { ProgressRepository.compute(ctx);nav.go("quests") }
            }){Icon(Icons.Rounded.Flag,null);Text(tr("Mes quêtes randonnée"))}
        }
        if(WeeklyQuests.Focus.HIKING in league.questFocus.orEmpty() && league.questWeeks.any { it.start==Progression.week(java.time.LocalDate.now().toEpochDay()) && it.quests.none { q -> q.metric==WeeklyQuests.Metric.HIKE } }) {
            Text(tr("Le domaine randonnée sera pris en compte aux prochaines quêtes. Les récompenses en cours restent conservées."),style=MaterialTheme.typography.bodySmall,color=Mf.colors.textSecondary)
        }
        Text(tr("Une sortie validée : 10 min et 500 m, ou 30 min si la distance manque. 30 XP au plus par jour de randonnée."),style=MaterialTheme.typography.bodySmall,color=Mf.colors.textSecondary)
    }
}
