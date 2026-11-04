package app.superhealth.ui.league

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import app.superhealth.ui.settings.IconSwitchRow
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
    if(SocialLifecycle.pending(ctx)){Text(tr("Terminez la sécurisation du Groupe avant un nouveau partage."));return}
    val scope = rememberCoroutineScope()
    val state by League.state.collectAsState()
    var custom by remember { mutableStateOf(state.customRelay) }
    var url by remember { mutableStateOf(state.relayUrl) }
    var code by remember { mutableStateOf(Secrets.get(ctx, "league_publish_key").orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun sync() = scope.launch { busy = true; try { Relay.sync(ctx) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { error = tr("Synchronisation indisponible. Réessayez plus tard.") }; busy = false }
    // état et actualisation d'abord, puis la fréquence ; le serveur personnalisé reste replié en bas
    GroupCard {
        SettingRow(tr("Actualiser maintenant"), when {
            busy -> tr("Synchronisation…")
            state.lastSync > 0 -> android.text.format.DateUtils.getRelativeTimeSpanString(state.lastSync, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
            else -> null
        }, subtitle = tr("Cartes chiffrées de vos amis"), icon = Icons.Rounded.Sync) { if (state.relayEnabled && !busy) sync() }
        RowDivider(56.dp)
        IconSwitchRow(tr("Utiliser le serveur"), tr("Relais chiffré SuperHealth ou le vôtre"), Icons.Rounded.Cloud, null, state.relayEnabled) { on ->
            League.configure(ctx) { it.copy(relayEnabled = on) }; LeagueWorker.schedule(ctx)
        }
        RowDivider(56.dp)
        IconSwitchRow(tr("Actualiser à l'ouverture"), tr("Amis connus sur le Wi-Fi, puis le relais toutes les 10 min au plus"), Icons.Rounded.Wifi, null, state.refreshOnOpen) { on ->
            League.configure(ctx) { it.copy(refreshOnOpen = on) }
        }
    }
    (error ?: state.syncError)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp)) }
    GroupHeader(tr("Fréquence"), Modifier.padding(start = 0.dp))
    MfCard {
        ChipChoice(listOf(0, 2, 4), state.syncsPerDay, { when (it) { 0 -> tr("Manuel"); 2 -> tr("2 fois / jour"); else -> tr("4 fois / jour") } }) { frequency ->
            League.configure(ctx) { it.copy(syncsPerDay = frequency) }; LeagueWorker.schedule(ctx)
        }
        Text(tr("Avec les notifications sociales actives, le mode automatique relève les nouveautés environ toutes les 15 min. En mode Manuel, ouvrez le fil pour actualiser. Android peut décaler ces passages pour économiser la batterie."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
    }
    GroupHeader(tr("Serveur personnalisé"), Modifier.padding(start = 0.dp))
    GroupCard {
        SwitchRow(tr("Utiliser mon serveur"), tr("Facultatif"), custom) { custom = it }
        if (custom) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(tr("Adresse du relais"), url, { url = it.trim() }, placeholder = "https://amis.exemple.fr")
            OutlinedTextField(code, { code = it }, label = { Text(tr("Code de publication du relais")) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Text(tr("Le code de publication est facultatif si votre serveur n'en exige pas."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
    }
    if (custom || custom != state.customRelay) PillButton(tr("Enregistrer"), Modifier.fillMaxWidth(), enabled = !busy) {
        if (custom && (!Relay.validBase(url) || (code.isNotBlank() && code.length !in 32..512))) error = tr("Saisissez une adresse HTTPS sans chemin et un code de publication de 32 caractères minimum.")
        else {
            Secrets.put(ctx, "league_publish_key", code)
            League.configure(ctx) { it.copy(customRelay = custom, relayUrl = if (custom) url.trimEnd('/') else "", syncError = null) }
            LeagueWorker.schedule(ctx)
            error = null
            sync()
        }
    }
}
