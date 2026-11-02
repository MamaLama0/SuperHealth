package app.superhealth.ui.league

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import app.superhealth.league.League
import app.superhealth.league.Nearby
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** L'appareil photo et le presse-papiers ne sont consultés qu'après l'action correspondante. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFriendScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showQr by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    var proximity by remember { mutableStateOf(false) }
    var nearbyOn by remember { mutableStateOf(false) }
    var nearby by remember { mutableStateOf<Nearby?>(null) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { text -> message = if (League.receive(ctx, text)) null else tr("Carte non valable.") }
    }
    fun receive(text: String) {
        message = if (League.receive(ctx, text)) null else tr("Aucune carte valable dans ce texte.")
    }
    fun invitation(action: (String) -> Unit) {
        scope.launch {
            try { action(withContext(Dispatchers.IO) { League.inviteCard(ctx.applicationContext) }) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = tr("Invitation indisponible. Réessayez.") }
        }
    }

    // Rien n'est annoncé au réseau avant le choix explicite. Fermeture et passage en arrière-plan arrêtent le port.
    LaunchedEffect(nearbyOn, visible) {
        if (!nearbyOn || !visible) return@LaunchedEffect
        var peer: Nearby? = null
        try {
            val activePeer = Nearby.create(ctx.applicationContext)
            peer = activePeer
            withContext(Dispatchers.IO) { activePeer.start() }
            nearby = activePeer
            awaitCancellation()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = tr("Recherche impossible sur ce réseau."); nearbyOn = false }
        finally { peer?.stop(); if (nearby === peer) nearby = null }
    }

    DetailScaffold(tr("Ajouter un ami"), { nav.popBackStack() }) {
        Spacer(Modifier.height(12.dp))
        Text(tr("Ensemble, suivez vos progrès."), style = MaterialTheme.typography.headlineSmall)
        Text(tr("Scannez son code ou envoyez-lui votre invitation."), style = MaterialTheme.typography.bodyMedium,
            color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
        PillButton(tr("Scanner le QR d'un ami"), Modifier.fillMaxWidth(), icon = Icons.Rounded.QrCodeScanner) {
            scanner.launch(ScanOptions().setPrompt(tr("Visez le QR code de votre ami")).setBeepEnabled(false)
                .setOrientationLocked(false).setDesiredBarcodeFormats(ScanOptions.QR_CODE))
        }
        Spacer(Modifier.height(16.dp))
        GroupCard {
            SettingRow(tr("Mon QR code"), subtitle = tr("À montrer à votre ami"), icon = Icons.Rounded.QrCode2) { invitation { showQr = it } }
            RowDivider()
            SettingRow(tr("Partager mon invitation"), subtitle = tr("Par le moyen de votre choix"), icon = Icons.Rounded.Share) {
                invitation { card ->
                    val text = tr("Ajoute-moi sur SuperHealth : ouvre ce lien ou colle ce message dans Amis.") + "\n" + League.link(card)
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), tr("Partager mon invitation")))
                }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp)) }
        Spacer(Modifier.height(20.dp))
        GroupCard {
            SettingRow(tr("J'ai reçu un lien ou un code"), subtitle = tr("Coller une invitation"), icon = Icons.Rounded.ContentPaste) { more = !more }
            if (more) {
                RowDivider()
                Column(Modifier.padding(16.dp)) {
                    TextInput(tr("Invitation"), pasted, { pasted = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                        TextButton(onClick = {
                            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            pasted = clipboard.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                            if (pasted.isBlank()) message = tr("Aucune invitation dans le presse-papiers.")
                        }) { Text(tr("Coller")) }
                        TextButton(onClick = { receive(pasted) }, enabled = pasted.isNotBlank()) { Text(tr("Ajouter")) }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        GroupCard {
            SettingRow(tr("Amis à proximité"), subtitle = tr("Autre méthode, sur le même Wi-Fi"), icon = Icons.Rounded.NearMe) {
                proximity = !proximity
                if (!proximity) nearbyOn = false
            }
            if (proximity) {
                RowDivider()
                SwitchRow(tr("Chercher sur ce Wi-Fi"), tr("Seulement tant que cet écran est visible"), nearbyOn) { nearbyOn = it }
                val found = nearby?.found?.collectAsState()?.value.orEmpty().filter { !League.isFriend(it.k) }
                if (nearbyOn && found.isEmpty()) Text(tr("Recherche d'invitations à proximité…"), style = MaterialTheme.typography.bodySmall,
                    color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                found.forEach { card ->
                    RowDivider()
                    SettingRow(card.n, value = tr("Ajouter")) { League.incoming.value = card }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    showQr?.let { card ->
        ModalBottomSheet(onDismissRequest = { showQr = null }, containerColor = Mf.colors.background,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("Mon QR code"), style = MaterialTheme.typography.titleLarge)
                Text(tr("Votre ami le scanne depuis Ajouter un ami."), color = Mf.colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                val bitmap = remember(card) { qr(League.link(card)).asImageBitmap() }
                Box(Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(12.dp)) {
                    Image(bitmap, tr("QR code"), Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
                }
                Text(trf("Empreinte : %1\$s", League.myFingerprint()), style = MaterialTheme.typography.bodySmall,
                    color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
