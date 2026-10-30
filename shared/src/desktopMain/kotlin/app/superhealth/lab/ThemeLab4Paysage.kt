package app.superhealth.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/*
 * Tour 4 — B. Paysage : chaque écran s'ouvre sur un paysage dessiné. Le soleil est la Forme (plus il est haut, plus vous
 * êtes en forme), la nuit est un petit ciel étoilé, la semaine d'activité forme la crête des collines et le programme
 * est un sentier à étapes. Navigation : une colline blanche en bas, le « + » est un soleil posé sur sa crête.
 */

private object Pa {
    val page = Color(0xFFF6F4FD); val ink = Color(0xFF22254A); val sub = Color(0xFF6A6E96); val faint = Color(0xFFE9E7F7)
    val skyTop = Color(0xFFFFD9C7); val skyMid = Color(0xFFFCE4F1); val skyLow = Color(0xFFE6E8FF)
    val sunIn = Color(0xFFFFE590); val sunMid = Color(0xFFFFB54D); val sunOut = Color(0xFFFF8A5B)
    val far = Color(0xFFD7DAFB); val mid = Color(0xFFB6BFF3); val near = Color(0xFF8D9FE8)
    val meadow = Color(0xFF7FD3B3); val meadow2 = Color(0xFF55B996)
    val navy = Color(0xFF3D4BB8); val green = Color(0xFF2FA67F); val violet = Color(0xFF7B61D9); val coral = Color(0xFFFF7A59)
    val teal = Color(0xFF22A6A0); val night1 = Color(0xFF1E2458); val night2 = Color(0xFF454A9C)
    val sun = Brush.linearGradient(listOf(Color(0xFFFFC44D), Color(0xFFFF7E5F)))
}

@Composable
private fun PaCard(modifier: Modifier = Modifier, pad: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Column(modifier.shadow(10.dp, shape, ambientColor = Color(0x333D4BB8), spotColor = Color(0x333D4BB8)).clip(shape).background(Color.White).padding(pad), content = content)
}

/** Crête : sommets (fractions) reliés en lignes (montagne) ou en courbe (colline), fermée vers le bas. */
private fun DrawScope.layer(pts: List<Pair<Float, Float>>, top: Color, bottom: Color, soft: Boolean, h: Float = size.height) {
    val o = pts.map { Offset(it.first * size.width, it.second * h) }
    val p = if (soft) smooth(o) else Path().apply {
        moveTo(o[0].x, o[0].y)
        // sommets légèrement arrondis
        for (i in 1 until o.size) {
            val a = o[i - 1]; val b = o[i]
            if (i < o.size - 1) { val c = o[i + 1]; val r = 0.18f
                lineTo(b.x - (b.x - a.x) * r, b.y - (b.y - a.y) * r); quadraticTo(b.x, b.y, b.x + (c.x - b.x) * r, b.y + (c.y - b.y) * r) }
            else lineTo(b.x, b.y)
        }
    }
    p.lineTo(size.width, size.height); p.lineTo(0f, size.height); p.close()
    drawPath(p, Brush.verticalGradient(listOf(top, bottom), startY = o.minOf { it.y }, endY = size.height))
}

private fun DrawScope.sky() = drawRect(Brush.verticalGradient(listOf(Pa.skyTop, Pa.skyMid, Pa.skyLow)))

private fun DrawScope.sunAt(c: Offset, r: Float) {
    glow(circlePath(c, r * 2.1f), Color(0x55FFB54D), r * 1.2f)
    drawCircle(Color(0x33FFFFFF), r * 1.55f, c); drawCircle(Color(0x33FFE590), r * 1.25f, c)
    drawCircle(Brush.radialGradient(listOf(Pa.sunIn, Pa.sunMid, Pa.sunOut), c, r), r, c)
}

private fun DrawScope.cloud(x: Float, y: Float, s: Float, a: Float = 0.75f) {
    val c = Color.White.copy(alpha = a)
    val p = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(Offset(x, y), 14 * s)); addOval(androidx.compose.ui.geometry.Rect(Offset(x + 16 * s, y - 6 * s), 18 * s))
        addOval(androidx.compose.ui.geometry.Rect(Offset(x + 36 * s, y + 1 * s), 13 * s))
        addRoundRect(androidx.compose.ui.geometry.RoundRect(x - 6 * s, y, x + 46 * s, y + 13 * s, CornerRadius(7 * s)))
    }
    drawPath(p, c)
}

