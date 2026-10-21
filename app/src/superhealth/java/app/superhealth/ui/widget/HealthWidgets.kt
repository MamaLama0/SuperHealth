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
    override val sizeMode = SizeMode.Responsive(WK.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        HealthRepository.invalidate()
        val ml = metric(context, Metric.HYDRATION) ?: 0.0
        val goal = HealthRepository.goal(Metric.HYDRATION) ?: 2000.0
        val ring = WK.ring((ml / goal).toFloat(), Water, WK.px(context, 110.dp), 0.12f)
        provideContent { HydrationContent(context, ml, goal, ring) }
    }
}

@Composable
private fun HydrationContent(ctx: Context, ml: Double, goal: Double, ring: Bitmap) {
    val shape = WK.shape(LocalSize.current)
    val add = actionRunCallback<AddWaterAction>()
    val drop = @Composable { size: androidx.compose.ui.unit.Dp ->
        Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
            Image(ImageProvider(ring), null, GlanceModifier.size(size))
            Image(ImageProvider(R.drawable.ic_w_drop), null, GlanceModifier.size(size * 0.34f), colorFilter = ColorFilter.tint(WK.c(Water)))
        }
    }
    val plus = @Composable { modifier: GlanceModifier, label: String ->
        Box(modifier.background(WK.tint(Water)).cornerRadius(16.dp).clickable(add), contentAlignment = Alignment.Center) {
            Text(label, style = TextStyle(color = WK.c(Water), fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
    val value = "${formatNumber(ml)} ml"
    Column(GlanceModifier.fillMaxSize().background(WK.bg).cornerRadius(WK.RADIUS.dp).padding(if (shape == WK.Shape.TINY) 6.dp else 12.dp)
        .clickable(if (shape == WK.Shape.TINY) add else WK.open(ctx, "h/metric/HYDRATION")),
        verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        when (shape) {
            // 1×1 : l'anneau ; toucher ajoute un verre
            WK.Shape.TINY -> drop(44.dp)
            WK.Shape.STRIP -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                drop(40.dp)
                Spacer(GlanceModifier.width(6.dp))
                Text(value, style = TextStyle(color = WK.text, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                plus(GlanceModifier.size(32.dp), "+")
            }
            WK.Shape.WIDE_STRIP -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                drop(40.dp)
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(value, style = TextStyle(color = WK.text, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(trf("Objectif %1\$s ml", formatNumber(goal)), style = TextStyle(color = WK.sub, fontSize = 10.sp), maxLines = 1)
                }
                plus(GlanceModifier.width(84.dp).height(32.dp), "+ 250 ml")
            }
            WK.Shape.SQUARE -> {
                drop(64.dp)
                Text(value, style = TextStyle(color = WK.text, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.height(6.dp))
                plus(GlanceModifier.fillMaxWidth().height(32.dp), "+ 250 ml")
            }
            else -> {
                val big = shape == WK.Shape.BIG
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    drop(if (big) 110.dp else 64.dp)
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(value, style = TextStyle(color = WK.text, fontSize = if (big) 26.sp else 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(trf("Objectif %1\$s ml", formatNumber(goal)), style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
                    }
                }
                Spacer(GlanceModifier.height(if (big) 16.dp else 6.dp))
                plus(GlanceModifier.fillMaxWidth().height(if (big) 44.dp else 32.dp), "+ 250 ml")
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

/** Insight du jour : le constat le plus marquant (deux de plus quand le widget est haut). */
class InsightWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WK.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val list = runCatching { InsightData.insights(context) }.getOrDefault(emptyList())
        val s = NutriApp.repository.prefs.settings.value
        val rows = list.take(3).map { Triple(it.text(s), it.source.color, it.route) }
        provideContent { InsightContent(context, rows) }
    }
}

@Composable
private fun InsightContent(ctx: Context, rows: List<Triple<String, Color, String>>) {
    val shape = WK.shape(LocalSize.current)
    val strip = shape == WK.Shape.TINY || shape == WK.Shape.STRIP || shape == WK.Shape.WIDE_STRIP
    val n = when (shape) { WK.Shape.BIG -> 3; else -> 1 }
    Column(GlanceModifier.fillMaxSize().background(WK.bg).cornerRadius(WK.RADIUS.dp).padding(if (strip) 8.dp else 14.dp).clickable(WK.open(ctx, "home")),
        verticalAlignment = Alignment.CenterVertically) {
        if (shape == WK.Shape.TINY) {
            // 1×1 : le symbole seul, toucher ouvre le constat
            Box(GlanceModifier.fillMaxSize().clickable(WK.open(ctx, rows.firstOrNull()?.third ?: "home")), contentAlignment = Alignment.Center) {
                Image(ImageProvider(R.drawable.ic_w_bulb), null, GlanceModifier.size(26.dp), colorFilter = ColorFilter.tint(WK.c(rows.firstOrNull()?.second ?: HealthHues.Mind)))
            }
            return@Column
        }
        if (!strip) {
            WidgetHeader(R.drawable.ic_w_bulb, HealthHues.Mind, tr("Insights"))
            Spacer(GlanceModifier.height(8.dp))
        }
        if (rows.isEmpty()) Text(tr("Les constats arrivent après quelques jours de suivi."), style = TextStyle(color = WK.sub, fontSize = 12.sp), maxLines = 3)
        rows.take(n).forEachIndexed { i, (text, color, route) ->
            if (i > 0) Spacer(GlanceModifier.height(10.dp))
            Row(GlanceModifier.fillMaxWidth().clickable(WK.open(ctx, route)), verticalAlignment = Alignment.CenterVertically) {
                if (strip) Image(ImageProvider(R.drawable.ic_w_bulb), null, GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(WK.c(color)))
                else Box(GlanceModifier.width(4.dp).height(if (n == 1) 44.dp else 36.dp).cornerRadius(2.dp).background(WK.c(color))) {}
                Spacer(GlanceModifier.width(8.dp))
                Text(text, style = TextStyle(color = WK.text, fontSize = when { strip -> 12.sp; n == 1 -> 15.sp; else -> 14.sp }, fontWeight = FontWeight.Medium),
                    maxLines = if (strip) 2 else 3, modifier = GlanceModifier.defaultWeight())
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
    val shape = WK.shape(LocalSize.current)
    val small = shape == WK.Shape.TINY || shape == WK.Shape.STRIP || shape == WK.Shape.WIDE_STRIP
    Column(GlanceModifier.fillMaxSize().background(WK.bg).cornerRadius(WK.RADIUS.dp).padding(if (small) 8.dp else 12.dp).clickable(WK.open(ctx, "home")),
        verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        when (shape) {
            // 1×1 : la première mesure disponible
            WK.Shape.TINY -> (items.firstOrNull { it.value != "—" } ?: items.first()).let { v ->
                Bubble(v, 24.dp)
                Text(v.value, style = TextStyle(color = WK.text, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            // 2×1 et 4×1 : symbole et valeur de chaque mesure en ligne
            WK.Shape.STRIP, WK.Shape.WIDE_STRIP -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                items.forEach { v ->
                    Row(GlanceModifier.defaultWeight().clickable(WK.open(ctx, v.route)), verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Bubble(v, if (shape == WK.Shape.STRIP) 18.dp else 26.dp)
                        Spacer(GlanceModifier.width(4.dp))
                        Text(v.value, style = TextStyle(color = WK.text, fontSize = if (shape == WK.Shape.STRIP) 11.sp else 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                }
            }
            WK.Shape.SQUARE -> items.forEachIndexed { i, v ->
                if (i > 0) Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth().clickable(WK.open(ctx, v.route)), verticalAlignment = Alignment.CenterVertically) {
                    Bubble(v, 26.dp)
                    Spacer(GlanceModifier.width(8.dp))
                    Text(v.value, style = TextStyle(color = WK.text, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
            WK.Shape.WIDE -> Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { items.forEach { VitalCell(ctx, it, 30.dp) } }
            WK.Shape.BIG -> {
                WidgetHeader(R.drawable.ic_w_heart, HealthHues.Heart, tr("Santé"))
                Spacer(GlanceModifier.height(12.dp))
                items.forEachIndexed { i, v ->
                    if (i > 0) Spacer(GlanceModifier.height(10.dp))
                    Row(GlanceModifier.fillMaxWidth().background(WK.chip).cornerRadius(18.dp).padding(10.dp).clickable(WK.open(ctx, v.route)),
                        verticalAlignment = Alignment.CenterVertically) {
                        Bubble(v, 34.dp)
                        Spacer(GlanceModifier.width(10.dp))
                        Text(v.label, style = TextStyle(color = WK.sub, fontSize = 13.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                        Text(v.value, style = TextStyle(color = WK.text, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.VitalCell(ctx: Context, v: Vital, size: androidx.compose.ui.unit.Dp) {
    Column(GlanceModifier.defaultWeight().clickable(WK.open(ctx, v.route)), horizontalAlignment = Alignment.CenterHorizontally) {
        Bubble(v, size)
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
    override val sizeMode = SizeMode.Responsive(WK.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val days = (6L downTo 0L).map { today.minusDays(it) }
        val values = days.map { d -> runCatching { Bridge.steps(context, d) }.getOrNull() }
        val goal = NutriApp.repository.prefs.settings.value.stepGoal
        val ring = WK.ring(if (goal > 0) ((values.last() ?: 0.0) / goal).toFloat() else 0f, HealthHues.Activity, WK.px(context, 72.dp), 0.12f)
        val bars = WK.bars(values, goal.takeIf { it > 0 }?.toDouble(), HealthHues.Activity, WK.px(context, 240.dp), WK.px(context, 110.dp))
        provideContent { StepsContent(context, values.last(), goal, ring, bars, days.map { dayLetter(it) }) }
    }
}

@Composable
private fun StepsContent(ctx: Context, today: Double?, goal: Int, ring: Bitmap, bars: Bitmap, letters: List<String>) {
    val shape = WK.shape(LocalSize.current)
    val value = today?.let { formatNumber(it) } ?: "—"
    val disc = @Composable { size: androidx.compose.ui.unit.Dp ->
        Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
            Image(ImageProvider(ring), null, GlanceModifier.size(size))
            Image(ImageProvider(R.drawable.ic_w_walk), null, GlanceModifier.size(size * 0.36f), colorFilter = ColorFilter.tint(WK.c(HealthHues.Activity)))
        }
    }
    val chart = @Composable { h: androidx.compose.ui.unit.Dp, withLetters: Boolean ->
        Image(ImageProvider(bars), null, GlanceModifier.fillMaxWidth().height(h), contentScale = androidx.glance.layout.ContentScale.FillBounds)
        if (withLetters) Row(GlanceModifier.fillMaxWidth()) {
            letters.forEach { Text(it, style = TextStyle(color = WK.sub, fontSize = 9.sp, textAlign = androidx.glance.text.TextAlign.Center), modifier = GlanceModifier.defaultWeight()) }
        }
    }
    val small = shape == WK.Shape.TINY || shape == WK.Shape.STRIP || shape == WK.Shape.WIDE_STRIP
    Column(GlanceModifier.fillMaxSize().background(WK.bg).cornerRadius(WK.RADIUS.dp).padding(if (small) 8.dp else 12.dp).clickable(WK.open(ctx, "h/metric/STEPS")),
        verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        when (shape) {
            WK.Shape.TINY -> { disc(36.dp); Text(value, style = TextStyle(color = WK.text, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1) }
            WK.Shape.STRIP -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                disc(40.dp); Spacer(GlanceModifier.width(8.dp))
                Text(value, style = TextStyle(color = WK.text, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            WK.Shape.WIDE_STRIP -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                disc(40.dp); Spacer(GlanceModifier.width(8.dp))
                Text(value, style = TextStyle(color = WK.text, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) { chart(30.dp, false) }
            }
            WK.Shape.SQUARE -> {
                disc(56.dp); Spacer(GlanceModifier.height(4.dp))
                Text(value, style = TextStyle(color = WK.text, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.height(4.dp)); chart(20.dp, false)
            }
            else -> {
                val big = shape == WK.Shape.BIG
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    disc(if (big) 72.dp else 52.dp); Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(value, style = TextStyle(color = WK.text, fontSize = if (big) 26.sp else 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        if (goal > 0) Text(trf("Objectif %1\$s", formatNumber(goal.toDouble())), style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
                    }
                }
                Spacer(GlanceModifier.height(if (big) 14.dp else 8.dp))
                chart(if (big) 110.dp else 28.dp, true)
            }
        }
    }
}

class HydrationWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HydrationWidget() }
class InsightWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = InsightWidget() }
class VitalsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = VitalsWidget() }
class StepsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StepsWidget() }
