@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.superhealth.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Catalogue de widgets « en vrac » : chaque famille propose plusieurs mises en page réellement différentes, numérotées,
 * toutes construites sur le kit Studio (le même catalogue se rend en sombre et en clair).
 */

class W(val id: String, val name: String, val content: @Composable () -> Unit)
class Family(val key: String, val title: String, val items: List<W>)

private val formeWeek = listOf(66f, 71f, 64f, 70f, 74f, 72f, 78f)
private val sleepWeek = listOf(430, 402, 465, 388, 441, 476, 452)
private val days7 = listOf("L", "M", "M", "J", "V", "S", "D")
private val actWeek = listOf(48, 52, 0, 29, 0, -1, -1)
private val legDay = listOf(Triple("Squat barre", "4 × 6", "1a25c6f170d8803d8231d083fdd65458"), Triple("Soulevé de terre roumain", "3 × 8", "1a35c6f170d880bebc64f4dce0d5f5f1"),
    Triple("Fentes bulgares", "3 × 10", "1a25c6f170d880d8bb7fef2de64a1e60"), Triple("Presse à cuisses 45°", "3 × 12", "1a25c6f170d88079a926dde776766181"),
    Triple("Mollets à la presse", "4 × 15", "1a15c6f170d88074b3d4e1548e32c508"))
/** Macros (g) des aliments de la journée fictive : protéines, lipides, glucides. */
private val macros = mapOf("Œufs brouillés" to Triple(13f, 13f, 1f), "Pain complet" to Triple(6f, 1.5f, 26f), "Banane" to Triple(1.3f, 0.4f, 27f),
    "Poulet rôti" to Triple(39f, 14f, 0f), "Riz basmati" to Triple(5f, 0.5f, 50f), "Brocolis" to Triple(3.4f, 0.5f, 8f), "Avocat" to Triple(1.5f, 11f, 6f),
    "Yaourt grec" to Triple(13f, 7f, 6f), "Pomme" to Triple(0.5f, 0.3f, 25f))

@Composable private fun d() = LocalD.current
private val wide = Modifier.fillMaxWidth()

private fun Design.stage(st: Int): Color = when (st) { 4 -> hues.heart.b; 3 -> hues.charge.b; 2 -> hues.sleep.b; else -> hues.sleep.a }

@Composable
private fun Chev() = Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = d().base.sub)

@Composable
private fun Thumb(id: String, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE2E0DF))) {
        image("ex/${id}_a.webp")?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun StartButton(text: String = "Commencer Jambes", modifier: Modifier = wide) {
    val d = d()
    Row(modifier.clip(CircleShape).background(d.fill(d.hues.effort)).padding(vertical = 15.dp), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White); Spacer(Modifier.width(6.dp)); Tt(text, 16.sp, Color.White, FontWeight.SemiBold)
    }
}

/** Corps de l'app, muscles du jour remplis par la teinte ; les séances faites sont grisées. */
@Composable
private fun Body5(day: Day, modifier: Modifier, hue: Hue? = null) {
    val d = d()
    val h = hue ?: when { day.next -> d.hues.effort; day.done -> Hue(d.base.sub, d.base.sub); else -> d.hues.charge }
    val base = if (d.dark) Color(0xFF2C2D34) else Color(0xFFD6D8DE); val bone = if (d.dark) Color(0xFF202127) else Color(0xFFE6E7EB)
    val muscle = if (d.dark) Color(0xFF3A3B44) else Color(0xFFC3C6CE)
    BodyCanvas(day.side, day.from, day.to, modifier) { art, s, y0, hh ->
        art.parts.forEach { (id, p) -> when { id == "body" -> drawPath(p, base); id == "bone" -> drawPath(p, bone); id !in day.target -> drawPath(p, muscle) } }
        art.parts.filter { it.first in day.target }.forEach { (_, p) ->
            if (d.glow > 0f && day.next) glow(p, h.b.copy(alpha = 0.8f * d.glow), 9.dp.toPx() / s)
            drawPath(p, if (d.flat) SolidColor(h.a) else Brush.verticalGradient(listOf(h.a, h.b), y0, y0 + hh))
        }
    }
}

@Composable
private fun Check(size: Dp = 18.dp) {
    val d = d()
    Box(Modifier.size(size).clip(CircleShape).background(d.hues.forme.a), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Check, null, Modifier.size(size * 0.72f), tint = if (d.dark) d.base.bg else Color.White)
    }
}

// =====================================================================================================================
// A. Forme
// =====================================================================================================================

@Composable private fun A1() { val d = d(); val h = d.hues
    Tile(wide, pad = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Tt("Forme", 13.sp, d.base.sub, FontWeight.Medium); Tt("Prêt à pousser", 22.sp, h.forme.a, FontWeight.SemiBold, true, brush = d.fill(h.forme)) }
            Chev()
        }
        Box(wide.height(206.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(200.dp)) {
                val w = 15.dp.toPx(); var r = size.minDimension / 2 - w / 2 - 2.dp.toPx()
                listOf(0.64f to h.charge, 0.84f to h.repos, 0.42f to h.effort).forEach { (f, hue) -> dRing(d, center, r, w, f, hue); r -= w + 4.dp.toPx() }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Tt("78", 44.sp, weight = FontWeight.SemiBold, display = true); Tt("sur 100", 12.sp, d.base.sub) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Charge", "64", h.charge), Triple("Repos", "84", h.repos), Triple("Effort", "42", h.effort)).forEach { (n, v, hue) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(d.base.s2).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Box(Modifier.width(20.dp).height(4.dp).clip(CircleShape).background(d.fill(hue))); Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) { Tt(v, 20.sp, weight = FontWeight.SemiBold, display = true); Spacer(Modifier.width(5.dp)); Tt(n, 12.sp, d.base.sub, modifier = Modifier.padding(bottom = 3.dp)) }
                }
            }
        }
    }
}

@Composable private fun A2() { val d = d(); val h = d.hues
    Tile(wide, pad = 18.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Tt("78", 68.sp, weight = FontWeight.Medium, display = true, spacing = (-2).sp)
            Tt(" /100", 15.sp, d.base.sub, modifier = Modifier.padding(bottom = 14.dp)); Spacer(Modifier.weight(1f))
            Pill("Prêt à pousser", d.tint(h.forme.a, 0.16f), if (d.dark) h.forme.a else h.forme.a.mix(Color.Black, 0.15f), Modifier.padding(bottom = 14.dp), 13.sp)
        }
        Tt("Bonne récupération, charge équilibrée.", 14.sp, d.base.sub)
        Spacer(Modifier.height(14.dp))
        listOf(Triple("Charge", 64, h.charge), Triple("Repos", 84, h.repos), Triple("Effort", 42, h.effort)).forEach { (n, v, hue) ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Tt(n, 14.sp, modifier = Modifier.width(64.dp))
                Box(Modifier.weight(1f).height(12.dp).clip(CircleShape).background(d.base.s2)) { Box(Modifier.fillMaxWidth(v / 100f).fillMaxHeight().clip(CircleShape).background(d.fill(hue))) }
                Tt("$v", 15.sp, weight = FontWeight.SemiBold, modifier = Modifier.width(36.dp).padding(start = 10.dp))
            }
        }
    }
}

