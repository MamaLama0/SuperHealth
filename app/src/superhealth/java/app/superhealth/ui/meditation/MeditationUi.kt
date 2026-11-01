package app.superhealth.ui.meditation

import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
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

/** Ambiances du plugin : identifiant du fichier, libellé. */
private val ambiences = listOf("" to "Aucune", "rain" to "Pluie", "ocean" to "Océan", "brown" to "Bruit doux")
/** Thèmes des séances, dans l'ordre d'affichage. */
private val themes = listOf("calm" to "Calme", "sleep" to "Sommeil", "focus" to "Concentration", "mood" to "Moral", "recover" to "Récupération")

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) =
    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, color = if (on) MeditationHue.onContainer() else Mf.colors.text,
        modifier = Modifier.clip(CircleShape).background(if (on) MeditationHue.container() else Mf.colors.chip).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp))

@Composable
private fun ChipRow(content: @Composable () -> Unit) =
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }

/** Grand bouton carré : symbole et nom, un seul geste. */
@Composable
private fun Tile(icon: ImageVector, label: String, hue: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Mf.colors.card).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(hue.container()), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = hue.onContainer())
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Section Méditation de l'onglet Activités, version simple des apps de méditation : la séance du jour d'un toucher, le minuteur, la respiration,
 * le calme tout de suite ; puis les programmes et les séances par thème.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.MeditationSection(nav: NavHostController) {
    val log by Meditation.log.collectAsState()
    val sh by SuperPrefs.settings.collectAsState()
    var timer by remember { mutableStateOf(false) }
    var theme by rememberSaveable { mutableStateOf("calm") }
    val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
    val week = remember(log) { Meditation.minutes(monday) }
    val streak = remember(log) { Meditation.streak() }
    val daily = remember(log) { Meditation.daily() }
    val content = Meditation.content()

    daily?.let { (s, kind) ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2C3566), Color(0xFF4A5AA8))))
            .clickable { Meditation.play(s, kind); nav.go("meditation/play") }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Séance du jour"), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
                Text(s.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                Text(listOfNotNull(trf("%1\$s min", s.minutes), if (week > 0) trf("%1\$s min cette semaine", week) else null, if (streak > 1) trf("%1\$s jours d'affilée", streak) else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(top = 4.dp), maxLines = 2)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(58.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, tr("Commencer"), tint = Color(0xFF2C3566), modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile(Icons.Rounded.Timer, tr("Minuteur"), MeditationHue, Modifier.weight(1f)) { timer = true }
        Tile(Icons.Rounded.Air, tr("Respirer"), HealthHues.Respiration, Modifier.weight(1f)) { nav.go("breathe") }
        content.sos?.let { Meditation.session(it) }?.let { s ->
            Tile(Icons.Rounded.Spa, tr("Vite, du calme"), HealthHues.Mind, Modifier.weight(1f)) { Meditation.play(s); nav.go("meditation/play") }
        }
    }

    if (content.programs.isNotEmpty()) {
        SectionLabel(tr("Programmes"))
        GroupCard {
            content.programs.forEachIndexed { i, pr ->
                if (i > 0) RowDivider()
                val done = remember(log) { Meditation.programDone(pr) }
                val next = pr.sessions.getOrNull(done)?.let { Meditation.session(it) }
                Row(Modifier.fillMaxWidth().clickable(enabled = next != null) { next?.let { s -> Meditation.play(s, "program:${pr.id}:$done"); nav.go("meditation/play") } }
                    .padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    // étape du parcours : le nombre fait, ou la coche à la fin
                    Box(Modifier.size(38.dp).clip(CircleShape).background(if (next == null) MeditationHue else MeditationHue.container()), contentAlignment = Alignment.Center) {
                        if (next == null) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        else Text("${done + 1}", style = MaterialTheme.typography.titleSmall, color = MeditationHue.onContainer())
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pr.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (next != null) trf("%1\$s sur %2\$s · Ensuite : %3\$s", done, pr.sessions.size, next.title) else tr("Terminé"), style = MaterialTheme.typography.bodySmall,
                            color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (next != null) Icon(Icons.Rounded.PlayArrow, tr("Commencer"), tint = MeditationHue.onContainer())
                }
            }
        }
    }

    SectionLabel(tr("Séances guidées"))
    ChipRow { themes.forEach { (id, label) -> Chip(tr(label), id == theme) { theme = id } } }
    Spacer(Modifier.height(8.dp))
    GroupCard {
        content.sessions.filter { it.theme == theme }.forEachIndexed { i, s ->
            if (i > 0) RowDivider()
            Row(Modifier.fillMaxWidth().clickable { Meditation.play(s); nav.go("meditation/play") }.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(MeditationHue.container()), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SelfImprovement, null, Modifier.size(21.dp), tint = MeditationHue.onContainer())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.about, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Text(trf("%1\$s min", s.minutes), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
            }
        }
    }
    if (coachAvailable()) {
        Spacer(Modifier.height(10.dp))
        CoachSessionButton { nav.go("meditation/play") }
    }

    if (timer) {
        var minutes by rememberSaveable { mutableIntStateOf(10) }
        var interval by rememberSaveable { mutableIntStateOf(0) }
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { timer = false }, containerColor = Mf.colors.background) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
                Text(tr("Minuteur"), style = MaterialTheme.typography.titleLarge)
                SectionLabel(tr("Durée"))
                ChipRow { listOf(3, 5, 10, 15, 20, 30).forEach { m -> Chip(trf("%1\$s min", m), m == minutes) { minutes = m } } }
                SectionLabel(tr("Gong intermédiaire"))
                ChipRow { listOf(0, 1, 5, 10).forEach { m -> Chip(if (m == 0) tr("Aucun") else trf("%1\$s min", m), m == interval) { interval = m } } }
                SectionLabel(tr("Ambiance"))
                ChipRow { ambiences.forEach { (id, label) -> Chip(tr(label), id == sh.meditationAmbience) { SuperPrefs.update { it.copy(meditationAmbience = id) } } } }
                Spacer(Modifier.height(16.dp))
                PillButton(tr("Commencer"), Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow) {
                    Meditation.current.value = Meditation.Play(tr("Méditation libre"), minutes, intervalMin = interval)
                    timer = false
                    nav.go("meditation/play")
                }
            }
        }
    }
}

