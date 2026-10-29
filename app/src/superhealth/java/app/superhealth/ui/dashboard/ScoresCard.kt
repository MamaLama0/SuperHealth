package app.superhealth.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.navigation.NavHostController
import app.superhealth.CardSize
import app.superhealth.DashCard
import app.superhealth.ui.go
import app.superhealth.ui.theme.Base
import app.superhealth.ui.theme.CardRadius
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.tr
import app.superhealth.ui.wellbeing.color
import app.superhealth.wellbeing.Form
import kotlin.math.roundToInt

/*
 * Scores du jour : Forme, Charge, Repos et Effort réunis, chacun en anneau, sur une carte « héros » presque noire où
 * les couleurs ressortent (idée reprise de l'écran du jour de NOOP ; dessin et code propres à SuperHealth).
 * Les chiffres montent une fois à l'ouverture, sans boucle ni clignotement.
 */

private val HeroTop = Color(0xFF1A1D24)
private val HeroBottom = Color(0xFF101217)
private val Ink = Color.White
private val Ink2 = Color.White.copy(alpha = 0.62f)

/** Un score de la carte : valeur sur 100, couleur, symbole, route, mot et historique (7 jours). */
private class Score(val label: String, val value: Int?, val color: Color, val icon: ImageVector, val route: String, val word: String?, val history: List<Double?>)

/** Charge par zones, comme une jauge de récupération : basse en rouge, moyenne en jaune, haute en vert. */
private fun chargeColor(v: Int?) = when { v == null -> Base.Green; v < 34 -> Base.Red; v < 67 -> Base.Yellow; else -> Base.Green }

private fun scores(w: WatchSnap, form: Form.Result?): List<Score> {
    val advice = form as? Form.Advice
    return listOf(
        Score(tr("Forme"), advice?.score, advice?.verdict?.color() ?: HealthHues.Mind, androidx.compose.material.icons.Icons.Rounded.Bolt, "form",
            advice?.verdict?.let { tr(it.short) }, emptyList()),
        Score(tr("Charge"), w.charge?.score, chargeColor(w.charge?.score), androidx.compose.material.icons.Icons.Rounded.BatteryChargingFull, "h/metric/RECOVERY",
            w.charge?.score?.let { tr(when { it < 34 -> "Basse"; it < 67 -> "Moyenne"; else -> "Haute" }) }, w.chargeHistory.takeLast(7)),
        Score(tr("Repos"), w.sleepScore?.roundToInt(), HealthHues.Sleep, androidx.compose.material.icons.Icons.Rounded.Bedtime, "h/metric/SLEEP",
            w.sleepScore?.takeIf { w.lastNight != null }?.let { tr(app.superhealth.health.data.Scores.restWord(it, w.lastNight!!.asleepMin, w.needMin ?: app.superhealth.health.data.Scores.sleepNeed()).label) },
            w.restHistory.takeLast(7)),
        Score(tr("Effort"), w.effort?.roundToInt(), Base.Blue, androidx.compose.material.icons.Icons.Rounded.LocalFireDepartment, "h/metric/EFFORT",
            null, w.efforts.takeLast(7)),
    )
}

