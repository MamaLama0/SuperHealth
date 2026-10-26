package app.superhealth.ui.dashboard

import app.superhealth.ui.theme.dashCardBackground
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.DashCard
import app.superhealth.nutrition.data.Analytics
import app.superhealth.nutrition.domain.Balance
import app.superhealth.nutrition.domain.Habits
import app.superhealth.nutrition.domain.Settings as NutritionSettings
import app.superhealth.ui.charts.BarChart
import app.superhealth.ui.charts.LineChart
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.ui.charts.Series
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.training.domain.Analytics as TrainingAnalytics
import app.superhealth.training.domain.Generator
import app.superhealth.training.ui.components.ExerciseThumb
import app.superhealth.training.ui.estimate
import app.superhealth.training.ui.volume
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.dayLetter
import app.superhealth.ui.formatNumber
import app.superhealth.ui.go
import app.superhealth.ui.localDate
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.WorkoutBlue
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.theme.container
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Cartes reprises des tableaux de bord de MacroFactor (habitudes, bilan) et de MacroFactor Workouts (prochaine séance,
 * tendances, séries par muscle, progression des exercices).
 */

/** Valeur et objectif d'un anneau, en texte. */
private fun ringText(r: Rings.Ring, s: NutritionSettings): Pair<String?, String> = when (r.kind) {
    Rings.Kind.KCAL -> r.value?.let { formatNumber(s.energyValue(it)) } to "${formatNumber(s.energyValue(r.goal))} ${tr(s.energyUnit.label)}"
    Rings.Kind.PROTEIN -> r.value?.let { formatNumber(it) } to "${formatNumber(r.goal)} g"
    Rings.Kind.WATER -> r.value?.let { formatNumber(it) } to "${formatNumber(r.goal)} ml"
    Rings.Kind.SLEEP -> r.value?.let { app.superhealth.ui.formatValue(app.superhealth.health.data.Metric.SLEEP, it) } to
        app.superhealth.ui.formatValue(app.superhealth.health.data.Metric.SLEEP, r.goal)
    Rings.Kind.CARDIO -> r.value?.let { formatNumber(it) } to "${formatNumber(r.goal)} min"
    else -> r.value?.let { formatNumber(it) } to formatNumber(r.goal)
}

private fun ringRoute(k: Rings.Kind): String = when (k) {
    Rings.Kind.STEPS -> "h/metric/STEPS"
    Rings.Kind.ENERGY -> "h/metric/BODY_ENERGY"
    Rings.Kind.WATER -> "h/metric/HYDRATION"
    Rings.Kind.SLEEP -> "h/metric/SLEEP"
    Rings.Kind.CARDIO -> "h/metric/CARDIO_WEEK"
    Rings.Kind.KCAL, Rings.Kind.PROTEIN -> "log"
}

/**
 * Anneaux du jour : en bulle (S) les anneaux seuls, en demi-tuile (M) avec les valeurs dessous, en pleine largeur (L) avec
 * la légende ; chaque mesure ouvre son écran. « Modifier » choisit les anneaux.
 */
