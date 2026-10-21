package app.superhealth.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.RowScope
import android.graphics.Bitmap
import nodomain.freeyourgadget.gadgetbridge.R
import kotlin.math.roundToInt
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import app.superhealth.nutrition.NutriApp
import app.superhealth.training.WorkoutApp
import app.superhealth.ui.formatNumber
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.abs

/** Ma journée : calories restantes, pas, protéines et prochaine séance. */
private data class Day(
    val onboarded: Boolean, val kcalLeft: Double, val kcalFrac: Float, val unit: String,
    val steps: Double?, val stepGoal: Int, val protein: Double, val proteinGoal: Double,
    val workout: String?, val workoutSub: String, val workoutRoute: String, val action: String?,
)

private suspend fun load(ctx: Context): Day {
    val repo = NutriApp.repository
    val today = LocalDate.now()
    val s = repo.prefs.settings.value
    val an = repo.analytics().first()
    val entries = repo.entriesForDay(today.toEpochDay()).first()
    val eaten = entries.sumOf { it.kcal }
    val target = an.targetFor(today.toEpochDay()).macros
    val steps = runCatching { Bridge.steps(ctx, today) }.getOrNull()
    val st = WorkoutApp.store.state.value
    val program = st.programs.firstOrNull { it.id == st.settings.activeProgramId }
    val day = program?.days?.getOrNull(program.dayIndex)
    data class W(val name: String?, val sub: String, val route: String, val action: String?)
    val w = when {
        st.session != null -> W(st.session!!.name, tr("Séance en cours"), "training:session", tr("Reprendre"))
        day == null -> W(null, tr("Aucun programme actif"), "training:workout", null)
        day.isRest -> W(day.name, tr("Jour de repos"), "training:workout", null)
        else -> W(day.name, trf("%1\$s exercices", day.exercises.size), "training:startnext", tr("Commencer"))
    }
    val kj = s.energyUnit.name == "KJ"
    val k = if (kj) 4.184 else 1.0
    return Day(s.onboarded, (target.kcal - eaten) * k, if (target.kcal > 0) (eaten / target.kcal).toFloat() else 0f, if (kj) "kJ" else "kcal",
        steps, s.stepGoal, entries.sumOf { it.protein }, target.protein, w.name, w.sub, w.route, w.action)
}

class DayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WK.ALL + DpSize(270.dp, 190.dp))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = runCatching { load(context) }.getOrNull()
        val px = WK.px(context, 46.dp)
        val rings = d?.let {
            listOf(WK.ring(it.kcalFrac, CaloriesColor, px), WK.ring(if (it.stepGoal > 0) ((it.steps ?: 0.0) / it.stepGoal).toFloat() else 0f, StepsColor, px),
                WK.ring(if (it.proteinGoal > 0) (it.protein / it.proteinGoal).toFloat() else 0f, ProteinColor, px))
        }
        provideContent { Content(context, d, rings) }
    }
}

private val CaloriesColor = app.superhealth.ui.theme.CaloriesColor
private val StepsColor = app.superhealth.ui.theme.HealthHues.Activity
private val ProteinColor = app.superhealth.ui.theme.ProteinColor

