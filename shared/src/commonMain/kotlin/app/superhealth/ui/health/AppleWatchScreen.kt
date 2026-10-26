package app.superhealth.ui.health

import app.superhealth.platform.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.HealthSources
import app.superhealth.health.data.HeartEvent
import app.superhealth.health.data.HeartEventKind
import app.superhealth.health.data.Metric
import app.superhealth.health.data.NightVitals
import app.superhealth.nutrition.ui.components.EmptyState
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.GroupHeader
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.formatValue
import app.superhealth.ui.go
import app.superhealth.ui.formatWithUnit
import app.superhealth.ui.relativeDay
import app.superhealth.ui.localDate
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.timeOf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.ui.unitOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dernière lecture : réaffichée au retour sur l'écran, le temps de relire (pas de clignotement). */
private var lastNight: NightVitals.Night? = null
private var lastLatest: Map<Metric, Pair<LocalDate, Double>> = emptyMap()

/** Familles de l'écran Apple Watch, dans l'ordre d'affichage. */
private val GROUPS = listOf(
    "Cœur" to listOf(Metric.RESTING_HR, Metric.WALKING_HR, Metric.HRV, Metric.HR_RECOVERY, Metric.VO2MAX, Metric.AFIB_BURDEN),
    "Activité et mobilité" to listOf(Metric.STEPS, Metric.STAND_HOURS, Metric.FLIGHTS, Metric.WALKING_SPEED, Metric.WALKING_STEADINESS),
    "Bien-être et environnement" to listOf(Metric.DAYLIGHT, Metric.MINDFUL, Metric.NOISE),
)

/** Plage habituelle et valeur de la nuit, dans la couleur du statut. */
private fun NightVitals.Status.color(): Color = when (this) {
    NightVitals.Status.HIGH, NightVitals.Status.LOW -> HealthHues.Mind
    NightVitals.Status.TYPICAL -> HealthHues.Activity
    NightVitals.Status.UNKNOWN -> Color(0xFF8E9199)
}

@Composable
fun nightVitalsState(vm: SuperViewModel): NightVitals.Night? {
    val refresh by vm.refresh.collectAsState()
    val ctx = LocalContext.current
    val night by produceState(lastNight, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { NightVitals.of(ctx) }.getOrNull() }?.also { lastNight = it } ?: value
    }
    return night
}

/**
 * Santé Apple Watch : signes vitaux de la nuit, cœur (dont alertes et ECG), activité et mobilité, bien-être.
 * Chaque mesure ouvre son écran détaillé.
 */
@Composable
fun AppleWatchScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val night = nightVitalsState(vm)
    val latest by produceState(lastLatest, refresh) {
        value = withContext(Dispatchers.IO) {
            GROUPS.flatMap { it.second }.filter { HealthRepository.isSupported(it) }
                .mapNotNull { m -> runCatching { HealthRepository.latestValue(ctx, m, LocalDate.now(), 60) }.getOrNull()?.let { m to it } }.toMap()
        }.also { lastLatest = it }
    }
    DetailScaffold(tr("Santé Apple Watch"), onBack = { nav.popBackStack() }) {
        GroupHeader(tr("Signes vitaux de la nuit"), Modifier.padding(start = 0.dp))
        MfCard { NightVitalsBody(night, onMetric = { nav.go("h/metric/${it.name}") }) }

        GROUPS.forEach { (title, metrics) ->
            val shown = metrics.filter { it in latest }
            if (shown.isEmpty() && title != "Cœur") return@forEach
            GroupHeader(tr(title), Modifier.padding(start = 0.dp))
            GroupCard {
                shown.forEachIndexed { i, m ->
                    if (i > 0) RowDivider()
                    val (d, v) = latest.getValue(m)
                    SettingRow(tr(m.label), formatWithUnit(m, v), subtitle = if (d == LocalDate.now()) null else relativeDay(d),
                        icon = m.icon, hue = m.color) { nav.go("h/metric/${m.name}") }
                }
                if (title == "Cœur") {
                    if (shown.isNotEmpty()) RowDivider()
                    SettingRow(tr("Alertes cardiaques et ECG"), subtitle = tr("Pouls élevé ou bas, rythme irrégulier, électrocardiogrammes"),
                        icon = Icons.Rounded.MonitorHeart, hue = HealthHues.Heart) { nav.go("h/heart") }
                }
            }
        }
        Text(tr("Les signes vitaux comparent la dernière nuit à vos 28 nuits précédentes (méthode SuperHealth). Ce ne sont pas des diagnostics médicaux."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp))
    }
}