@Composable private fun A3() { val d = d(); val h = d.hues
    Tile(wide, pad = 18.dp) {
        Lab(null, "Forme")
        Box(wide.height(170.dp), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.fillMaxSize()) {
                val st = 16.dp.toPx(); val c = Offset(size.width / 2, size.height - 8.dp.toPx()); val r = minOf(size.width / 2 - st, size.height - st - 8.dp.toPx())
                listOf(Triple(0f, 0.4f, h.repos), Triple(0.4f, 0.7f, h.charge), Triple(0.7f, 1f, h.forme)).forEach { (a, b, hue) ->
                    drawArc(if (d.flat) SolidColor(hue.a) else Brush.sweepGradient(listOf(hue.a, hue.b, hue.a), c), 180f + 180f * a + 1.5f, 180f * (b - a) - 3f, false,
                        Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(st))
                }
                for (i in 0..20) {
                    val a = Math.toRadians(180.0 + 9.0 * i); val r1 = r - st / 2 - 6.dp.toPx(); val r2 = r1 - (if (i % 5 == 0) 9.dp.toPx() else 5.dp.toPx())
                    drawLine(d.base.dim, Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()), Offset(c.x + r2 * cos(a).toFloat(), c.y + r2 * sin(a).toFloat()), 1.5.dp.toPx(), StrokeCap.Round)
                }
                val a = Math.toRadians(180.0 + 180.0 * 0.78); val tip = Offset(c.x + (r - 30.dp.toPx()) * cos(a).toFloat(), c.y + (r - 30.dp.toPx()) * sin(a).toFloat())
                drawLine(d.base.ink, c, tip, 4.dp.toPx(), StrokeCap.Round)
                drawCircle(d.base.ink, 9.dp.toPx(), c); drawCircle(d.base.s1, 4.dp.toPx(), c)
            }
        }
        Row(wide.padding(horizontal = 4.dp)) { Tt("Repos", 11.sp, d.base.sub, modifier = Modifier.weight(1f)); Tt("Maintien", 11.sp, d.base.sub, modifier = Modifier.weight(1f).wrapContentWidth()); Tt("Pousser", 11.sp, d.base.sub) }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Tt("78", 40.sp, weight = FontWeight.SemiBold, display = true); Spacer(Modifier.width(10.dp))
            Column(Modifier.padding(bottom = 6.dp)) { Tt("Pousser", 15.sp, weight = FontWeight.SemiBold); Tt("Charge 64 · Repos 84 · Effort 42", 12.sp, d.base.sub) }
        }
    }
}

@Composable private fun A4() { val d = d(); val h = d.hues
    Tile(wide, pad = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tt("Forme", 17.sp, weight = FontWeight.SemiBold, display = true); Spacer(Modifier.width(8.dp)); Tt("78", 17.sp, h.forme.a, FontWeight.SemiBold, true, brush = d.fill(h.forme))
            Spacer(Modifier.weight(1f)); Tt("Prêt à pousser", 13.sp, d.base.sub)
        }
        Spacer(Modifier.height(14.dp))
        Row {
            listOf(Triple("Charge", 64, h.charge), Triple("Repos", 84, h.repos), Triple("Effort", 42, h.effort)).forEach { (n, v, hue) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(88.dp)) { dRing(d, center, size.minDimension / 2 - 6.dp.toPx(), 10.dp.toPx(), v / 100f, hue, from = 135f, span = 270f) }
                        Tt("$v", 24.sp, weight = FontWeight.SemiBold, display = true)
                    }
                    Tt(n, 13.sp, d.base.sub, FontWeight.Medium, modifier = Modifier.offset(y = (-6).dp))
                }
            }
        }
    }
}

@Composable private fun A5() { val d = d(); val h = d.hues
    Tile(wide, pad = 18.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column {
                Tt("Forme", 13.sp, d.base.sub, FontWeight.Medium)
                Tt("78", 54.sp, weight = FontWeight.Medium, display = true, spacing = (-1).sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(14.dp), tint = h.forme.a); Tt(" 6 depuis lundi", 13.sp, h.forme.a, FontWeight.Medium)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Canvas(wide.height(96.dp)) {
                    val lo = 60f; val hi = 82f; val pad = 8.dp.toPx()
                    val pts = formeWeek.mapIndexed { i, v -> Offset(pad + (size.width - 2 * pad) * i / 6f, pad + (size.height - 2 * pad) * (hi - v) / (hi - lo)) }
                    val area = smooth(pts).apply { lineTo(pts.last().x, size.height); lineTo(pts[0].x, size.height); close() }
                    drawPath(area, Brush.verticalGradient(listOf(d.tint(h.forme.a, 0.28f), Color.Transparent)))
                    drawPath(smooth(pts), d.fill(h.forme), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    pts.dropLast(1).forEach { drawCircle(d.base.s1, 3.5.dp.toPx(), it); drawCircle(h.forme.a, 3.5.dp.toPx(), it, style = Stroke(2.dp.toPx())) }
                    dGlowDot(d, pts.last(), 9.dp.toPx(), h.forme.b)
                    drawCircle(h.forme.b, 6.dp.toPx(), pts.last()); drawCircle(d.base.s1, 2.5.dp.toPx(), pts.last())
                }
                Row(wide.padding(horizontal = 4.dp)) { days7.forEachIndexed { i, s -> Tt(s, 11.sp, if (i == 6) d.base.ink else d.base.sub, FontWeight.Medium, modifier = Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.Center) } }
            }
        }
        Spacer(Modifier.height(10.dp))
        Tt("Prêt à pousser · Charge 64 · Repos 84 · Effort 42", 13.sp, d.base.sub)
    }
}

@Composable private fun A6() { val d = d(); val h = d.hues
    Tile(wide, pad = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(cookie(9, 0.07f)).background(d.fill(h.forme)), contentAlignment = Alignment.Center) {
                Tt("78", 21.sp, if (d.dark) Color(0xFF052A1E) else Color.White, FontWeight.Bold, true)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Tt("Prêt à pousser", 17.sp, weight = FontWeight.SemiBold, display = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(64 to h.charge, 84 to h.repos, 42 to h.effort).forEachIndexed { i, (v, hue) ->
                        Box(Modifier.size(7.dp).clip(CircleShape).background(hue.a)); Tt(" $v", 13.sp, d.base.sub); if (i < 2) Spacer(Modifier.width(10.dp))
                    }
                }
            }
            Chev()
        }
    }
}

@Composable private fun A7() { val d = d(); val h = d.hues
    Tile(wide, pad = 18.dp) {
        Box(wide.height(210.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(210.dp)) {
                val n = 48; val c = center; val r1 = size.minDimension / 2 - 4.dp.toPx(); val r2 = r1 - 18.dp.toPx(); val lit = (n * 0.78f).roundToInt()
                for (i in 0 until n) {
                    val t = i / (n - 1f); val a = Math.toRadians(135.0 + 270.0 * t)
                    val col = if (i < lit) (if (d.flat) h.forme.a else h.forme.a.mix(h.forme.b, t)) else d.base.s2
                    val p1 = Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()); val p2 = Offset(c.x + r2 * cos(a).toFloat(), c.y + r2 * sin(a).toFloat())
                    if (i == lit - 1) dGlowDot(d, p1.mix(p2), 10.dp.toPx(), col)
                    drawLine(col, p1, p2, 4.5.dp.toPx(), StrokeCap.Round)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Tt("Forme", 13.sp, d.base.sub, FontWeight.Medium); Tt("78", 56.sp, weight = FontWeight.Medium, display = true); Tt("Prêt à pousser", 14.sp, h.forme.a, FontWeight.SemiBold)
            }
        }
        Row(wide, horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(Triple("Charge", 64, h.charge), Triple("Repos", 84, h.repos), Triple("Effort", 42, h.effort)).forEach { (n, v, hue) ->
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(hue.a)); Tt(" $n ", 13.sp, d.base.sub); Tt("$v", 13.sp, weight = FontWeight.SemiBold) }
            }
        }
    }
}

private fun Offset.mix(o: Offset) = Offset((x + o.x) / 2, (y + o.y) / 2)

