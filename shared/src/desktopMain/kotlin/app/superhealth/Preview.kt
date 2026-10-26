package app.superhealth

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import app.superhealth.health.data.HealthSources

/**
 * Aperçu sur ordinateur : la même interface que l'app iOS, dans une fenêtre au format iPhone 16 (393 × 852 pt),
 * avec des données santé de démonstration à la place d'Apple Santé.
 */
fun main() {
    HealthSources.current = DemoHealthSource
    application {
        Window(onCloseRequest = ::exitApplication, title = "SuperHealth (aperçu iPhone)",
            state = WindowState(size = DpSize(393.dp, 852.dp))) {
            App()
        }
    }
}
