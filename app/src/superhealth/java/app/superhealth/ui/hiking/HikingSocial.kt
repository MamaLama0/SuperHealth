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
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf

@Composable
internal fun HikingSocial(nav:NavHostController) {
    val ctx=LocalContext.current;val scope=rememberCoroutineScope()
    val library by Hiking.library.collectAsState();val result by ProgressRepository.current.collectAsState()
    val league by League.state.collectAsState()
    LaunchedEffect(library.completed) { runCatching { ProgressRepository.compute(ctx) } }
    if(!League.available()) return
    val totals=result?.summary?.totals.orEmpty()
    MfCard {
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.EmojiEvents,null,tint=HikingHue)
            Column(Modifier.weight(1f)) {
                Text(tr("Votre carnet d’aventures"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text(trf("%1\$s randonnées · %2\$s km",totals["hikes"] ?: 0,
                    (totals["hikeMeters"] ?: 0)/1000),style=MaterialTheme.typography.bodySmall,color=Mf.colors.textSecondary)
            }
        }
        Text(tr("Une sortie validée : 10 min et 500 m, ou 30 min si la distance manque. 30 XP au plus par jour de randonnée."),style=MaterialTheme.typography.bodySmall,color=Mf.colors.textSecondary)
    }
}
