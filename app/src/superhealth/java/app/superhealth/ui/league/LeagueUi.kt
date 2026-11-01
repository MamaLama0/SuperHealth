package app.superhealth.ui.league

import android.graphics.Bitmap
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.league.League
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.Ring
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.Segmented
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.ProfilePhoto
import app.superhealth.ui.coach.askCoach
import app.superhealth.ui.coach.coachAvailable
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.ui.userName
import java.time.LocalDate

internal val LeagueHue = Color(0xFF4FA8E8)

internal fun qr(text: String, px: Int = 720): Bitmap {
    val m = com.google.zxing.MultiFormatWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, px, px, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    val b = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
    for (y in 0 until m.height) for (x in 0 until m.width) b.setPixel(x, y, if (m.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    return b
}

@Composable
private fun Bar(value: Int?, modifier: Modifier) {
    Box(modifier.height(6.dp).clip(CircleShape).background(Mf.colors.chip)) {
        if (value != null && value > 0) Box(Modifier.fillMaxWidth(value / 100f).height(6.dp).clip(CircleShape).background(LeagueHue))
    }
}

/** Ligne du classement : moi ([friend] nul) ou un ami ; score nul : rien à comparer pour ce choix. */
private class Rank(val name: String, val score: Int?, val friend: League.Friend?)

@Composable
private fun Avatar(r: Rank, size: Dp) {
    val photo = if (r.friend == null) ProfilePhoto.bitmap.value else null
    if (photo != null) Image(photo, null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    else Initial(r.name, size)
}

/** Podium sur cercles concentriques, à la Rolla : le premier au centre, plus grand et plus haut ; deuxième et troisième de part et d'autre. */
@Composable
private fun Podium(top: List<Rank>, suffix: String, onOpen: (League.Friend) -> Unit) {
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.fillMaxWidth().height(200.dp).clipToBounds()) {
            val c = Offset(size.width / 2, 58.dp.toPx())
            listOf(52, 86, 124, 166).forEachIndexed { i, r ->
                drawCircle(LeagueHue.copy(alpha = 0.30f - i * 0.06f), r.dp.toPx(), c, style = Stroke(1.dp.toPx()))
            }
        }
        @Composable
        fun Place(i: Int, size: Dp, y: Dp, modifier: Modifier) {
            val r = top.getOrNull(i) ?: return Spacer(modifier)
            Column(modifier.offset(y = y).clickable(enabled = r.friend != null) { r.friend?.let(onOpen) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.padding(bottom = 10.dp).clip(CircleShape).border(2.dp, if (i == 0) LeagueHue else Mf.colors.line, CircleShape).padding(3.dp)) { Avatar(r, size) }
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (i == 0) LeagueHue else Mf.colors.chip), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (i == 0) Color.White else Mf.colors.text)
                    }
                }
                Text(if (r.friend == null) tr("Vous") else r.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Text((r.score?.toString() ?: "—") + suffix, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = LeagueHue.onContainer())
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Place(1, 56.dp, 44.dp, Modifier.weight(1f))
            Place(0, 76.dp, 4.dp, Modifier.weight(1f))
            Place(2, 56.dp, 44.dp, Modifier.weight(1f))
        }
    }
}

