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

/** Démarrage de l'app (init de l'App Swift) : source de santé et tâches de fond, avant toute interface. */
fun launch() {
    HealthSources.current = HealthKitSource
    app.superhealth.platform.BackgroundRefresh.register()
}

/** Ouverture par une notification, un widget ou un lien « superhealth://open?route=… ». */
fun openRoute(route: String?) = SuperHealth.openRoute(route)
