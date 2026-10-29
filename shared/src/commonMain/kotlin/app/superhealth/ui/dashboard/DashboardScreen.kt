package app.superhealth.ui.dashboard

import app.superhealth.platform.*

import app.superhealth.feature
import app.superhealth.has

import app.superhealth.available
import app.superhealth.nutrition.domain.stepGoalFor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.StackedLineChart
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import app.superhealth.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import app.superhealth.DashCard
import app.superhealth.sizeOf
import app.superhealth.sizes
import app.superhealth.vitalMetrics
import app.superhealth.bridge.Bridge
import app.superhealth.bridge.LiveHeart
import app.superhealth.health.data.CardioWeek
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.Metric
import app.superhealth.health.data.Point
import app.superhealth.nutrition.data.CheckInState
import app.superhealth.nutrition.domain.Expenditure
import app.superhealth.nutrition.domain.Macros
import app.superhealth.ui.charts.BarChart
import app.superhealth.ui.charts.LineChart
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.ProgressBar
import app.superhealth.nutrition.ui.components.Ring
import app.superhealth.ui.charts.Series
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.macros
import app.superhealth.nutrition.ui.screens.StrategySummaryCard
import app.superhealth.nutrition.ui.screens.signed
import app.superhealth.nutrition.ui.weightValue
import app.superhealth.training.domain.Analytics as TrainingAnalytics
import app.superhealth.training.ui.components.BodyMap
import app.superhealth.training.ui.components.StatCell
import app.superhealth.training.ui.screens.RecordCard
import app.superhealth.training.ui.screens.WeekHabit
import app.superhealth.training.ui.screens.intensity
import app.superhealth.training.ui.volume
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.dayLetter
import app.superhealth.ui.formatNumber
import app.superhealth.ui.formatValue
import app.superhealth.ui.formatWithUnit
import app.superhealth.ui.go
import app.superhealth.ui.openTab
import app.superhealth.ui.health.ActivityRow
import app.superhealth.ui.health.HeartChart
import app.superhealth.ui.health.openActivity
import app.superhealth.ui.localDate
import app.superhealth.ui.longToday
import app.superhealth.ui.theme.CaloriesColor
import app.superhealth.ui.theme.CardRadius
import app.superhealth.ui.theme.CarbsColor
import app.superhealth.ui.theme.FatColor
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.dashCardBackground
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.ProteinColor
import app.superhealth.ui.theme.WorkoutBlue
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.timeOf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.platform.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlin.math.roundToInt

/** Couleur et icône de chaque carte (pastille d'en-tête, fond des cartes teintées). */
/** Couleur des tracés et valeurs d'une carte : sa famille, ou le blanc d'une carte « Plein » (voir CardTone). */
@Composable
internal fun DashCard.ink(): Color = app.superhealth.ui.theme.LocalCardInk.current ?: hue()

internal fun DashCard.hue(): Color = when (this) {
    DashCard.CALORIES -> CaloriesColor
    DashCard.WEIGHT -> app.superhealth.ui.theme.Base.Pink
    DashCard.INTAKE -> HealthHues.Mind
    DashCard.EXPENDITURE -> HealthHues.Respiration
    DashCard.MACROS -> ProteinColor
    DashCard.STEPS, DashCard.CARDIO -> HealthHues.Activity
    DashCard.HEART, DashCard.VITALS -> HealthHues.Heart
    DashCard.SLEEP -> HealthHues.Sleep
    DashCard.ENERGY -> HealthHues.Mind
    DashCard.HYDRATION -> HealthHues.Respiration
    DashCard.ACTIVITIES -> HealthHues.Fitness
    DashCard.STRATEGY -> HealthHues.Body
    DashCard.TRAINING_WEEK, DashCard.MUSCLES, DashCard.RECORDS -> WorkoutBlue
    DashCard.RUNNING -> HealthHues.Fitness
    DashCard.RECOVERY -> HealthHues.Sleep
    DashCard.RINGS -> Rings.Kind.STEPS.color
    DashCard.FOCUS -> HealthHues.Body
    DashCard.HABITS -> HealthHues.Body
    DashCard.BODY -> HealthHues.Body
    DashCard.BALANCE -> HealthHues.Respiration
    DashCard.NEXT_WORKOUT, DashCard.TRAINING_TRENDS, DashCard.MUSCLE_SETS, DashCard.LIFTS -> WorkoutBlue
    DashCard.INSIGHTS -> HealthHues.Mind
    DashCard.CORRELATION -> HealthHues.Respiration
    DashCard.CHARGE -> app.superhealth.ui.theme.Base.Green
    DashCard.EFFORT -> app.superhealth.ui.theme.Base.Blue
    DashCard.SLEEP_STAGES -> HealthHues.Sleep
    DashCard.HRV_STATUS -> HealthHues.Heart
    DashCard.STRESS_DAY -> HealthHues.Mind
    DashCard.HR_ZONES -> HealthHues.Fitness
    DashCard.LOAD -> WorkoutBlue
    DashCard.DAY_TIMELINE -> HealthHues.Activity
    DashCard.COACH, DashCard.COACH_WEEK -> app.superhealth.ui.coach.CoachHue
    DashCard.FORM -> HealthHues.Activity
    DashCard.JOURNAL -> HealthHues.Mind
    DashCard.BEDTIME -> HealthHues.Sleep
    DashCard.CYCLE -> app.superhealth.ui.wellbeing.CycleHue
    DashCard.SLEEP_SCHEDULE, DashCard.SLEEP_QUALITY -> HealthHues.Sleep
    DashCard.NIGHT_HEART, DashCard.NIGHT_VITALS, DashCard.VITALS_TRENDS -> HealthHues.Heart
}

internal fun DashCard.icon(): ImageVector = when (this) {
    DashCard.CALORIES -> Icons.Rounded.LocalFireDepartment
    DashCard.WEIGHT -> Icons.Rounded.MonitorWeight
    DashCard.INTAKE -> Icons.Rounded.Restaurant
    DashCard.EXPENDITURE -> Icons.Rounded.LocalFireDepartment
    DashCard.MACROS -> Icons.Rounded.PieChart
    DashCard.STEPS -> Icons.AutoMirrored.Rounded.DirectionsWalk
    DashCard.HEART -> Icons.Rounded.Favorite
    DashCard.SLEEP -> Icons.Rounded.Bedtime
    DashCard.CARDIO -> Icons.AutoMirrored.Rounded.DirectionsRun
    DashCard.ENERGY -> Icons.Rounded.SelfImprovement
    DashCard.VITALS -> Icons.Rounded.MonitorHeart
    DashCard.HYDRATION -> Icons.Rounded.WaterDrop
    DashCard.ACTIVITIES -> Icons.Rounded.Timeline
    DashCard.TRAINING_WEEK -> Icons.Rounded.FitnessCenter
    DashCard.MUSCLES -> Icons.Rounded.AccessibilityNew
    DashCard.RECORDS -> Icons.Rounded.EmojiEvents
    DashCard.STRATEGY -> Icons.Rounded.Flag
    DashCard.RUNNING -> Icons.AutoMirrored.Rounded.DirectionsRun
    DashCard.RECOVERY -> Icons.Rounded.BatteryChargingFull
    DashCard.RINGS -> Icons.Rounded.DonutLarge
    DashCard.FOCUS -> Icons.Rounded.EventAvailable
    DashCard.HABITS -> Icons.Rounded.EventAvailable
    DashCard.BODY -> Icons.Rounded.Straighten
    DashCard.BALANCE -> Icons.Rounded.Balance
    DashCard.NEXT_WORKOUT -> Icons.Rounded.PlayArrow
    DashCard.TRAINING_TRENDS -> Icons.Rounded.Insights
    DashCard.MUSCLE_SETS -> Icons.Rounded.AccessibilityNew
    DashCard.LIFTS -> Icons.AutoMirrored.Rounded.TrendingUp
    DashCard.INSIGHTS -> Icons.Rounded.Lightbulb
    DashCard.CORRELATION -> Icons.Rounded.StackedLineChart
    DashCard.CHARGE -> Icons.Rounded.BatteryChargingFull
    DashCard.EFFORT -> Icons.Rounded.Bolt
    DashCard.SLEEP_STAGES -> Icons.Rounded.Bedtime
    DashCard.HRV_STATUS -> Icons.Rounded.MonitorHeart
    DashCard.STRESS_DAY -> Icons.Rounded.SelfImprovement
    DashCard.HR_ZONES -> Icons.Rounded.Favorite
    DashCard.LOAD -> Icons.AutoMirrored.Rounded.TrendingUp
    DashCard.DAY_TIMELINE -> Icons.Rounded.Timeline
    DashCard.COACH -> Icons.Rounded.AutoAwesome
    DashCard.COACH_WEEK -> Icons.Rounded.DateRange
    DashCard.FORM -> Icons.Rounded.Speed
    DashCard.JOURNAL -> Icons.Rounded.SentimentSatisfied
    DashCard.BEDTIME -> Icons.Rounded.Bedtime
    DashCard.CYCLE -> Icons.Rounded.CalendarMonth
    DashCard.SLEEP_SCHEDULE -> Icons.Rounded.Schedule
    DashCard.SLEEP_QUALITY -> Icons.Rounded.Hotel
    DashCard.NIGHT_HEART -> Icons.Rounded.FavoriteBorder
    DashCard.NIGHT_VITALS -> Icons.Rounded.NightsStay
    DashCard.VITALS_TRENDS -> Icons.AutoMirrored.Rounded.ShowChart
}