@Composable
internal fun ScoresCard(c: DashCard, size: CardSize, w: WatchSnap, modifier: Modifier, nav: NavHostController) {
    val form = app.superhealth.ui.wellbeing.rememberForm()
    val all = scores(w, form)
    val hero = Modifier.clip(RoundedCornerShape(CardRadius)).background(Brush.verticalGradient(listOf(HeroTop, HeroBottom)))
        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(CardRadius))
    when (size) {
        // petit : les quatre anneaux imbriqués, de l'extérieur vers l'intérieur
        CardSize.S -> Box(modifier.square().then(hero).cardClick { nav.go("form") }.padding(10.dp), contentAlignment = Alignment.Center) {
            Nested(all, Modifier.fillMaxSize())
        }
        // moyen : grille 2 × 2
        CardSize.M -> Column(modifier.fillMaxWidth().then(hero).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            all.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { s -> ScoreRing(s, 62.dp, Modifier.weight(1f), word = false) { nav.go(s.route) } }
                }
            }
        }
        // bandeau : les quatre en ligne
        CardSize.W -> Row(modifier.fillMaxWidth().then(hero).padding(horizontal = 8.dp, vertical = 10.dp)) {
            all.forEach { s -> ScoreRing(s, 54.dp, Modifier.weight(1f), word = false) { nav.go(s.route) } }
        }
        // grand : titre, les quatre anneaux et leur mot, puis la semaine de chaque score
        else -> Column(modifier.fillMaxWidth().then(hero).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Scores du jour"), style = MaterialTheme.typography.titleSmall, color = Ink, modifier = Modifier.weight(1f))
                Text(tr("7 j"), style = MaterialTheme.typography.labelMedium, color = Ink2)
            }
            Spacer(Modifier.height(14.dp))
            Row { all.forEach { s -> ScoreRing(s, 70.dp, Modifier.weight(1f), word = true) { nav.go(s.route) } } }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                all.forEach { s -> Spark(s.history, s.color, Modifier.weight(1f).height(26.dp)) }
            }
            (form as? Form.Advice)?.let { a ->
                Spacer(Modifier.height(12.dp))
                Text(tr(a.verdict.label), style = MaterialTheme.typography.bodySmall, color = Ink2, maxLines = 2)
            }
        }
    }
}

/** Anneau d'un score : piste sombre, arc en dégradé de sa couleur, nombre qui monte au centre, nom (et mot) dessous. */
@Composable
private fun ScoreRing(s: Score, d: Dp, modifier: Modifier, word: Boolean, onClick: () -> Unit) {
    val fill = remember(s.label) { Animatable(0f) }
    LaunchedEffect(s.value) { fill.animateTo((s.value ?: 0) / 100f, tween(1100, easing = FastOutSlowInEasing)) }
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(d), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = size.minDimension * 0.11f
                val box = Size(size.width - stroke, size.height - stroke)
                val tl = Offset(stroke / 2, stroke / 2)
                drawArc(s.color.copy(alpha = 0.18f), 0f, 360f, false, tl, box, style = Stroke(stroke))
                if (fill.value > 0f) rotate(-90f) {
                    drawArc(Brush.sweepGradient(0f to s.color.copy(alpha = 0.55f), fill.value to s.color), 0f, 360f * fill.value, false, tl, box,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Text(s.value?.let { (fill.value * 100).roundToInt().coerceAtMost(it).toString() } ?: "—",
                color = Ink, fontWeight = FontWeight.Bold, fontSize = (d.value * 0.3f).sp, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(s.icon, null, Modifier.size(12.dp), tint = s.color)
            Spacer(Modifier.width(3.dp))
            Text(s.label, style = MaterialTheme.typography.labelMedium, color = Ink, maxLines = 1)
        }
        if (word) Text(s.word ?: " ", style = MaterialTheme.typography.labelSmall, color = Ink2, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** Petit : quatre anneaux imbriqués, sans texte (chaque couleur dit son score). */
@Composable
private fun Nested(all: List<Score>, modifier: Modifier) {
    val fill = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fill.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    Canvas(modifier.aspectRatio(1f)) {
        val n = all.size
        val stroke = size.minDimension / (2f * n + 1.4f)
        all.forEachIndexed { i, s ->
            val inset = stroke / 2 + i * stroke * 1.18f
            val box = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(s.color.copy(alpha = 0.18f), 0f, 360f, false, tl, box, style = Stroke(stroke))
            val v = (s.value ?: 0) / 100f * fill.value
            if (v > 0f) rotate(-90f) { drawArc(s.color, 0f, 360f * v, false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round)) }
        }
    }
}

/** Semaine d'un score : courbe douce, dernier point marqué. */
@Composable
private fun Spark(values: List<Double?>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val pts = values.withIndex().filter { it.value != null }
        if (pts.size < 2) { drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f); return@Canvas }
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        fun y(v: Double) = (size.height - 3f) - (v / 100.0).toFloat().coerceIn(0f, 1f) * (size.height - 6f)
        val path = Path()
        pts.forEachIndexed { k, p -> val x = p.index * step; if (k == 0) path.moveTo(x, y(p.value!!)) else path.lineTo(x, y(p.value!!)) }
        drawPath(path, color.copy(alpha = 0.9f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        val last = pts.last()
        drawCircle(color, 3.dp.toPx(), Offset(last.index * step, y(last.value!!)))
    }
}
