package app.superhealth.ui.league

import android.graphics.Bitmap
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
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
private class Rank(val name: String, val score: Double?, val friend: League.Friend?)

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
                        Text("${top.indexOfFirst { it.score == r.score } + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (i == 0) Color.White else Mf.colors.text)
                    }
                }
                Text(if (r.friend == null) tr("Vous") else r.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Text((r.score?.let { app.superhealth.nutrition.ui.num(it, if (it % 1.0 == 0.0) 0 else 1) } ?: "—") + suffix, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = LeagueHue.onContainer())
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
    val sh by app.superhealth.SuperPrefs.settings.collectAsState()
    if (!sh.leagueEnabled) {
        DetailScaffold(tr("Classement et progression"), onBack = { nav.popBackStack() }) {
            Text(tr("Le classement est désactivé. Vos amis et vos données sont conservés."))
            PillButton(tr("Activer le classement"), Modifier.fillMaxWidth()) { app.superhealth.SuperPrefs.update { it.copy(leagueEnabled = true) } }
        }
        return
    }
    val st by League.state.collectAsState()
    val mine by League.mine.collectAsState()
    var mode by rememberSaveable { mutableStateOf("XP") }
    val xpMode = mode == "XP"
    var opened by remember { mutableStateOf<League.Friend?>(null) }
    var month by rememberSaveable { mutableStateOf(false) }
    var part by rememberSaveable { mutableStateOf<String?>(null) }
    var metricKey by rememberSaveable { mutableStateOf<String?>(null) }
    var metricPicker by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { runCatching { League.compute(ctx) } }
    val name = userName() ?: tr("Moi")
    val s = mine
    val endDay = s?.endDay ?: LocalDate.now().minusDays(1).toEpochDay()
    val recent = System.currentTimeMillis() / 1000 - 48 * 3600
    val pillar = League.Pillar.entries.firstOrNull { it.name == part }.takeIf { !month && metricKey == null }
    val metrics = (s?.metrics.orEmpty() + st.friends.flatMap { it.metrics }).distinctBy { it.key }.sortedBy { it.label }
    val metric = metrics.firstOrNull { it.key == metricKey }.takeIf { !month }
    fun mean(scores: Map<String, Int>): Double? = scores.mapNotNull { (key, value) ->
        key.removePrefix("v2:").substringBefore(':').toLongOrNull()?.takeIf { key.startsWith("v2:") && key.substringAfterLast(':').toIntOrNull() == s?.coverage && it in (endDay - 27)..endDay }?.let { value.toDouble() }
    }.takeIf { it.isNotEmpty() }?.average()
    val rows = (st.friends.map { f ->
        val compatible = f.model == app.superhealth.league.HealthIndex.MODEL && f.endDay == endDay && f.time >= recent
        val value: Double? = when {
            xpMode -> f.progress?.takeIf { it.model == app.superhealth.league.Progression.MODEL && it.weekStart == s?.progress?.weekStart && f.time >= recent }?.weekXp?.toDouble()
            month -> mean(f.history)
            metric != null -> f.metrics.firstOrNull { it.key == metric.key && it.unit == metric.unit && it.scope == metric.scope && it.lowerWins == metric.lowerWins }
                ?.takeIf { f.metricsTime >= recent && (it.scope == "all" || (f.metricsEndDay == endDay && f.model == app.superhealth.league.HealthIndex.MODEL)) }?.value
            pillar != null -> if (compatible) f.pillars.getOrNull(pillar.ordinal)?.toDouble() else null
            else -> if (compatible && f.ready && f.coverage == s?.coverage) f.score.toDouble() else null
        }
        Rank(f.shown, value, f)
    } + Rank(name, when {
        xpMode -> s?.progress?.weekXp?.toDouble()
        month -> mean(st.best)
        metric != null -> s?.metrics?.firstOrNull { it.key == metric.key }?.value
        pillar != null -> s?.pillars?.get(pillar)?.toDouble()
        else -> s?.takeIf { it.ready }?.total?.toDouble()
    }, null)).sortedWith(compareBy<Rank> { it.score == null }.thenBy { if (metric?.lowerWins == true) it.score ?: 0.0 else -(it.score ?: 0.0) })
    val ranked = rows.filter { it.score != null }
    val missing = rows.filter { it.score == null }
    val suffix = if (xpMode) " XP" else if (pillar != null) " %" else metric?.let { " ${it.unit}" } ?: ""

    DetailScaffold(tr("Classement"), onBack = { nav.popBackStack() }, action = {
        IconButton(onClick = { nav.navigate("friends/add") }) { Icon(Icons.Rounded.PersonAdd, tr("Ajouter un ami")) }
        IconButton(onClick = { nav.navigate("league/settings") }) { Icon(Icons.Rounded.Tune, tr("Réglages du classement"), tint = Mf.colors.text) }
    }) {
        Segmented(listOf(tr("XP semaine"), tr("Indice de santé"), tr("Performances")), listOf("XP", "HEALTH", "PERF").indexOf(mode)) {
            mode = listOf("XP", "HEALTH", "PERF")[it]; metricKey = null; part = null; month = false
            if (mode == "PERF") metricPicker = true
        }
        if (xpMode) {
            val myXp = s?.progress?.weekXp
            val myRank = ranked.indexOfFirst { it.score == myXp?.toDouble() }.takeIf { myXp != null && it >= 0 }?.plus(1)
            val above = ranked.lastOrNull { it.friend != null && it.score != null && it.score > (myXp ?: 0) }
            MfCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Illustrated badge is shared with the dashboard and the progression path.
                    GameArt(GameArtKind.TROPHY, Modifier.size(84.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("Défi de la semaine"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(trf("%1\$s XP", myXp ?: 0), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = LeagueHue.onContainer())
                        Text(if (myRank != null && ranked.size > 1) trf("Place %1\$s sur %2\$s", myRank, ranked.size) else tr("Invitez vos amis dans la course !"), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                above?.let { r -> Text(trf("Écart avec %1\$s : %2\$s XP", r.name, ((r.score ?: 0.0) - (myXp ?: 0)).toInt()), style = MaterialTheme.typography.labelLarge, color = LeagueHue.onContainer()) }
                Text(trf("Nouvelle semaine dans %1\$s jours", (app.superhealth.league.Progression.week(LocalDate.now().toEpochDay()) + 7 - LocalDate.now().toEpochDay())), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                Spacer(Modifier.height(12.dp))
                PillButton(tr("Ajouter un ami"), Modifier.fillMaxWidth(), icon = Icons.Rounded.PersonAdd) { nav.navigate("friends/add") }
            }
            SettingRow(tr("Mes séries et mes niveaux"), subtitle = tr("Missions, jalons et évolution de mes performances")) { nav.navigate("progress") }
        }
        if (mode == "HEALTH") Segmented(listOf(tr("7 jours"), tr("28 jours")), if (month) 1 else 0) { month = it == 1 }
        Text(if (xpMode) tr("XP de la semaine en cours, du lundi au dimanche. Seules les cartes récentes de la même semaine sont comparées.") else if (month) tr("Moyenne des indices disponibles sur 28 jours ; les jours absents restent absents.") else
            trf("Du %1\$s au %2\$s · cartes de moins de 48 h · même couverture", app.superhealth.nutrition.ui.screens.fmtDate(endDay - 6), app.superhealth.nutrition.ui.screens.fmtDate(endDay)),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
        if (!month && !xpMode) SettingRow(tr("Comparer"), metric?.label ?: if (pillar == null) tr("Indice global de santé") else tr(pillar.label)) { metricPicker = true }
        Spacer(Modifier.height(14.dp))
        if (ranked.isNotEmpty()) Podium(ranked.take(3), suffix) { opened = it }
        else MfCard { Text(tr("Pas encore de données comparables"), color = Mf.colors.textSecondary) }
        if (!month && !xpMode) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf<Pair<String?, String>>(null to "Indice global de santé").plus(League.Pillar.entries.map { it.name to it.label }).forEach { (id, label) ->
                    val on = id == part
                    Text(tr(label), style = MaterialTheme.typography.labelLarge, maxLines = 1, color = if (on) LeagueHue.onContainer() else Mf.colors.text,
                        modifier = Modifier.clip(CircleShape).background(if (on) LeagueHue.container() else Mf.colors.chip).clickable { part = id; metricKey = null }.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (ranked.size > 3) {
            GroupCard {
                ranked.drop(3).forEachIndexed { i, r ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().clickable(enabled = r.friend != null) { opened = r.friend }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${ranked.indexOfFirst { it.score == r.score } + 1}", style = MaterialTheme.typography.titleSmall, color = Mf.colors.textSecondary, modifier = Modifier.width(26.dp))
                        Avatar(r, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(if (r.friend == null) trf("%1\$s (vous)", r.name) else r.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (r.friend == null) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text((r.score?.let { app.superhealth.nutrition.ui.num(it, if (it % 1.0 == 0.0) 0 else 1) } ?: "—") + suffix, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (missing.isNotEmpty()) {
            SectionLabel(tr("À actualiser ou non comparables"))
            GroupCard {
                missing.forEachIndexed { i, row ->
                    if (i > 0) RowDivider()
                    SettingRow(if (row.friend == null) tr("Vous") else row.name, "—", subtitle = tr("Données insuffisantes, période ou parties différentes")) { row.friend?.let { opened = it } }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        run {
            if (!xpMode) PillButton(tr("Ajouter un ami"), Modifier.fillMaxWidth(), icon = Icons.Rounded.PersonAdd) { nav.navigate("friends/add") }
            Spacer(Modifier.height(10.dp))
        }
        // mon score global : l'anneau et les six parties sur deux colonnes
        if (!xpMode) MfCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring((s?.total ?: 0) / 1000f, LeagueHue, 72.dp, stroke = 7.dp) {
                    Text(s?.takeIf { it.ready }?.total?.toString() ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Indice global de santé"), style = MaterialTheme.typography.titleMedium)
                    Text(if (s?.ready == true) trf("%1\$s parties renseignées sur 6 · 7 jours glissants", s.pillars.values.count { it != null }) else tr("Données insuffisantes : au moins 3 parties renseignées"),
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
        if (!xpMode && coachAvailable()) {
            Spacer(Modifier.height(10.dp))
            PillButton(tr("Comment gagner des points ?"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.AutoAwesome) {
                val detail = mine?.pillars?.entries?.joinToString { (p, v) -> "${p.name.lowercase()}: ${v ?: "n/a"}%" }.orEmpty()
                nav.askCoach(trf("Mon score de la semaine est %1\$s/1000 (%2\$s). Quelle est l'action la plus utile d'ici dimanche, sans me surcharger ?", mine?.total ?: 0, detail))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (metricPicker) ModalBottomSheet(onDismissRequest = { metricPicker = false }, containerColor = Mf.colors.background) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding()) {
            Text(tr("Que comparer ?"), style = MaterialTheme.typography.titleLarge)
            app.superhealth.nutrition.ui.components.TextInput(tr("Rechercher une activité ou un exercice"), query, { query = it })
            SettingRow(tr("Indice global de santé")) { metricKey = null; part = null; metricPicker = false }
            metrics.filter { it.label.contains(query, ignoreCase = true) }.forEach { m ->
                RowDivider()
                SettingRow(m.label, m.unit, subtitle = if (m.scope == "all") tr("PR historique · même exercice") else tr("7 jours · même période")) { metricKey = m.key; part = null; metricPicker = false }
            }
        }
    }
    opened?.let { f -> FriendSheet(f) { opened = null } }

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
            text = { Text(trf("Empreinte : %1\$s\nVérifiez-la avec cette personne avant d'accepter le partage.", League.fingerprint(c.k))) },
            confirmButton = { TextButton(onClick = {
                League.accept(ctx, c); League.incoming.value = null

            }) { Text(tr("Ajouter")) } },
            dismissButton = { TextButton(onClick = { League.incoming.value = null }) { Text(tr("Refuser")) } })
    }
    if (bad) AlertDialog(onDismissRequest = { League.incomingError.value = false }, text = { Text(tr("Carte non valable : elle a été modifiée ou est incomplète.")) },
        confirmButton = { TextButton(onClick = { League.incomingError.value = false }) { Text(tr("OK")) } })
}
