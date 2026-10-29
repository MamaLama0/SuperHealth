package app.superhealth.ui.coach

import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.CardSize
import app.superhealth.DashCard
import app.superhealth.SuperPrefs
import app.superhealth.coach.Coach
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.nutrition.ui.components.TextInput
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.dashboard.Tile
import app.superhealth.ui.dashboard.cardClick
import app.superhealth.ui.dashboard.square
import app.superhealth.ui.go
import app.superhealth.ui.theme.CardRadius
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.timeOf
import app.superhealth.ui.tr
import kotlinx.coroutines.launch
import app.superhealth.ui.theme.dashCardBackground

/** Violet du coach : la même teinte que sa carte et son module. */
internal val CoachHue = Color(0xFF8E7CF0)

/** Question à poser en ouvrant le coach (depuis une mesure, une séance…), consommée par l'écran. */
object CoachRequest { val pending = mutableStateOf<String?>(null) }

/** Ouvre le coach avec une question déjà posée. */
fun NavHostController.askCoach(question: String) { CoachRequest.pending.value = question; go("coach") }

/** Le coach peut-il répondre ? (module actif et IA configurée) */
fun coachAvailable(): Boolean = app.superhealth.modules.Modules.isActive(app.superhealth.modules.Modules.COACH)

/** Pastille du coach : étincelle blanche sur un dégradé violet. */
@Composable
internal fun CoachAvatar(size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFA594FF), CoachHue, Color(0xFF6A5AE0)))),
        contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(size * 0.55f), tint = Color.White) }
}

/** Texte du modèle : **gras** et puces, rien d'autre (les réponses sont courtes). */
internal fun rich(text: String): AnnotatedString = buildAnnotatedString {
    text.lines().forEachIndexed { i, raw ->
        if (i > 0) append("\n")
        var line = raw.trimEnd()
        val bullet = Regex("""^\s*([-*•]|\d+[.)])\s+""").find(line)
        if (bullet != null) { append(if (bullet.value.trim().first().isDigit()) bullet.value.trim() + " " else "•  "); line = line.substring(bullet.range.last + 1) }
        line = line.trimStart('#', ' ').takeIf { raw.trimStart().startsWith("#") } ?: line
        var k = 0
        val parts = line.split("**")
        parts.forEach { p ->
            if (k % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(p) } else append(p)
            k++
        }
    }
}

/**
 * Carte « Coach » (point du jour) : petit (l'avatar), moyen et bandeau (la phrase du jour), grand (la phrase, les points
 * et deux questions à poser). Préparé une fois par jour à l'affichage, si le point du jour est activé.
 */
