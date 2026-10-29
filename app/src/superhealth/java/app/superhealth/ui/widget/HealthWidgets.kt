package app.superhealth.ui.widget

import app.superhealth.nutrition.domain.stepGoalFor
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.ManualLogStore
import app.superhealth.health.data.Metric
import app.superhealth.insights.InsightData
import app.superhealth.insights.Insights
import app.superhealth.insights.route
import app.superhealth.insights.source
import app.superhealth.insights.text
import app.superhealth.nutrition.NutriApp
import app.superhealth.ui.dayLetter
import app.superhealth.ui.formatDuration
import app.superhealth.ui.formatNumber
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import nodomain.freeyourgadget.gadgetbridge.R
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import app.superhealth.nutrition.ui.energyValue

/*
 * Widgets santé, au style des cartes du tableau de bord : hydratation (un verre ajouté sans ouvrir l'app), insight du
 * jour, constantes, pas, et « Aperçu » (bandeau 4×1 du bureau). Petit, moyen et bandeau : symbole et valeur ; grand : détail.
 */

private val Water = Color(0xFF0A84FF)
private val Battery = Color(0xFF9BE22A)

private suspend fun metric(ctx: Context, m: Metric, d: LocalDate = LocalDate.now()): Double? =
    if (Bridge.hasSource(m)) runCatching { Bridge.healthValue(ctx, m, d) }.getOrNull()?.takeIf { it > 0 } else null

/** Anneau avec le symbole au centre. */
@Composable
private fun Disc(ring: Bitmap, icon: Int, color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(ImageProvider(ring), null, GlanceModifier.size(size))
        Image(ImageProvider(icon), null, GlanceModifier.size(size * 0.36f), colorFilter = ColorFilter.tint(WK.c(color)))
    }
}

/** Hydratation : anneau du jour et « + 250 ml » qui enregistre un verre depuis l'écran d'accueil. */
class HydrationWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WK.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        HealthRepository.invalidate()
        val ml = metric(context, Metric.HYDRATION) ?: 0.0
        val goal = HealthRepository.goal(Metric.HYDRATION) ?: 2000.0
        val ring = WK.ring((ml / goal).toFloat(), Water, WK.px(context, 96.dp), 0.12f)
        provideContent { HydrationContent(context, ml, goal, ring) }
    }
}

@Composable
private fun HydrationContent(ctx: Context, ml: Double, goal: Double, ring: Bitmap) {
    val size = WK.size(LocalSize.current)
    val add = actionRunCallback<AddWaterAction>()
    val plus = @Composable { modifier: GlanceModifier, label: String ->
        Box(modifier.background(WK.chipOn).cornerRadius(16.dp).clickable(add), contentAlignment = Alignment.Center) {
            Text(label, style = TextStyle(color = WK.on(Water), fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
    val value = formatNumber(ml)
    // petit : toucher ajoute un verre ; sinon le widget ouvre l'écran de la mesure
    WidgetCard(if (size == WK.Size.SMALL) add else WK.open(ctx, "h/metric/HYDRATION"), Water, padding = if (size == WK.Size.SMALL) 6.dp else 12.dp,
        horizontal = Alignment.CenterHorizontally) {
        when (size) {
            WK.Size.SMALL -> Disc(ring, R.drawable.ic_w_drop, Water, 44.dp)
            WK.Size.MEDIUM -> {
                Disc(ring, R.drawable.ic_w_drop, Water, 58.dp)
                Spacer(GlanceModifier.height(2.dp))
                WidgetValue(value, "ml", Water, 16)
                Spacer(GlanceModifier.height(6.dp))
                plus(GlanceModifier.fillMaxWidth().height(30.dp), "+ 250 ml")
            }
            WK.Size.STRIP -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Disc(ring, R.drawable.ic_w_drop, Water, 40.dp)
                Spacer(GlanceModifier.width(10.dp))
                Box(GlanceModifier.defaultWeight()) { WidgetValue(value, "ml", Water, 18) }
                plus(GlanceModifier.width(84.dp).height(32.dp), "+ 250 ml")
            }
            WK.Size.LARGE -> {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Disc(ring, R.drawable.ic_w_drop, Water, 64.dp)
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(tr("Hydratation"), style = TextStyle(color = WK.sub, fontSize = 12.sp), maxLines = 1)
                        WidgetValue(value, "ml", Water, 22)
                        Text(trf("Objectif %1\$s ml", formatNumber(goal)), style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
                    }
                }
                Spacer(GlanceModifier.height(8.dp))
                plus(GlanceModifier.fillMaxWidth().height(34.dp), "+ 250 ml")
            }
        }
    }
}

/** Un verre d'eau enregistré depuis le widget (même journal que le + de l'app). */
class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ManualLogStore.get(context).add(Metric.HYDRATION, System.currentTimeMillis(), 250.0)
        HealthRepository.invalidate()
        DayWidgets.refresh(context)
    }
}

