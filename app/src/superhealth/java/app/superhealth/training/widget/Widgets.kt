package app.superhealth.training.widget

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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.Generator
import app.superhealth.training.ui.dayLetter
import app.superhealth.training.ui.tr
import app.superhealth.training.ui.trf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private object W {
    val bg = ColorProvider(Color(0xFFFFFFFF), Color(0xFF1C1C1E))
    val chip = ColorProvider(Color(0xFFF1F1F3), Color(0xFF2C2C2E))
    val text = ColorProvider(Color(0xFF111111), Color(0xFFF2F2F2))
    val sub = ColorProvider(Color(0xFF6E6E73), Color(0xFFA1A1A6))
    val strong = ColorProvider(Color(0xFF111111), Color(0xFFF2F2F2))
    val onStrong = ColorProvider(Color(0xFFFFFFFF), Color(0xFF111111))
    val blue = ColorProvider(Color(0xFF276EF1), Color(0xFF276EF1))
}

/** Bleu des séances (WorkoutBlue de l'app) : le widget prend le fond pastel de la carte « Prochaine séance ». */
private val Blue = app.superhealth.ui.theme.WorkoutBlue

private data class Snap(val title: String, val subtitle: String, val action: String, val route: String, val week: Set<Int>, val onboarded: Boolean,
    val progress: Float? = null)

private fun snapshot(ctx: Context): Snap = snapshot(WorkoutApp.store.state.value)

private fun snapshot(st: app.superhealth.training.data.AppState): Snap {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val monday = today.minusDays(today.dayOfWeek.value - 1L)
    val week = st.history.map { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
        .filter { !it.isBefore(monday) }.map { it.dayOfWeek.value }.toSet()
    st.session?.let { s ->
        val done = s.exercises.sumOf { e -> e.sets.count { it.done } }; val total = s.exercises.sumOf { it.sets.size }
        return Snap(s.name, trf("%1\$d/%2\$d séries faites", done, total), tr("Reprendre"), "session", week, st.settings.onboarded,
            done.toFloat() / total.coerceAtLeast(1))
    }
    val p = st.programs.firstOrNull { it.id == st.settings.activeProgramId }
    val day = p?.days?.getOrNull(p.dayIndex)
    return when {
        p == null || day == null -> Snap(tr("Aucun programme actif"), tr("Séance libre ou nouveau programme"), tr("Séance vide"), "startempty", week, st.settings.onboarded)
        day.isRest -> Snap(day.name, tr("Jour de repos"), tr("Ouvrir"), "workout", week, st.settings.onboarded)
        else -> Snap(day.name, trf("%1\$d exercices · ≈ %2\$d min", day.exercises.size, Generator.estimateMinutes(day, p.cycleIndex)),
            tr("Commencer"), "startnext", week, st.settings.onboarded)
    }
}

private fun open(ctx: Context, route: String): Action =
    actionStartActivity(Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("lasalle://open/$route"))
        .putExtra(MainActivity.EXTRA_ROUTE, "training:$route").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))

/** Widget « Prochaine séance » : jour du programme (ou séance en cours), bouton de départ, semaine d'entraînement. */
class NextWorkoutWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(130.dp, 57.dp), DpSize(270.dp, 57.dp), DpSize(180.dp, 110.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = WorkoutApp.store
        provideContent {
            // contenu suivi en direct : une session Glance ouverte ne garde plus un instantané périmé
            val flow = androidx.compose.runtime.remember { store.state.map { runCatching { snapshot(it) }.getOrNull() }.distinctUntilChanged() }
            Content(context, flow.collectAsState(runCatching { snapshot(store.state.value) }.getOrNull()).value)
        }
    }
}

@Composable
private fun Content(ctx: Context, s: Snap?) {
    val wide = LocalSize.current.width >= 250.dp
    if (LocalSize.current.height < 100.dp && s != null && s.onboarded) {
        // 2×1 et 4×1 : nom de la séance et départ d'une touche
        Row(GlanceModifier.fillMaxSize().background(app.superhealth.ui.widget.WK.tint(Blue)).cornerRadius(24.dp).padding(horizontal = 12.dp, vertical = 8.dp).clickable(open(ctx, "workout")),
            verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(s.title, style = TextStyle(color = W.text, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                if (wide) Text(s.subtitle, style = TextStyle(color = W.sub, fontSize = 11.sp), maxLines = 1)
            }
            Box(GlanceModifier.height(32.dp).background(W.strong).cornerRadius(16.dp).padding(horizontal = 12.dp).clickable(open(ctx, s.route)),
                contentAlignment = Alignment.Center) {
                Text(if (wide) s.action else "▶", style = TextStyle(color = W.onStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
        }
        return
    }
    Column(GlanceModifier.fillMaxSize().background(app.superhealth.ui.widget.WK.tint(Blue)).cornerRadius(24.dp).padding(14.dp).clickable(open(ctx, "workout"))) {
        if (s == null || !s.onboarded) {
            Text("La Salle", style = TextStyle(color = W.text, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            Text(tr("Ouvrez l'app pour commencer."), style = TextStyle(color = W.sub, fontSize = 12.sp))
            return@Column
        }
        Text(tr(if (s.route == "session") "Séance en cours" else "Prochaine séance"), style = TextStyle(color = W.sub, fontSize = 12.sp))
        Text(s.title, style = TextStyle(color = app.superhealth.ui.widget.WK.on(Blue), fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(s.subtitle, style = TextStyle(color = W.sub, fontSize = 12.sp), maxLines = 1)
        s.progress?.let {
            Spacer(GlanceModifier.height(8.dp))
            androidx.glance.appwidget.LinearProgressIndicator(it, GlanceModifier.fillMaxWidth().height(6.dp), color = W.blue, backgroundColor = W.chip)
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (wide) Row(GlanceModifier.defaultWeight()) {
                // 7 éléments sans intercalaires : Glance refuse plus de 10 enfants par conteneur
                (1..7).forEach { d ->
                    val done = d in s.week
                    Box(GlanceModifier.padding(end = if (d < 7) 2.dp else 0.dp)) {
                        Box(GlanceModifier.size(16.dp).cornerRadius(8.dp).background(if (done) W.blue else app.superhealth.ui.widget.WK.chipOn), contentAlignment = Alignment.Center) {
                            Text(dayLetter(d), style = TextStyle(color = if (done) ColorProvider(Color.White, Color.White) else W.sub, fontSize = 8.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            } else Spacer(GlanceModifier.defaultWeight())
            Box(GlanceModifier.height(36.dp).background(W.strong).cornerRadius(18.dp).padding(horizontal = 14.dp).clickable(open(ctx, s.route)),
                contentAlignment = Alignment.Center) {
                Text(s.action, style = TextStyle(color = W.onStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

class NextWorkoutWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = NextWorkoutWidget() }

object Widgets {
    /** Par identifiants système (voir La Bouffe : updateAll() dépend d'une table Glance parfois en retard). */
    suspend fun refresh(ctx: Context) {
        runCatching { app.superhealth.ui.widget.DayWidgets.refresh(ctx) }
        val glance = androidx.glance.appwidget.GlanceAppWidgetManager(ctx)
        android.appwidget.AppWidgetManager.getInstance(ctx).getAppWidgetIds(android.content.ComponentName(ctx, NextWorkoutWidgetReceiver::class.java))
            .forEach { id -> runCatching { NextWorkoutWidget().update(ctx, glance.getGlanceIdBy(id)) } }
    }
}
