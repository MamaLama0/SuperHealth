package app.superhealth.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.superhealth.ui.SuperRoot
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.theme.SuperTheme

/** Accès aux parcours réels dans le paquet QA uniquement. Aucune route supplémentaire dans l'app distribuée. */
class FlowPreviewActivity : ComponentActivity() {
    private val nutrition: app.superhealth.nutrition.ui.AppViewModel by viewModels {
        app.superhealth.nutrition.ui.AppViewModel.factory(app.superhealth.nutrition.NutriApp.repository)
    }
    private val training: app.superhealth.training.ui.AppViewModel by viewModels()
    private val vm: SuperViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (packageName != "app.superhealth.qa") { finish(); return }
        vm.bind(nutrition, training)
        intent.getStringExtra("activity")?.takeIf { name -> app.superhealth.ui.activities.ActivitySection.entries.any { it.name == name } }?.let { name ->
            app.superhealth.SuperPrefs.update { it.copy(activityCurrent = name, activityAsked = true, activitySections = (it.activitySections + name).distinct()) }
        }
        vm.pendingRoute.value = intent.getStringExtra("route") ?: "home"
        enableEdgeToEdge()
        setContent {
            val settings by nutrition.settings.collectAsState()
            SuperTheme(settings.themeMode, settings.accent, settings.textSize) { SuperRoot(vm) }
        }
    }
}
