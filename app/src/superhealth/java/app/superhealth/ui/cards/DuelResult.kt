package app.superhealth.ui.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.superhealth.cards.CharacterCombat
import app.superhealth.cards.CharacterAttribute
import app.superhealth.cards.Stat
import app.superhealth.nutrition.ui.components.NavBar
import app.superhealth.ui.tr
import java.text.NumberFormat

@Composable
internal fun DuelResult(battle: CharacterCombat.Battle, opponent: String, onBack: () -> Unit, replay: () -> Unit) {
    val won = battle.winner > 0
    val tied = battle.winner == 0
    val accent = if (won) Color(0xFFFFD677) else if (tied) Color(0xFFADCCEB) else Color(0xFFFF988A)
    val deep = if (won) Color(0xFF453820) else if (tied) Color(0xFF243D50) else Color(0xFF4B252E)
    val ink = Color(0xFF101923)
    val muted = Color(0xFFB9C5D0)
    val number = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 } }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance by animateFloatAsState(if (entered) 1f else 0f, tween(280), label = "duel-result")
    Column(Modifier.fillMaxSize().background(ink).statusBarsPadding().navigationBarsPadding()) {
        NavBar("Le bilan du duel", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(deep, deep.copy(alpha = .35f))))
                .border(1.dp, accent.copy(alpha = .4f), RoundedCornerShape(24.dp)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (won) Icons.Rounded.EmojiEvents else if (tied) Icons.Rounded.Balance else Icons.Rounded.Shield,
                    null, Modifier.size(48.dp).graphicsLayer { scaleX = .65f + entrance * .35f; scaleY = scaleX; alpha = entrance }, tint = accent)
                Text(tr(if (won) "VICTOIRE" else if (tied) "ÉGALITÉ" else "DÉFAITE"), color = accent,
                    style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                Text(tr(if (won) "Tu remportes ce duel !" else if (tied) "Même énergie, même score." else "La revanche se prépare à l’entraînement."),
                    color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            Text(tr("Vie restante"), color = muted, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ResultScore(tr("Toi"), number.format(battle.myHealth / 100.0), won, tied, accent, Modifier.weight(1f))
                ResultScore(opponent, number.format(battle.theirHealth / 100.0), !won && !tied, tied, accent, Modifier.weight(1f))
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF1A2733)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Les échanges"), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Dégâts infligés"), Modifier.weight(1f), color = muted, style = MaterialTheme.typography.labelSmall)
                    Text(tr("Toi"), Modifier.width(64.dp), color = muted, textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
                    Text(opponent, Modifier.width(74.dp), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
                }
                battle.exchanges.forEach { e ->
                    val ahead = e.myDamage.compareTo(e.theirDamage)
                    val rowTint = if (ahead > 0) Color(0xFF9BDBBB) else if (ahead < 0) Color(0xFFFFB3A5) else muted
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(rowTint.copy(alpha = .07f)).padding(vertical = 9.dp, horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(when(e.stat) {
                            CharacterAttribute.FOR -> Icons.Rounded.FitnessCenter
                            CharacterAttribute.DEX -> Icons.AutoMirrored.Rounded.DirectionsRun
                            CharacterAttribute.CON -> Icons.Rounded.Shield
                        }, null, Modifier.size(18.dp), tint = rowTint)
                        Spacer(Modifier.width(7.dp))
                        Text(tr(e.stat.label), Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Text(number.format(e.myDamage / 100.0), Modifier.width(57.dp), color = if (ahead >= 0) rowTint else muted,
                            textAlign = TextAlign.End, style = MaterialTheme.typography.titleSmall, fontWeight = if (ahead >= 0) FontWeight.Black else FontWeight.Normal)
                        Text(number.format(e.theirDamage / 100.0), Modifier.width(67.dp), color = if (ahead <= 0) rowTint else muted,
                            textAlign = TextAlign.End, style = MaterialTheme.typography.titleSmall, fontWeight = if (ahead <= 0) FontWeight.Black else FontWeight.Normal)
                    }
                }
            }
            val key = if (won) battle.exchanges.maxByOrNull { it.myDamage - it.theirDamage }
                else battle.exchanges.minByOrNull { it.myDamage - it.theirDamage }
            if (!tied && key != null) Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (won) Icons.Rounded.Star else Icons.AutoMirrored.Rounded.TrendingUp, null, Modifier.size(23.dp), tint = accent)
                Column {
                    Text(tr(if (won) "Ton point fort" else "Pour la revanche"), color = accent, style = MaterialTheme.typography.labelLarge)
                    Text(tr(key.stat.label), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(2.dp))
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onBack, Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                Text(tr("Terminer"), color = Color.White)
            }
            Button(replay, Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = ink)) {
                Icon(Icons.Rounded.Replay, null, Modifier.size(19.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr(if (won || tied) "Rejouer" else "Revanche"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ResultScore(name: String, value: String, winner: Boolean, tied: Boolean, accent: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(if (winner) accent.copy(alpha = .15f) else Color(0xFF1A2733))
        .border(if (winner) 2.dp else 1.dp, if (winner) accent else Color(0xFF35424E), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(name, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (winner) Icon(Icons.Rounded.EmojiEvents, tr("Vainqueur"), Modifier.size(18.dp), tint = accent)
        }
        Text(value, color = if (winner || tied) accent else Color(0xFFB9C5D0), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Text(tr("PV sur 100"), color = Color(0xFFB9C5D0), style = MaterialTheme.typography.labelSmall)
    }
}