private fun DrawScope.birds(x: Float, y: Float, s: Float) {
    listOf(Offset(0f, 0f), Offset(16f, 7f), Offset(-12f, 10f)).forEach { o ->
        val b = Path().apply { moveTo(x + o.x * s - 6 * s, y + o.y * s - 2 * s); quadraticTo(x + o.x * s - 3 * s, y + o.y * s - 4 * s, x + o.x * s, y + o.y * s)
            quadraticTo(x + o.x * s + 3 * s, y + o.y * s - 4 * s, x + o.x * s + 6 * s, y + o.y * s - 2 * s) }
        drawPath(b, Pa.ink.copy(alpha = 0.45f), style = Stroke(1.3f * s, cap = StrokeCap.Round))
    }
}

@Composable
private fun PaScreen(tab: Int, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Pa.page)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        PaNav(tab, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TopIcons(modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xB3FFFFFF)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = Pa.ink) }
        Box(Modifier.size(42.dp).clip(CircleShape).background(Pa.navy), contentAlignment = Alignment.Center) { X("B", 17.sp, Color.White, FontWeight.SemiBold) }
    }
}

/** Colline de navigation ; le « + » est un soleil posé sur la bosse de droite, sous le pouce. */
@Composable
private fun PaNav(tab: Int, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(128.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val top = 50.dp.toPx(); val w = size.width; val fx = w - 56.dp.toPx(); val d = 1.dp.toPx()
            val p = Path().apply {
                moveTo(0f, top + 12 * d)
                cubicTo(w * 0.22f, top - 8 * d, w * 0.42f, top + 14 * d, fx - 62 * d, top + 4 * d)
                cubicTo(fx - 36 * d, top - 2 * d, fx - 42 * d, top - 30 * d, fx, top - 30 * d)
                cubicTo(fx + 42 * d, top - 30 * d, fx + 34 * d, top + 2 * d, w, top + 4 * d)
                lineTo(w, size.height); lineTo(0f, size.height); close()
            }
            glow(p, Color(0x403D4BB8), 18 * d)
            drawPath(p, Brush.verticalGradient(listOf(Pa.meadow, Pa.meadow2), startY = top - 30 * d, endY = size.height))
        }
        Row(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Icons.Rounded.Today to "Aujourd'hui", Icons.Rounded.Restaurant to "Nutrition", Icons.AutoMirrored.Rounded.DirectionsRun to "Activités").forEachIndexed { i, (ic, l) ->
                val on = i == tab
                Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp, 32.dp).clip(CircleShape).background(if (on) Color.White else Color.Transparent), contentAlignment = Alignment.Center) {
                        Icon(ic, null, Modifier.size(23.dp), tint = if (on) Color(0xFF2E8F6E) else Color(0xE6FFFFFF))
                    }
                    Spacer(Modifier.height(3.dp))
                    X(l, 12.sp, Color.White, if (on) FontWeight.Bold else FontWeight.Medium, lines = 1)
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(end = 25.dp, top = 0.dp).size(62.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(62.dp)) { sunAt(center, size.minDimension / 2) }
            Icon(Icons.Rounded.Add, null, Modifier.size(32.dp), tint = Color.White)
        }
    }
}

/** Demi-cercle « lever de soleil » : le soleil monte avec la valeur. */
@Composable
private fun SunGauge(f: Float, hue: Color, modifier: Modifier) {
    Canvas(modifier) {
        val st = 4.dp.toPx(); val r = minOf(size.width / 2, size.height) - st
        val c = Offset(size.width / 2, size.height - st / 2)
        drawArc(Pa.faint, 180f, 180f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(st, cap = StrokeCap.Round))
        drawArc(hue, 180f, 180f * f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(st, cap = StrokeCap.Round))
        val a = Math.toRadians(180.0 + 180.0 * f)
        val s = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
        glow(circlePath(s, 6.dp.toPx()), hue.copy(alpha = 0.5f), 4.dp.toPx())
        drawCircle(Color.White, 6.dp.toPx(), s); drawCircle(hue, 4.dp.toPx(), s)
    }
}