/** Mesures santé lues pour le tableau de bord. */
internal class HealthSnap(
    val series: Map<Metric, List<Pair<LocalDate, Double?>>> = emptyMap(),
    val cardio: CardioWeek? = null,
    val energy: List<Point> = emptyList(),
    val heartToday: List<Point> = emptyList(),
    val activities: List<Bridge.Activity> = emptyList(),
    val perf: app.superhealth.health.data.Performance.Snapshot? = null,
    val watch: WatchSnap = WatchSnap(),
    /** Anneaux de l'Apple Watch du jour (calories actives, minutes d'exercice, heures debout et leurs objectifs). */
    val apple: app.superhealth.health.data.ActivitySummary? = null,
) {
    fun with(w: WatchSnap) = HealthSnap(series, cardio, energy, heartToday, activities, perf, w, apple)
    fun latest(m: Metric): Pair<LocalDate, Double>? = series[m]?.lastOrNull { it.second != null }?.let { it.first to it.second!! }
    fun today(m: Metric): Double? = series[m]?.lastOrNull()?.takeIf { it.first == LocalDate.now() }?.second
}

/** Dernières mesures lues : affichées tout de suite au retour sur l'onglet, le temps de relire. */
private var lastSnap = HealthSnap()
private var lastWatch = WatchSnap()
/** Repas de la semaine déjà lus (carte Macros) : réaffichés sans clignoter quand la carte revient à l'écran. */
private var macrosCache: List<app.superhealth.nutrition.data.FoodEntryEntity> = emptyList()

/** Constantes choisies (Personnaliser > Constantes). */
private val VITALS: List<Metric> get() = app.superhealth.SuperPrefs.settings.value.vitalMetrics

private fun Metric.dashMetrics(card: DashCard): Boolean = when (card) {
    DashCard.STEPS -> this == Metric.STEPS
    DashCard.HEART -> this == Metric.HEART_RATE || this == Metric.RESTING_HR
    DashCard.SLEEP -> this == Metric.SLEEP
    DashCard.ENERGY -> this == Metric.BODY_ENERGY || this == Metric.STRESS
    DashCard.HYDRATION -> this == Metric.HYDRATION
    DashCard.VITALS -> this in VITALS
    DashCard.RINGS -> this in ringMetrics()
    else -> false
}

/**
 * Une carte « montre » s'affiche dès qu'une source existe (montre qui mesure, ou Health Connect), sans attendre ses données :
 * les cartes ne surgissent plus après coup en décalant tout l'écran.
 */
private fun hasSource(c: DashCard): Boolean = when (c) {
    DashCard.HEART -> Bridge.hasSource(Metric.HEART_RATE) || Bridge.hasSource(Metric.RESTING_HR)
    DashCard.SLEEP -> Bridge.hasSource(Metric.SLEEP)
    DashCard.CARDIO -> Bridge.hasSource(Metric.CARDIO_WEEK)
    DashCard.ENERGY -> Bridge.hasSource(Metric.BODY_ENERGY) || Bridge.hasSource(Metric.STRESS)
    DashCard.VITALS -> VITALS.any { Bridge.hasSource(it) }
    DashCard.RUNNING, DashCard.RECOVERY -> app.superhealth.bridge.Watch.devices().isNotEmpty()
    else -> if (c.isWatchChart) watchSource(c) else true
}

/** Mesures montre lues pour les anneaux choisis. */
private fun ringMetrics(): Set<Metric> = Rings.kinds(app.superhealth.SuperPrefs.settings.value.rings).mapNotNull {
    when (it) {
        Rings.Kind.STEPS -> Metric.STEPS
        Rings.Kind.ENERGY -> Metric.BODY_ENERGY
        Rings.Kind.WATER -> Metric.HYDRATION
        Rings.Kind.SLEEP -> Metric.SLEEP
        else -> null
    }
}.toSet()

/** Mesures des cartes affichées : séries des jours en parallèle (la base de la montre accepte plusieurs lectures). */
private suspend fun loadHealth(ctx: Context, cards: List<DashCard>): HealthSnap = kotlinx.coroutines.coroutineScope {
        val today = LocalDate.now()
        val metrics = Metric.entries.filter { m -> cards.any { m.dashMetrics(it) } && Bridge.hasSource(m) }
        val series = metrics.associateWith { m ->
            val days = if (m == Metric.STEPS || m == Metric.SLEEP || m == Metric.HYDRATION) 7L else 14L
            (days - 1 downTo 0).map { today.minusDays(it) }.map { d -> async { d to runCatching { Bridge.healthValue(ctx, m, d) }.getOrNull() } }
        }
        val wantCardio = DashCard.CARDIO in cards || (DashCard.RINGS in cards && Rings.Kind.CARDIO in Rings.kinds(app.superhealth.SuperPrefs.settings.value.rings))
        val cardio = if (wantCardio && Bridge.hasSource(Metric.CARDIO_WEEK)) runCatching { HealthRepository.cardioWeek(today) }.getOrNull() else null
        val energy = if (DashCard.ENERGY in cards && Bridge.hasSource(Metric.BODY_ENERGY)) runCatching { HealthRepository.intraday(Metric.BODY_ENERGY, today) }.getOrDefault(emptyList()) else emptyList()
        val heart = if (DashCard.HEART in cards && HealthRepository.isSupported(Metric.HEART_RATE)) runCatching { HealthRepository.intraday(Metric.HEART_RATE, today) }.getOrDefault(emptyList()) else emptyList()
        val acts = if (DashCard.ACTIVITIES in cards) runCatching { Bridge.activities(0L).take(4) }.getOrDefault(emptyList()) else emptyList()
        val perf = if (DashCard.RUNNING in cards || DashCard.RECOVERY in cards) runCatching { app.superhealth.health.data.Performance.snapshot(ctx) }.getOrNull() else null
        val heart2 = if (heart.isEmpty() && DashCard.DAY_TIMELINE in cards && HealthRepository.isSupported(Metric.HEART_RATE))
            runCatching { HealthRepository.intraday(Metric.HEART_RATE, today) }.getOrDefault(emptyList()) else heart
        val ringKinds = Rings.kinds(app.superhealth.SuperPrefs.settings.value.rings)
        val wantApple = DashCard.RINGS in cards && ringKinds.any { it == Rings.Kind.MOVE || it == Rings.Kind.EXERCISE || it == Rings.Kind.STAND }
        val apple = if (wantApple && app.superhealth.bridge.Watch.devices().isNotEmpty())
            runCatching { app.superhealth.health.data.HealthSources.current.activitySummary(today) }.getOrNull() else null
        HealthSnap(series.mapValues { it.value.awaitAll() }, cardio, energy, heart2, acts, perf, apple = apple)
}

