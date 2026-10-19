package app.superhealth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.superhealth.ui.SuperRoot
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.theme.SuperTheme
import app.superhealth.ui.theme.isDark

class MainActivity : ComponentActivity() {
    private val nutrition: app.superhealth.nutrition.ui.AppViewModel by viewModels {
        app.superhealth.nutrition.ui.AppViewModel.factory(app.superhealth.nutrition.NutriApp.repository)
    }
    private val training: app.superhealth.training.ui.AppViewModel by viewModels()
    private val vm: SuperViewModel by viewModels()

    companion object { const val EXTRA_ROUTE = "route" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.bind(nutrition, training)
        if (savedInstanceState == null) vm.pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        enableEdgeToEdge()
        setContent {
            val s by vm.nutrition.settings.collectAsState()
            val dark = s.themeMode.isDark(isSystemInDarkTheme())
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            SuperTheme(s.themeMode, s.accent) { SuperRoot(vm) }
        }
    }

    /** Raccourcis des widgets et de la notification de séance : l'activité existe déjà. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { vm.pendingRoute.value = it }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume(this)
    }
}