// ---- Aujourd'hui ----------------------------------------------------------------------------------------------------------

@Composable
fun PaysageHome() = PaScreen(0) {
    Box(Modifier.fillMaxWidth().height(372.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            sky()
            cloud(size.width * 0.5f, size.height * 0.3f, 0.8.dp.toPx(), 0.6f)
            // Forme 78 : le soleil est à 78 % de sa course
            val a = Math.toRadians(180.0 + 180.0 * 0.78)
            sunAt(Offset(size.width * 0.5f + size.width * 0.36f * cos(a).toFloat(), size.height * 0.6f + size.height * 0.42f * sin(a).toFloat()), 30.dp.toPx())
            birds(size.width * 0.62f, size.height * 0.4f, 1.dp.toPx())
            layer(listOf(0f to .62f, .08f to .55f, .16f to .58f, .27f to .43f, .36f to .53f, .45f to .48f, .58f to .6f, .7f to .46f, .8f to .4f, .9f to .51f, 1f to .47f),
                Pa.far, Pa.skyLow, soft = false)
            layer(listOf(0f to .72f, .12f to .6f, .22f to .67f, .34f to .56f, .48f to .69f, .6f to .61f, .74f to .67f, .86f to .57f, 1f to .65f), Pa.mid, Pa.far, soft = false)
            layer(listOf(0f to .82f, .18f to .73f, .38f to .81f, .6f to .71f, .82f to .79f, 1f to .73f), Pa.near, Pa.mid, soft = true)
            layer(listOf(0f to .9f, .25f to .84f, .5f to .9f, .75f to .85f, 1f to .88f), Pa.meadow, Pa.meadow2, soft = true)
            layer(listOf(0f to .97f, .5f to .935f, 1f to .965f), Pa.page, Pa.page, soft = true)
        }
        TopIcons(Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 16.dp))
        Column(Modifier.padding(start = 22.dp, top = 52.dp)) {
            X("Vendredi 30 octobre", 14.sp, Pa.sub, FontWeight.Medium)
            X("78", 92.sp, Pa.ink, FontWeight.Medium, display = true, spacing = (-3).sp)
            X("Forme · prêt à pousser", 16.sp, Pa.ink, FontWeight.Medium, modifier = Modifier.offset(y = (-8).dp))
        }
    }
    PaCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().offset(y = (-48).dp), pad = 14.dp) {
        Row {
            listOf(Triple("Charge", 64, Pa.navy), Triple("Repos", 84, Pa.violet), Triple("Effort", 42, Pa.coral)).forEach { (n, v, c) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    SunGauge(v / 100f, c, Modifier.size(62.dp, 34.dp))
                    X("$v", 20.sp, Pa.ink, FontWeight.SemiBold, display = true); X(n, 12.sp, Pa.sub)
                }
            }
        }
    }
    Column(Modifier.padding(horizontal = 16.dp).offset(y = (-48).dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // la nuit, en petit ciel étoilé
        Box(Modifier.fillMaxWidth().height(150.dp).shadow(10.dp, RoundedCornerShape(30.dp), ambientColor = Color(0x553D4BB8), spotColor = Color(0x553D4BB8))
            .clip(RoundedCornerShape(30.dp)).background(Brush.verticalGradient(listOf(Pa.night1, Pa.night2)))) {
            Canvas(Modifier.fillMaxSize()) {
                val rnd = kotlin.random.Random(7)
                repeat(38) { drawCircle(Color.White.copy(alpha = 0.25f + rnd.nextFloat() * 0.6f), (0.6f + rnd.nextFloat() * 1.1f).dp.toPx(), Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height * 0.55f)) }
                val m = Offset(size.width - 46.dp.toPx(), 36.dp.toPx()); val mr = 15.dp.toPx()
                glow(circlePath(m, mr * 1.6f), Color(0x55FFF1C9), mr)
                drawCircle(Color(0xFFFFF1C9), mr, m); drawCircle(Color(0xFF242B63), mr * 0.86f, m + Offset(mr * 0.45f, -mr * 0.25f))
                // phases de la nuit en collines : plus le sommeil est profond, plus la colline est haute
                val total = night4.sumOf { it.second }.toFloat(); var x = 0f
                val pts = mutableListOf(Offset(0f, size.height * 0.8f))
                night4.forEach { (st, mn) -> val w = size.width * mn / total; pts += Offset(x + w / 2, size.height * (0.5f + 0.1f * st)); x += w }
                pts += Offset(size.width, size.height * 0.8f)
                val hill = smooth(pts).apply { lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                drawPath(hill, Brush.verticalGradient(listOf(Color(0xFF9C8CFF), Color(0xFF5A57C9)), startY = size.height * 0.55f, endY = size.height))
                val hill2 = smooth(pts.map { Offset(it.x, it.y + size.height * 0.12f) }).apply { lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                drawPath(hill2, Color(0xFF3A3B8C))
            }
            Column(Modifier.padding(18.dp)) {
                X("Nuit", 13.sp, Color(0xB3FFFFFF), FontWeight.Medium)
                Row(verticalAlignment = Alignment.Bottom) { X("7 h 32", 30.sp, Color.White, FontWeight.Medium, display = true); X("  score 84", 14.sp, Color(0xCCFFFFFF), modifier = Modifier.padding(bottom = 5.dp)) }
            }
            X("23:48 → 07:20", 12.sp, Color(0xCCFFFFFF), modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PaCard(Modifier.weight(1.25f).height(150.dp)) {
                X("Calories", 13.sp, Pa.sub, FontWeight.Medium)
                Row(verticalAlignment = Alignment.Bottom) { X("927", 28.sp, Pa.ink, FontWeight.Medium, display = true); X(" restantes", 13.sp, Pa.sub, modifier = Modifier.padding(bottom = 5.dp)) }
                Spacer(Modifier.weight(1f))
                Trail(1353f / 2280, Modifier.fillMaxWidth().height(44.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(Triple(Icons.AutoMirrored.Rounded.DirectionsRun, "6 240", "pas"), Triple(Icons.Rounded.Favorite, "54", "bpm repos")).forEachIndexed { i, (ic, v, l) ->
                    PaCard(Modifier.fillMaxWidth().height(68.dp), pad = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(if (i == 0) Color(0xFFFFEAD9) else Color(0xFFFFE1E8)), contentAlignment = Alignment.Center) {
                                Icon(ic, null, Modifier.size(21.dp), tint = if (i == 0) Color(0xFFE5622F) else Color(0xFFE8456B))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column { X(v, 19.sp, Pa.ink, FontWeight.SemiBold, display = true); X(l, 11.sp, Pa.sub) }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Pa.sun), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(20.dp), tint = Color.White) }
            Spacer(Modifier.width(10.dp))
            X("Bonne nuit, charge correcte : c'est le jour des Jambes.", 14.sp, Pa.ink,
                modifier = Modifier.clip(RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

/** Sentier sur une colline : la partie parcourue est pleine, le reste en pointillés, un drapeau au sommet. */
@Composable
private fun Trail(f: Float, modifier: Modifier) {
    Canvas(modifier) {
        val pts = listOf(0f to .92f, .22f to .72f, .45f to .62f, .7f to .38f, .92f to .2f).map { Offset(it.first * size.width, it.second * size.height) }
        val hill = smooth(listOf(Offset(0f, size.height * 0.95f)) + pts.drop(1).map { it + Offset(0f, 5.dp.toPx()) } + Offset(size.width, size.height * 0.3f))
            .apply { lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(hill, Brush.verticalGradient(listOf(Color(0xFFD6F3E7), Color(0xFFF3FBF7))))
        val path = smooth(pts)
        drawPath(path, Pa.teal.copy(alpha = 0.35f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 9f))))
        val m = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
        val done = Path(); m.getSegment(0f, m.length * f, done, true)
        drawPath(done, Pa.teal, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        val at = m.getPosition(m.length * f)
        glow(circlePath(at, 8.dp.toPx()), Pa.teal.copy(alpha = 0.5f), 5.dp.toPx())
        drawCircle(Color.White, 7.dp.toPx(), at); drawCircle(Pa.teal, 4.5.dp.toPx(), at)
        val top = pts.last()
        drawLine(Pa.ink, top, top - Offset(0f, 16.dp.toPx()), 1.5.dp.toPx())
        drawPath(Path().apply { moveTo(top.x, top.y - 16.dp.toPx()); lineTo(top.x + 11.dp.toPx(), top.y - 12.dp.toPx()); lineTo(top.x, top.y - 8.dp.toPx()); close() }, Pa.coral)
    }
}

// ---- Nutrition -------------------------------------------------------------------------------------------------------------

@Composable
fun PaysageJournal() = PaScreen(1) {
    // la course du soleil : chaque repas est posé à son heure
    BoxWithConstraints(Modifier.fillMaxWidth().height(286.dp)) {
        val w = maxWidth; val hh = maxHeight
        val cx = 0.5f; val rx = 0.42f; val cy = 0.9f; val ry = 0.5f
        fun at(t: Float): Pair<Float, Float> { val a = Math.toRadians(180.0 + 180.0 * ((t - 7f) / 12f)); return (cx + rx * cos(a).toFloat()) to (cy + ry * sin(a).toFloat()) }
        Canvas(Modifier.fillMaxSize()) {
            sky()
            cloud(size.width * 0.66f, size.height * 0.2f, 0.9.dp.toPx(), 0.6f)
            val c = Offset(size.width * cx, size.height * cy)
            drawArc(Pa.ink.copy(alpha = 0.25f), 180f, 180f, false, Offset(c.x - size.width * rx, c.y - size.height * ry), Size(size.width * rx * 2, size.height * ry * 2),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 8f))))
            val now = at(17.5f)
            drawArc(Color(0x99FFB54D), 180f, 180f * (10.5f / 12f), false, Offset(c.x - size.width * rx, c.y - size.height * ry), Size(size.width * rx * 2, size.height * ry * 2),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            sunAt(Offset(now.first * size.width, now.second * size.height), 17.dp.toPx())
            layer(listOf(0f to .8f, .14f to .72f, .3f to .78f, .5f to .7f, .7f to .77f, .86f to .69f, 1f to .75f), Pa.mid, Pa.far, soft = false)
            layer(listOf(0f to .9f, .3f to .84f, .6f to .9f, 1f to .85f), Pa.meadow, Pa.meadow2, soft = true)
            layer(listOf(0f to .975f, .5f to .945f, 1f to .97f), Pa.page, Pa.page, soft = true)
        }
        TopIcons(Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 16.dp))
        Column(Modifier.padding(start = 22.dp, top = 50.dp)) {
            X("Nutrition", 30.sp, Pa.ink, FontWeight.Medium, display = true); X("Aujourd'hui", 14.sp, Pa.sub, FontWeight.Medium)
        }
        listOf(8.17f to meals4[0], 12.67f to meals4[1], 16.5f to meals4[2]).forEach { (t, m) ->
            val (x, y) = at(t)
            Column(Modifier.offset(w * x - 24.dp, hh * y - 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(48.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Color.White).border(2.dp, Color(0xFFFFD08A), CircleShape),
                    contentAlignment = Alignment.Center) { PixelIcon(m.third.first().icon, 30.dp) }
                X(m.second, 11.sp, Pa.ink, FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp).clip(CircleShape).background(Color(0xCCFFFFFF)).padding(horizontal = 6.dp, vertical = 1.dp))
            }
        }
    }
    Column(Modifier.padding(horizontal = 16.dp).offset(y = (-40).dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PaCard(Modifier.fillMaxWidth(), pad = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SunGauge(1353f / 2280, Pa.teal, Modifier.size(64.dp, 36.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.width(92.dp)) { X("927", 26.sp, Pa.ink, FontWeight.Medium, display = true); X("kcal restantes", 11.sp, Pa.sub) }
                listOf(Triple("Prot.", 98 to 150, Color(0xFFE8456B)), Triple("Lip.", 52 to 76, Color(0xFFF2A007)), Triple("Gluc.", 141 to 285, Pa.green)).forEach { (n, v, c) ->
                    Column(Modifier.weight(1f).padding(start = 6.dp)) {
                        X("${v.first} g", 14.sp, Pa.ink, FontWeight.SemiBold); X(n, 11.sp, Pa.sub)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Pa.faint)) {
                            Box(Modifier.fillMaxWidth(v.first.toFloat() / v.second).height(6.dp).clip(CircleShape).background(c))
                        }
                    }
                }
            }
        }
        meals4.forEachIndexed { mi, (name, time, foods) ->
            PaCard(Modifier.fillMaxWidth(), pad = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    Icon(Icons.Rounded.WbSunny, null, Modifier.size(18.dp), tint = listOf(Color(0xFFFFB54D), Color(0xFFFF9A3D), Color(0xFFFF7E5F))[mi])
                    Spacer(Modifier.width(8.dp))
                    X(name, 17.sp, Pa.ink, FontWeight.SemiBold, display = true, modifier = Modifier.weight(1f))
                    X("$time · ${foods.sumOf { it.kcal }} kcal", 13.sp, Pa.sub)
                }
                foods.forEach { f ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF2F0FF)), contentAlignment = Alignment.Center) { PixelIcon(f.icon, 28.dp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { X(f.name, 15.sp, Pa.ink, lines = 1); X(f.qty, 12.sp, Pa.sub) }
                        X("${f.kcal}", 14.sp, Pa.sub, FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ---- Activités -------------------------------------------------------------------------------------------------------------

@Composable
fun PaysageActivities() = PaScreen(2) {
    // la semaine en relief : un sommet par jour, hauteur = minutes d'activité
    val week = listOf("L" to 48, "M" to 52, "M" to 0, "J" to 29, "V" to 0, "S" to -1, "D" to -1)
    BoxWithConstraints(Modifier.fillMaxWidth().height(250.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            sky()
            sunAt(Offset(size.width * 0.86f, size.height * 0.52f), 22.dp.toPx())
            cloud(size.width * 0.5f, size.height * 0.3f, 0.8.dp.toPx(), 0.55f)
            layer(listOf(0f to .7f, .2f to .6f, .4f to .66f, .62f to .56f, .82f to .64f, 1f to .58f), Pa.far, Pa.skyLow, soft = false)
            val n = week.size; val base = 0.86f
            val pts = mutableListOf(0f to base)
            week.forEachIndexed { i, (_, m) ->
                val x = (i + 0.5f) / n
                pts += (x - 0.5f / n) to base; pts += x to (base - 0.03f - 0.3f * maxOf(m, 0) / 52f)
            }
            pts += 1f to base
            layer(pts, Pa.near, Pa.mid, soft = false)
            layer(listOf(0f to .93f, .5f to .9f, 1f to .93f), Pa.meadow, Pa.meadow2, soft = true)
            layer(listOf(0f to .985f, .5f to .96f, 1f to .985f), Pa.page, Pa.page, soft = true)
        }
        TopIcons(Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 16.dp))
        Column(Modifier.padding(start = 22.dp, top = 50.dp)) {
            X("Activités", 30.sp, Pa.ink, FontWeight.Medium, display = true); X("3 sorties · 2 h 09 cette semaine", 14.sp, Pa.sub, FontWeight.Medium)
        }
        week.forEachIndexed { i, (d, m) ->
            val x = maxWidth * ((i + 0.5f) / week.size)
            X(d, 11.sp, if (i == 4) Pa.ink else Color.White, FontWeight.SemiBold, modifier = Modifier.offset(x - 4.dp, maxHeight * 0.84f - 16.dp))
            if (m > 0) X("$m", 10.sp, Pa.ink, FontWeight.SemiBold, modifier = Modifier.offset(x - 7.dp, maxHeight * (0.83f - 0.3f * m / 52f) - 16.dp))
        }
    }
    Column(Modifier.padding(horizontal = 16.dp).offset(y = (-26).dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PaCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { X("Push Pull Legs", 20.sp, Pa.ink, FontWeight.Medium, display = true); X("Semaine 3 · étape 3 sur 4", 13.sp, Pa.sub) }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Pa.sub)
            }
            ProgramTrail(Modifier.fillMaxWidth().height(206.dp))
            Row(Modifier.fillMaxWidth().shadow(8.dp, CircleShape, ambientColor = Pa.coral, spotColor = Pa.coral).clip(CircleShape).background(Pa.sun).padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White); Spacer(Modifier.width(6.dp)); X("Commencer Jambes", 17.sp, Color.White, FontWeight.SemiBold)
            }
        }
        PaCard(Modifier.fillMaxWidth(), pad = 0.dp) {
            Box(Modifier.fillMaxWidth().height(112.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val alt = listOf(.7f, .62f, .66f, .5f, .42f, .48f, .3f, .36f, .52f, .6f, .56f, .68f, .72f)
                    val pts = alt.mapIndexed { i, a -> Offset(size.width * i / (alt.size - 1), size.height * (0.48f + a * 0.5f)) }
                    val area = smooth(pts).apply { lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                    drawPath(area, Brush.verticalGradient(listOf(Color(0xFFBDEBD9), Color(0xFFF4FBF8))))
                    drawPath(smooth(pts), Pa.green, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(Modifier.padding(16.dp)) {
                    X("Course · hier", 13.sp, Pa.sub, FontWeight.Medium)
                    Row(verticalAlignment = Alignment.Bottom) { X("5,2 km", 26.sp, Pa.ink, FontWeight.Medium, display = true); X("  28:41 · D+ 64 m", 13.sp, Pa.sub, modifier = Modifier.padding(bottom = 4.dp)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(Triple(Icons.Rounded.FitnessCenter, "Pull · mardi", "12 340 kg"), Triple(Icons.AutoMirrored.Rounded.DirectionsBike, "Vélo · lundi", "21,0 km")).forEach { (ic, l, v) ->
                PaCard(Modifier.weight(1f), pad = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFE8EBFF)), contentAlignment = Alignment.Center) { Icon(ic, null, Modifier.size(20.dp), tint = Pa.navy) }
                        Spacer(Modifier.width(10.dp))
                        Column { X(v, 17.sp, Pa.ink, FontWeight.SemiBold, display = true, lines = 1); X(l, 11.sp, Pa.sub) }
                    }
                }
            }
        }
    }
}

/** Le programme en sentier : quatre étapes reliées, la prochaine dans un halo de soleil. */
@Composable
private fun ProgramTrail(modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val stops = listOf(0.13f to 0.66f, 0.38f to 0.32f, 0.63f to 0.64f, 0.87f to 0.3f)
        val w = maxWidth; val hh = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val pts = stops.map { Offset(it.first * size.width, it.second * size.height) }
            val path = smooth(listOf(Offset(-10f, size.height * 0.9f)) + pts + Offset(size.width + 10f, size.height * 0.1f))
            drawPath(path, Pa.faint, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, Color(0xFFC9C6E8), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))))
            val done = smooth(listOf(Offset(-10f, size.height * 0.9f)) + pts.take(3))
            drawPath(done, Pa.green, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        }
        program4.forEachIndexed { i, d ->
            val (x, y) = stops[i]; val big = if (d.next) 76.dp else 62.dp
            Column(Modifier.offset(w * x - big / 2, hh * y - big / 2), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    if (d.next) Canvas(Modifier.size(big)) { glow(circlePath(center, size.minDimension / 2), Color(0x99FFB54D), 12.dp.toPx()) }
                    Box(Modifier.size(big).clip(CircleShape).background(if (d.next) Color(0xFFFFF4E6) else Color(0xFFF4F3FB))
                        .border(if (d.next) 3.dp else 2.dp, when { d.done -> Pa.green; d.next -> Color(0xFFFFA94D); else -> Pa.faint }, CircleShape)) {
                        BodyIn(d.side, d.target, if (d.next) Color(0xFFFF7E5F) else if (d.done) Pa.green else Pa.navy, Color(0xFFD9D7EC), Color(0xFFEAE8F6), d.from, d.to,
                            Modifier.fillMaxSize().padding(8.dp))
                    }
                    if (d.done) Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(Pa.green), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = Color.White)
                    }
                }
                X(d.name, 13.sp, Pa.ink, if (d.next) FontWeight.Bold else FontWeight.Medium, lines = 1, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}
