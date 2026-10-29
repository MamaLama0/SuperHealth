package app.superhealth.training.ui.screens

import app.superhealth.platform.*

import app.superhealth.ui.theme.WorkoutBlue
import androidx.compose.foundation.border
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
 * Onglet Activités de SuperHealth : programme de musculation actif, changement de programme, puis [others]
 * (course, marche, randonnée… de la montre et de Health Connect) et l'historique de musculation.
 */
@Composable
fun WorkoutTabScreen(
    vm: AppViewModel, nav: NavHostController,
    top: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {},
    /** Musculation activée (Réglages > Fonctions) ; [cardioFirst] : les activités de la montre passent devant (sportif d'endurance). */
    strength: Boolean = true, cardioFirst: Boolean = false,
    others: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {},
) {
    val st by vm.state.collectAsState()
    val db by vm.db.collectAsState()
    val active = st.programs.firstOrNull { it.id == st.settings.activeProgramId }
    var create by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        ScreenTitle(tr("Activités"), actions = {
            if (strength) app.superhealth.nutrition.ui.components.CircleAction(Icons.Rounded.History, tr("Historique")) { nav.navigate("history") }
            app.superhealth.nutrition.ui.components.SettingsAvatar { nav.navigate("settings") }
        })
        Column(Modifier.padding(horizontal = 16.dp)) {
            top()
            if (cardioFirst || !strength) others()
            if (strength) {
            SectionLabel(tr("Musculation"))
            WeekStrip(st.history, st.settings) { nav.navigate("t/insights") }
            var askSkip by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            if (askSkip) androidx.compose.material3.AlertDialog(onDismissRequest = { askSkip = false }, containerColor = Mf.colors.card,
                title = { Text(tr("Passer cette séance ?")) }, text = { Text(tr("Elle est retirée du cycle en cours ; la suivante devient la prochaine.")) },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { askSkip = false; vm.skipNext() }) { Text(tr("Passer"), color = Mf.colors.accent) } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { askSkip = false }) { Text(tr("Annuler"), color = Mf.colors.text) } })
            if (active != null) ActiveProgramCard(active, db!!, nav, resume = st.session != null, onSkip = { askSkip = true },
                onStart = { day -> if (st.session == null) vm.startProgramDay(active, day, active.cycleIndex); nav.navigate("session") },
                onAdjusted = { delta -> app.superhealth.ui.wellbeing.startAdjusted(vm, nav, delta) })
            else MfCard(onClick = { nav.navigate("wizard") }) {
                // Pas de programme actif : invitation à en générer un
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(WorkoutBlue), contentAlignment = Alignment.Center) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (st.session == null) PillButton(tr("Séance vide"), Modifier.weight(1f), strong = false, icon = Icons.Rounded.Bolt) {
                    if (st.session == null) vm.startEmpty(); nav.navigate("session")
                }
                // Programme généré ou manuel, ou séance perso (sans l'algorithme de génération).
                PillButton(tr("Créer"), Modifier.weight(1f), strong = false, icon = Icons.Rounded.Add) { create = true }
            }
            if (create) CreateTrainingSheet(vm, nav) { create = false }

            val library = st.programs.filter { it.id != active?.id && !it.archived }
            if (library.isNotEmpty() || st.customWorkouts.isNotEmpty()) {
                SectionLabel(if (active != null) tr("Changer de programme") else tr("Bibliothèque"))
                GroupCard {
                    library.forEachIndexed { i, p ->
                        if (i > 0) RowDivider()
                        ProgramRow(p) { nav.navigate("program/${p.id}") }
                    }
                    st.customWorkouts.forEachIndexed { i, w ->
                        if (i > 0 || library.isNotEmpty()) RowDivider()
                        SettingRow(w.name, trf("%1\$s exercices", w.exercises.size), subtitle = tr("Séance perso"), icon = Icons.Rounded.FitnessCenter, hue = app.superhealth.ui.theme.Base.Grey) {
                            nav.navigate("custom/${w.id}")
                        }
                    }
                }
            }
            }
            if (strength && !cardioFirst) others()
            if (!strength) { Spacer(Modifier.height(24.dp)); return@Column }

            val archived = st.programs.filter { it.archived }
            if (archived.isNotEmpty()) {
                SectionLabel(tr("Archive"))
                GroupCard { archived.forEachIndexed { i, p -> if (i > 0) RowDivider(); ProgramRow(p) { nav.navigate("program/${p.id}") } } }
            }

            if (st.history.isNotEmpty()) {
                SectionLabel(tr("Historique musculation")) { Text(tr("Tout voir"), Modifier.clickable { nav.navigate("history") }, color = Mf.colors.accent, style = MaterialTheme.typography.labelLarge) }
                GroupCard {
                    st.history.sortedByDescending { it.startTime }.take(4).forEachIndexed { i, w ->
                        if (i > 0) RowDivider()
                        SettingRow(w.name, shortDate(w.startTime), subtitle = "${durationWords(w.durationSec)} · ${st.settings.volume(app.superhealth.training.domain.Analytics.run { w.exercises.sumOf { volume(it.sets) } })}") {
                            nav.navigate("workoutdetail/${w.id}")
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
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

/** Programme actif : cycle en cours, jours du cycle, prochaine séance (WorkoutProgramExpandableCard). */
@Composable
private fun ActiveProgramCard(p: Program, db: ExerciseDb, nav: NavHostController, resume: Boolean, onSkip: () -> Unit, onStart: (Int) -> Unit,
                              onAdjusted: (Int) -> Unit) {
    val color = ProgramColors[p.color % ProgramColors.size]
    MfCard(padding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().height(6.dp).background(color))
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { nav.navigate("program/${p.id}") }) {
                    Text(tr("Programme actif"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
                    Text(p.name, style = MaterialTheme.typography.headlineSmall)
                }
                Text(trf("Cycle %1\$s/%2\$s", (p.cycleIndex + 1), p.numCycles), Modifier.clip(CircleShape).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = color)
            }
            Spacer(Modifier.height(12.dp))
            // jours du cycle : cases larges (toute la largeur jusqu'à 7 jours, défilement au-delà)
            val fit = p.days.size <= 7
            Row(if (fit) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                p.days.forEachIndexed { i, d ->
                    // jour fait dans ce cycle ; un jour de repos l'est quand tous les jours d'entraînement avant lui le sont
                    val done = if (d.isRest) i < p.dayIndex && p.days.take(i).all { it.isRest || it.id in p.doneDayIds } else d.id in p.doneDayIds
                    val current = i == p.dayIndex
                    Column((if (fit) Modifier.weight(1f) else Modifier.width(64.dp)).height(64.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (current) color.copy(alpha = 0.18f) else Mf.colors.chip)
                        .border(if (current) 1.5.dp else 0.dp, if (current) color else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable { nav.navigate("programday/${p.id}/${i}") }.padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(trf("J%1\$s", (i + 1)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            color = if (current) color else Mf.colors.text)
                        Spacer(Modifier.height(2.dp))
                        if (done) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = color)
                        else Text(if (d.isRest) tr("Repos") else dayShort(d.name), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, color = Mf.colors.textSecondary)
                    }
                }
            }
            val next = p.days.getOrNull(p.dayIndex)
            if (next != null && !next.isRest) {
                Spacer(Modifier.height(14.dp))
                NextDay(next, p, db)
                Spacer(Modifier.height(12.dp))
                // une séance est déjà en cours : le bouton y ramène au lieu d'en promettre une nouvelle
                PillButton(if (resume) tr("Reprendre la séance") else trf("Commencer %1\$s", next.name), Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow) { onStart(p.dayIndex) }
                // la Forme du jour pèse sur la séance : un cran plus lourd, une série de moins, ou le repos conseillé
                if (!resume) FormHint(onAdjusted)
                if (!resume) Text(tr("Passer cette séance"), Modifier.fillMaxWidth().clip(CircleShape).clickable(onClick = onSkip).padding(10.dp),
                    style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            } else if (p.cycleIndex >= p.numCycles) {
                Text(tr("Programme terminé ! Lancez un nouveau bloc ou activez un autre programme."), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun NextDay(day: ProgramDay, p: Program, db: ExerciseDb) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(day.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(trf("≈ %1\$s min", (Generator.estimateMinutes(day, p.cycleIndex))), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
    }
    Spacer(Modifier.height(8.dp))
    day.exercises.take(6).forEach { pe ->
        val e = db.exercise(pe.exerciseId)
        val t = Generator.targetsFor(pe, p.cycleIndex)
        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseThumb(e, 36.dp, 8.dp)
            Spacer(Modifier.width(10.dp))
            Text(e?.name ?: "?", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(trf("%1\$s × %2\$s", t.size, (t.firstOrNull()?.let { "${it.minReps}-${it.maxReps}" } ?: "")), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
    }
    if (day.exercises.size > 6) Text(trf("+ %1\$s exercices", (day.exercises.size - 6)), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
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
private fun dayShort(name: String): String {
    val parts = name.split(' ')
    return if (parts.size > 1 && parts.last().length == 1) "${parts.first().take(5)} ${parts.last()}" else parts.first().take(8)
}


/** Semaine de musculation en un coup d'œil : jours entraînés (lun → dim), séances, séries et volume. */
@Composable
private fun WeekStrip(history: List<app.superhealth.training.domain.Workout>, settings: app.superhealth.training.domain.Settings, onClick: () -> Unit) {
    val today = LocalDate.now()
    val start = app.superhealth.training.domain.Analytics.weekStart(today)
    val week = history.filter { !app.superhealth.ui.localDate(it.startTime).isBefore(start) }
    val days = week.map { app.superhealth.ui.localDate(it.startTime) }.toSet()
    val sets = week.sumOf { w -> w.exercises.sumOf { app.superhealth.training.domain.Analytics.workingSets(it).size } }
    val volume = week.sumOf { w -> w.exercises.sumOf { app.superhealth.training.domain.Analytics.volume(it.sets) } }
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).background(Mf.colors.card)
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Cette semaine"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
            Text(trf(if (week.size > 1) "%1\$s séances" else "%1\$s séance", week.size) + " · " + trf(if (sets > 1) "%1\$s séries" else "%1\$s série", sets),
                style = MaterialTheme.typography.titleSmall, maxLines = 1)
            if (volume > 0) Text(settings.volume(volume), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0L..6L).forEach { i ->
                val d = start.plusDays(i)
                val done = d in days
                Box(Modifier.size(22.dp).clip(CircleShape).background(when {
                    done -> app.superhealth.ui.theme.WorkoutBlue
                    d == today -> app.superhealth.ui.theme.WorkoutBlue.copy(alpha = 0.18f)
                    else -> Mf.colors.chip
                }), contentAlignment = Alignment.Center) {
                    Text(app.superhealth.ui.dayLetter(d), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (done) Color.White else Mf.colors.textSecondary)
                }
            }
        }
    }
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
