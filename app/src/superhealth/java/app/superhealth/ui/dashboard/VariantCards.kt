package app.superhealth.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.CardSize
import app.superhealth.CardVariant
import app.superhealth.DashCard
import app.superhealth.Feature
import app.superhealth.SuperPrefs
import app.superhealth.SuperSettings
import app.superhealth.bridge.Bridge
import app.superhealth.feature
import app.superhealth.has
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.Metric
import app.superhealth.measureCards
import app.superhealth.nutrition.data.Analytics
import app.superhealth.nutrition.domain.Macros
import app.superhealth.nutrition.domain.Settings as NutritionSettings
import app.superhealth.nutrition.domain.stepGoalFor
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.macros
import app.superhealth.nutrition.ui.minuteOf
import app.superhealth.nutrition.ui.screens.MealSlot
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.dayLetter
import app.superhealth.ui.formatNumber
import app.superhealth.ui.go
import app.superhealth.ui.theme.CaloriesColor
import app.superhealth.ui.theme.CardRadius
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.LocalCardInk
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.WorkoutBlue
import app.superhealth.ui.theme.dashCardBackground
import app.superhealth.ui.theme.dashChipColors
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.theme.veilOf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.ui.wellbeing.MoodPicker
import app.superhealth.ui.wellbeing.moodColor
import app.superhealth.ui.wellbeing.moodIcon
import app.superhealth.wellbeing.Journal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Cartes 4.2 : d'autres façons de montrer une donnée, choisies carte par carte dans sa fenêtre de réglage (voir CardVariant).
 *   Niveau        : le fond de la carte se remplit jusqu'à la part de l'objectif ; les chiffres restent à l'encre pour la lecture.
 *   Courbe du jour: les pas qui montent vers l'objectif, avec l'estimation du soir.
 *   Fil du jour   : la journée sur 24 h, une piste par donnée (sommeil, pas, pouls, repas, séances).
 * Et deux cartes : « Mes mesures » (plusieurs mesures au choix, l'une sous l'autre) et le Moral dans les quatre tailles.
 */

/** Réglage du contenu d'une carte (les mesures qu'elle montre) : libellé du lien et écran ; null : rien à choisir. */
internal fun DashCard.content(): Pair<String, String>? = when (this) {
    DashCard.VITALS -> "Choisir les constantes" to "vitals/edit"
    DashCard.RINGS -> "Choisir les anneaux" to "rings/edit"
    DashCard.MEASURES -> "Choisir les mesures" to "measures/edit"
    else -> null
}

/** Dessine la carte dans le dessin choisi ; false : le classique s'en charge (aussi pour les tailles que le dessin ne couvre pas). */
@Composable
internal fun VariantCard(c: DashCard, size: CardSize, v: CardVariant, vm: SuperViewModel, nav: NavHostController, h: HealthSnap, an: Analytics,
                         s: NutritionSettings, m: Modifier, tab: (String) -> Unit): Boolean {
    when {
        v == CardVariant.LEVEL && c == DashCard.HYDRATION -> EauNiveau(size, c, vm, h, m) { nav.go("h/metric/HYDRATION") }
        v == CardVariant.LEVEL && c == DashCard.CALORIES -> CaloriesNiveau(size, c, vm, an, s, m) { vm.nutrition.selectedDay.value = an.today; tab("log") }
        v == CardVariant.LEVEL && c == DashCard.CARDIO && size != CardSize.L -> CardioNiveau(size, c, h, m) { nav.go("h/metric/CARDIO_WEEK") }
        v == CardVariant.LEVEL && c == DashCard.STEPS && size != CardSize.L -> PasNiveau(size, c, h, s, m) { nav.go("h/metric/STEPS") }
        v == CardVariant.CURVE && c == DashCard.STEPS && size != CardSize.S -> PasCourbe(size, h, s, m) { nav.go("h/metric/STEPS") }
        v == CardVariant.TRACKS && c == DashCard.DAY_TIMELINE && size == CardSize.L -> DayTracksCard(c, vm, h, s, m, nav)
        else -> return false
    }
    return true
}

// ---- Niveau --------------------------------------------------------------------------------------------------------

private val sub @Composable get() = Mf.colors.textSecondary
private val ink @Composable get() = Mf.colors.text
/** Piste des petites barres posées sur une carte, teintée ou non. */
private val rail @Composable get() = Mf.colors.text.copy(alpha = 0.14f)

private fun pct(f: Float) = "${(f * 100).roundToInt()} %"

/**
 * Niveau : le fond se remplit jusqu'à [f], de bas en haut ([wave] : surface en vague, pour l'eau) ou de gauche à droite
 * ([sideways], bandeau). Deux repères marquent le niveau sur les bords.
 */
