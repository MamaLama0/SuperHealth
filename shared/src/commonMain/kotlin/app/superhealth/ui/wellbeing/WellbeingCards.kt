package app.superhealth.ui.wellbeing

import app.superhealth.platform.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentNeutral
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.SentimentVeryDissatisfied
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import app.superhealth.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.CardSize
import app.superhealth.DashCard
import app.superhealth.Feature
import app.superhealth.SuperPrefs
import app.superhealth.has
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.training.domain.SessionExercise
import app.superhealth.training.domain.SetType
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.dashboard.CompactBubble
import app.superhealth.ui.dashboard.CompactData
import app.superhealth.ui.dashboard.CompactMedium
import app.superhealth.ui.dashboard.CompactStrip
import app.superhealth.ui.dashboard.Tile
import app.superhealth.ui.go
import app.superhealth.ui.theme.Base
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.wellbeing.Bedtime
import app.superhealth.wellbeing.BodyAlert
import app.superhealth.wellbeing.Checkin
import app.superhealth.wellbeing.Cycle
import app.superhealth.wellbeing.Form
import app.superhealth.wellbeing.Journal
import app.superhealth.platform.LocalDate

/** Rose du cycle, distinct du rouge du cœur. */
val CycleHue = Color(0xFFE0609A)

/** Visage de chaque humeur (1 à 5). */
fun moodIcon(mood: Int): ImageVector = when (mood) {
    1 -> Icons.Rounded.SentimentVeryDissatisfied
    2 -> Icons.Rounded.SentimentDissatisfied
    3 -> Icons.Rounded.SentimentNeutral
    4 -> Icons.Rounded.SentimentSatisfied
    else -> Icons.Rounded.SentimentVerySatisfied
}

/** Couleur d'une humeur, du rouge au vert (jamais assombrie vers le brun). */
fun moodColor(mood: Int): Color = when (mood) {
    1 -> Base.Red
    2 -> Base.Orange
    3 -> Base.Yellow
    4 -> Base.Green
    else -> Base.Cyan
}

