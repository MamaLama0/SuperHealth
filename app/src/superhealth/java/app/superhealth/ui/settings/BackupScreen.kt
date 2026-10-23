package app.superhealth.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.backup.Archive
import app.superhealth.backup.BackupCrypto
import app.superhealth.backup.BackupWorker
import app.superhealth.backup.Remote
import app.superhealth.backup.Secrets
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.nutrition.ui.components.TextInput
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.localDate
import app.superhealth.ui.relativeDay
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.timeOf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sauvegarde : code de récupération, sauvegarde automatique chiffrée (Nextcloud, WebDAV ou dossier), fichier à exporter
 * ou à restaurer. Toute sauvegarde est chiffrée ; à la restauration, le code de ce téléphone est essayé d'abord.
 */
@Composable
fun BackupScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sh by vm.settings.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf(false) }
    var askCode by remember { mutableStateOf<ByteArray?>(null) }
    var pickRemote by remember { mutableStateOf<List<Remote.Entry>?>(null) }
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    fun err(e: Throwable) = trf("Erreur : %1\$s", e.message ?: e.javaClass.simpleName)

    /** Restaure ; si le code de ce téléphone n'ouvre pas le fichier (sauvegarde d'un autre téléphone), on le demande. */
    fun restore(bytes: ByteArray, code: String? = null) = scope.launch {
        busy = true
        try { toast(Archive.restore(ctx, bytes, code)); vm.bump() }
        catch (e: BackupCrypto.WrongCode) { askCode = bytes; if (code != null) toast(tr("Code incorrect")) }
        catch (e: Exception) { toast(err(e)) }
        busy = false
    }

    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Archive.MIME)) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            toast(runCatching {
                val bytes = Archive.build(ctx)
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) } }
                tr("Sauvegarde chiffrée exportée")
            }.getOrElse(::err))
            busy = false
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() } } }
                .getOrElse { toast(err(it)); return@launch }
            restore(bytes)
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        SuperPrefs.update { it.copy(backupFolder = uri.toString(), backupDest = Remote.Kind.FOLDER.name) }
    }

    DetailScaffold(tr("Sauvegarde"), onBack = { nav.popBackStack() }) {
        SectionLabel(tr("Code de récupération"))
        MfCard {
            val code = remember { Secrets.backupCode(ctx) }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Rounded.Key, null, tint = Mf.colors.accent)
                Text(if (showCode) code else code.replace(Regex("[0-9A-Z]"), "•"), Modifier.weight(1f).padding(horizontal = 10.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace), maxLines = 2)
                IconButton(onClick = { showCode = !showCode }) { Icon(Icons.Rounded.Visibility, tr("Afficher")) }
                IconButton(onClick = {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("SuperHealth", code))
                    toast(tr("Code copié"))
                }) { Icon(Icons.Rounded.ContentCopy, tr("Copier")) }
            }
            Spacer(Modifier.height(6.dp))
            Text(tr("Propre à ce téléphone, il reste le même après une réinstallation. Notez-le pour restaurer sur un autre téléphone."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }

        SectionLabel(tr("Sauvegarde automatique"))
        MfCard {
            ChipChoice(Remote.Kind.entries, Remote.kind(), { tr(it.label) }) { k ->
                if (k == Remote.Kind.FOLDER && sh.backupFolder == null) pickFolder.launch(null)
                else SuperPrefs.update { it.copy(backupDest = k.name) }
            }
            when (Remote.kind()) {
                Remote.Kind.NEXTCLOUD, Remote.Kind.WEBDAV -> {
                    Spacer(Modifier.height(8.dp))
                    val nc = Remote.kind() == Remote.Kind.NEXTCLOUD
                    var url by remember { mutableStateOf(sh.webdavUrl) }
                    var user by remember { mutableStateOf(sh.webdavUser) }
                    var pass by remember { mutableStateOf(Secrets.get(ctx, Secrets.WEBDAV_PASSWORD) ?: "") }
                    TextInput(if (nc) tr("Adresse du serveur") else tr("Adresse du dossier WebDAV"), url, { url = it.trim(); SuperPrefs.update { s -> s.copy(webdavUrl = url) } },
                        placeholder = if (nc) "https://cloud.exemple.fr" else "https://exemple.fr/dav/SuperHealth")
                    TextInput(tr("Utilisateur"), user, { user = it.trim(); SuperPrefs.update { s -> s.copy(webdavUser = user) } })
                    Secret(if (nc) tr("Mot de passe d'application") else tr("Mot de passe"), pass) { pass = it; Secrets.put(ctx, Secrets.WEBDAV_PASSWORD, it) }
                    if (nc) Text(tr("Nextcloud > Paramètres > Sécurité > Créer un mot de passe d'application. Dossier : SuperHealth."),
                        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
                }
                Remote.Kind.FOLDER -> {
                    Spacer(Modifier.height(8.dp))
                    val name = sh.backupFolder?.let { android.net.Uri.parse(it).lastPathSegment?.substringAfterLast(':') }
                    SettingRow(tr("Dossier"), name ?: tr("Choisir"), icon = Icons.Rounded.Folder) { pickFolder.launch(null) }
                    Text(tr("Téléphone, carte SD, Syncthing ou app de cloud qui ouvre ses dossiers (Nextcloud, Proton Drive, Google Drive…)."),
                        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
                }
                Remote.Kind.NONE -> {}
            }
            if (Remote.kind() != Remote.Kind.NONE) {
                Spacer(Modifier.height(12.dp))
                Text(tr("Fréquence"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                ChipChoice(listOf(0, 1, 7), sh.backupEvery, { when (it) { 0 -> tr("À la main"); 1 -> tr("Chaque jour"); else -> tr("Chaque semaine") } }) { d ->
                    SuperPrefs.update { it.copy(backupEvery = d) }
                }
                Spacer(Modifier.height(10.dp))
                Text(when {
                    busy -> tr("En cours…")
                    sh.lastBackupError != null -> trf("Échec : %1\$s", sh.lastBackupError)
                    sh.lastBackup > 0 -> trf("Dernière sauvegarde : %1\$s", relativeDay(localDate(sh.lastBackup)) + " " + timeOf(sh.lastBackup))
                    else -> tr("Aucune sauvegarde envoyée")
                }, style = MaterialTheme.typography.bodySmall, color = if (sh.lastBackupError != null && !busy) app.superhealth.ui.theme.HealthHues.Heart else Mf.colors.textSecondary)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(tr("Sauvegarder"), Modifier.weight(1f), enabled = !busy, icon = Icons.Rounded.CloudUpload) {
                        scope.launch { busy = true; runCatching { BackupWorker.now(ctx) }.onSuccess { toast(tr("Sauvegarde envoyée")) }; busy = false }
                    }
                    PillButton(tr("Restaurer"), Modifier.weight(1f), strong = false, enabled = !busy, icon = Icons.Rounded.CloudDownload) {
                        scope.launch { busy = true; runCatching { Remote.list(ctx) }.onSuccess { pickRemote = it }.onFailure { toast(err(it)) }; busy = false }
                    }
                }
            }
        }

        SectionLabel(tr("Fichier"))
        GroupCard {
            SettingRow(tr("Exporter"), subtitle = tr("Fichier chiffré : nutrition, musculation, montre, réglages"), icon = Icons.Rounded.Backup) {
                exportFile.launch(Archive.fileName())
            }
            RowDivider()
            SettingRow(tr("Restaurer un fichier"), subtitle = tr("SuperHealth (chiffré ou non), La Bouffe ou La Salle"), icon = Icons.Rounded.Restore) {
                importFile.launch(arrayOf("*/*"))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    pickRemote?.let { list ->
        AlertDialog(onDismissRequest = { pickRemote = null }, containerColor = Mf.colors.card,
            title = { Text(tr("Restaurer")) },
            text = {
                Column {
                    if (list.isEmpty()) Text(tr("Aucune sauvegarde à cette destination."), color = Mf.colors.textSecondary)
                    list.take(10).forEach { e ->
                        Text(e.name.removePrefix("superhealth-").removeSuffix(".${Archive.EXT}") + "  ·  " + (e.size / 1024) + " Ko",
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                pickRemote = null
                                scope.launch { busy = true; runCatching { Remote.download(ctx, e) }.onSuccess { restore(it) }.onFailure { toast(err(it)) }; busy = false }
                            }.padding(vertical = 10.dp, horizontal = 4.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickRemote = null }) { Text(tr("Fermer"), color = Mf.colors.text) } })
    }
    askCode?.let { bytes ->
        var code by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { askCode = null }, containerColor = Mf.colors.card,
            title = { Text(tr("Code de récupération")) },
            text = {
                Column {
                    Text(tr("Cette sauvegarde vient d'un autre téléphone : saisissez son code."), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(code, { code = it.uppercase().take(40) }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Mf.colors.accent, unfocusedBorderColor = Mf.colors.divider))
                }
            },
            confirmButton = {
                TextButton(enabled = BackupCrypto.isValidCode(code), onClick = { askCode = null; restore(bytes, code) }) { Text(tr("Restaurer"), color = Mf.colors.accent) }
            },
            dismissButton = { TextButton(onClick = { askCode = null }) { Text(tr("Annuler"), color = Mf.colors.text) } })
    }
}

/** Champ de mot de passe, masqué. */
@Composable
private fun Secret(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Mf.colors.chip).padding(horizontal = 14.dp, vertical = 12.dp)) {
            BasicTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Mf.colors.text), cursorBrush = SolidColor(Mf.colors.accent))
        }
    }
}
