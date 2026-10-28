package app.superhealth.health.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.superhealth.ui.theme.HealthHues

/** How a metric is summarised over a period and drawn in its detail chart. */
enum class Aggregation { SUM, AVERAGE, LAST }

enum class ChartKind { BARS, LINE, DOTS }

/** Familles de mesures (catégories « Stats importantes » de Google Health), couleur MacroFactor de chacune. */
enum class Category(val label: String, val color: Color) {
    HEART("Cœur", HealthHues.Heart),
    METABOLISM("Métabolisme", HealthHues.Body),
    RESPIRATION("Respiration", HealthHues.Respiration),
    TEMPERATURE("Température", HealthHues.Fitness),
    FITNESS("Forme", HealthHues.Activity),
    SLEEP("Sommeil", HealthHues.Sleep),
    NUTRITION("Nutrition", HealthHues.Body),
    MIND("Bien-être", HealthHues.Mind),
    VITALS("Signes vitaux", HealthHues.Heart),
}

/**
 * Mesures santé lues dans Gadgetbridge (montre) ou saisies à la main. Libellés en français, traduits par tr().
 * Le poids et la masse grasse ne sont plus ici : ils passent par les pesées du module nutrition.
 */
enum class Metric(
    val label: String,
    val unit: String?,
    val icon: ImageVector,
    val aggregation: Aggregation,
    val chart: ChartKind,
    val category: Category,
    /** Fraction digits when displayed. */
    val digits: Int = 0,
    /** Logged by hand on the phone (stored in [ManualLogStore]) rather than by a device. */
    val manual: Boolean = false,
    val about: String? = null,
) {
    STEPS("Pas", null, Icons.AutoMirrored.Rounded.DirectionsWalk, Aggregation.SUM, ChartKind.BARS, Category.FITNESS,
        about = "Vos pas, comptés par la montre et par le téléphone (pour chaque heure, le plus grand des deux : rien n'est compté deux fois). Ils affinent aussi l'estimation de votre dépense énergétique."),
    DISTANCE("Distance", "km", Icons.Rounded.Straighten, Aggregation.SUM, ChartKind.BARS, Category.FITNESS, digits = 2),
    CALORIES("Calories actives", "kcal", Icons.Rounded.LocalFireDepartment, Aggregation.SUM, ChartKind.BARS, Category.METABOLISM,
        about = "Calories dépensées en mouvement, estimées par la montre. La dépense utilisée pour vos cibles reste celle calculée à partir de vos pesées et de votre journal."),
    ACTIVE_MINUTES("Minutes actives", "min", Icons.Rounded.Timer, Aggregation.SUM, ChartKind.BARS, Category.FITNESS),
    CARDIO_WEEK("Minutes cardio", "min", Icons.AutoMirrored.Rounded.DirectionsRun, Aggregation.LAST, ChartKind.BARS, Category.FITNESS,
        about = "Minutes d'intensité modérée ou soutenue cumulées depuis lundi. L'OMS recommande au moins 150 minutes par semaine."),
    HEART_RATE("Fréquence cardiaque", "bpm", Icons.Rounded.Favorite, Aggregation.AVERAGE, ChartKind.LINE, Category.HEART),
    RESTING_HR("FC au repos", "bpm", Icons.Rounded.FavoriteBorder, Aggregation.AVERAGE, ChartKind.DOTS, Category.HEART,
        about = "Votre fréquence cardiaque la plus basse au calme. Une baisse durable accompagne souvent une meilleure forme."),
    HRV("Variabilité cardiaque", "ms", Icons.Rounded.MonitorHeart, Aggregation.AVERAGE, ChartKind.DOTS, Category.HEART,
        about = "Variation entre deux battements pendant la nuit. Comparez-la surtout à votre propre moyenne."),
    SLEEP("Sommeil", null, Icons.Rounded.Bedtime, Aggregation.AVERAGE, ChartKind.BARS, Category.SLEEP),
    SLEEP_SCORE("Score de sommeil", null, Icons.Rounded.Bedtime, Aggregation.AVERAGE, ChartKind.DOTS, Category.SLEEP),
    READINESS("Disposition", null, Icons.Rounded.Speed, Aggregation.LAST, ChartKind.DOTS, Category.FITNESS,
        about = "Disposition à l'entraînement calculée par la montre (sommeil, récupération, charge récente)."),
    BODY_ENERGY("Énergie corporelle", null, Icons.Rounded.Bolt, Aggregation.LAST, ChartKind.LINE, Category.MIND),
    STRESS("Stress", null, Icons.Rounded.SelfImprovement, Aggregation.AVERAGE, ChartKind.BARS, Category.MIND),
    SPO2("SpO₂", "%", Icons.Rounded.Bloodtype, Aggregation.AVERAGE, ChartKind.DOTS, Category.RESPIRATION),
    RESPIRATORY_RATE("Fréquence respiratoire", "resp./min", Icons.Rounded.Air, Aggregation.AVERAGE, ChartKind.DOTS, Category.RESPIRATION, digits = 1),
    VO2MAX("VO₂ max", null, Icons.Rounded.Whatshot, Aggregation.LAST, ChartKind.DOTS, Category.FITNESS, digits = 1),
    TEMPERATURE("Température cutanée", "°C", Icons.Rounded.Thermostat, Aggregation.AVERAGE, ChartKind.DOTS, Category.TEMPERATURE, digits = 1),
    RECOVERY("Charge", "%", Icons.Rounded.BatteryChargingFull, Aggregation.LAST, ChartKind.DOTS, Category.VITALS,
        about = "Récupération du matin (0–100) : votre VFC de la nuit comparée à votre propre référence, avec le pouls au repos, le sommeil, la respiration et la température. Méthodes publiées reprises par NOOP ; pas le score officiel de WHOOP. Sans VFC, elle repose sur le pouls au repos et le sommeil."),
    EFFORT("Effort", null, Icons.Rounded.Whatshot, Aggregation.LAST, ChartKind.BARS, Category.FITNESS,
        about = "Charge cardiovasculaire de la journée (0–100) : le temps passé dans chaque zone de réserve cardiaque (Karvonen, Edwards), sur une échelle logarithmique où 100 reste exceptionnel."),
    SLEEP_DEBT("Dette de sommeil", null, Icons.Rounded.Bedtime, Aggregation.LAST, ChartKind.BARS, Category.SLEEP,
        about = "Ce qu'il faut ajouter à votre besoin de sommeil cette nuit : 55 % de ce qui a manqué la veille, reporté de nuit en nuit sur deux semaines. Dormir plus que le besoin ne crée pas d'avance."),
    FITNESS_AGE("Âge de forme", "ans", Icons.Rounded.Speed, Aggregation.LAST, ChartKind.DOTS, Category.FITNESS,
        about = "L'âge d'une personne moyenne qui aurait votre forme cardio, d'après votre pouls au repos et votre Effort de la semaine (modèle de l'étude HUNT, Nes 2011). Calculé chaque samedi, à ±5 ans près : suivez sa tendance."),
    WEIGHT("Poids", "kg", Icons.Rounded.MonitorWeight, Aggregation.LAST, ChartKind.DOTS, Category.METABOLISM, digits = 1),
    BODY_FAT("Masse grasse", "%", Icons.Rounded.Percent, Aggregation.LAST, ChartKind.DOTS, Category.METABOLISM, digits = 1),
    HYDRATION("Hydratation", "ml", Icons.Rounded.WaterDrop, Aggregation.SUM, ChartKind.BARS, Category.NUTRITION, manual = true),
    GLUCOSE("Glycémie", "mg/dl", Icons.Rounded.Bloodtype, Aggregation.AVERAGE, ChartKind.DOTS, Category.VITALS, manual = true),
    BODY_TEMPERATURE("Température corporelle", "°C", Icons.Rounded.Thermostat, Aggregation.AVERAGE, ChartKind.DOTS, Category.TEMPERATURE, digits = 1, manual = true);

    val color: Color get() = category.color
}

/** Mesures calculées par SuperHealth à partir des autres (voir Scores). */
val Metric.computed: Boolean get() = this == Metric.RECOVERY || this == Metric.EFFORT || this == Metric.SLEEP_DEBT || this == Metric.FITNESS_AGE

/** Mesures saisies à la main (feuille « Enregistrer ») ; le poids passe par la pesée du module nutrition. */
val LOGGABLE_METRICS = listOf(Metric.HYDRATION, Metric.GLUCOSE, Metric.BODY_TEMPERATURE)
