package app.superhealth.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.dashCardBackground
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.tr
import app.superhealth.ui.wellbeing.color
import app.superhealth.wellbeing.Form
import kotlin.math.roundToInt

/*
 * Scores du jour : Forme, Charge, Repos et Effort réunis sur une carte comme les autres (fond du thème, pastille et titre).
 * La Forme mène, en grand anneau ; les trois autres suivent en lignes courtes (symbole, valeur, barre arrondie).
 * Pistes pleines teintées de la couleur du score (trackOf, comme les anneaux d'activité), jamais du gris transparent.
 * Les chiffres montent une fois à l'ouverture, sans boucle ni clignotement.
 */

/** Un score de la carte : valeur sur 100, couleur, symbole, route et mot. */
private class Score(val label: String, val value: Int?, val color: Color, val icon: ImageVector, val route: String, val word: String?)

/** Charge par zones, comme une jauge de récupération : basse en rouge, moyenne en jaune, haute en vert. */
private fun chargeColor(v: Int?) = when { v == null -> Base.Green; v < 34 -> Base.Red; v < 67 -> Base.Yellow; else -> Base.Green }

private fun scores(w: WatchSnap, form: Form.Result?): List<Score> {
    val advice = form as? Form.Advice
    return listOf(
        Score(tr("Forme"), advice?.score, advice?.verdict?.color() ?: HealthHues.Mind, androidx.compose.material.icons.Icons.Rounded.Bolt, "form",
            advice?.verdict?.let { tr(it.short) } ?: if (form is Form.Missing) tr("À compléter") else null),
        Score(tr("Charge"), w.charge?.score, chargeColor(w.charge?.score), androidx.compose.material.icons.Icons.Rounded.BatteryChargingFull, "h/metric/RECOVERY",
            w.charge?.score?.let { tr(when { it < 34 -> "Basse"; it < 67 -> "Moyenne"; else -> "Haute" }) }),
        Score(tr("Repos"), w.sleepScore?.roundToInt(), HealthHues.Sleep, androidx.compose.material.icons.Icons.Rounded.Bedtime, "h/metric/SLEEP",
            w.sleepScore?.takeIf { w.lastNight != null }?.let { tr(app.superhealth.health.data.Scores.restWord(it, w.lastNight!!.asleepMin, w.needMin ?: app.superhealth.health.data.Scores.sleepNeed()).label) }),
        Score(tr("Effort"), w.effort?.roundToInt(), Base.Blue, androidx.compose.material.icons.Icons.Rounded.LocalFireDepartment, "h/metric/EFFORT", null),
    )
}

/** Valeur animée une fois de 0 à la cible (0..1). */
@Composable
private fun animated(key: String, v: Int?): Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(v) { a.animateTo((v ?: 0) / 100f, tween(1000, easing = FastOutSlowInEasing)) }
    return a.value
}

