package app.superhealth.ui.league

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.league.*
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Un code court suffit. L'adresse de distribution reste absente de l'interface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFriendScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by League.state.collectAsState()
    var code by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(state.relayUrl) }
    var mine by remember { mutableStateOf<InvitationCodes.Invitation?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showQr by remember { mutableStateOf(false) }
    var offlineQr by remember { mutableStateOf<String?>(null) }
    var legacy by remember { mutableStateOf(false) }
    var oldInvitation by remember { mutableStateOf("") }
    fun receive(raw: String, fromScanner: Boolean = false) {
        scope.launch {
            busy = true; message = null
            try {
                val ok = if (!fromScanner && FriendCode.normalize(raw) != null) InvitationCodes.lookup(ctx, raw, server.takeIf { custom })
                    else InvitationCodes.receive(ctx, raw)
                if (!ok) message = tr("Invitation non valable.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = tr(e.message?.takeIf { !it.contains("://") && !it.contains(".") } ?: "Code indisponible. Vérifiez le code et le serveur, puis réessayez.") }
            finally { busy = false }
        }
    }
    fun publish(renew: Boolean = false) {
        scope.launch {
            busy = true; message = null
            try { mine = InvitationCodes.publish(ctx, renew) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = tr("Impossible de créer un code. Vérifiez la connexion et mettez à jour votre serveur si nécessaire.") }
            finally { busy = false }
        }
    }
    val lifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(mine?.code, lifecycle) {
        if (mine == null || !lifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return@LaunchedEffect
        while (true) {
            try { InvitationCodes.collectResponses(ctx) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
            kotlinx.coroutines.delay(15000)
        }
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { it.contents?.let { raw -> receive(raw, true) } }
    DetailScaffold(tr("Ajouter un ami"), { nav.popBackStack() }, contentSpacing = 12.dp) {
        Text(tr("Un code, et vous êtes connectés"), style = MaterialTheme.typography.headlineMedium)
        Text(tr("Saisissez le code de votre ami. SuperHealth retrouve son invitation sur le serveur."), color = Mf.colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        MfCard {
            TextInput(tr("Code de votre ami"), code, { code = it.uppercase(java.util.Locale.ROOT).take(32) }, placeholder = "ABCD-EFGH-2345")
            SwitchRow(tr("Rechercher sur un serveur personnalisé"), tr("Activez seulement si votre ami utilise son propre serveur"), custom) { custom = it }
            if (custom) TextInput(tr("Adresse du serveur personnalisé"), server, { server = it.trim() }, placeholder = "https://amis.exemple.fr")
            PillButton(tr("Rechercher mon ami"), Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !busy && FriendCode.normalize(code) != null && (!custom || Relay.validBase(server))) { receive(code) }
            TextButton(enabled = !busy, onClick = {
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                if (text.isBlank()) message = tr("Aucune invitation dans le presse-papiers.") else { code = text; receive(text) }
            }) { Text(tr("Coller une invitation")) }
        }
        Spacer(Modifier.height(14.dp))
        MfCard {
            Text(tr("Mon code ami"), style = MaterialTheme.typography.titleLarge)
            Text(tr("Valable 24 heures. Partagez-le avec les personnes que vous souhaitez ajouter."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            mine?.let { invitation ->
                Text(invitation.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Mf.colors.accent,
                    modifier = Modifier.padding(vertical = 16.dp))
                Text(tr(if (state.customRelay) "Créé sur votre serveur personnalisé" else "Créé sur le serveur SuperHealth"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("SuperHealth", invitation.text))
                    }) { Text(tr("Copier")) }
                    TextButton(onClick = { showQr = true }) { Text(tr("QR code")) }
                    TextButton(onClick = {
                        val shareText = tr("Mon code ami SuperHealth : ") + invitation.text + "\n" + invitation.link
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText), tr("Partager mon invitation")))
                    }) { Text(tr("Partager")) }
                }
                TextButton(enabled = !busy, onClick = { publish(true) }) { Text(tr("Renouveler mon code")) }
            } ?: PillButton(tr("Créer mon code"), Modifier.fillMaxWidth().padding(top = 12.dp), enabled = !busy) { publish() }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) }
        Spacer(Modifier.height(14.dp))
        GroupCard {
            SettingRow(tr("Mon QR sans serveur"), subtitle = tr("Échanger une carte directement, même sans Internet")) {
                scope.launch { offlineQr = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { League.link(League.inviteCard(ctx)) } }
            }
            RowDivider()
            SettingRow(tr("Scanner le QR d'un ami"), icon = Icons.Rounded.QrCodeScanner) {
                scanner.launch(ScanOptions().setPrompt(tr("Visez le QR code de votre ami")).setBeepEnabled(false).setOrientationLocked(false).setDesiredBarcodeFormats(ScanOptions.QR_CODE))
            }
            RowDivider()
            SettingRow(tr("Invitation d'une ancienne version"), subtitle = tr("Les anciens liens et cartes restent acceptés")) { legacy = !legacy }
            if (legacy) Column(Modifier.padding(16.dp)) {
                TextInput(tr("Invitation"), oldInvitation, { oldInvitation = it })
                TextButton(enabled = !busy && oldInvitation.isNotBlank(), onClick = { receive(oldInvitation, true) }) { Text(tr("Ajouter")) }
            }
        }
    }
    if (showQr && mine != null) ModalBottomSheet(onDismissRequest = { showQr = false }, containerColor = Mf.colors.background) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(mine!!.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            val bitmap = remember(mine!!.link) { qr(mine!!.link).asImageBitmap() }
            Box(Modifier.padding(vertical = 18.dp).widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(12.dp)) {
                Image(bitmap, tr("QR code"), Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
            }
            Text(tr("Votre ami le scanne depuis Ajouter un ami."), color = Mf.colors.textSecondary)
        }
    }
    offlineQr?.let { raw -> ModalBottomSheet(onDismissRequest = { offlineQr = null }, containerColor = Mf.colors.background) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr("Mon QR code"), style = MaterialTheme.typography.titleLarge)
            val bitmap = remember(raw) { qr(raw).asImageBitmap() }
            Box(Modifier.padding(vertical = 18.dp).widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(12.dp)) {
                Image(bitmap, tr("QR code"), Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
            }
            Text(tr("Votre ami le scanne depuis Ajouter un ami."), color = Mf.colors.textSecondary)
        }
    } }

}
