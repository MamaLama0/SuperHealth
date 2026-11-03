package app.superhealth.ui.league

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr

internal val RankGold = Color(0xFFEAB544)
internal fun rankColor(place: Int) = when (place) { 1 -> RankGold; 2 -> Color(0xFFA2B8D5); 3 -> Color(0xFFD68E62); else -> LeagueHue }

@Composable
internal fun RankMedal(place: Int, modifier: Modifier = Modifier) {
    val hue = rankColor(place)
    Box(modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(hue.copy(alpha = .17f))
        .border(1.dp, hue.copy(alpha = .35f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Text(place.toString(), color = hue.onContainer(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun RankingHero(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(LeagueHue.copy(alpha = if (Mf.colors.dark) .23f else .14f),
            RankGold.copy(alpha = if (Mf.colors.dark) .10f else .08f), Mf.colors.card)))
        .border(1.dp, LeagueHue.copy(alpha = .25f), RoundedCornerShape(28.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GameArt(GameArtKind.TROPHY, Modifier.size(32.dp))
            Text(tr("LE CLUB"), style = MaterialTheme.typography.labelLarge, color = LeagueHue.onContainer(), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        content()
    }
}

internal data class PodiumEntry(val name: String, val value: String, val place: Int, val me: Boolean)

@Composable
internal fun RankingPodium(entries: List<PodiumEntry>) {
    val top = entries.take(3)
    if (top.size < 2) return
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val order = if (top.size == 3) listOf(1, 0, 2) else listOf(1, 0)
        order.forEach { i ->
            val entry = top[i]
            val hue = rankColor(entry.place)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (entry.place == 1) GameArt(GameArtKind.TROPHY, Modifier.size(24.dp)) else Spacer(Modifier.height(12.dp))
                Box(Modifier.size(if (entry.place == 1) 44.dp else 38.dp).clip(CircleShape)
                    .background(hue.copy(alpha = .18f)).border(2.dp, hue, CircleShape), contentAlignment = Alignment.Center) {
                    Text(entry.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().heightIn(min = if (entry.place == 1) 108.dp else 88.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(Brush.verticalGradient(listOf(hue.copy(alpha = .24f), hue.copy(alpha = .07f))))
                    .padding(horizontal = 6.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("#${entry.place}", style = MaterialTheme.typography.titleLarge, color = hue.onContainer(), fontWeight = FontWeight.Black)
                    Text(if (entry.me) tr("Vous") else entry.name, style = MaterialTheme.typography.labelMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(entry.value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}
