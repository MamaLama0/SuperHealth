package app.superhealth.ui.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.superhealth.MainActivity
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.Metric
import app.superhealth.nutrition.NutriApp
import app.superhealth.ui.dashboard.Rings
import app.superhealth.ui.formatNumber
import app.superhealth.ui.tr
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Anneaux du jour sur l'écran d'accueil (pas, Body Battery, calories mangées), dessinés en image : Glance ne trace pas d'arcs. */
class RingsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rings = runCatching { load(context) }.getOrNull()
        val bmp = renderRings(rings ?: Rings.build(Rings.defaults, { null }, { 0.0 }), 360)
        provideContent { Content(context, rings, bmp) }
    }

    private suspend fun load(ctx: Context): List<Rings.Ring> {
        val today = LocalDate.now()
        val repo = NutriApp.repository
        val s = repo.prefs.settings.value
        val target = repo.analytics().first().targetFor(today.toEpochDay()).macros
        val entries = repo.entriesForDay(today.toEpochDay()).first()
        val kinds = Rings.kinds(app.superhealth.SuperPrefs.settings.value.rings)
        suspend fun metric(m: Metric) = if (Bridge.hasSource(m)) runCatching { Bridge.healthValue(ctx, m, today) }.getOrNull() else null
        val cardio = if (Rings.Kind.CARDIO in kinds && Bridge.hasSource(Metric.CARDIO_WEEK))
            runCatching { app.superhealth.health.data.HealthRepository.cardioWeek(today) }.getOrNull() else null
        val values = kinds.associateWith { k ->
            when (k) {
                Rings.Kind.STEPS -> runCatching { Bridge.steps(ctx, today) }.getOrNull()
                Rings.Kind.ENERGY -> metric(Metric.BODY_ENERGY)
                Rings.Kind.KCAL -> entries.takeIf { it.isNotEmpty() }?.sumOf { it.kcal }
                Rings.Kind.PROTEIN -> entries.takeIf { it.isNotEmpty() }?.sumOf { it.protein }
                Rings.Kind.WATER -> metric(Metric.HYDRATION)
                Rings.Kind.SLEEP -> metric(Metric.SLEEP)
                Rings.Kind.CARDIO -> cardio?.total?.toDouble()
            }
        }
        return Rings.build(kinds, { values[it] }, { app.superhealth.ui.dashboard.ringGoal(it, s.stepGoal, target.kcal, target.protein, cardio?.target) })
    }
}

private val bg = ColorProvider(Color(0xFFFFFFFF), Color(0xFF000000))
private val text = ColorProvider(Color(0xFF111111), Color(0xFFF2F2F2))
private val sub = ColorProvider(Color(0xFF6E6E73), Color(0xFFA1A1A6))

private fun open(ctx: Context, route: String): Action =
    actionStartActivity(Intent(ctx, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW).setData(Uri.parse("superhealth://open/$route"))
        .putExtra(MainActivity.EXTRA_ROUTE, route)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))

@Composable
private fun Content(ctx: Context, rings: List<Rings.Ring>?, bmp: Bitmap) {
    val wide = LocalSize.current.width >= 250.dp
    Row(GlanceModifier.fillMaxSize().background(bg).cornerRadius(24.dp).padding(12.dp).clickable(open(ctx, "home")),
        verticalAlignment = Alignment.CenterVertically, horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally) {
        val side = minOf(LocalSize.current.height - 24.dp, if (wide) 130.dp else LocalSize.current.width - 24.dp)
        Image(ImageProvider(bmp), tr("Anneaux du jour"), GlanceModifier.size(side))
        if (wide && rings != null) {
            Spacer(GlanceModifier.width(14.dp))
            Column(GlanceModifier.defaultWeight()) {
                rings.forEachIndexed { i, r ->
                    if (i > 0) Spacer(GlanceModifier.height(if (rings.size > 3) 1.dp else 4.dp))
                    Text(tr(r.kind.label), style = TextStyle(color = sub, fontSize = 11.sp), maxLines = 1)
                    Text("${r.value?.let { formatNumber(it) } ?: "—"} / ${formatNumber(r.goal)}",
                        style = TextStyle(color = ColorProvider(r.kind.color, r.kind.color), fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }
}

/** Les anneaux en image carrée de [px] pixels, même géométrie que la carte du tableau de bord. */
internal fun renderRings(rings: List<Rings.Ring>, px: Int): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val n = rings.size.coerceAtLeast(1)
    val stroke = px * 0.30f / n
    val gap = stroke * 0.12f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
    c.rotate(-90f, px / 2f, px / 2f)
    rings.forEachIndexed { i, r ->
        val inset = stroke / 2 + i * (stroke + gap)
        val box = RectF(inset, inset, px - inset, px - inset)
        val col = r.kind.color
        paint.shader = null; paint.color = col.copy(alpha = 0.22f).toArgb()
        c.drawArc(box, 0f, 360f, false, paint)
        val p = r.progress
        if (p <= 0.001f) return@forEachIndexed
        paint.shader = SweepGradient(px / 2f, px / 2f, intArrayOf(Rings.shade(col).toArgb(), col.toArgb(), col.toArgb()), floatArrayOf(0f, 0.5f, 1f))
        c.drawArc(box, 0f, p.coerceAtMost(1f) * 360f, false, paint)
        if (p > 1f) { paint.shader = null; paint.color = col.toArgb(); c.drawArc(box, 0f, (p - 1f) * 360f, false, paint) }
    }
    return bmp
}

class RingsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RingsWidget() }