@Composable
fun DashboardScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val sh by vm.settings.collectAsState()
    val a by vm.nutrition.analytics.collectAsState()
    val refresh by vm.refresh.collectAsState()
    val devices by vm.devices.collectAsState()
    // parties de l'app coupées : leurs cartes ne s'affichent pas (elles restent dans la liste, pour revenir à la réactivation)
    val cards = sh.dashboard.filter { c -> c.feature()?.let { sh.has(it) } ?: true }

    val base by produceState(lastSnap, refresh, cards, sh.watchModule, devices.size, sh.vitals, sh.rings) {
        // Lectures de la base de la montre hors du thread principal (elles faisaient saccader l'écran à chaque retour).
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadHealth(ctx, cards) }.also { lastSnap = it }
    }
    // scores et graphiques de montre (Charge sur 60 nuits…) lus à part : ils ne retardent pas les autres cartes
    val watchSnap by produceState(lastWatch, refresh, cards, sh.watchModule, devices.size) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { loadWatch(ctx, cards) }.getOrDefault(lastWatch) }.also { lastWatch = it }
    }
    val health = remember(base, watchSnap) { base.with(watchSnap) }

    fun tab(route: String) { runCatching { nav.openTab(route) } }

    val name = remember(refresh) { app.superhealth.ui.userName() }
    val modules by app.superhealth.modules.Modules.installed.collectAsState()
    // cartes qui posent une question (moral du jour) : elles s'effacent une fois la réponse donnée
    val moodDays by app.superhealth.wellbeing.Journal.days.collectAsState()
    val moodDone = moodDays[app.superhealth.platform.LocalDate.now().toEpochDay()] != null && app.superhealth.ui.wellbeing.moodOpen != app.superhealth.platform.LocalDate.now().toEpochDay()
    val visible = remember(cards, sh.watchModule, sh.healthConnect, devices.size, refresh, modules, moodDone) {
        cards.filter { (!it.watch || hasSource(it)) && it.available && (!it.isWatchChart || watchSource(it)) && !(it == DashCard.JOURNAL && moodDone) }
    }
    // ressenti du matin : les questions sur la première carte à score visible, en grand de préférence
    checkinFirst = visible.filter { it in app.superhealth.ui.wellbeing.CHECKIN_GATED }
        .let { g -> g.firstOrNull { sh.sizeOf(it) == app.superhealth.CardSize.L } ?: g.firstOrNull() }
    val rows = remember(visible, sh.dashSizes) { app.superhealth.packRows(visible) { sh.sizeOf(it).span } }
    var editing by remember { mutableStateOf<DashCard?>(null) }
    // Liste paresseuse : seules les rangées à l'écran sont composées (premier affichage plus rapide, moins de mémoire).
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            // En-tête : salutation et date, réglages du tableau de bord, avatar (profil).
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(longToday(), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
                    Text(name?.let { trf("Bonjour %1\$s", it) } ?: tr("Aujourd'hui"), style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    // module Coach : son étincelle en tête, à portée de pouce
                    if (modules.any { it.kind == app.superhealth.modules.Modules.COACH && it.enabled })
                        app.superhealth.nutrition.ui.components.CircleAction(Icons.Rounded.AutoAwesome, tr("Coach"), tint = app.superhealth.ui.coach.CoachHue) { nav.go("coach") }
                    app.superhealth.nutrition.ui.components.CircleAction(Icons.Rounded.Tune, tr("Personnaliser")) { nav.go("dash/edit") }
                    app.superhealth.nutrition.ui.components.SettingsAvatar { nav.go("settings") }
                }
            }
        }
        val an = a ?: return@LazyColumn
        item(key = "banner") {
            // Seul élément au-dessus de la grille : le bandeau de pesée (désactivable), refermé d'un geste.
            Column(Modifier.padding(horizontal = 16.dp)) { WeighBanner(vm, an); Spacer(Modifier.height(6.dp)) }
        }
        // Grille de 3 blocs : carré (S, 1 bloc), demi (M, 2 blocs), pleine largeur (L) ; les trous se comblent tout seuls.
        items(rows.size, key = { i -> rows[i].joinToString("+") { it.name } }) { i ->
            val row = rows[i]
            val used = row.sumOf { sh.sizeOf(it).span }
            val onlySquares = row.all { sh.sizeOf(it) == app.superhealth.CardSize.S }
            // hauteur commune entre cartes voisines
            val sameHeight = row.size > 1
            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = GAP).fillMaxWidth().then(if (sameHeight) Modifier.height(IntrinsicSize.Min) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(GAP)) {
                row.forEach { c ->
                    val size = sh.sizeOf(c)
                    // rangée incomplète : un carré garde son bloc, une carte plus large s'étend sur la place libre
                    val w = if (used == app.superhealth.GRID || onlySquares || size == app.superhealth.CardSize.S) size.span.toFloat()
                        else size.span + (app.superhealth.GRID - used).toFloat() / row.count { sh.sizeOf(it) != app.superhealth.CardSize.S }
                    androidx.compose.runtime.CompositionLocalProvider(LocalCardLongPress provides { editing = c }, LocalFillHeight provides (sameHeight && !onlySquares)) {
                        Card(c, size, vm, nav, health, Modifier.weight(w).then(if (sameHeight) Modifier.fillMaxHeight() else Modifier), ::tab)
                    }
                }
                if (onlySquares && used < app.superhealth.GRID) Spacer(Modifier.weight((app.superhealth.GRID - used).toFloat()))
            }
        }
        item(key = "footer") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (visible.isEmpty()) {
                    MfCard(onClick = { nav.go("dash/edit") }) {
                        Text(tr("Aucune carte affichée"), style = MaterialTheme.typography.titleMedium)
                        Text(tr("Touchez pour choisir vos graphiques."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                }
                Spacer(Modifier.height(4.dp))
                PillButton(tr("Personnaliser le tableau de bord"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.Tune) { nav.go("dash/edit") }
            }
        }
    }
    editing?.let { c -> CardSheet(c, sh, onDismiss = { editing = null }) { editing = null; nav.go("dash/edit") } }
}

/**
 * Bandeau de pesée, tout en haut du tableau de bord, tant qu'aucune pesée n'est saisie aujourd'hui. Toucher : saisir la
 * pesée sans quitter l'écran ; flèche : le replier jusqu'à demain. Désactivable dans Réglages > Tableau de bord.
 */
