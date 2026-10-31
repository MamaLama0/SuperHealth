package app.superhealth.ui.league

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.league.League
import app.superhealth.league.Nearby
import app.superhealth.modules.Modules
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.nutrition.ui.components.TextInput
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.ProfilePhoto
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.ui.userName
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

@Composable
private fun Initial(name: String, size: androidx.compose.ui.unit.Dp = 40.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(LeagueHue.container()), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(app.superhealth.ui.Lang.locale), style = MaterialTheme.typography.titleMedium, color = LeagueHue.onContainer())
    }
}

/**
 * Amis : ma carte, les personnes à proximité, mes amis. Partie de l'app (dès Réglages), sans serveur ni compte : l'identité est la clé du téléphone.
 * Ajouter un ami installe le plugin Classement, où tout le monde apparaît.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by League.state.collectAsState()
    val week = League.weekKey()
    var showQr by remember { mutableStateOf<String?>(null) }
    var opened by remember { mutableStateOf<League.Friend?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var nearbyOn by remember { mutableStateOf(false) }
    var nearby by remember { mutableStateOf<Nearby?>(null) }
    val name = userName() ?: "?"
    val fingerprint = remember { runCatching { League.myFingerprint() }.getOrDefault("—") }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let { message = if (League.receive(ctx, it)) null else tr("Carte non valable.") } }

    // à proximité : uniquement tant que l'option est cochée et l'écran ouvert
    LaunchedEffect(nearbyOn) {
        nearby?.stop(); nearby = null
        if (nearbyOn) runCatching { Nearby(ctx.applicationContext, League.myCard(ctx)).also { it.start() } }.onSuccess { nearby = it }
            .onFailure { message = tr("Recherche impossible sur ce réseau."); nearbyOn = false }
    }
    DisposableEffect(Unit) { onDispose { nearby?.stop() } }

    DetailScaffold(tr("Amis"), onBack = { nav.popBackStack() }) {
        MfCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val photo = ProfilePhoto.bitmap.value
                if (photo != null) Image(photo, null, Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop) else Initial(name, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(trf("Empreinte : %1\$s", fingerprint), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(tr("QR code"), Modifier.weight(1f), icon = Icons.Rounded.QrCode2) { scope.launch { runCatching { showQr = League.myCard(ctx) } } }
                PillButton(tr("Message"), Modifier.weight(1f), strong = false, icon = Icons.Rounded.Share) {
                    scope.launch {
                        runCatching {
                            val text = tr("Ajoute-moi sur SuperHealth : ouvre ce lien ou colle ce message dans Amis.") + "\n" + League.link(League.myCard(ctx))
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), tr("Partager ma carte")))
                        }
                    }
                }
            }
        }
        SectionLabel(tr("Ajouter un ami"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(tr("Scanner"), Modifier.weight(1f), strong = false, icon = Icons.Rounded.QrCodeScanner) {
                scanner.launch(ScanOptions().setPrompt(tr("Visez le QR code de votre ami")).setBeepEnabled(false).setOrientationLocked(false).setDesiredBarcodeFormats(ScanOptions.QR_CODE))
            }
            PillButton(tr("Coller"), Modifier.weight(1f), strong = false, icon = Icons.Rounded.ContentPaste) {
                val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                message = if (League.receive(ctx, clip)) null else tr("Aucune carte valable dans le presse-papiers.")
            }
        }
        Spacer(Modifier.height(10.dp))
        GroupCard {
            SwitchRow(tr("Détail par partie"), tr("Sinon, seul le total est partagé"), st.sharePillars) { League.setSharePillars(ctx, it) }
            RowDivider()
            SwitchRow(tr("À proximité, même Wi-Fi"), tr("Tant que cet écran est ouvert : met aussi à jour vos amis présents"), nearbyOn) { nearbyOn = it }
            val found = nearby?.found?.collectAsState()?.value.orEmpty().filter { !League.isFriend(it.k) }
            found.forEach { c ->
                RowDivider()
                Row(Modifier.fillMaxWidth().clickable { League.incoming.value = c }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Initial(c.n)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.n, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(trf("Empreinte : %1\$s", League.fingerprint(c.k)), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                    Text(tr("Ajouter"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.accent)
                }
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }

        SectionLabel(trf("Mes amis (%1\$s)", st.friends.size))
        if (st.friends.isEmpty()) MfCard { Text(tr("Pas encore d'amis. Montrez votre QR code ou envoyez votre carte : ils apparaîtront ici et dans le classement."), color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodyMedium) }
        else GroupCard {
            st.friends.sortedByDescending { if (it.week == week) it.score else -1 }.forEachIndexed { i, f ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().clickable { opened = f }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Initial(f.shown)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.shown, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (f.week == week) tr("À jour cette semaine") else tr("Pas encore de carte cette semaine"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
                    }
                    Text(if (f.week == week) f.score.toString() else "—", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PillButton(tr("Voir le classement"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.EmojiEvents) {
            scope.launch { if (Modules.ensure(ctx.applicationContext, Modules.LEAGUE)) nav.go("league") else message = tr("Plugin Classement injoignable : réessayez avec du réseau.") }
        }
        Text(tr("Rien ne passe par un serveur. Votre carte contient votre prénom, votre score de la semaine et son détail."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textTertiary,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 24.dp))
    }

    showQr?.let { card ->
        ModalBottomSheet(onDismissRequest = { showQr = null }, containerColor = Mf.colors.background) {
            Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                val bmp = remember(card) { qr(League.link(card)).asImageBitmap() }
                Box(Modifier.size(300.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(12.dp)) {
                    Image(bmp, tr("QR code"), Modifier.size(276.dp), filterQuality = FilterQuality.None)
                }
                Spacer(Modifier.height(12.dp))
                Text(trf("Empreinte : %1\$s", fingerprint), style = MaterialTheme.typography.titleSmall)
                Text(tr("Votre ami la compare en vous ajoutant."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
        }
    }
    opened?.let { f0 ->
        val f = st.friends.firstOrNull { it.id == f0.id } ?: f0
        var alias by remember(f.id) { mutableStateOf(f.alias ?: "") }
        var removing by remember { mutableStateOf(false) }
        ModalBottomSheet(onDismissRequest = { League.rename(ctx, f.id, alias); opened = null }, containerColor = Mf.colors.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Initial(f.shown, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.shown, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(trf("Empreinte : %1\$s", League.fingerprint(f.key)), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                    if (f.week == week) Text(f.score.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = LeagueHue.onContainer())
                }
                if (f.streak > 1) Text(trf("%1\$s semaines d'affilée", f.streak), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
                if (f.pillars.isNotEmpty() && f.week == week) {
                    Spacer(Modifier.height(10.dp))
                    League.Pillar.entries.forEachIndexed { i, p ->
                        val v = f.pillars.getOrNull(i)
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(tr(p.label), style = MaterialTheme.typography.bodySmall, maxLines = 1, color = if (v == null) Mf.colors.textTertiary else Mf.colors.text, modifier = Modifier.width(124.dp))
                            Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(Mf.colors.chip)) {
                                if (v != null && v > 0) Box(Modifier.fillMaxWidth(v / 100f).height(8.dp).clip(CircleShape).background(LeagueHue))
                            }
                            Text(v?.let { "$it %" } ?: "—", style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 8.dp).width(44.dp))
                        }
                    }
                }
                if (f.history.size > 1) {
                    SectionLabel(tr("Semaines"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        f.history.entries.sortedBy { it.key }.takeLast(6).forEach { (w, s) ->
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Mf.colors.card).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$s", style = MaterialTheme.typography.titleSmall)
                                Text(w.substringAfter('-'), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                            }
                        }
                    }
                }
                TextInput(tr("Surnom"), alias, { alias = it.take(24) }, placeholder = f.name)
                TextButton(onClick = { removing = true }) { Text(tr("Retirer"), color = Mf.colors.textSecondary) }
            }
        }
        if (removing) AlertDialog(onDismissRequest = { removing = false }, title = { Text(trf("Retirer %1\$s ?", f.shown)) },
            confirmButton = { TextButton(onClick = { League.remove(ctx, f.id); removing = false; opened = null }) { Text(tr("Retirer")) } },
            dismissButton = { TextButton(onClick = { removing = false }) { Text(tr("Annuler")) } })
    }
}