@Composable private fun A8() { val d = d(); val h = d.hues; val ink = Color(0xFF04261C)
    Tile(wide, pad = 20.dp, bg = Brush.linearGradient(listOf(h.forme.a, h.forme.b))) {
            Column {
                Row { Tt("Forme", 14.sp, ink.copy(alpha = 0.7f), FontWeight.SemiBold, modifier = Modifier.weight(1f)); Tt("ven. 30 oct.", 13.sp, ink.copy(alpha = 0.6f)) }
                Tt("78", 84.sp, ink, FontWeight.Medium, true, spacing = (-3).sp)
                Tt("Prêt à pousser", 20.sp, ink, FontWeight.SemiBold, true, modifier = Modifier.offset(y = (-6).dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Charge 64", "Repos 84", "Effort 42").forEach { Pill(it, Color(0x40FFFFFF), ink, size = 13.sp) }
                }
            }
    }
}

// =====================================================================================================================
// B. Sommeil
// =====================================================================================================================

@Composable private fun SleepHead(right: String = "84") { val d = d()
    Row(verticalAlignment = Alignment.Bottom) {
        Tt("7", 30.sp, weight = FontWeight.SemiBold, display = true); Tt(" h ", 16.sp, d.base.sub, modifier = Modifier.padding(bottom = 4.dp))
        Tt("32", 30.sp, weight = FontWeight.SemiBold, display = true); Spacer(Modifier.weight(1f))
        Pill("Score $right", d.tint(d.hues.sleep.a, 0.18f), if (d.dark) d.hues.sleep.b else d.hues.sleep.a, Modifier.padding(bottom = 6.dp))
    }
}

@Composable private fun B1() { val d = d(); val cols = (1..4).associateWith { d.stage(it) }
    Tile(wide) {
        Lab(Icons.Rounded.Bedtime, "Sommeil", d.hues.sleep.b); SleepHead()
        Spacer(Modifier.height(8.dp))
        Canvas(wide.height(78.dp)) {
            val total = night4.sumOf { it.second }.toFloat(); val pad = 5.dp.toPx(); val r = 5.dp.toPx()
            fun y(st: Int) = pad + (size.height - 2 * pad) * (4 - st) / 3f
            val line = Path(); var x = 0f
            night4.forEachIndexed { i, (st, m) ->
                val w = size.width * m / total; val yy = y(st)
                if (i == 0) line.moveTo(0f, yy) else {
                    val py = y(night4[i - 1].first); val dd = if (yy > py) r else -r
                    line.lineTo(x - r, py); line.quadraticTo(x, py, x, py + dd); line.lineTo(x, yy - dd); line.quadraticTo(x, yy, x + r, yy)
                }
                x += w
            }
            line.lineTo(size.width, y(night4.last().first))
            val area = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            drawPath(area, Brush.verticalGradient(listOf(d.tint(d.hues.sleep.a, 0.3f), Color.Transparent)))
            if (d.glow > 0f) glow(line, d.hues.sleep.b.copy(alpha = 0.6f * d.glow), 5.dp.toPx(), 3.dp.toPx())
            drawPath(line, if (d.flat) SolidColor(d.hues.sleep.a) else Brush.verticalGradient(listOf(cols.getValue(4), cols.getValue(3), cols.getValue(2), cols.getValue(1)), pad, size.height - pad),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Row { Tt("23:48", 11.sp, d.base.sub); Spacer(Modifier.weight(1f)); Tt("07:20", 11.sp, d.base.sub) }
    }
}

@Composable private fun B2() { val d = d()
    Tile(wide) {
        Row(verticalAlignment = Alignment.CenterVertically) { Lab(Icons.Rounded.Bedtime, "Sommeil", d.hues.sleep.b, Modifier.weight(1f)); Tt("7 h 32 · score 84", 13.sp, weight = FontWeight.SemiBold) }
        Spacer(Modifier.height(12.dp))
        val total = night4.sumOf { it.second }.toFloat()
        listOf(4 to "Éveil", 3 to "Paradoxal", 2 to "Léger", 1 to "Profond").forEach { (st, n) ->
            val c = d.stage(st); val mins = night4.filter { it.first == st }.sumOf { it.second }
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Tt(n, 12.sp, d.base.sub, modifier = Modifier.width(70.dp))
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    drawRoundRect(d.base.s2, cornerRadius = CornerRadius(size.height / 2))
                    var x = 0f
                    night4.forEach { (s, m) -> val w = size.width * m / total; if (s == st) drawRoundRect(c, Offset(x, 0f), Size(w, size.height), CornerRadius(size.height / 2)); x += w }
                }
                Tt("${mins / 60}h${(mins % 60).toString().padStart(2, '0')}", 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.width(48.dp).padding(start = 8.dp))
            }
        }
    }
}

@Composable private fun B3() { val d = d()
    Tile(wide) {
        Lab(Icons.Rounded.Bedtime, "Sommeil", d.hues.sleep.b); SleepHead()
        Tt("23:48 → 07:20", 13.sp, d.base.sub)
        Spacer(Modifier.height(12.dp))
        val tot = listOf(1, 2, 3, 4).associateWith { st -> night4.filter { it.first == st }.sumOf { it.second } }; val sum = tot.values.sum().toFloat()
        Row(wide.height(16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(1, 2, 3, 4).forEach { st -> Box(Modifier.weight(tot.getValue(st) / sum).fillMaxHeight().clip(CircleShape).background(d.stage(st))) }
        }
        Spacer(Modifier.height(12.dp))
        listOf(listOf(1 to "Profond", 2 to "Léger"), listOf(3 to "Paradoxal", 4 to "Éveil")).forEach { row ->
            Row(Modifier.padding(vertical = 3.dp)) {
                row.forEach { (st, n) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(d.stage(st))); Tt("  $n", 13.sp, d.base.sub, modifier = Modifier.weight(1f))
                        Tt("${(tot.getValue(st) * 100 / sum).roundToInt()} %", 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(end = 14.dp))
                    }
                }
            }
        }
    }
}

@Composable private fun B4() { val d = d()
    Tile(wide) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = center; val w = 16.dp.toPx(); val r = size.minDimension / 2 - w / 2 - 2.dp.toPx()
                    drawCircle(d.base.s2, r, c, style = Stroke(w))
                    for (i in 0 until 24) {
                        val a = Math.toRadians(-90.0 + 15.0 * i); val r1 = r - w / 2 - 5.dp.toPx(); val r2 = r1 - (if (i % 6 == 0) 7.dp.toPx() else 3.dp.toPx())
                        drawLine(d.base.dim, Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()), Offset(c.x + r2 * cos(a).toFloat(), c.y + r2 * sin(a).toFloat()), 1.5.dp.toPx(), StrokeCap.Round)
                    }
                    var start = -90f + 360f * (23.8f / 24f)
                    night4.forEachIndexed { i, (st, m) ->
                        val sw = 360f * m / 1440f
                        drawArc(d.stage(st), start - 0.3f, sw + 0.6f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r),
                            style = Stroke(w, cap = if (i == 0 || i == night4.lastIndex) StrokeCap.Round else StrokeCap.Butt))
                        start += sw
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Tt("7 h 32", 22.sp, weight = FontWeight.SemiBold, display = true); Tt("score 84", 12.sp, d.base.sub) }
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Coucher" to "23:48", "Lever" to "07:20", "Régularité" to "± 18 min", "Dette" to "− 28 min").forEach { (k, v) ->
                    Column { Tt(k, 12.sp, d.base.sub); Tt(v, 16.sp, weight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable private fun B5() { val d = d()
    Tile(wide) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Lab(Icons.Rounded.Bedtime, "7 dernières nuits", d.hues.sleep.b); Tt("7 h 18 en moyenne", 20.sp, weight = FontWeight.SemiBold, display = true) }
        }
        Spacer(Modifier.height(10.dp))
        Canvas(wide.height(110.dp)) {
            val n = 7; val gap = 12.dp.toPx(); val bw = (size.width - gap * (n - 1)) / n; val max = 540f
            val goal = size.height * (1 - 480f / max)
            drawLine(d.base.dim, Offset(0f, goal), Offset(size.width, goal), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            sleepWeek.forEachIndexed { i, m ->
                val x = i * (bw + gap); var y = size.height
                listOf(1 to 0.2f, 2 to 0.52f, 3 to 0.24f, 4 to 0.04f).forEach { (st, part) ->
                    val hh = size.height * m / max * part
                    drawRoundRect(d.stage(st).copy(alpha = if (i == 6) 1f else 0.75f), Offset(x, y - hh + 1.5f), Size(bw, hh - 1.5f), CornerRadius(4.dp.toPx()))
                    y -= hh
                }
            }
        }
        Row(wide) { days7.forEachIndexed { i, s -> Tt(s, 11.sp, if (i == 6) d.base.ink else d.base.sub, FontWeight.Medium, modifier = Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.Center) } }
    }
}

@Composable private fun B6() { val d = d()
    Tile(wide, pad = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(d.hues.sleep.a.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bedtime, null, tint = d.hues.sleep.b) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Tt("7 h 32 de sommeil", 17.sp, weight = FontWeight.SemiBold, display = true); Tt("Score 84 · couché 23:48", 12.sp, d.base.sub) }
            val tot = listOf(1, 2, 3, 4).associateWith { st -> night4.filter { it.first == st }.sumOf { it.second } }; val sum = tot.values.sum().toFloat()
            Row(Modifier.width(72.dp).height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(1, 2, 3, 4).forEach { st -> Box(Modifier.weight(tot.getValue(st) / sum).fillMaxHeight().clip(CircleShape).background(d.stage(st))) }
            }
        }
    }
}