/** Insight du jour : le constat le plus marquant (deux en grand). */
class InsightWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WK.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val list = runCatching { InsightData.insights(context) }.getOrDefault(emptyList())
        val s = NutriApp.repository.prefs.settings.value
        val rows = list.take(2).map { Triple(it.text(s), it.source.color, it.route) }
        provideContent { InsightContent(context, rows) }
    }
}

@Composable
private fun InsightContent(ctx: Context, rows: List<Triple<String, Color, String>>) {
    val size = WK.size(LocalSize.current)
    val first = rows.firstOrNull()
    val hue = first?.second ?: HealthHues.Mind
    WidgetCard(WK.open(ctx, first?.third ?: "home"), null, padding = if (size == WK.Size.SMALL) 6.dp else 12.dp,
        horizontal = if (size == WK.Size.SMALL) Alignment.CenterHorizontally else Alignment.Start) {
        when (size) {
            // petit : le symbole du constat, dans sa couleur
            WK.Size.SMALL -> WidgetChip(R.drawable.ic_w_bulb, hue, 40.dp, onTint = false)
            WK.Size.MEDIUM, WK.Size.STRIP -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                WidgetChip(R.drawable.ic_w_bulb, hue, if (size == WK.Size.STRIP) 32.dp else 28.dp, onTint = false)
                Spacer(GlanceModifier.width(8.dp))
                Text(first?.first ?: tr("Les constats arrivent après quelques jours de suivi."),
                    style = TextStyle(color = WK.text, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = if (size == WK.Size.STRIP) 2 else 5,
                    modifier = GlanceModifier.defaultWeight())
            }
            WK.Size.LARGE -> {
                WidgetHeader(R.drawable.ic_w_bulb, HealthHues.Mind, tr("Insights"))
                Spacer(GlanceModifier.height(8.dp))
                if (rows.isEmpty()) Text(tr("Les constats arrivent après quelques jours de suivi."), style = TextStyle(color = WK.sub, fontSize = 12.sp), maxLines = 3)
                rows.forEachIndexed { i, (text, color, route) ->
                    if (i > 0) Spacer(GlanceModifier.height(8.dp))
                    Row(GlanceModifier.fillMaxWidth().clickable(WK.open(ctx, route)), verticalAlignment = Alignment.CenterVertically) {
                        Box(GlanceModifier.width(4.dp).height(32.dp).cornerRadius(2.dp).background(WK.c(color))) {}
                        Spacer(GlanceModifier.width(8.dp))
                        Text(text, style = TextStyle(color = WK.text, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 2,
                            modifier = GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }
}

/** Santé : sommeil de la nuit, pouls au repos et Body Battery ; chaque mesure ouvre son écran. */
class VitalsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WK.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sleep = metric(context, Metric.SLEEP)
        val rhr = metric(context, Metric.RESTING_HR) ?: metric(context, Metric.RESTING_HR, LocalDate.now().minusDays(1))
        val bb = metric(context, Metric.BODY_ENERGY)
        provideContent {
            VitalsContent(context, listOf(
                Vital(R.drawable.ic_w_moon, HealthHues.Sleep, sleep?.let { formatDuration(it) } ?: "—", null, tr("Sommeil"), "h/metric/SLEEP"),
                Vital(R.drawable.ic_w_heart, HealthHues.Heart, rhr?.let { formatNumber(it) } ?: "—", tr("bpm"), tr("Pouls au repos"), "h/metric/RESTING_HR"),
                Vital(R.drawable.ic_w_bolt, Battery, bb?.let { formatNumber(it) } ?: "—", null, tr("Body Battery"), "h/metric/BODY_ENERGY"),
            ))
        }
    }
}

private data class Vital(val icon: Int, val color: Color, val value: String, val unit: String?, val label: String, val route: String)

@Composable
private fun VitalsContent(ctx: Context, items: List<Vital>) {
    val size = WK.size(LocalSize.current)
    val main = items.firstOrNull { it.value != "—" } ?: items.first()
    WidgetCard(WK.open(ctx, if (size == WK.Size.SMALL) main.route else "h/vitals"), if (size == WK.Size.SMALL) main.color else null,
        padding = if (size == WK.Size.SMALL) 6.dp else 12.dp, horizontal = Alignment.CenterHorizontally) {
        when (size) {
            // petit : la première mesure disponible, symbole et valeur
            WK.Size.SMALL -> {
                WidgetChip(main.icon, main.color, 24.dp)
                Text(main.value, style = TextStyle(color = WK.on(main.color), fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            WK.Size.MEDIUM -> items.forEachIndexed { i, v ->
                if (i > 0) Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth().clickable(WK.open(ctx, v.route)), verticalAlignment = Alignment.CenterVertically) {
                    WidgetChip(v.icon, v.color, 28.dp, onTint = false)
                    Spacer(GlanceModifier.width(8.dp))
                    WidgetValue(v.value, v.unit, v.color, 15)
                }
            }
            WK.Size.STRIP -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                items.forEach { v ->
                    Row(GlanceModifier.defaultWeight().clickable(WK.open(ctx, v.route)), verticalAlignment = Alignment.CenterVertically) {
                        WidgetChip(v.icon, v.color, 28.dp, onTint = false)
                        Spacer(GlanceModifier.width(6.dp))
                        WidgetValue(v.value, null, v.color, 15)
                    }
                }
            }
            WK.Size.LARGE -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                items.forEach { v ->
                    Column(GlanceModifier.defaultWeight().fillMaxHeight().background(WK.tint(v.color)).cornerRadius(18.dp).padding(8.dp)
                        .clickable(WK.open(ctx, v.route)), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                        WidgetChip(v.icon, v.color, 30.dp)
                        Spacer(GlanceModifier.height(6.dp))
                        WidgetValue(v.value, v.unit, v.color, 17)
                        Text(v.label, style = TextStyle(color = WK.sub, fontSize = 10.sp), maxLines = 1)
                    }
                    if (v != items.last()) Spacer(GlanceModifier.width(6.dp))
                }
            }
        }
    }
}

/** Pas : aujourd'hui, anneau d'objectif et barres des 7 derniers jours. */
class StepsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WK.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val days = (6L downTo 0L).map { today.minusDays(it) }
        val values = days.map { d -> runCatching { Bridge.steps(context, d) }.getOrNull() }
        val goal = NutriApp.repository.prefs.settings.value.stepGoalFor(today)
        val ring = WK.ring(if (goal > 0) ((values.last() ?: 0.0) / goal).toFloat() else 0f, HealthHues.Activity, WK.px(context, 72.dp), 0.12f)
        val bars = WK.bars(values, goal.takeIf { it > 0 }?.toDouble(), HealthHues.Activity, WK.px(context, 240.dp), WK.px(context, 60.dp))
        provideContent { StepsContent(context, values.last(), goal, ring, bars, days.map { dayLetter(it) }) }
    }
}

@Composable
private fun StepsContent(ctx: Context, today: Double?, goal: Int, ring: Bitmap, bars: Bitmap, letters: List<String>) {
    val size = WK.size(LocalSize.current)
    val hue = HealthHues.Activity
    val value = today?.let { formatNumber(it) } ?: "—"
    val chart = @Composable { h: androidx.compose.ui.unit.Dp, modifier: GlanceModifier ->
        Image(ImageProvider(bars), null, modifier.height(h), contentScale = androidx.glance.layout.ContentScale.FillBounds)
    }
    WidgetCard(WK.open(ctx, "h/metric/STEPS"), hue, padding = if (size == WK.Size.SMALL) 6.dp else 12.dp,
        horizontal = if (size == WK.Size.SMALL) Alignment.CenterHorizontally else Alignment.Start) {
        when (size) {
            WK.Size.SMALL -> Disc(ring, R.drawable.ic_w_walk, hue, 44.dp)
            WK.Size.MEDIUM -> {
                WidgetChip(R.drawable.ic_w_walk, hue, 28.dp)
                Spacer(GlanceModifier.height(6.dp))
                WidgetValue(value, null, hue, 20)
                Spacer(GlanceModifier.defaultWeight())
                chart(34.dp, GlanceModifier.fillMaxWidth())
            }
            WK.Size.STRIP -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Disc(ring, R.drawable.ic_w_walk, hue, 40.dp)
                Spacer(GlanceModifier.width(10.dp))
                WidgetValue(value, null, hue, 18)
                Spacer(GlanceModifier.width(12.dp))
                chart(30.dp, GlanceModifier.defaultWeight())
            }
            WK.Size.LARGE -> {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Disc(ring, R.drawable.ic_w_walk, hue, 46.dp)
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        WidgetValue(value, tr("pas"), hue, 20)
                        if (goal > 0) Text(trf("Objectif %1\$s", formatNumber(goal.toDouble())), style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                chart(30.dp, GlanceModifier.fillMaxWidth())
                Row(GlanceModifier.fillMaxWidth()) {
                    letters.forEach { Text(it, style = TextStyle(color = WK.sub, fontSize = 9.sp, textAlign = androidx.glance.text.TextAlign.Center), modifier = GlanceModifier.defaultWeight()) }
                }
            }
        }
    }
}

/**
 * Aperçu (bandeau 4×1 du bureau) : calories restantes, pas, eau et Body Battery, chacun dans sa pastille ; pas de texte,
 * seulement les valeurs. En plus haut, le nom de chaque mesure s'ajoute dessous.
 */
class OverviewWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(WK.STRIP, WK.LARGE, androidx.compose.ui.unit.DpSize(180.dp, 57.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        HealthRepository.invalidate()
        val repo = NutriApp.repository
        val today = LocalDate.now()
        val s = repo.prefs.settings.value
        val items = runCatching {
            val eaten = repo.entriesForDay(today.toEpochDay()).first().sumOf { it.kcal }
            val target = repo.analytics().first().targetFor(today.toEpochDay()).macros.kcal
            val left = s.energyValue(target - eaten)
            listOf(
                Vital(R.drawable.ic_w_food, app.superhealth.ui.theme.CaloriesColor, formatNumber(kotlin.math.abs(left)), null,
                    if (left >= 0) tr("Restantes") else tr("en trop"), "log"),
                Vital(R.drawable.ic_w_walk, HealthHues.Activity, runCatching { Bridge.steps(context, today) }.getOrNull()?.let { formatNumber(it) } ?: "—", null,
                    tr("Pas"), "h/metric/STEPS"),
                Vital(R.drawable.ic_w_drop, Water, formatNumber(metric(context, Metric.HYDRATION) ?: 0.0), "ml", tr("Hydratation"), "h/metric/HYDRATION"),
                Vital(R.drawable.ic_w_bolt, Battery, metric(context, Metric.BODY_ENERGY)?.let { formatNumber(it) } ?: "—", null, tr("Body Battery"), "h/metric/BODY_ENERGY"),
            )
        }.getOrDefault(emptyList())
        provideContent { OverviewContent(context, items) }
    }
}

@Composable
private fun OverviewContent(ctx: Context, items: List<Vital>) {
    val ls = LocalSize.current
    val labels = ls.height >= 100.dp
    val shown = if (ls.width < 250.dp) items.take(3) else items
    WidgetCard(WK.open(ctx, "home"), null, padding = 8.dp) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            shown.forEachIndexed { i, v ->
                if (i > 0) Spacer(GlanceModifier.width(6.dp))
                Column(GlanceModifier.defaultWeight().fillMaxHeight().background(WK.tint(v.color)).cornerRadius(18.dp).padding(horizontal = 6.dp)
                    .clickable(WK.open(ctx, v.route)), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (labels) {
                        WidgetChip(v.icon, v.color, 26.dp)
                        Spacer(GlanceModifier.height(4.dp))
                        WidgetValue(v.value, v.unit, v.color, 16)
                        Text(v.label, style = TextStyle(color = WK.sub, fontSize = 10.sp), maxLines = 1)
                    } else Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(ImageProvider(v.icon), null, GlanceModifier.size(14.dp), colorFilter = ColorFilter.tint(WK.c(v.color)))
                        Spacer(GlanceModifier.width(4.dp))
                        Text(v.value, style = TextStyle(color = WK.on(v.color), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                }
            }
        }
    }
}

class HydrationWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HydrationWidget() }
class InsightWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = InsightWidget() }
class VitalsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = VitalsWidget() }
class StepsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StepsWidget() }
class OverviewWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = OverviewWidget() }
