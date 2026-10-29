package app.superhealth.ui.wellbeing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.has
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.GroupHeader
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.Segmented
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.nutrition.ui.components.Toggle
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.go
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.wellbeing.Bedtime
import app.superhealth.wellbeing.Checkin
import app.superhealth.wellbeing.Cycle
import app.superhealth.wellbeing.Journal
import app.superhealth.wellbeing.Reminders
import androidx.compose.runtime.withFrameMillis
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---- Journal du moral ----------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val days by Journal.days.collectAsState()
    val sh by SuperPrefs.settings.collectAsState()
    val todayDay = LocalDate.now().toEpochDay()
    val today = days[todayDay]
    var findings by remember { mutableStateOf<Journal.Findings?>(null) }
    LaunchedEffect(days.size) { findings = runCatching { Journal.findings(ctx) }.getOrNull() }
    var editTags by remember { mutableStateOf(false) }

    DetailScaffold(tr("Journal du moral"), onBack = { nav.popBackStack() }) {
        GroupCard {
            Column(Modifier.padding(14.dp)) {
                Text(tr("Comment ça va aujourd'hui ?"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                MoodPicker(today?.mood) { Journal.setMood(ctx, it) }
            }
        }
        if (today != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                GroupHeader(tr("Aujourd'hui"), Modifier.weight(1f))
                Text(tr(if (editTags) "OK" else "Modifier la liste"), color = Mf.colors.accent, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 8.dp, bottom = 6.dp).clickable { editTags = !editTags })
            }
            JournalTagChips(today, editTags)
        }

        GroupHeader(tr("30 derniers jours"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (29L downTo 0L).forEach { k ->
                val m = days[todayDay - k]?.mood
                Box(Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(4.dp)).background(m?.let { moodColor(it).copy(alpha = 0.85f) } ?: Mf.colors.chip))
            }
        }

        GroupHeader(tr("Ce qui vous influence"))
        val f = findings
        GroupCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    f == null -> Text(tr("Calcul…"), color = Mf.colors.textSecondary)
                    f.impacts.isEmpty() && f.links.isEmpty() -> Text(
                        trf("Rien de net pour l'instant (jours notés : %1\$s). Un constat apparaît quand une habitude a au moins 5 jours avec et 5 sans, et que l'écart ne peut pas venir du hasard.", f.loggedDays),
                        style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
                    else -> {
                        f.impacts.forEach { i ->
                            val d = i.diff.delta
                            val value = when (i.outcome) {
                                Journal.Outcome.FEEL -> String.format(app.superhealth.ui.Lang.locale, "%+.1f", d).replace("-", "−") + " / 5"
                                else -> (if (d.roundToInt() > 0) "+" else "−") + abs(d.roundToInt()) + if (i.outcome == Journal.Outcome.CHARGE) " %" else " min"
                            }
                            Text(trf("%1\$s : %2\$s sur la %3\$s", tr(i.tag.label), value,
                                tr(i.outcome.label).lowercase(app.superhealth.ui.Lang.locale)), style = MaterialTheme.typography.bodyMedium)
                            Text(trf("%1\$s jours avec, %2\$s sans", i.diff.nWith, i.diff.nWithout), style = MaterialTheme.typography.bodySmall,
                                color = Mf.colors.textSecondary)
                        }
                        f.links.forEach { l ->
                            Text(if (l.r > 0) trf("Votre moral suit votre %1\$s (r = %2\$s, %3\$s jours).", tr(when (l.outcome) { Journal.Outcome.SLEEP -> "sommeil"; Journal.Outcome.FEEL -> "forme ressentie"; else -> "Charge" }),
                                String.format(app.superhealth.ui.Lang.locale, "%.2f", l.r), l.n)
                            else trf("Votre moral va à l'inverse de votre %1\$s (r = %2\$s, %3\$s jours).", tr(when (l.outcome) { Journal.Outcome.SLEEP -> "sommeil"; Journal.Outcome.FEEL -> "forme ressentie"; else -> "Charge" }),
                                String.format(app.superhealth.ui.Lang.locale, "%.2f", l.r), l.n),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Text(tr("Comparaison de vos propres jours, pas un avis médical."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))

        // ressenti du matin : réglé ici, avec le journal (une seule ligne dans Réglages)
        GroupCard {
            SwitchRow(tr("Ressenti du matin"), tr("Sommeil et énergie notés avant de voir vos scores"), sh.morningCheck) { on ->
                SuperPrefs.update { it.copy(morningCheck = on) }
            }
        }
    }
}

// ---- Forme du jour ------------------------------------------------------------------------------------------------

@Composable
fun FormScreen(vm: SuperViewModel, nav: NavHostController) {
    val r = rememberForm()
    DetailScaffold(tr("Forme du jour"), onBack = { nav.popBackStack() }) {
        GroupCard { Column(Modifier.padding(16.dp)) { FormBody(r, vm, nav, compact = false) } }
        app.superhealth.ui.coach.CoachChip(tr("Que faire aujourd'hui ?"),
            tr("Vu ma forme du jour, mon sommeil et mes dernières séances, que me conseilles-tu pour aujourd'hui (entraînement, repas, coucher) ?"),
            nav, Modifier.padding(top = 10.dp))
        Text(tr("Score adaptatif : VFC, pouls au repos, Repos, entraînement (7 j / 28 j), moral et ressenti, comparés à votre normale. Dès 30 matins de ressenti, les poids s'ajustent à vous. « Malade » noté au journal impose le repos."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
    }
}

// ---- Cycle --------------------------------------------------------------------------------------------------------

@Composable
fun CycleScreen(vm: SuperViewModel, nav: NavHostController) {
    val days by Cycle.days.collectAsState()
    val info = remember(days) { Cycle.info() }
    val today = LocalDate.now().toEpochDay()
    DetailScaffold(tr("Cycle"), onBack = { nav.popBackStack() }) {
        GroupCard {
            Column(Modifier.padding(16.dp)) {
                if (info == null) Text(tr("Aucun cycle noté."), style = MaterialTheme.typography.titleMedium)
                else {
                    Text(trf("Jour %1\$s", info.dayOfCycle), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                        color = CycleHue.onContainer())
                    info.phase?.let { Text(tr(it.label), style = MaterialTheme.typography.bodyLarge) }
                    Spacer(Modifier.height(6.dp))
                    info.length?.let { Text(trf("Cycle habituel : %1\$s j · règles : %2\$s j", it, info.periodLength), style = MaterialTheme.typography.bodyMedium,
                        color = Mf.colors.textSecondary) }
                    info.nextStart?.let { Text(trf("Prochaines règles vers le %1\$s", app.superhealth.nutrition.ui.shortDate(it)),
                        style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary) }
                    info.ovulation?.let { Text(trf("Fenêtre fertile estimée : %1\$s – %2\$s", app.superhealth.nutrition.ui.shortDate(it - 5),
                        app.superhealth.nutrition.ui.shortDate(it)), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary) }
                    if (info.length == null) Text(trf("Prédictions après %1\$s cycles complets.", Cycle.MIN_CYCLES), style = MaterialTheme.typography.bodyMedium,
                        color = Mf.colors.textSecondary)
                }
                Spacer(Modifier.height(12.dp))
                PillButton(if (today in days) tr("Pas de règles aujourd'hui") else tr("Règles aujourd'hui"), strong = today !in days) { Cycle.toggle(today) }
            }
        }
        GroupHeader(tr("8 dernières semaines"))
        // calendrier : touchez un jour pour le cocher ou le décocher
        val first = LocalDate.ofEpochDay(today).minusDays(55).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }.toEpochDay()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row { listOf("L", "M", "M", "J", "V", "S", "D").forEach { Text(tr(it), Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary) } }
            generateSequence(first) { it + 7 }.takeWhile { it <= today }.forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0 until 7).forEach { i ->
                        val d = week + i
                        val on = d in days
                        val predicted = info?.nextStart?.let { n -> d in n until n + info.periodLength } == true
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                            .background(when { on -> CycleHue; predicted -> CycleHue.container(1.2f); else -> Mf.colors.chip })
                            .then(if (d <= today) Modifier.clickable { Cycle.toggle(d) } else Modifier),
                            contentAlignment = Alignment.Center) {
                            Text(LocalDate.ofEpochDay(d).dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium,
                                color = if (on) Color.White else if (d > today) Mf.colors.textTertiary else Mf.colors.text,
                                fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
        if (info != null && info.cycles.isNotEmpty()) {
            GroupHeader(tr("Cycles"))
            GroupCard {
                info.cycles.takeLast(6).reversed().forEachIndexed { i, (start, len) ->
                    if (i > 0) RowDivider()
                    SettingRow(app.superhealth.nutrition.ui.shortDate(start), value = trf("%1\$s j", len))
                }
            }
        }
        GroupHeader(tr("Import"))
        CycleImport(vm)
        Text(tr("Estimation calendaire (ovulation ≈ 14 jours avant les règles suivantes), pas une contraception. Ces données restent sur le téléphone et ne sont jamais envoyées à l'IA."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
    }
}

// ---- Respiration guidée -------------------------------------------------------------------------------------------

/** Rythmes proposés : (libellé, phases (texte, secondes, ampleur visée 0–1)). */
private val patterns = listOf(
    // 6 respirations par minute : la fréquence de résonance de la plupart des adultes (Lehrer 2000)
    "Cohérence" to listOf(Triple("Inspirez", 5.0, 1f), Triple("Expirez", 5.0, 0f)),
    "4-7-8" to listOf(Triple("Inspirez", 4.0, 1f), Triple("Retenez", 7.0, 1f), Triple("Expirez", 8.0, 0f)),
    "Carré" to listOf(Triple("Inspirez", 4.0, 1f), Triple("Retenez", 4.0, 1f), Triple("Expirez", 4.0, 0f), Triple("Retenez", 4.0, 0f)),
)

@Composable
fun BreatheScreen(nav: NavHostController) {
    var pattern by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(5) }
    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val haptic = LocalHapticFeedback.current
    val phases = patterns[pattern].second
    val cycle = phases.sumOf { it.second }
    val total = minutes * 60_000L
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = System.currentTimeMillis() - elapsed
        var lastPhase = -1
        while (running) {
            withFrameMillis { }
            elapsed = System.currentTimeMillis() - start
            if (elapsed >= total) { running = false; elapsed = total; haptic.performHapticFeedback(HapticFeedbackType.LongPress); break }
            val t = (elapsed / 1000.0) % cycle
            var acc = 0.0; var idx = 0
            for ((i, p) in phases.withIndex()) { if (t < acc + p.second) { idx = i; break }; acc += p.second }
            if (idx != lastPhase) { if (lastPhase >= 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); lastPhase = idx }
        }
    }
    if (running) KeepScreenOn()
    // phase en cours et progression dans la phase
    val t = (elapsed / 1000.0) % cycle
    var acc = 0.0; var idx = 0
    for ((i, p) in phases.withIndex()) { if (t < acc + p.second) { idx = i; break }; acc += p.second }
    val ph = phases[idx]
    val from = phases[(idx - 1 + phases.size) % phases.size].third
    val k = ((t - acc) / ph.second).toFloat().coerceIn(0f, 1f)
    // montée et descente douces (sinusoïdale), pas de battement
    val ease = (1 - cos(PI * k).toFloat()) / 2
    val amp = if (running) from + (ph.third - from) * ease else 0.35f
    val shown by animateFloatAsState(amp, tween(if (running) 0 else 400), label = "breath")

    DetailScaffold(tr("Respirer"), onBack = { nav.popBackStack() }) {
        Segmented(patterns.map { tr(it.first) }, pattern) { if (!running) { pattern = it; elapsed = 0 } }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            val hue = HealthHues.Respiration
            Canvas(Modifier.fillMaxWidth(0.92f).aspectRatio(1f)) {
                val r = size.minDimension / 2 * (0.42f + 0.55f * shown)
                // forme organique : un cercle légèrement ondulé qui tourne lentement
                val path = Path()
                val turn = (elapsed / 9000.0) * 2 * PI
                for (i in 0..120) {
                    val a = i / 120.0 * 2 * PI
                    val wobble = 1 + 0.035 * sin(3 * a + turn) + 0.02 * sin(5 * a - turn * 1.3)
                    val p = Offset(center.x + (r * wobble * cos(a)).toFloat(), center.y + (r * wobble * sin(a)).toFloat())
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path, Brush.radialGradient(listOf(hue.copy(alpha = 0.55f), hue.copy(alpha = 0.18f)), center, r * 1.1f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (running) tr(ph.first) else if (elapsed >= total) tr("Terminé") else tr("Prêt"), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold)
                if (running) Text(((ph.second * (1 - k)) + 0.999).toInt().toString(), style = MaterialTheme.typography.titleLarge, color = Mf.colors.textSecondary)
            }
        }
        Spacer(Modifier.height(8.dp))
        val left = ((total - elapsed) / 1000).coerceAtLeast(0)
        Text("%d:%02d".format(left / 60, left % 60), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
            color = Mf.colors.textSecondary)
        Spacer(Modifier.height(12.dp))
        if (!running) Segmented(listOf(1, 3, 5, 10).map { trf("%1\$s min", it) }, listOf(1, 3, 5, 10).indexOf(minutes)) { minutes = listOf(1, 3, 5, 10)[it]; elapsed = 0 }
        Spacer(Modifier.height(12.dp))
        PillButton(if (running) tr("Arrêter") else tr("Commencer"), Modifier.fillMaxWidth(), strong = !running) {
            if (!running && elapsed >= total) elapsed = 0
            running = !running
        }
        Text(tr("Une vibration marque chaque changement : vous pouvez fermer les yeux."), style = MaterialTheme.typography.bodySmall,
            color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
    }
}

// ---- Rappels ------------------------------------------------------------------------------------------------------

@Composable
fun RemindersScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val sh by SuperPrefs.settings.collectAsState()
    val ask = rememberNotificationAsk()
    fun set(f: (app.superhealth.SuperSettings) -> app.superhealth.SuperSettings) {
        SuperPrefs.update(f); ask(); Reminders.schedule(ctx)
    }
    @Composable
    fun TimeRow(title: String, minute: Int, default: Int, onSet: (Int) -> Unit) {
        // comme les autres interrupteurs : le sous-titre dit l'heure ; toucher la ligne choisit l'heure (et active le rappel)
        fun pick() {
            val m = if (minute >= 0) minute else default
            android.app.TimePickerDialog(ctx, { _, h, mi -> onSet(h * 60 + mi) }, m / 60, m % 60, true).show()
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable { pick() }.padding(start = 16.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(if (minute >= 0) trf("Chaque jour à %1\$s", Bedtime.clock(minute)) else tr("Coupé"), style = MaterialTheme.typography.bodySmall,
                    color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 1.dp))
            }
            Spacer(Modifier.width(10.dp))
            Toggle(minute >= 0, { on -> onSet(if (on) default else -1) })
        }
    }
    DetailScaffold(tr("Rappels"), onBack = { nav.popBackStack() }) {
        GroupCard {
            if (sh.has(app.superhealth.Feature.NUTRITION)) {
                TimeRow(tr("Pesée du matin"), sh.remindWeigh, 7 * 60) { v -> set { it.copy(remindWeigh = v) } }
                RowDivider()
                TimeRow(tr("Journal alimentaire"), sh.remindLog, 20 * 60) { v -> set { it.copy(remindLog = v) } }
                RowDivider()
            }
            if (sh.has(app.superhealth.Feature.JOURNAL)) {
                TimeRow(tr("Journal du moral"), sh.remindJournal, 21 * 60) { v -> set { it.copy(remindJournal = v) } }
                RowDivider()
            }
            SwitchRow(tr("Coucher conseillé"), tr("30 min avant l'heure conseillée"), sh.remindBedtime) { v -> set { it.copy(remindBedtime = v) } }
        }
        Text(tr("Un rappel déjà inutile (pesée faite, humeur notée, repas notés récemment) ne sonne pas."), style = MaterialTheme.typography.bodySmall,
            color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        if (sh.remindWeigh >= 0 || sh.remindLog >= 0 || sh.remindJournal >= 0 || sh.remindBedtime) ExactAlarmRow()
    }
}


/** Ressenti du matin : les deux questions, puis retour aux scores. */
@Composable
fun CheckinScreen(nav: NavHostController) {
    val days by Checkin.days.collectAsState()
    val done = days[LocalDate.now().toEpochDay()] != null
    LaunchedEffect(done) { if (done && nav.currentBackStackEntry?.destination?.route == "checkin") nav.popBackStack() }
    DetailScaffold(tr("Ressenti du matin"), onBack = { nav.popBackStack() }) {
        GroupCard { Column(Modifier.padding(16.dp)) { CheckinPickers() } }
        Text(tr("Noté avant de voir vos scores, pour qu'ils n'influencent pas votre ressenti."), style = MaterialTheme.typography.bodySmall,
            color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
    }
}