// =====================================================================================================================
// C. Calories et macros
// =====================================================================================================================

private val macroRows = listOf(Triple("Protéines", 98 to 150, 0), Triple("Lipides", 52 to 76, 1), Triple("Glucides", 141 to 285, 2))
private fun Design.mh(i: Int) = with(hues) { listOf(prot, fat, carb)[i] }

@Composable private fun C1() { val d = d()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(Modifier.weight(1f).height(176.dp)) {
            Lab(Icons.Rounded.LocalFireDepartment, "Calories", d.hues.kcal.a)
            Box(wide.weight(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(112.dp)) { dRing(d, center, size.minDimension / 2 - 7.dp.toPx(), 12.dp.toPx(), 1353f / 2280, d.hues.kcal) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Tt("927", 26.sp, weight = FontWeight.SemiBold, display = true); Tt("restantes", 11.sp, d.base.sub) }
            }
        }
        Spacer(Modifier.weight(0.6f))
    }
}

@Composable private fun C2() { val d = d()
    Tile(wide, pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(66.dp)) {
                val w = 8.dp.toPx(); val r = size.minDimension / 2 - w / 2; val gap = 16f
                listOf(98f / 150, 52f / 76, 141f / 285).forEachIndexed { i, f ->
                    val hue = listOf(d.hues.prot, d.hues.fat, d.hues.carb)[i]; val start = -90f + i * 120f + gap / 2; val span = 120f - gap
                    drawArc(d.track(hue), start, span, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
                    drawArc(hue.a, start, span * f, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.width(96.dp)) { Tt("927", 28.sp, d.hues.kcal.a, FontWeight.SemiBold, true, brush = d.fill(d.hues.kcal)); Tt("kcal restantes", 12.sp, d.base.sub) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                macroRows.forEach { (n, v, i) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tt(n.take(4) + ".", 11.sp, d.base.sub, modifier = Modifier.width(36.dp))
                        Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(d.base.s2)) { Box(Modifier.fillMaxWidth(v.first.toFloat() / v.second).fillMaxHeight().clip(CircleShape).background(d.fill(d.mh(i)))) }
                        Tt("${v.first}", 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.width(34.dp).padding(start = 6.dp), lines = 1)
                    }
                }
            }
        }
    }
}

@Composable private fun C3() { val d = d(); val mealHues = listOf(d.hues.steps, d.hues.kcal, d.hues.repos)
    Tile(wide) {
        Row(verticalAlignment = Alignment.Bottom) {
            Tt("1 353", 30.sp, weight = FontWeight.SemiBold, display = true); Tt(" / 2 280 kcal", 14.sp, d.base.sub, modifier = Modifier.padding(bottom = 5.dp))
            Spacer(Modifier.weight(1f)); Tt("927 restantes", 14.sp, d.hues.kcal.a, FontWeight.SemiBold, modifier = Modifier.padding(bottom = 5.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(wide.height(18.dp).clip(CircleShape).background(d.base.s2), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            meals4.forEachIndexed { i, m -> Box(Modifier.weight(m.third.sumOf { it.kcal }.toFloat()).fillMaxHeight().background(d.fill(mealHues[i]))) }
            Spacer(Modifier.weight(927f))
        }
        Spacer(Modifier.height(12.dp))
        Row {
            meals4.forEachIndexed { i, m ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(mealHues[i].a)); Spacer(Modifier.width(6.dp))
                    Column { Tt(m.first, 12.sp, d.base.sub, lines = 1); Tt("${m.third.sumOf { it.kcal }}", 14.sp, weight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable private fun C4() { val d = d()
    Tile(wide) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Tt("927", 34.sp, weight = FontWeight.SemiBold, display = true); Tt("kcal restantes", 13.sp, d.base.sub)
                Spacer(Modifier.height(4.dp)); Tt("1 353 / 2 280", 12.sp, d.base.sub)
            }
            macroRows.forEach { (n, v, i) ->
                Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(56.dp)) { dRing(d, center, size.minDimension / 2 - 4.dp.toPx(), 7.dp.toPx(), v.first.toFloat() / v.second, d.mh(i)) }
                        Tt(n.take(1), 15.sp, d.mh(i).a, FontWeight.Bold)
                    }
                    Tt("${v.first} g", 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable private fun C5() { val d = d()
    val kc = listOf(98f * 4, 52f * 9, 141f * 4); val sum = kc.sum()
    Tile(wide) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 22.dp.toPx(); val r = size.minDimension / 2 - w / 2; var a = -90f
                    kc.forEachIndexed { i, k ->
                        val sw = 360f * k / sum
                        drawArc(listOf(d.hues.prot, d.hues.fat, d.hues.carb)[i].a, a + 2f, sw - 4f, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(w))
                        a += sw
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Tt("1 353", 20.sp, weight = FontWeight.SemiBold, display = true); Tt("kcal", 11.sp, d.base.sub) }
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                macroRows.forEachIndexed { k, (n, v, i) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(d.mh(i).a)); Spacer(Modifier.width(8.dp))
                        Column { Tt(n, 13.sp, d.base.sub); Row { Tt("${(kc[k] * 100 / sum).roundToInt()} %", 15.sp, weight = FontWeight.SemiBold); Tt("  ${v.first} g", 13.sp, d.base.sub) } }
                    }
                }
            }
        }
    }
}

@Composable private fun C6() { val d = d()
    Tile(wide) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Tt("Il reste", 13.sp, d.base.sub); Tt("927 kcal", 28.sp, weight = FontWeight.SemiBold, display = true); Tt("sur 2 280 aujourd'hui", 12.sp, d.base.sub) }
            Canvas(Modifier.size(150.dp, 62.dp)) {
                val nub = 7.dp.toPx(); val st = 2.dp.toPx(); val body = Size(size.width - nub - 3.dp.toPx(), size.height)
                drawRoundRect(d.base.dim, Offset(st / 2, st / 2), Size(body.width - st, body.height - st), CornerRadius(14.dp.toPx()), style = Stroke(st))
                drawRoundRect(d.base.dim, Offset(body.width + 2.dp.toPx(), size.height * 0.32f), Size(nub, size.height * 0.36f), CornerRadius(3.dp.toPx()))
                val inset = 5.dp.toPx(); val inner = Size(body.width - 2 * inset, body.height - 2 * inset); val cells = 5; val g = 3.dp.toPx()
                val cw = (inner.width - g * (cells - 1)) / cells; val f = 927f / 2280 * cells
                for (i in 0 until cells) {
                    val part = (f - i).coerceIn(0f, 1f); if (part <= 0f) continue
                    drawRoundRect(d.fill(d.hues.kcal), Offset(inset + i * (cw + g), inset), Size(cw * part, inner.height), CornerRadius(8.dp.toPx()))
                }
            }
        }
    }
}

// =====================================================================================================================
// D. Petites tuiles (pas, cœur, poids)
// =====================================================================================================================

@Composable private fun D12() { val d = d()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(Modifier.weight(1f).height(156.dp)) {
            Lab(Icons.AutoMirrored.Rounded.DirectionsRun, "Pas", d.hues.steps.a); Tt("6 240", 26.sp, weight = FontWeight.SemiBold, display = true)
            Spacer(Modifier.weight(1f))
            Canvas(wide.height(48.dp)) {
                val n = 24; val gap = 2.dp.toPx(); val bw = (size.width - gap * (n - 1)) / n; val max = steps4.max().toFloat()
                steps4.forEachIndexed { i, v ->
                    val x = i * (bw + gap)
                    if (i > 16) drawCircle(d.base.dim, bw / 2.4f, Offset(x + bw / 2, size.height - bw / 2))
                    else { val bh = maxOf(bw, size.height * v / max)
                        drawRoundRect(d.vfill(d.hues.steps, size.height - bh, size.height), Offset(x, size.height - bh), Size(bw, bh), CornerRadius(bw / 2), alpha = if (v == 0) 0.3f else 1f) }
                }
            }
        }
        Tile(Modifier.weight(1f).height(156.dp)) {
            Lab(Icons.Rounded.Favorite, "Cœur au repos", d.hues.heart.a)
            Row(verticalAlignment = Alignment.Bottom) { Tt("54", 26.sp, weight = FontWeight.SemiBold, display = true); Tt(" bpm", 13.sp, d.base.sub, modifier = Modifier.padding(bottom = 4.dp)) }
            Spacer(Modifier.weight(1f))
            Canvas(wide.height(48.dp)) {
                val v = heart4.map { it.toFloat() }; val lo = v.min() - 1; val hi = v.max() + 1; val pad = 6.dp.toPx()
                val pts = v.mapIndexed { i, y -> Offset(pad + (size.width - 2 * pad) * i / (v.size - 1), pad + (size.height - 2 * pad) * (hi - y) / (hi - lo)) }
                drawPath(smooth(pts).apply { lineTo(pts.last().x, size.height); lineTo(pts[0].x, size.height); close() }, Brush.verticalGradient(listOf(d.tint(d.hues.heart.a, 0.3f), Color.Transparent)))
                drawPath(smooth(pts), d.fill(d.hues.heart), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                dGlowDot(d, pts.last(), 7.dp.toPx(), d.hues.heart.a)
                drawCircle(d.hues.heart.a, 5.dp.toPx(), pts.last()); drawCircle(d.base.s1, 2.dp.toPx(), pts.last())
            }
        }
    }
}

