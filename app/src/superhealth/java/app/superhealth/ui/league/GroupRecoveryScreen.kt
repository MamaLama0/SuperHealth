package app.superhealth.ui.league

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import app.superhealth.backup.Secrets
import app.superhealth.league.GroupRecovery
import app.superhealth.league.Relay
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.tr
import kotlinx.coroutines.launch

@Composable fun GroupRecoveryScreen(nav: NavHostController) { GroupRecoveryContent { nav.popBackStack() } }
@Composable fun GroupRecoveryDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface { GroupRecoveryContent(onClose) }
    }
}
@Composable private fun GroupRecoveryContent(onClose: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }; var server by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }; var confirm by remember { mutableStateOf(false) }
    DetailScaffold(tr("Retrouver mon Groupe"), onClose) {
        Text(tr("Profil, amis, titres et badges : une copie chiffrée, sans vos journaux de santé."))
        val code = remember { Secrets.backupCode(ctx) }
        Text(if (show) code else "•••• •••• •••• •••• •••• •••• •••• ••••", style = MaterialTheme.typography.titleMedium)
        Row {
            TextButton({ show = !show }) { Text(tr(if (show) "Masquer" else "Afficher le code")) }
            TextButton({
                val clip = ClipData.newPlainText("SuperHealth", code)
                if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                message = tr("Code copié : gardez-le hors de votre téléphone.")
            }) { Text(tr("Copier")) }
        }
        Text(tr("Ce code donne accès à votre identité du Groupe. Gardez-le dans votre gestionnaire de mots de passe."), style = MaterialTheme.typography.bodySmall)
        Button(enabled = !busy, onClick = { scope.launch {
            busy = true
            try { Relay.publishOnly(ctx); GroupRecovery.upload(ctx); message = tr("Groupe protégé. Conservez votre code de récupération.") }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { message = tr("Protection du Groupe indisponible. Mettez le serveur à jour.") }
            finally { busy = false }
        } }) { Text(tr("Protéger mon Groupe maintenant")) }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(tr("Changer de téléphone"), style = MaterialTheme.typography.titleLarge)
        Text(tr("Saisissez le code conservé sur votre ancien appareil. Les données de santé déjà sur ce téléphone ne seront pas remplacées."), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(input, { input = it.take(80) }, label = { Text(tr("Code de récupération")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(server, { server = it.trim() }, label = { Text(tr("Serveur personnalisé (facultatif)")) }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && input.isNotBlank(), onClick = { confirm = true }) { Text(tr("Retrouver mon Groupe")) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it) }
        Text(tr("Si l'ancien téléphone est perdu, renouvelez ensuite vos liens de partage dans les paramètres du Groupe et transmettez une nouvelle invitation à vos amis."), style = MaterialTheme.typography.bodySmall)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(tr("Retrouver ce Groupe ?")) },
        text = { Text(tr("Le profil du Groupe et sa liste d'amis remplaceront ceux de cette installation. Vos journaux de santé restent conservés.")) },
        confirmButton = { TextButton({ confirm = false; scope.launch {
            busy = true
            try { GroupRecovery.recover(ctx, input, server); message = tr("Groupe retrouvé. Vos publications se chargent progressivement.") }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { message = tr("Groupe introuvable. Vérifiez le code et le serveur.") }
            finally { busy = false }
        } }) { Text(tr("Retrouver")) } }, dismissButton = { TextButton({ confirm = false }) { Text(tr("Annuler")) } })
}
