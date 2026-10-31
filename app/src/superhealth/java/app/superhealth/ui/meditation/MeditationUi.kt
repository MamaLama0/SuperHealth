package app.superhealth.ui.meditation

import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.coach.Coach
import app.superhealth.meditation.Meditation
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.Toggle
import app.superhealth.ui.coach.coachAvailable
import app.superhealth.ui.go
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.ui.wellbeing.KeepScreenOn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val MeditationHue = Color(0xFF7B8CDE)

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) =
    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, color = if (on) MeditationHue.onContainer() else Mf.colors.text,
        modifier = Modifier.clip(CircleShape).background(if (on) MeditationHue.container() else Mf.colors.chip).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp))

/** Section Méditation de l'onglet Activités : le minuteur tout de suite, les séances guidées dessous, la respiration, le coach. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.MeditationSection(nav: NavHostController) {
    val log by Meditation.log.collectAsState()
    val sh by SuperPrefs.settings.collectAsState()
    var minutes by rememberSaveable { mutableIntStateOf(10) }
    var interval by rememberSaveable { mutableIntStateOf(0) }
    var options by remember { mutableStateOf(false) }
    val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
    val week = remember(log) { Meditation.minutes(monday) }
    val streak = remember(log) { Meditation.streak() }
    if (week > 0) Text(listOfNotNull(trf("Cette semaine : %1\$s min", week), if (streak > 1) trf("%1\$s jours d'affilée", streak) else null).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
    // comme la pratique libre de Petit BamBou : la durée et on commence ; gong intermédiaire et bruit blanc restent à part
    MfCard {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(5, 10, 15, 20, 30).forEach { m -> Chip(trf("%1\$s min", m), m == minutes) { minutes = m } }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(tr("Commencer"), Modifier.weight(1f), icon = Icons.Rounded.PlayArrow) {
                Meditation.current.value = Meditation.Play(tr("Méditation libre"), minutes, intervalMin = interval, noise = sh.meditationNoise)
                nav.go("meditation/play")
            }
            Text(tr("Options"), Modifier.clip(CircleShape).clickable { options = true }.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = Mf.colors.accent)
        }
    }
    if (options) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { options = false }, containerColor = Mf.colors.background) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Text(tr("Gong intermédiaire"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 1, 5, 10).forEach { m -> Chip(if (m == 0) tr("Aucun") else trf("%1\$s min", m), m == interval) { interval = m } }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Bruit blanc"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Toggle(sh.meditationNoise, { on -> SuperPrefs.update { it.copy(meditationNoise = on) } })
            }
        }
    }
    val programs = Meditation.content().programs
    if (programs.isNotEmpty()) {
        SectionLabel(tr("Programmes"))
        GroupCard {
            programs.forEachIndexed { i, pr ->
                if (i > 0) RowDivider()
                val done = remember(log) { Meditation.programDone(pr) }
                val next = pr.sessions.getOrNull(done)?.let { Meditation.session(it) }
                Row(Modifier.fillMaxWidth().clickable(enabled = next != null) {
                    next?.let { s -> Meditation.current.value = Meditation.Play(s.title, s.minutes, s.lines, kind = "program:${pr.id}:$done"); nav.go("meditation/play") }
                }.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pr.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (next != null) trf("%1\$s sur %2\$s · Ensuite : %3\$s", done, pr.sessions.size, next.title) else tr("Terminé"), style = MaterialTheme.typography.bodySmall,
                            color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (next != null) Icon(Icons.Rounded.PlayArrow, null, tint = MeditationHue.onContainer())
                }
            }
        }
    }
    SectionLabel(tr("Séances guidées"))
    GroupCard {
        Meditation.content().sessions.forEachIndexed { i, s ->
            if (i > 0) RowDivider()
            Row(Modifier.fillMaxWidth().clickable {
                Meditation.current.value = Meditation.Play(s.title, s.minutes, s.lines, kind = "guided"); nav.go("meditation/play")
            }.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(MeditationHue.container()), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SelfImprovement, null, Modifier.size(21.dp), tint = MeditationHue.onContainer())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.about, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
            }
        }
        RowDivider()
        Row(Modifier.fillMaxWidth().clickable { nav.go("breathe") }.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(HealthHues.Respiration.container()), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Air, null, Modifier.size(21.dp), tint = HealthHues.Respiration.onContainer())
            }
            Spacer(Modifier.width(12.dp))
            Text(tr("Respirer"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
        }
    }
    if (coachAvailable()) {
        Spacer(Modifier.height(10.dp))
        CoachSessionButton(minutes) { nav.go("meditation/play") }
    }
}

/** Séance écrite par le coach pour aujourd'hui (selon la forme, le sommeil, le moral partagés avec lui). */
@Composable
private fun CoachSessionButton(minutes: Int, onReady: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    PillButton(if (busy) tr("Le coach écrit votre séance…") else trf("Séance du coach · %1\$s min", minutes), Modifier.fillMaxWidth(), strong = false, enabled = !busy, icon = Icons.Rounded.AutoAwesome) {
        busy = true; error = null
        scope.launch {
            runCatching {
                val p = Coach.prompts() ?: error(tr("Contenu du plugin introuvable : réinstallez-le dans Réglages > Plugins."))
                val lang = if (app.superhealth.ui.Lang.english) "English" else "French"
                val raw = Coach.once(ctx, p, "Write a guided meditation of exactly $minutes minutes adapted to my day (use my sleep, form and mood if available). " +
                    "Output ONLY lines in the form mm:ss | sentence, in $lang, one short calm sentence per line, spaced by 30 to 90 seconds, first line at 00:05, last line at least 30 seconds before the end. " +
                    "No title, no list, no medical claim.")
                val lines = Meditation.parseScript(raw).filter { it.at < minutes * 60 }
                if (lines.size < 2) error(tr("Réponse inattendue du coach, réessayez."))
                Meditation.current.value = Meditation.Play(tr("Séance du coach"), minutes, lines, kind = "coach")
                onReady()
            }.onFailure { error = it.message }
            busy = false
        }
    }
    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
}