@Composable
fun CoachCard(c: DashCard, size: CardSize, vm: SuperViewModel, modifier: Modifier, nav: NavHostController) {
    val ctx = LocalContext.current
    val sh by vm.settings.collectAsState()
    val brief by Coach.brief.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val today = brief?.takeIf { Coach.briefIsToday() }
    val scope = rememberCoroutineScope()
    fun refresh() {
        if (busy || !Coach.ready()) return
        busy = true; error = null
        scope.launch { runCatching { Coach.makeBrief(ctx) }.onFailure { error = it.message }; busy = false }
    }
    LaunchedEffect(sh.coachBrief) { if (sh.coachBrief && !Coach.briefIsToday()) refresh() }
    val open: () -> Unit = { nav.go("coach") }
    val headline = today?.headline ?: when {
        busy -> tr("Le coach prépare le point du jour…")
        !Coach.ready() -> tr("Configurez l'IA pour parler au coach")
        error != null -> tr("Point du jour indisponible")
        else -> tr("Une question sur ta journée ?")
    }
    val box = modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).then(Modifier.dashCardBackground())
        .cardClick(open)
    when (size) {
        CardSize.S -> Box(box.square().padding(10.dp), contentAlignment = Alignment.Center) { CoachAvatar(46.dp) }
        CardSize.M -> Column(box.padding(12.dp)) {
            CoachAvatar(30.dp)
            Spacer(Modifier.height(8.dp))
            Text(headline, style = MaterialTheme.typography.titleSmall, color = CoachHue.onContainer(), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        CardSize.W -> Row(box.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CoachAvatar(32.dp)
            Spacer(Modifier.width(10.dp))
            Text(headline, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = CoachHue.onContainer(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        CardSize.L -> Tile(c, tr("Coach"), modifier, trailing = today?.let { b -> if (b.at > 0) timeOf(b.at) else tr("Point du jour") }, onClick = open) {
            Text(headline, style = MaterialTheme.typography.titleMedium, color = CoachHue.onContainer())
            today?.points?.forEach { p ->
                Row(Modifier.padding(top = 6.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(CoachHue))
                    Spacer(Modifier.width(8.dp))
                    Text(rich(p), style = MaterialTheme.typography.bodyMedium)
                }
            }
            val sugg = Coach.prompts()?.suggestions.orEmpty().take(2)
            if (Coach.ready()) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // nouveau point du jour (données de la journée qui ont changé)
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Mf.colors.card.copy(alpha = 0.85f)).clickable(enabled = !busy) { refresh() },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Refresh, tr("Actualiser le point du jour"), Modifier.size(18.dp), tint = CoachHue.onContainer().copy(alpha = if (busy) 0.4f else 1f))
                    }
                    sugg.forEach { s -> SuggestionChip(s.text) { nav.askCoach(s.text) } }
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = CoachHue.onContainer(), maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(Mf.colors.card.copy(alpha = 0.85f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp))
}

/**
 * Conversation avec le coach : questions suggérées, réponses à partir de vos données, proposition de retenir un fait
 * (à valider), et à tout moment ce qui lui est envoyé.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val chat by Coach.chat.collectAsState()
    val sh by vm.settings.collectAsState()
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var proposal by remember { mutableStateOf<String?>(null) }
    var showContext by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val list = rememberLazyListState()
    val ready = Coach.ready()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    /** Lance une demande au modèle (question, nouvel essai, autre réponse) ; annulable. */
    fun run(call: suspend () -> Coach.Msg) {
        if (thinking) return
        error = null; proposal = null; thinking = true
        job = scope.launch {
            try {
                val m = call()
                proposal = Coach.split(m.text).second?.takeIf { f -> sh.coachMemory.none { x -> x.equals(f, true) } }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) { error = e.message ?: e.javaClass.simpleName
            } finally { thinking = false; job = null }
        }
    }
    fun send(q: String) {
        if (q.isBlank() || thinking) return
        input = ""
        run { Coach.ask(ctx, q) }
    }
    fun stop() { job?.cancel(); job = null; thinking = false }
    LaunchedEffect(Unit) { CoachRequest.pending.value?.let { q -> CoachRequest.pending.value = null; if (ready) send(q) else input = q } }
    // séparateurs de jour, erreur et proposition : on descend jusqu'au dernier élément réel de la liste
    LaunchedEffect(chat.size, thinking, error) { kotlinx.coroutines.yield(); val n = list.layoutInfo.totalItemsCount; if (n > 1) list.animateScrollToItem(n - 1) }

    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour")) }
            CoachAvatar(30.dp)
            Spacer(Modifier.width(10.dp))
            Text(tr("Coach"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { showContext = runCatching { Coach.context(ctx) }.getOrElse { it.message ?: "?" } } }) {
                Icon(Icons.Rounded.Visibility, tr("Ce que voit le coach"), tint = Mf.colors.textSecondary)
            }
            IconButton(onClick = { settings = true }) { Icon(Icons.Rounded.Tune, tr("Réglages du coach"), tint = Mf.colors.textSecondary) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "intro") { Intro(ready, sh.coachMemory.size, nav) }
            chat.forEachIndexed { i, m ->
                // séparateur de jour quand la conversation change de date
                val day = java.time.Instant.ofEpochMilli(m.time).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                val prevDay = chat.getOrNull(i - 1)?.let { java.time.Instant.ofEpochMilli(it.time).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
                if (day != prevDay) item(key = "day$day") { DaySeparator(day) }
                item(key = m.time.toString() + m.role) {
                    val last = i == chat.lastIndex
                    Bubble(m, onCopy = { clipboard.setText(AnnotatedString(if (m.role == "user") m.text else Coach.split(m.text).first)) },
                        onRegenerate = if (last && m.role == "assistant" && ready && !thinking) ({ run { Coach.regenerate(ctx) } }) else null,
                        onEdit = if (last && m.role == "user" && !thinking) ({ Coach.dropPendingQuestion()?.let { input = it }; error = null }) else null)
                }
            }
            if (thinking) item(key = "thinking") { Thinking(onStop = { stop() }) }
            error?.let { e -> item(key = "error") {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(app.superhealth.ui.theme.Base.Red.container())
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(e, style = MaterialTheme.typography.bodySmall, color = app.superhealth.ui.theme.Base.Red.onContainer(), modifier = Modifier.weight(1f))
                    if (chat.lastOrNull()?.role == "user") androidx.compose.material3.TextButton(onClick = { run { Coach.answer(ctx) } }) {
                        Text(tr("Réessayer"), color = app.superhealth.ui.theme.Base.Red.onContainer())
                    }
                }
            } }
            proposal?.let { f ->
                item(key = "remember") {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CoachHue.container()).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Retenir ?"), style = MaterialTheme.typography.labelMedium, color = CoachHue.onContainer())
                            Text(f, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { Coach.remember(f); proposal = null }) { Icon(Icons.Rounded.Check, tr("Retenir"), tint = CoachHue.onContainer()) }
                        IconButton(onClick = { proposal = null }) { Icon(Icons.Rounded.Close, tr("Non"), tint = Mf.colors.textSecondary) }
                    }
                }
            }
        }
        val sugg = Coach.prompts()?.suggestions.orEmpty()
        if (ready && sugg.isNotEmpty() && !thinking && (chat.isEmpty() || chat.last().role == "assistant")) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sugg.forEach { s ->
                    Text(s.text, style = MaterialTheme.typography.labelLarge, color = CoachHue.onContainer(), maxLines = 1,
                        modifier = Modifier.clip(CircleShape).background(CoachHue.container()).clickable { send(s.text) }.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Mf.colors.card).padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (input.isEmpty()) Text(if (ready) tr("Demande au coach…") else tr("Configurez d'abord l'IA"), color = Mf.colors.textTertiary)
                BasicTextField(input, { input = it }, enabled = ready, textStyle = MaterialTheme.typography.bodyLarge.copy(color = Mf.colors.text),
                    cursorBrush = SolidColor(CoachHue), maxLines = 5, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { send(input) }))
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(48.dp).clip(CircleShape).background(if (input.isNotBlank() && !thinking) CoachHue else Mf.colors.chip)
                .clickable(enabled = input.isNotBlank() && !thinking) { send(input) }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.Send, tr("Envoyer"), tint = if (input.isNotBlank() && !thinking) Color.White else Mf.colors.textTertiary)
            }
        }
    }

    showContext?.let { text ->
        ModalBottomSheet(onDismissRequest = { showContext = null }, containerColor = Mf.colors.background) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
                Text(tr("Ce que voit le coach"), style = MaterialTheme.typography.titleLarge)
                Text(tr("Seul ce résumé est envoyé au fournisseur IA, avec vos questions. Rien d'autre ne quitte le téléphone."),
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
                Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Mf.colors.card).padding(12.dp))
            }
        }
    }
    if (settings) CoachSettings(nav) { settings = false }
}