@Composable
internal fun RingsCard(c: DashCard, size: app.superhealth.CardSize, rings: List<Rings.Ring>, s: NutritionSettings, modifier: Modifier,
                       nav: NavHostController, tab: (String) -> Unit) {
    fun open(k: Rings.Kind) { ringRoute(k).let { if (it == "log") tab("log") else nav.go(it) } }
    when (size) {
        app.superhealth.CardSize.S -> Box(modifier.square().clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).dashCardBackground()
            .cardClick { nav.go("rings/edit") }.padding(10.dp), contentAlignment = Alignment.Center) {
            ActivityRings(rings, 92.dp, track = Mf.colors.background, icons = false)
        }
        // Demi : l'anneau d'abord, chaque mesure reconnue au symbole posé sur son rayon ; en face, la valeur seule, dans sa couleur.
        app.superhealth.CardSize.M -> Box(modifier.fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).dashCardBackground()
            .cardClick { nav.go("rings/edit") }.padding(10.dp), contentAlignment = Alignment.Center) {
            // largeur calculée sans BoxWithConstraints : la rangée mesure ses cartes en hauteur intrinsèque
            val screen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
            val inner = (screen - 32.dp - GAP * 2) * (2f / 3f) + GAP - 20.dp
            val side = (inner * 0.64f).coerceIn(100.dp, 164.dp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ActivityRings(rings, side, track = Mf.colors.background)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (rings.size > 3) 2.dp else 8.dp)) {
                    rings.forEach { r ->
                        // valeur dans sa couleur et pourcentage de l'objectif dessous : on sait où l'on en est sans libellé
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open(r.kind) }) {
                            Text(ringText(r, s).first ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                color = r.kind.color.onContainer(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (rings.size <= 3) Text(if (r.value == null) "–" else "${(r.progress * 100).toInt()} %",
                                style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary, maxLines = 1)
                        }
                    }
                }
            }
        }
        // Bandeau : petits anneaux, puis chaque valeur (symbole et couleur de son anneau) sur une ligne.
        app.superhealth.CardSize.W -> Row(modifier.fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).dashCardBackground()
            .cardClick { nav.go("rings/edit") }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ActivityRings(rings, 52.dp, track = Mf.colors.background, icons = false)
            Spacer(Modifier.width(12.dp))
            rings.forEach { r ->
                Row(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { open(r.kind) }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(r.kind.icon, null, Modifier.size(14.dp), tint = r.kind.color)
                    Spacer(Modifier.width(3.dp))
                    Text(ringText(r, s).first ?: "—", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = r.kind.color.onContainer(),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        app.superhealth.CardSize.L -> Tile(c, tr("Anneaux du jour"), modifier, tinted = false, trailing = tr("Modifier"), onClick = { nav.go("rings/edit") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActivityRings(rings, 140.dp, track = Mf.colors.card)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (rings.size > 3) 4.dp else 10.dp)) {
                    rings.forEach { r ->
                        val (value, goal) = ringText(r, s)
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open(r.kind) }.padding(vertical = 2.dp)) {
                            Text(tr(r.kind.label), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, maxLines = 1)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(value ?: "—", style = if (rings.size > 3) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold, color = r.kind.color.onContainer(), maxLines = 1)
                                Text(" / $goal", style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 3.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Habitudes : journal et pesées (séries de jours), entraînement (séries de semaines), avec les 7 derniers jours. */
@Composable
internal fun HabitsCard(c: DashCard, vm: SuperViewModel, an: Analytics, modifier: Modifier, nav: NavHostController) {
    val st by vm.training.state.collectAsState()
    val logged = remember(an) { an.days.filter { it.intakeKcal != null || it.fasted }.map { it.epochDay }.toSet() }
    val weighed = remember(an) { an.days.filter { it.weightKg != null }.map { it.epochDay }.toSet() }
    val trained = remember(st.history) { st.history.map { localDate(it.startTime).toEpochDay() }.toSet() }
    Tile(c, tr("Habitudes"), modifier, tinted = false, onClick = { nav.go("habits") }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HabitColumn(Icons.Rounded.LocalFireDepartment, tr("Journal"), Habits.streak(logged, an.today).current, tr("j."), logged, an.today, c.ink(), Modifier.weight(1f))
            HabitColumn(Icons.Rounded.MonitorWeight, tr("Pesées"), Habits.streak(weighed, an.today).current, tr("j."), weighed, an.today, c.ink(), Modifier.weight(1f))
            HabitColumn(Icons.Rounded.FitnessCenter, tr("Entraînement"), Habits.weekStreak(trained, an.today), tr("sem."), trained, an.today, WorkoutBlue, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HabitColumn(icon: ImageVector, label: String, streak: Int, unit: String, done: Set<Long>, today: Long, hue: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Mf.colors.background).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = hue)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$streak", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(" $unit", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
        // mini-calendrier des 7 derniers jours (MiniHabitCalendar)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (today - 6..today).forEach { d ->
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (d in done) hue else Mf.colors.chip))
            }
        }
    }
}

/** Bilan énergétique des 7 derniers jours : apports moins dépense, barres de part et d'autre de l'équilibre. */
@Composable
internal fun BalanceCard(c: DashCard, an: Analytics, s: NutritionSettings, modifier: Modifier, nav: NavHostController) {
    val days = remember(an) { Balance.days(an.days, an::tdeeAt, an.today) }
    val avg = Balance.average(days)
    val surplus = app.superhealth.ui.theme.Base.Orange; val deficit = c.ink()
    Tile(c, tr("Bilan énergétique"), modifier, onClick = { nav.go("expenditure") }) {
        Value(avg?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + formatNumber(abs(s.energyValue(it))) } ?: "—",
            trf("%1\$s / jour", tr(s.energyUnit.label)), color = c.ink().onContainer())
        Sub(when {
            avg == null -> tr("Journalisez vos repas pour voir votre bilan")
            avg < 0 -> tr("Déficit moyen sur 7 jours")
            else -> tr("Surplus moyen sur 7 jours")
        })
        Spacer(Modifier.height(10.dp))
        app.superhealth.ui.charts.SignedBarChart(days.map { it.second }, surplus, deficit, height = 70.dp, showAxis = false,
            labels = days.map { (d, _) -> dayLetter(LocalDate.ofEpochDay(d)) })
    }
}

