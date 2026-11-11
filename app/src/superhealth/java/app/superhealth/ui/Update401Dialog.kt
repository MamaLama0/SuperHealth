package app.superhealth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.superhealth.ui.theme.Mf

@Composable
internal fun Update401Dialog(dismiss: () -> Unit) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(horizontal = 24.dp).widthIn(max = 440.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp), color = Mf.colors.card) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF421F35), Color(0xFF763148))))
                    .padding(horizontal = 22.dp, vertical = 19.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("SuperHealth 4.0.1", color = Color(0xFFF9BDCB), style = MaterialTheme.typography.labelLarge)
                    Text("Vasi c bon j'en ai marre", color = Color.White, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text(tr("Bon. J’ai refait le journal et les quêtes. Je vais m’asseoir."),
                        color = Color(0xFFFCE2E8), style = MaterialTheme.typography.bodyMedium)
                }
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    UpdateLine(Icons.Rounded.CalendarMonth, Color(0xFF217064), "Planning des repas",
                        "Planifie tes repas avec le + des jours futurs et glisse-les entre les horaires.")
                    UpdateLine(Icons.Rounded.Leaderboard, Color(0xFF69519A), "Stats sur 100",
                        "La Force reprend les notes des quatre groupes musculaires. La Constitution donne plus de poids au sommeil et à la nutrition.")
                    UpdateLine(Icons.Rounded.Toll, Color(0xFF966324), "Paquets",
                        "Les paquets s’achètent avec l’or des quêtes et des combats. Ouvre-en autant que tu veux à la suite.")
                    Button(dismiss, Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA03B56), contentColor = Color.White)) {
                        Text(tr("Compris"), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateLine(icon: ImageVector, tint: Color, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        val ink = if (Mf.colors.dark) androidx.compose.ui.graphics.lerp(tint, Color.White, .5f) else tint
        Box(Modifier.size(38.dp).background(tint.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = ink)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tr(title), style = MaterialTheme.typography.titleSmall, color = Mf.colors.text, fontWeight = FontWeight.SemiBold)
            Text(tr(detail), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
    }
}