/** Les cinq humeurs, à toucher. */
@Composable
fun MoodPicker(selected: Int?, modifier: Modifier = Modifier, labels: List<String> = Journal.MOODS, onPick: (Int) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (1..5).forEach { m ->
            val on = selected == m
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onPick(m) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(if (on) moodColor(m).container(1.8f) else Color.Transparent),
                    contentAlignment = Alignment.Center) {
                    Icon(moodIcon(m), tr(labels[m - 1]), Modifier.size(30.dp), tint = if (on || selected == null) moodColor(m) else Mf.colors.textTertiary)
                }
                Spacer(Modifier.height(2.dp))
                Text(tr(labels[m - 1]), style = MaterialTheme.typography.labelSmall, color = if (on) Mf.colors.text else Mf.colors.textSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---- Journal du moral -----------------------------------------------------------------------------------------

@Composable
fun JournalCard(c: DashCard, size: CardSize, vm: SuperViewModel, modifier: Modifier, nav: NavHostController) {
    val ctx = LocalContext.current
    val days by Journal.days.collectAsState()
    val today = days[LocalDate.now().toEpochDay()]
    val open: () -> Unit = { nav.go("journal") }
    val d = CompactData(today?.let { tr(Journal.MOODS[it.mood - 1]) } ?: tr("Noter"), null, tr("Moral"), today?.let { it.mood / 5f },
        history = (13L downTo 0L).map { k -> days[LocalDate.now().toEpochDay() - k]?.mood?.toDouble() }, bars = true, onClick = open)
    when (size) {
        CardSize.S -> CompactBubble(c, d, modifier)
        CardSize.M -> CompactMedium(c, d, modifier)
        CardSize.W -> CompactStrip(c, d, modifier)
        CardSize.L -> Tile(c, if (today == null) tr("Comment ça va ?") else tr("Moral du jour"), modifier, onClick = open) {
            MoodPicker(today?.mood) { Journal.setMood(ctx, it) }
            if (today != null && today.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(today.tags.mapNotNull { n -> Journal.Tag.entries.firstOrNull { it.name == n }?.let { tr(it.label) } }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---- Forme du jour --------------------------------------------------------------------------------------------

/** Dernier résultat calculé, gardé entre deux affichages (pas de clignotement au défilement). */
private var formCache: Pair<LocalDate, Form.Result>? = null

@Composable
fun rememberForm(): Form.Result? {
    val ctx = LocalContext.current
    val journal by Journal.days.collectAsState()
    val sh by SuperPrefs.settings.collectAsState()
    val feel by Checkin.days.collectAsState()
    var r by remember { mutableStateOf(formCache?.takeIf { it.first == LocalDate.now() }?.second) }
    LaunchedEffect(journal, sh.features, feel) {
        val v = runCatching { Form.today(ctx, sh.has(Feature.JOURNAL)) }.getOrNull() ?: return@LaunchedEffect
        formCache = LocalDate.now() to v
        r = v
    }
    return r
}

fun Form.Verdict.color(): Color = when (this) {
    Form.Verdict.PUSH -> Base.Green
    Form.Verdict.NORMAL -> Base.Blue
    Form.Verdict.EASY -> Base.Yellow
    Form.Verdict.REST -> Base.Purple
}

@Composable
fun FormCard(c: DashCard, size: CardSize, vm: SuperViewModel, modifier: Modifier, nav: NavHostController) {
    val r = rememberForm()
    val open: () -> Unit = { nav.go("form") }
    val advice = r as? Form.Advice
    val d = CompactData(advice?.verdict?.short?.let(::tr) ?: "—", null, tr("Forme"), advice?.let { a -> (a.signals.sumOf { it.level } + 4) / 8f }, onClick = open)
    when (size) {
        CardSize.S -> CompactBubble(c, d, modifier)
        CardSize.M -> CompactMedium(c, d, modifier)
        CardSize.W -> CompactStrip(c, d, modifier)
        CardSize.L -> Tile(c, tr("Forme du jour"), modifier, onClick = open) { FormBody(r, vm, nav, compact = true) }
    }
}

/** Contenu de la forme du jour : verdict, signaux et proposition, ou ce qui manque. */
@Composable
fun FormBody(r: Form.Result?, vm: SuperViewModel, nav: NavHostController, compact: Boolean) {
    r?.body?.takeIf { it.level != BodyAlert.Level.NONE }?.let { BodyLine(it); Spacer(Modifier.height(10.dp)) }
    when (r) {
        null -> Text(tr("Calcul…"), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
        is Form.Missing -> {
            Text(tr("Pas de conseil sans données complètes."), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            r.what.forEach { k ->
                Text("• " + when (k) {
                    Form.Kind.CHARGE -> trf("Charge : %1\$s nuits de référence", Form.MIN_CHARGE_NIGHTS)
                    Form.Kind.SLEEP -> tr("Sommeil : la nuit dernière et 7 nuits d'historique")
                    Form.Kind.LOAD -> tr("Entraînement : 14 jours de pouls sur 4 semaines")
                    Form.Kind.MOOD -> if (!SuperPrefs.settings.value.has(Feature.JOURNAL)) tr("Moral : activez le journal du moral")
                        else trf("Moral : la note du jour et %1\$s jours notés", Form.MIN_MOOD_DAYS)
                    else -> ""
                }, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
            if (Form.Kind.MOOD in r.what && SuperPrefs.settings.value.has(Feature.JOURNAL) && Journal.today() == null) {
                Spacer(Modifier.height(8.dp))
                PillButton(tr("Noter mon moral"), strong = false) { nav.go("journal") }
            }
        }
        is Form.Advice -> {
            val col = r.verdict.color()
            Text(tr(r.verdict.label), style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = col.onContainer())
            Spacer(Modifier.height(8.dp))
            r.signals.forEach { sg ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(when (sg.level) { 1 -> Base.Green; -1 -> Base.Orange; else -> Mf.colors.textTertiary }))
                    Spacer(Modifier.width(8.dp))
                    Text(tr(sg.kind.label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(sg.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            val program = vm.training.activeProgram()?.takeIf { p -> p.days.getOrNull(p.dayIndex)?.isRest == false }
            when (r.verdict) {
                Form.Verdict.PUSH -> {
                    Text(if (program != null) tr("Tout est au vert : une série de plus sur chaque exercice.")
                        else tr("Tout est au vert : ajoutez un bloc intense, comme 4 × 4 min à 90 % de votre FC max."),
                        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    if (program != null) { Spacer(Modifier.height(8.dp)); PillButton(tr("Séance plus dure")) { startAdjusted(vm, nav, +1) } }
                }
                Form.Verdict.EASY -> {
                    Text(if (program != null) tr("Un signal est bas : une série de moins sur les gros exercices.") else tr("Un signal est bas : restez en endurance facile."),
                        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    if (program != null) { Spacer(Modifier.height(8.dp)); PillButton(tr("Séance allégée"), strong = false) { startAdjusted(vm, nav, -1) } }
                }
                Form.Verdict.REST -> {
                    Text(tr("Plusieurs signaux sont bas : marche, mobilité ou repos."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    PillButton(tr("Respirer 5 min"), strong = false) { nav.go("breathe") }
                }
                Form.Verdict.NORMAL -> Text(tr("Faites la séance prévue."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
        }
    }
}

/**
 * Lance la séance du jour du programme avec une série de travail de plus ([delta] = +1) sur chaque exercice, ou une de
 * moins (−1) sur ceux qui en ont au moins trois.
 */
fun startAdjusted(vm: SuperViewModel, nav: NavHostController, delta: Int) {
    val t = vm.training
    if (t.state.value.session == null) {
        val p = t.activeProgram() ?: return
        t.startProgramDay(p, p.dayIndex, p.cycleIndex)
        t.session { s -> s.copy(adjust = delta, exercises = s.exercises.map { adjustSets(it, delta) }) }
    }
    nav.navigate("session")
}

private fun adjustSets(ex: SessionExercise, delta: Int): SessionExercise {
    val work = ex.sets.filter { it.type != SetType.WARMUP }
    return when {
        delta > 0 && work.isNotEmpty() -> ex.copy(sets = ex.sets + work.last().copy(id = UUID.randomUUID().toString(), done = false, completedAt = null))
        delta < 0 && work.size >= 3 -> ex.copy(sets = ex.sets - work.last())
        else -> ex
    }
}

// ---- Coucher conseillé ------------------------------------------------------------------------------------------

private var bedCache: Pair<LocalDate, Bedtime.Plan?>? = null

@Composable
fun rememberBedtime(): Bedtime.Plan? {
    val ctx = LocalContext.current
    var p by remember { mutableStateOf(bedCache?.takeIf { it.first == LocalDate.now() }?.second) }
    LaunchedEffect(Unit) {
        val v = runCatching { Bedtime.tonight(ctx) }.getOrNull()
        bedCache = LocalDate.now() to v
        p = v
    }
    return p
}

@Composable
fun BedtimeCard(c: DashCard, size: CardSize, modifier: Modifier, nav: NavHostController) {
    val p = rememberBedtime()
    val open: () -> Unit = { nav.go("h/metric/SLEEP") }
    val d = CompactData(p?.let { Bedtime.clock(it.bedMinute) } ?: "—", null, tr("Coucher"), null, onClick = open)
    when (size) {
        CardSize.S -> CompactBubble(c, d, modifier)
        CardSize.M -> CompactMedium(c, d, modifier)
        CardSize.W -> CompactStrip(c, d, modifier)
        CardSize.L -> Tile(c, tr("Coucher conseillé"), modifier, onClick = open) {
            if (p == null) {
                Text(trf("Il faut %1\$s nuits avec heure de coucher et de lever.", Bedtime.MIN_NIGHTS), style = MaterialTheme.typography.bodyMedium,
                    color = Mf.colors.textSecondary)
                return@Tile
            }
            Text(Bedtime.clock(p.bedMinute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = c.hueInk())
            Spacer(Modifier.height(4.dp))
            Text(trf("Réveil %1\$s · besoin %2\$s", Bedtime.clock(p.wakeMinute), hm(p.needMin)) +
                (if (p.debtMin >= 10) trf(" + dette %1\$s", hm(p.debtMin)) else ""),
                style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
            Text(trf("Endormi %1\$s %% du temps au lit", (p.efficiency * 100).toInt()), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
    }
}

private fun hm(min: Double): String = "${(min / 60).toInt()} h ${(min % 60).toInt().toString().padStart(2, '0')}"

@Composable
private fun DashCard.hueInk(): Color = when (this) { DashCard.CYCLE -> CycleHue; else -> app.superhealth.ui.theme.HealthHues.Sleep }.onContainer()

// ---- Cycle -------------------------------------------------------------------------------------------------------

@Composable
fun CycleCard(c: DashCard, size: CardSize, modifier: Modifier, nav: NavHostController) {
    val days by Cycle.days.collectAsState()
    val info = remember(days) { Cycle.info() }
    val open: () -> Unit = { nav.go("cycle") }
    val d = CompactData(info?.let { trf("J%1\$s", it.dayOfCycle) } ?: "—", null, tr("Cycle"),
        info?.length?.let { len -> (info.dayOfCycle.toFloat() / len).coerceAtMost(1f) }, onClick = open)
    when (size) {
        CardSize.S -> CompactBubble(c, d, modifier)
        CardSize.M -> CompactMedium(c, d, modifier)
        CardSize.W -> CompactStrip(c, d, modifier)
        CardSize.L -> Tile(c, tr("Cycle"), modifier, onClick = open) {
            if (info == null) {
                Text(tr("Notez le premier jour de vos règles."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                PillButton(tr("Règles aujourd'hui"), strong = false) { Cycle.toggle(LocalDate.now().toEpochDay()) }
                return@Tile
            }
            Text(trf("Jour %1\$s", info.dayOfCycle), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = c.hueInk())
            info.phase?.let { Text(tr(it.label), style = MaterialTheme.typography.bodyMedium) }
            info.nextStart?.let { n ->
                val inDays = n - LocalDate.now().toEpochDay()
                Text(if (inDays > 0) trf("Prochaines règles dans %1\$s j (estimation)", inDays) else tr("Règles attendues"),
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            } ?: Text(trf("Prédictions après %1\$s cycles complets.", Cycle.MIN_CYCLES), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary)
        }
    }
}

// ---- Signes de la nuit ---------------------------------------------------------------------------------------

private fun signText(g: BodyAlert.Sign): String {
    val l = app.superhealth.ui.Lang.locale
    return when (g.metric) {
        app.superhealth.health.data.Metric.RESTING_HR -> trf("pouls %1\$s bpm", String.format(l, "%+.0f", g.delta))
        app.superhealth.health.data.Metric.RESPIRATORY_RATE -> trf("respiration %1\$s /min", String.format(l, "%+.1f", g.delta))
        app.superhealth.health.data.Metric.TEMPERATURE -> trf("température %1\$s °C", String.format(l, "%+.1f", g.delta))
        else -> trf("VFC %1\$s ms", String.format(l, "%+.0f", g.delta))
    }
}

/** Une phrase, seulement quand la nuit sort de l'ordinaire : alerte, ou écart expliqué par la veille. */
@Composable
fun BodyLine(b: BodyAlert.Status) {
    val signs = b.signs.joinToString(", ") { signText(it) }.replaceFirstChar { it.uppercase() }
    val text = when {
        b.level == BodyAlert.Level.ALERT -> trf("Depuis 2 nuits : %1\$s. Votre corps lutte peut-être.", signs)
        b.causes.isNotEmpty() -> trf("%1\$s, sans doute à cause de %2\$s.", signs, b.causes.joinToString(" " + tr("et") + " ") { tr(it.label) })
        else -> trf("%1\$s cette nuit : à surveiller.", signs)
    }
    val col = if (b.level == BodyAlert.Level.ALERT) Base.Orange else Mf.colors.textSecondary
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(col.container(0.6f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (b.level == BodyAlert.Level.ALERT) col.onContainer() else Mf.colors.text,
            modifier = Modifier.weight(1f))
    }
}

// ---- Ressenti du matin ---------------------------------------------------------------------------------------

/** Cartes masquées tant que le ressenti du matin n'est pas noté (si l'option est activée). */
val CHECKIN_GATED = setOf(DashCard.CHARGE, DashCard.RECOVERY, DashCard.FORM, DashCard.SLEEP_STAGES)

/** Vrai tant que le ressenti du jour manque et que l'option est activée. */
@Composable
fun checkinPending(): Boolean {
    val sh by SuperPrefs.settings.collectAsState()
    val days by Checkin.days.collectAsState()
    return sh.morningCheck && days[LocalDate.now().toEpochDay()] == null
}

/** Les deux questions ; enregistre dès que les deux sont choisies. */
@Composable
fun CheckinPickers() {
    var sleep by remember { mutableStateOf<Int?>(null) }
    var energy by remember { mutableStateOf<Int?>(null) }
    fun save() { val s = sleep; val e = energy; if (s != null && e != null) Checkin.set(Checkin.Feel(s, e)) }
    Text(tr("Avez-vous bien dormi ?"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    MoodPicker(sleep, labels = Checkin.SLEEP) { sleep = it; save() }
    Spacer(Modifier.height(10.dp))
    Text(tr("Votre énergie ?"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    MoodPicker(energy, labels = Checkin.ENERGY) { energy = it; save() }
}

/**
 * À la place d'une carte à score tant que le ressenti manque : les questions sur la première de ces cartes (en grand),
 * une invitation sur les autres.
 */
@Composable
fun CheckinCard(c: DashCard, size: CardSize, first: Boolean, modifier: Modifier, nav: NavHostController) {
    val open: () -> Unit = { nav.go("checkin") }
    if (first && size == CardSize.L) Tile(c, tr("Ressenti du matin"), modifier, onClick = null) { CheckinPickers() }
    else if (first) {
        val d = CompactData(tr("Ressenti"), null, tr("Avez-vous bien dormi ?"), null, onClick = open)
        when (size) {
            CardSize.S -> CompactBubble(c, d, modifier)
            CardSize.M -> CompactMedium(c, d, modifier)
            else -> CompactStrip(c, d, modifier)
        }
    }
    else {
        val d = CompactData(tr("Ressenti"), null, tr(c.label), null, onClick = open)
        when (size) {
            CardSize.S -> CompactBubble(c, d, modifier)
            CardSize.M -> CompactMedium(c, d, modifier)
            CardSize.W -> CompactStrip(c, d, modifier)
            CardSize.L -> Tile(c, tr(c.label), modifier, onClick = open) {
                Text(tr("Notez d'abord votre ressenti du matin."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
            }
        }
    }
}
