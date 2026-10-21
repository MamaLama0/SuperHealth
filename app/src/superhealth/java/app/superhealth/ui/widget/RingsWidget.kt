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

/** Anneaux du jour sur l'écran d'accueil (ceux choisis dans l'app), dessinés en image avec le symbole de chaque mesure. */
class RingsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(130.dp, 190.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rings = runCatching { load(context) }.getOrNull()
        val bmp = WK.rings(rings ?: Rings.build(Rings.defaults, { null }, { 0.0 }), WK.px(context, 150.dp), context)
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

/** Valeur d'un anneau, sans objectif : le symbole sur le rayon dit de quelle mesure il s'agit. */
private fun value(r: Rings.Ring): String = r.value?.let {
    if (r.kind == Rings.Kind.SLEEP) app.superhealth.ui.formatDuration(it) else formatNumber(it)
} ?: "—"

@Composable
private fun Content(ctx: Context, rings: List<Rings.Ring>?, bmp: Bitmap) {
    val size = LocalSize.current
    val wide = size.width >= 200.dp && size.width > size.height * 1.3f
    val tall = !wide && size.height >= 170.dp && size.width >= 110.dp
    val pad = 12.dp
    Column(GlanceModifier.fillMaxSize().background(WK.bg).cornerRadius(WK.RADIUS.dp).padding(pad).clickable(WK.open(ctx, "home")),
        verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            wide && rings != null -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(bmp), tr("Anneaux du jour"), GlanceModifier.size(minOf(size.height - pad * 2, size.width * 0.55f)))
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) {
                    rings.forEachIndexed { i, r ->
                        if (i > 0) Spacer(GlanceModifier.height(if (rings.size > 3) 2.dp else 6.dp))
                        Text(value(r), style = TextStyle(color = WK.c(r.kind.color), fontSize = if (rings.size > 3) 15.sp else 18.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1)
                    }
                }
            }
            tall && rings != null -> {
                Image(ImageProvider(bmp), tr("Anneaux du jour"), GlanceModifier.size(minOf(size.width - pad * 2, size.height - pad * 2 - 30.dp)))
                Spacer(GlanceModifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    rings.forEachIndexed { i, r ->
                        if (i > 0) Spacer(GlanceModifier.width(8.dp))
                        Text(value(r), style = TextStyle(color = WK.c(r.kind.color), fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                }
            }
            else -> Image(ImageProvider(bmp), tr("Anneaux du jour"), GlanceModifier.size(minOf(size.height, size.width) - pad * 2))
        }
    }
}

class RingsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RingsWidget() }