@Composable private fun D34() { val d = d()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(Modifier.weight(1f).height(156.dp)) {
            Lab(Icons.Rounded.MonitorWeight, "Poids · tendance", d.hues.weight.a)
            Row(verticalAlignment = Alignment.Bottom) { Tt("81,2", 26.sp, weight = FontWeight.SemiBold, display = true); Tt(" kg", 13.sp, d.base.sub, modifier = Modifier.padding(bottom = 4.dp)) }
            Tt("−1,4 kg en 4 sem.", 12.sp, d.hues.forme.a, FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Canvas(wide.height(40.dp)) {
                val raw = weight4.map { it.toFloat() }; var t = raw[0]; val trend = raw.map { t += 0.1f * (it - t); t }
                val lo = raw.min() - 0.2f; val hi = raw.max() + 0.2f; val pad = 4.dp.toPx()
                fun p(i: Int, v: Float) = Offset(pad + (size.width - 2 * pad) * i / (raw.size - 1), pad + (size.height - 2 * pad) * (hi - v) / (hi - lo))
                raw.forEachIndexed { i, v -> drawCircle(d.base.dim, 2.5.dp.toPx(), p(i, v)) }
                drawPath(smooth(trend.mapIndexed { i, v -> p(i, v) }), d.fill(d.hues.weight), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Tile(Modifier.weight(1f).height(156.dp)) {
            Lab(Icons.AutoMirrored.Rounded.DirectionsRun, "Pas", d.hues.steps.a)
            Box(wide.weight(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(100.dp)) { dRing(d, center, size.minDimension / 2 - 6.dp.toPx(), 10.dp.toPx(), 6240f / 8000, d.hues.steps, from = 135f, span = 270f) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Tt("6 240", 19.sp, weight = FontWeight.SemiBold, display = true); Tt("/ 8 000", 11.sp, d.base.sub) }
            }
        }
    }
}

@Composable private fun D5() { val d = d()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple("Cœur au repos", "54 bpm", false), Triple("VFC nocturne", "62 ms", true)).forEach { (l, v, up) ->
            Tile(Modifier.weight(1f), pad = 14.dp) {
                Tt(l, 13.sp, d.base.sub, FontWeight.Medium)
                Tt(v, 22.sp, weight = FontWeight.SemiBold, display = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(d.hues.forme.a.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(if (up) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null, Modifier.size(13.dp), tint = d.hues.forme.a)
                    }
                    Tt(if (up) "  +5 vs moyenne" else "  −2 vs moyenne", 12.sp, d.base.sub)
                }
            }
        }
    }
}

// =====================================================================================================================
// E. Programme de musculation
// =====================================================================================================================

@Composable private fun ProgHead(sub: String = "Semaine 3 · 2 séances sur 4") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Tt("Push Pull Legs", 20.sp, weight = FontWeight.SemiBold, display = true); Tt(sub, 13.sp, d().base.sub) }
        Chev()
    }
}

@Composable private fun E1() { val d = d()
    Tile(wide) {
        ProgHead(); Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            program4.forEach { day ->
                val shape = RoundedCornerShape(20.dp)
                Column(Modifier.weight(1f).clip(shape).background(if (day.next) d.tint(d.hues.effort.a, 0.08f) else d.base.s2)
                    .then(if (day.next) Modifier.border(1.5.dp, d.hues.effort.a, shape) else Modifier).padding(top = 8.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(wide.height(100.dp)) { Body5(day, Modifier.fillMaxSize()); if (day.done) Box(Modifier.align(Alignment.TopEnd).padding(end = 6.dp)) { Check() } }
                    Spacer(Modifier.height(4.dp)); Tt(day.name, 13.sp, if (day.done) d.base.sub else d.base.ink, FontWeight.SemiBold, lines = 1)
                }
            }
        }
        Spacer(Modifier.height(14.dp)); StartButton()
    }
}

@Composable private fun E2() { val d = d()
    Tile(wide) {
        ProgHead(); Spacer(Modifier.height(8.dp))
        program4.forEach { day ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(d.base.s2)) { Body5(day, Modifier.fillMaxSize().padding(4.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Tt(day.name, 16.sp, if (day.done) d.base.sub else d.base.ink, FontWeight.SemiBold); Tt(day.groups, 12.sp, d.base.sub) }
                when { day.done -> Check(22.dp); day.next -> Pill("Prochaine", d.tint(d.hues.effort.a, 0.16f), d.hues.effort.a); else -> Tt("5 exos", 12.sp, d.base.sub) }
            }
        }
        Spacer(Modifier.height(10.dp)); StartButton()
    }
}

@Composable private fun E3() { val d = d(); val next = program4.first { it.next }
    Tile(wide) {
        Row {
            Box(Modifier.size(112.dp, 196.dp).clip(RoundedCornerShape(22.dp)).background(d.base.s2)) {
                Body5(next.copy(from = 0.42f, to = 1f), Modifier.fillMaxSize().padding(8.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Tt("PROCHAINE SÉANCE", 11.sp, d.hues.effort.a, FontWeight.Bold, spacing = 1.sp)
                Tt("Jambes", 26.sp, weight = FontWeight.SemiBold, display = true)
                Tt("5 exercices · ~55 min", 13.sp, d.base.sub)
                Spacer(Modifier.height(10.dp))
                legDay.take(3).forEach { (n, s, id) ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Thumb(id, 36.dp); Spacer(Modifier.width(10.dp))
                        Column { Tt(n, 13.sp, lines = 1); Tt(s, 11.sp, d.base.sub) }
                    }
                }
                Tt("+ 2 autres", 12.sp, d.base.sub, modifier = Modifier.padding(start = 46.dp))
            }
        }
        Spacer(Modifier.height(14.dp)); StartButton()
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            program4.filter { !it.next }.forEach { day ->
                Row(Modifier.weight(1f).clip(CircleShape).background(d.base.s2).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (day.done) Check(15.dp) else Box(Modifier.size(15.dp).clip(CircleShape).border(1.5.dp, d.base.dim, CircleShape))
                    Tt("  ${day.name}", 12.sp, d.base.sub, FontWeight.Medium, lines = 1)
                }
            }
        }
    }
}

@Composable private fun E4() { val d = d()
    Column {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
            Tt("Push Pull Legs", 20.sp, weight = FontWeight.SemiBold, display = true, modifier = Modifier.weight(1f)); Tt("2 / 4", 14.sp, d.base.sub, FontWeight.SemiBold)
        }
        Box(wide.height(214.dp)) {
            Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(program4[2], program4[3], program4[0], program4[1]).forEach { day ->
                    if (day.next) Tile(Modifier.width(232.dp).height(214.dp), pad = 14.dp) {
                        Row(Modifier.weight(1f)) {
                            Body5(day, Modifier.width(92.dp).fillMaxHeight())
                            Column(Modifier.padding(start = 10.dp)) {
                                Pill("Prochaine", d.tint(d.hues.effort.a, 0.16f), d.hues.effort.a); Spacer(Modifier.height(8.dp))
                                Tt("Jambes", 22.sp, weight = FontWeight.SemiBold, display = true); Tt("Cuisses · Mollets", 12.sp, d.base.sub)
                                Spacer(Modifier.height(6.dp)); Tt("5 exos · 55 min", 12.sp, d.base.sub)
                            }
                        }
                        Spacer(Modifier.height(10.dp)); StartButton("Commencer")
                    } else Tile(Modifier.width(118.dp).height(214.dp), pad = 12.dp) {
                        Box(wide.weight(1f)) { Body5(day, Modifier.fillMaxSize()); if (day.done) Box(Modifier.align(Alignment.TopEnd)) { Check() } }
                        Tt(day.name, 15.sp, if (day.done) d.base.sub else d.base.ink, FontWeight.SemiBold); Tt(if (day.done) "Faite" else "5 exos", 11.sp, d.base.sub)
                    }
                }
            }
        }
    }
}

