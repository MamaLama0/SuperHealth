package app.superhealth

import androidx.compose.ui.window.ComposeUIViewController
import app.superhealth.health.data.HealthSources
import app.superhealth.health.HealthKitSource
import platform.UIKit.UIViewController

/** Contrôleur racine appelé par l'app Swift (iosApp/ContentView.swift). */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun MainViewController(): UIViewController {
    HealthSources.current = HealthKitSource
    app.superhealth.platform.installNotificationDelegate()
    // rendu sur un fil à part : le fil principal ne fait plus que la composition et la mise en page
    return ComposeUIViewController(configure = { parallelRendering = true }) { App() }
}

/** Démarrage de l'app (init de l'App Swift) : source de santé et tâches de fond, avant toute interface. */
fun launch() {
    HealthSources.current = HealthKitSource
    app.superhealth.platform.BackgroundRefresh.register()
}

/** Ouverture par une notification, un widget ou un lien « superhealth://open?route=… ». */
fun openRoute(route: String?) = SuperHealth.openRoute(route)
