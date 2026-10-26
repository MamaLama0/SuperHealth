package app.superhealth.ui.health

import app.superhealth.platform.*

import androidx.compose.ui.graphics.Color
import app.superhealth.bridge.Bridge
import app.superhealth.nutrition.NutriApp
import app.superhealth.ui.theme.HealthHues
import app.superhealth.platform.LocalDate

/** Couleur d'une activité, partagée par les listes, les cartes et le détail d'une séance. */
fun Bridge.Kind.hue(): Color = when (this) {
    Bridge.Kind.STRENGTH -> app.superhealth.ui.theme.WorkoutBlue
    Bridge.Kind.RUN -> HealthHues.Fitness
    Bridge.Kind.WALK, Bridge.Kind.HIKE -> HealthHues.Activity
    Bridge.Kind.BIKE -> HealthHues.Body
    Bridge.Kind.SWIM -> HealthHues.Respiration
    Bridge.Kind.YOGA -> HealthHues.Sleep
    Bridge.Kind.OTHER -> HealthHues.Mind
}

/** Zones cardiaques (échauffement → maximum). */
data class Zone(val n: Int, val label: String, val color: Color)

val ZONES = listOf(
    Zone(1, "Échauffement", Color(0xFF8FA3B8)),
    Zone(2, "Endurance", HealthHues.Body),
    Zone(3, "Aérobie", HealthHues.Activity),
    Zone(4, "Seuil", HealthHues.Mind),
    Zone(5, "Maximum", HealthHues.Heart),
)

/** Fréquence cardiaque maximale théorique (220 − âge), quand la montre ne donne pas ses zones. */
fun theoreticalMaxHr(): Int = 220 - (LocalDate.now().year - NutriApp.repository.prefs.profile.value.birthYear)