@Composable private fun E5() { val d = d(); val next = program4.first { it.next }
    Tile(wide) {
        ProgHead("Semaine 3")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            program4.forEach { day ->
                Column(Modifier.weight(1f)) {
                    Box(wide.height(8.dp).clip(CircleShape).background(when { day.done -> d.fill(d.hues.forme); day.next -> SolidColor(d.hues.effort.a); else -> SolidColor(d.base.s2) }))
                    Tt(day.name, 11.sp, if (day.next) d.base.ink else d.base.sub, if (day.next) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 5.dp), lines = 1)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.clip(RoundedCornerShape(20.dp)).background(d.base.s2).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Body5(next, Modifier.size(56.dp, 74.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Tt("Jambes", 18.sp, weight = FontWeight.SemiBold, display = true); Tt("Squat, roumain, bulgares…", 12.sp, d.base.sub, lines = 1) }
            Box(Modifier.size(52.dp).clip(CircleShape).background(d.fill(d.hues.effort)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(28.dp), tint = Color.White) }
        }
    }
}

@Composable private fun E6() { val d = d(); val next = program4.first { it.next }
    Tile(wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            program4.forEach { day ->
                val sel = day.next
                Row(Modifier.clip(CircleShape).background(if (sel) d.base.ink else d.base.s2).padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (day.done) { Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = d.hues.forme.a); Spacer(Modifier.width(4.dp)) }
                    Tt(day.name, 13.sp, if (sel) d.base.bg else d.base.sub, FontWeight.SemiBold, lines = 1)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Body5(next.copy(side = "front", from = 0.38f, to = 1f), Modifier.size(96.dp, 190.dp))
            Body5(next.copy(side = "back", target = setOf("glutes", "hamstrings", "calves"), from = 0.38f, to = 1f), Modifier.size(96.dp, 190.dp))
            Column(Modifier.padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Quadriceps", "Ischios", "Fessiers", "Mollets").forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(d.hues.effort.a)); Tt("  $m", 13.sp) }
                }
                Tt("5 exos · 55 min", 12.sp, d.base.sub)
            }
        }
        Spacer(Modifier.height(12.dp)); StartButton()
    }
}

// =====================================================================================================================
// F. Semaine d'activité
// =====================================================================================================================

@Composable private fun F1() { val d = d()
    Row(wide.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) { Tt("3", 22.sp, weight = FontWeight.SemiBold, display = true); Tt(" activités", 14.sp, d.base.sub, modifier = Modifier.padding(bottom = 2.dp)) }
            Tt("2 h 09 cette semaine", 12.sp, d.base.sub)
        }
        actWeek.forEachIndexed { i, m ->
            Column(Modifier.width(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.width(7.dp).height(34.dp)) {
                    val bh = if (m > 0) size.height * m / 52 else size.width
                    drawRoundRect(if (m > 0) d.vfill(d.hues.effort, size.height - bh, size.height) else SolidColor(d.base.dim), Offset(0f, size.height - bh), Size(size.width, bh), CornerRadius(size.width / 2), alpha = if (m < 0) 0.4f else 1f)
                }
                Spacer(Modifier.height(4.dp)); Tt(days7[i], 10.sp, if (i == 4) d.base.ink else d.base.sub, FontWeight.Medium)
            }
        }
    }
}

@Composable private fun F2() { val d = d()
    val icons = listOf(Icons.AutoMirrored.Rounded.DirectionsBike, Icons.Rounded.FitnessCenter, null, Icons.AutoMirrored.Rounded.DirectionsRun, null, null, null)
    Row(wide, horizontalArrangement = Arrangement.SpaceBetween) {
        days7.forEachIndexed { i, s ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(if (icons[i] != null) d.fill(d.hues.effort) else SolidColor(Color.Transparent))
                    .border(if (i == 4) 2.dp else 1.dp, if (i == 4) d.base.ink else if (icons[i] != null) Color.Transparent else d.base.dim, CircleShape), contentAlignment = Alignment.Center) {
                    icons[i]?.let { Icon(it, null, Modifier.size(20.dp), tint = Color.White) }
                }
                Tt(s, 11.sp, if (i == 4) d.base.ink else d.base.sub, FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable private fun F3() { val d = d()
    Tile(wide, pad = 14.dp) {
        Row(wide, horizontalArrangement = Arrangement.SpaceBetween) {
            actWeek.forEachIndexed { i, m ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(38.dp)) { dRing(d, center, size.minDimension / 2 - 3.dp.toPx(), 5.dp.toPx(), maxOf(m, 0) / 60f, d.hues.effort) }
                        Tt(if (m > 0) "$m" else "", 10.sp, weight = FontWeight.SemiBold)
                    }
                    Tt(days7[i], 11.sp, if (i == 4) d.base.ink else d.base.sub, FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable private fun F4() { val d = d()
    val weeks = listOf(listOf(40, 0, 55, 0, 30, 70, 0), listOf(45, 60, 0, 35, 0, 90, 20), listOf(50, 0, 45, 0, 40, 0, 65), actWeek)
    Tile(wide, pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) { Tt("Octobre", 15.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Tt("14 activités · 13 h 20", 12.sp, d.base.sub) }
        Spacer(Modifier.height(10.dp))
        Row(wide) { days7.forEach { Tt(it, 10.sp, d.base.sub, modifier = Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.Center) } }
        weeks.forEachIndexed { w, wk ->
            Row(wide.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                wk.forEachIndexed { i, m ->
                    val today = w == 3 && i == 4
                    Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(7.dp))
                        .background(if (m > 0) d.hues.forme.a.copy(alpha = 0.25f + 0.75f * minOf(m, 90) / 90f) else if (m < 0) Color.Transparent else d.base.s2)
                        .then(if (today) Modifier.border(1.5.dp, d.base.ink, RoundedCornerShape(7.dp)) else Modifier))
                }
            }
        }
    }
}

// =====================================================================================================================
// G. Repas
// =====================================================================================================================

@Composable private fun G1() { val d = d(); val (name, time, foods) = meals4[1]
    Tile(wide, pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(d.hues.kcal.a)); Spacer(Modifier.width(8.dp))
            Tt(name, 17.sp, weight = FontWeight.SemiBold, display = true, modifier = Modifier.weight(1f)); Tt("$time · ${foods.sumOf { it.kcal }} kcal", 13.sp, d.base.sub)
        }
        foods.forEach { f ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(d.base.s2), contentAlignment = Alignment.Center) { PixelIcon(f.icon, 28.dp) }
                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Tt(f.name, 15.sp, lines = 1); Tt(f.qty, 12.sp, d.base.sub) }
                Tt("${f.kcal}", 14.sp, d.base.sub, FontWeight.Medium)
            }
        }
    }
}

