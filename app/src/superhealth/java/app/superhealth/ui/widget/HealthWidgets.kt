package app.superhealth.ui.widget

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

/*
 * Widgets santé : hydratation (ajout d'un verre sans ouvrir l'app), insight du jour, constantes et pas de la semaine.
 */

private val Water = Color(0xFF0A84FF)

private suspend fun metric(ctx: Context, m: Metric, d: LocalDate = LocalDate.now()): Double? =
    if (Bridge.hasSource(m)) runCatching { Bridge.healthValue(ctx, m, d) }.getOrNull()?.takeIf { it > 0 } else null

/** Hydratation : anneau du jour, et « + 250 ml » qui enregistre un verre directement depuis l'écran d'accueil. */
class HydrationWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(200.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        HealthRepository.invalidate()
        val ml = metric(context, Metric.HYDRATION) ?: 0.0
        val goal = HealthRepository.goal(Metric.HYDRATION) ?: 2000.0
        val ring = WK.ring((ml / goal).toFloat(), Water, WK.px(context, 64.dp), 0.13f)
        provideContent { HydrationContent(context, ml, goal, ring) }
    }
}

@Composable
private fun HydrationContent(ctx: Context, ml: Double, goal: Double, ring: Bitmap) {
    val wide = LocalSize.current.width >= 200.dp
    val add = actionRunCallback<AddWaterAction>()
    Column(GlanceModifier.fillMaxSize().background(WK.bg).cornerRadius(WK.RADIUS.dp).padding(12.dp).clickable(WK.open(ctx, "h/metric/HYDRATION")),
        verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(GlanceModifier.size(64.dp), contentAlignment = Alignment.Center) {
                Image(ImageProvider(ring), null, GlanceModifier.size(64.dp))
                Image(ImageProvider(R.drawable.ic_w_drop), null, GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(WK.c(Water)))
            }
            if (wide) {
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text("${formatNumber(ml)} ml", style = TextStyle(color = WK.text, fontSize = 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(trf("Objectif %1\$s ml", formatNumber(goal)), style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
                }
            }
        }
        if (!wide) Text("${formatNumber(ml)} ml", style = TextStyle(color = WK.text, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Spacer(GlanceModifier.height(6.dp))
        Box(GlanceModifier.fillMaxWidth().height(32.dp).background(WK.tint(Water)).cornerRadius(16.dp).clickable(add), contentAlignment = Alignment.Center) {
            Text("+ 250 ml", style = TextStyle(color = WK.c(Water), fontSize = 13.sp, fontWeight = FontWeight.Bold))
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

/** Insight du jour : le constat le plus marquant (deux de plus quand le widget est haut). */
class InsightWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 80.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 200.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val list = runCatching { InsightData.insights(context) }.getOrDefault(emptyList())
        val s = NutriApp.repository.prefs.settings.value
        val rows = list.take(3).map { Triple(it.text(s), it.source.color, it.route) }
        provideContent { InsightContent(context, rows) }
    }
}

@Composable
private fun InsightContent(ctx: Context, rows: List<Triple<String, Color, String>>) {
    val h = LocalSize.current.height
    val n = if (h >= 200.dp) 3 else 1
    Column(GlanceModifier.fillMaxSize().background(WK.bg).cornerRadius(WK.RADIUS.dp).padding(14.dp).clickable(WK.open(ctx, "home"))) {
        WidgetHeader(R.drawable.ic_w_bulb, HealthHues.Mind, tr("Insights"))
        Spacer(GlanceModifier.height(8.dp))
        if (rows.isEmpty()) Text(tr("Les constats arrivent après quelques jours de suivi."), style = TextStyle(color = WK.sub, fontSize = 12.sp), maxLines = 3)
        rows.take(n).forEachIndexed { i, (text, color, route) ->
            if (i > 0) Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth().clickable(WK.open(ctx, route)), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.width(4.dp).height(if (n == 1) 40.dp else 32.dp).cornerRadius(2.dp).background(WK.c(color))) {}
                Spacer(GlanceModifier.width(8.dp))
                Text(text, style = TextStyle(color = WK.text, fontSize = if (n == 1) 15.sp else 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = if (n == 1) 3 else 2, modifier = GlanceModifier.defaultWeight())
            }
        }
    }
}

/** Santé : sommeil de la nuit, pouls au repos et Body Battery ; chaque mesure ouvre son écran. */
class VitalsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 80.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sleep = metric(context, Metric.SLEEP)
        val rhr = metric(context, Metric.RESTING_HR) ?: metric(context, Metric.RESTING_HR, LocalDate.now().minusDays(1))
        val bb = metric(context, Metric.BODY_ENERGY)
        provideContent {
            VitalsContent(context, listOf(
                Vital(R.drawable.ic_w_moon, HealthHues.Sleep, sleep?.let { formatDuration(it) } ?: "—", tr("Sommeil"), "h/metric/SLEEP"),
                Vital(R.drawable.ic_w_heart, HealthHues.Heart, rhr?.let { formatNumber(it) } ?: "—", tr("Repos (bpm)"), "h/metric/RESTING_HR"),
                Vital(R.drawable.ic_w_bolt, Color(0xFF9BE22A), bb?.let { formatNumber(it) } ?: "—", tr("Body Battery"), "h/metric/BODY_ENERGY"),
            ))
        }
    }
}

private data class Vital(val icon: Int, val color: Color, val value: String, val label: String, val route: String)

@Composable
private fun VitalsContent(ctx: Context, items: List<Vital>) {
    val wide = LocalSize.current.width >= 250.dp
    Column(GlanceModifier.fillMaxSize().background(WK.bg).cornerRadius(WK.RADIUS.dp).padding(12.dp).clickable(WK.open(ctx, "home")),
        verticalAlignment = Alignment.CenterVertically) {
        if (wide) Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { VitalCell(ctx, it) }
        } else items.forEachIndexed { i, v ->
            if (i > 0) Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth().clickable(WK.open(ctx, v.route)), verticalAlignment = Alignment.CenterVertically) {
                Bubble(v, 26.dp)
                Spacer(GlanceModifier.width(8.dp))
                Text(v.value, style = TextStyle(color = WK.text, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

@Composable
private fun RowScope.VitalCell(ctx: Context, v: Vital) {
    Column(GlanceModifier.defaultWeight().clickable(WK.open(ctx, v.route)), horizontalAlignment = Alignment.CenterHorizontally) {
        Bubble(v, 30.dp)
        Spacer(GlanceModifier.height(4.dp))
        Text(v.value, style = TextStyle(color = WK.text, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(v.label, style = TextStyle(color = WK.sub, fontSize = 10.sp), maxLines = 1)
    }
}

@Composable
private fun Bubble(v: Vital, size: androidx.compose.ui.unit.Dp) {
    Box(GlanceModifier.size(size).cornerRadius(size / 2).background(WK.tint(v.color)), contentAlignment = Alignment.Center) {
        Image(ImageProvider(v.icon), null, GlanceModifier.size(size * 0.58f), colorFilter = ColorFilter.tint(WK.c(v.color)))
    }
}

/** Pas : aujourd'hui, anneau d'objectif et barres des 7 derniers jours (dès 2 cases de large). */
class StepsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(200.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val days = (6L downTo 0L).map { today.minusDays(it) }
        val values = days.map { d -> runCatching { Bridge.steps(context, d) }.getOrNull() }
        val goal = NutriApp.repository.prefs.settings.value.stepGoal
        val ring = WK.ring(if (goal > 0) ((values.last() ?: 0.0) / goal).toFloat() else 0f, HealthHues.Activity, WK.px(context, 56.dp), 0.13f)
        val bars = WK.bars(values, goal.takeIf { it > 0 }?.toDouble(), HealthHues.Activity, WK.px(context, 150.dp), WK.px(context, 28.dp))
        provideContent { StepsContent(context, values.last(), goal, ring, bars, days.map { dayLetter(it) }) }
    }
}

@Composable
private fun StepsContent(ctx: Context, today: Double?, goal: Int, ring: Bitmap, bars: Bitmap, letters: List<String>) {
    val wide = LocalSize.current.width >= 200.dp
    Column(GlanceModifier.fillMaxSize().background(WK.bg).cornerRadius(WK.RADIUS.dp).padding(12.dp).clickable(WK.open(ctx, "h/metric/STEPS")),
        verticalAlignment = Alignment.CenterVertically, horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(56.dp), contentAlignment = Alignment.Center) {
                Image(ImageProvider(ring), null, GlanceModifier.size(56.dp))
                Image(ImageProvider(R.drawable.ic_w_walk), null, GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(WK.c(HealthHues.Activity)))
            }
            if (wide) {
                Spacer(GlanceModifier.width(10.dp))
                Column {
                    Text(today?.let { formatNumber(it) } ?: "—", style = TextStyle(color = WK.text, fontSize = 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    if (goal > 0) Text(trf("Objectif %1\$s", formatNumber(goal.toDouble())), style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
                }
            }
        }
        if (wide) {
            Spacer(GlanceModifier.height(8.dp))
            Image(ImageProvider(bars), null, GlanceModifier.fillMaxWidth().height(28.dp), contentScale = androidx.glance.layout.ContentScale.FillBounds)
            Row(GlanceModifier.fillMaxWidth()) {
                letters.forEach { Text(it, style = TextStyle(color = WK.sub, fontSize = 9.sp, textAlign = androidx.glance.text.TextAlign.Center), modifier = GlanceModifier.defaultWeight()) }
            }
        } else {
            Spacer(GlanceModifier.height(4.dp))
            Text(today?.let { formatNumber(it) } ?: "—", style = TextStyle(color = WK.text, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

class HydrationWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HydrationWidget() }
class InsightWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = InsightWidget() }
class VitalsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = VitalsWidget() }
class StepsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StepsWidget() }