/**
 * Lecture : anneau et temps restant sur fond sombre, écran allumé, gongs aux moments prévus, phrase en cours affichée et lue par la voix
 * du téléphone (certains téléphones n'en ont pas : le texte reste affiché).
 */
@Composable
fun MeditationPlayScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val play by Meditation.current.collectAsState()
    val p = play ?: return
    KeepScreenOn()
    val total = p.minutes * 60
    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    var running by rememberSaveable { mutableStateOf(true) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var voice by remember { mutableStateOf(p.lines.isEmpty()) }
    val tts = remember { arrayOfNulls<TextToSpeech>(1) }
    val noise = remember { arrayOfNulls<MediaPlayer>(1) }
    val gongs = remember { ArrayList<MediaPlayer>() }
    fun gong(name: String) {
        val f = Meditation.audio(name) ?: return
        runCatching {
            val mp = MediaPlayer().apply { setDataSource(f.path); prepare(); setOnCompletionListener { it.release(); gongs.remove(it) }; start() }
            gongs += mp
        }
    }
    DisposableEffect(Unit) {
        if (p.lines.isNotEmpty()) tts[0] = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val ok = tts[0]?.setLanguage(app.superhealth.ui.Lang.locale)
                voice = ok != null && ok >= TextToSpeech.LANG_AVAILABLE
                tts[0]?.setSpeechRate(0.85f)
            }
        }
        if (p.noise) Meditation.audio("white_noise_player")?.let { f ->
            noise[0] = runCatching { MediaPlayer().apply { setDataSource(f.path); isLooping = true; setVolume(0.25f, 0.25f); prepare(); start() } }.getOrNull()
        }
        gong("gong_timer")
        onDispose {
            tts[0]?.stop(); tts[0]?.shutdown()
            noise[0]?.runCatching { stop(); release() }
            gongs.toList().forEach { it.runCatching { release() } }
        }
    }
    LaunchedEffect(running, finished) {
        noise[0]?.runCatching { if (running && !finished) start() else pause() }
        while (running && !finished) {
            delay(1000)
            elapsed++
            p.lines.firstOrNull { it.at == elapsed }?.let { l -> tts[0]?.speak(l.text, TextToSpeech.QUEUE_FLUSH, null, "l$elapsed") }
            if (p.intervalMin > 0 && elapsed < total && elapsed % (p.intervalMin * 60) == 0) gong("gong_timer_inter")
            if (elapsed >= total) {
                finished = true
                gong("gong_timer")
                Meditation.record(ctx, p.minutes, p.kind)
            }
        }
    }
    val current = p.lines.lastOrNull { it.at <= elapsed }
    val bg = Color(0xFF10121A)
    // fermer ou revenir en arrière garde le temps déjà fait
    fun close() {
        if (!finished && elapsed >= 60) Meditation.record(ctx, (elapsed + 30) / 60, p.kind)
        Meditation.current.value = null; nav.popBackStack()
    }
    androidx.activity.compose.BackHandler { close() }
    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { close() }) { Icon(Icons.Rounded.Close, tr("Fermer"), tint = Color.White) }
            Text(p.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val hue = MeditationHue
            val f = (elapsed.toFloat() / total).coerceIn(0f, 1f)
            Canvas(Modifier.size(240.dp)) {
                drawArc(Color.White.copy(alpha = 0.1f), 0f, 360f, false, style = Stroke(8.dp.toPx()))
                drawArc(hue, -90f, 360f * f, false, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
            }
            val left = (total - elapsed).coerceAtLeast(0)
            Text(if (finished) tr("Terminé") else "%d:%02d".format(left / 60, left % 60), color = Color.White, fontSize = if (finished) 30.sp else 44.sp, fontWeight = FontWeight.Medium)
        }
        Text(current?.text ?: "", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 24.dp))
        if (p.lines.isNotEmpty() && !voice) Text(tr("Pas de voix sur ce téléphone : le texte reste affiché."), color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp))
        Box(Modifier.fillMaxWidth().padding(bottom = 20.dp), contentAlignment = Alignment.Center) {
            if (finished) PillButton(tr("Terminer"), Modifier.padding(horizontal = 24.dp).fillMaxWidth()) { Meditation.current.value = null; nav.popBackStack() }
            else Box(Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(Color.White.copy(alpha = 0.14f)).clickable { running = !running }, contentAlignment = Alignment.Center) {
                Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tr(if (running) "Pause" else "Reprendre"), tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}