/** Classement : le podium, puis le reste ; mon score global et ses parties ; tous les réglages derrière un seul bouton. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeagueScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val st by League.state.collectAsState()
    val mine by League.mine.collectAsState()
    var settings by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<League.Friend?>(null) }
    var month by rememberSaveable { mutableStateOf(false) }
    var part by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { League.compute(ctx) } }
    val week = League.weekKey()
    val name = userName() ?: tr("Moi")
    val s = mine
    val pillar = League.Pillar.entries.firstOrNull { it.name == part }.takeIf { !month }
    // le mois : moyenne des 4 dernières semaines ; une semaine sans carte vaut 0
    val weeks = remember(week) { (0L until 4L).map { League.weekKey(LocalDate.now().minusWeeks(it)) } }
    val rows = remember(st, mine, month, pillar, name) {
        fun mean(scores: Map<String, Int>) = weeks.sumOf { scores[it] ?: 0 } / weeks.size
        (st.friends.map { f ->
            val value = when {
                month -> mean(f.history + (f.week to f.score))
                pillar != null -> if (f.week == week) f.pillars.getOrNull(pillar.ordinal) else null
                else -> if (f.week == week) f.score else 0
            }
            Rank(f.shown, value, f)
        } + Rank(name, when {
            month -> mean(st.best)
            pillar != null -> s?.pillars?.get(pillar)
            else -> s?.total ?: 0
        }, null)).sortedByDescending { it.score ?: -1 }
    }
    val suffix = if (pillar != null) " %" else ""

    DetailScaffold(tr("Classement"), onBack = { nav.popBackStack() }, action = {
        IconButton(onClick = { settings = true }) { Icon(Icons.Rounded.Tune, tr("Réglages du classement"), tint = Mf.colors.text) }
    }) {
        Segmented(listOf(tr("Semaine"), tr("Mois")), if (month) 1 else 0) { month = it == 1 }
        Spacer(Modifier.height(14.dp))
        Podium(rows.take(3), suffix) { opened = it }
        if (!month) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf<Pair<String?, String>>(null to "Score global").plus(League.Pillar.entries.map { it.name to it.label }).forEach { (id, label) ->
                    val on = id == part
                    Text(tr(label), style = MaterialTheme.typography.labelLarge, maxLines = 1, color = if (on) LeagueHue.onContainer() else Mf.colors.text,
                        modifier = Modifier.clip(CircleShape).background(if (on) LeagueHue.container() else Mf.colors.chip).clickable { part = id }.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (rows.size > 3) {
            GroupCard {
                rows.drop(3).forEachIndexed { i, r ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().clickable(enabled = r.friend != null) { opened = r.friend }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 4}", style = MaterialTheme.typography.titleSmall, color = Mf.colors.textSecondary, modifier = Modifier.width(26.dp))
                        Avatar(r, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(if (r.friend == null) trf("%1\$s (vous)", r.name) else r.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (r.friend == null) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text((r.score?.toString() ?: "—") + suffix, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (st.friends.isEmpty()) {
            PillButton(tr("Ajouter un ami"), Modifier.fillMaxWidth(), icon = Icons.Rounded.PersonAdd) { nav.navigate("friends") }
            Spacer(Modifier.height(10.dp))
        }
        // mon score global : l'anneau et les six parties sur deux colonnes
        MfCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring((s?.total ?: 0) / 1000f, LeagueHue, 72.dp, stroke = 7.dp) {
                    Text(s?.total?.toString() ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Score global"), style = MaterialTheme.typography.titleMedium)
                    Text(listOfNotNull(tr("Sur 1000, repart lundi"), League.streak().takeIf { it > 1 }?.let { trf("%1\$s semaines d'affilée", it) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            League.Pillar.entries.chunked(2).forEach { pair ->
                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { p ->
                        val v = s?.pillars?.get(p)
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(tr(p.label), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (v == null) Mf.colors.textTertiary else Mf.colors.text, modifier = Modifier.weight(1f))
                                Text(v?.let { "$it %" } ?: "—", style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, textAlign = TextAlign.End)
                            }
                            Spacer(Modifier.height(3.dp))
                            Bar(v, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        if (coachAvailable()) {
            Spacer(Modifier.height(10.dp))
            PillButton(tr("Comment gagner des points ?"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.AutoAwesome) {
                val detail = mine?.pillars?.entries?.joinToString { (p, v) -> "${p.name.lowercase()}: ${v ?: "n/a"}%" }.orEmpty()
                nav.askCoach(trf("Mon score de la semaine est %1\$s/1000 (%2\$s). Quelle est l'action la plus utile d'ici dimanche, sans me surcharger ?", mine?.total ?: 0, detail))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    opened?.let { f -> FriendSheet(f) { opened = null } }
    if (settings) ModalBottomSheet(onDismissRequest = { settings = false }, containerColor = Mf.colors.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Text(tr("Réglages du classement"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
            SectionLabel(tr("Amis"))
            GroupCard {
                SettingRow(tr("Mes amis"), st.friends.size.toString()) { settings = false; nav.navigate("friends") }
            }
            SectionLabel(tr("Ce que je partage"))
            GroupCard {
                SwitchRow(tr("Détail par partie"), tr("Sinon, seul le total est partagé"), st.sharePillars) { League.setSharePillars(ctx, it) }
            }
            SectionLabel(tr("Calcul"))
            GroupCard {
                SettingRow(tr("Comment c'est calculé")) { settings = false; rules = true }
            }
        }
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
            Text(tr("Score global : moyenne des parties que vous suivez, sur 1000. Une partie sans source ne compte pas. Le score repart de zéro chaque lundi. Le mois : moyenne des 4 dernières semaines."), style = MaterialTheme.typography.bodyMedium)
            Text(tr("Votre carte ne contient que votre prénom, le score, la semaine et le détail si vous le laissez. Elle est signée : personne d'autre ne peut parler en votre nom. Sans serveur, rien n'empêche un ami de truquer sa propre app."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
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
