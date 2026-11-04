package app.superhealth.ui.league

import app.superhealth.league.Achievements
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.league.*
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.Analytics
import app.superhealth.training.ui.weight
import java.text.Normalizer
import java.time.LocalDate

private val GameGreen = Color(0xFF58CC02)
private val GameGold = Color(0xFFFFC800)
private val GameOrange = Color(0xFFFF9600)

/** Niveau personnel et XP réellement calculés ; l'illustration n'est pas une récompense attribuée. */
@Composable
internal fun ProgressSummaryCard(summary: Progression.Summary) {
    val start = Progression.threshold(summary.level)
    val next = Progression.threshold(summary.level + 1)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(GameGreen.copy(alpha = 0.19f), GameGold.copy(alpha = 0.12f), Mf.colors.card)))
        .border(1.dp, GameGreen.copy(alpha = 0.3f), RoundedCornerShape(28.dp)).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Niveau"), style = MaterialTheme.typography.titleMedium, color = Mf.colors.textSecondary)
                Text(summary.level.toString(), fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text(trf("%1\$s XP cette semaine", summary.weekXp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            GameArt(GameArtKind.XP, Modifier.size(104.dp))
        }
        Spacer(Modifier.height(16.dp))
        ProgressBar(((summary.totalXp - start).toFloat() / (next - start)).coerceIn(0f, 1f), GameGreen, Modifier.fillMaxWidth(), height = 16.dp,
            track = Mf.colors.background.copy(alpha = 0.45f))
        Text(trf("%1\$s / %2\$s XP vers le niveau %3\$s", summary.totalXp - start, next - start, summary.level + 1),
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Text(trf("%1\$s XP au total", summary.totalXp), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
internal fun TrendLine(values: List<Double>, modifier: Modifier = Modifier) {
    val color = Mf.colors.accent; val track = Mf.colors.chip
    Canvas(modifier.fillMaxWidth().height(85.dp).padding(vertical = 8.dp)) {
        drawLine(track, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        if (values.isEmpty()) return@Canvas
        val min = values.minOrNull()!!; val span = (values.maxOrNull()!! - min).coerceAtLeast(1.0)
        val points = values.mapIndexed { i, v -> Offset(if (values.size == 1) size.width / 2 else i.toFloat() / (values.size - 1) * size.width, (size.height * (0.9 - 0.8 * ((v - min) / span))).toFloat()) }
        val path = Path(); points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        drawPath(path, color, style = Stroke(2.dp.toPx())); points.forEach { drawCircle(color, 3.dp.toPx(), it) }
    }
}

private fun streakCount(s: Progression.Streak, count: Int = s.current): String = trf(
    if (s.weekly) { if (count == 1) "%1\$s semaine" else "%1\$s semaines" } else { if (count == 1) "%1\$s jour" else "%1\$s jours" },
    count.toString() + if (s.capped && count == s.current) "+" else "")

/** Paliers de la référence : une étape est colorée uniquement si le record du suivi atteint ce seuil. */
@Composable
private fun StreakJourney(s: Progression.Streak) {
    val badges = Achievements.catalog.filter { it.metric == "streak:${s.track}" }.sortedBy { it.threshold }
    var all by remember(s.track) { mutableStateOf(false) }
    val nextIndex = badges.indexOfFirst { it.threshold > s.best }.let { if (it < 0) badges.size else it }
    val first = (nextIndex - 1).coerceIn(0, (badges.size - 4).coerceAtLeast(0))
    val visible = if (all) badges else badges.drop(first).take(4)
    var detail by remember(s.track) { mutableStateOf<Int?>(null) }
    val neutral = Mf.colors.chip
    MfCard {
        Text(tr("Le chemin de votre régularité"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(trf("Record : %1\$s", streakCount(s, s.best)), style = MaterialTheme.typography.bodyMedium, color = GameOrange,
            modifier = Modifier.padding(top = 4.dp))
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            visible.forEach { badge ->
                val goal = badge.threshold.toInt()
                val reached = s.best >= goal
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { detail = goal }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).background(if (reached) GameGreen.copy(alpha = 0.15f) else Mf.colors.background, CircleShape), contentAlignment = Alignment.Center) {
                        app.superhealth.nutrition.ui.components.AssetImage(
                            badge.asset,
                            Modifier.size(42.dp).graphicsLayer { alpha = if (reached) 1f else .35f }, crop = false)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(streakCount(s, goal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(tr(if (reached) "Jalon débloqué !" else "Continuez votre série pour débloquer ce jalon."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                    if (reached) Icon(Icons.Rounded.CheckCircle, tr("Accompli"), tint = GameGreen, modifier = Modifier.size(24.dp))
                }
            }
        }
        TextButton({ all = !all }, Modifier.fillMaxWidth()) { Text(tr(if (all) "Voir moins" else "Tous les jalons")) }
    }
    detail?.let { goal ->
        AlertDialog(onDismissRequest = { detail = null }, containerColor = Mf.colors.card,
            title = { Text(streakCount(s, goal)) },
            text = { Text(if (s.best >= goal) tr("Jalon débloqué !") else tr("Continuez votre série pour débloquer ce jalon.")) },
            confirmButton = { TextButton({ detail = null }) { Text(tr("OK")) } })
    }
}

@Composable
private fun TrackingMissions(summary: Progression.Summary, nav: NavHostController) {
    summary.streaks.forEach { s ->
        val track = Progression.Track.entries.firstOrNull { it.name == s.track } ?: return@forEach
        val complete = s.progress >= s.target
        MfCard(Modifier.padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackArt(s.track, s.best, Modifier.size(42.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(tr(track.label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(trackingGoal(track), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    Text(trackingProgress(s), style = MaterialTheme.typography.titleMedium,
                        color = Mf.colors.accent, modifier = Modifier.padding(top = 4.dp))
                }
                if (complete) Icon(Icons.Rounded.CheckCircle, tr("Accompli"), tint = GameGreen, modifier = Modifier.size(28.dp))
            }
            Text(streakCount(s) + " · " + trf("Record : %1\$s", streakCount(s, s.best)),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
            ProgressBar(trackingFraction(s), if (complete) GameGreen else GameGold, Modifier.padding(top = 12.dp), height = 8.dp)
            if (!complete) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(trf("%1\$s XP / jour actif", track.xp), Modifier.weight(1f).padding(end = 8.dp), color = Mf.colors.accent, style = MaterialTheme.typography.labelMedium)
                TextButton({ nav.continueTracking(track) }) { Text(tr("Continuer mon suivi")) }
            }
        }
    }
}

@Composable
private fun WeekCalendar(summary: Progression.Summary) {
    MfCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            summary.days.forEach { day ->
                val date = LocalDate.ofEpochDay(day.day)
                val active = day.xp > 0
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, app.superhealth.ui.Lang.locale), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                    Box(Modifier.padding(vertical = 8.dp).size(29.dp).clip(CircleShape).background(if (active) GameGreen else Mf.colors.chip), contentAlignment = Alignment.Center) {
                        if (active) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = Color(0xFF184300))
                        else Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                    }
                    Text(if (active) day.xp.toString() else "—", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(tr("XP de suivi plafonnés à 120 par jour ; bonus de quête en plus."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun searchKey(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(app.superhealth.ui.Lang.locale)

/** Un seul champ à ouvrir : pas de mur de puces pour les nombreux exercices et mesures. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressChoiceSheet(title: String, options: List<Pair<String, String>>, selected: String?, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(options, query) { options.filter { searchKey(it.second).contains(searchKey(query)) } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Mf.colors.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding().imePadding()) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(query, { query = it }, label = { Text(tr("Rechercher")) }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                if (filtered.isEmpty()) item { Text(tr("Aucun résultat"), color = Mf.colors.textSecondary, modifier = Modifier.padding(vertical = 16.dp)) }
                items(filtered, key = { it.first }) { (id, name) ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(id); onDismiss() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (id == selected) Icon(Icons.Rounded.CheckCircle, null, tint = Mf.colors.accent, modifier = Modifier.size(23.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val enabled by app.superhealth.SuperPrefs.settings.collectAsState()
    if (!enabled.leagueEnabled) {
        DetailScaffold(tr("Ma progression"), onBack = { nav.popBackStack() }, contentSpacing = 12.dp) {
            PillButton(tr("Activer le classement"), Modifier.fillMaxWidth()) { app.superhealth.SuperPrefs.update { it.copy(leagueEnabled = true) } }
        }
        return
    }
    val result by ProgressRepository.current.collectAsState()
    val data by WorkoutApp.store.state.collectAsState()
    val db by WorkoutApp.db.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var exercise by remember { mutableStateOf<String?>(null) }
    var exercisePicker by remember { mutableStateOf(false) }
    var trackId by remember { mutableStateOf<String?>(null) }
    var trackPicker by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { League.compute(ctx) }.onFailure { error = it.message } }
    val p = result?.summary
    val ids = data.history.flatMap { it.exercises }.map { it.exerciseId }.distinct()
    val selected = exercise?.takeIf { it in ids } ?: ids.firstOrNull()
    DetailScaffold(tr("Ma progression"), onBack = { nav.popBackStack() }, contentSpacing = 12.dp, action = {
        IconButton({ nav.go("league/settings") }) { Icon(Icons.Rounded.Settings, tr("Réglages du classement")) }
    }) {
        if (p != null) {
            ProgressSummaryCard(p)
            GroupCard { SettingRow(tr("Quêtes de la semaine"), subtitle = tr("Objectifs et récompenses")) { nav.go("quests") } }
            SectionLabel(tr("Mes défis de suivi"))
            TrackingMissions(p, nav)
            SectionLabel(tr("Votre chemin"))
            val streak = p.streaks.firstOrNull { it.track == trackId } ?: p.streaks.firstOrNull()
            if (streak != null) {
                GroupCard {
                    SettingRow(tr("Parcours de série"), Progression.Track.entries.firstOrNull { it.name == streak.track }?.label?.let { tr(it) }) { trackPicker = true }
                }
                Spacer(Modifier.height(10.dp))
                StreakJourney(streak)
            } else SettingRow(tr("Choisir mes suivis")) { nav.go("group/score") }
            SectionLabel(tr("Cette semaine"))
            WeekCalendar(p)
            SettingRow(tr("Profil du Groupe")) { nav.go("social") }
            SectionLabel(tr("Mes badges"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MfCard(Modifier.weight(1f)) {
                    GameArt(if (p.records > 0) GameArtKind.TROPHY else GameArtKind.XP_EMPTY, Modifier.fillMaxWidth().height(80.dp))
                    Text(trf("%1\$s records sportifs", p.records), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(tr("Depuis vos séances enregistrées"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
                val perfect = p.days.size == 7 && p.days.all { it.day <= p.throughDay && it.xp > 0 }
                MfCard(Modifier.weight(1f)) {
                    GameArt(if (perfect) GameArtKind.PERFECT_WEEK else GameArtKind.XP_EMPTY, Modifier.fillMaxWidth().height(80.dp))
                    Text(tr("Semaine suivie"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(trf("%1\$s / 7 jours renseignés", p.days.count { it.xp > 0 }), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
            }
        } else Text(error ?: tr("Calcul de la progression…"), color = Mf.colors.textSecondary)
        SectionLabel(tr("Évolution des performances"))
        if (db != null && selected != null) {
            GroupCard { SettingRow(tr("Exercice"), db!!.exercise(selected)?.name ?: selected, icon = Icons.Rounded.Search) { exercisePicker = true } }
            val history = remember(selected, data.history, db) { Analytics.history(selected, data.history, db!!) }
            val points = history.takeLast(12)
            val weighted = db!!.exercise(selected)?.usesWeight == true
            MfCard(Modifier.padding(top = 10.dp)) {
                Text(tr(if (weighted) "1RM estimé · dernières séances" else "Répétitions · dernières séances"), style = MaterialTheme.typography.titleSmall)
                val values = points.map { if (weighted) it.e1rm else it.reps.toDouble() }
                TrendLine(values)
                if (values.isNotEmpty()) {
                    val best = history.maxOf { if (weighted) it.e1rm else it.reps.toDouble() }
                    val format: (Double) -> String = { value -> if (weighted) data.settings.weight(value) else trf("%1\$s reps", value.toInt()) }
                    Text(trf("Dernier : %1\$s · record : %2\$s", format(values.last()), format(best)), style = MaterialTheme.typography.bodyMedium)
                    Text(trf("Du %1\$s au %2\$s", Analytics.day(points.first().time).toString(), Analytics.day(points.last().time).toString()), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
                }
            }
        } else Text(tr("Vos séances enregistrées feront apparaître vos courbes et vos records."), color = Mf.colors.textSecondary)
        Spacer(Modifier.height(12.dp))
        PillButton(tr("Comparer avec mes amis"), Modifier.fillMaxWidth()) { nav.go("league") }
    }
    if (exercisePicker) ProgressChoiceSheet(tr("Choisir un exercice"), ids.map { it to (db?.exercise(it)?.name ?: it) }, selected,
        { exercisePicker = false }) { exercise = it }
    if (trackPicker && p != null) ProgressChoiceSheet(tr("Choisir un parcours"), p.streaks.map { s -> s.track to tr(Progression.Track.entries.first { it.name == s.track }.label) }, trackId,
        { trackPicker = false }) { trackId = it }
}

@Composable
internal fun FriendProgressDetails(friend: League.Friend) {
    val mine by ProgressRepository.current.collectAsState()
    val state by WorkoutApp.store.state.collectAsState()
    friend.progress?.let { p ->
        SectionLabel(tr("Progression")); ProgressSummaryCard(p)
        p.streaks.forEach { s ->
            val track = Progression.Track.entries.firstOrNull { it.name == s.track } ?: return@forEach
            val own = mine?.summary?.streaks?.firstOrNull { it.track == s.track && it.weekly == s.weekly && it.target == s.target }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackArt(s.track, s.best, Modifier.size(28.dp))
                Text(tr(track.label) + " · " + streakCount(s) + (own?.let { " · " + trf("vous : %1\$s", it.current) } ?: ""),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp))
            }
        }
    }
    val metrics = friend.metrics.filter { friend.trends[it.key].orEmpty().size >= 2 }
    var key by remember(friend.id) { mutableStateOf<String?>(null) }
    var picker by remember(friend.id) { mutableStateOf(false) }
    val metric = metrics.firstOrNull { it.key == key } ?: metrics.firstOrNull()
    if (metric != null) {
        SectionLabel(tr("Évolution reçue"))
        GroupCard { SettingRow(tr("Mesure"), metric.label, icon = Icons.Rounded.Search) { picker = true } }
        val points = friend.trends[metric.key].orEmpty().takeLast(12)
        TrendLine(points.map { it.value })
        val format: (Double) -> String = { value -> if (metric.unit == "kg") state.settings.weight(value) else app.superhealth.nutrition.ui.num(value, 1) + " " + metric.unit }
        Text(trf("Dernier : %1\$s · record reçu : %2\$s", format(points.last().value), format(if (metric.lowerWins) friend.trends[metric.key].orEmpty().minOf { it.value } else friend.trends[metric.key].orEmpty().maxOf { it.value })), style = MaterialTheme.typography.bodyMedium)
        Text(trf("Du %1\$s au %2\$s", LocalDate.ofEpochDay(points.first().day).toString(), LocalDate.ofEpochDay(points.last().day).toString()), style = MaterialTheme.typography.bodySmall)
        Text(tr("Cartes reçues uniquement ; les jours absents ne sont pas interpolés. Les totaux d'activité portent sur 7 jours glissants."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        if (picker) ProgressChoiceSheet(tr("Choisir une mesure"), metrics.map { it.key to it.label }, metric.key, { picker = false }) { key = it }
    }
}