/** Séance écrite par le coach pour aujourd'hui (selon la forme, le sommeil, le moral partagés avec lui). */
@Composable
private fun CoachSessionButton(onReady: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val minutes = 10
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
 * Lecture : anneau et temps restant sur fond sombre, écran allumé, gongs aux moments prévus, ambiance au choix, voix enregistrée du plugin.
 * La voix du téléphone ne sert qu'aux séances du coach ; sans voix sur le téléphone, le texte reste affiché.
 */
@Composable
fun MeditationPlayScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val play by Meditation.current.collectAsState()
    val sh by SuperPrefs.settings.collectAsState()
    val p = play ?: return
    KeepScreenOn()
    val total = p.minutes * 60
    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    var running by rememberSaveable { mutableStateOf(true) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    val recorded = p.session != null && Meditation.voice(p.session, 0) != null
    val needsTts = p.lines.isNotEmpty() && !recorded
    val tts = remember { arrayOfNulls<TextToSpeech>(1) }
    val loop = remember { arrayOfNulls<MediaPlayer>(1) }
    val sounds = remember { ArrayList<MediaPlayer>() }
    fun sound(path: String) {
        runCatching {
            val mp = MediaPlayer().apply { setDataSource(path); prepare(); setOnCompletionListener { it.release(); sounds.remove(it) }; start() }
            sounds += mp
        }
    }
    fun gong(name: String) { Meditation.audio(name)?.let { sound(it.path) } }
    fun say(i: Int) {
        val file = p.session?.let { Meditation.voice(it, i) }
        if (file != null) sound(file.path) else tts[0]?.speak(p.lines[i].text, TextToSpeech.QUEUE_FLUSH, null, "l$i")
    }
    DisposableEffect(Unit) {
        if (needsTts) tts[0] = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val ok = tts[0]?.setLanguage(app.superhealth.ui.Lang.locale)
                ttsReady = ok != null && ok >= TextToSpeech.LANG_AVAILABLE
                tts[0]?.setSpeechRate(0.85f)
            }
        }
        gong("gong_timer")
        onDispose {
            tts[0]?.stop(); tts[0]?.shutdown()
            loop[0]?.runCatching { stop(); release() }
            sounds.toList().forEach { it.runCatching { release() } }
        }
    }
    // changer d'ambiance pendant la séance : la boucle repart aussitôt
    LaunchedEffect(sh.meditationAmbience) {
        loop[0]?.runCatching { stop(); release() }
        loop[0] = Meditation.ambience(sh.meditationAmbience)?.let { f ->
            runCatching { MediaPlayer().apply { setDataSource(f.path); isLooping = true; setVolume(0.28f, 0.28f); prepare(); if (running && !finished) start() } }.getOrNull()
        }
    }
    LaunchedEffect(running, finished) {
        loop[0]?.runCatching { if (running && !finished) start() else pause() }
        sounds.toList().forEach { it.runCatching { if (running) start() else if (isPlaying) pause() } }
        while (running && !finished) {
            delay(1000)
            elapsed++
            p.lines.indexOfFirst { it.at == elapsed }.takeIf { it >= 0 }?.let { say(it) }
            if (p.intervalMin > 0 && elapsed < total && elapsed % (p.intervalMin * 60) == 0) gong("gong_timer_inter")
            if (elapsed >= total) {
                finished = true
                gong("gong_timer")
                Meditation.record(ctx, p.minutes, p.kind)
            }
        }
    }
    val current = p.lines.lastOrNull { it.at <= elapsed }
    // fermer ou revenir en arrière garde le temps déjà fait
    fun close() {
        if (!finished && elapsed >= 60) Meditation.record(ctx, (elapsed + 30) / 60, p.kind)
        Meditation.current.value = null; nav.popBackStack()
    }
    androidx.activity.compose.BackHandler { close() }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B2145), Color(0xFF0E1020)))).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { close() }) { Icon(Icons.Rounded.Close, tr("Fermer"), tint = Color.White) }
            Text(p.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val f = (elapsed.toFloat() / total).coerceIn(0f, 1f)
            Canvas(Modifier.size(260.dp)) {
                drawArc(Color.White.copy(alpha = 0.1f), 0f, 360f, false, style = Stroke(6.dp.toPx()))
                drawArc(MeditationHue, -90f, 360f * f, false, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            }
            val left = (total - elapsed).coerceAtLeast(0)
            Text(if (finished) tr("Terminé") else "%d:%02d".format(left / 60, left % 60), color = Color.White, fontSize = if (finished) 30.sp else 46.sp, fontWeight = FontWeight.Medium)
        }
        Text(current?.text ?: "", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 24.dp))
        if (needsTts && !ttsReady) Text(tr("Pas de voix sur ce téléphone : le texte reste affiché."), color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp))
        // ambiance : un seul rang, au-dessus du bouton
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            ambiences.forEach { (id, label) ->
                val on = id == sh.meditationAmbience
                Text(tr(label), style = MaterialTheme.typography.labelMedium, maxLines = 1, color = if (on) Color(0xFF0E1020) else Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.clip(CircleShape).background(if (on) Color.White else Color.White.copy(alpha = 0.12f))
                        .clickable { SuperPrefs.update { it.copy(meditationAmbience = id) } }.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
        Box(Modifier.fillMaxWidth().padding(bottom = 22.dp), contentAlignment = Alignment.Center) {
            if (finished) PillButton(tr("Terminer"), Modifier.padding(horizontal = 24.dp).fillMaxWidth()) { Meditation.current.value = null; nav.popBackStack() }
            else Box(Modifier.size(72.dp).clip(CircleShape).background(Color.White).clickable { running = !running }, contentAlignment = Alignment.Center) {
                Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tr(if (running) "Pause" else "Reprendre"), tint = Color(0xFF1B2145), modifier = Modifier.size(38.dp))
            }
        }
    }
}
