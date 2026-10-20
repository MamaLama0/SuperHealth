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

private object C {
    val bg = ColorProvider(Color(0xFFFFFFFF), Color(0xFF1C1C1E))
    val text = ColorProvider(Color(0xFF111111), Color(0xFFF2F2F2))
    val sub = ColorProvider(Color(0xFF6E6E73), Color(0xFFA1A1A6))
    val track = ColorProvider(Color(0xFFE6E6EA), Color(0xFF3A3A3C))
    fun c(color: Color) = ColorProvider(color, color)
    val kcal = Color(0xFF2E6BE6)
    val steps = Color(0xFF1E9E6A)
    val lift = Color(0xFF3D5AFE)
}

/** Ma journée : calories restantes, pas et prochaine séance. */
private data class Day(
    val onboarded: Boolean, val kcalLeft: Double, val kcalFrac: Float, val unit: String,
    val steps: Double?, val stepGoal: Int, val workout: String?, val workoutSub: String, val workoutRoute: String,
)

private suspend fun load(ctx: Context): Day {
    val repo = NutriApp.repository
    val today = LocalDate.now()
    val s = repo.prefs.settings.value
    val an = repo.analytics().first()
    val eaten = repo.entriesForDay(today.toEpochDay()).first().sumOf { it.kcal }
    val target = an.targetFor(today.toEpochDay()).macros.kcal
    val steps = runCatching { Bridge.steps(ctx, today) }.getOrNull()
    val st = WorkoutApp.store.state.value
    val program = st.programs.firstOrNull { it.id == st.settings.activeProgramId }
    val day = program?.days?.getOrNull(program.dayIndex)
    val (workout, sub, route) = when {
        st.session != null -> Triple(st.session!!.name, tr("Séance en cours"), "training:session")
        day == null -> Triple(null, tr("Aucun programme actif"), "training:workout")
        day.isRest -> Triple(day.name, tr("Jour de repos"), "training:workout")
        else -> Triple(day.name, trf("%1\$s exercices · touchez pour commencer", day.exercises.size), "training:startnext")
    }
    val kj = s.energyUnit.name == "KJ"
    val k = if (kj) 4.184 else 1.0
    return Day(s.onboarded, (target - eaten) * k, if (target > 0) (eaten / target).toFloat() else 0f, if (kj) "kJ" else "kcal",
        steps, s.stepGoal, workout, sub, route)
}

private fun open(ctx: Context, route: String): Action =
    actionStartActivity(Intent(ctx, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW).setData(Uri.parse("superhealth://open/$route"))
        .putExtra(MainActivity.EXTRA_ROUTE, route)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))

class DayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 110.dp), DpSize(250.dp, 180.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = runCatching { load(context) }.getOrNull()
        provideContent { Content(context, d) }
    }
}

@Composable
private fun Content(ctx: Context, d: Day?) {
    val tall = LocalSize.current.height >= 180.dp
    Column(GlanceModifier.fillMaxSize().background(C.bg).cornerRadius(24.dp).padding(14.dp).clickable(open(ctx, "home"))) {
        if (d == null || !d.onboarded) {
            Text("SuperHealth", style = TextStyle(color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            Text(tr("Ouvrez l'app pour la configurer."), style = TextStyle(color = C.sub, fontSize = 12.sp))
            return@Column
        }
        if (tall) {
            Text(tr("Ma journée"), style = TextStyle(color = C.text, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(8.dp))
        }
        Line(ctx, C.kcal, if (d.kcalLeft >= 0) tr("Calories restantes") else tr("Calories en trop"),
            "${formatNumber(abs(d.kcalLeft))} ${d.unit}", d.kcalFrac, "log")
        Spacer(GlanceModifier.height(if (tall) 10.dp else 6.dp))
        Line(ctx, C.steps, tr("Pas"), d.steps?.let { if (d.stepGoal > 0) "${formatNumber(it)} / ${formatNumber(d.stepGoal.toDouble())}" else formatNumber(it) } ?: "—",
            if (d.stepGoal > 0) ((d.steps ?: 0.0) / d.stepGoal).toFloat() else null, "h/metric/STEPS")
        Spacer(GlanceModifier.height(if (tall) 10.dp else 6.dp))
        Row(GlanceModifier.fillMaxWidth().clickable(open(ctx, d.workoutRoute)), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(C.c(C.lift))) {}
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(d.workout ?: tr("Musculation"), style = TextStyle(color = C.text, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                if (tall) Text(d.workoutSub, style = TextStyle(color = C.sub, fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Line(ctx: Context, color: Color, label: String, value: String, frac: Float?, route: String) {
    Column(GlanceModifier.fillMaxWidth().clickable(open(ctx, route))) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(C.c(color))) {}
            Spacer(GlanceModifier.width(8.dp))
            Text(label, style = TextStyle(color = C.sub, fontSize = 12.sp), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
            Text(value, style = TextStyle(color = C.text, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        if (frac != null) {
            Spacer(GlanceModifier.height(4.dp))
            LinearProgressIndicator(frac.coerceIn(0f, 1f), GlanceModifier.fillMaxWidth().height(6.dp), color = C.c(color), backgroundColor = C.track)
        }
    }
}

class DayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DayWidget() }

object DayWidgets {
    suspend fun refresh(ctx: Context) {
        val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
        val glance = androidx.glance.appwidget.GlanceAppWidgetManager(ctx)
        mgr.getAppWidgetIds(android.content.ComponentName(ctx, DayWidgetReceiver::class.java)).forEach { id ->
            runCatching { DayWidget().update(ctx, glance.getGlanceIdBy(id)) }
        }
    }
}