@Composable
private fun Modifier.level(hue: Color, f: Float, wave: Boolean, sideways: Boolean): Modifier {
    // carte « Plein » : la couleur est déjà le fond, le niveau s'y lit en plus clair
    val full = LocalCardInk.current != null
    val fill = if (full) lerp(Mf.colors.card, Color.White, 0.2f) else lerp(Mf.colors.card, hue, if (Mf.colors.dark) 0.36f else 0.3f)
    val edge = if (full) Color.White else hue
    val p by animateFloatAsState(f.coerceIn(0f, 1f), label = "level")
    return drawBehind {
        if (p <= 0f) return@drawBehind
        val w = 3.dp.toPx(); val l = 10.dp.toPx(); val r = CornerRadius(w / 2)
        if (sideways) {
            val x = size.width * p
            drawRect(fill, size = Size(x, size.height))
            if (p < 1f) { drawRoundRect(edge, Offset(x - w / 2, 0f), Size(w, l), r); drawRoundRect(edge, Offset(x - w / 2, size.height - l), Size(w, l), r) }
            return@drawBehind
        }
        val y = size.height * (1 - p)
        if (wave && p < 1f) {
            val a = 3.dp.toPx(); val wl = 44.dp.toPx()
            drawPath(Path().apply {
                moveTo(0f, y); var x = 0f
                while (x < size.width) { quadraticTo(x + wl / 4, y - a, x + wl / 2, y); quadraticTo(x + wl * 3 / 4, y + a, x + wl, y); x += wl }
                lineTo(x, size.height); lineTo(0f, size.height); close()
            }, fill)
        } else {
            drawRect(fill, Offset(0f, y), Size(size.width, size.height - y))
            if (p < 1f) { drawRoundRect(edge, Offset(0f, y - w / 2), Size(l, w), r); drawRoundRect(edge, Offset(size.width - l, y - w / 2), Size(l, w), r) }
        }
    }
}

@Composable
private fun LevelTile(hue: Color, f: Float, modifier: Modifier, onClick: () -> Unit, wave: Boolean = false, sideways: Boolean = false, hPad: Dp = 14.dp, vPad: Dp = 12.dp,
                      center: Boolean = false, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.clip(RoundedCornerShape(CardRadius)).dashCardBackground(hue).level(hue, f, wave, sideways).cardClick(onClick).padding(horizontal = hPad, vertical = vPad),
        verticalArrangement = if (center) Arrangement.Center else Arrangement.Top, horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start, content = content)

@Composable
private fun LevelSmall(m: Modifier, onClick: () -> Unit, icon: ImageVector, hue: Color, f: Float, value: String, unit: String?, wave: Boolean = false) =
    LevelTile(hue, f, m.square(), onClick, wave, hPad = 8.dp, vPad = 8.dp, center = true) {
        Icon(icon, null, Modifier.size(16.dp), tint = LocalCardInk.current ?: hue.onContainer())
        Text(value, style = MaterialTheme.typography.titleMedium, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        unit?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }

@Composable
private fun LevelMedium(m: Modifier, onClick: () -> Unit, icon: ImageVector, title: String, hue: Color, f: Float, value: String, unit: String?, caption: String? = null,
                        wave: Boolean = false, visual: @Composable BoxScope.() -> Unit) =
    LevelTile(hue, f, m.fillMaxWidth().heightIn(min = MEDIUM_H), onClick, wave) {
        Head(icon, title, hue, pct(f), ink)
        Spacer(Modifier.height(6.dp))
        V(value, unit, 26.sp, color = ink)
        Box(Modifier.heightIn(min = 18.dp), contentAlignment = Alignment.CenterStart) { if (caption != null) Tq(caption, 12.sp, sub) }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().weight(1f).heightIn(min = 44.dp), contentAlignment = Alignment.BottomStart, content = visual)
    }

@Composable
private fun LevelWide(m: Modifier, onClick: () -> Unit, icon: ImageVector, hue: Color, f: Float, value: String, unit: String?, label: String, rightW: Dp = 112.dp,
                      right: @Composable BoxScope.() -> Unit) {
    val (chipBg, chipTint) = dashChipColors(hue)
    LevelTile(hue, f, m.fillMaxWidth().heightIn(min = 72.dp), onClick, sideways = true, hPad = 12.dp, vPad = 10.dp, center = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(chipBg), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp), tint = chipTint) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { V(value, unit, 20.sp, color = ink); Tq(label, 12.sp, sub, modifier = Modifier.padding(top = 1.dp)) }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(rightW), contentAlignment = Alignment.CenterEnd, content = right)
        }
    }
}

@Composable
private fun LevelLarge(m: Modifier, onClick: () -> Unit, icon: ImageVector, title: String, hue: Color, f: Float, wave: Boolean = false, content: @Composable ColumnScope.() -> Unit) =
    LevelTile(hue, f, m.fillMaxWidth(), onClick, wave, hPad = 16.dp, vPad = 16.dp) { Head(icon, title, hue, pct(f), ink); Spacer(Modifier.height(12.dp)); content() }

@Composable
private fun MiniBar(f: Float, c: Color, modifier: Modifier, h: Dp = 6.dp) {
    val track = rail
    Canvas(modifier.height(h)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (f > 0f) drawRoundRect(c, size = Size(maxOf(size.height, size.width * f.coerceAtMost(1f)), size.height), cornerRadius = r)
    }
}

