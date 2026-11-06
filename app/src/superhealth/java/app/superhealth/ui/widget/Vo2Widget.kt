package app.superhealth.ui.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
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
import app.superhealth.health.data.Metric
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.domain.Sex
import app.superhealth.ui.formatNumber
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * VO₂ max : dernière valeur de la montre (ou de Health Connect), niveau selon l'âge et le sexe, et courbe des 90 derniers
 * jours. Petit : la jauge et le chiffre ; bandeau et plus grand : niveau, évolution sur 30 jours et courbe.
 */
object Vo2 {
    /** Niveaux indicatifs (repères ACSM simplifiés, ml/kg/min) : bornes basses de Moyen, Bon, Excellent, Supérieur. */
    fun level(vo2: Double, age: Int, sex: Sex): Int {
        val male = when { age < 30 -> listOf(38, 42, 46, 52); age < 40 -> listOf(36, 40, 44, 49); age < 50 -> listOf(34, 38, 42, 47)
            age < 60 -> listOf(31, 35, 39, 44); else -> listOf(28, 32, 36, 41) }
        val bounds = if (sex == Sex.MALE) male else male.map { it - 6 }
        return bounds.count { vo2 >= it }
    }

    fun levelLabel(level: Int) = tr(when (level) { 0 -> "Faible"; 1 -> "Moyen"; 2 -> "Bon"; 3 -> "Excellent"; else -> "Supérieur" })
}

class Vo2Widget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WK.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val days = (89L downTo 0L).map { today.minusDays(it) }
        val values = coroutineScope {
            days.map { d -> async(Dispatchers.IO) { runCatching { Bridge.healthValue(context, Metric.VO2MAX, d) }.getOrNull()?.takeIf { it > 0 } } }.awaitAll()
        }
        val last = values.withIndex().lastOrNull { it.value != null }
        val v = last?.value
        // évolution : dernière valeur face à la mesure la plus proche d'il y a 30 jours
        val before = last?.let { l -> values.take((l.index - 29).coerceAtLeast(0) + 1).lastOrNull { it != null } }
        val delta = if (v != null && before != null && last.index >= 30) v - before else null
        val profile = NutriApp.repository.prefs.profile.value
        val level = v?.let { Vo2.level(it, today.year - profile.birthYear, profile.sex) }
        val hue = HealthHues.Fitness
        val ring = WK.ring(v?.let { ((it - 20) / 45).toFloat().coerceIn(0.04f, 1f) } ?: 0f, hue, WK.px(context, 96.dp), 0.11f)
        val spark = WK.spark(values, hue, WK.px(context, 240.dp), WK.px(context, 56.dp))
        val age = last?.let { (days.size - 1 - it.index) }
        provideContent { Vo2Content(context, v, level, delta, age, ring, spark, values.count { it != null } >= 2) }
    }
}

@Composable
private fun Vo2Gauge(ring: Bitmap, value: String, color: Color, size: Dp) {
    Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(ImageProvider(ring), null, GlanceModifier.size(size))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(ImageProvider(R.drawable.ic_w_flame), null, GlanceModifier.size(size * 0.2f), colorFilter = ColorFilter.tint(WK.on(color)))
            Text(value, style = TextStyle(color = WK.on(color), fontSize = (size.value * 0.27f).sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

@Composable
private fun Vo2Content(ctx: Context, v: Double?, level: Int?, delta: Double?, ageDays: Int?, ring: Bitmap, spark: Bitmap, hasCurve: Boolean) {
    val size = WK.size(LocalSize.current)
    val hue = HealthHues.Fitness
    val value = v?.let { formatNumber(it, 1) } ?: "—"
    val levelText = level?.let { Vo2.levelLabel(it) }
    val deltaText = delta?.takeIf { kotlin.math.abs(it) >= 0.1 }?.let { (if (it > 0) "+" else "−") + formatNumber(kotlin.math.abs(it), 1) + " " + tr("en 30 j") }
    val sub = when {
        v == null -> tr("Pas encore de mesure")
        ageDays != null && ageDays > 14 -> trf("Mesuré il y a %1\$s j", ageDays)
        else -> listOfNotNull(levelText, deltaText).joinToString(" · ")
    }
    val curve = @Composable { h: Dp, modifier: GlanceModifier ->
        if (hasCurve) Image(ImageProvider(spark), null, modifier.height(h), contentScale = ContentScale.FillBounds)
    }
    WidgetCard(WK.open(ctx, "h/metric/VO2MAX"), hue, padding = if (size == WK.Size.SMALL) 4.dp else 12.dp,
        horizontal = if (size == WK.Size.SMALL) Alignment.CenterHorizontally else Alignment.Start) {
        when (size) {
            WK.Size.SMALL -> Vo2Gauge(ring, v?.let { formatNumber(it, 0) } ?: "—", hue, 50.dp)
            WK.Size.MEDIUM -> {
                WidgetHeader(R.drawable.ic_w_flame, hue, "VO₂ max")
                Spacer(GlanceModifier.height(4.dp))
                WidgetValue(value, null, hue, 26)
                Text(sub, style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                curve(30.dp, GlanceModifier.fillMaxWidth())
            }
            WK.Size.STRIP -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Vo2Gauge(ring, v?.let { formatNumber(it, 0) } ?: "—", hue, 42.dp)
                Spacer(GlanceModifier.width(10.dp))
                Column {
                    Text("VO₂ max", style = TextStyle(color = WK.on(hue), fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    Text(levelText ?: sub, style = TextStyle(color = WK.text, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                Spacer(GlanceModifier.width(12.dp))
                curve(30.dp, GlanceModifier.defaultWeight())
            }
            WK.Size.LARGE -> {
                WidgetHeader(R.drawable.ic_w_flame, hue, "VO₂ max", trailing = tr("90 jours"))
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    WidgetValue(value, "ml/kg/min", hue, 28)
                    Spacer(GlanceModifier.defaultWeight())
                    levelText?.let {
                        Box(GlanceModifier.background(WK.tint(hue)).cornerRadius(50.dp).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(it, style = TextStyle(color = WK.on(hue), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        }
                    }
                }
                Text(if (v == null) sub else listOfNotNull(deltaText, ageDays?.takeIf { it > 14 }?.let { trf("Mesuré il y a %1\$s j", it) }).joinToString(" · ")
                    .ifEmpty { tr("Estimé par la montre à l'effort") }, style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                curve(40.dp, GlanceModifier.fillMaxWidth())
            }
        }
    }
}

class Vo2WidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = Vo2Widget() }
