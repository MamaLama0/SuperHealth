package app.superhealth.ui.league

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.superhealth.nutrition.ui.components.AssetImage
import app.superhealth.ui.theme.Mf

/** Visuel local iOS, sans moteur Rive Android ; compteur lisible avec Réduire les animations. */
@Composable
internal fun StreakAnimation(count: Int,modifier: Modifier=Modifier) {
    Box(modifier,contentAlignment=Alignment.Center) {
        AssetImage("game/flame.png",Modifier.matchParentSize())
        Text(count.toString(),Modifier.align(Alignment.BottomCenter).padding(bottom=4.dp).background(Mf.colors.card,CircleShape).padding(horizontal=8.dp,vertical=1.dp),
            style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    }
}
internal fun streakColor(count: Int): Color = when {
    count>=100 -> Color(0xFF9B6DFF);count>=30 -> Color(0xFF39B9F8);count>=14 -> Color(0xFFEF5261)
    count>=7 -> Color(0xFFFFB628);else -> Color(0xFFFF7C25)
}
