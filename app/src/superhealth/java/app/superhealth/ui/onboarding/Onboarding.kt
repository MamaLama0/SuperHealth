package app.superhealth.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import app.superhealth.SuperPrefs
import app.superhealth.bridge.HealthConnectHub
import app.superhealth.bridge.Watch
import app.superhealth.training.ui.GymTemplate
import app.superhealth.ui.SuperViewModel
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.PermissionsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.discovery.DiscoveryActivityV2
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Warehouse
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Garage
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import app.superhealth.nutrition.data.Repository.Companion.computeAnalytics
import app.superhealth.nutrition.data.WeighInEntity
import app.superhealth.nutrition.domain.Activity
import app.superhealth.nutrition.domain.Athlete
import app.superhealth.nutrition.domain.CalorieFloor
import app.superhealth.nutrition.domain.DietPreference
import app.superhealth.nutrition.domain.ExerciseLevel
import app.superhealth.nutrition.domain.Goal
import app.superhealth.nutrition.domain.GoalType
import app.superhealth.nutrition.domain.Language
import app.superhealth.nutrition.domain.LifetimeWeight
import app.superhealth.nutrition.domain.ProteinPreference
import app.superhealth.nutrition.domain.Sex
import app.superhealth.nutrition.domain.Targets
import app.superhealth.nutrition.domain.Training
import app.superhealth.nutrition.domain.WeightChange
import app.superhealth.nutrition.domain.WeightUnit
import app.superhealth.nutrition.ui.KG_PER_LB
import app.superhealth.ui.Lang
import app.superhealth.nutrition.ui.components.AssetImage
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.energy
import app.superhealth.nutrition.ui.num
import app.superhealth.nutrition.ui.shortDate
import app.superhealth.ui.theme.CarbsColor
import app.superhealth.ui.theme.FatColor
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.ProteinColor
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.nutrition.ui.weight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Questionnaire d'accueil de SuperHealth : un seul parcours pour les trois modules.
 * - Vous : prénom, sexe, naissance, taille, poids, silhouette, activité, historique (questions de MacroFactor) ;
 * - Objectif : type, poids cible, durée (le rythme en découle, comme dans MacroFactor), protéines, diète, plancher, répartition ;
 * - Entraînement : type d'entraînement, puis expérience et salle si musculation (questions de MacroFactor Workouts) ;
 * - Connexions : montre (module LocalHealth, désactivable) et Health Connect ;
 * puis génération animée du programme nutrition et, au choix, du programme de musculation.
 */
private enum class Step(val section: Int) {
    WELCOME(0), NAME(0), SEX(0), BIRTH(0), HEIGHT(0), WEIGHT(0), BODY_FAT(0), ACTIVITY(0), HISTORY(0),
    GOAL(1), TARGET(1), PACE(1), PROTEIN(1), DIET(1), FLOOR(1), DISTRIBUTION(1),
    TRAINING(2), EXPERIENCE(2), GYM(2), PROGRAM(2), LIFT_GOAL(2), DAYS(2), DURATION(2),
    WATCH(3), HEALTH(3),
    GENERATING(4), RESULT(4),
}

private val SECTIONS = listOf("Vous", "Objectif", "Entraînement", "Connexions")

/** Choix pour la montre : l'associer maintenant, plus tard, ou désactiver le module LocalHealth. */
private enum class WatchChoice(val label: String, val description: String) {
    PAIR("Oui, l'associer maintenant", "Recherche Bluetooth de votre montre ou bracelet"),
    LATER("Oui, plus tard", "Le module montre reste actif, à associer dans Réglages"),
    NONE("Non, pas de montre", "Le module montre est désactivé (réactivable dans Réglages)"),
}

/** Illustration spatiale (thème « Lune », tools/moon_recolor.sh) associée à chaque étape. */
private fun Step.image() = "illustrations/illustration_" + when (this) {
    Step.WELCOME -> 1; Step.NAME -> 6; Step.SEX -> 7; Step.BIRTH -> 10; Step.HEIGHT -> 14; Step.WEIGHT -> 23; Step.BODY_FAT -> 15
    Step.ACTIVITY -> 4; Step.HISTORY -> 5; Step.HEALTH -> 24; Step.GOAL -> 17; Step.TARGET -> 22; Step.PACE -> 26; Step.TRAINING -> 9
    Step.PROTEIN -> 16; Step.DIET -> 3; Step.FLOOR -> 13; Step.DISTRIBUTION -> 25; Step.GENERATING -> 28; Step.RESULT -> 2
    Step.EXPERIENCE -> 11; Step.GYM -> 12; Step.WATCH -> 19
    Step.PROGRAM -> 8; Step.LIFT_GOAL -> 18; Step.DAYS -> 20; Step.DURATION -> 21
} + ".webp"


/**
 * [FULL] : questionnaire d'accueil (enregistré). Les simulations rejouent une partie du questionnaire sans rien enregistrer,
 * par exemple pour calculer les calories ou le programme d'un ami.
 */
enum class OnboardingMode { FULL, NUTRITION_SIM, TRAINING_SIM }

