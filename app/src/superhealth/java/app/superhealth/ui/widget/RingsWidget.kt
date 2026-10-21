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
import androidx.glance.layout.fillMaxWidth
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
    override val sizeMode = SizeMode.Responsive(WK.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rings = runCatching { loadRings(context) }.getOrNull()
        val bmp = WK.rings(rings ?: Rings.build(Rings.defaults, { null }, { 0.0 }), WK.px(context, 220.dp), context)
        provideContent { Content(context, rings, bmp) }
    }
}

/** Anneaux choisis dans l'app, avec les valeurs et objectifs du jour (partagé par les widgets d'anneaux). */
internal suspend fun loadRings(ctx: Context): List<Rings.Ring> {
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

/** Valeur d'un anneau, sans objectif : le symbole sur le rayon dit de quelle mesure il s'agit. */
private fun value(r: Rings.Ring): String = r.value?.let { fmt(r.kind, it) } ?: "—"

internal fun fmt(k: Rings.Kind, v: Double) = if (k == Rings.Kind.SLEEP) app.superhealth.ui.formatDuration(v) else formatNumber(v)

@Composable
private fun Content(ctx: Context, rings: List<Rings.Ring>?, bmp: Bitmap) {
    val ls = LocalSize.current
    val size = WK.size(ls)
    WidgetCard(WK.open(ctx, "home"), null, padding = if (size == WK.Size.LARGE) 14.dp else 8.dp, horizontal = Alignment.CenterHorizontally) {
        val side = minOf(ls.width, ls.height) - 16.dp
        when {
            // petit : les anneaux seuls, chaque symbole sur son rayon
            rings == null || size == WK.Size.SMALL -> Image(ImageProvider(bmp), tr("Anneaux du jour"), GlanceModifier.size(side))
            // moyen : les anneaux, et les valeurs seules dessous, dans leur couleur
            size == WK.Size.MEDIUM -> {
                Image(ImageProvider(bmp), tr("Anneaux du jour"), GlanceModifier.size(side - 22.dp))
                Spacer(GlanceModifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    rings.forEachIndexed { i, r ->
                        if (i > 0) Spacer(GlanceModifier.width(6.dp))
                        Text(value(r), style = TextStyle(color = WK.on(r.kind.color), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                }
            }
            // bandeau : petits anneaux, puis symbole et valeur de chaque anneau
            size == WK.Size.STRIP -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(bmp), tr("Anneaux du jour"), GlanceModifier.size(side))
                Spacer(GlanceModifier.width(10.dp))
                rings.forEach { r ->
                    Row(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                        Image(ImageProvider(WK.icon(r.kind)), null, GlanceModifier.size(13.dp), colorFilter = androidx.glance.ColorFilter.tint(WK.c(r.kind.color)))
                        Spacer(GlanceModifier.width(3.dp))
                        Text(value(r), style = TextStyle(color = WK.on(r.kind.color), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                }
            }
            // grand : le détail, chaque mesure avec son nom et son objectif
            else -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(bmp), tr("Anneaux du jour"), GlanceModifier.size(minOf(side, ls.width * 0.45f)))
                Spacer(GlanceModifier.width(14.dp))
                Column(GlanceModifier.defaultWeight()) {
                    rings.forEachIndexed { i, r ->
                        if (i > 0) Spacer(GlanceModifier.height(if (rings.size > 3) 2.dp else 5.dp))
                        // quatre anneaux : nom et valeur sur une même ligne pour tenir dans 2 cases de haut
                        if (rings.size <= 3) Text(tr(r.kind.label), style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (rings.size > 3) Text(tr(r.kind.label) + "  ", style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
                            Text(value(r), style = TextStyle(color = WK.on(r.kind.color), fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            Text(" / ${fmt(r.kind, r.goal)}", style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

class RingsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RingsWidget() }
