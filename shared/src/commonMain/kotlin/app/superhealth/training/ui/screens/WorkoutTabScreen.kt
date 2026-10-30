package app.superhealth.training.ui.screens

import app.superhealth.ui.go
import app.superhealth.platform.*

import app.superhealth.ui.theme.WorkoutBlue
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import app.superhealth.training.ui.tr
import app.superhealth.training.ui.trf
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.theme.container
import app.superhealth.ui.coach.askCoach
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.graphics.graphicsLayer
import app.superhealth.ui.wellbeing.color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Add
import app.superhealth.training.domain.ExerciseDb
import app.superhealth.training.domain.Generator
import app.superhealth.training.domain.Program
import app.superhealth.training.domain.ProgramDay
import app.superhealth.training.ui.AppViewModel
import app.superhealth.training.ui.components.ExerciseThumb
import app.superhealth.training.ui.components.GroupCard
import app.superhealth.training.ui.components.MfCard
import app.superhealth.training.ui.components.PillButton
import app.superhealth.training.ui.components.RowDivider
import app.superhealth.training.ui.components.ScreenTitle
import app.superhealth.training.ui.components.SectionLabel
import app.superhealth.training.ui.components.SettingRow
import app.superhealth.training.ui.durationWords
import app.superhealth.training.ui.longDate
import app.superhealth.training.ui.shortDate
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.ProgramColors
import app.superhealth.training.ui.volume

/**
 * Onglet Activités, pensé dans l'ordre où on s'en sert : la semaine d'un coup d'œil ([week], toutes activités), la séance
 * du jour et trois gestes rapides, les activités récentes ([recent], musculation et cardio mêlées), puis les programmes.
 * L'import de fichiers et l'historique de la montre vivent dans les Réglages (Données).
 */
@Composable
fun WorkoutTabScreen(
    vm: AppViewModel, nav: NavHostController,
    /** Musculation activée (Réglages > Fonctions) ; [cardioFirst] : les activités récentes passent devant (sportif d'endurance). */
    strength: Boolean = true, cardioFirst: Boolean = false,
    week: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {},
    recent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {},
    historyRoute: String = "history",
) {
    val st by vm.state.collectAsState()
    val db by vm.db.collectAsState()
    val active = st.programs.firstOrNull { it.id == st.settings.activeProgramId }
    var create by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        app.superhealth.nutrition.ui.components.TabHeader(app.superhealth.ui.longToday(), tr("Activités")) {
            app.superhealth.nutrition.ui.components.CircleAction(Icons.Rounded.History, tr("Historique")) { nav.navigate(historyRoute) }
            app.superhealth.nutrition.ui.components.SettingsAvatar { nav.go("settings") }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            week()
            if (cardioFirst || !strength) recent()
            if (strength) {
                SectionLabel(tr("Entraînement"))
                if (active != null) ActiveProgramCard(active, db!!, st.settings.sex, nav, resume = st.session != null,
                    onStart = { i -> vm.startProgramDay(active, i, active.cycleIndex); nav.navigate("session") },
                    onAdjusted = { delta -> app.superhealth.ui.wellbeing.startAdjusted(vm, nav, delta) })
                else MfCard(onClick = { create = true }) {
                    // pas de programme actif : une seule invitation, qui ouvre les mêmes choix que « Créer »
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(WorkoutBlue), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("Créer un programme"), style = MaterialTheme.typography.titleMedium)
                            Text(tr("Généré selon vos objectifs, votre emploi du temps et votre équipement."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                // gestes rapides, toujours au même endroit : séance libre, coach, historique
                val coach = app.superhealth.ui.coach.coachAvailable()
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (st.session == null) ActionTile(Icons.Rounded.Bolt, tr("Séance libre"), WorkoutBlue, Modifier.weight(1f)) { vm.startEmpty(); nav.navigate("session") }
                    if (coach) ActionTile(Icons.Rounded.AutoAwesome, tr("Coach"), app.superhealth.ui.coach.CoachHue, Modifier.weight(1f)) {
                        nav.askCoach(if (active == null) tr("Construis-moi un programme de musculation adapté à mon niveau, mon matériel et mes objectifs. Pose-moi d'abord les questions utiles.")
                            else trf("Analyse mon programme « %1\$s » et mes dernières séances : que faudrait-il ajuster ?", active.name))
                    }
                    ActionTile(Icons.Rounded.History, tr("Historique"), app.superhealth.ui.theme.HealthHues.Fitness, Modifier.weight(1f)) { nav.navigate(historyRoute) }
                }
                if (create) CreateTrainingSheet(vm, nav) { create = false }
            }
            if (strength && !cardioFirst) recent()
            if (strength) Programs(st, active, nav) { create = true }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Geste rapide : symbole teinté au-dessus de son nom, sur une tuile douce. */
@Composable
private fun ActionTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, hue: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(hue.container()).clickable(onClick = onClick).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(22.dp), tint = hue.onContainer())
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = hue.onContainer(), maxLines = 1)
    }
}

