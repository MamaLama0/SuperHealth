package app.superhealth.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.superhealth.SuperPrefs
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.Scores
import app.superhealth.ui.dashboard.chargeColor
import app.superhealth.ui.formatDuration
import app.superhealth.ui.formatNumber
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.time.LocalDate
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind

private val EffortBlue = Color(0xFF3F8CFF)
private val Gold = Color(0xFFF2A93B)

/** Jauge en arc (270°), comme sur le tableau de bord. */
private fun gauge(fraction: Float?, color: Color, px: Int): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val s = px * 0.1f
    val box = RectF(s / 2, s / 2, px - s / 2, px - s / 2)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s; strokeCap = Paint.Cap.ROUND; this.color = color.copy(alpha = 0.22f).toArgb() }
    c.drawArc(box, 135f, 270f, false, p)
    if (fraction != null && fraction > 0f) { p.color = color.toArgb(); c.drawArc(box, 135f, 270f * fraction.coerceAtMost(1f), false, p) }
    return bmp
}

@Composable
private fun GaugeBox(bmp: Bitmap, value: String, unit: String?, color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(ImageProvider(bmp), null, GlanceModifier.size(size))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = TextStyle(color = WK.on(color), fontSize = (size.value * 0.26f).sp, fontWeight = FontWeight.Bold), maxLines = 1)
            unit?.let { Text(it, style = TextStyle(color = WK.sub, fontSize = 9.sp), maxLines = 1) }
        }
    }
}

/** Charge et effort, à la manière de WHOOP : récupération du matin et charge cardiovasculaire du jour. */
class ChargeEffortWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WK.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val charge = runCatching { Scores.chargeOf(context, today) }.getOrNull()
        val effort = runCatching { Scores.effortOf(context, today) }.getOrNull()
        val px = WK.px(context, 80.dp)
        val cCol = charge?.score?.let(::chargeColor) ?: Color(0xFF2FBF71)
        val g1 = gauge(charge?.score?.div(100f), cCol, px)
        val g2 = gauge(effort?.div(100)?.toFloat(), EffortBlue, px)
        provideContent {
            val size = WK.size(LocalSize.current)
            val cv = charge?.score?.toString() ?: "—"
            val ev = effort?.let { formatNumber(it) } ?: "—"
            WidgetCard(WK.open(context, "h/metric/RECOVERY"), null, padding = if (size == WK.Size.SMALL) 6.dp else 12.dp,
                horizontal = Alignment.CenterHorizontally) {
                when (size) {
                    WK.Size.SMALL -> GaugeBox(g1, cv, "%", cCol, 48.dp)
                    WK.Size.STRIP -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        WidgetChip(R.drawable.ic_w_battery, cCol, 30.dp, onTint = false); Spacer(GlanceModifier.width(8.dp))
                        WidgetValue(cv, "%", cCol, 18); Spacer(GlanceModifier.defaultWeight())
                        WidgetChip(R.drawable.ic_w_bolt, EffortBlue, 30.dp, onTint = false); Spacer(GlanceModifier.width(8.dp))
                        WidgetValue(ev, null, EffortBlue, 18)
                    }
                    else -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                        val s = if (size == WK.Size.LARGE) 88.dp else 52.dp
                        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            GaugeBox(g1, cv, if (size == WK.Size.LARGE) "%" else null, cCol, s)
                            if (size == WK.Size.LARGE) Text(tr("Charge"), style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
                        }
                        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            GaugeBox(g2, ev, if (size == WK.Size.LARGE) "/ 100" else null, EffortBlue, s)
                            if (size == WK.Size.LARGE) Text(tr("Effort"), style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

class ChargeEffortWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ChargeEffortWidget() }

/** Hypnogramme d'une nuit en image (éveil en haut, profond en bas). */
private fun hypnogram(night: List<HealthRepository.SleepPhase>, w: Int, h: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    if (night.isEmpty()) return bmp
    val c = android.graphics.Canvas(bmp)
    val t0 = night.first().start; val t1 = night.last().end
    val row = h / 4f; val bh = row * 0.62f
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    night.forEach { s ->
        val r = when (s.kind) { ActivityKind.AWAKE_SLEEP -> 0; ActivityKind.REM_SLEEP -> 1; ActivityKind.DEEP_SLEEP -> 3; else -> 2 }
        p.color = when (s.kind) {
            ActivityKind.DEEP_SLEEP -> 0xFF4B3FC2.toInt(); ActivityKind.REM_SLEEP -> 0xFF59B4F5.toInt(); ActivityKind.AWAKE_SLEEP -> 0xFFF08A5D.toInt(); else -> 0xFF9B8AF2.toInt()
        }
        val x0 = w * (s.start - t0) / (t1 - t0).toFloat(); val x1 = w * (s.end - t0) / (t1 - t0).toFloat()
        c.drawRoundRect(RectF(x0, row * r + (row - bh) / 2, maxOf(x1, x0 + 2), row * r + (row + bh) / 2), bh / 2, bh / 2, p)
    }
    return bmp
}

/** Sommeil de la nuit : durée, heures, hypnogramme, dette. */
class SleepWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WK.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val night = HealthRepository.sleepNight(LocalDate.now()).orEmpty()
        val debt = runCatching { Scores.sleepDebtOf(context, LocalDate.now()) }.getOrNull()
        val asleep = night.filter { it.kind != ActivityKind.AWAKE_SLEEP }.sumOf { it.end - it.start } / 60_000.0
        val hypno = hypnogram(night, WK.px(context, 240.dp), WK.px(context, 50.dp))
        val ring = WK.ring((asleep / Scores.sleepNeed()).toFloat(), HealthHues.Sleep, WK.px(context, 72.dp))
        provideContent {
            val size = WK.size(LocalSize.current)
            val hue = HealthHues.Sleep
            val v = if (night.isEmpty()) "—" else formatDuration(asleep)
            WidgetCard(WK.open(context, "h/metric/SLEEP"), hue, padding = if (size == WK.Size.SMALL) 6.dp else 12.dp,
                horizontal = if (size == WK.Size.SMALL) Alignment.CenterHorizontally else Alignment.Start) {
                when (size) {
                    WK.Size.SMALL -> Box(GlanceModifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Image(ImageProvider(ring), null, GlanceModifier.size(48.dp))
                        Text(v, style = TextStyle(color = WK.on(hue), fontSize = 10.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                    WK.Size.STRIP -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        WidgetChip(R.drawable.ic_w_moon, hue, 30.dp); Spacer(GlanceModifier.width(8.dp))
                        WidgetValue(v, null, hue, 18); Spacer(GlanceModifier.width(10.dp))
                        Image(ImageProvider(hypno), null, GlanceModifier.defaultWeight().height(30.dp))
                    }
                    else -> {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            WidgetChip(R.drawable.ic_w_moon, hue, 28.dp); Spacer(GlanceModifier.width(8.dp))
                            WidgetValue(v, null, hue, 20)
                        }
                        Spacer(GlanceModifier.defaultWeight())
                        if (night.isNotEmpty()) Image(ImageProvider(hypno), null, GlanceModifier.fillMaxWidth().height(if (size == WK.Size.LARGE) 50.dp else 34.dp))
                        if (size == WK.Size.LARGE) Text(listOfNotNull(
                            night.firstOrNull()?.let { app.superhealth.ui.timeOf(it.start) + " – " + app.superhealth.ui.timeOf(night.last().end) },
                            debt?.let { if (it <= 0) tr("Aucune dette de sommeil") else trf("Dette %1\$s", formatDuration(it)) }).joinToString(" · "),
                            style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}

class SleepWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SleepWidget() }
