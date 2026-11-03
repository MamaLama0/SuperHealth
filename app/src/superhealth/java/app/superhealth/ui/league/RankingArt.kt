package app.superhealth.ui.league

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

internal data class PodiumEntry(val name: String, val value: String, val place: Int, val me: Boolean, val friend: app.superhealth.league.League.Friend? = null)

@Composable
internal fun RankingPodium(entries: List<PodiumEntry>, onFriend: (app.superhealth.league.League.Friend) -> Unit = {}) {
    val top = entries.take(3)
    if (top.size < 2) return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Mf.colors.card).padding(horizontal = 12.dp, vertical = 18.dp)) {
        Text(tr("Le podium"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val order = if (top.size == 3) listOf(1, 0, 2) else listOf(1, 0)
            order.forEach { i ->
                val entry = top[i]
                val hue = rankColor(entry.place)
                val winner = entry.place == 1
                Column(Modifier.weight(1f).clickable(enabled = entry.friend != null) { entry.friend?.let(onFriend) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (winner) { GameArt(GameArtKind.TROPHY, Modifier.size(30.dp)); Spacer(Modifier.height(8.dp)) }
                    Box(Modifier.border(if (winner) 3.dp else 2.dp, hue, CircleShape).padding(4.dp)) {
                        FriendAvatar(entry.friend, entry.name, if (winner) 60.dp else 48.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(if (entry.me) tr("Vous") else entry.name, style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.fillMaxWidth().height(if (winner) 112.dp else if (entry.place == 2) 88.dp else 72.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                        .background(Brush.verticalGradient(listOf(hue.copy(alpha = if (Mf.colors.dark) .30f else .22f), hue.copy(alpha = .07f))))
                        .padding(horizontal = 5.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween) {
                        Text(entry.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = Mf.colors.text)
                        Text(entry.place.toString(), style = if (winner) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
                            color = hue.onContainer(), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