/** Mes programmes : les autres programmes et séances perso ; les archivés sous une ligne repliée. */
@Composable
private fun Programs(st: app.superhealth.training.data.AppState, active: Program?, nav: NavHostController, onCreate: () -> Unit) {
    val library = st.programs.filter { it.id != active?.id && !it.archived }
    val archived = st.programs.filter { it.archived }
    // sans programme actif, la carte « Créer un programme » fait déjà l'invitation
    if (library.isEmpty() && st.customWorkouts.isEmpty() && archived.isEmpty() && active == null) return
    var showArchived by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    SectionLabel(tr("Mes programmes")) {
        Text(tr("Créer"), Modifier.clip(CircleShape).clickable(onClick = onCreate).padding(horizontal = 6.dp, vertical = 2.dp),
            color = Mf.colors.accent, style = MaterialTheme.typography.labelLarge)
    }
    if (library.isEmpty() && st.customWorkouts.isEmpty() && archived.isEmpty()) return
    GroupCard {
        var first = true
        fun sep(): Boolean = if (first) { first = false; false } else true
        library.forEach { p -> if (sep()) RowDivider(); ProgramRow(p) { nav.navigate("program/${p.id}") } }
        st.customWorkouts.forEach { w ->
            if (sep()) RowDivider()
            SettingRow(w.name, trf("%1\$s exercices", w.exercises.size), subtitle = tr("Séance perso"), icon = Icons.Rounded.FitnessCenter, hue = app.superhealth.ui.theme.Base.Grey) {
                nav.navigate("custom/${w.id}")
            }
        }
        if (archived.isNotEmpty()) {
            if (sep()) RowDivider()
            Row(Modifier.fillMaxWidth().clickable { showArchived = !showArchived }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(trf("Archivés (%1\$s)", archived.size), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(20.dp).graphicsLayer(rotationZ = if (showArchived) 180f else 0f), tint = Mf.colors.textTertiary)
            }
            if (showArchived) archived.forEach { p -> RowDivider(); ProgramRow(p) { nav.navigate("program/${p.id}") } }
        }
    }
}

@Composable
private fun ProgramRow(p: Program, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(ProgramColors[p.color % ProgramColors.size]), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.FitnessCenter, null, Modifier.size(18.dp), tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(trf("%1\$s séances · %2\$s cycles", (p.days.count { !it.isRest }), p.numCycles), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
    }
}

/**
 * Programme actif : nom et avancement, puis ses séances. Aucune n'est imposée : toucher celle qu'on veut la sélectionne,
 * et on choisit de la lancer ou de la modifier.
 */
