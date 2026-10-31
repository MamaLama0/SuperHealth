package app.superhealth.ui.league

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Group
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.league.League
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.coach.askCoach
import app.superhealth.ui.coach.coachAvailable
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.ui.userName
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

internal val LeagueHue = Color(0xFF4FA8E8)

internal fun qr(text: String, px: Int = 720): Bitmap {
    val m = com.google.zxing.MultiFormatWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, px, px, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    val b = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
    for (y in 0 until m.height) for (x in 0 until m.width) b.setPixel(x, y, if (m.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    return b
}

@Composable
private fun Bar(value: Int?, modifier: Modifier) {
    Box(modifier.height(8.dp).clip(CircleShape).background(Mf.colors.chip)) {
        if (value != null && value > 0) Box(Modifier.fillMaxWidth(value / 100f).height(8.dp).clip(CircleShape).background(LeagueHue))
    }
}

/** Classement : mon score et ses parties, les amis, et les trois façons d'échanger sa carte. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LeagueScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val st by League.state.collectAsState()
    val mine by League.mine.collectAsState()
    var rules by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<League.Friend?>(null) }
    LaunchedEffect(Unit) { runCatching { League.compute(ctx) } }
    val week = League.weekKey()
    val name = userName() ?: tr("Moi")

    DetailScaffold(tr("Classement"), onBack = { nav.popBackStack() }) {
        val s = mine
        MfCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(s?.total?.toString() ?: "—", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = LeagueHue.onContainer())
                Text(" / 1000", style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.weight(1f))
                League.streak().takeIf { it > 1 }?.let { Text(trf("%1\$s semaines d'affilée", it), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            League.Pillar.entries.forEach { p ->
                val v = s?.pillars?.get(p)
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(p.label), style = MaterialTheme.typography.bodySmall, color = if (v == null) Mf.colors.textTertiary else Mf.colors.text, maxLines = 1, modifier = Modifier.width(124.dp))
                    Bar(v, Modifier.weight(1f))
                    Text(v?.let { "$it %" } ?: "—", style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 8.dp).width(44.dp))
                }
            }
            Text(tr("Comment c'est calculé"), Modifier.clickable { rules = true }.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = Mf.colors.accent)
        }

        SectionLabel(tr("Cette semaine"))
        val rows = (st.friends.map { Triple(it.shown, if (it.week == week) it.score else 0, it) } + Triple(name, s?.total ?: 0, null)).sortedByDescending { it.second }
        GroupCard {
            rows.forEachIndexed { i, (n, score, f) ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().combinedClickable(onClick = { if (f != null) nav.navigate("friends") }, onLongClick = { if (f != null) removing = f }).padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = if (i == 0) LeagueHue.onContainer() else Mf.colors.textSecondary, modifier = Modifier.width(26.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (f == null) trf("%1\$s (vous)", n) else n, style = MaterialTheme.typography.bodyLarge, fontWeight = if (f == null) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (f != null && f.week != week) Text(tr("Pas encore de carte cette semaine"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
                    }
                    Text(score.toString(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (st.friends.isEmpty()) Text(tr("Échangez vos cartes pour vous comparer. Rien ne passe par un serveur."), style = MaterialTheme.typography.bodySmall,
            color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp))

        // partager sa carte et ajouter quelqu'un : tout est dans Amis
        Spacer(Modifier.height(10.dp))
        PillButton(tr("Amis"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.Group) { nav.navigate("friends") }
        if (coachAvailable()) {
            Spacer(Modifier.height(10.dp))
            PillButton(tr("Comment gagner des points ?"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.AutoAwesome) {
                val detail = mine?.pillars?.entries?.joinToString { (p, v) -> "${p.name.lowercase()}: ${v ?: "n/a"}%" }.orEmpty()
                nav.askCoach(trf("Mon score de la semaine est %1\$s/1000 (%2\$s). Quelle est l'action la plus utile d'ici dimanche, sans me surcharger ?", mine?.total ?: 0, detail))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (rules) ModalBottomSheet(onDismissRequest = { rules = false }, containerColor = Mf.colors.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Comment c'est calculé"), style = MaterialTheme.typography.titleLarge)
            listOf(
                "Musculation" to "Séances de la semaine face aux séances de votre programme (3 sans programme).",
                "Cardio" to "Minutes d'activité face à 150 min par semaine (repère de l'OMS).",
                "Pas" to "Chaque jour compte pour un septième, plafonné à votre objectif.",
                "Sommeil" to "Chaque nuit compte pour un septième, plafonnée à 7 h (repère de l'AASM).",
                "Nutrition" to "Jours journalisés à ±10 % de votre cible, sur 7.",
                "Esprit et mobilité" to "Méditation et étirements face à 70 min par semaine.",
            ).forEach { (t, d) -> Column { Text(tr(t), style = MaterialTheme.typography.titleSmall); Text(tr(d), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary) } }
            Text(tr("Total : moyenne des parties que vous suivez, sur 1000. Une partie sans source ne compte pas. Le score repart de zéro chaque lundi."), style = MaterialTheme.typography.bodyMedium)
            Text(tr("Votre carte ne contient que votre prénom, le score, la semaine et le détail si vous le laissez. Elle est signée : personne d'autre ne peut parler en votre nom. Sans serveur, rien n'empêche un ami de truquer sa propre app."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
    }
    removing?.let { f ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text(trf("Retirer %1\$s ?", f.shown)) },
            confirmButton = { TextButton(onClick = { League.remove(ctx, f.id); removing = null }) { Text(tr("Retirer")) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(tr("Annuler")) } })
    }
}

/** Confirmation d'un nouvel ami : posée quel que soit l'écran, car un lien peut ouvrir l'app n'importe quand. */
@Composable
fun LeagueIncomingDialog() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val card by League.incoming.collectAsState()
    val bad by League.incomingError.collectAsState()
    card?.let { c ->
        AlertDialog(onDismissRequest = { League.incoming.value = null },
            title = { Text(trf("Ajouter %1\$s ?", c.n)) },
            text = { Text(trf("Empreinte : %1\$s\nVérifiez-la avec cette personne. Score annoncé : %2\$s.", League.fingerprint(c.k), c.s)) },
            confirmButton = { TextButton(onClick = {
                League.accept(ctx, c); League.incoming.value = null
                // le classement est là où apparaissent les amis
                scope.launch { app.superhealth.modules.Modules.ensure(ctx.applicationContext, app.superhealth.modules.Modules.LEAGUE) }
            }) { Text(tr("Ajouter")) } },
            dismissButton = { TextButton(onClick = { League.incoming.value = null }) { Text(tr("Refuser")) } })
    }
    if (bad) AlertDialog(onDismissRequest = { League.incomingError.value = false }, text = { Text(tr("Carte non valable : elle a été modifiée ou est incomplète.")) },
        confirmButton = { TextButton(onClick = { League.incomingError.value = false }) { Text(tr("OK")) } })
}
