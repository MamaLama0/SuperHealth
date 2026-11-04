package app.superhealth.ui.league

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.navigation.NavHostController
import app.superhealth.backup.Secrets
import app.superhealth.league.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SocialLifecycleScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(SocialLifecycle.pending(ctx)) }
    var previous by remember { mutableStateOf(false) }
    fun run(operation: suspend () -> Unit) { scope.launch {
        busy = true; message = null
        try { operation() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { message = e.message?.takeIf { !it.contains("://") } ?: tr("Opération à réessayer.") }
        finally { busy = false; pending = SocialLifecycle.pending(ctx) }
    } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) run {
            withContext(Dispatchers.IO) {
                val bytes = if (previous) File(ctx.filesDir, "league/previous-group.shsocial").readBytes() else SocialLifecycle.export(ctx)
                ctx.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
            }
            message = tr("Export social chiffré enregistré. Conservez son code de récupération.")
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run {
            val bytes = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readNBytes(40 * 1024 * 1024 + 1) } }
            SocialLifecycle.importArchive(ctx, bytes, code)
            message = tr("Archive sociale restaurée.")
        }
    }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(5000); pending = SocialLifecycle.pending(ctx) } }
    DetailScaffold(tr("Mes données sociales"), { nav.popBackStack() }) {
        Text(tr("L’export chiffré contient le profil, les messages, les contacts et les clés. Les fichiers des médias distants sont référencés."))
        Button(enabled = !busy, onClick = { previous = false; export.launch("SuperHealth-social.shsocial") }) { Text(tr("Exporter mon Groupe")) }
        if (File(ctx.filesDir, "league/previous-group.shsocial").isFile) TextButton(enabled = !busy, onClick = { previous = true; export.launch("SuperHealth-ancien-Groupe.shsocial") }) { Text(tr("Exporter l’ancien Groupe")) }
        OutlinedTextField(code, { code = it.take(80) }, label = { Text(tr("Code de récupération de l’archive")) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        TextButton(enabled = !busy && !pending && code.isNotBlank(), onClick = { mode = "IMPORT" }) { Text(tr("Importer une archive sociale")) }
        if (Secrets.get(ctx, "group_restore_pending") != null) Button(enabled = !busy, onClick = { run { withContext(Dispatchers.IO) { GroupRecovery.resumeRestore(ctx) }; message = tr("Restauration terminée.") } }) { Text(tr("Terminer la restauration")) }
        if (pending) {
            Text(tr("Une révocation est en cours. Les contacts et créateurs doivent confirmer le retrait de vos anciens accès."))
            Button(enabled = !busy, onClick = { run { val done = SocialLifecycle.resume(ctx); message = tr(if (done) "Révocation terminée. Conservez le nouveau code et échangez de nouvelles invitations." else "Les confirmations distantes sont encore attendues.") } }) { Text(tr("Poursuivre la révocation")) }
        } else {
            TextButton(enabled = !busy, onClick = { mode = "SECURE" }) { Text(tr("Sécuriser après perte d’un appareil")) }
            TextButton(enabled = !busy, onClick = { mode = "DELETE" }) { Text(tr("Supprimer mon identité sociale"), color = MaterialTheme.colorScheme.error) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(tr(it)) }
    }
    mode?.let { selected -> AlertDialog(onDismissRequest = { mode = null }, title = { Text(tr("Confirmer cette opération ?")) },
        text = { Text(tr(when (selected) {
            "IMPORT" -> "Le profil social sera remplacé. Les journaux de santé sont conservés."
            "SECURE" -> "Les anciens accès et le code sont révoqués. Une nouvelle identité conserve vos publications et médias personnels. Les contacts devront échanger de nouvelles invitations."
            else -> "Les accès, publications et médias que vous hébergez seront supprimés du relais. Les contacts peuvent conserver leurs copies. Les journaux de santé sont conservés."
        })) }, confirmButton = { TextButton(enabled = !busy, onClick = {
            mode = null
            if (selected == "IMPORT") importPicker.launch(arrayOf("application/octet-stream", "application/*", "*/*"))
            else run { val done = SocialLifecycle.begin(ctx, selected); message = tr(if (done) "Opération terminée. Conservez le nouveau code de récupération." else "Opération enregistrée. Les confirmations des contacts sont attendues.") }
        }) { Text(tr("Confirmer")) } }, dismissButton = { TextButton({ mode = null }) { Text(tr("Annuler")) } }) }
}