@Composable
private fun Intro(ready: Boolean, memories: Int, nav: NavHostController) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CoachAvatar(64.dp)
        Spacer(Modifier.height(10.dp))
        Text(tr("Ton coach santé"), style = MaterialTheme.typography.titleLarge)
        Text(tr("Il répond à partir de tes repas, de ton poids, de tes séances et de ta montre."),
            style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (!ready) {
            Spacer(Modifier.height(12.dp))
            PillButton(tr("Configurer l'IA"), icon = Icons.Rounded.AutoAwesome) { nav.go("aisettings") }
        }
    }
}

@Composable
private fun DaySeparator(day: java.time.LocalDate) {
    val today = java.time.LocalDate.now()
    val label = when (day) {
        today -> tr("Aujourd'hui")
        today.minusDays(1) -> tr("Hier")
        else -> day.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", app.superhealth.ui.Lang.locale)).replaceFirstChar { it.uppercase() }
    }
    Text(label, Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        style = MaterialTheme.typography.labelMedium, color = Mf.colors.textTertiary)
}

/** Bulle ; appui long : copier, et sur le dernier message, autre réponse ou modifier la question. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: Coach.Msg, onCopy: () -> Unit, onRegenerate: (() -> Unit)?, onEdit: (() -> Unit)?) {
    val mine = m.role == "user"
    val text = if (mine) m.text else Coach.split(m.text).first
    var menu by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!mine) { CoachAvatar(26.dp); Spacer(Modifier.width(8.dp)) }
        Box {
        androidx.compose.material3.DropdownMenu(menu, { menu = false }, containerColor = Mf.colors.card) {
            androidx.compose.material3.DropdownMenuItem({ Text(tr("Copier")) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; onCopy() })
            onRegenerate?.let { f -> androidx.compose.material3.DropdownMenuItem({ Text(tr("Autre réponse")) }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                onClick = { menu = false; f() }) }
            onEdit?.let { f -> androidx.compose.material3.DropdownMenuItem({ Text(tr("Modifier")) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                onClick = { menu = false; f() }) }
        }
        Column(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (mine) 20.dp else 6.dp, bottomEnd = if (mine) 6.dp else 20.dp))
            .background(if (mine) Mf.colors.strong else Mf.colors.card)
            .combinedClickable(onClick = {}, onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menu = true })
            .padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(if (mine) AnnotatedString(text) else rich(text), style = MaterialTheme.typography.bodyMedium, color = if (mine) Mf.colors.onStrong else Mf.colors.text)
            Text(timeOf(m.time), style = MaterialTheme.typography.labelSmall, color = (if (mine) Mf.colors.onStrong else Mf.colors.textTertiary).copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp))
        }
        }
    }
}

/** Le coach écrit : trois points qui s'éclairent tour à tour (seulement pendant l'attente). */
@Composable
private fun Thinking(onStop: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { while (true) { t.snapTo(0f); t.animateTo(3f, tween(1200)) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        CoachAvatar(26.dp)
        Spacer(Modifier.width(8.dp))
        Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Mf.colors.card).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                val on = t.value.toInt() == i
                Box(Modifier.size(7.dp).clip(CircleShape).background(CoachHue).alpha(if (on) 1f else 0.35f))
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(tr("Arrêter"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onStop).padding(horizontal = 10.dp, vertical = 8.dp))
    }
}