/** En-tête (tout est dans la normale, ou nombre de mesures hors de l'habituel) puis une ligne par mesure. */
@Composable
fun NightVitalsBody(night: NightVitals.Night?, onMetric: ((Metric) -> Unit)?, compact: Boolean = false) {
    val n = night
    if (n == null || n.measured == 0) {
        Text(tr("Portez l'Apple Watch la nuit avec le suivi du sommeil : vos signes vitaux apparaissent après quelques nuits."),
            style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
        return
    }
    val ok = n.outliers == 0
    val hue = if (ok) HealthHues.Activity else HealthHues.Mind
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null, Modifier.size(22.dp), tint = hue)
        Spacer(Modifier.width(8.dp))
        Text(if (ok) tr("Dans votre plage habituelle") else if (n.outliers == 1) tr("1 mesure hors de l'habituel") else trf("%1\$s mesures hors de l'habituel", n.outliers),
            style = MaterialTheme.typography.titleMedium, color = hue.onContainer())
    }
    Spacer(Modifier.height(10.dp))
    val items = if (compact) n.items.filter { it.value != null }.take(4) else n.items
    items.forEachIndexed { i, it ->
        if (i > 0) Spacer(Modifier.height(10.dp))
        VitalRangeRow(it, onMetric)
    }
}

@Composable
private fun VitalRangeRow(item: NightVitals.Item, onClick: ((Metric) -> Unit)?) {
    val m = item.metric
    val c = item.status.color()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).then(if (onClick != null) Modifier.clickable { onClick(m) } else Modifier)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(m.icon, null, Modifier.size(16.dp), tint = m.color)
            Spacer(Modifier.width(6.dp))
            Text(tr(m.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.value?.let { formatValue(m, it) + (unitOf(m)?.let { u -> " $u" } ?: "") } ?: "—", style = MaterialTheme.typography.titleSmall,
                color = if (item.status == NightVitals.Status.UNKNOWN) Mf.colors.textSecondary else c.onContainer())
        }
        val lo = item.low; val hi = item.high; val v = item.value
        if (lo != null && hi != null && v != null) {
            Spacer(Modifier.height(5.dp))
            // la plage habituelle occupe le milieu de la barre ; la valeur s'y place, ou dépasse d'un côté
            val span = (hi - lo).coerceAtLeast(1e-6)
            val f = (((v - lo) / span) * 0.5 + 0.25).coerceIn(0.02, 0.98).toFloat()
            // dessin direct (pas de BoxWithConstraints : la carte du tableau de bord se mesure en hauteur intrinsèque)
            val track = Mf.colors.chip
            val band = HealthHues.Activity.container(1.6f)
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                val h = 6.dp.toPx(); val y = (size.height - h) / 2
                val r = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2)
                drawRoundRect(track, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, h), r)
                drawRoundRect(band, androidx.compose.ui.geometry.Offset(size.width * 0.25f, y), androidx.compose.ui.geometry.Size(size.width * 0.5f, h), r)
                drawCircle(c, size.height / 2, androidx.compose.ui.geometry.Offset(size.width * f, size.height / 2))
            }
        }
    }
}

/** Alertes de la montre (pouls élevé ou bas, rythme irrégulier) et ECG de l'année. */
@Composable
fun HeartEventsScreen(vm: SuperViewModel, nav: NavHostController) {
    val refresh by vm.refresh.collectAsState()
    val events by produceState<List<HeartEvent>?>(null, refresh) {
        value = withContext(Dispatchers.IO) {
            runCatching { HealthSources.current.heartEvents(nowMs() - 365 * 86_400_000L, nowMs()) }.getOrDefault(emptyList())
        }
    }
    DetailScaffold(tr("Alertes cardiaques et ECG"), onBack = { nav.popBackStack() }) {
        val list = events
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(Icons.Rounded.MonitorHeart, tr("Aucune alerte ni ECG cette année"),
                tr("L'Apple Watch vous prévient d'un pouls anormalement élevé ou bas et d'un rythme irrégulier ; les ECG faits avec l'app ECG apparaissent ici."),
                hue = HealthHues.Heart)
            else -> listOf(HeartEventKind.ECG, HeartEventKind.IRREGULAR, HeartEventKind.HIGH, HeartEventKind.LOW).forEach { k ->
                val l = list.filter { it.kind == k }
                if (l.isEmpty()) return@forEach
                GroupHeader(tr(k.label), Modifier.padding(start = 0.dp))
                GroupCard {
                    l.take(30).forEachIndexed { i, e ->
                        if (i > 0) RowDivider()
                        val title = e.detail?.let(::tr) ?: e.bpm?.let { trf("Seuil %1\$s bpm", it) } ?: tr(k.label)
                        val sub = relativeDay(localDate(e.timeMs)) + " · " + timeOf(e.timeMs) + (if (e.kind == HeartEventKind.ECG) e.bpm?.let { " · " + trf("%1\$s bpm", it) } ?: "" else "")
                        SettingRow(title, null, subtitle = sub, icon = if (e.kind == HeartEventKind.ECG) Icons.Rounded.MonitorHeart else Icons.Rounded.Favorite,
                            hue = if (e.alert) HealthHues.Heart else HealthHues.Activity)
                    }
                }
            }
        }
        Text(tr("Informations reprises d'Apple Santé, à montrer à un médecin en cas de doute : SuperHealth ne pose aucun diagnostic."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp))
    }
}