@Composable
private fun WeighBanner(vm: SuperViewModel, an: app.superhealth.nutrition.data.Analytics) {
    val sh by vm.settings.collectAsState()
    val s by vm.nutrition.settings.collectAsState()
    val profile by vm.nutrition.profile.collectAsState()
    val today = LocalDate.now().toEpochDay()
    val weighed = an.days.any { it.epochDay == today && it.weightKg != null }
    var sheet by remember { mutableStateOf(false) }
    val hue = DashCard.WEIGHT.hue()
    androidx.compose.animation.AnimatedVisibility(sh.weighPrompt && !weighed && sh.weighSkipDay != today,
        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()) {
        app.superhealth.ui.theme.CardTone(hue) {
        Row(Modifier.padding(top = 2.dp, bottom = 6.dp).fillMaxWidth().clip(RoundedCornerShape(CardRadius)).dashCardBackground(hue)
            .clickable { sheet = true }.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Rounded.MonitorWeight, hue, 30.dp)
            Spacer(Modifier.width(12.dp))
            Text(tr("Pesée du jour"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Box(Modifier.size(34.dp).clip(CircleShape).clickable { app.superhealth.SuperPrefs.update { it.copy(weighSkipDay = today) } },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.KeyboardArrowUp, tr("Masquer jusqu'à demain"), tint = Mf.colors.textTertiary)
            }
        }
        }
    }
    if (sheet) app.superhealth.nutrition.ui.screens.WeighInSheet(null, s, profile.sex, onDismiss = { sheet = false }, onDelete = null) { w ->
        sheet = false
        vm.nutrition.launch { weighIns.upsert(w) }
    }
}

/**
 * « À faire » : rappels (check-in, pesée, source manquante) et ce qu'il reste pour les objectifs du jour (protéines, pas,
 * séance prévue). Une ligne disparaît une fois faite ; carte optionnelle, placée où l'on veut.
 */
@Composable
internal fun focusItems(vm: SuperViewModel, an: app.superhealth.nutrition.data.Analytics, h: HealthSnap, nav: NavHostController,
                        tab: (String) -> Unit): List<Triple<ImageVector, Color, Pair<String, () -> Unit>>> {
    val s by vm.nutrition.settings.collectAsState()
    val sh by vm.settings.collectAsState()
    val devices by vm.devices.collectAsState()
    val eaten by vm.nutrition.todayEntries.collectAsState()
    val st by vm.training.state.collectAsState()
    return buildList {
        if (an.checkInState == CheckInState.NOW) add(Triple(Icons.Rounded.EventAvailable, HealthHues.Body, tr("Check-in disponible") to { nav.go("checkin") }))
        // rappel médication : prises dont l'heure est passée, pas encore cochées
        val medsTaken by app.superhealth.meds.Meds.taken.collectAsState()
        if (medsTaken.isNotEmpty() || app.superhealth.meds.Meds.meds.value.isNotEmpty()) app.superhealth.meds.Meds.late().takeIf { it.isNotEmpty() }?.let { late ->
            add(Triple(Icons.Rounded.Medication, app.superhealth.ui.meds.MedsHue,
                (if (late.size == 1) late[0].med.name else trf("%1\$s prises à faire", late.size)) to { nav.go("meds") }))
        }
        // photo de progression : au départ, puis à chaque palier de l'objectif (25, 50, 75, 100 %)
        val photos by remember { vm.nutrition.repo.body.photosFlow() }.collectAsState(initial = emptyList())
        app.superhealth.nutrition.domain.PhotoMilestones.due(an, photos)?.let { m ->
            add(Triple(Icons.Rounded.Straighten, HealthHues.Body, (if (m == 0) tr("Photo de départ") else trf("%1\$s %% de votre objectif : photo", m)) to { nav.go("body") }))
        }
        if (an.days.lastOrNull()?.weightKg == null) add(Triple(Icons.Rounded.MonitorWeight, HealthHues.Body, tr("Pesée du jour") to { nav.go("weight") }))
        val target = an.targetFor(an.today).macros.protein
        val p = target - eaten.sumOf { it.macros().protein }
        if (target > 0 && p > 1) add(Triple(Icons.Rounded.Restaurant, ProteinColor, trf("%1\$s g de protéines", p.roundToInt()) to { tab("log") }))
        val steps = h.today(Metric.STEPS)
        val stepGoal = s.stepGoalFor(LocalDate.now())
        if (stepGoal > 0 && steps != null && steps < stepGoal)
            add(Triple(Icons.AutoMirrored.Rounded.DirectionsWalk, HealthHues.Activity, trf("%1\$s pas restants", formatNumber(stepGoal - steps)) to { nav.go("h/metric/STEPS") }))
        val prog = st.programs.firstOrNull { it.id == st.settings.activeProgramId }
        val day = prog?.days?.getOrNull(prog.dayIndex)
        when {
            st.session != null -> add(Triple(Icons.Rounded.FitnessCenter, WorkoutBlue, trf("%1\$s en cours", st.session!!.name) to { nav.go("session") }))
            day != null && !day.isRest && st.history.none { localDate(it.startTime) == LocalDate.now() } ->
                add(Triple(Icons.Rounded.FitnessCenter, WorkoutBlue, trf("Séance : %1\$s", day.name) to { tab("activities") }))
        }
        if (sh.dashboard.any { it.watch } && !(sh.watchModule && devices.isNotEmpty()) && !sh.healthConnect)
            add(Triple(Icons.Rounded.Watch, HealthHues.Heart, tr("Associez une montre ou Apple Santé") to { nav.go("settings") }))
    }
}

/**
 * « À faire » : rappels (check-in, pesée, source manquante) et ce qu'il reste pour les objectifs du jour (protéines, pas,
 * séance prévue). Une ligne disparaît une fois faite ; carte optionnelle, placée où l'on veut.
 */
@Composable
private fun FocusCard(c: DashCard, vm: SuperViewModel, an: app.superhealth.nutrition.data.Analytics, h: HealthSnap, modifier: Modifier,
                      nav: NavHostController, tab: (String) -> Unit) {
    val items = focusItems(vm, an, h, nav, tab)
    Tile(c, tr("À faire"), modifier, tinted = false, onClick = null, padding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp)) {
        if (items.isEmpty()) Text(tr("Tout est fait pour aujourd'hui."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary,
            modifier = Modifier.padding(bottom = 8.dp))
        items.forEach { (icon, hue, t) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { t.second() }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                IconChip(icon, hue, 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(t.first, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
            }
        }
    }
}

/** Espace entre les cartes de la grille. */
internal val GAP = 10.dp

/** Appui long sur une carte : réglages rapides (taille, place, masquer). */
/** Carte posée dans une rangée à hauteur commune : un carré prend toute la hauteur au lieu de rester carré. */
internal val LocalFillHeight = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Carré d'un bloc, ou toute la hauteur de la rangée à côté d'une carte plus haute. */
@Composable
internal fun Modifier.square(): Modifier = if (LocalFillHeight.current) this else this.aspectRatio(1f)

internal val LocalCardLongPress = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }


/** Réglages rapides d'une carte, sans quitter le tableau de bord. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CardSheet(c: DashCard, sh: app.superhealth.SuperSettings, onDismiss: () -> Unit, onMore: () -> Unit) {
    fun move(d: Int) = app.superhealth.SuperPrefs.update { st ->
        val i = st.dashboard.indexOf(c); val j = (i + d).coerceIn(0, st.dashboard.lastIndex)
        st.copy(dashboard = with(app.superhealth.training.domain.ProgramEdit) { st.dashboard.moved(i, j) })
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Mf.colors.background) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(c.icon(), c.hue(), 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(tr(c.label), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (c.sizes.size > 1) {
                Spacer(Modifier.height(16.dp))
                Text(tr("Taille"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    c.sizes.chunked(4).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            line.forEach { z ->
                                val on = sh.sizeOf(c) == z
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (on) Mf.colors.strong else Mf.colors.card)
                                    .clickable { app.superhealth.SuperPrefs.update { it.copy(dashSizes = it.dashSizes + (c.name to z.name)) } }.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    SizeGlyph(z, if (on) Mf.colors.onStrong else c.hue(), if (on) Mf.colors.onStrong.copy(alpha = 0.25f) else Mf.colors.chip)
                                    Spacer(Modifier.height(6.dp))
                                    Text(z.title, style = MaterialTheme.typography.labelLarge, color = if (on) Mf.colors.onStrong else Mf.colors.text, maxLines = 1)
                                }
                            }
                            repeat(4 - line.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(tr("Monter"), Modifier.weight(1f), strong = false) { move(-1) }
                PillButton(tr("Descendre"), Modifier.weight(1f), strong = false) { move(1) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(tr("Masquer"), Modifier.weight(1f), strong = false) { app.superhealth.SuperPrefs.update { it.copy(dashboard = it.dashboard - c) }; onDismiss() }
                PillButton(tr("Tout personnaliser"), Modifier.weight(1f), icon = Icons.Rounded.Tune, onClick = onMore)
            }
        }
    }
}

/** Silhouette d'une taille dans une grille de 3 × 2 blocs (la forme en couleur, la grille en fond). */
@Composable
internal fun SizeGlyph(z: app.superhealth.CardSize, color: Color, grid: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(width = 42.dp, height = 28.dp)) {
        val g = 2.dp.toPx(); val cw = (size.width - 2 * g) / 3; val ch = (size.height - g) / 2
        val r = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
        for (i in 0..2) for (j in 0..1) drawRoundRect(grid, androidx.compose.ui.geometry.Offset(i * (cw + g), j * (ch + g)),
            androidx.compose.ui.geometry.Size(cw, ch), r)
        val w = z.span * cw + (z.span - 1) * g
        val h = when (z) { app.superhealth.CardSize.L -> size.height; app.superhealth.CardSize.W -> ch * 0.5f; else -> ch }
        drawRoundRect(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Size(w, h), r)
    }
}

/** Pastille ronde teintée de sa couleur, symbole dans la couleur (comme Google Santé). */
@Composable
private fun IconChip(icon: ImageVector, hue: Color, size: androidx.compose.ui.unit.Dp = 30.dp, onTint: Boolean = false) {
    val (bg, tint) = app.superhealth.ui.theme.dashChipColors(hue)
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.58f), tint = tint)
    }
}

/**
 * Carte du tableau de bord : pastille ronde de la famille et titre, détail gris et chevron à droite, puis le contenu
 * sur fond neutre. [tinted] est gardé pour les appels existants mais ne teinte plus
 * le fond (une seule famille de cartes dans toute l'app). Le contenu de [bottom] est poussé en bas de la carte, pour
 * aligner les graphiques de deux cartes voisines.
 */
@Composable
internal fun Tile(
    c: DashCard, title: String, modifier: Modifier, tinted: Boolean = true, trailing: String? = null,
    onClick: (() -> Unit)?, padding: PaddingValues = PaddingValues(14.dp),
    bottom: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val hue = c.ink()
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius))
            .dashCardBackground(hue)
            .cardClick(onClick).padding(padding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(c.icon(), hue, 26.dp)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = Mf.colors.textSecondary, maxLines = 1) }
            if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = Mf.colors.textTertiary)
        }
        Spacer(Modifier.height(10.dp))
        content()
        if (bottom != null) {
            Spacer(Modifier.height(10.dp))
            Spacer(Modifier.weight(1f))
            bottom()
        }
    }
}

/** Toucher : ouvre la carte ; appui long : réglages rapides de la carte. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.cardClick(onClick: (() -> Unit)?): Modifier {
    val long = LocalCardLongPress.current
    return if (onClick == null && long == null) this
    else this.then(Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = long))
}

/** Fond des pistes (barres, anneaux) posées sur une carte. */
@Composable
internal fun softTrack(): Color = Mf.colors.chip

/** Grande valeur et son unité. */
@Composable
internal fun Value(value: String, unit: String? = null, color: Color = Mf.colors.text, big: Boolean = false) {
    // valeur en gras, unité grise plus petite sur la même ligne de base (comme « 72 BPM » dans Santé)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium, color = color, maxLines = 1,
            modifier = Modifier.alignByBaseline())
        if (!unit.isNullOrBlank()) Text(" $unit", style = MaterialTheme.typography.titleSmall, color = Mf.colors.textSecondary, maxLines = 1,
            modifier = Modifier.alignByBaseline())
    }
}