/** Réglages du coach : ton, point du jour, mémoire (lire, retirer, ajouter), modèle, effacer la conversation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoachSettings(nav: NavHostController, onDismiss: () -> Unit) {
    val sh by SuperPrefs.settings.collectAsState()
    var fact by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Mf.colors.background) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(tr("Réglages du coach"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            Text(tr("Ton"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
            Spacer(Modifier.height(6.dp))
            ChipChoice(Coach.Tone.entries, runCatching { Coach.Tone.valueOf(sh.coachTone) }.getOrDefault(Coach.Tone.WARM), { tr(it.label) }) { t ->
                SuperPrefs.update { it.copy(coachTone = t.name) }
            }
            Spacer(Modifier.height(10.dp))
            MfCard(padding = PaddingValues(0.dp)) {
                SwitchRow(tr("Point du jour"), tr("Préparé chaque matin sur la carte Coach"), sh.coachBrief) { v -> SuperPrefs.update { it.copy(coachBrief = v) } }
            }
            SectionLabel(tr("Ce qu'il retient"))
            MfCard {
                if (sh.coachMemory.isEmpty()) Text(tr("Rien pour l'instant. Le coach propose de retenir ce qui compte (objectif, blessure, horaires) : vous validez."),
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                sh.coachMemory.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { Coach.forget(m) }) { Icon(Icons.Rounded.Close, tr("Oublier"), tint = Mf.colors.textSecondary) }
                    }
                }
                TextInput(tr("Ajouter"), fact, { fact = it.take(160) }, placeholder = tr("Ex. : genou gauche fragile"))
                if (fact.isNotBlank()) PillButton(tr("Retenir"), Modifier.fillMaxWidth(), strong = false) { Coach.remember(fact); fact = "" }
            }
            SectionLabel(tr("Données envoyées"))
            MfCard(padding = PaddingValues(0.dp)) {
                // le cycle n'est proposé qu'avec son suivi activé
                Coach.Share.entries.filter { it != Coach.Share.CYCLE || sh.features.contains(app.superhealth.Feature.CYCLE.name) }.forEach { c ->
                    val on = if (c.optIn) c.name in sh.coachShared else c.name !in sh.coachHidden
                    SwitchRow(tr(c.label), if (c.optIn && !on) tr("Coupé tant que vous ne l'activez pas") else null, on) { v -> Coach.setShared(c, v) }
                }
            }
            Text(tr("Moral et cycle ne partent que si vous les activez. Le détail : « Ce que voit le coach », en haut."), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
            SectionLabel(tr("Modèle"))
            MfCard {
                TextInput(tr("Modèle du coach (vide : celui de l'IA)"), sh.coachModel, { v -> SuperPrefs.update { it.copy(coachModel = v.trim()) } },
                    placeholder = "deepseek/deepseek-v4.1-flash")
                Text(tr("Un modèle plus grand répond mieux aux questions d'entraînement ; le fournisseur et la clé sont ceux de l'IA."),
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
                Spacer(Modifier.height(6.dp))
                PillButton(tr("Fournisseur et clé"), Modifier.fillMaxWidth(), strong = false) { onDismiss(); nav.go("aisettings") }
            }
            Spacer(Modifier.height(14.dp))
            var confirmClear by remember { mutableStateOf(false) }
            PillButton(tr("Effacer la conversation"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.DeleteOutline) { confirmClear = true }
            if (confirmClear) androidx.compose.material3.AlertDialog(onDismissRequest = { confirmClear = false }, containerColor = Mf.colors.card,
                title = { Text(tr("Effacer la conversation ?")) }, text = { Text(tr("Ce qu'il retient est gardé.")) },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmClear = false; Coach.clearChat(); onDismiss() }) {
                    Text(tr("Effacer"), color = app.superhealth.ui.theme.Base.Red) } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmClear = false }) { Text(tr("Annuler"), color = Mf.colors.text) } })
        }
    }
}
