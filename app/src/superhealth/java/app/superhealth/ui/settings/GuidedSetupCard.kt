package app.superhealth.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr

/** Même porte d'entrée pour les deux parcours ; les réglages précis restent juste en dessous. */
@Composable
fun GuidedSetupCard(title: String, description: String, onStart: () -> Unit) {
    MfCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(30.dp), tint = Mf.colors.accent)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
            }
        }
        Spacer(Modifier.height(14.dp))
        PillButton(tr("Me laisser guider"), Modifier.fillMaxWidth(), onClick = onStart)
    }
}