@Composable private fun G2() { val d = d(); val hues = listOf(d.hues.steps, d.hues.kcal, d.hues.repos)
    Tile(wide, pad = 14.dp) {
        meals4.forEachIndexed { i, (name, time, foods) ->
            Row {
                Column(Modifier.width(46.dp)) { Tt(time, 13.sp, weight = FontWeight.SemiBold); Tt("${foods.sumOf { it.kcal }}", 11.sp, d.base.sub) }
                Column(Modifier.width(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape).background(hues[i].a))
                    if (i < 2) Box(Modifier.width(2.dp).height(18.dp + 34.dp * ((foods.size + 1) / 2)).background(d.base.s2))
                }
                Column(Modifier.weight(1f).padding(start = 8.dp, bottom = 12.dp)) {
                    Tt(name, 15.sp, weight = FontWeight.SemiBold)
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        foods.forEach { f ->
                            Row(Modifier.clip(CircleShape).background(d.base.s2).padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                PixelIcon(f.icon, 22.dp); Tt(" ${f.name}", 12.sp, lines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun G3() { val d = d(); val (name, time, foods) = meals4[1]
    Tile(wide, pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
            Tt(name, 17.sp, weight = FontWeight.SemiBold, display = true, modifier = Modifier.weight(1f)); Tt("$time · ${foods.sumOf { it.kcal }} kcal", 13.sp, d.base.sub)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 4) {
            (foods + Food("Ajouter", "", 0, "")).forEach { f ->
                Column(Modifier.width(74.dp).clip(RoundedCornerShape(18.dp)).background(d.base.s2).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (f.kcal == 0) { Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = d.base.sub) }; Tt("Ajouter", 12.sp, d.base.sub) }
                    else { PixelIcon(f.icon, 36.dp); Tt(f.name.substringBefore(' '), 12.sp, lines = 1); Tt("${f.kcal}", 11.sp, d.base.sub) }
                }
            }
        }
    }
}

@Composable private fun G4() { val d = d(); val (name, time, foods) = meals4[1]
    Tile(wide, pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
            Tt(name, 17.sp, weight = FontWeight.SemiBold, display = true, modifier = Modifier.weight(1f)); Tt("$time · ${foods.sumOf { it.kcal }} kcal", 13.sp, d.base.sub)
        }
        foods.forEach { f ->
            val (p, l, g) = macros.getValue(f.name); val kc = listOf(p * 4, l * 9, g * 4); val s = kc.sum()
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                PixelIcon(f.icon, 32.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row { Tt(f.name, 15.sp, modifier = Modifier.weight(1f), lines = 1); Tt("${f.kcal}", 14.sp, weight = FontWeight.SemiBold) }
                    Spacer(Modifier.height(5.dp))
                    Row(wide.height(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        kc.forEachIndexed { i, k -> if (k > 0) Box(Modifier.weight(k / s).fillMaxHeight().clip(CircleShape).background(d.mh(i).a)) }
                    }
                    Tt("${f.qty} · P ${p.roundToInt()} · L ${l.roundToInt()} · G ${g.roundToInt()}", 11.sp, d.base.sub, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

@Composable private fun G5() { val d = d()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        meals4.forEach { (name, time, foods) ->
            Tile(wide, pad = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(104.dp)) { Tt(name, 15.sp, weight = FontWeight.SemiBold, lines = 1); Tt("$time · ${foods.sumOf { it.kcal }} kcal", 12.sp, d.base.sub) }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                        foods.forEach { f -> Box(Modifier.size(40.dp).clip(CircleShape).background(d.base.s2).border(2.dp, d.base.s1, CircleShape), contentAlignment = Alignment.Center) { PixelIcon(f.icon, 26.dp) } }
                    }
                    Chev()
                }
            }
        }
    }
}

// =====================================================================================================================
// H. Navigation (bande du bas ou du haut)
// =====================================================================================================================

private val tabs = listOf(Icons.Rounded.Today to "Aujourd'hui", Icons.Rounded.Restaurant to "Nutrition", Icons.AutoMirrored.Rounded.DirectionsRun to "Activités")

@Composable private fun Fab(square: Boolean = true, size: Dp = 60.dp) { val d = d(); val shape = if (square) RoundedCornerShape(20.dp) else CircleShape
    Box(Modifier.size(size).shadow(if (d.dark) 0.dp else 8.dp, shape).clip(shape).background(d.fill(d.hues.action)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Add, null, Modifier.size(30.dp), tint = Color.White)
    }
}

@Composable private fun NavBand(top: Boolean = false, content: @Composable BoxScope.() -> Unit) =
    Box(wide.height(if (top) 128.dp else 104.dp).clip(RoundedCornerShape(d().radius)).background(d().base.bg).border(1.dp, d().base.line, RoundedCornerShape(d().radius)), content = content)

@Composable private fun H1() { val d = d()
    NavBand {
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.shadow(if (d.dark) 0.dp else 8.dp, CircleShape).clip(CircleShape).background(d.base.s2).padding(5.dp)) {
                tabs.forEachIndexed { i, (ic, l) ->
                    val on = i == 2
                    Row(Modifier.clip(CircleShape).background(if (on) d.base.ink else Color.Transparent).padding(horizontal = if (on) 15.dp else 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(ic, null, Modifier.size(22.dp), tint = if (on) d.base.bg else d.base.sub); if (on) Tt("  $l", 14.sp, d.base.bg, FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.weight(1f)); Fab(false, 56.dp)
        }
    }
}

@Composable private fun H2() { val d = d()
    NavBand {
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(d.base.s1).padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { i, (ic, l) ->
                val on = i == 2
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp, 30.dp).clip(CircleShape).background(if (on) d.hues.action.a.copy(alpha = 0.18f) else Color.Transparent), contentAlignment = Alignment.Center) {
                        Icon(ic, null, Modifier.size(22.dp), tint = if (on) d.hues.action.a else d.base.sub)
                    }
                    Tt(l, 11.sp, if (on) d.base.ink else d.base.sub, if (on) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.padding(top = 3.dp))
                }
            }
            Fab(true, 52.dp)
        }
    }
}

@Composable private fun H3() { val d = d()
    NavBand {
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.shadow(if (d.dark) 0.dp else 8.dp, CircleShape).clip(CircleShape).background(d.base.s2).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { i, (ic, _) ->
                    Column(Modifier.padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(ic, null, Modifier.size(24.dp), tint = if (i == 2) d.base.ink else d.base.sub)
                        Box(Modifier.padding(top = 4.dp).size(4.dp).clip(CircleShape).background(if (i == 2) d.hues.action.a else Color.Transparent))
                    }
                }
                Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(28.dp).background(d.base.dim))
                Box(Modifier.padding(horizontal = 8.dp)) { Icon(Icons.Rounded.Search, null, Modifier.size(24.dp), tint = d.base.sub) }
            }
            Spacer(Modifier.weight(1f)); Fab(false, 56.dp)
        }
    }
}

@Composable private fun H4() { val d = d()
    NavBand {
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(CircleShape).background(d.base.s2).padding(4.dp)) {
                tabs.forEachIndexed { i, (_, l) ->
                    val on = i == 2
                    Box(Modifier.weight(1f).clip(CircleShape).background(if (on) d.base.s1 else Color.Transparent).padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                        Tt(l, 13.sp, if (on) d.base.ink else d.base.sub, if (on) FontWeight.SemiBold else FontWeight.Medium, lines = 1)
                    }
                }
            }
            Spacer(Modifier.width(12.dp)); Fab(false, 52.dp)
        }
    }
}

@Composable private fun H5() { val d = d()
    NavBand(top = true) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(d.base.s2), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, null, Modifier.size(21.dp), tint = d.base.ink) }
                Spacer(Modifier.weight(1f))
                Row(Modifier.clip(CircleShape).background(d.base.s2).padding(4.dp)) {
                    tabs.forEachIndexed { i, (ic, l) ->
                        val on = i == 2
                        Row(Modifier.clip(CircleShape).background(if (on) d.base.ink else Color.Transparent).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(ic, null, Modifier.size(19.dp), tint = if (on) d.base.bg else d.base.sub); if (on) Tt(" $l", 13.sp, d.base.bg, FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).background(d.fill(d.hues.repos)), contentAlignment = Alignment.Center) { Tt("B", 16.sp, Color.White, FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(10.dp))
            Tt("Activités", 30.sp, weight = FontWeight.SemiBold, display = true)
        }
    }
}

@Composable private fun H6() { val d = d()
    NavBand(top = true) {
        Column(Modifier.padding(top = 16.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Tt("VEN. 30 OCT.", 12.sp, d.base.sub, FontWeight.SemiBold, spacing = 1.4.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = d.base.ink); Spacer(Modifier.width(14.dp))
                Box(Modifier.size(34.dp).clip(CircleShape).background(d.fill(d.hues.repos)), contentAlignment = Alignment.Center) { Tt("B", 14.sp, Color.White, FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                tabs.forEachIndexed { i, (_, l) ->
                    Column(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
                        Tt(l, 19.sp, if (i == 2) d.base.ink else d.base.sub, FontWeight.SemiBold, true)
                        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp).clip(CircleShape).background(if (i == 2) d.fill(d.hues.action) else SolidColor(Color.Transparent)))
                    }
                }
            }
            Box(wide.height(1.dp).background(d.base.line))
        }
    }
}

