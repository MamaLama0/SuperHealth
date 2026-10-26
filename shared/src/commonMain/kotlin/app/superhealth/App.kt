package app.superhealth

import app.superhealth.platform.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.superhealth.bridge.Bridge
import app.superhealth.nutrition.NutriApp
import app.superhealth.training.WorkoutApp
import app.superhealth.ui.SuperRoot
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.SuperTheme
import app.superhealth.ui.theme.isDark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Démarrage de SuperHealth (SuperHealthApp + MainActivity d'Android) : modules nutrition et entraînement, plugins, pont
 * entre modules ; puis l'interface, identique à celle d'Android.
 */
object SuperHealth {
    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var vm: SuperViewModel
        private set

    /** À appeler une fois, après avoir choisi la source de santé de la plateforme (HealthSources.current). */
    fun init() {
        if (started) return
        started = true
        SuperPrefs.init(appContext)
        app.superhealth.modules.Modules.init(appContext)
        app.superhealth.coach.Coach.init(appContext)
        app.superhealth.bridge.RunImport.init(appContext)
        app.superhealth.meals.MealPlanner.init(appContext)
        app.superhealth.meds.Meds.init(appContext)
        NutriApp.init(appContext)
        WorkoutApp.init(appContext)
        Bridge.start(appContext)
        vm = SuperViewModel(appContext)
        vm.bind(app.superhealth.nutrition.ui.AppViewModel(NutriApp.repository), app.superhealth.training.ui.AppViewModel(appContext))
        launchRoute?.let { vm.pendingRoute.value = it; launchRoute = null }
        // tâches périodiques d'Android (WorkManager) : faites au lancement sur iOS
        scope.launch {
            runCatching { app.superhealth.modules.Modules.autoUpdate(appContext) }
            runCatching { app.superhealth.backup.BackupWorker.runIfDue(appContext) }
            runCatching { app.superhealth.meds.Meds.schedule(appContext) }
        }
    }

    /** Route demandée avant le démarrage (raccourci de l'icône, widget, notification à froid). */
    private var launchRoute: String? = null

    /** Route ouverte depuis l'extérieur (notification, raccourci, widget). */
    fun openRoute(route: String?) {
        val r = app.superhealth.ui.Routes.external(route) ?: return
        if (started) vm.pendingRoute.value = r else launchRoute = r
    }
}

/** Interface complète de SuperHealth. */
@Composable
fun App() {
    SuperHealth.init()
    val vm = SuperHealth.vm
    val s by vm.nutrition.settings.collectAsState()
    val dark = s.themeMode.isDark(isSystemInDarkTheme())
    LifecycleResumeEffect(Unit) {
        vm.onResume(appContext)
        // en quittant l'app : widgets à jour (pas, anneaux et signes vitaux relus)
        onPauseOrDispose { app.superhealth.ui.widget.Widgets.publish() }
    }
    // cœur de l'icône dans la couleur d'accent, comme le lanceur Android (iOS demande confirmation par une alerte)
    val onboarded = SuperPrefs.settings.collectAsState().value.onboarded
    LaunchedEffect(s.accent, onboarded) {
        if (onboarded) setAppIcon(if (s.accent == app.superhealth.nutrition.domain.AccentColor.PINK) null
            else "AppIcon-" + s.accent.name.lowercase().replaceFirstChar { it.uppercase() })
    }
    SuperTheme(s.themeMode, s.accent) {
        Box(Modifier.fillMaxSize().background(Mf.colors.background)) {
            SuperRoot(vm)
            PlatformDialogHost()
            ToastHost()
        }
    }
    LaunchedEffect(s.themeMode) {
        setWindowAppearance(when (s.themeMode) { app.superhealth.nutrition.domain.ThemeMode.LIGHT -> false; app.superhealth.nutrition.domain.ThemeMode.DARK -> true; else -> null })
    }
}

/** Messages courts (Toast d'Android) en bulle au bas de l'écran. */
@Composable
private fun ToastHost() {
    val t by Toast.current.collectAsState()
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(t) {
        val msg = t ?: return@LaunchedEffect
        shown = msg.first
        delay(if (msg.first.length > 60) 3500 else 2200)
        shown = null
    }
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(bottom = 96.dp, start = 24.dp, end = 24.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(shown != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
            Text(shown ?: "", Modifier.clip(RoundedCornerShape(18.dp)).background(Mf.colors.text.copy(alpha = 0.92f)).padding(horizontal = 16.dp, vertical = 10.dp),
                color = Mf.colors.background, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