@Composable
private fun ActiveProgramCard(p: Program, db: ExerciseDb, sex: app.superhealth.training.domain.Sex, nav: NavHostController, resume: Boolean, onStart: (Int) -> Unit, onAdjusted: (Int) -> Unit) {
    val train = p.days.filter { !it.isRest }
    val done = train.count { it.id in p.doneDayIds }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).background(Mf.colors.card).padding(16.dp)) {
        Row(Modifier.clickable { nav.navigate("program/${p.id}") }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(trf("Semaine %1\$s · %2\$s séances sur %3\$s", p.cycleIndex + 1, done, train.size), style = MaterialTheme.typography.bodySmall,
                    color = Mf.colors.textSecondary, maxLines = 1)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        ProgramPicker(p, db, sex, resume, 96.dp, { nav.navigate("session") }, onStart, { i -> nav.navigate("programday/${p.id}/$i") }) { i ->
            // la Forme du jour pèse sur la séance prévue : un cran plus lourd, une série de moins, ou le repos conseillé
            if (i == p.dayIndex) FormHint(onAdjusted)
        }
        if (p.cycleIndex >= p.numCycles) Text(tr("Programme terminé ! Lancez un nouveau bloc ou activez un autre programme."), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
    }
}

/**
 * Les séances d'un programme, un corps « rayons X » par séance (faites en gris et cochées). Toucher en sélectionne une ;
 * apparaissent alors « Lancer » et « Modifier ». Une séance en cours : le bouton y ramène.
 */
@Composable
internal fun ProgramPicker(p: Program, db: ExerciseDb?, sex: app.superhealth.training.domain.Sex, resume: Boolean, bodyH: androidx.compose.ui.unit.Dp, onResume: () -> Unit,
                           onStart: (Int) -> Unit, onEdit: (Int) -> Unit, oneLine: Boolean = false, below: @Composable (Int) -> Unit = {}) {
    val color = ProgramColors[p.color % ProgramColors.size]
    val train = p.days.withIndex().filter { !it.value.isRest }
    var selected by androidx.compose.runtime.saveable.rememberSaveable(p.id) { androidx.compose.runtime.mutableStateOf(-1) }
    // toutes visibles, sans défilement : jusqu'à 4 sur une ligne, puis deux lignes égales (plus basses) ; carte à demi-largeur : les corps seuls
    val half = oneLine
    val perRow = when { oneLine || train.size <= 4 -> train.size.coerceAtLeast(1); train.size <= 8 -> (train.size + 1) / 2; else -> 4 }
    val lines = train.chunked(perRow)
    val height = if (lines.size > 1) bodyH * 0.8f else bodyH
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                line.forEach { (i, d) ->
                    val isDone = d.id in p.doneDayIds
                    val sel = i == selected
                    val muscles = androidx.compose.runtime.remember(d, db) { d.exercises.flatMap { db?.exercise(it.exerciseId)?.muscleGroups.orEmpty() }.toSet() }
                    val shape = RoundedCornerShape(22.dp)
                    Column(Modifier.weight(1f).clip(shape).background(if (sel) color.container(1.4f) else Mf.colors.chip)
                        .then(if (sel) Modifier.border(2.dp, color, shape) else Modifier)
                        .clickable { selected = if (sel) -1 else i }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(height)) {
                            app.superhealth.training.ui.components.XRayBody(muscles, sex, if (isDone && !sel) null else color, Modifier.fillMaxSize())
                            if (isDone) Box(Modifier.align(Alignment.TopEnd).padding(end = 6.dp).size(18.dp).clip(CircleShape).background(app.superhealth.ui.theme.Base.Green),
                                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = Color.White) }
                        }
                        if (!half) Text(dayShort(d.name), style = MaterialTheme.typography.labelMedium, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (isDone && !sel) Mf.colors.textSecondary else Mf.colors.text, modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp))
                    }
                }
                repeat(perRow - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    val day = p.days.getOrNull(selected)?.takeIf { !it.isRest }
    if (resume) {
        Spacer(Modifier.height(12.dp))
        PillButton(tr("Reprendre la séance"), Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow, onClick = onResume)
    } else if (day != null) {
        if (half) Text(day.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(if (half) 6.dp else 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(tr("Lancer") + " · " + trf("%1\$s min", Generator.estimateMinutes(day, p.cycleIndex)), Modifier.weight(1.4f), icon = Icons.Rounded.PlayArrow) { onStart(selected) }
            PillButton(tr("Modifier"), Modifier.weight(1f), strong = false) { onEdit(selected) }
        }
        below(selected)
    }
}

/** Historique complet (WorkoutHistoryView). */
@Composable
fun HistoryScreen(vm: AppViewModel, nav: NavHostController) {
    val st by vm.state.collectAsState()
    DetailScaffold(tr("Historique"), nav) {
        if (st.history.isEmpty()) MfCard { Text(tr("Aucun entraînement terminé pour l'instant."), color = Mf.colors.textSecondary) }
        st.history.sortedByDescending { it.startTime }.groupBy { app.superhealth.training.ui.localDate(it.startTime).withDayOfMonth(1) }.forEach { (month, list) ->
            SectionLabel(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", app.superhealth.training.ui.Lang.locale)).replaceFirstChar { it.uppercase() })
            GroupCard {
                list.forEachIndexed { i, w ->
                    if (i > 0) RowDivider()
                    SettingRow(w.name, durationWords(w.durationSec), subtitle = trf("%1\$s · %2\$s exercices", (longDate(w.startTime)), w.exercises.size)) { nav.navigate("workoutdetail/${w.id}") }
                }
            }
        }
    }
}

/** Libellé court d'un jour : lettre de variante (« Corps entier A » → « A ») ou premier mot. */
internal fun dayShort(name: String): String {
    val parts = name.split(' ')
    return if (parts.size > 1 && parts.last().length == 1) "${parts.first()} ${parts.last()}" else parts.first()
}


/** Conseil de la Forme du jour sous « Commencer » : séance plus dure ou allégée en un geste, repos signalé. */
@Composable
private fun FormHint(onAdjusted: (Int) -> Unit) {
    val a = app.superhealth.ui.wellbeing.rememberForm() as? app.superhealth.wellbeing.Form.Advice ?: return
    val top = (app.superhealth.wellbeing.Checkin.days.collectAsState().value[app.superhealth.platform.LocalDate.now().toEpochDay()]?.energy ?: 0) >= 5
    val (label, delta) = when (a.verdict) {
        app.superhealth.wellbeing.Form.Verdict.PUSH -> tr("Forme au vert : un cran plus lourd") to 1
        app.superhealth.wellbeing.Form.Verdict.NORMAL -> if (top) tr("Au top : un cran plus lourd") to 1 else return
        app.superhealth.wellbeing.Form.Verdict.EASY -> tr("Forme basse : séance allégée") to -1
        app.superhealth.wellbeing.Form.Verdict.REST -> tr("Forme du jour : repos conseillé") to 0
    }
    Spacer(Modifier.height(8.dp))
    if (delta == 0) Text(label, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
        color = a.verdict.color().onContainer(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    else PillButton(label, Modifier.fillMaxWidth(), strong = false) { onAdjusted(delta) }
}