/** Sept jours en tubes : chacun se remplit jusqu'à sa part de l'objectif, la lettre du jour dessous. */
@Composable
private fun Tubes(days: List<Pair<LocalDate, Double?>>, goal: Double, hue: Color, modifier: Modifier = Modifier, h: Dp = 26.dp) {
    val track = rail; val today = LocalDate.now(); val c = LocalCardInk.current ?: hue
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        days.forEach { (d, v) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.width(9.dp).height(h)) {
                    val r = CornerRadius(size.width / 2)
                    drawRoundRect(track, cornerRadius = r)
                    val p = if (goal > 0 && v != null) (v / goal).toFloat().coerceIn(0f, 1f) else 0f
                    if (p > 0f) { val bh = maxOf(size.width, size.height * p); drawRoundRect(c, Offset(0f, size.height - bh), Size(size.width, bh), r) }
                }
                Tq(dayLetter(d), 11.sp, if (d == today) ink else sub, FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Bouton d'ajout posé sur la carte (un verre d'eau). */
@Composable
private fun AddPill(text: String, hue: Color, onClick: () -> Unit) {
    val haptic = app.superhealth.ui.rememberHaptics()
    val bg = when { LocalCardInk.current != null -> Color.White.copy(alpha = 0.26f); Mf.neutral -> Mf.colors.text; else -> hue }
    val fg = when { LocalCardInk.current != null -> Color.White; Mf.neutral -> Mf.colors.card; bg.luminance() > 0.5f -> Color.Black; else -> Color.White }
    Row(Modifier.clip(CircleShape).background(bg).clickable { haptic.confirm(); onClick() }.padding(start = 9.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Add, null, Modifier.size(15.dp), tint = fg)
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

@Composable
private fun EauNiveau(sz: CardSize, c: DashCard, vm: SuperViewModel, h: HealthSnap, m: Modifier, onClick: () -> Unit) {
    val sh by vm.settings.collectAsState()
    val v = h.today(Metric.HYDRATION) ?: 0.0
    val goal = HealthRepository.goal(Metric.HYDRATION) ?: 2000.0
    val f = if (goal > 0) (v / goal).toFloat() else 0f
    val hue = c.hue(); val icon = c.icon(); val txt = formatNumber(v); val of = "/ ${formatNumber(goal)} ml"
    val glasses = sh.waterSizes.ifEmpty { listOf(250) }
    fun add(ml: Int) = vm.log(Metric.HYDRATION, ml.toDouble())
    when (sz) {
        CardSize.S -> LevelSmall(m, onClick, icon, hue, f, txt, "ml", wave = true)
        CardSize.M -> LevelMedium(m, onClick, icon, tr("Eau"), hue, f, txt, "ml", trf("sur %1\$s", formatNumber(goal)), wave = true) {
            AddPill("${glasses.first()} ml", hue) { add(glasses.first()) }
        }
        CardSize.W -> LevelWide(m, onClick, icon, hue, f, txt, of, tr("Eau") + " · " + pct(f)) { AddPill("${glasses.first()} ml", hue) { add(glasses.first()) } }
        else -> LevelLarge(m, onClick, icon, tr("Hydratation"), hue, f, wave = true) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { V(txt, of, 30.sp, color = ink) }
                Tq(if (v >= goal) tr("Objectif atteint") else trf("reste %1\$s ml", formatNumber(goal - v)), 12.sp, sub)
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Tubes(h.series[Metric.HYDRATION].orEmpty(), goal, hue, Modifier.weight(1f).padding(end = 24.dp), 44.dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) { glasses.take(2).forEach { ml -> AddPill("$ml ml", hue) { add(ml) } } }
            }
        }
    }
}

@Composable
private fun CaloriesNiveau(sz: CardSize, c: DashCard, vm: SuperViewModel, an: Analytics, s: NutritionSettings, m: Modifier, onClick: () -> Unit) {
    val eatenList by vm.nutrition.todayEntries.collectAsState()
    val eaten = remember(eatenList) { eatenList.fold(Macros.ZERO) { acc, e -> acc + e.macros() } }
    val target = an.targetFor(an.today).macros
    val left = target.kcal - eaten.kcal
    val leftTxt = formatNumber(abs(s.energyValue(left))); val word = if (left < 0) tr("en trop") else tr("restantes")
    val f = if (target.kcal > 0) (eaten.kcal / target.kcal).toFloat() else 0f
    val pair = "${formatNumber(s.energyValue(eaten.kcal))} / ${formatNumber(s.energyValue(target.kcal))}"
    val hue = c.hue(); val icon = c.icon()
    val macros = listOf(Triple("Protéines", eaten.protein to target.protein, Sc.prot), Triple("Lipides", eaten.fat to target.fat, Sc.fat), Triple("Glucides", eaten.carbs to target.carbs, Sc.carb))
    fun part(v: Pair<Double, Double>) = if (v.second > 0) (v.first / v.second).toFloat() else 0f
    when (sz) {
        CardSize.S -> LevelSmall(m, onClick, icon, hue, f, leftTxt, word)
        CardSize.M -> LevelMedium(m, onClick, icon, tr("Calories"), hue, f, leftTxt, word, pair) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                macros.forEach { (n, v, col) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tq(tr(n).take(1), 12.sp, col, FontWeight.SemiBold, modifier = Modifier.width(14.dp))
                        MiniBar(part(v), col, Modifier.weight(1f))
                        Tq("${v.first.roundToInt()}", 12.sp, ink, FontWeight.SemiBold, modifier = Modifier.width(34.dp).padding(start = 6.dp))
                    }
                }
            }
        }
        CardSize.W -> LevelWide(m, onClick, icon, hue, f, leftTxt, word, "$pair ${tr(s.energyUnit.label)}", 104.dp) {
            Column(Modifier.width(104.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { macros.forEach { (_, v, col) -> MiniBar(part(v), col, Modifier.fillMaxWidth(), 5.dp) } }
        }
        else -> LevelLarge(m, onClick, icon, tr("Calories"), hue, f) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { V(leftTxt, word, 30.sp, color = ink) }
                Tq("$pair ${tr(s.energyUnit.label)}", 12.sp, sub)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                macros.forEach { (n, v, col) ->
                    Column(Modifier.weight(1f)) {
                        Tq(tr(n), 12.sp, sub)
                        MiniBar(part(v), col, Modifier.fillMaxWidth().padding(vertical = 5.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Tq("${v.first.roundToInt()}", 15.sp, ink, FontWeight.SemiBold, true, Modifier.alignByBaseline())
                            Tq(" / ${v.second.roundToInt()} g", 12.sp, sub, modifier = Modifier.alignByBaseline())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardioNiveau(sz: CardSize, c: DashCard, h: HealthSnap, m: Modifier, onClick: () -> Unit) {
    val cw = h.cardio
    val total = cw?.total ?: 0; val target = cw?.target ?: 150
    val f = cw?.progress ?: 0f
    val hue = c.hue(); val icon = c.icon(); val of = "/ $target min"
    val today = cw?.let { trf("+%1\$s aujourd'hui", it.today) }
    val rest = if (total >= target) tr("Objectif atteint") else trf("reste %1\$s min", target - total)
    when (sz) {
        CardSize.S -> LevelSmall(m, onClick, icon, hue, f, cw?.total?.toString() ?: "—", of)
        CardSize.M -> LevelMedium(m, onClick, icon, tr("Cardio"), hue, f, cw?.total?.toString() ?: "—", of, today) { Tq(rest, 13.sp, ink, FontWeight.SemiBold) }
        else -> LevelWide(m, onClick, icon, hue, f, cw?.total?.toString() ?: "—", of, tr("Minutes cardio") + " · " + pct(f)) {
            Column(horizontalAlignment = Alignment.End) { Tq(rest, 12.sp, ink, FontWeight.SemiBold); if (today != null) Tq(today, 12.sp, sub) }
        }
    }
}

@Composable
private fun PasNiveau(sz: CardSize, c: DashCard, h: HealthSnap, s: NutritionSettings, m: Modifier, onClick: () -> Unit) {
    val v = h.today(Metric.STEPS) ?: 0.0
    val goal = s.stepGoalFor(LocalDate.now()).toDouble()
    val f = if (goal > 0) (v / goal).toFloat() else 0f
    val hue = c.hue(); val icon = c.icon(); val txt = formatNumber(v); val of = "/ ${formatNumber(goal)}"
    val week = h.series[Metric.STEPS].orEmpty()
    when (sz) {
        CardSize.S -> LevelSmall(m, onClick, icon, hue, f, txt, of)
        CardSize.M -> LevelMedium(m, onClick, icon, tr("Pas"), hue, f, txt, of) { Tubes(week, goal, hue, Modifier.fillMaxWidth()) }
        else -> LevelWide(m, onClick, icon, hue, f, txt, of, tr("Pas") + " · " + pct(f), 124.dp) { Tubes(week, goal, hue, Modifier.width(124.dp), 22.dp) }
    }
}

// ---- Courbe du jour ------------------------------------------------------------------------------------------------

/** Pas cumulés heure par heure, l'objectif en pointillés, l'estimation du soir en tirets. */
@Composable
private fun CumulCurve(hourly: List<Double?>, total: Double, goal: Double, now: Float, projected: Double?, hue: Color, modifier: Modifier) {
    val dash = sub; val inner = Mf.colors.card
    val area = veilOf(hue).copy(alpha = if (Mf.colors.dark) 0.26f else 0.18f)
    Canvas(modifier) {
        val top = maxOf(goal, total, projected ?: 0.0).coerceAtLeast(1.0) * 1.12
        fun x(hh: Float) = size.width * (hh / 24f).coerceIn(0f, 1f)
        fun y(v: Double) = (size.height * (1 - v / top)).toFloat()
        val sum = hourly.sumOf { it ?: 0.0 }
        val pts = mutableListOf(Offset(0f, y(0.0)))
        if (sum > 0) {
            // les pas de la journée répartis selon les heures mesurées
            val k = if (total > 0) total / sum else 1.0; var acc = 0.0
            for (i in 0..minOf(23, now.toInt())) { acc += hourly.getOrNull(i) ?: 0.0; pts += Offset(x(minOf(i + 1f, now)), y(acc * k)) }
        } else pts += Offset(x(now), y(total))
        if (goal > 0) drawLine(dash, Offset(0f, y(goal)), Offset(size.width, y(goal)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
        val line = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(Path().apply { addPath(line); lineTo(pts.last().x, size.height); lineTo(0f, size.height); close() }, area)
        drawPath(line, hue, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (projected != null && projected > total) drawLine(hue, pts.last(), Offset(size.width, y(projected)), 2.dp.toPx(), StrokeCap.Round,
            PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())))
        drawCircle(hue, 4.5.dp.toPx(), pts.last()); drawCircle(inner, 2.dp.toPx(), pts.last())
    }
}

@Composable
private fun PasCourbe(sz: CardSize, h: HealthSnap, s: NutritionSettings, m: Modifier, onClick: () -> Unit) {
    val v = h.today(Metric.STEPS) ?: 0.0
    val goal = s.stepGoalFor(LocalDate.now()).toDouble()
    val f = if (goal > 0) (v / goal).toFloat() else 0f
    val hue = Sc.steps.a; val icon = Icons.AutoMirrored.Rounded.ShowChart
    val txt = formatNumber(v); val of = "/ ${formatNumber(goal)}"
    val hourly = remember(h.watch.hourly) { hourlySteps(h.watch.hourly) }
    val now = LocalTime.now().let { it.hour + it.minute / 60f }
    // estimation du soir : ce qui reste d'une journée habituelle (moyenne des jours précédents), au rythme d'une journée de 7 h à 22 h
    val usual = h.series[Metric.STEPS].orEmpty().dropLast(1).mapNotNull { it.second }.filter { it > 0 }.takeIf { it.size >= 3 }?.average()
    val projected = usual?.takeIf { now in 7f..21f && v > 0 }?.let { v + it * (1 - ((now - 7f) / 15f).coerceIn(0f, 1f)) }
    val evening = projected?.let { trf("≈ %1\$s ce soir", formatNumber((it / 100).roundToInt() * 100.0)) }
    when (sz) {
        CardSize.M -> MediumShell(m, onClick, icon, tr("Pas du jour"), hue, pct(f), value = txt, caption = evening ?: trf("sur %1\$s", formatNumber(goal))) {
            CumulCurve(hourly, v, goal, now, projected, hue, Modifier.fillMaxSize())
        }
        CardSize.W -> Wide(m, onClick, icon, hue, evening ?: tr("Pas du jour"), { V(txt, of, 20.sp) }, 112.dp) {
            CumulCurve(hourly, v, goal, now, projected, hue, Modifier.width(112.dp).height(46.dp))
        }
        else -> Large(m, onClick, icon, tr("Pas du jour"), hue, pct(f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { V(txt, of, 30.sp) }
                if (evening != null) Tq(evening, 12.sp, sub)
            }
            Spacer(Modifier.height(12.dp))
            CumulCurve(hourly, v, goal, now, projected, hue, Modifier.fillMaxWidth().height(96.dp))
            DayAxisFull()
        }
    }
}

@Composable
private fun DayAxisFull(modifier: Modifier = Modifier) = Row(modifier.fillMaxWidth().padding(top = 4.dp)) {
    listOf("0 h", "6 h", "12 h", "18 h", "24 h").forEachIndexed { i, l ->
        Text(l, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary)
        if (i < 4) Spacer(Modifier.weight(1f))
    }
}

// ---- Fil du jour ---------------------------------------------------------------------------------------------------

private val VALUE_W = 82.dp

/** Une piste de 24 h : symbole, tracé, valeur ; repères à 6, 12 et 18 h, trait de l'heure en cours. */
@Composable
private fun Track(icon: ImageVector, hue: Color, value: String, now: Float, draw: DrawScope.(x: (Float) -> Float) -> Unit) {
    val grid = Mf.colors.text.copy(alpha = 0.1f); val cursor = Mf.colors.text
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(17.dp), tint = hue)
        Spacer(Modifier.width(8.dp))
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val x = { hh: Float -> size.width * (hh / 24f).coerceIn(0f, 1f) }
            listOf(6f, 12f, 18f).forEach { drawLine(grid, Offset(x(it), 0f), Offset(x(it), size.height), 1.dp.toPx()) }
            draw(x)
            drawLine(cursor, Offset(x(now), 0f), Offset(x(now), size.height), 1.5.dp.toPx())
        }
        Text(value, Modifier.width(VALUE_W), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
    }
}

/** Ma journée en fil : sommeil, pas, pouls, repas et séances sur le même axe de 24 h. Une piste sans source n'est pas montrée. */
@Composable
private fun DayTracksCard(c: DashCard, vm: SuperViewModel, h: HealthSnap, s: NutritionSettings, modifier: Modifier, nav: NavHostController) {
    val sh by vm.settings.collectAsState()
    val refresh by vm.refresh.collectAsState()
    val eaten by vm.nutrition.todayEntries.collectAsState()
    val start = HealthRepository.startMs(LocalDate.now())
    fun hour(ms: Long) = (ms - start) / 3_600_000f
    val time = LocalTime.now(); val now = time.hour + time.minute / 60f
    val acts by produceState(emptyList<Bridge.Activity>(), refresh, start) {
        value = withContext(Dispatchers.IO) { runCatching { Bridge.activities(start) }.getOrDefault(emptyList()) }
    }
    val steps = remember(h.watch.hourly) { hourlySteps(h.watch.hourly) }
    val night = h.watch.lastNight?.takeIf { it.date == LocalDate.now() }
    val heart = h.heartToday
    val meals = remember(eaten, sh.mealEnds) { eaten.groupBy { MealSlot.of(it.minuteOf()) }.values.map { l -> l.minOf { it.minuteOf() } / 60f to l.sumOf { it.kcal } } }
    Tile(c, tr("Ma journée"), modifier, trailing = "%d:%02d".format(time.hour, time.minute), onClick = { nav.go("h/metric/STEPS") }) {
        if (night != null) Track(Icons.Rounded.Bedtime, HealthHues.Sleep, night.asleepMin.roundToInt().let { "${it / 60} h %02d".format(it % 60) }, now) { x ->
            val t = 8.dp.toPx(); val a = x(hour(night.bedMs).coerceAtLeast(0f)); val b = x(hour(night.wakeMs))
            drawRoundRect(HealthHues.Sleep, Offset(a, (size.height - t) / 2), Size((b - a).coerceAtLeast(t), t), CornerRadius(t / 2))
        }
        Track(Icons.AutoMirrored.Rounded.DirectionsWalk, HealthHues.Activity, (h.today(Metric.STEPS) ?: steps.sumOf { it ?: 0.0 }).let { formatNumber(it) }, now) { x ->
            val max = steps.maxOf { it ?: 0.0 }.coerceAtLeast(1.0); val bw = size.width / 24 * 0.7f; val pad = 4.dp.toPx()
            steps.forEachIndexed { i, v ->
                if (v != null && v > 0) { val bh = maxOf(bw, ((size.height - 2 * pad) * v / max).toFloat()); drawRoundRect(HealthHues.Activity, Offset(x(i + 0.15f), size.height - pad - bh), Size(bw, bh), CornerRadius(bw / 2)) }
            }
        }
        if (heart.size >= 2) Track(Icons.Rounded.Favorite, HealthHues.Heart, trf("%1\$s–%2\$s bpm", heart.minOf { it.value }.roundToInt(), heart.maxOf { it.value }.roundToInt()), now) { x ->
            val lo = heart.minOf { it.value } - 3; val hi = heart.maxOf { it.value } + 3; val pad = 4.dp.toPx()
            val p = Path()
            heart.forEachIndexed { i, pt ->
                val o = Offset(x(hour(pt.timestampMs)), pad + (size.height - 2 * pad) * (1 - ((pt.value - lo) / (hi - lo)).toFloat()))
                if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
            }
            drawPath(p, HealthHues.Heart, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (sh.has(Feature.NUTRITION)) Track(Icons.Rounded.Restaurant, CaloriesColor, formatNumber(s.energyValue(eaten.sumOf { it.kcal })) + " " + tr(s.energyUnit.label), now) { x ->
            val max = meals.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
            meals.forEach { (hh, kcal) -> drawCircle(CaloriesColor, (3.dp.toPx() + 3.dp.toPx() * (kcal / max).toFloat()), Offset(x(hh), size.height / 2)) }
        }
        if (sh.has(Feature.TRAINING) || acts.isNotEmpty()) Track(Icons.Rounded.FitnessCenter, WorkoutBlue, if (acts.isEmpty()) "—" else trf("%1\$s min", acts.sumOf { it.minutes }), now) { x ->
            val t = 12.dp.toPx()
            acts.forEach { a ->
                val x0 = x(hour(a.start)); val x1 = x(hour(a.end))
                drawRoundRect(WorkoutBlue, Offset(x0, (size.height - t) / 2), Size((x1 - x0).coerceAtLeast(4.dp.toPx()), t), CornerRadius(3.dp.toPx()))
            }
        }
        DayAxisFull(Modifier.padding(start = 25.dp, end = VALUE_W))
    }
}

// ---- Mes mesures ---------------------------------------------------------------------------------------------------

/** La mesure peut-elle s'afficher : sa partie de l'app est activée et, pour une mesure de montre, une source existe. */
internal fun measureUsable(c: DashCard, sh: SuperSettings): Boolean =
    (c.feature()?.let { sh.has(it) } ?: true) && (!c.watch || hasSource(c)) && (!c.isWatchChart || watchSource(c))

/** Courbe fine d'une ligne de mesure. */
@Composable
private fun MiniLine(values: List<Double?>, hue: Color, modifier: Modifier) {
    val vals = values.dropWhile { it == null }
    Canvas(modifier) {
        val present = vals.filterNotNull(); if (present.size < 2) return@Canvas
        val lo = present.min(); val hi = present.max(); val pad = 3.dp.toPx()
        val pts = vals.withIndex().mapNotNull { (i, v) -> v?.let {
            Offset(pad + (size.width - 2 * pad) * i / (vals.size - 1).coerceAtLeast(1), pad + (size.height - 2 * pad) * (if (hi > lo) ((hi - it) / (hi - lo)).toFloat() else 0.5f)) } }
        drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }, hue, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(hue, 2.6.dp.toPx(), pts.last())
    }
}

/** Le petit graphique d'une mesure : ses jours en barres ou en courbe, sinon sa progression. */
@Composable
private fun MeasureChart(d: CompactData, hue: Color, modifier: Modifier) {
    when {
        d.week != null -> WeekDots(d.week, hue, modifier)
        d.history.count { it != null } >= 2 -> if (d.bars) DayBars(d.history, d.goal, hue, modifier) else MiniLine(d.history, hue, modifier)
        d.progress != null -> Box(modifier, contentAlignment = Alignment.Center) { MiniBar(d.progress, hue, Modifier.fillMaxWidth()) }
        else -> Spacer(modifier)
    }
}

@Composable
private fun MeasureValue(d: CompactData, size: androidx.compose.ui.unit.TextUnit) = Row(verticalAlignment = Alignment.Bottom) {
    Tq(d.value, size, ink, FontWeight.SemiBold, true, Modifier.alignByBaseline())
    if (!d.unit.isNullOrBlank()) Tq(" ${d.unit}", 11.sp, sub, modifier = Modifier.alignByBaseline())
}

/**
 * « Mes mesures » : les mesures choisies (Personnaliser, ou la fenêtre de la carte), l'une sous l'autre, chacune avec sa
 * valeur du jour et ses derniers jours. Quatre en moyen, trois en bandeau, six en grand ; toucher une ligne ouvre sa mesure.
 */
@Composable
internal fun MeasuresCard(c: DashCard, size: CardSize, vm: SuperViewModel, nav: NavHostController, h: HealthSnap, an: Analytics, s: NutritionSettings,
                          modifier: Modifier, tab: (String) -> Unit) {
    val sh by vm.settings.collectAsState()
    val max = when (size) { CardSize.W -> 3; CardSize.L -> 6; else -> 4 }
    val rows = sh.measureCards.filter { measureUsable(it, sh) }.mapNotNull { m -> key(m) { compactOf(m, vm, h, an, s, nav, tab) }?.let { m to it } }.take(max)
    val shape = RoundedCornerShape(CardRadius)
    if (rows.isEmpty()) {
        Tile(c, tr("Mes mesures"), modifier, onClick = { nav.go("measures/edit") }) { Sub(tr("Touchez pour choisir vos mesures.")) }
        return
    }
    when (size) {
        CardSize.W -> Row(modifier.fillMaxWidth().heightIn(min = 72.dp).clip(shape).dashCardBackground(c.hue()).cardClick(null).padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            rows.forEach { (m, d) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).cardClick(d.onClick).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(m.icon(), tr(m.label), Modifier.size(16.dp), tint = m.hue()); Spacer(Modifier.width(5.dp)); MeasureValue(d, 15.sp)
                    }
                    MeasureChart(d, m.hue(), Modifier.fillMaxWidth().padding(top = 5.dp).height(22.dp))
                }
            }
        }
        CardSize.L -> Column(modifier.fillMaxWidth().clip(shape).dashCardBackground(c.hue()).cardClick(null).padding(vertical = 6.dp)) {
            rows.forEachIndexed { i, (m, d) ->
                if (i > 0) Box(Modifier.padding(start = 56.dp, end = 16.dp).fillMaxWidth().height(1.dp).background(Mf.colors.line))
                val (chipBg, chipTint) = dashChipColors(m.hue())
                Row(Modifier.fillMaxWidth().cardClick(d.onClick).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(chipBg), contentAlignment = Alignment.Center) { Icon(m.icon(), null, Modifier.size(17.dp), tint = chipTint) }
                    Spacer(Modifier.width(10.dp))
                    Text(m.short(), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MeasureChart(d, m.hue(), Modifier.width(72.dp).height(26.dp))
                    Column(Modifier.width(92.dp), horizontalAlignment = Alignment.End) {
                        MeasureValue(d, 15.sp)
                        val note = d.progress?.takeIf { d.unit != "%" && !d.value.endsWith("%") }?.let { pct(it) }
                            ?: d.delta(m)?.let { (n, t) -> (if (n < 0) "−" else "+") + t }
                        if (note != null) Tq(note, 11.sp, m.hue().onContainer(), FontWeight.SemiBold)
                    }
                }
            }
        }
        else -> Column(modifier.fillMaxWidth().heightIn(min = MEDIUM_H).clip(shape).dashCardBackground(c.hue()).cardClick(null).padding(vertical = 8.dp)) {
            rows.forEach { (m, d) ->
                Row(Modifier.fillMaxWidth().weight(1f).cardClick(d.onClick).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(m.icon(), tr(m.label), Modifier.size(18.dp), tint = m.hue())
                    Spacer(Modifier.width(8.dp))
                    MeasureChart(d, m.hue(), Modifier.width(46.dp).height(22.dp))
                    Spacer(Modifier.weight(1f))
                    MeasureValue(d, 15.sp)
                }
            }
        }
    }
}

// ---- Moral ---------------------------------------------------------------------------------------------------------

/**
 * Le moral est à demander maintenant et la carte Moral est sur l'accueil : elle prend alors la place du bandeau et propose
 * les cinq visages (posé par le tableau de bord, mêmes règles que le bandeau).
 */
internal var moodDue by mutableStateOf(false)

internal fun skipMood() {
    val today = LocalDate.now().toEpochDay()
    SuperPrefs.update { it.copy(moodSkipDay = today, moodSkips = (it.moodSkips + today).distinct().takeLast(14)) }
}

/** La semaine en cours, un visage coloré par jour noté ; aujourd'hui cerclé. */
@Composable
private fun WeekFaces(days: Map<Long, Journal.Entry>, modifier: Modifier = Modifier, face: Dp = 20.dp) {
    val today = LocalDate.now(); val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val empty = Mf.colors.text.copy(alpha = 0.18f)
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        (0..6).forEach { i ->
            val d = monday.plusDays(i.toLong()); val mood = days[d.toEpochDay()]?.mood
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(face + 5.dp).then(if (d == today) Modifier.border(1.5.dp, Mf.colors.text, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                    if (mood != null) Icon(moodIcon(mood), tr(Journal.MOODS[mood - 1]), Modifier.size(face), tint = moodColor(mood))
                    else Box(Modifier.size(face * 0.4f).clip(CircleShape).background(empty))
                }
                Tq(dayLetter(d), 11.sp, if (d == today) ink else sub, FontWeight.SemiBold, modifier = Modifier.padding(top = 1.dp))
            }
        }
    }
}

/** Les cinq visages à toucher, sans libellé (la carte demande le moral). */
@Composable
private fun FacePicker(modifier: Modifier = Modifier, face: Dp = 26.dp, onPick: (Int) -> Unit) {
    val haptic = app.superhealth.ui.rememberHaptics()
    // cinq parts égales : sur une carte étroite les visages rétrécissent ensemble au lieu d'écraser le dernier
    Row(modifier) {
        (1..5).forEach { mood ->
            Box(Modifier.weight(1f).height(face + 10.dp).clip(CircleShape).clickable { haptic.confirm(); onPick(mood) }, contentAlignment = Alignment.Center) {
                Icon(moodIcon(mood), tr(Journal.MOODS[mood - 1]), Modifier.padding(horizontal = 2.dp).size(face), tint = moodColor(mood))
            }
        }
    }
}

/** Moral : le visage du jour en petit, la semaine en moyen et en bandeau, cinq semaines et le décompte par humeur en grand. */
@Composable
internal fun MoodCard(c: DashCard, size: CardSize, modifier: Modifier, nav: NavHostController) {
    val ctx = LocalContext.current
    val days by Journal.days.collectAsState()
    val today = LocalDate.now(); val key = today.toEpochDay()
    val mood = days[key]?.mood
    val ask = moodDue && mood == null
    val hue = c.ink(); val icon = c.icon()
    val label = mood?.let { tr(Journal.MOODS[it - 1]) }
    val shape = RoundedCornerShape(CardRadius)
    val open: () -> Unit = { if (mood == null) app.superhealth.ui.wellbeing.moodSheet = true else nav.go("journal") }
    val pick: (Int) -> Unit = { m -> Journal.setMood(ctx, m) }
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week = (0..6).mapNotNull { days[monday.plusDays(it.toLong()).toEpochDay()]?.mood }
    val caption = if (week.isEmpty()) tr("Rien de noté cette semaine") else trf("%1\$s bon(s) jour(s) sur %2\$s", week.count { it >= 4 }, week.size)
    when (size) {
        CardSize.S -> Column(modifier.square().clip(shape).dashCardBackground(hue).cardClick(open).padding(8.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Icon(mood?.let { moodIcon(it) } ?: Icons.Rounded.SentimentSatisfied, null, Modifier.size(38.dp), tint = mood?.let { moodColor(it) } ?: if (ask) hue else Mf.colors.textTertiary)
                if (ask) Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(Mf.colors.text))
            }
            Text(label ?: tr(if (ask) "Ça va ?" else "À noter"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0..6).forEach { i ->
                    val m = days[monday.plusDays(i.toLong()).toEpochDay()]?.mood
                    Box(Modifier.size(6.dp).clip(CircleShape).background(m?.let { moodColor(it) } ?: Mf.colors.text.copy(alpha = 0.16f)))
                }
            }
        }
        CardSize.M -> Column(modifier.fillMaxWidth().heightIn(min = MEDIUM_H).clip(shape).dashCardBackground(hue).cardClick(open).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { CardHead(icon, tr("Moral"), hue) }
                if (ask) Icon(Icons.Rounded.Close, tr("Pas aujourd'hui"), Modifier.size(26.dp).clip(CircleShape).clickable { skipMood() }.padding(4.dp), tint = Mf.colors.textTertiary)
            }
            Spacer(Modifier.height(8.dp))
            Tq(if (ask) tr("Comment ça va ?") else label ?: tr("Pas encore noté"), if (ask) 18.sp else 22.sp, ink, FontWeight.SemiBold)
            Tq(if (ask) tr("Touchez un visage") else caption, 12.sp, sub, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            if (ask) FacePicker(Modifier.fillMaxWidth(), 26.dp, pick) else WeekFaces(days, Modifier.fillMaxWidth(), 19.dp)
        }
        CardSize.W -> Row(modifier.fillMaxWidth().heightIn(min = 72.dp).clip(shape).dashCardBackground(hue).cardClick(open).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (ask) {
                Column(Modifier.weight(1f).padding(start = 4.dp)) { Tq(tr("Comment ça va ?"), 16.sp, ink, FontWeight.SemiBold); Tq(tr("Moral du jour"), 12.sp, sub) }
                FacePicker(Modifier.width(170.dp), 24.dp, pick)
            } else {
                val (chipBg, chipTint) = dashChipColors(hue)
                Box(Modifier.size(44.dp).clip(CircleShape).background(chipBg), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp), tint = chipTint) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Tq(label ?: tr("À noter"), 17.sp, ink, FontWeight.SemiBold); Tq(tr("Moral"), 12.sp, sub) }
                WeekFaces(days, Modifier.width(158.dp), 16.dp)
            }
        }
        else -> {
            val start = monday.minusWeeks(4)
            val counts = Journal.counts(start, today)
            val empty = Mf.colors.chip
            Tile(c, tr("Moral"), modifier, trailing = label, onClick = open) {
                if (ask) {
                    Text(tr("Comment ça va ?"), style = MaterialTheme.typography.titleMedium, color = ink)
                    Spacer(Modifier.height(6.dp))
                    MoodPicker(null) { pick(it) }
                    Text(tr("Pas aujourd'hui"), Modifier.align(Alignment.End).clip(CircleShape).clickable { skipMood() }.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium, color = sub)
                } else Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    (5 downTo 1).forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(moodIcon(m), tr(Journal.MOODS[m - 1]), Modifier.size(19.dp), tint = moodColor(m))
                            Tq(" ${counts[m - 1]}", 14.sp, ink, FontWeight.SemiBold, true)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    (0..6).forEach { d ->
                        Text(dayLetter(start.plusDays(d.toLong())), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary, textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    (0..4).forEach { w ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            (0..6).forEach { d ->
                                val day = start.plusDays(w * 7L + d)
                                val m = days[day.toEpochDay()]?.mood
                                val cell = RoundedCornerShape(7.dp)
                                Box(Modifier.weight(1f).height(18.dp).clip(cell)
                                    .background(when { day.isAfter(today) -> Color.Transparent; m != null -> moodColor(m); else -> empty })
                                    .then(if (day == today) Modifier.border(1.5.dp, Mf.colors.text, cell) else Modifier))
                            }
                        }
                    }
                }
            }
        }
    }
}
