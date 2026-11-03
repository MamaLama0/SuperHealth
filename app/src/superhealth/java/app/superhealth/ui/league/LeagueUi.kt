package app.superhealth.ui.league

import android.graphics.Bitmap
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.league.League
import app.superhealth.league.HealthIndex
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.Segmented
import app.superhealth.nutrition.ui.components.SettingRow
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

/** Ligne du classement : moi ([friend] nul) ou un ami ; score nul : rien à comparer pour ce choix. */
private class Rank(val name: String, value: Double?, val friend: League.Friend?) {
    // Les valeurs affichées au dixième doivent partager leur rang quand elles sont égales.
    val score = value?.let { kotlin.math.round(it * 10) / 10 }
}

@Composable
private fun Avatar(r: Rank, size: Dp) {
    val photo = if (r.friend == null) ProfilePhoto.bitmap.value else null
    if (photo != null) Image(photo, null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    else Initial(r.name, size)
}

private fun number(value: Double) = app.superhealth.nutrition.ui.num(value, if (value % 1.0 == 0.0) 0 else 1)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LeagueScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf(false) }
    val sh by app.superhealth.SuperPrefs.settings.collectAsState()
    if (!sh.leagueEnabled) {
        DetailScaffold(tr("Classement et progression"), onBack = { nav.popBackStack() }, contentSpacing = 12.dp) {
            Text(tr("Le classement est désactivé. Vos amis et vos données sont conservés."))
            PillButton(tr("Activer le classement"), Modifier.fillMaxWidth()) { app.superhealth.SuperPrefs.update { it.copy(leagueEnabled = true) }; app.superhealth.league.LeagueWorker.schedule(ctx) }
        }
        return
    }
    val st by League.state.collectAsState()
    val mine by League.mine.collectAsState()
    var mode by rememberSaveable { mutableStateOf("HEALTH") }
    val xpMode = mode == "XP"
    var opened by remember { mutableStateOf<League.Friend?>(null) }
    var month by rememberSaveable { mutableStateOf(false) }
    var part by rememberSaveable { mutableStateOf<String?>(null) }
    var metricKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showParts by rememberSaveable { mutableStateOf(false) }
    var metricPicker by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { runCatching { League.compute(ctx) } }
    val name = userName() ?: tr("Moi")
    val s = mine
    val endDay = s?.endDay ?: LocalDate.now().minusDays(1).toEpochDay()
    val now = System.currentTimeMillis() / 1000
    val recent = now - 48 * 3600
    val pillar = League.Pillar.entries.firstOrNull { it.name == part }.takeIf { !month && metricKey == null }
    val metrics = (s?.metrics.orEmpty() + st.friends.flatMap { it.metrics }).distinctBy { it.key }.sortedBy { it.label }
    val metric = metrics.firstOrNull { it.key == metricKey }.takeIf { !month }
    fun mean(scores: Map<String, Int>, coverage: Int = s?.coverage ?: 0) = HealthIndex.mean28(scores, endDay, coverage)
    val rows = (st.friends.map { f ->
        val compatible = f.model == HealthIndex.MODEL && f.endDay == endDay && f.time >= recent
        val value: Double? = when {
            xpMode -> f.progress?.takeIf { it.model == app.superhealth.league.Progression.MODEL && it.weekStart == s?.progress?.weekStart && f.time >= recent }?.weekXp?.toDouble()
            mode == "PERF" && metric == null -> null
            month -> mean(f.history, f.coverage)
            metric != null -> f.metrics.firstOrNull { it.key == metric.key && it.unit == metric.unit && it.scope == metric.scope && it.lowerWins == metric.lowerWins }
                ?.takeIf { f.metricsTime >= recent && (it.scope == "all" || f.metricsEndDay == endDay) }?.value
            pillar != null -> if (compatible) f.pillars.getOrNull(pillar.ordinal)?.toDouble() else null
            else -> if (s?.comparable(f, now) == true) f.score.toDouble() else null
        }
        Rank(f.shown, value, f)
    } + Rank(name, when {
        xpMode -> s?.progress?.weekXp?.toDouble()
        mode == "PERF" && metric == null -> null
        month -> mean(st.best)
        metric != null -> s?.metrics?.firstOrNull { it.key == metric.key }?.value
        pillar != null -> s?.pillars?.get(pillar)?.toDouble()
        else -> s?.takeIf { it.ready }?.total?.toDouble()
    }, null)).sortedWith(compareBy<Rank> { it.score == null }
        .thenBy { if (metric?.lowerWins == true) it.score else it.score?.unaryMinus() }
        .thenBy { it.friend != null }.thenBy { it.name.lowercase() }.thenBy { it.friend?.id })
    val ranked = rows.filter { it.score != null }
    val missing = rows.filter { it.score == null }
    val suffix = if (xpMode) " XP" else if (pillar != null) " %" else metric?.let { " ${it.unit}" } ?: ""

    DetailScaffold(tr("Classement"), onBack = { nav.popBackStack() }, contentSpacing = 12.dp, action = {
        IconButton(onClick = { nav.navigate("friends/add") }) { Icon(Icons.Rounded.PersonAdd, tr("Ajouter un ami")) }
        IconButton(onClick = { nav.navigate("league/settings") }) { Icon(Icons.Rounded.Tune, tr("Réglages du classement"), tint = Mf.colors.text) }
    }) {
        Segmented(listOf(tr("Score global"), tr("XP semaine"), tr("Performances")), listOf("HEALTH", "XP", "PERF").indexOf(mode)) {
            mode = listOf("HEALTH", "XP", "PERF")[it]
            part = null
            metricKey = if (mode == "PERF") metrics.firstOrNull()?.key else null
            month = false
            if (mode == "PERF") metricPicker = true
        }
        if (mode == "HEALTH") Segmented(listOf(tr("7 jours"), tr("28 jours")), if (month) 1 else 0) { month = it == 1 }
        if (mode == "PERF") SettingRow(tr("Comparer"), metric?.label ?: tr("Choisir une mesure")) { metricPicker = true }
        val criterion = when {
            xpMode -> tr("XP semaine")
            month -> tr("Score moyen sur 28 jours")
            mode == "PERF" -> metric?.label ?: tr("Performances")
            pillar != null -> tr(pillar.label)
            else -> tr("Score global")
        }
        val period = when {
            xpMode -> tr("Du lundi au dimanche")
            metric?.scope == "all" -> tr("PR historique · même exercice")
            else -> {
                val end = LocalDate.ofEpochDay(endDay)
                val begin = end.minusDays(if (month) 27 else 6)
                val startFormat = java.time.format.DateTimeFormatter.ofPattern(if (begin.year == end.year) "d MMM" else "d MMM yyyy", app.superhealth.ui.Lang.locale)
                val endFormat = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", app.superhealth.ui.Lang.locale)
                begin.format(startFormat) + " – " + end.format(endFormat)
            }
        }
        val own = ranked.firstOrNull { it.friend == null }
        val position = ranked.indexOfFirst { own != null && it.score == own.score } + 1
        val tied = own != null && ranked.any { it.friend != null && it.score == own.score }
        RankingHero {
            Text(criterion, style = MaterialTheme.typography.labelLarge, color = LeagueHue.onContainer())
            Text(period, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            Spacer(Modifier.height(10.dp))
            Text(when {
                own == null -> tr("Pas encore de données comparables")
                ranked.size == 1 -> tr("Votre résultat")
                else -> trf("Place %1\$s sur %2\$s", position, ranked.size) + if (tied) tr(" · ex æquo") else ""
            }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            own?.score?.let { value ->
                FlowRow(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp)) {
                    Text(number(value), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                        color = LeagueHue.onContainer())
                    Text(if (mode == "HEALTH" && pillar == null) "/ 1000" else suffix.trim(),
                        style = MaterialTheme.typography.titleMedium, color = Mf.colors.textSecondary,
                        modifier = Modifier.align(Alignment.Bottom).padding(bottom = 5.dp))
                }
                if (mode == "HEALTH") LinearProgressIndicator(progress = { (value / if (pillar == null) 1000 else 100).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp), color = LeagueHue,
                    trackColor = LeagueHue.container(), drawStopIndicator = {})
                ranked.getOrNull(position - 2)?.let { ahead ->
                    val gap = number(kotlin.math.abs(ahead.score!! - value))
                    Text(if (mode == "HEALTH" && pillar == null) trf("%1\$s points pour rejoindre %2\$s", gap, ahead.name)
                        else trf("%1\$s pour rejoindre %2\$s", gap + suffix, ahead.name),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (metric != null) Text(if (metric.lowerWins) tr("La valeur la plus basse arrive en tête.") else tr("La valeur la plus élevée arrive en tête."), style = MaterialTheme.typography.bodySmall)
            if (month) Text(tr("Moyenne des indices disponibles sur 28 jours ; les jours absents restent absents."), style = MaterialTheme.typography.bodySmall)
        }
        if (mode == "HEALTH" && s?.ready != true) PillButton(tr("Choisir mes activités"), Modifier.fillMaxWidth(), strong = false) { nav.navigate("league/settings") }
        if (refreshError) Text(tr("Actualisation impossible. Réessayez."), color = MaterialTheme.colorScheme.error)
        if (ranked.size >= 2) RankingPodium(ranked.take(3).map { row ->
            PodiumEntry(row.name, number(row.score!!) + suffix, ranked.indexOfFirst { it.score == row.score } + 1, row.friend == null)
        })
        SectionLabel(tr("La course aux points"))
        if (ranked.isNotEmpty()) GroupCard {
            ranked.forEachIndexed { i, row ->
                if (i > 0) RowDivider()
                val me = row.friend == null
                val place = ranked.indexOfFirst { it.score == row.score } + 1
                Row(Modifier.fillMaxWidth().background(if (me) LeagueHue.container() else Color.Transparent)
                    .clickable(enabled = !me) { opened = row.friend }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RankMedal(place)
                    Spacer(Modifier.width(8.dp))
                    Avatar(row, 32.dp)
                    Text(if (me) tr("Vous") else row.name, Modifier.weight(1f).padding(horizontal = 10.dp),
                        style = MaterialTheme.typography.bodyLarge, fontWeight = if (me) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(number(row.score!!) + suffix, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        if (mode == "HEALTH" && !month && pillar == null) Text(
            if (s?.ready == true) tr("Même période · au moins 3 domaines par personne · cartes de moins de 48 h")
            else tr("Données insuffisantes : au moins 3 parties renseignées"),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        if (mode == "HEALTH" && !month) {
            SettingRow(tr("Détail par partie"), if (showParts) tr("Masquer") else tr("Afficher")) { showParts = !showParts }
            if (showParts) GroupCard {
                SettingRow(tr("Score global"), s?.takeIf { it.ready }?.total?.toString() ?: "—") { part = null }
                League.Pillar.entries.forEach { p ->
                    RowDivider()
                    SettingRow(tr(p.label), s?.pillars?.get(p)?.let { "$it %" } ?: "—") { part = p.name }
                }
            }
        }
        if (xpMode) SettingRow(tr("Mes séries et mes niveaux"), subtitle = tr("Missions, jalons et évolution de mes performances")) { nav.navigate("progress") }
        if (missing.isNotEmpty()) {
            SectionLabel(tr("À actualiser ou non comparables"))
            GroupCard {
                missing.forEachIndexed { i, row ->
                    if (i > 0) RowDivider()
                    val reason = when {
                        row.friend == null -> tr("Données insuffisantes, période ou version différentes")
                        mode == "HEALTH" && !month && row.friend.model != HealthIndex.MODEL -> tr("Ancienne carte : demander une mise à jour")
                        !month && row.friend.time < recent -> tr("Carte reçue il y a plus de 48 h")
                        else -> tr("Données insuffisantes, période ou version différentes")
                    }
                    SettingRow(if (row.friend == null) tr("Vous") else row.name, "—", subtitle = reason) { row.friend?.let { opened = it } }
                }
            }
            if (st.relayEnabled) PillButton(tr(if (refreshing) "Actualisation…" else "Actualiser les scores"),
                Modifier.fillMaxWidth(), strong = false, enabled = !refreshing, icon = Icons.Rounded.Refresh) {
                refreshing = true
                refreshError = false
                scope.launch {
                    try { app.superhealth.league.Relay.sync(ctx); League.compute(ctx); refreshError = League.state.value.syncError != null }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { refreshError = true }
                    finally { refreshing = false }
                }
            }
        }
        PillButton(tr("Ajouter un ami"), Modifier.fillMaxWidth(), icon = Icons.Rounded.PersonAdd) { nav.navigate("friends/add") }
        if (mode == "HEALTH" && !month && pillar == null && s?.ready == true && coachAvailable()) {
            Spacer(Modifier.height(10.dp))
            PillButton(tr("Comment gagner des points ?"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.AutoAwesome) {
                val detail = mine?.pillars?.entries?.joinToString { (p, v) -> "${p.name.lowercase()}: ${v ?: "n/a"}%" }.orEmpty()
                nav.askCoach(trf("Mon score sur les sept derniers jours terminés est de %1\$s/1000 (%2\$s). Quel domaine pourrais-je travailler ?", mine?.total ?: 0, detail))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (metricPicker) ModalBottomSheet(onDismissRequest = { metricPicker = false }, containerColor = Mf.colors.background) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding()) {
            Text(tr("Que comparer ?"), style = MaterialTheme.typography.titleLarge)
            app.superhealth.nutrition.ui.components.TextInput(tr("Rechercher une activité ou un exercice"), query, { query = it })
            SettingRow(tr("Indice global de santé")) { mode = "HEALTH"; metricKey = null; part = null; metricPicker = false }
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
    var accepting by remember { mutableStateOf(false) }
    card?.let { c ->
        AlertDialog(onDismissRequest = { League.incoming.value = null },
            title = { Text(trf("Ajouter %1\$s ?", c.n)) },
            text = { Text(trf("Empreinte : %1\$s\nVérifiez-la avec cette personne avant d'accepter le partage.", League.fingerprint(c.k))) },
            confirmButton = { TextButton(enabled = !accepting, onClick = {
                scope.launch {
                    accepting = true
                    try { app.superhealth.league.InvitationCodes.confirm(ctx, c); League.incoming.value = null }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { android.widget.Toast.makeText(ctx, tr("Ajout impossible. Réessayez."), android.widget.Toast.LENGTH_LONG).show() }
                    finally { accepting = false }
                }
            }) { Text(tr("Ajouter")) } },
            dismissButton = { TextButton(onClick = { League.incoming.value = null }) { Text(tr("Refuser")) } })
    }
    if (bad) AlertDialog(onDismissRequest = { League.incomingError.value = false }, text = { Text(tr("Carte non valable : elle a été modifiée ou est incomplète.")) },
        confirmButton = { TextButton(onClick = { League.incomingError.value = false }) { Text(tr("OK")) } })
}