/** Prochaine séance du programme actif (ou séance en cours), à lancer d'une touche. */
@Composable
internal fun NextWorkoutCard(c: DashCard, size: app.superhealth.CardSize, vm: SuperViewModel, modifier: Modifier, nav: NavHostController, tab: (String) -> Unit) {
    val st by vm.training.state.collectAsState()
    val db = vm.training.db.collectAsState().value
    val p = st.programs.firstOrNull { it.id == st.settings.activeProgramId }
    val day = p?.days?.getOrNull(p.dayIndex)
    val session = st.session
    if (size == app.superhealth.CardSize.W) {
        // bandeau : nom de la séance et départ d'une touche
        val name = session?.name ?: day?.takeIf { !it.isRest }?.name
        Row(modifier.fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius))
            .dashCardBackground()
            .cardClick { if (session != null) nav.go("session") else tab("activities") }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(c.icon(), null, Modifier.size(22.dp), tint = c.ink())
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name ?: day?.name ?: tr("Aucun programme actif"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Sub(if (session != null) tr("Séance en cours") else if (day?.isRest == true) tr("Jour de repos") else p?.name)
            }
            if (name != null) PillButton(if (session != null) tr("Reprendre") else tr("Commencer"), icon = Icons.Rounded.PlayArrow) {
                if (session != null) nav.go("session") else if (vm.training.startNext()) nav.go("session") else tab("activities")
            }
        }
        return
    }
    Tile(c, if (session != null) tr("Séance en cours") else tr("Prochaine séance"), modifier, tinted = true,
        trailing = p?.takeIf { session == null }?.let { trf("Cycle %1\$s/%2\$s", it.cycleIndex + 1, it.numCycles) },
        onClick = { if (session != null) nav.go("session") else tab("activities") }) {
        when {
            session != null -> {
                Text(session.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val done = session.exercises.sumOf { e -> e.sets.count { it.done } }
                Sub(trf("%1\$s/%2\$s séries faites", done, session.exercises.sumOf { it.sets.size }))
                Spacer(Modifier.height(10.dp))
                PillButton(tr("Reprendre"), Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow) { nav.go("session") }
            }
            p == null || day == null -> {
                Text(tr("Aucun programme actif"), style = MaterialTheme.typography.titleMedium)
                Sub(tr("Créez un programme ou lancez une séance libre."))
                Spacer(Modifier.height(10.dp))
                PillButton(tr("Entraînement"), Modifier.fillMaxWidth(), strong = false) { tab("activities") }
            }
            day.isRest -> {
                Text(day.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                Sub(tr("Jour de repos"))
            }
            else -> {
                Text(day.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Sub(trf("%1\$s · %2\$s exercices · ≈ %3\$s min", p.name, day.exercises.size, Generator.estimateMinutes(day, p.cycleIndex)))
                if (db != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        day.exercises.take(6).forEach { ExerciseThumb(db.exercise(it.exerciseId), 40.dp, 10.dp) }
                        if (day.exercises.size > 6) Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Mf.colors.card), contentAlignment = Alignment.Center) {
                            Text("+${day.exercises.size - 6}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                PillButton(tr("Commencer"), Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow) {
                    if (vm.training.startNext()) nav.go("session") else tab("activities")
                }
            }
        }
    }
}

/** Tendances d'entraînement (Total Sets / Volume / Duration) sur 8 semaines, et les exercices les plus travaillés. */
@Composable
internal fun TrendsCard(c: DashCard, vm: SuperViewModel, modifier: Modifier, nav: NavHostController) {
    val st by vm.training.state.collectAsState()
    val db = vm.training.db.collectAsState().value
    var which by rememberSaveable { mutableIntStateOf(0) }
    val weeks = remember(st.history) { TrainingAnalytics.weeklyTotals(st.history, 8, LocalDate.now()) }
    val top = remember(st.history) { TrainingAnalytics.topBySets(st.history, System.currentTimeMillis() - 28L * 86_400_000, 3) }
    val values = weeks.map {
        when (which) { 0 -> it.sets.toDouble(); 1 -> it.volume; else -> it.durationSec / 60.0 }
    }
    fun fmt(v: Double) = when (which) { 0 -> formatNumber(v); 1 -> st.settings.volume(v); else -> trf("%1\$s min", v.roundToInt()) }
    Tile(c, tr("Tendances d'entraînement"), modifier, tinted = false, onClick = { nav.go("t/insights") }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(tr("Séries"), tr("Volume"), tr("Durée")).forEachIndexed { i, l ->
                Text(l, style = MaterialTheme.typography.labelLarge, maxLines = 1, color = if (i == which) Mf.colors.onStrong else Mf.colors.text,
                    modifier = Modifier.clip(CircleShape).background(if (i == which) Mf.colors.strong else Mf.colors.chip).clickable { which = i }
                        .padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        val prev = values.dropLast(1).filter { it > 0 }
        Value(fmt(values.last()), color = c.ink().onContainer())
        Sub(prev.takeIf { it.isNotEmpty() }?.average()?.let { trf("Cette semaine · moyenne %1\$s", fmt(it)) } ?: tr("Cette semaine"))
        Spacer(Modifier.height(10.dp))
        BarChart(values.map { it.takeIf { v -> v > 0 } }, values.map { null }, c.ink(), height = 70.dp, track = softTrack(),
            labels = weeks.map { "${it.start.dayOfMonth}/${it.start.monthValue}" })
        if (db != null && top.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(tr("Les plus travaillés (4 sem.)"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
            top.forEach { (id, sets) ->
                val e = db.exercise(id)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.go("exercise/$id") }.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    ExerciseThumb(e, 32.dp, 8.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(e?.name ?: "?", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(trf("%1\$s séries", sets), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
                }
            }
        }
    }
}

/** Séries hebdomadaires moyennes par muscle (4 semaines), repères 8–16 séries. */
@Composable
internal fun MuscleSetsCard(c: DashCard, vm: SuperViewModel, modifier: Modifier, nav: NavHostController) {
    val st by vm.training.state.collectAsState()
    val db = vm.training.db.collectAsState().value ?: return
    val sets = remember(st.history, db) { TrainingAnalytics.weeklySetsByMuscle(st.history, db, 4, System.currentTimeMillis()) }
    val rows = sets.entries.sortedByDescending { it.value }.take(8)
    Tile(c, tr("Séries par muscle"), modifier, tinted = false, trailing = tr("moy. / sem."), onClick = { nav.go("levels") }) {
        if (rows.isEmpty()) Text(tr("Vos séries par muscle apparaîtront après vos premières séances."), style = MaterialTheme.typography.bodyMedium,
            color = Mf.colors.textSecondary)
        val scale = 20.0
        rows.forEach { (g, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr(g.label), Modifier.width(118.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BoxWithConstraints(Modifier.weight(1f).height(10.dp)) {
                    val w = maxWidth
                    // bande des repères 8–16 séries
                    Box(Modifier.fillMaxHeight().width(w).clip(CircleShape).background(Mf.colors.chip))
                    Box(Modifier.padding(start = w * (TrainingAnalytics.SETS_LOW / scale).toFloat()).fillMaxHeight()
                        .width(w * ((TrainingAnalytics.SETS_HIGH - TrainingAnalytics.SETS_LOW) / scale).toFloat()).background(c.ink().copy(alpha = 0.18f)))
                    Box(Modifier.fillMaxHeight().width(w * (v / scale).toFloat().coerceIn(0.03f, 1f)).clip(CircleShape)
                        .background(if (v >= TrainingAnalytics.SETS_LOW) c.ink() else c.ink().copy(alpha = 0.55f)))
                }
                Text(formatNumber(v, 1), Modifier.width(40.dp), style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1)
            }
        }
        if (rows.isNotEmpty()) Sub(tr("Zone teintée : 8 à 16 séries par semaine"))
    }
}

/** Progression des exercices les plus fréquents : 1RM estimé sur 12 semaines et évolution. */
@Composable
internal fun LiftsCard(c: DashCard, vm: SuperViewModel, modifier: Modifier, nav: NavHostController) {
    val st by vm.training.state.collectAsState()
    val db = vm.training.db.collectAsState().value ?: return
    val since = System.currentTimeMillis() - 84L * 86_400_000
    val lifts = remember(st.history, db) {
        TrainingAnalytics.topBySets(st.history, since, 4).map { (id, _) ->
            id to TrainingAnalytics.history(id, st.history, db).filter { it.time >= since && it.e1rm > 0 }
        }.filter { it.second.isNotEmpty() }
    }
    Tile(c, tr("Progression des exercices"), modifier, tinted = false, trailing = tr("1RM estimé"), onClick = { nav.go("t/insights") }) {
        if (lifts.isEmpty()) Text(tr("Vos courbes de force apparaîtront après quelques séances."), style = MaterialTheme.typography.bodyMedium,
            color = Mf.colors.textSecondary)
        lifts.forEach { (id, pts) ->
            val e = db.exercise(id)
            val change = if (pts.size >= 2) (pts.last().e1rm / pts.first().e1rm - 1) * 100 else null
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.go("exercise/$id") }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ExerciseThumb(e, 36.dp, 9.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e?.name ?: "?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(st.settings.estimate(pts.last().e1rm), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
                Box(Modifier.width(72.dp)) {
                    if (pts.size >= 2) LineChart(listOf(Series(pts.map { it.time / 60_000 to it.e1rm }, c.ink())), height = 28.dp, showAxis = false)
                }
                Spacer(Modifier.width(8.dp))
                Text(change?.let { (if (it >= 0) "+" else "") + formatNumber(it, 1) + " %" } ?: "—", Modifier.width(56.dp),
                    style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    color = when { change == null -> Mf.colors.textSecondary; change >= 0 -> app.superhealth.ui.theme.Base.Green; else -> app.superhealth.ui.theme.Base.Red })
            }
        }
    }
}
