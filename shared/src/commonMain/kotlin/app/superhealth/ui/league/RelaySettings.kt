package app.superhealth.ui.league

import app.superhealth.platform.*

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    var custom by remember { mutableStateOf(state.customRelay) }
    var url by remember { mutableStateOf(state.relayUrl) }
    var code by remember { mutableStateOf(Secrets.get(ctx, "league_publish_key").orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    SectionLabel(tr("Mises à jour à distance"))
    MfCard {
        Text(tr("Le serveur SuperHealth actualise les cartes chiffrées de vos amis. Vous pouvez aussi utiliser votre propre serveur."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        SwitchRow(tr("Actualiser à l'ouverture"), tr("Cherche les amis connus sur le Wi-Fi et actualise le relais ; tentatives distantes espacées de 10 min"), state.refreshOnOpen) { on -> League.configure(ctx) { it.copy(refreshOnOpen = on) } }
        SwitchRow(tr("Utiliser le serveur"), null, state.relayEnabled) { on -> League.configure(ctx) { it.copy(relayEnabled = on) }; LeagueWorker.schedule(ctx) }
        SwitchRow(tr("Serveur personnalisé"), tr("Facultatif"), custom) { custom = it }
        if (custom) {
        TextInput(tr("Adresse du relais"), url, { url = it.trim() }, placeholder = "https://amis.exemple.fr")
        OutlinedTextField(code, { code = it }, label = { Text(tr("Code de publication du relais")) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Text(tr("Le code de publication est facultatif si votre serveur n'en exige pas."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        Spacer(Modifier.height(8.dp))
        ChipChoice(listOf(0, 2, 4), state.syncsPerDay, { when (it) { 0 -> tr("Manuel"); 2 -> tr("2 fois / jour"); else -> tr("4 fois / jour") } }) { frequency ->
            League.configure(ctx) { it.copy(syncsPerDay = frequency) }; LeagueWorker.schedule(ctx)
        }
        Text(tr("iOS décide quand actualiser les données en arrière-plan."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textTertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(tr("Enregistrer"), Modifier.weight(1f), enabled = !busy) {
                if (custom && (!Relay.validBase(url) || (code.isNotBlank() && code.length !in 32..512))) error = tr("Saisissez une adresse HTTPS sans chemin et un code de publication de 32 caractères minimum.")
                else {
                    Secrets.put(ctx, "league_publish_key", code)
                    League.configure(ctx) { it.copy(customRelay = custom, relayUrl = if (custom) url.trimEnd('/') else "", syncError = null) }
                    LeagueWorker.schedule(ctx)
                    error = null
                    scope.launch { busy = true; try { Relay.sync(ctx) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { error = tr("Synchronisation indisponible. Réessayez plus tard.") }; busy = false }
                }
            }
            PillButton(tr("Actualiser"), Modifier.weight(1f), strong = false, enabled = state.relayEnabled && !busy) {
                scope.launch { busy = true; try { Relay.sync(ctx) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { error = tr("Synchronisation indisponible. Réessayez plus tard.") }; busy = false }
            }
        }
        (error ?: state.syncError)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (busy) Text(tr("Synchronisation…"), style = MaterialTheme.typography.bodySmall)
        if (state.lastSync > 0) Text(tr("Dernier passage : ") + app.superhealth.platform.Instant.ofEpochMilli(state.lastSync).atZone(app.superhealth.platform.ZoneId.systemDefault()).toLocalDateTime().toString().replace('T', ' '), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
    }
}