@Composable
internal fun Sub(text: String?) {
    text?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** Cartes aux couleurs multiples (séries, muscles, phases) : en « Plein », fond profond pour que ces couleurs restent lisibles. */
private val richCards = setOf(DashCard.RINGS, DashCard.MUSCLES, DashCard.MUSCLE_SETS, DashCard.ACTIVITIES, DashCard.VITALS, DashCard.SLEEP_STAGES,
    DashCard.HR_ZONES, DashCard.DAY_TIMELINE, DashCard.MACROS, DashCard.CALORIES, DashCard.INSIGHTS, DashCard.CORRELATION, DashCard.BALANCE,
    DashCard.FOCUS, DashCard.LOAD, DashCard.CHARGE, DashCard.ENERGY, DashCard.SLEEP, DashCard.NIGHT_VITALS, DashCard.VITALS_TRENDS)

/** Une carte dans son ton (labo de style) : fond et couleurs de la carte suivent la variante choisie. */
@Composable
private fun Card(c: DashCard, size: app.superhealth.CardSize, vm: SuperViewModel, nav: NavHostController, h: HealthSnap, modifier: Modifier, tab: (String) -> Unit) {
    app.superhealth.ui.theme.CardTone(c.hue(), rich = c in richCards, night = c == DashCard.RINGS) { CardBody(c, size, vm, nav, h, modifier, tab) }
}

/** Carte qui porte les questions du ressenti du matin (les autres cartes à score y renvoient). */
private var checkinFirst: DashCard? = null

@Composable
private fun CardBody(c: DashCard, size: app.superhealth.CardSize, vm: SuperViewModel, nav: NavHostController, h: HealthSnap, modifier: Modifier, tab: (String) -> Unit) {
    val an = vm.nutrition.analytics.collectAsState().value ?: return
    val s by vm.nutrition.settings.collectAsState()
    // ressenti du matin d'abord : les cartes à score attendent la réponse
    if (c in app.superhealth.ui.wellbeing.CHECKIN_GATED && app.superhealth.ui.wellbeing.checkinPending()) {
        app.superhealth.ui.wellbeing.CheckinCard(c, size, c == checkinFirst, modifier, nav)
        return
    }
    // bulle (S) ou tuile générique (M) des cartes sans mise en page demi-largeur propre
    // Petit, moyen et bandeau : versions à regarder d'un coup d'œil, communes à toutes les cartes qui ont une valeur ;
    // grand : la version détaillée de chaque carte.
    val special = when (c) {
        DashCard.RINGS, DashCard.INSIGHTS, DashCard.CORRELATION, DashCard.COACH, DashCard.COACH_WEEK,
        DashCard.FORM, DashCard.JOURNAL, DashCard.BEDTIME, DashCard.CYCLE, DashCard.VITALS, DashCard.VITALS_TRENDS -> true
        DashCard.NEXT_WORKOUT -> size == app.superhealth.CardSize.W
        DashCard.MUSCLES -> size != app.superhealth.CardSize.W
        else -> false
    }
    if (size != app.superhealth.CardSize.L && !special) {
        val d = compactOf(c, vm, h, an, s, nav, tab) ?: return
        when (size) {
            app.superhealth.CardSize.S -> CompactBubble(c, d, modifier)
            app.superhealth.CardSize.W -> CompactStrip(c, d, modifier)
            else -> CompactMedium(c, d, modifier)
        }
        return
    }
    when (c) {
        DashCard.CALORIES -> {
            val todayEntries by vm.nutrition.todayEntries.collectAsState()
            val consumed = todayEntries.fold(Macros.ZERO) { acc, e -> acc + e.macros() }
            val target = an.targetFor(an.today).macros
            val left = s.energyValue(target.kcal - consumed.kcal)
            Tile(c, tr("Calories restantes"), modifier, onClick = { vm.nutrition.selectedDay.value = an.today; tab("log") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(if (target.kcal > 0) (consumed.kcal / target.kcal).toFloat() else 0f, CaloriesColor, 128.dp, stroke = 12.dp,
                        track = softTrack()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatNumber(abs(left)), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1,
                                color = CaloriesColor.onContainer())
                            Text(if (left < 0) tr("en trop") else s.energyUnit.label.let(::tr), style = MaterialTheme.typography.labelMedium,
                                color = if (left < 0) HealthHues.Heart else Mf.colors.textSecondary, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MacroPill(tr("Protéines"), consumed.protein, target.protein, ProteinColor)
                        MacroPill(tr("Lipides"), consumed.fat, target.fat, FatColor)
                        MacroPill(tr("Glucides"), consumed.carbs, target.carbs, CarbsColor)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    Sub(trf("Consommé %1\$s", formatNumber(s.energyValue(consumed.kcal))))
                    Spacer(Modifier.weight(1f))
                    Sub(trf("Objectif %1\$s", formatNumber(s.energyValue(target.kcal))))
                }
            }
        }
        DashCard.WEIGHT -> {
            val pts = an.trend.takeLast(28)
            val week = an.trend.takeLast(8)
            Tile(c, tr("Tendance du poids"), modifier, onClick = { nav.go("weight") }, bottom = {
                LineChart(listOf(
                    Series(pts.filter { it.scale != null }.map { it.epochDay to s.weightValue(it.scale!!) }, c.ink().copy(alpha = 0.45f), dots = true),
                    Series(pts.map { it.epochDay to s.weightValue(it.trend) }, c.ink()),
                ), height = 56.dp, showAxis = false)
            }) {
                Value(an.currentTrendKg?.let { formatNumber(s.weightValue(it), 1) } ?: "—", tr(s.weightUnit.label), color = c.ink().onContainer())
                Sub(if (week.size >= 2) signed(s.weightValue(week.last().trend - week.first().trend), 2) + tr(" / sem.") else tr("Ajoutez des pesées"))
            }
        }
        DashCard.EXPENDITURE -> {
            val pts = an.expenditure.takeLast(28)
            val first = pts.firstOrNull()?.tdee
            Tile(c, tr("Dépense"), modifier, onClick = { nav.go("expenditure") }, bottom = {
                LineChart(listOf(Series(pts.map { it.epochDay to s.energyValue(it.tdee) }, c.ink())), height = 56.dp, showAxis = false)
            }) {
                Value(s.energyValue(an.currentTdee).roundToInt().toString(), tr(s.energyUnit.label), color = c.ink().onContainer())
                Sub(when (an.expenditure.lastOrNull()?.status) {
                    Expenditure.Status.HOLDING -> tr("En pause (données insuffisantes)")
                    Expenditure.Status.INITIAL, null -> tr("Estimation initiale")
                    else -> first?.let { signed(s.energyValue(an.currentTdee - it), 0) + trf(" sur %1\$s j", pts.size) }
                })
            }
        }
        DashCard.INTAKE -> {
            val days = (an.today - 6..an.today).toList()
            val byDay = an.days.associateBy { it.epochDay }
            val avg = days.mapNotNull { byDay[it]?.intakeKcal }.takeIf { it.isNotEmpty() }?.average()
            Tile(c, tr("Apports des 7 derniers jours"), modifier, onClick = { nav.go("nutrition") }) {
                Value(avg?.let { s.energyValue(it).roundToInt().toString() } ?: "—", trf("%1\$s / jour en moyenne", tr(s.energyUnit.label)), color = c.ink().onContainer())
                Spacer(Modifier.height(12.dp))
                BarChart(days.map { byDay[it]?.intakeKcal?.let(s::energyValue) }, days.map { s.energyValue(an.targetFor(it).macros.kcal) }, c.ink(),
                    height = 90.dp, labels = days.map { dayLetter(LocalDate.ofEpochDay(it)) })
            }
        }
        DashCard.MACROS -> {
            val days = (an.today - 6..an.today)
            val entries by remember(an.today) { vm.nutrition.repo.entries.rangeFlow(days.first, days.last) }.collectAsState(macrosCache)
            macrosCache = entries
            val logged = entries.groupBy { it.epochDay }
            val n = logged.size.coerceAtLeast(1)
            val avg = logged.values.flatten().fold(Macros.ZERO) { acc, e -> acc + e.macros() }.let { Macros(it.kcal / n, it.protein / n, it.fat / n, it.carbs / n) }
            val tgt = days.map { an.targetFor(it).macros }.let { l -> Macros(l.sumOf { it.kcal } / 7, l.sumOf { it.protein } / 7, l.sumOf { it.fat } / 7, l.sumOf { it.carbs } / 7) }
            Tile(c, tr("Macros de la semaine"), modifier, trailing = trf("%1\$s j journalisés", logged.size), onClick = { nav.go("macros") }) {
                MacroBar(tr("Protéines"), avg.protein, tgt.protein, ProteinColor)
                MacroBar(tr("Lipides"), avg.fat, tgt.fat, FatColor)
                MacroBar(tr("Glucides"), avg.carbs, tgt.carbs, CarbsColor)
            }
        }
        DashCard.STRATEGY -> Box(modifier) { StrategySummaryCard(an, s) { nav.go("strategy") } }
        DashCard.RINGS -> {
            val todayEntries by vm.nutrition.todayEntries.collectAsState()
            val sh by vm.settings.collectAsState()
            RingsCard(c, size, dashRings(sh.rings, h, todayEntries, an, s), s, modifier, nav, tab)
        }
        DashCard.HABITS -> HabitsCard(c, vm, an, modifier, nav)
        DashCard.BODY -> BodyCard(c, vm, an, modifier, nav)
        DashCard.FOCUS -> FocusCard(c, vm, an, h, modifier, nav, tab)
        DashCard.BALANCE -> BalanceCard(c, an, s, modifier, nav)
        DashCard.NEXT_WORKOUT -> NextWorkoutCard(c, size, vm, modifier, nav, tab)
        DashCard.TRAINING_TRENDS -> TrendsCard(c, vm, modifier, nav)
        DashCard.MUSCLE_SETS -> MuscleSetsCard(c, vm, modifier, nav)
        DashCard.LIFTS -> LiftsCard(c, vm, modifier, nav)
        DashCard.INSIGHTS -> InsightsCard(c, size, vm, s, modifier, nav)
        DashCard.CORRELATION -> CorrelationCard(c, size, vm, s, modifier)
        DashCard.CHARGE -> ChargeCard(c, h.watch, modifier, nav)
        DashCard.EFFORT -> EffortCard(c, h.watch, modifier, nav)
        DashCard.SLEEP_STAGES -> SleepStagesCard(c, h.watch, modifier, nav)
        DashCard.HRV_STATUS -> HrvStatusCard(c, h.watch, modifier, nav)
        DashCard.STRESS_DAY -> StressDayCard(c, h.watch, modifier, nav)
        DashCard.HR_ZONES -> HrZonesCard(c, h.watch, modifier, nav)
        DashCard.LOAD -> LoadCard(c, h.watch, modifier, nav)
        DashCard.DAY_TIMELINE -> DayTimelineCard(c, h, h.watch, modifier, nav)
        DashCard.SLEEP_SCHEDULE -> SleepScheduleCard(c, h.watch, modifier, nav)
        DashCard.SLEEP_QUALITY -> SleepQualityCard(c, h.watch, modifier, nav)
        DashCard.NIGHT_HEART -> NightHeartCard(c, h.watch, modifier, nav)
        DashCard.NIGHT_VITALS -> NightVitalsCard(c, h.watch, modifier, nav)
        DashCard.VITALS_TRENDS -> if (size == app.superhealth.CardSize.L) VitalsTrendsCard(c, h.watch, modifier, nav) else TrendsCompact(c, size, h.watch.trends, modifier, nav)
        DashCard.COACH -> app.superhealth.ui.coach.CoachCard(c, size, vm, modifier, nav)
        DashCard.COACH_WEEK -> app.superhealth.ui.coach.CoachWeekCard(c, size, vm, modifier, nav)
        DashCard.FORM -> app.superhealth.ui.wellbeing.FormCard(c, size, vm, modifier, nav)
        DashCard.JOURNAL -> app.superhealth.ui.wellbeing.JournalCard(c, size, vm, modifier, nav)
        DashCard.BEDTIME -> app.superhealth.ui.wellbeing.BedtimeCard(c, size, modifier, nav)
        DashCard.CYCLE -> app.superhealth.ui.wellbeing.CycleCard(c, size, modifier, nav)
        DashCard.STEPS -> {
            val series = h.series[Metric.STEPS].orEmpty()
            val today = h.today(Metric.STEPS)
            val goal = s.stepGoalFor(LocalDate.now())
            Tile(c, tr("Pas"), modifier, onClick = { nav.go("h/metric/STEPS") }, bottom = {
                BarChart(series.map { it.second }, series.map { s.stepGoalFor(it.first).takeIf { g -> g > 0 }?.toDouble() }, c.ink(), height = 34.dp, track = softTrack(), dimBelowGoal = true,
                    labels = series.map { dayLetter(it.first) })
            }) {
                Value(today?.let { formatNumber(it) } ?: "—", color = c.ink().onContainer())
                Sub(if (goal > 0) trf("Objectif %1\$s", formatNumber(goal.toDouble())) else null)
                if (goal > 0) { Spacer(Modifier.height(6.dp)); ProgressBar(((today ?: 0.0) / goal).toFloat(), c.ink(), height = 6.dp, track = softTrack()) }
            }
        }
        DashCard.HEART -> HeartCard(c, h, nav, modifier)
        DashCard.RUNNING -> {
            val weeks = h.perf?.weeks.orEmpty().takeLast(8)
            val cur = weeks.lastOrNull()
            Tile(c, tr("Course à pied"), modifier, onClick = { nav.go("h/performance") }) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Value(cur?.let { formatNumber(it.runKm, 1) } ?: "—", "km", color = c.ink().onContainer(), big = true)
                    Spacer(Modifier.weight(1f))
                    h.perf?.records?.firstOrNull { it.label == "5 km" }?.let { r ->
                        Column(horizontalAlignment = Alignment.End) {
                            Text(app.superhealth.ui.health.clock(r.sec), style = MaterialTheme.typography.titleMedium, color = c.ink().onContainer())
                            Text(tr("Record 5 km"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                        }
                    }
                }
                Sub(trf("Cette semaine · %1\$s séance(s)", cur?.sessions ?: 0))
                Spacer(Modifier.height(12.dp))
                BarChart(weeks.map { it.runKm.takeIf { k -> k > 0 } }, weeks.map { null }, c.ink(), height = 64.dp, track = softTrack(),
                    labels = weeks.map { "${it.start.dayOfMonth}/${it.start.monthValue}" })
            }
        }
        DashCard.RECOVERY -> {
            val p = h.perf
            Tile(c, tr("Récupération"), modifier, onClick = { nav.go("h/performance") }) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { app.superhealth.ui.health.RecoveryRing(p?.recoveryLeftH, 92.dp, 9.dp) }
                Spacer(Modifier.height(6.dp))
                Sub(p?.acwr?.let { trf("Charge %1\$s", tr(app.superhealth.ui.health.loadStatus(it).first).lowercase(app.superhealth.ui.Lang.locale)) })
            }
        }
        DashCard.SLEEP -> SleepWeekCard(c, h, modifier, nav)
        DashCard.CARDIO -> {
            val cw = h.cardio
            Tile(c, tr("Minutes cardio"), modifier, onClick = { nav.go("h/metric/CARDIO_WEEK") }) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Ring(cw?.progress ?: 0f, c.ink(), 92.dp, stroke = 9.dp, track = softTrack()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(cw?.total?.toString() ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("/ ${cw?.target ?: 150}", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Sub(cw?.let { trf("+%1\$s aujourd'hui", it.today) })
            }
        }
        DashCard.ENERGY -> {
            val energy = h.latest(Metric.BODY_ENERGY)
            val stress = h.latest(Metric.STRESS)
            val main = if (energy != null || Bridge.hasSource(Metric.BODY_ENERGY)) Metric.BODY_ENERGY else Metric.STRESS
            Tile(c, tr(main.label), modifier, onClick = { nav.go("h/metric/${main.name}") }, bottom = {
                val pts = if (h.energy.size >= 2) h.energy.map { it.timestampMs / 60000 to it.value }
                    else h.series[main].orEmpty().mapNotNull { (d, v) -> v?.let { d.toEpochDay() to it } }
                LineChart(listOf(Series(pts, c.ink())), height = 48.dp, showAxis = false)
            }) {
                Value((if (main == Metric.BODY_ENERGY) energy else stress)?.second?.let { formatNumber(it) } ?: "—", "/ 100", color = c.ink().onContainer())
                Sub(if (energy != null && stress != null) trf("Stress %1\$s", formatNumber(stress.second)) else null)
            }
        }
        DashCard.HYDRATION -> {
            val today = h.today(Metric.HYDRATION) ?: 0.0
            val goal = HealthRepository.goal(Metric.HYDRATION) ?: 2000.0
            Tile(c, tr("Hydratation"), modifier, onClick = { nav.go("h/metric/HYDRATION") }, bottom = {
                Row(Modifier.fillMaxWidth().clip(CircleShape).background(Mf.colors.card).clickable { vm.log(Metric.HYDRATION, 250.0) }.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(16.dp), tint = c.ink())
                    Text("250 ml", style = MaterialTheme.typography.labelLarge, maxLines = 1, color = c.ink().onContainer())
                }
            }) {
                Value(formatNumber(today), "ml", color = c.ink().onContainer())
                Sub(trf("Objectif %1\$s ml", formatNumber(goal)))
                Spacer(Modifier.height(8.dp))
                ProgressBar((today / goal).toFloat(), c.ink(), height = 6.dp, track = softTrack())
            }
        }
        DashCard.VITALS -> {
            // Les mesures choisies dont une source existe (montre ou Health Connect) ; touche l'en-tête pour les choisir.
            val tiles = VITALS.filter { Bridge.hasSource(it) }
            if (size != app.superhealth.CardSize.L) { VitalsCompact(c, size, tiles, { h.latest(it)?.second }, modifier, nav); return }
            Tile(c, tr("Constantes"), modifier, onClick = { nav.go("h/vitals") }, tinted = false) {
                if (tiles.isEmpty()) Text(tr("Vos constantes apparaîtront après une nuit avec la montre."), style = MaterialTheme.typography.bodyMedium,
                    color = Mf.colors.textSecondary)
                val female = vm.nutrition.profile.collectAsState().value.sex.name == "FEMALE"
                VitalsGrid(tiles, { h.series[it].orEmpty() }, female) { m -> nav.go("h/metric/${m.name}") }
            }
        }
        DashCard.ACTIVITIES -> {
            val ctx = LocalContext.current
            Tile(c, tr("Dernières activités"), modifier, onClick = { nav.go("h/activities") }, padding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp)) {
                if (h.activities.isEmpty()) Text(tr("Aucune activité pour l'instant."), style = MaterialTheme.typography.bodyMedium,
                    color = Mf.colors.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
                h.activities.take(4).forEach { act -> ActivityRow(act, horizontal = 0.dp, chip = softTrack()) { openActivity(ctx, nav, act) } }
            }
        }
        DashCard.TRAINING_WEEK -> {
            val st by vm.training.state.collectAsState()
            val weekStart = TrainingAnalytics.weekStart(LocalDate.now())
            val thisWeek = st.history.filter { !localDate(it.startTime).isBefore(weekStart) }
            Tile(c, tr("Semaine d'entraînement"), modifier, onClick = { nav.go("t/insights") }) {
                Row {
                    StatCell("${thisWeek.size}", tr("Séances"), Modifier.weight(1f))
                    StatCell("${thisWeek.sumOf { w -> w.exercises.sumOf { TrainingAnalytics.workingSets(it).size } }}", tr("Séries"), Modifier.weight(1f))
                    StatCell(st.settings.volume(thisWeek.sumOf { w -> w.exercises.sumOf { TrainingAnalytics.volume(it.sets) } }), tr("Volume"), Modifier.weight(1.3f))
                }
                Spacer(Modifier.height(14.dp))
                WeekHabit(st.history)
            }
        }
        DashCard.MUSCLES -> {
            val st by vm.training.state.collectAsState()
            val db = vm.training.db.collectAsState().value ?: return
            val weekStart = TrainingAnalytics.weekStart(LocalDate.now())
            val sets = TrainingAnalytics.setsByMuscle(st.history.filter { !localDate(it.startTime).isBefore(weekStart) }, db)
            // petit et moyen : la silhouette seule, sans texte
            if (size != app.superhealth.CardSize.L) {
                Box(modifier.then(if (size == app.superhealth.CardSize.S) Modifier.square() else Modifier.height(190.dp)).clip(RoundedCornerShape(CardRadius))
                    .dashCardBackground().cardClick { nav.go("levels") }.padding(8.dp), contentAlignment = Alignment.Center) {
                    BodyMap(intensity(sets, TrainingAnalytics.SETS_HIGH), st.settings.sex, Modifier.fillMaxSize())
                }
                return
            }
            Tile(c, tr("Muscles travaillés cette semaine"), modifier, onClick = { nav.go("levels") }) {
                BodyMap(intensity(sets, TrainingAnalytics.SETS_HIGH), st.settings.sex, Modifier.fillMaxWidth().height(220.dp))
            }
        }
        DashCard.RECORDS -> {
            val st by vm.training.state.collectAsState()
            val db = vm.training.db.collectAsState().value ?: return
            val records = remember(st.history) {
                val sorted = st.history.sortedBy { it.startTime }
                sorted.flatMapIndexed { i, w -> TrainingAnalytics.newRecords(w, sorted.take(i), db).map { w to it } }.takeLast(6).reversed()
            }
            Tile(c, tr("Records récents"), modifier, onClick = null, padding = PaddingValues(14.dp)) {
                if (records.isEmpty()) Text(tr("Vos records apparaîtront après vos premières séances."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
                else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    records.forEach { (w, r) -> RecordCard(db, st.settings, r, app.superhealth.training.ui.shortDate(w.startTime)) { nav.go("exercise/${r.exerciseId}") } }
                }
            }
        }
    }
}

/**
 * Fréquence cardiaque, en tuile compacte : dernière valeur, FC au repos et petite courbe du jour (montre, par tranches de 10 min).
 * « Direct » (pastille en haut) : la montre envoie ses mesures en temps réel tant que la carte est à l'écran.
 * La courbe détaillée est sur l'écran de la mesure.
 */
@Composable
private fun HeartCard(c: DashCard, h: HealthSnap, nav: NavHostController, modifier: Modifier) {
    val hue = c.ink()
    var live by remember { mutableStateOf(false) }
    val livePts = remember { mutableStateListOf<Pair<Long, Double>>() }
    val canLive = remember(h) { LiveHeart.available() }
    if (live) LaunchedEffect(Unit) {
        LiveHeart.samples.collect { s -> s.bpm?.let { livePts += s.timeMs to it.toDouble() } }
    }
    val day = h.heartToday.map { it.timestampMs to it.value }
    val rest = h.latest(Metric.RESTING_HR)?.second
    val now = livePts.lastOrNull()?.first ?: System.currentTimeMillis()
    // en direct : médiane des 10 dernières secondes, qui écarte un battement aberrant sans retarder la valeur (lissage de NOOP)
    val current = livePts.filter { it.first >= now - 10_000 }.map { it.second }.sorted().let { w -> w.getOrNull(w.size / 2) }
        ?: day.lastOrNull()?.second
    Tile(c, tr("Cœur"), modifier, onClick = { nav.go("h/metric/HEART_RATE") }, bottom = {
        when {
            live -> {
                val from = maxOf(now - 10 * 60_000L, (livePts.firstOrNull()?.first ?: now) - 20_000L)
                HeartChart(livePts.filter { it.first >= from }, hue, height = 40.dp, from = from, to = maxOf(now, from + 60_000L), gapMs = 60_000L,
                    live = true, showAxis = false)
            }
            day.size >= 2 -> {
                val start = HealthRepository.startMs(LocalDate.now())
                HeartChart(day, hue, height = 40.dp, from = start, to = start + 86_400_000L, reference = rest, showAxis = false)
            }
            else -> {
                val pts = h.series[Metric.RESTING_HR].orEmpty().mapNotNull { (d, v) -> v?.let { d.toEpochDay() to it } }
                if (pts.size >= 2) LineChart(listOf(Series(pts, hue)), height = 40.dp, showAxis = false)
            }
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Value(current?.let { formatNumber(it) } ?: h.latest(Metric.HEART_RATE)?.second?.let { formatNumber(it) } ?: "—", tr("bpm"),
                color = hue.onContainer())
            Spacer(Modifier.weight(1f))
            if (canLive) Box(Modifier.size(30.dp).clip(CircleShape).background(if (live) hue else softTrack())
                .clickable { live = !live; if (!live) livePts.clear() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Sensors, tr("Direct"), Modifier.size(16.dp), tint = if (live) Color.White else hue.onContainer())
            }
        }
        Sub(when {
            live -> tr("En direct de la montre")
            rest != null -> trf("Repos %1\$s", formatNumber(rest))
            day.isNotEmpty() -> trf("Mesuré à %1\$s", timeOf(day.last().first))
            else -> null
        })
    }
}

/** Macro restante : pastille teintée de la couleur MacroFactor de la macro. */
@Composable
private fun MacroPill(label: String, value: Double, target: Double, color: Color) {
    val left = target - value
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.container(1.4f)).padding(horizontal = 10.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = color.onContainer(), modifier = Modifier.weight(1f), maxLines = 1)
            Text(trf(if (left >= 0) "%1\$s g restants" else "%1\$s g en trop", abs(left).roundToInt()), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = color.onContainer(), maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        ProgressBar(if (target > 0) (value / target).toFloat() else 0f, color, height = 4.dp, track = softTrack())
    }
}

@Composable
private fun MacroBar(label: String, value: Double, target: Double, color: Color) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${value.roundToInt()} / ${target.roundToInt()} g", style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        ProgressBar(if (target > 0) (value / target).toFloat() else 0f, color, height = 5.dp, track = softTrack())
    }
}

