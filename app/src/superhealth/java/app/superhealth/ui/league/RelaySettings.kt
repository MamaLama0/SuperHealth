package app.superhealth.ui.league

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.superhealth.backup.Secrets
import app.superhealth.league.*
import app.superhealth.nutrition.ui.components.*
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import kotlinx.coroutines.launch

@Composable
internal fun RelaySettings() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by League.state.collectAsState()
    var url by remember { mutableStateOf(state.relayUrl) }
    var code by remember { mutableStateOf(Secrets.get(ctx, "league_publish_key").orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    SectionLabel(tr("Mises à jour à distance"))
    MfCard {
        Text(tr("Votre relais personnel conserve uniquement des cartes chiffrées. Configurez-le puis échangez vos invitations. Le QR et la proximité restent utilisables sans relais."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        SwitchRow(tr("Actualiser à l'ouverture"), tr("Cherche les amis connus sur le Wi-Fi et actualise le relais ; tentatives distantes espacées de 10 min"), state.refreshOnOpen) { on -> League.configure(ctx) { it.copy(refreshOnOpen = on) } }
        TextInput(tr("Adresse du relais"), url, { url = it.trim() }, placeholder = "https://amis.exemple.fr")
        OutlinedTextField(code, { code = it }, label = { Text(tr("Code de publication du relais")) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        ChipChoice(listOf(0, 2, 4), state.syncsPerDay, { when (it) { 0 -> tr("Manuel"); 2 -> tr("2 fois / jour"); else -> tr("4 fois / jour") } }) { frequency ->
            League.configure(ctx) { it.copy(syncsPerDay = frequency) }; LeagueWorker.schedule(ctx)
        }
        Text(tr("Android peut décaler ces passages pour économiser la batterie."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textTertiary)
        SwitchRow(tr("Partager activités, courses et PR"), tr("Totaux et records, sans parcours GPS"), state.shareActivities) { on -> League.configure(ctx) { it.copy(shareActivities = on) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(tr("Enregistrer"), Modifier.weight(1f), enabled = !busy) {
                if (url.isNotBlank() && (!Relay.validBase(url) || code.length !in 32..512)) error = tr("Saisissez une adresse HTTPS sans chemin et un code de publication de 32 caractères minimum.")
                else {
                    Secrets.put(ctx, "league_publish_key", code)
                    League.configure(ctx) { it.copy(relayUrl = url.trimEnd('/'), syncError = null) }
                    LeagueWorker.schedule(ctx)
                    error = null
                    scope.launch { busy = true; try { Relay.sync(ctx) } catch (e: Exception) { error = e.message }; busy = false }
                }
            }
            PillButton(tr("Actualiser"), Modifier.weight(1f), strong = false, enabled = !busy) {
                scope.launch { busy = true; try { Relay.sync(ctx) } catch (e: Exception) { error = e.message }; busy = false }
            }
        }
        (error ?: state.syncError)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (busy) Text(tr("Synchronisation…"), style = MaterialTheme.typography.bodySmall)
        if (state.lastSync > 0) Text(tr("Dernier passage : ") + java.time.Instant.ofEpochMilli(state.lastSync).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString().replace('T', ' '), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
    }
}