@Composable private fun H7() { val d = d()
    NavBand {
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { i, (ic, l) ->
                Column(Modifier.padding(end = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(ic, null, Modifier.size(25.dp), tint = if (i == 2) d.base.ink else d.base.dim)
                    Tt(l, 10.sp, if (i == 2) d.base.ink else d.base.sub, FontWeight.Medium, modifier = Modifier.padding(top = 3.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(width = 104.dp, height = 52.dp).clip(CircleShape).background(d.fill(d.hues.action)), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Add, null, Modifier.size(24.dp), tint = Color.White); Tt(" Noter", 15.sp, Color.White, FontWeight.SemiBold) }
            }
        }
    }
}

// =====================================================================================================================
// I. Activité récente
// =====================================================================================================================

private val route = listOf(0.08f to 0.78f, 0.2f to 0.52f, 0.16f to 0.26f, 0.36f to 0.12f, 0.55f to 0.3f, 0.7f to 0.14f, 0.92f to 0.3f, 0.86f to 0.62f, 0.62f to 0.7f, 0.46f to 0.88f, 0.22f to 0.84f)

@Composable private fun I1() { val d = d()
    Tile(wide) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Lab(Icons.AutoMirrored.Rounded.DirectionsRun, "Course · hier", d.hues.forme.a)
                Row(verticalAlignment = Alignment.Bottom) { Tt("5,2", 34.sp, weight = FontWeight.SemiBold, display = true); Tt(" km", 15.sp, d.base.sub, modifier = Modifier.padding(bottom = 5.dp)) }
                Tt("28:41 · 5:31 /km", 13.sp, d.base.sub)
            }
            Canvas(Modifier.size(130.dp, 82.dp)) {
                val pts = route.map { Offset(it.first * size.width, it.second * size.height) }; val r = smooth(pts)
                if (d.glow > 0f) glow(r, d.hues.forme.b.copy(alpha = 0.6f * d.glow), 6.dp.toPx(), 4.dp.toPx())
                drawPath(r, d.fill(d.hues.forme), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(d.base.ink, 4.dp.toPx(), pts.first()); drawCircle(d.hues.forme.a, 4.dp.toPx(), pts.last(), style = Stroke(2.dp.toPx()))
            }
        }
    }
}

@Composable private fun I2() { val d = d()
    Tile(wide, pad = 0.dp) {
        Box(wide.height(128.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val alt = listOf(.7f, .62f, .66f, .5f, .42f, .48f, .3f, .36f, .52f, .6f, .56f, .68f, .72f)
                val pts = alt.mapIndexed { i, a -> Offset(size.width * i / (alt.size - 1), size.height * (0.5f + a * 0.45f)) }
                drawPath(smooth(pts).apply { lineTo(size.width, size.height); lineTo(0f, size.height); close() }, Brush.verticalGradient(listOf(d.tint(d.hues.forme.a, 0.3f), Color.Transparent)))
                drawPath(smooth(pts), d.fill(d.hues.forme), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
            Column(Modifier.padding(16.dp)) {
                Lab(Icons.AutoMirrored.Rounded.DirectionsRun, "Course · hier", d.hues.forme.a)
                Row(verticalAlignment = Alignment.Bottom) { Tt("5,2 km", 26.sp, weight = FontWeight.SemiBold, display = true); Tt("   28:41 · D+ 64 m", 13.sp, d.base.sub, modifier = Modifier.padding(bottom = 4.dp)) }
            }
        }
    }
}

@Composable private fun I3() { val d = d()
    val splits = listOf("1" to 338, "2" to 329, "3" to 331, "4" to 327, "5" to 324)
    Tile(wide) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) { Lab(Icons.AutoMirrored.Rounded.DirectionsRun, "Course · hier", d.hues.forme.a); Tt("5,2 km · 28:41", 22.sp, weight = FontWeight.SemiBold, display = true) }
            Pill("Négatif", d.tint(d.hues.forme.a, 0.16f), d.hues.forme.a, Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        splits.forEach { (k, s) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Tt("km $k", 12.sp, d.base.sub, modifier = Modifier.width(44.dp))
                Box(Modifier.weight(1f).height(14.dp)) {
                    Box(Modifier.fillMaxWidth(1f - (s - 300) / 100f).fillMaxHeight().clip(CircleShape).background(if (s == splits.minOf { it.second }) d.fill(d.hues.forme) else SolidColor(d.base.s2)))
                }
                Tt("${s / 60}:${(s % 60).toString().padStart(2, '0')}", 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.width(44.dp).padding(start = 8.dp))
            }
        }
    }
}

// =====================================================================================================================

val FAMILIES = listOf(
    Family("A", "Forme", listOf(W("A1", "Anneaux concentriques") { A1() }, W("A2", "Grand chiffre + barres") { A2() }, W("A3", "Jauge à aiguille") { A3() },
        W("A4", "Trois cadrans") { A4() }, W("A5", "Chiffre + semaine") { A5() }, W("A6", "Bandeau compact") { A6() }, W("A7", "Arc de graduations") { A7() },
        W("A8", "Carte pleine couleur") { A8() })),
    Family("B", "Sommeil", listOf(W("B1", "Courbe en marches") { B1() }, W("B2", "Couloirs par phase") { B2() }, W("B3", "Barre des phases") { B3() },
        W("B4", "Cadran 24 h") { B4() }, W("B5", "Semaine empilée") { B5() }, W("B6", "Ligne compacte") { B6() })),
    Family("C", "Calories et macros", listOf(W("C1", "Anneau (tuile ½)") { C1() }, W("C2", "Rangée compacte") { C2() }, W("C3", "Barre des repas") { C3() },
        W("C4", "Trois anneaux macros") { C4() }, W("C5", "Assiette des macros") { C5() }, W("C6", "Batterie") { C6() })),
    Family("D", "Petites tuiles", listOf(W("D1·D2", "Pas par heure · cœur") { D12() }, W("D3·D4", "Poids lissé · pas en anneau") { D34() }, W("D5", "Écart à la moyenne") { D5() })),
    Family("E", "Programme muscu", listOf(W("E1", "Quatre corps") { E1() }, W("E2", "Liste") { E2() }, W("E3", "Prochaine séance détaillée") { E3() },
        W("E4", "Cartes qui défilent") { E4() }, W("E5", "Progression + prochaine") { E5() }, W("E6", "Anatomie face/dos") { E6() })),
    Family("F", "Semaine d'activité", listOf(W("F1", "Barres fines") { F1() }, W("F2", "Pastilles par sport") { F2() }, W("F3", "Anneaux de minutes") { F3() }, W("F4", "Mois en carreaux") { F4() })),
    Family("G", "Journal des repas", listOf(W("G1", "Tuile repas") { G1() }, W("G2", "Fil de la journée") { G2() }, W("G3", "Grille d'aliments") { G3() },
        W("G4", "Macros par aliment") { G4() }, W("G5", "Repas en une ligne") { G5() })),
    Family("H", "Navigation", listOf(W("H1", "Pilule flottante + rond") { H1() }, W("H2", "Barre pleine + carré") { H2() }, W("H3", "Dock avec recherche") { H3() },
        W("H4", "Segments texte") { H4() }, W("H5", "Îlot en haut") { H5() }, W("H6", "Onglets en haut") { H6() }, W("H7", "Icônes nues + « Noter »") { H7() })),
    Family("I", "Activité récente", listOf(W("I1", "Tracé") { I1() }, W("I2", "Profil d'altitude") { I2() }, W("I3", "Allure par km") { I3() })),
)

@Composable
fun CatalogSheet(f: Family) {
    val d = d()
    Column(Modifier.fillMaxSize().background(d.base.bg).padding(horizontal = 16.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Tt(f.key, 40.sp, d.hues.action.a, FontWeight.Bold, true); Spacer(Modifier.width(10.dp))
            Tt(f.title, 26.sp, weight = FontWeight.SemiBold, display = true, modifier = Modifier.padding(bottom = 5.dp))
        }
        f.items.forEach { w ->
            Column {
                Row(Modifier.padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(w.id, d.base.ink, d.base.bg, size = 13.sp); Spacer(Modifier.width(8.dp)); Tt(w.name, 14.sp, d.base.sub, FontWeight.Medium)
                }
                w.content()
            }
        }
    }
}
