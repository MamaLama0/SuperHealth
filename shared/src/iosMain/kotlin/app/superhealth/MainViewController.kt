package app.superhealth

import androidx.compose.ui.window.ComposeUIViewController
import app.superhealth.health.data.HealthSources
import app.superhealth.health.HealthKitSource
import platform.UIKit.UIViewController

/** Contrôleur racine appelé par l'app Swift (iosApp/ContentView.swift). */
fun MainViewController(): UIViewController {
    HealthSources.current = HealthKitSource
    app.superhealth.platform.installNotificationDelegate()
    return ComposeUIViewController { App() }
}

/** Ouverture par une notification ou un lien « superhealth://route ». */
fun openRoute(route: String?) = SuperHealth.openRoute(route)