@Composable
internal fun ScoresCard(c: DashCard, size: CardSize, w: WatchSnap, modifier: Modifier, nav: NavHostController) {
    val form = app.superhealth.ui.wellbeing.rememberForm()
    val all = scores(w, form)
    val lead = all.first(); val rest = all.drop(1)
    val box = Modifier.clip(RoundedCornerShape(CardRadius)).dashCardBackground(c.ink())
    when (size) {
        // petit : les quatre anneaux emboîtés (Forme à l'extérieur), sans texte
        CardSize.S -> Box(modifier.square().then(box).cardClick { nav.go("form") }.padding(10.dp), contentAlignment = Alignment.Center) {
            Nested(all, Modifier.fillMaxSize())
        }
        // moyen : la Forme en grand anneau, les trois autres en barres avec leur symbole
        CardSize.M -> Row(modifier.fillMaxWidth().then(box).cardClick { nav.go("form") }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LeadRing(lead, 92.dp, word = false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { rest.forEach { ScoreBar(it, label = false) { nav.go(it.route) } } }
        }
        // bandeau : quatre anneaux, valeur au centre, symbole dessous
        CardSize.W -> Row(modifier.fillMaxWidth().then(box).padding(horizontal = 8.dp, vertical = 10.dp)) {
            all.forEach { s -> SmallRing(s, 52.dp, Modifier.weight(1f)) { nav.go(s.route) } }
        }
        // grand : en-tête habituel, Forme en grand avec son mot, puis chaque score en ligne avec son mot et sa barre
        else -> Tile(c, tr("Scores du jour"), modifier, onClick = { nav.go("form") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LeadRing(lead, 116.dp, word = true)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { rest.forEach { ScoreBar(it, label = true) { nav.go(it.route) } } }
            }
            (form as? Form.Advice)?.let { a ->
                Spacer(Modifier.height(12.dp))
                Text(tr(a.verdict.label), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 2)
            }
        }
    }
}

/** Anneau plein de la Forme : piste teintée, arc en dégradé le long du trait, valeur et mot au centre. */
@Composable
private fun LeadRing(s: Score, d: Dp, word: Boolean) {
    val f = animated(s.label, s.value)
    val track = trackOf(s.color, Mf.colors.dark)
    Box(Modifier.size(d), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { arc(s.color, track, f, size.minDimension * 0.1f) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(s.icon, null, Modifier.size(d * 0.14f), tint = s.color)
            Text(s.value?.let { (f * 100).roundToInt().coerceAtMost(it).toString() } ?: "—",
                color = Mf.colors.text, fontWeight = FontWeight.Bold, fontSize = (d.value * 0.27f).sp, maxLines = 1)
            if (word) Text(s.word ?: s.label, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1)
        }
    }
}

/** Ligne d'un score : symbole, nom et mot (grand format), valeur ; barre arrondie dessous. */
@Composable
private fun ScoreBar(s: Score, label: Boolean, onClick: () -> Unit) {
    val f = animated(s.label, s.value)
    val track = trackOf(s.color, Mf.colors.dark)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(s.icon, null, Modifier.size(16.dp), tint = s.color)
            if (label) {
                Spacer(Modifier.width(6.dp))
                Text(s.label, style = MaterialTheme.typography.labelLarge, color = Mf.colors.text, maxLines = 1)
                s.word?.let { Text(" · $it", style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, maxLines = 1) }
            }
            Spacer(Modifier.weight(1f))
            Text(s.value?.let { (f * 100).roundToInt().coerceAtMost(it).toString() } ?: "—", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = Mf.colors.text, maxLines = 1)
        }
        Spacer(Modifier.height(5.dp))
        Canvas(Modifier.fillMaxWidth().height(7.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            if (f > 0f) drawRoundRect(Brush.horizontalGradient(listOf(s.color.copy(alpha = 0.7f), s.color), endX = size.width * f),
                size = Size(maxOf(size.height, size.width * f), size.height), cornerRadius = r)
        }
    }
}

/** Petit anneau du bandeau : valeur au centre, symbole dessous. */
@Composable
private fun SmallRing(s: Score, d: Dp, modifier: Modifier, onClick: () -> Unit) {
    val f = animated(s.label, s.value)
    val track = trackOf(s.color, Mf.colors.dark)
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(d), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { arc(s.color, track, f, size.minDimension * 0.12f) }
            Text(s.value?.let { (f * 100).roundToInt().coerceAtMost(it).toString() } ?: "—",
                color = Mf.colors.text, fontWeight = FontWeight.Bold, fontSize = (d.value * 0.3f).sp, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Icon(s.icon, null, Modifier.size(14.dp), tint = s.color)
    }
}

/** Piste pleine puis arc arrondi, dégradé de la couleur le long du trait. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.arc(color: Color, track: Color, f: Float, stroke: Float) {
    val box = Size(size.width - stroke, size.height - stroke)
    val tl = Offset(stroke / 2, stroke / 2)
    drawArc(track, 0f, 360f, false, tl, box, style = Stroke(stroke))
    if (f > 0f) rotate(-90f) {
        drawArc(Brush.sweepGradient(0f to color.copy(alpha = 0.65f), f to color, f to color.copy(alpha = 0.65f)), 0f, 360f * f, false, tl, box,
            style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Petit : quatre anneaux emboîtés, sans texte (chaque couleur dit son score). */
@Composable
private fun Nested(all: List<Score>, modifier: Modifier) {
    val fill = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fill.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
    val dark = Mf.colors.dark
    val tracks = all.map { trackOf(it.color, dark) }
    Canvas(modifier.aspectRatio(1f)) {
        val n = all.size
        val stroke = size.minDimension / (2f * n + 1.4f)
        all.forEachIndexed { i, s ->
            val inset = stroke / 2 + i * stroke * 1.18f
            val box = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(tracks[i], 0f, 360f, false, tl, box, style = Stroke(stroke))
            val v = (s.value ?: 0) / 100f * fill.value
            if (v > 0f) rotate(-90f) { drawArc(s.color, 0f, 360f * v, false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round)) }
        }
    }
}