@Composable
fun OnboardingScreen(svm: SuperViewModel, mode: OnboardingMode = OnboardingMode.FULL, onClose: () -> Unit = {}) {
    val vm = svm.nutrition
    val ctx = LocalContext.current
    val sim = mode != OnboardingMode.FULL
    // Simulation : valeurs neutres, pas celles de l'utilisateur.
    val profile = if (sim) remember { app.superhealth.nutrition.domain.Profile() } else vm.profile.collectAsState().value
    val program = if (sim) remember { app.superhealth.nutrition.domain.Program() } else vm.program.collectAsState().value
    val settings by vm.settings.collectAsState()
    /** Questionnaire relancé depuis les Réglages : l'utilisateur a déjà des données. */
    val rerun = mode == OnboardingMode.FULL && settings.onboarded

    var unit by rememberSaveable { mutableStateOf(settings.weightUnit) }
    var sex by rememberSaveable { mutableStateOf(profile.sex) }
    var birthYear by rememberSaveable { mutableIntStateOf(profile.birthYear) }
    var heightCm by rememberSaveable { mutableIntStateOf(profile.heightCm.roundToInt()) }
    var weightKg by rememberSaveable { mutableDoubleStateOf(if (profile.sex == Sex.FEMALE) 65.0 else 80.0) }
    // questionnaire relancé : la molette part de la dernière pesée plutôt que d'une valeur par défaut
    var weightLoaded by rememberSaveable { mutableStateOf(sim) }
    if (!weightLoaded) LaunchedEffect(Unit) {
        vm.repo.weighIns.all().lastOrNull()?.let { weightKg = it.weightKg }
        weightLoaded = true
    }
    var bodyFat by rememberSaveable { mutableStateOf<Int?>(null) }
    var activity by rememberSaveable { mutableStateOf(profile.activity) }
    var exercise by rememberSaveable { mutableStateOf(profile.exercise) }
    var athlete by rememberSaveable { mutableStateOf(profile.athlete) }
    var change by rememberSaveable { mutableStateOf(profile.weightChange) }
    var lifetime by rememberSaveable { mutableStateOf(profile.lifetimeWeight) }
    // questionnaire relancé : objectif et cible du programme en cours comme point de départ
    var goal by rememberSaveable { mutableStateOf(if (rerun) program.goalType else GoalType.CUT) }
    var targetKg by rememberSaveable { mutableDoubleStateOf(if (rerun) program.targetWeightKg ?: 0.0 else 0.0) }
    var rate by rememberSaveable { mutableDoubleStateOf(0.5) }
    var weeks by rememberSaveable { mutableIntStateOf(10) }
    var training by rememberSaveable { mutableStateOf(if (mode == OnboardingMode.TRAINING_SIM) Training.STRENGTH else program.training) }
    var protein by rememberSaveable { mutableStateOf(program.proteinPreference) }
    var diet by rememberSaveable { mutableStateOf(program.dietPreference) }
    var floor by rememberSaveable { mutableStateOf(program.calorieFloor) }
    var shift by rememberSaveable { mutableStateOf(false) }
    var highDays by rememberSaveable { mutableStateOf(setOf(6, 7)) }
    var health by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(if (sim) "" else app.superhealth.ui.userName() ?: "") }
    var experience by rememberSaveable { mutableStateOf(app.superhealth.training.domain.Experience.INTERMEDIATE) }
    var gym by rememberSaveable { mutableStateOf(GymTemplate.COMMERCIAL) }
    // Programme de musculation généré à la fin du questionnaire (questions essentielles de l'assistant de La Salle).
    var wantProgram by rememberSaveable { mutableStateOf(true) }
    var liftGoal by rememberSaveable { mutableStateOf(app.superhealth.training.domain.Goal.HYPERTROPHY) }
    var liftDays by rememberSaveable { mutableIntStateOf(3) }
    var liftMinutes by rememberSaveable { mutableIntStateOf(50) }
    var watch by rememberSaveable { mutableStateOf(WatchChoice.LATER) }
    var paired by rememberSaveable { mutableStateOf(GBApplication.app().deviceManager.devices.isNotEmpty()) }
    val lifts = training == Training.STRENGTH || training == Training.BOTH

    val healthAvailable = remember { HealthConnectHub.status(ctx) == HealthConnectHub.Status.AVAILABLE }
    val steps = Step.entries.filter {
        when (mode) {
            OnboardingMode.NUTRITION_SIM -> if (it.section == 2 && it != Step.TRAINING || it.section == 3 || it == Step.WELCOME || it == Step.NAME) return@filter false
            OnboardingMode.TRAINING_SIM -> if (it !in setOf(Step.SEX, Step.EXPERIENCE, Step.GYM, Step.LIFT_GOAL, Step.DAYS, Step.DURATION, Step.GENERATING, Step.RESULT)) return@filter false
            OnboardingMode.FULL -> {}
        }
        when (it) {
            Step.HEALTH -> healthAvailable
            Step.TARGET, Step.PACE -> goal != GoalType.MAINTENANCE
            Step.EXPERIENCE, Step.GYM, Step.PROGRAM -> lifts
            Step.LIFT_GOAL, Step.DAYS, Step.DURATION -> lifts && wantProgram
            else -> true
        }
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    var forward by rememberSaveable { mutableStateOf(true) }
    fun next() { forward = true; if (index < steps.lastIndex) index++ }
    fun back() { forward = false; if (index > 0) index-- }
    BackHandler(step != Step.GENERATING && (index > 0 || sim)) { if (index > 0) back() else onClose() }

    /**
     * « Passer » : relancé depuis les Réglages, on garde tout tel quel ; premier lancement, l'app démarre avec des réglages
     * neutres (maintien du poids, pas de pesée inventée) à compléter plus tard.
     */
    fun skip() {
        if (rerun) { SuperPrefs.update { it.copy(onboarded = true) }; return }
        val t = svm.training
        val myGym = t.gymFromTemplate(GymTemplate.COMMERCIAL, tr("Ma salle"))
        t.saveGym(myGym)
        t.settings { it.copy(onboarded = true, activeGymId = myGym.id) }
        vm.repo.prefs.setProgram(program.copy(goalType = GoalType.MAINTENANCE, ratePctPerWeek = 0.0, startEpochDay = LocalDate.now().toEpochDay()))
        vm.repo.prefs.setGoals(listOf(Goal(startDay = LocalDate.now().toEpochDay(), type = GoalType.MAINTENANCE, startWeightKg = weightKg,
            targetWeightKg = null, ratePctPerWeek = 0.0)))
        vm.repo.prefs.setSettings(settings.copy(onboarded = true, language = if (Lang.english) Language.EN else Language.FR))
        SuperPrefs.update { it.copy(onboarded = true) }
    }

    // Profil, programme et objectif tels que saisis : utilisés pour l'aperçu du rythme et pour le résultat.
    val today = LocalDate.now().toEpochDay()
    fun targetOk() = (goal == GoalType.CUT && targetKg < weightKg - 0.05) || (goal == GoalType.BULK && targetKg > weightKg + 0.05)
    /**
     * Durée → rythme en % du poids par semaine (unité des programmes de MacroFactor) : écart / semaines / poids actuel.
     * Les durées proposées gardent le rythme entre 0,1 % et le maximum de MacroFactor (1 % en perte, 0,5 % en prise).
     */
    fun rateFor(w: Int) = abs(targetKg - weightKg) / w / weightKg * 100
    /** Cible par défaut (5 kg dans le sens de l'objectif), gardée si elle va déjà dans le bon sens. */
    fun initTarget() {
        if (goal != GoalType.MAINTENANCE && !targetOk()) targetKg = (weightKg + if (goal == GoalType.CUT) -5 else 5).coerceIn(35.0, 250.0)
    }
    fun paceWeeks(): List<Int> {
        val delta = abs(targetKg - weightKg).coerceAtLeast(0.1)
        val max = if (goal == GoalType.BULK) 0.5 else 1.0
        val shortest = kotlin.math.ceil(delta / (weightKg * max / 100)).toInt().coerceAtLeast(1)
        val longest = (delta / (weightKg * 0.1 / 100)).toInt().coerceIn(shortest, 156)
        return (shortest..longest).toList()
    }
    /** Durée par défaut : celle du rythme recommandé de MacroFactor (0,5 % en perte, 0,25 % en prise), puis le rythme exact qui en découle. */
    fun initPace() {
        val options = paceWeeks()
        val pref = abs(targetKg - weightKg) / (weightKg * (if (goal == GoalType.BULK) 0.25 else 0.5) / 100)
        weeks = pref.roundToInt().coerceIn(options.first(), options.last())
        rate = rateFor(weeks)
    }
    fun draftProfile() = profile.copy(sex = sex, birthYear = birthYear, heightCm = heightCm.toDouble(), activity = activity,
        bodyFatPct = bodyFat?.toDouble(), weightChange = change, lifetimeWeight = lifetime, exercise = exercise, athlete = athlete)
    fun draftTarget() = when {
        goal == GoalType.MAINTENANCE -> weightKg
        targetKg > 0 -> targetKg
        else -> null
    }
    fun draftProgram() = program.copy(goalType = goal, ratePctPerWeek = if (goal == GoalType.MAINTENANCE) 0.0 else rate,
        targetWeightKg = draftTarget(), startEpochDay = today, training = training, dietPreference = diet, proteinPreference = protein,
        calorieFloor = floor, highDays = if (shift) highDays else emptySet())
    fun draftGoal() = Goal(startDay = today, type = goal, startWeightKg = weightKg, targetWeightKg = draftTarget(),
        ratePctPerWeek = if (goal == GoalType.MAINTENANCE) 0.0 else rate)
    fun draftCheckIn(): Pair<Double, app.superhealth.nutrition.domain.CheckIn> {
        val w = listOf(WeighInEntity(today, weightKg, bodyFat?.toDouble()))
        val p = draftProgram(); val g = draftGoal()
        val an = computeAnalytics(emptyList(), w, emptyList(), draftProfile(), p, settings.copy(weightUnit = unit), listOf(g), emptyList(), today)
        return an.initialTdee to Targets.checkIn(today, an.initialTdee, weightKg, an.lbmAt(today), p, g, heightCm.toDouble())
    }

    /** Programme de musculation proposé (aperçu sur l'écran final, activé à la validation). */
    fun draftLiftProgram(): app.superhealth.training.domain.Program? {
        if (!lifts || !wantProgram) return null
        val t = svm.training
        val equipment = t.gymFromTemplate(gym).equipmentIds
        return t.generate(app.superhealth.training.domain.Generator.Input(
            goal = liftGoal, daysPerWeek = liftDays, split = app.superhealth.training.domain.Split.recommended(liftDays), minutes = liftMinutes,
            periodization = app.superhealth.training.domain.Periodization.LINEAR, deload = app.superhealth.training.domain.Deload.LAST,
            experience = experience, equipment = equipment, name = tr("Mon programme"),
        ))
    }

    fun finish(program: Boolean) {
        val s = settings.copy(weightUnit = unit, healthConnect = health,
            language = if (Lang.english) Language.EN else Language.FR)
        val p = draftProgram(); val g = draftGoal()
        // Prénom : profil Gadgetbridge (envoyé aux montres qui l'affichent).
        GBApplication.getPrefs().preferences.edit().putString(ActivityUser.PREF_USER_NAME, name.trim()).apply()
        vm.repo.prefs.setProfile(draftProfile())
        vm.repo.prefs.setProgram(p)
        vm.repo.prefs.setGoals(listOf(g))
        val ci = draftCheckIn().second
        // Module entraînement : expérience et salle (le sexe, les unités et le poids lui sont recopiés par le pont).
        val t = svm.training
        val myGym = t.gymFromTemplate(gym, tr("Ma salle"))
        t.saveGym(myGym)
        t.settings { it.copy(onboarded = true, experience = experience, activeGymId = myGym.id) }
        // Programme généré avec l'équipement de la salle créée, activé directement.
        if (program) {
            val p = draftLiftProgram()
            if (p != null) t.saveProgram(p.copy(gymId = myGym.id), activate = true)
            else svm.pendingRoute.value = "training:wizard" // base d'exercices pas encore prête : l'assistant prend le relais
        }
        Watch.setEnabled(watch != WatchChoice.NONE)
        SuperPrefs.update { it.copy(onboarded = true, healthConnect = health) }
        vm.launch {
            weighIns.upsert(WeighInEntity(today, weightKg, bodyFat?.toDouble()))
            saveCheckIn(ci)
            prefs.setSettings(s.copy(onboarded = true))
        }
        if (health) svm.syncNow(ctx)
    }

    // Montre : autorisations Bluetooth de Gadgetbridge, puis recherche et association.
    val pairWatch = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        paired = GBApplication.app().deviceManager.devices.isNotEmpty()
    }
    val askWatchPermissions = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pairWatch.launch(Intent(ctx, DiscoveryActivityV2::class.java))
    }

    val askHealth = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { g ->
        health = g.isNotEmpty(); if (health) next()
    }

    Column(Modifier.fillMaxSize().background(Mf.colors.background).safeDrawingPadding().imePadding()) {
        // Barre de progression (masquée sur l'accueil et la génération)
        val showBar = step != Step.WELCOME && step != Step.GENERATING && step != Step.RESULT
        if (sim && !showBar) Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, tr("Fermer")) }
            Text(tr("Simulation · rien n'est enregistré"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
        }
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBar) {
                IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour")) }
                // Une barre par partie du questionnaire, remplie au fil des étapes de cette partie.
                Row(Modifier.weight(1f).padding(end = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SECTIONS.indices.forEach { sec ->
                        val inSec = steps.filter { it.section == sec && it != Step.WELCOME }
                        val pos = inSec.indexOf(step)
                        val target = when {
                            step.section > sec -> 1f
                            step.section < sec || pos < 0 -> 0f
                            else -> (pos + 1f) / inSec.size
                        }
                        val frac by animateFloatAsState(target, tween(500, easing = FastOutSlowInEasing), label = "progress")
                        Column(Modifier.weight(1f)) {
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Mf.colors.chip)) {
                                Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(Mf.colors.accent))
                            }
                            Text(tr(SECTIONS[sec]), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = if (step.section == sec) Mf.colors.text else Mf.colors.textTertiary, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            } else if (step == Step.WELCOME) {
                androidx.compose.material3.TextButton(onClick = { skip() }) { Text(tr("Passer"), color = Mf.colors.textSecondary) }
                Spacer(Modifier.weight(1f))
                ChipChoice(listOf(Language.FR, Language.EN), if (Lang.english) Language.EN else Language.FR, { if (it == Language.FR) "FR" else "EN" }) { l ->
                    vm.repo.prefs.setSettings(settings.copy(language = l)); Lang.apply(l.name)
                }
                Spacer(Modifier.width(12.dp))
            }
        }
        AnimatedContent(step, Modifier.weight(1f), label = "step", transitionSpec = {
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { dir * it / 3 } + fadeIn(tween(300))) togetherWith
                (slideOutHorizontally(tween(300)) { -dir * it / 4 } + fadeOut(tween(200)))
        }) { st ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                when (st) {
                    Step.WELCOME -> Welcome()
                    Step.NAME -> Page(st, tr("Comment vous appelez-vous ?"), tr("Votre prénom, pour vous saluer et l'afficher sur votre montre.")) {
                        OutlinedTextField(name, { name = it.take(24) }, Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text(tr("Prénom"), color = Mf.colors.textTertiary) },
                            leadingIcon = { Icon(Icons.Rounded.Person, null, tint = Mf.colors.textSecondary) },
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Mf.colors.card, unfocusedContainerColor = Mf.colors.card,
                                focusedBorderColor = Mf.colors.accent, unfocusedBorderColor = Color.Transparent))
                    }
                    Step.SEX -> Page(st, tr("Quel est votre sexe ?"), tr("Il sert à estimer votre métabolisme de base.")) {
                        Options(listOf(Sex.MALE, Sex.FEMALE), sex, { if (it == Sex.MALE) tr("Homme") else tr("Femme") }) { sex = it }
                    }
                    Step.BIRTH -> Page(st, tr("Quelle est votre année de naissance ?"), null) {
                        val years = (LocalDate.now().year - 13 downTo 1930).toList()
                        Wheel(years.map { it.toString() }, years.indexOf(birthYear).coerceAtLeast(0)) { birthYear = years[it] }
                    }
                    Step.HEIGHT -> Page(st, tr("Quelle est votre taille ?"), null) {
                        val hs = (120..220).toList()
                        Wheel(hs.map { trf("%1\$s cm", it) }, hs.indexOf(heightCm).coerceAtLeast(0)) { heightCm = hs[it] }
                    }
                    Step.WEIGHT -> Page(st, tr("Quel est votre poids actuel ?"), tr("Le matin, à jeun, c'est l'idéal.")) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ChipChoice(WeightUnit.entries, unit, { it.label.tr() }) { unit = it }
                        }
                        Spacer(Modifier.height(8.dp))
                        WeightWheel(weightKg, unit) { weightKg = it }
                    }
                    Step.BODY_FAT -> Page(st, tr("Quelle silhouette vous ressemble le plus ?"), tr("Facultatif : une estimation suffit.")) {
                        BodyFatPicker(sex, bodyFat?.toDouble()) { bodyFat = if (bodyFat == it) null else it }
                        Spacer(Modifier.height(8.dp))
                        Text(bodyFat?.let { trf("Masse grasse : %1\$d %%", it) } ?: trf("Sans réponse : %1\$d %% par défaut", if (sex == Sex.MALE) 25 else 35),
                            style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                    Step.ACTIVITY -> Page(st, tr("Quel est votre niveau d'activité ?"), tr("En dehors du sport : travail, marche, quotidien.")) {
                        Options(Activity.entries, activity, { it.label.tr() }, { when (it) {
                            Activity.MOSTLY_SEDENTARY -> tr("Bureau, peu de marche")
                            Activity.MODERATELY_ACTIVE -> tr("Debout ou en mouvement une partie de la journée")
                            Activity.VERY_ACTIVE -> tr("Métier physique, beaucoup de marche")
                        } }) { activity = it }
                        SmallLabel(tr("Séances de sport par semaine"))
                        ChipChoice(ExerciseLevel.entries, exercise, { it.label.tr() }) { exercise = it }
                        SmallLabel(tr("Vous êtes…"))
                        ChipChoice(Athlete.entries, athlete, { it.label.tr() }) { athlete = it }
                    }
                    Step.HISTORY -> Page(st, tr("Comment évolue votre poids ?"), tr("Ces dernières semaines.")) {
                        Options(listOf(WeightChange.LOSING, WeightChange.MAINTAINING, WeightChange.GAINING, WeightChange.UNKNOWN), change, { it.label.tr() },
                            icon = { when (it) {
                                WeightChange.LOSING -> Icons.AutoMirrored.Rounded.TrendingDown
                                WeightChange.GAINING -> Icons.AutoMirrored.Rounded.TrendingUp
                                else -> Icons.AutoMirrored.Rounded.TrendingFlat
                            } }) { change = it }
                        SmallLabel(tr("Par le passé"))
                        ChipChoice(LifetimeWeight.entries, lifetime, { it.label.tr() }) { lifetime = it }
                    }
                    Step.HEALTH -> Page(st, tr("Connecter Health Connect ?"), tr("Échange avec vos autres apps santé, sans quitter le téléphone.")) {
                        Feature(Icons.AutoMirrored.Rounded.DirectionsRun, tr("Vos pas affinent la dépense"))
                        Feature(Icons.Rounded.MonitorWeight, tr("Pesées d'une balance connectée"))
                        Feature(Icons.Rounded.Favorite, tr("Fréquence cardiaque, sommeil et activités des autres apps"))
                        Feature(Icons.Rounded.Restaurant, tr("Repas, pesées et séances partagés avec vos autres apps"))
                        Spacer(Modifier.height(16.dp))
                        PillButton(tr(if (health) tr("Connecté") else tr("Connecter")), Modifier.fillMaxWidth(), icon = if (health) Icons.Rounded.CheckCircle else null) {
                            if (!health) when (HealthConnectHub.status(ctx)) {
                                // Sans Health Connect (Android 13 et moins sans l'app), la demande plantait l'app.
                                HealthConnectHub.Status.AVAILABLE -> runCatching { askHealth.launch(HealthConnectHub.PERMISSIONS) }
                                HealthConnectHub.Status.NEEDS_UPDATE -> runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata")))
                                }
                                HealthConnectHub.Status.UNAVAILABLE -> android.widget.Toast.makeText(ctx, tr("Health Connect n'est pas disponible sur ce téléphone"),
                                    android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    Step.GOAL -> Page(st, tr("Quel est votre objectif ?"), null) {
                        Options(GoalType.entries, goal, { when (it) {
                            GoalType.CUT -> tr("Perdre du poids"); GoalType.MAINTENANCE -> tr("Maintenir mon poids"); GoalType.BULK -> tr("Prendre du poids")
                        } }, icon = { when (it) {
                            GoalType.CUT -> Icons.AutoMirrored.Rounded.TrendingDown
                            GoalType.MAINTENANCE -> Icons.AutoMirrored.Rounded.TrendingFlat
                            GoalType.BULK -> Icons.AutoMirrored.Rounded.TrendingUp
                        } }) { g -> if (g != goal) { goal = g; targetKg = 0.0 } }
                    }
                    Step.TARGET -> Page(st, tr("Quel poids visez-vous ?"),
                        trf("Poids actuel : %1\$s", settings.copy(weightUnit = unit).weight(weightKg, 1))) {
                        WeightWheel(targetKg, unit) { targetKg = it }
                        val delta = targetKg - weightKg
                        Text((if (delta > 0) "+" else if (delta < 0) "−" else "") + settings.copy(weightUnit = unit).weight(abs(delta), 1),
                            style = MaterialTheme.typography.titleMedium, color = Mf.colors.textSecondary, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            textAlign = TextAlign.Center)
                        if (!targetOk()) Text(if (goal == GoalType.CUT) tr("Choisissez un poids plus bas que le poids actuel.") else tr("Choisissez un poids plus haut que le poids actuel."),
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            textAlign = TextAlign.Center)
                    }
                    Step.PACE -> Page(st, tr("En combien de temps ?"), tr("Le rythme et votre budget calorique en découlent.")) {
                        val options = paceWeeks()
                        Wheel(options.map { w -> trf("%1\$s sem. · %2\$s", w, shortDate(today + w * 7L)) }, options.indexOf(weeks).coerceAtLeast(0), height = 200) {
                            weeks = options[it]; rate = rateFor(weeks)
                        }
                        val kgWeek = weightKg * rate / 100
                        val (_, ci) = remember(rate, targetKg, goal) { draftCheckIn() }
                        val max = if (goal == GoalType.BULK) 0.5 else 1.0
                        Spacer(Modifier.height(12.dp))
                        Text(trf("%1\$s %% du poids / semaine", num(rate, 2)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Text(tr(when { rate <= max * 0.3 -> "Doux"; rate <= max * 0.6 -> "Modéré (recommandé)"; else -> "Ambitieux" }),
                            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        RowStat(tr("Par semaine"), settings.copy(weightUnit = unit).weight(kgWeek, 2))
                        RowStat(tr("Date de fin estimée"), shortDate(today + weeks * 7L))
                        RowStat(tr("Budget initial"), settings.energy(ci.kcal))
                    }
                    Step.TRAINING -> Page(st, tr("Quel entraînement pendant ce programme ?"), null) {
                        Options(Training.entries, training, { when (it) {
                            Training.NONE -> tr("Aucun ou activité légère"); Training.CARDIO -> tr("Cardio"); Training.STRENGTH -> tr("Musculation"); Training.BOTH -> tr("Cardio et musculation")
                        } }, icon = { when (it) {
                            Training.NONE -> Icons.Rounded.SelfImprovement; Training.CARDIO -> Icons.AutoMirrored.Rounded.DirectionsRun
                            Training.STRENGTH -> Icons.Rounded.FitnessCenter; Training.BOTH -> Icons.Rounded.Bolt
                        } }) { training = it }
                    }
                    Step.EXPERIENCE -> Page(st, tr("Votre expérience en musculation"), tr("Elle règle la progression et le volume de vos séances.")) {
                        Options(app.superhealth.training.domain.Experience.entries, experience, { when (it) {
                            app.superhealth.training.domain.Experience.BEGINNER -> tr("Débutant")
                            app.superhealth.training.domain.Experience.INTERMEDIATE -> tr("Intermédiaire")
                            app.superhealth.training.domain.Experience.ADVANCED -> tr("Avancé")
                        } }, { when (it) {
                            app.superhealth.training.domain.Experience.BEGINNER -> tr("Moins d'un an d'entraînement régulier")
                            app.superhealth.training.domain.Experience.INTERMEDIATE -> tr("1 à 4 ans")
                            app.superhealth.training.domain.Experience.ADVANCED -> tr("Plus de 4 ans")
                        } }) { experience = it }
                    }
                    Step.GYM -> Page(st, tr("Où vous entraînerez-vous ?"), tr("L'équipement est pré-rempli ; vous pourrez l'ajuster.")) {
                        Options(GymTemplate.entries, gym, { it.label }, icon = { when (it) {
                            GymTemplate.COMMERCIAL -> Icons.Rounded.Apartment; GymTemplate.WAREHOUSE -> Icons.Rounded.Warehouse; GymTemplate.LOCAL -> Icons.Rounded.Store
                            GymTemplate.GARAGE -> Icons.Rounded.Garage; GymTemplate.HOME -> Icons.Rounded.Home; GymTemplate.EVERYTHING -> Icons.Rounded.AllInclusive
                            GymTemplate.BLANK -> Icons.Rounded.CropFree
                        } }) { gym = it }
                    }
                    Step.PROGRAM -> Page(st, tr("Voulez-vous un programme de musculation ?"), tr("Selon votre niveau, votre salle et votre emploi du temps.")) {
                        Options(listOf(true, false), wantProgram, { if (it) tr("Oui, créez-le pour moi") else tr("Non, je m'entraîne à ma façon") },
                            { if (it) tr("Séances, exercices, séries et progression") else tr("Vous pourrez en créer un plus tard dans Activités") },
                            icon = { if (it) Icons.Rounded.AutoFixHigh else Icons.Rounded.FitnessCenter }) { wantProgram = it }
                    }
                    Step.LIFT_GOAL -> Page(st, tr("Quel est votre objectif principal ?"), null) {
                        Options(app.superhealth.training.domain.Goal.entries, liftGoal, { tr(it.label) }, { tr(it.description) }) { liftGoal = it }
                    }
                    Step.DAYS -> Page(st, tr("Combien de séances par semaine ?"), tr("La structure (full body, haut/bas, push/pull/legs…) en découle.")) {
                        Wheel((1..7).map { trf("%1\$s par semaine", it) }, liftDays - 1) { liftDays = it + 1 }
                    }
                    Step.DURATION -> Page(st, tr("Combien de temps par séance ?"), null) {
                        Options(listOf(30, 50, 70, 90), liftMinutes, { when (it) {
                            30 -> tr("20 à 40 minutes"); 50 -> tr("40 à 60 minutes"); 70 -> tr("60 à 80 minutes"); else -> tr("80 à 100 minutes")
                        } }, icon = { Icons.Rounded.Timer }) { liftMinutes = it }
                    }
                    Step.WATCH -> Page(st, tr("Avez-vous une montre connectée ?"), tr("Pas, fréquence cardiaque, sommeil et séances, sans compte ni cloud.")) {
                        Options(WatchChoice.entries, watch, { it.label }, { it.description }, icon = { when (it) {
                            WatchChoice.PAIR -> Icons.Rounded.Bluetooth; WatchChoice.LATER -> Icons.Rounded.Watch; WatchChoice.NONE -> Icons.Rounded.Close
                        } }) { watch = it }
                        if (watch == WatchChoice.PAIR) {
                            Spacer(Modifier.height(12.dp))
                            PillButton(if (paired) tr("Montre associée") else tr("Rechercher ma montre"), Modifier.fillMaxWidth(),
                                icon = if (paired) Icons.Rounded.CheckCircle else Icons.Rounded.Bluetooth) {
                                if (!Watch.enabled) Watch.setEnabled(true)
                                runCatching { askWatchPermissions.launch(Intent(ctx, PermissionsActivity::class.java)) }
                                    .onFailure { runCatching { pairWatch.launch(Intent(ctx, DiscoveryActivityV2::class.java)) } }
                            }
                            Text(tr("Compatible avec les montres prises en charge par Gadgetbridge (Garmin, Amazfit, Xiaomi, Huawei, Pebble…)."),
                                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    Step.PROTEIN -> Page(st, tr("Quelle quantité de protéines préférez-vous ?"), null) {
                        Options(ProteinPreference.entries, protein, { it.label.tr() }, { when (it) {
                            ProteinPreference.LOW -> tr("Bas de la fourchette optimale")
                            ProteinPreference.MODERATE -> tr("Milieu de la fourchette optimale")
                            ProteinPreference.HIGH -> tr("Haut de la fourchette optimale")
                            ProteinPreference.EXTRA -> tr("Apport maximal recommandé")
                        } }) { protein = it }
                    }
                    Step.DIET -> Page(st, tr("Quelle diète préférez-vous ?"), null) {
                        Options(DietPreference.entries, diet, { it.label.tr() }, { it.description.tr() }) { diet = it }
                    }
                    Step.FLOOR -> Page(st, tr("Quel plancher calorique ?"), tr("Vos cibles ne descendront jamais en dessous.")) {
                        Options(CalorieFloor.entries, floor, { it.label.tr() }, { when (it) {
                            CalorieFloor.STANDARD -> tr("Recommandé")
                            CalorieFloor.LOW -> tr("Plus bas, à surveiller")
                            CalorieFloor.NONE -> tr("Aucun (avec prudence)")
                        } }) { floor = it }
                    }
                    Step.DISTRIBUTION -> Page(st, tr("Comment répartir vos calories ?"), tr("Sur la semaine.")) {
                        Options(listOf(false, true), shift, { if (it) tr("Jours plus élevés") else tr("Répartition égale") },
                            { if (it) tr("Plus de calories certains jours") else tr("Même cible chaque jour") }) { shift = it }
                        if (shift) {
                            SmallLabel(tr("Jours plus élevés"))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                (1..7).forEach { d ->
                                    val sel = d in highDays
                                    val bg by animateColorAsState(if (sel) Mf.colors.accent else Mf.colors.card, label = "day")
                                    Box(Modifier.size(42.dp).clip(CircleShape).background(bg).clickable {
                                        highDays = if (sel) highDays - d else highDays + d
                                    }, contentAlignment = Alignment.Center) {
                                        Text(java.time.DayOfWeek.of(d).getDisplayName(java.time.format.TextStyle.NARROW, Lang.locale),
                                            color = if (sel) Color.White else Mf.colors.text, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    Step.GENERATING -> Generating { next() }
                    Step.RESULT -> if (mode == OnboardingMode.TRAINING_SIM) {
                        Spacer(Modifier.height(16.dp))
                        Text(tr("Programme simulé"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        val liftProgram = remember { draftLiftProgram() }
                        if (liftProgram != null) LiftProgramCard(liftProgram, liftMinutes)
                        else Text(tr("La base d'exercices se charge encore : réessayez dans un instant."), color = Mf.colors.textSecondary)
                    } else {
                        val (tdee, ci) = remember { draftCheckIn() }
                        Result(tdee, ci, settings, draftProgram())
                        if (sim) return@Column
                        Spacer(Modifier.height(12.dp))
                        Text(tr("Et aussi"), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        val liftProgram = remember { draftLiftProgram() }
                        if (liftProgram != null) LiftProgramCard(liftProgram, liftMinutes)
                        else if (lifts) Feature(Icons.Rounded.FitnessCenter, trf("Musculation : %1\$s, %2\$s", gym.label.let(::tr).lowercase(Lang.locale), experience.label.let(::tr).lowercase(Lang.locale)))
                        Feature(Icons.Rounded.Watch, when (watch) {
                            WatchChoice.NONE -> tr("Module montre désactivé")
                            else -> if (paired) tr("Montre associée") else tr("Montre à associer dans Réglages")
                        })
                        Feature(Icons.Rounded.Favorite, if (health) tr("Health Connect connecté") else tr("Health Connect : à connecter dans Réglages"))
                    }
                }
            }
        }
        if (step != Step.GENERATING) {
            val label = when (step) {
                Step.WELCOME -> tr("Commencer")
                Step.RESULT -> if (sim) tr("Nouvelle simulation") else tr("C'est parti")
                Step.HEALTH -> if (health) tr("Continuer") else tr("Plus tard")
                Step.NAME -> if (name.isBlank()) tr("Passer") else tr("Continuer")
                Step.WATCH -> if (watch == WatchChoice.PAIR && !paired) tr("Plus tard") else tr("Continuer")
                Step.BODY_FAT -> if (bodyFat == null) tr("Passer") else tr("Continuer")
                else -> tr("Continuer")
            }
            val ok = step != Step.TARGET || targetOk()
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                PillButton(tr(label), Modifier.fillMaxWidth(), enabled = ok) {
                    when {
                        step == Step.RESULT && sim -> { forward = false; index = 0 }
                        step == Step.RESULT -> finish(program = lifts && wantProgram)
                        else -> {
                            if (step == Step.GOAL) initTarget()
                            if (step == Step.TARGET) initPace()
                            next()
                        }
                    }
                }
                if (sim && step == Step.RESULT) {
                    if (mode == OnboardingMode.TRAINING_SIM) PillButton(tr("Garder ce programme dans la bibliothèque"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false) {
                        draftLiftProgram()?.let { svm.training.saveProgram(it.copy(name = tr("Programme simulé"))) }
                        android.widget.Toast.makeText(ctx, tr("Ajouté à la bibliothèque"), android.widget.Toast.LENGTH_SHORT).show()
                    }
                    PillButton(tr("Fermer"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false, onClick = onClose)
                }
            }
        }
    }
}

/** Accueil : promesse et fonctions (pre-onboarding de MacroFactor). */
@Composable
private fun Welcome() {
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Floating("illustrations/illustration_1.webp", 200) }
    Spacer(Modifier.height(20.dp))
    Text("SuperHealth", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(tr("Nutrition, musculation et santé, au même endroit."), style = MaterialTheme.typography.titleMedium,
        color = Mf.colors.textSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    listOf(
        Icons.Rounded.Insights to tr("Un coaching calé sur votre métabolisme"),
        Icons.Rounded.Bolt to tr("La saisie d'aliments la plus rapide"),
        Icons.Rounded.MonitorWeight to tr("La vraie tendance de votre poids"),
        Icons.Rounded.AutoFixHigh to tr("Des programmes de musculation qui progressent avec vous"),
        Icons.Rounded.Favorite to tr("Votre montre : pas, cœur, sommeil et activités"),
        Icons.Rounded.BarChart to tr("Un tableau de bord qui réunit tout"),
        Icons.Rounded.Lock to tr("Sans compte ni pub : tout reste sur le téléphone"),
    ).forEachIndexed { i, (icon, text) -> Staggered(i) { Feature(icon, text) } }
}

@Composable
private fun Page(step: Step, title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
        Floating(step.image(), 130)
    }
    Spacer(Modifier.height(18.dp))
    Text(tr(title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    if (subtitle != null) Text(tr(subtitle), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(16.dp))
    Column(content = content)
    Spacer(Modifier.height(12.dp))
}

/** Illustration qui flotte doucement. */
@Composable
private fun Floating(path: String, sizeDp: Int) {
    val t = rememberInfiniteTransition(label = "float")
    val y by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    val appear = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    val d = LocalDensity.current
    AssetImage(path, Modifier.size(sizeDp.dp).graphicsLayer { translationY = with(d) { y.dp.toPx() }; scaleX = appear.value; scaleY = appear.value }
        .clip(RoundedCornerShape((sizeDp / 5).dp)))
}

/** Apparition décalée (fondu + montée) des éléments d'une liste. */
@Composable
private fun Staggered(index: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(80L + index * 60L); a.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1 - a.value) * 40f }) { content() }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Mf.colors.accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Mf.colors.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(tr(text), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Grandes tuiles de choix (une seule sélection), animées. */
@Composable
private fun <T> Options(
    items: List<T>, selected: T, label: (T) -> String, description: ((T) -> String)? = null,
    icon: ((T) -> ImageVector)? = null, onPick: (T) -> Unit,
) {
    items.forEachIndexed { i, item ->
        Staggered(i) {
            val sel = item == selected
            val border by animateColorAsState(if (sel) Mf.colors.accent else Color.Transparent, label = "b")
            val bg by animateColorAsState(if (sel) Mf.colors.accent.copy(alpha = 0.12f) else Mf.colors.card, label = "bg")
            val scale by animateFloatAsState(if (sel) 1f else 0.98f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "s")
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).scale(scale).clip(RoundedCornerShape(18.dp)).background(bg)
                    .border(2.dp, border, RoundedCornerShape(18.dp)).clickable { onPick(item) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(icon(item), null, tint = if (sel) Mf.colors.accent else Mf.colors.textSecondary, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(tr(label(item)), style = MaterialTheme.typography.titleMedium)
                    description?.let { Text(tr(it(item)), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary) }
                }
                if (sel) Icon(Icons.Rounded.CheckCircle, null, tint = Mf.colors.accent, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun SmallLabel(text: String) = Text(tr(text), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary,
    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp))

@Composable
private fun RowStat(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Sélecteur à molette (comme les pickers de MacroFactor). */
@Composable
private fun Wheel(values: List<String>, initial: Int, height: Int = 220, modifier: Modifier = Modifier, onIndex: (Int) -> Unit) {
    val itemH = 44.dp
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val pad = (height.dp - itemH) / 2
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect { (i, off) ->
            val px = with(state.layoutInfo) { visibleItemsInfo.firstOrNull()?.size ?: 1 }
            onIndex((i + if (off > px / 2) 1 else 0).coerceIn(0, values.lastIndex))
        }
    }
    Box(modifier.fillMaxWidth().height(height.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(itemH).clip(RoundedCornerShape(12.dp)).background(Mf.colors.card))
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(vertical = pad),
            flingBehavior = rememberSnapFlingBehavior(state), horizontalAlignment = Alignment.CenterHorizontally) {
            items(values.size) { i ->
                val center by remember { androidx.compose.runtime.derivedStateOf {
                    val info = state.layoutInfo
                    val item = info.visibleItemsInfo.firstOrNull { it.index == i } ?: return@derivedStateOf 3f
                    val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                    abs(item.offset + item.size / 2f - mid) / item.size
                } }
                Box(Modifier.height(itemH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(values[i], fontSize = 22.sp, fontWeight = if (center < 0.5f) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.graphicsLayer { alpha = (1f - center * 0.3f).coerceIn(0.2f, 1f); val s = (1f - center * 0.08f).coerceIn(0.75f, 1f); scaleX = s; scaleY = s })
                }
            }
        }
    }
}

/** Poids : molette entière + molette décimale, dans l'unité choisie. */
@Composable
private fun WeightWheel(kg: Double, unit: WeightUnit, height: Int = 220, onKg: (Double) -> Unit) {
    val toU = if (unit == WeightUnit.LB) 1 / KG_PER_LB else 1.0
    val v = kg * toU
    val range = if (unit == WeightUnit.LB) (70..550) else (30..250)
    var whole by remember(unit) { mutableIntStateOf(v.toInt().coerceIn(range.first, range.last)) }
    var tenth by remember(unit) { mutableIntStateOf(((v - v.toInt()) * 10).roundToInt().coerceIn(0, 9)) }
    LaunchedEffect(whole, tenth, unit) { onKg((whole + tenth / 10.0) / toU) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Wheel(range.map { it.toString() }, whole - range.first, height, Modifier.weight(1.4f)) { whole = range.first + it }
        Text(",", fontSize = 24.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Wheel((0..9).map { it.toString() }, tenth, height, Modifier.weight(0.8f)) { tenth = it }
        Text(unit.label.tr(), fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp).width(36.dp))
    }
}

/** Génération animée (coached_result_step) : messages successifs puis passage au résultat. */
@Composable
private fun Generating(onDone: () -> Unit) {
    val messages = listOf(
        tr("Ajustement du plan à vos informations."),
        tr("Alignement du plan sur votre objectif."),
        tr("Un peu plus à gauche, un peu plus à droite."),
        tr("On augmente les chances de réussite."),
        tr("Terminé. Votre plan est prêt."),
    )
    var i by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val per = 850L
        kotlinx.coroutines.coroutineScope {
            launch { progress.animateTo(1f, tween((per * messages.size).toInt(), easing = LinearEasing)) }
            for (k in messages.indices) { i = k; delay(per) }
        }
        delay(250); onDone()
    }
    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val t = rememberInfiniteTransition(label = "rocket")
        val y by t.animateFloat(-10f, 10f, infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ry")
        Box(Modifier.graphicsLayer { translationY = y }) { AssetImage("illustrations/illustration_28.webp", Modifier.size(200.dp).clip(RoundedCornerShape(40.dp))) }
        Spacer(Modifier.height(36.dp))
        AnimatedContent(i, label = "msg", transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
        }) { k ->
            Text(tr(messages[k]), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(0.7f).height(6.dp).clip(CircleShape).background(Mf.colors.chip)) {
            Box(Modifier.fillMaxWidth(progress.value).height(6.dp).clip(CircleShape).background(Mf.colors.accent))
        }
    }
}

/** Résultat : « Votre programme est prêt » avec cibles et explication courte. */
@Composable
private fun Result(tdee: Double, ci: app.superhealth.nutrition.domain.CheckIn, s: app.superhealth.nutrition.domain.Settings, p: app.superhealth.nutrition.domain.Program) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Floating(Step.RESULT.image(), 120) }
    Spacer(Modifier.height(16.dp))
    Text(tr("Votre programme est prêt"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(16.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Mf.colors.card).padding(20.dp)) {
        Text(tr("Cible moyenne"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
        Text(s.energy(ci.kcal * a.value), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            MacroStat(tr("Protéines"), ci.protein * a.value, ProteinColor, Modifier.weight(1f))
            MacroStat(tr("Lipides"), ci.fat * a.value, FatColor, Modifier.weight(1f))
            MacroStat(tr("Glucides"), ci.carbs * a.value, CarbsColor, Modifier.weight(1f))
        }
        if (ci.days.isNotEmpty()) {
            val hi = ci.days.maxOf { it.kcal }; val lo = ci.days.minOf { it.kcal }
            Spacer(Modifier.height(10.dp))
            Text(trf("Jours hauts %1\$s · autres jours %2\$s", s.energy(hi), s.energy(lo)),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(tr("Comment a-t-il été conçu ?"), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    val bullets = listOfNotNull(
        trf("Dépense estimée : %1\$s par jour.", s.energy(tdee)),
        if (ci.limitedByFloor) tr("Cible relevée au plancher calorique.") else null,
        trf("Protéines : %1\$s g par kg de masse maigre.", num(Targets.proteinPerKgLbm(p.goalType, p.training, p.proteinPreference), 1)),
        tr("La dépense et les cibles s'ajustent à chaque check-in hebdomadaire."),
    )
    bullets.forEachIndexed { i, b ->
        Staggered(i) {
            Row(Modifier.padding(vertical = 4.dp)) {
                Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(Mf.colors.accent))
                Spacer(Modifier.width(10.dp))
                Text(b, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MacroStat(label: String, g: Double, color: Color, modifier: Modifier) {
    Column(modifier) {
        Box(Modifier.width(28.dp).height(4.dp).clip(CircleShape).background(color))
        Spacer(Modifier.height(6.dp))
        Text(trf("%1\$s g", (g.roundToInt())), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
    }
}

/** Grille de silhouettes MacroFactor (assets body_fat) pour choisir son % de masse grasse. */
@Composable
fun BodyFatPicker(sex: Sex, selected: Double?, onPick: (Int) -> Unit) {
    val steps = if (sex == Sex.MALE) listOf(6, 10, 15, 20, 25, 30, 35, 40) else listOf(15, 20, 25, 30, 35, 40, 45, 50)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEach { pct ->
            val sel = selected?.let { abs(it - pct) < 0.5 } == true
            val bg by animateColorAsState(if (sel) Mf.colors.accent.copy(alpha = 0.25f) else Mf.colors.card, label = "bf")
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(bg).clickable { onPick(pct) }.padding(6.dp)) {
                AssetImage("bodyfat/${if (sex == Sex.MALE) "m" else "f"}_${pct}pct.webp", Modifier.size(width = 64.dp, height = 80.dp))
                Text("${pct} %", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Aperçu du programme de musculation généré : nom, rythme et séances de la semaine. */
@Composable
private fun LiftProgramCard(p: app.superhealth.training.domain.Program, minutes: Int) {
    val blue = app.superhealth.ui.theme.WorkoutBlue
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius))
        .background(androidx.compose.ui.graphics.lerp(Mf.colors.card, blue, if (Mf.colors.dark) 0.27f else 0.15f)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Mf.colors.card), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.FitnessCenter, null, tint = blue)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Votre programme de musculation"), style = MaterialTheme.typography.titleMedium)
                val training = p.days.count { !it.isRest }
                Text(trf("%1\$s séances par semaine · ≈ %2\$s min", training, minutes), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        p.days.filter { !it.isRest }.forEachIndexed { i, d ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(blue), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Text(d.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                Text(trf("%1\$s exercices", d.exercises.size), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
            }
        }
    }
}
