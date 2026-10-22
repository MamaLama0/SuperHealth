package app.superhealth.nutrition.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.unit.ColorProvider as UnitColorProvider
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
import androidx.compose.ui.unit.DpSize
import app.superhealth.MainActivity
import app.superhealth.nutrition.NutriApp
import nodomain.freeyourgadget.gadgetbridge.R
import app.superhealth.nutrition.domain.Settings
import app.superhealth.nutrition.ui.SearchMode
import app.superhealth.nutrition.ui.energy
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.tr
import app.superhealth.nutrition.ui.weight
import app.superhealth.ui.theme.Base
import app.superhealth.ui.widget.WK.Size as WShape
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import androidx.compose.runtime.collectAsState
import java.time.LocalDate
import kotlin.math.roundToInt

/** Couleurs des widgets (clair / sombre), reprises de la palette de l'app. */
private object W {
    val bg = ColorProvider(Color(0xFFFFFFFF), Color(0xFF1C1C1E))
    val chip = ColorProvider(Color(0xFFF1F1F3), Color(0xFF2C2C2E))
    val text = ColorProvider(Color(0xFF111111), Color(0xFFF2F2F2))
    val sub = ColorProvider(Color(0xFF6E6E73), Color(0xFFA1A1A6))
    val track = ColorProvider(Color(0xFFE6E6EA), Color(0xFF3A3A3C))
    fun c(color: Color) = ColorProvider(color, color)
}

/** Instantané calculé pour les widgets. */
private data class Snapshot(
    val settings: Settings,
    val onboarded: Boolean,
    val kcal: Double, val kcalTarget: Double,
    val p: Double, val pT: Double, val f: Double, val fT: Double, val c: Double, val cT: Double,
    val trendKg: Double?, val trendDelta7: Double?, val tdee: Double?,
    /** Tendance des 30 derniers jours (null : pas de valeur ce jour-là). */
    val trend30: List<Double?> = emptyList(),
)

private suspend fun snapshot(ctx: Context): Snapshot = snapshots(ctx).first()

/**
 * Instantané en flux : le contenu des widgets suit directement la base. Sans cela, une session Glance encore
 * ouverte recomposait avec l'instantané capturé au départ et la mise à jour n'apparaissait qu'à son expiration.
 */
private fun snapshots(ctx: Context): Flow<Snapshot> {
    val repo = NutriApp.repository
    val today = LocalDate.now().toEpochDay()
    return combine(repo.analytics(), repo.entriesForDay(today), repo.prefs.settings) { an, es, s -> build(s, an, es, today) }
}

private fun build(s: Settings, an: app.superhealth.nutrition.data.Analytics, es: List<app.superhealth.nutrition.data.FoodEntryEntity>, today: Long): Snapshot {
    val t = an.targetFor(today).macros
    val trend = an.currentTrendKg
    val before = an.trendByDay[today - 7]
    return Snapshot(
        s, s.onboarded, es.sumOf { it.kcal }, t.kcal,
        es.sumOf { it.protein }, t.protein, es.sumOf { it.fat }, t.fat, es.sumOf { it.carbs }, t.carbs,
        trend, if (trend != null && before != null) trend - before else null,
        an.expenditure.lastOrNull()?.tdee,
        (today - 29..today).map { an.trendByDay[it] },
    )
}

/** Ouvre l'app sur une route de navigation (gérée par MainActivity / NutriRoot). */
private fun open(ctx: Context, route: String): Action =
    actionStartActivity(Intent(ctx, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW).setData(Uri.parse("labouffe://open/$route"))
        .putExtra(MainActivity.EXTRA_ROUTE, route)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))

/** Widget « Aujourd'hui » : calories et macros restantes + raccourcis de saisie. */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(160.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 180.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = runCatching { snapshot(context) }.getOrNull()
        provideContent { TodayContent(context, live(context, first)) }
    }
}