@Composable
private fun Content(ctx: Context, d: Day?, rings: List<Bitmap>?) {
    val size = WK.size(LocalSize.current)
    WidgetCard(WK.open(ctx, "home"), null, padding = if (size == WK.Size.LARGE) 14.dp else 8.dp,
        horizontal = if (size == WK.Size.SMALL) Alignment.CenterHorizontally else Alignment.Start) {
        if (d == null || !d.onboarded || rings == null) {
            Text("SuperHealth", style = TextStyle(color = WK.text, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            return@WidgetCard
        }
        val stats = listOf(
            Triple(rings[0], R.drawable.ic_w_food, CaloriesColor) to (formatNumber(abs(d.kcalLeft)) to (if (d.kcalLeft >= 0) tr("Restantes") else tr("en trop"))),
            Triple(rings[1], R.drawable.ic_w_walk, StepsColor) to ((d.steps?.let { formatNumber(it) } ?: "—") to tr("Pas")),
            Triple(rings[2], R.drawable.ic_w_egg, ProteinColor) to ("${(d.proteinGoal - d.protein).coerceAtLeast(0.0).roundToInt()} g" to tr("Protéines")),
        )
        val routes = listOf("log", "h/metric/STEPS", "log")
        when (size) {
            // petit : calories restantes dans leur anneau, sans texte
            WK.Size.SMALL -> Box(GlanceModifier.fillMaxSize().clickable(WK.open(ctx, "log")), contentAlignment = Alignment.Center) {
                Image(ImageProvider(rings[0]), null, GlanceModifier.fillMaxSize())
                Text(stats[0].second.first, style = TextStyle(color = WK.on(CaloriesColor), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            // moyen : un anneau et sa valeur par ligne
            WK.Size.MEDIUM -> stats.forEachIndexed { i, (art, txt) ->
                if (i > 0) Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth().clickable(WK.open(ctx, routes[i])), verticalAlignment = Alignment.CenterVertically) {
                    Ring(art.first, art.second, art.third, 32.dp)
                    Spacer(GlanceModifier.width(8.dp))
                    Text(txt.first, style = TextStyle(color = WK.on(art.third), fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
            // bandeau : les trois anneaux et leur valeur côte à côte
            WK.Size.STRIP -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                stats.forEachIndexed { i, (art, txt) ->
                    Row(GlanceModifier.defaultWeight().clickable(WK.open(ctx, routes[i])), verticalAlignment = Alignment.CenterVertically) {
                        Ring(art.first, art.second, art.third, 38.dp)
                        Spacer(GlanceModifier.width(6.dp))
                        Text(txt.first, style = TextStyle(color = WK.on(art.third), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                }
            }
            // grand : le détail, avec la séance du jour
            WK.Size.LARGE -> {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    stats.forEachIndexed { i, (art, txt) -> Stat(ctx, art.first, art.second, art.third, txt.first, txt.second, routes[i]) }
                }
                // la séance du jour dès que le widget a la place (plus de 2 cases de haut)
                if (LocalSize.current.height >= 160.dp) { Spacer(GlanceModifier.defaultWeight()); WorkoutRow(ctx, d) }
            }
        }
    }
}

@Composable
private fun Ring(ring: Bitmap, icon: Int, color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(ImageProvider(ring), null, GlanceModifier.size(size))
        Image(ImageProvider(icon), null, GlanceModifier.size(size * 0.4f), colorFilter = ColorFilter.tint(WK.c(color)))
    }
}

@Composable
private fun WorkoutRow(ctx: Context, d: Day) {
    Row(GlanceModifier.fillMaxWidth().background(WK.chip).cornerRadius(18.dp).padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
        .clickable(WK.open(ctx, d.workoutRoute)), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.ic_w_lift), null, GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(WK.c(LiftColor)))
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(d.workout ?: tr("Musculation"), style = TextStyle(color = WK.text, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Text(d.workoutSub, style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
        }
        d.action?.let {
            Box(GlanceModifier.height(30.dp).background(WK.strong).cornerRadius(15.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(it, style = TextStyle(color = WK.onStrong, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

private val LiftColor = app.superhealth.ui.theme.WorkoutBlue

/** Anneau, symbole au centre, valeur et libellé ; touche = écran de la mesure. */
@Composable
private fun RowScope.Stat(ctx: Context, ring: Bitmap, icon: Int, color: Color, value: String, label: String, route: String) {
    Column(GlanceModifier.defaultWeight().clickable(WK.open(ctx, route)), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(GlanceModifier.size(40.dp), contentAlignment = Alignment.Center) {
            Image(ImageProvider(ring), null, GlanceModifier.size(40.dp))
            Image(ImageProvider(icon), null, GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(WK.c(color)))
        }
        Spacer(GlanceModifier.height(2.dp))
        Text(value, style = TextStyle(color = WK.on(color), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(label, style = TextStyle(color = WK.sub, fontSize = 10.sp), maxLines = 1)
    }
}

class DayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DayWidget() }

object DayWidgets {
    suspend fun refresh(ctx: Context) {
        val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
        val glance = androidx.glance.appwidget.GlanceAppWidgetManager(ctx)
        listOf(DayWidgetReceiver::class.java to DayWidget(), RingsWidgetReceiver::class.java to RingsWidget(),
            HydrationWidgetReceiver::class.java to HydrationWidget(), InsightWidgetReceiver::class.java to InsightWidget(),
            VitalsWidgetReceiver::class.java to VitalsWidget(), StepsWidgetReceiver::class.java to StepsWidget(),
            OverviewWidgetReceiver::class.java to OverviewWidget()).forEach { (receiver, widget) ->
            mgr.getAppWidgetIds(android.content.ComponentName(ctx, receiver)).forEach { id ->
                runCatching { widget.update(ctx, glance.getGlanceIdBy(id)) }
            }
        }
    }
}