@Composable
private fun TodayContent(ctx: Context, s: Snapshot?) {
    val size = LocalSize.current
    Column(
        GlanceModifier.fillMaxSize().background(W.bg).cornerRadius(24.dp).padding(14.dp).clickable(open(ctx, "log")),
    ) {
        if (s == null || !s.onboarded) {
            Text("La Bouffe", style = TextStyle(color = W.text, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            Text(tr("Ouvrez l'app pour configurer votre programme."), style = TextStyle(color = W.sub, fontSize = 12.sp))
            return@Column
        }
        val left = s.kcalTarget - s.kcal
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(tr(if (left >= 0) "Restant" else "Dépassement"), style = TextStyle(color = W.sub, fontSize = 12.sp))
                Text(s.settings.energy(kotlin.math.abs(left)), style = TextStyle(color = W.text, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            }
            if (size.width >= 250.dp) {
                Text("${s.kcal.roundToInt()} / ${s.kcalTarget.roundToInt()}", style = TextStyle(color = W.sub, fontSize = 12.sp))
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator((s.kcal / s.kcalTarget.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f),
            GlanceModifier.fillMaxWidth().height(6.dp), color = W.c(Base.Blue), backgroundColor = W.track)
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            Macro(tr("Protéines"), s.p, s.pT, Base.Red, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            Macro(tr("Lipides"), s.f, s.fT, Base.Yellow, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            Macro(tr("Glucides"), s.c, s.cT, Base.Green, GlanceModifier.defaultWeight())
        }
        if (size.height >= 180.dp) {
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth()) {
                Shortcut(ctx, R.drawable.ic_w_search, "search/${SearchMode.SEARCH}/-1", GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(8.dp))
                Shortcut(ctx, R.drawable.ic_w_scan, "search/${SearchMode.SCAN}/-1", GlanceModifier.defaultWeight())
                if (s.settings.aiEnabled) {
                    Spacer(GlanceModifier.width(8.dp))
                    Shortcut(ctx, R.drawable.ic_w_ai, "ai", GlanceModifier.defaultWeight())
                }
                Spacer(GlanceModifier.width(8.dp))
                Shortcut(ctx, R.drawable.ic_w_scale, "weight", GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun Macro(label: String, v: Double, target: Double, color: Color, modifier: GlanceModifier) {
    Column(modifier) {
        Text("${v.roundToInt()} / ${target.roundToInt()} g", style = TextStyle(color = W.text, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        Spacer(GlanceModifier.height(3.dp))
        LinearProgressIndicator((v / target.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f),
            GlanceModifier.fillMaxWidth().height(4.dp), color = W.c(color), backgroundColor = W.track)
        Text(label, style = TextStyle(color = W.sub, fontSize = 10.sp), maxLines = 1)
    }
}

@Composable
private fun Shortcut(ctx: Context, icon: Int, route: String, modifier: GlanceModifier) {
    Box(modifier.height(40.dp).background(W.chip).cornerRadius(20.dp).clickable(open(ctx, route)), contentAlignment = Alignment.Center) {
        Image(ImageProvider(icon), null, GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(W.text))
    }
}

/** Widget « Poids » : tendance, variation sur 7 jours et courbe sur 30 jours (selon la taille) ; touche = saisir une pesée. */
class WeightWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(app.superhealth.ui.widget.WK.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = runCatching { snapshot(context) }.getOrNull()
        provideContent { WeightContent(context, live(context, first)) }
    }
}

@Composable
private fun WeightContent(ctx: Context, snap: Snapshot?) {
    val K = app.superhealth.ui.widget.WK
    val size = K.size(LocalSize.current)
    val hue = Base.Pink
    val kg = snap?.trendKg?.let { snap.settings.weight(it) }
    val number = kg?.substringBeforeLast(" ") ?: "—"
    val unit = kg?.substringAfterLast(" ", "")?.takeIf { it.isNotBlank() }
    val delta = snap?.trendDelta7?.let { d -> (if (d >= 0) "+" else "−") + snap.settings.weight(kotlin.math.abs(d)) + " " + tr("sur 7 j") }
    val spark = androidx.compose.runtime.remember(snap?.trend30) {
        snap?.trend30?.takeIf { t -> t.count { it != null } >= 2 }?.let { t -> K.spark(t, hue, K.px(ctx, 240.dp), K.px(ctx, 70.dp)) }
    }
    val curve = @Composable { h: androidx.compose.ui.unit.Dp, modifier: GlanceModifier ->
        if (spark != null) Image(ImageProvider(spark), null, modifier.height(h), contentScale = androidx.glance.layout.ContentScale.FillBounds)
    }
    val add = @Composable { s: androidx.compose.ui.unit.Dp ->
        Box(GlanceModifier.size(s).background(K.chipOn).cornerRadius(s / 2), contentAlignment = Alignment.Center) {
            Image(ImageProvider(R.drawable.ic_w_add), null, GlanceModifier.size(s * 0.56f), colorFilter = ColorFilter.tint(K.on(hue)))
        }
    }
    app.superhealth.ui.widget.WidgetCard(open(ctx, "weight"), hue, padding = if (size == WShape.SMALL) 6.dp else 12.dp,
        horizontal = if (size == WShape.SMALL) Alignment.CenterHorizontally else Alignment.Start) {
        when (size) {
            // petit : le symbole et la tendance, sans unité
            WShape.SMALL -> {
                app.superhealth.ui.widget.WidgetChip(R.drawable.ic_w_scale, hue, 24.dp)
                Text(number, style = TextStyle(color = K.on(hue), fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            // moyen : la tendance et sa courbe sur 30 jours
            WShape.MEDIUM -> {
                app.superhealth.ui.widget.WidgetChip(R.drawable.ic_w_scale, hue, 28.dp)
                Spacer(GlanceModifier.height(6.dp))
                app.superhealth.ui.widget.WidgetValue(number, unit, hue, 20)
                Spacer(GlanceModifier.defaultWeight())
                curve(36.dp, GlanceModifier.fillMaxWidth())
            }
            WShape.STRIP -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                app.superhealth.ui.widget.WidgetChip(R.drawable.ic_w_scale, hue, 32.dp)
                Spacer(GlanceModifier.width(10.dp))
                app.superhealth.ui.widget.WidgetValue(number, unit, hue, 18)
                Spacer(GlanceModifier.width(12.dp))
                Box(GlanceModifier.defaultWeight()) { curve(32.dp, GlanceModifier.fillMaxWidth()) }
                Spacer(GlanceModifier.width(8.dp))
                add(30.dp)
            }
            // grand : le détail (variation sur 7 jours, dépense) et l'ajout d'une pesée
            WShape.LARGE -> {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(tr("Tendance du poids"), style = TextStyle(color = W.sub, fontSize = 12.sp), maxLines = 1)
                        app.superhealth.ui.widget.WidgetValue(number, unit, hue, 22)
                        Text(listOfNotNull(delta, snap?.tdee?.let { tr("Dépense") + " " + snap.settings.energy(it) }).joinToString(" · "),
                            style = TextStyle(color = W.sub, fontSize = 11.sp), maxLines = 1)
                    }
                    add(34.dp)
                }
                Spacer(GlanceModifier.defaultWeight())
                curve(34.dp, GlanceModifier.fillMaxWidth())
            }
        }
    }
}

/** Données à jour pendant toute la vie de la session Glance. */
@Composable
private fun live(ctx: Context, first: Snapshot?): Snapshot? {
    val flow = androidx.compose.runtime.remember { snapshots(ctx).catch { } }
    return flow.collectAsState(first).value
}

/**
 * Petit widget « Calories et macros » : calories restantes et barre, plus protéines / lipides / glucides restants
 * dès que la place le permet (à droite en 2×1 large, en dessous en 2×2, en barres seules en 1×2) ; touche = journal.
 */
class KcalWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(
        DpSize(57.dp, 57.dp), DpSize(110.dp, 40.dp), DpSize(200.dp, 40.dp), DpSize(110.dp, 90.dp), DpSize(57.dp, 110.dp),
    ))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = runCatching { snapshot(context) }.getOrNull()
        provideContent { KcalContent(context, live(context, first)) }
    }
}

@Composable
private fun KcalContent(ctx: Context, s: Snapshot?) {
    val size = LocalSize.current
    val tall = size.height > size.width
    Column(GlanceModifier.fillMaxSize().background(W.bg).cornerRadius(20.dp).padding(horizontal = 12.dp, vertical = 8.dp)
        .clickable(open(ctx, "log")), verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = if (tall) Alignment.CenterHorizontally else Alignment.Start) {
        if (s == null || !s.onboarded) {
            Text("La Bouffe", style = TextStyle(color = W.text, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            return@Column
        }
        val left = s.kcalTarget - s.kcal
        val value = s.settings.energyValue(kotlin.math.abs(left)).roundToInt().toString()
        val unit = if (s.settings.energyUnit == app.superhealth.nutrition.domain.EnergyUnit.KJ) "kJ" else "kcal"
        val kcalColor = if (left >= 0) W.text else W.c(Base.Red)
        val macros = listOf(Triple(tr("P"), s.p to s.pT, Base.Red), Triple(tr("L"), s.f to s.fT, Base.Yellow), Triple(tr("G"), s.c to s.cT, Base.Green))
        when {
            tall -> {
                Text(value, style = TextStyle(color = kcalColor, fontSize = 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(unit + " · " + tr(if (left >= 0) "restantes" else "en trop"), style = TextStyle(color = W.sub, fontSize = 10.sp), maxLines = 1)
                Spacer(GlanceModifier.height(6.dp))
                KcalBar(s, left)
                if (size.height >= 110.dp) {
                    Spacer(GlanceModifier.height(8.dp))
                    // sous-colonne : Glance refuse plus de 10 éléments dans un même conteneur
                    Column(GlanceModifier.fillMaxWidth()) {
                        macros.forEach { (l, vt, c) -> MiniMacro(l, vt.first, vt.second, c, GlanceModifier.fillMaxWidth(), compact = true); Spacer(GlanceModifier.height(4.dp)) }
                    }
                }
            }
            size.height < 90.dp && size.width >= 200.dp -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    KcalLine(value, unit, left, kcalColor)
                    Spacer(GlanceModifier.height(4.dp))
                    KcalBar(s, left)
                }
                macros.forEach { (l, vt, c) -> Spacer(GlanceModifier.width(10.dp)); MiniMacro(l, vt.first, vt.second, c, GlanceModifier.width(40.dp)) }
            }
            else -> {
                KcalLine(value, unit, left, kcalColor)
                Spacer(GlanceModifier.height(4.dp))
                KcalBar(s, left)
                if (size.height >= 90.dp) {
                    Spacer(GlanceModifier.height(10.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        macros.forEachIndexed { i, (l, vt, c) ->
                            if (i > 0) Spacer(GlanceModifier.width(8.dp))
                            MiniMacro(l, vt.first, vt.second, c, GlanceModifier.defaultWeight())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KcalLine(value: String, unit: String, left: Double, color: UnitColorProvider) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = TextStyle(color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(" $unit " + tr(if (left >= 0) "restantes" else "en trop"), style = TextStyle(color = W.sub, fontSize = 11.sp), maxLines = 1)
    }
}

@Composable
private fun KcalBar(s: Snapshot, left: Double) = LinearProgressIndicator((s.kcal / s.kcalTarget.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f),
    GlanceModifier.fillMaxWidth().height(5.dp), color = W.c(if (left >= 0) Base.Blue else Base.Red), backgroundColor = W.track)

/** Macro restante : lettre colorée, grammes restants (« +n » en dépassement) et barre fine. */
@Composable
private fun MiniMacro(label: String, v: Double, target: Double, color: Color, modifier: GlanceModifier, compact: Boolean = false) {
    val left = (target - v).roundToInt()
    val amount = if (left >= 0) "$left g" else "+${-left} g"
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TextStyle(color = W.c(color), fontSize = if (compact) 10.sp else 11.sp, fontWeight = FontWeight.Bold))
            Text(" $amount", style = TextStyle(color = if (left >= 0) W.text else W.c(Base.Red), fontSize = if (compact) 10.sp else 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
        Spacer(GlanceModifier.height(2.dp))
        LinearProgressIndicator((v / target.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f),
            GlanceModifier.fillMaxWidth().height(3.dp), color = W.c(color), backgroundColor = W.track)
    }
}

class KcalWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = KcalWidget() }
class TodayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TodayWidget() }
class WeightWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = WeightWidget() }

object Widgets {
    /**
     * Mise à jour par identifiants système de chaque récepteur : updateAll() s'appuie sur la table récepteur → widget
     * de Glance, pas encore à jour juste après l'ajout d'un nouveau type de widget (un widget recevait alors le contenu d'un autre).
     */
    suspend fun refresh(ctx: Context) {
        runCatching { app.superhealth.ui.widget.DayWidgets.refresh(ctx) }
        val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
        val glance = androidx.glance.appwidget.GlanceAppWidgetManager(ctx)
        listOf(TodayWidgetReceiver::class.java to TodayWidget(), WeightWidgetReceiver::class.java to WeightWidget(), KcalWidgetReceiver::class.java to KcalWidget())
            .forEach { (receiver, widget) ->
                mgr.getAppWidgetIds(android.content.ComponentName(ctx, receiver)).forEach { id ->
                    runCatching { widget.update(ctx, glance.getGlanceIdBy(id)) }
                }
            }
    }
}
