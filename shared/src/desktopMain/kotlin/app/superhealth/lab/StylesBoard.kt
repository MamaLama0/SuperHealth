package app.superhealth.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.MonitorHeart
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Tour 6 : six styles complets. Les widgets retenus (A4, A6, B1, B3, B4, B6, C2, C3, C4, petites tuiles D) sont écrits
 * une seule fois sur le socle [Style] et prennent la forme de chaque style.
 */

private fun hu(a: Long, b: Long = a) = Hue(Color(a), Color(b))

private val PASTEL_H = MHues(hu(0xFF1E9C72, 0xFF22A3A0), hu(0xFF3B5BDB), hu(0xFF7048E8), hu(0xFFF0622A), hu(0xFF0E958B), hu(0xFF5F3DC4, 0xFF8E6CEF),
    hu(0xFFE03160), hu(0xFFE89B00), hu(0xFFE03160), hu(0xFFE89B00), hu(0xFF1E9C72), hu(0xFF1C7ED6), hu(0xFF7048E8))
private val TRAIT_H = MHues(hu(0xFF2F9E6E), hu(0xFF2C6BE0), hu(0xFF6C4FD8), hu(0xFFE2552D), hu(0xFF158F86), hu(0xFF5546D6, 0xFF8577F0),
    hu(0xFFD9365E), hu(0xFFD99100), hu(0xFFD9365E), hu(0xFFD99100), hu(0xFF2F9E6E), hu(0xFF2C6BE0), hu(0xFF111111))
private val DOUX_H = MHues(hu(0xFF7FDDBA, 0xFF86C9F0), hu(0xFF7AA7FF, 0xFF9CC8FF), hu(0xFFA08CFF, 0xFFC9A6FF), hu(0xFFFF9A7A, 0xFFFF8FB3), hu(0xFF6FD8C8, 0xFF7FC4F5),
    hu(0xFF9C9AFF, 0xFFC7A9FF), hu(0xFFFF7F98, 0xFFFFB0C4), hu(0xFFFFD866, 0xFFFFB866), hu(0xFFFF8FA3), hu(0xFFFFD27A), hu(0xFF8FE3B5), hu(0xFF86C9F0), hu(0xFFA08CFF, 0xFF7AA7FF))
private val ENCRE_H = TRAIT_H.copy(forme = hu(0xFF13A86B), action = hu(0xFF0D0D0D))

val STYLES = listOf(
    Style("nuit", "Nuit", "Sombre, tuiles pleines, données en dégradé",
        Base("nuit", true, Color(0xFF0E0F12), Color(0xFF18191D), Color(0xFF24252B), Color(0x10FFFFFF), Color(0xFFF3F3F6), Color(0xFF9A9BA4), Color(0xFF44454C)),
        VIF_DARK, nav = NavS.PILL),
    Style("pastel", "Pastel", "Clair, une teinte par mesure, coins en feuille, traits épais",
        Base("pastel", false, Color(0xFFF6F5F1), Color.White, Color(0xFFEEECE7), Color(0x10000000), Color(0xFF1D1B20), Color(0xFF66636B), Color(0xFFCFCCD3)),
        PASTEL_H, card = CardM.TINTED, corner = Corner.LEAF, radius = 34.dp, stroke = 1.35f, icon = IconBox.COOKIE, gradient = false, nav = NavS.BAR),
    Style("plein", "Plein", "Chaque carte prend la couleur de sa mesure",
        Base("plein", true, Color(0xFF0B0B0E), Color(0xFF17171B), Color(0xFF222227), Color(0x10FFFFFF), Color.White, Color(0xFF9A9AA2), Color(0xFF3F3F46)),
        VIF_DARK, card = CardM.SOLID, radius = 30.dp, stroke = 1.15f, numW = FontWeight.Bold, icon = IconBox.CIRCLE, nav = NavS.DOCK),
    Style("trait", "Trait", "Clair et précis : filets, traits fins coupés, segments, capitales",
        Base("trait", false, Color(0xFFF7F7F5), Color(0xFFFDFDFC), Color(0xFFEFEFEC), Color(0x1F000000), Color(0xFF111111), Color(0xFF75757A), Color(0xFFD4D4D2)),
        TRAIT_H, card = CardM.OUTLINED, corner = Corner.SQUARE, radius = 10.dp, stroke = 0.6f, butt = true, bar = BarS.SEGMENT,
        text = Inter, display = InterDisplay, numW = FontWeight.Medium, caps = true, gradient = false, area = false, nav = NavS.TOPTABS),
    Style("ardoise", "Ardoise", "Sombre bleuté, couleurs douces, cartes teintées",
        ARDOISE, DOUX_H, card = CardM.TINTED, radius = 34.dp, stroke = 1.5f, icon = IconBox.SQUIRCLE, nav = NavS.SEGMENTS),
    Style("encre", "Encre", "Clair monochrome, une seule couleur d'accent",
        Base("encre", false, Color(0xFFEDEDEB), Color.White, Color(0xFFF1F1EF), Color(0x10000000), Color(0xFF0D0D0D), Color(0xFF6E6E6E), Color(0xFFD0D0CE)),
        ENCRE_H, card = CardM.ELEVATED, radius = 24.dp, numW = FontWeight.Bold, icon = IconBox.CIRCLE, gradient = false, mono = true, nav = NavS.NOTER),
)

private val W = Modifier.fillMaxWidth()

@Composable private fun ScorePill(text: String, hue: Hue) {
    val hd = data(hue, true); val f = sf(); val s = st()
    Box(Modifier.clip(if (s.corner == Corner.SQUARE) RoundedCornerShape(4.dp) else CircleShape).background(if (f.on) f.ink.copy(alpha = 0.2f) else track(hue)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Tx(text, 12.sp, Role.LABEL, color = if (f.on) f.ink else if (s.mono) s.base.ink else if (s.dark) hd.b else hd.a)
    }
}

@Composable private fun Chev5() = Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = sf().sub)

@Composable private fun stages(): Map<Int, Color> { val h = st().hues
    val l = series(listOf(h.sleep, Hue(h.sleep.b, h.sleep.b), Hue(h.charge.b, h.charge.b), Hue(h.heart.b, h.heart.b)))
    return mapOf(1 to l[0].a, 2 to l[1].a, 3 to l[2].a, 4 to l[3].a)
}

// ---- A ----------------------------------------------------------------------------------------------------------------------

@Composable fun VA4() { val s = st(); val h = s.hues
    Card5(h.forme, W) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tx("Forme", 17.sp, Role.DISPLAY); Spacer(Modifier.width(8.dp)); Tx("78", 17.sp, Role.NUM, color = data(h.forme, true).a)
            Spacer(Modifier.weight(1f)); Tx("Prêt à pousser", 13.sp, Role.LABEL)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            listOf(Triple("Charge", 64, h.charge), Triple("Repos", 84, h.repos), Triple("Effort", 42, h.effort)).forEach { (n, v, hue) ->
                val hd = data(hue); val tr = track(hue)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(86.dp)) { val w = 10.dp.toPx() * s.stroke; arc5(s, center, size.minDimension / 2 - w / 2 - 2.dp.toPx(), w, v / 100f, hd, tr, 135f, 270f) }
                        Tx("$v", 24.sp, Role.NUM)
                    }
                    Tx(n, 13.sp, Role.LABEL, modifier = Modifier.offset(y = (-6).dp))
                }
            }
        }
    }
}

@Composable fun VA6() { val s = st(); val h = s.hues
    Card5(h.forme, W, pad = 12.dp) {
        val f = sf()
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shape = when (s.icon) { IconBox.COOKIE -> cookie(9, 0.07f); IconBox.SQUIRCLE -> RoundedCornerShape(18.dp); else -> if (s.corner == Corner.SQUARE) RoundedCornerShape(8.dp) else CircleShape }
            val hf = data(h.forme, true)
            Box(Modifier.size(56.dp).clip(shape).background(if (f.on) SolidColor(f.ink.copy(alpha = 0.22f)) else brush(hf)), contentAlignment = Alignment.Center) {
                Tx("78", 21.sp, Role.NUM, color = if (f.on) f.ink else if (hf.a.luminance() > 0.62f) Color(0xFF0C1A15) else Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Tx("Prêt à pousser", 17.sp, Role.DISPLAY)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(Triple("Charge", 64, h.charge), Triple("Repos", 84, h.repos), Triple("Effort", 42, h.effort)).forEachIndexed { i, (_, v, hue) ->
                        Box(Modifier.size(7.dp).clip(if (s.corner == Corner.SQUARE) RoundedCornerShape(1.dp) else CircleShape).background(data(hue).a))
                        Tx(" $v", 13.sp, color = f.sub); if (i < 2) Spacer(Modifier.width(10.dp))
                    }
                }
            }
            Chev5()
        }
    }
}

// ---- B ----------------------------------------------------------------------------------------------------------------------

@Composable private fun SleepTop() {
    Row(verticalAlignment = Alignment.Bottom) {
        Tx("7", 30.sp, Role.NUM); Tx(" h ", 16.sp, color = sf().sub, modifier = Modifier.padding(bottom = 4.dp)); Tx("32", 30.sp, Role.NUM)
        Spacer(Modifier.weight(1f)); Box(Modifier.padding(bottom = 6.dp)) { ScorePill("Score 84", st().hues.sleep) }
    }
}

@Composable fun VB1() { val s = st(); val h = s.hues
    Card5(h.sleep, W) {
        Label5(Icons.Rounded.Bedtime, "Sommeil", h.sleep); SleepTop()
        Spacer(Modifier.height(8.dp))
        val cols = stages(); val single = sf().on || s.mono
        Canvas(W.height(76.dp)) {
            val total = night4.sumOf { it.second }.toFloat(); val pad = 5.dp.toPx(); val r = if (s.butt) 0.01f else 5.dp.toPx()
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
            val main = cols.getValue(1)
            if (s.area) drawPath(Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, Brush.verticalGradient(listOf(main.copy(alpha = 0.28f), Color.Transparent)))
            if (!s.area) for (k in 1..4) drawLine(cols.getValue(1).copy(alpha = 0.12f), Offset(0f, y(k)), Offset(size.width, y(k)), 1f)
            drawPath(line, if (single) SolidColor(main) else Brush.verticalGradient(listOf(cols.getValue(4), cols.getValue(3), cols.getValue(2), cols.getValue(1)), pad, size.height - pad),
                style = Stroke(3.dp.toPx() * s.stroke, cap = s.cap, join = if (s.butt) StrokeJoin.Miter else StrokeJoin.Round))
        }
        Row { Tx("23:48", 11.sp, Role.LABEL); Spacer(Modifier.weight(1f)); Tx("07:20", 11.sp, Role.LABEL) }
    }
}

@Composable private fun PhaseBar(height: Dp) {
    val s = st(); val cols = stages()
    val tot = listOf(1, 2, 3, 4).associateWith { k -> night4.filter { it.first == k }.sumOf { it.second } }; val sum = tot.values.sum().toFloat()
    val shape = when (s.bar) { BarS.PILL -> CircleShape; else -> RoundedCornerShape(2.dp) }
    Row(W.height(height), horizontalArrangement = Arrangement.spacedBy(if (s.bar == BarS.SEGMENT) 2.dp else 3.dp)) {
        listOf(1, 2, 3, 4).forEach { k -> Box(Modifier.weight(tot.getValue(k) / sum).fillMaxHeight().clip(shape).background(cols.getValue(k))) }
    }
}

@Composable fun VB3() { val s = st(); val h = s.hues
    Card5(h.sleep, W) {
        Label5(Icons.Rounded.Bedtime, "Sommeil", h.sleep); SleepTop()
        Tx("23:48 → 07:20", 13.sp, color = sf().sub)
        Spacer(Modifier.height(12.dp)); PhaseBar(14.dp * s.stroke.coerceAtLeast(0.9f)); Spacer(Modifier.height(12.dp))
        val cols = stages()
        val tot = listOf(1, 2, 3, 4).associateWith { k -> night4.filter { it.first == k }.sumOf { it.second } }; val sum = tot.values.sum().toFloat()
        listOf(listOf(1 to "Profond", 2 to "Léger"), listOf(3 to "Paradoxal", 4 to "Éveil")).forEach { row ->
            Row(Modifier.padding(vertical = 3.dp)) {
                row.forEach { (k, n) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(if (s.corner == Corner.SQUARE) RoundedCornerShape(2.dp) else CircleShape).background(cols.getValue(k)))
                        Tx("  $n", 13.sp, color = sf().sub, modifier = Modifier.weight(1f))
                        Tx("${(tot.getValue(k) * 100 / sum).roundToInt()} %", 13.sp, Role.NUM, modifier = Modifier.padding(end = 14.dp))
                    }
                }
            }
        }
    }
}

@Composable fun VB4() { val s = st(); val h = s.hues
    Card5(h.sleep, W) {
        val cols = stages(); val f = sf()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = center; val w = 16.dp.toPx() * s.stroke; val r = size.minDimension / 2 - w / 2 - 2.dp.toPx()
                    drawCircle(f.s2, r, c, style = Stroke(w))
                    for (i in 0 until 24) {
                        val a = Math.toRadians(-90.0 + 15.0 * i); val r1 = r - w / 2 - 5.dp.toPx(); val r2 = r1 - (if (i % 6 == 0) 7.dp.toPx() else 3.dp.toPx())
                        drawLine(f.dim, Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()), Offset(c.x + r2 * cos(a).toFloat(), c.y + r2 * sin(a).toFloat()), 1.5.dp.toPx(), s.cap)
                    }
                    var start = -90f + 360f * (23.8f / 24f)
                    night4.forEachIndexed { i, (k, m) ->
                        val sw = 360f * m / 1440f
                        drawArc(cols.getValue(k), start - 0.3f, sw + 0.6f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r),
                            style = Stroke(w, cap = if (i == 0 || i == night4.lastIndex) s.cap else StrokeCap.Butt))
                        start += sw
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Tx("7 h 32", 22.sp, Role.NUM); Tx("score 84", 12.sp, Role.LABEL) }
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf("Coucher" to "23:48", "Lever" to "07:20", "Régularité" to "± 18 min", "Dette" to "− 28 min").forEach { (k, v) -> Column { Tx(k, 12.sp, Role.LABEL); Tx(v, 16.sp, Role.NUM) } }
            }
        }
    }
}

@Composable fun VB6() { val s = st(); val h = s.hues
    Card5(h.sleep, W, pad = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconIn(Icons.Rounded.Bedtime, h.sleep, 46.dp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Tx("7 h 32 de sommeil", 17.sp, Role.DISPLAY); Tx("Score 84 · couché 23:48", 12.sp, color = sf().sub) }
            Box(Modifier.width(72.dp)) { PhaseBar(10.dp) }
        }
    }
}

// ---- C ----------------------------------------------------------------------------------------------------------------------

private val macroRows5 = listOf(Triple("Protéines", 98 to 150, 0), Triple("Lipides", 52 to 76, 1), Triple("Glucides", 141 to 285, 2))

@Composable fun VC2() { val s = st(); val h = s.hues
    Card5(h.kcal, W, pad = 14.dp) {
        val ms = series(listOf(h.prot, h.fat, h.carb)); val trs = listOf(h.prot, h.fat, h.carb).map { track(it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(66.dp)) {
                val w = 8.dp.toPx() * s.stroke; val r = size.minDimension / 2 - w / 2; val gap = 18f
                listOf(98f / 150, 52f / 76, 141f / 285).forEachIndexed { i, f ->
                    val start = -90f + i * 120f + gap / 2; val span = 120f - gap
                    arc5(s, center, r, w, f, Hue(ms[i].a, ms[i].a), trs[i], start, span)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.width(if (s.caps) 112.dp else 98.dp)) { Tx("927", 28.sp, Role.NUM, color = data(h.kcal, true).a.takeIf { !sf().on && !s.mono }); Tx("kcal restantes", 12.sp, Role.LABEL) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                macroRows5.forEach { (n, v, i) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tx(n.take(4) + ".", 11.sp, Role.LABEL, modifier = Modifier.width(38.dp))
                        HBar(v.first.toFloat() / v.second, listOf(h.prot, h.fat, h.carb)[i], Modifier.weight(1f), (5 * s.stroke).dp)
                        Tx("${v.first}", 12.sp, Role.NUM, modifier = Modifier.width(34.dp).padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable fun VC3() { val s = st(); val h = s.hues
    Card5(h.kcal, W) {
        val mh = series(listOf(h.steps, h.kcal, h.repos))
        Row(verticalAlignment = Alignment.Bottom) {
            Tx("1 353", 30.sp, Role.NUM); Tx(" / 2 280 kcal", 14.sp, color = sf().sub, modifier = Modifier.padding(bottom = 5.dp))
            Spacer(Modifier.weight(1f)); Tx("927 restantes", 14.sp, Role.DISPLAY, color = data(h.kcal, true).a, modifier = Modifier.padding(bottom = 5.dp))
        }
        Spacer(Modifier.height(12.dp))
        val shape = if (s.bar == BarS.PILL) CircleShape else RoundedCornerShape(3.dp)
        Row(W.height(18.dp * s.stroke.coerceAtLeast(0.8f)).clip(shape).background(sf().s2), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            meals4.forEachIndexed { i, m -> Box(Modifier.weight(m.third.sumOf { it.kcal }.toFloat()).fillMaxHeight().background(brush(mh[i]))) }
            Spacer(Modifier.weight(927f))
        }
        Spacer(Modifier.height(12.dp))
        Row {
            meals4.forEachIndexed { i, m ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(if (s.corner == Corner.SQUARE) RoundedCornerShape(1.dp) else CircleShape).background(mh[i].a)); Spacer(Modifier.width(6.dp))
                    Column { Tx(m.first, 12.sp, Role.LABEL); Tx("${m.third.sumOf { it.kcal }}", 14.sp, Role.NUM) }
                }
            }
        }
    }
}

@Composable fun VC4() { val s = st(); val h = s.hues
    Card5(h.kcal, W) {
        val ms = series(listOf(h.prot, h.fat, h.carb))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Tx("927", 34.sp, Role.NUM); Tx("kcal restantes", 13.sp, Role.LABEL); Spacer(Modifier.height(4.dp)); Tx("1 353 / 2 280", 12.sp, color = sf().sub) }
            macroRows5.forEach { (n, v, i) ->
                val tr = track(listOf(h.prot, h.fat, h.carb)[i])
                Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(56.dp)) { val w = 7.dp.toPx() * s.stroke; arc5(s, center, size.minDimension / 2 - w / 2 - 1.dp.toPx(), w, v.first.toFloat() / v.second, ms[i], tr) }
                        Tx(n.take(1), 15.sp, Role.NUM, color = ms[i].a)
                    }
                    Tx("${v.first} g", 12.sp, Role.NUM, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

// ---- D ----------------------------------------------------------------------------------------------------------------------

@Composable fun VD12() { val s = st(); val h = s.hues
    Row(horizontalArrangement = Arrangement.spacedBy(s.gap)) {
        Card5(h.steps, Modifier.weight(1f).height(156.dp)) {
            Label5(Icons.AutoMirrored.Rounded.DirectionsRun, "Pas", h.steps); Tx("6 240", 26.sp, Role.NUM)
            Spacer(Modifier.weight(1f))
            val hd = data(h.steps); val f = sf()
            Canvas(W.height(48.dp)) {
                val n = 24; val gap = 2.dp.toPx(); val bw = (size.width - gap * (n - 1)) / n; val max = steps4.max().toFloat()
                steps4.forEachIndexed { i, v ->
                    val x = i * (bw + gap)
                    if (i > 16) drawCircle(f.dim, bw / 2.4f, Offset(x + bw / 2, size.height - bw / 2))
                    else if (s.bar == BarS.SEGMENT) {
                        val lit = maxOf(1, (v / max * 8).roundToInt()); val ch = size.height / 8
                        for (k in 0 until 8) drawRect(if (k < lit && v > 0) hd.a else f.s2, Offset(x, size.height - (k + 1) * ch + 1f), Size(bw, ch - 2f))
                    } else {
                        val bh = maxOf(bw, size.height * v / max)
                        drawRoundRect(if (s.gradient) Brush.verticalGradient(listOf(hd.b, hd.a), size.height - bh, size.height) else SolidColor(hd.a), Offset(x, size.height - bh), Size(bw, bh),
                            CornerRadius(if (s.bar == BarS.PILL) bw / 2 else 1f), alpha = if (v == 0) 0.3f else 1f)
                    }
                }
            }
        }
        Card5(h.heart, Modifier.weight(1f).height(156.dp)) {
            Label5(Icons.Rounded.Favorite, "Cœur au repos", h.heart)
            Row(verticalAlignment = Alignment.Bottom) { Tx("54", 26.sp, Role.NUM); Tx(" bpm", 13.sp, color = sf().sub, modifier = Modifier.padding(bottom = 4.dp)) }
            Spacer(Modifier.weight(1f))
            val hd = data(h.heart); val f = sf()
            Canvas(W.height(48.dp)) {
                val v = heart4.map { it.toFloat() }; val lo = v.min() - 1; val hi = v.max() + 1; val pad = 6.dp.toPx()
                val pts = v.mapIndexed { i, y -> Offset(pad + (size.width - 2 * pad) * i / (v.size - 1), pad + (size.height - 2 * pad) * (hi - y) / (hi - lo)) }
                val line = if (s.butt) Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } } else smooth(pts)
                if (s.area) drawPath(Path().apply { addPath(line); lineTo(pts.last().x, size.height); lineTo(pts[0].x, size.height); close() }, Brush.verticalGradient(listOf(hd.a.copy(alpha = 0.28f), Color.Transparent)))
                drawPath(line, if (s.gradient) Brush.horizontalGradient(listOf(hd.b, hd.a)) else SolidColor(hd.a), style = Stroke(2.5.dp.toPx() * s.stroke.coerceAtLeast(0.8f), cap = s.cap))
                drawCircle(hd.a, 4.5.dp.toPx(), pts.last())
            }
        }
    }
}


@Composable fun VD34() { val s = st(); val h = s.hues
    Row(horizontalArrangement = Arrangement.spacedBy(s.gap)) {
        Card5(h.weight, Modifier.weight(1f).height(156.dp)) {
            Label5(Icons.Rounded.MonitorWeight, "Poids", h.weight)
            Row(verticalAlignment = Alignment.Bottom) { Tx("81,2", 26.sp, Role.NUM); Tx(" kg", 13.sp, color = sf().sub, modifier = Modifier.padding(bottom = 4.dp)) }
            Tx("−1,4 kg en 4 sem.", 12.sp, color = if (sf().on) sf().ink else data(h.forme, true).a)
            Spacer(Modifier.weight(1f))
            val hd = data(h.weight); val f = sf()
            Canvas(W.height(40.dp)) {
                val raw = weight4.map { it.toFloat() }; var t = raw[0]; val trend = raw.map { t += 0.1f * (it - t); t }
                val lo = raw.min() - 0.2f; val hi = raw.max() + 0.2f; val pad = 4.dp.toPx()
                fun p(i: Int, v: Float) = Offset(pad + (size.width - 2 * pad) * i / (raw.size - 1), pad + (size.height - 2 * pad) * (hi - v) / (hi - lo))
                raw.forEachIndexed { i, v -> if (s.butt) drawRect(f.dim, p(i, v) - Offset(2f, 2f) * density, Size(4f, 4f) * density) else drawCircle(f.dim, 2.5.dp.toPx(), p(i, v)) }
                drawPath(smooth(trend.mapIndexed { i, v -> p(i, v) }), if (s.gradient) Brush.horizontalGradient(listOf(hd.a, hd.b)) else SolidColor(hd.a), style = Stroke(2.5.dp.toPx() * s.stroke.coerceAtLeast(0.8f), cap = s.cap))
            }
        }
        Card5(h.steps, Modifier.weight(1f).height(156.dp)) {
            Label5(Icons.AutoMirrored.Rounded.DirectionsRun, "Pas", h.steps)
            val hd = data(h.steps); val tr = track(h.steps)
            Box(W.weight(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(100.dp)) { val w = 10.dp.toPx() * s.stroke; arc5(s, center, size.minDimension / 2 - w / 2 - 2.dp.toPx(), w, 6240f / 8000, hd, tr, 135f, 270f) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Tx("6 240", 19.sp, Role.NUM); Tx("/ 8 000", 11.sp, Role.LABEL) }
            }
        }
    }
}

@Composable fun VD5() { val s = st(); val h = s.hues
    Row(horizontalArrangement = Arrangement.spacedBy(s.gap)) {
        listOf(Triple(h.heart, "Cœur au repos" to "54 bpm", false), Triple(h.charge, "VFC nocturne" to "62 ms", true)).forEach { (hue, t, up) ->
            Card5(hue, Modifier.weight(1f), pad = 14.dp) {
                Tx(t.first, 13.sp, Role.LABEL); Tx(t.second, 22.sp, Role.NUM)
                val good = data(h.forme, true); val f = sf()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).clip(if (s.corner == Corner.SQUARE) RoundedCornerShape(4.dp) else CircleShape).background(if (f.on) f.ink.copy(alpha = 0.2f) else track(h.forme)), contentAlignment = Alignment.Center) {
                        Icon(if (up) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null, Modifier.size(13.dp), tint = if (f.on) f.ink else good.a)
                    }
                    Tx(if (up) "  +5 vs moyenne" else "  −2 vs moyenne", 12.sp, color = f.sub)
                }
            }
        }
    }
}

// ---- écran : en-tête et navigation propres au style ---------------------------------------------------------------------------

private val tabs5 = listOf(Icons.Rounded.Today to "Aujourd'hui", Icons.Rounded.Restaurant to "Nutrition", Icons.AutoMirrored.Rounded.DirectionsRun to "Activités")

@Composable private fun Avatar(size: Dp = 40.dp) { val s = st()
    Box(Modifier.size(size).clip(if (s.corner == Corner.SQUARE) RoundedCornerShape(10.dp) else CircleShape).background(if (s.mono) SolidColor(s.base.ink) else brush(s.hues.action)), contentAlignment = Alignment.Center) {
        Tx("B", 16.sp, Role.DISPLAY, color = if (s.mono) s.base.bg else Color.White)
    }
}

@Composable private fun Round(size: Dp = 40.dp, content: @Composable () -> Unit) { val s = st()
    Box(Modifier.size(size).clip(if (s.corner == Corner.SQUARE) RoundedCornerShape(10.dp) else CircleShape).background(if (s.card == CardM.ELEVATED || s.card == CardM.OUTLINED) s.base.s1 else s.base.s2)
        .then(if (s.card == CardM.OUTLINED) Modifier.border(1.dp, s.base.line, if (s.corner == Corner.SQUARE) RoundedCornerShape(10.dp) else CircleShape) else Modifier), contentAlignment = Alignment.Center) { content() }
}

@Composable private fun Header5(title: String) { val s = st()
    if (s.nav == NavS.TOPTABS) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tx("Vendredi 30 octobre", 12.sp, Role.LABEL, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = s.base.ink); Spacer(Modifier.width(14.dp)); Avatar(34.dp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                tabs5.forEachIndexed { i, (_, l) ->
                    Column(Modifier.width(IntrinsicSize.Max)) {
                        Tx(l, 20.sp, Role.DISPLAY, color = if (i == 0) s.base.ink else s.base.sub)
                        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(2.dp).background(if (i == 0) s.base.ink else Color.Transparent))
                    }
                }
            }
            Box(W.height(1.dp).background(s.base.line))
        }
    } else Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Tx("Vendredi 30 octobre", 13.sp, Role.LABEL); Tx(title, 32.sp, Role.DISPLAY) }
        if (s.nav != NavS.DOCK) { Round { Icon(Icons.Rounded.Search, null, Modifier.size(21.dp), tint = s.base.ink) }; Spacer(Modifier.width(8.dp)) }
        Avatar()
    }
}

@Composable private fun Fab5(size: Dp = 60.dp, label: String? = null) { val s = st()
    val shape = when { label != null -> CircleShape; s.corner == Corner.SQUARE -> RoundedCornerShape(12.dp); s.corner == Corner.LEAF -> s.shape(0.7f); s.nav == NavS.BAR -> RoundedCornerShape(18.dp); else -> CircleShape }
    val fill = if (s.mono) SolidColor(s.base.ink) else brush(s.hues.action)
    Row(Modifier.height(size).then(if (label == null) Modifier.width(size) else Modifier).shadow(if (s.dark) 0.dp else 8.dp, shape).clip(shape).background(fill)
        .padding(horizontal = if (label != null) 20.dp else 0.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Add, null, Modifier.size(28.dp), tint = if (s.mono) s.base.bg else Color.White)
        if (label != null) Tx(" $label", 15.sp, Role.DISPLAY, color = if (s.mono) s.base.bg else Color.White)
    }
}

@Composable private fun Nav5(modifier: Modifier) { val s = st(); val b = s.base
    val floatBg = if (s.dark) b.s2 else b.s1
    Box(modifier.fillMaxWidth()) {
        if (s.nav != NavS.BAR) Box(Modifier.matchParentWidthHeight(150.dp).background(Brush.verticalGradient(listOf(Color.Transparent, b.bg, b.bg))))
        when (s.nav) {
            NavS.PILL -> Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.shadow(if (s.dark) 0.dp else 10.dp, CircleShape).clip(CircleShape).background(floatBg).padding(5.dp)) {
                    tabs5.forEachIndexed { i, (ic, l) ->
                        val on = i == 0
                        Row(Modifier.clip(CircleShape).background(if (on) b.ink else Color.Transparent).padding(horizontal = if (on) 16.dp else 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(ic, null, Modifier.size(22.dp), tint = if (on) b.bg else b.sub); if (on) Tx("  $l", 14.sp, Role.DISPLAY, color = b.bg)
                        }
                    }
                }
                Spacer(Modifier.weight(1f)); Fab5(58.dp)
            }
            NavS.BAR -> Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(b.s1).padding(start = 8.dp, end = 14.dp, top = 12.dp, bottom = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs5.forEachIndexed { i, (ic, l) ->
                    val on = i == 0
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(58.dp, 32.dp).clip(if (s.corner == Corner.LEAF) s.shape(0.55f) else CircleShape).background(if (on) s.hues.action.a.mix(Color.White, 0.84f) else Color.Transparent), contentAlignment = Alignment.Center) {
                            Icon(ic, null, Modifier.size(23.dp), tint = if (on) s.hues.action.a else b.sub)
                        }
                        Tx(l, 12.sp, Role.TEXT, color = if (on) b.ink else b.sub, weight = if (on) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Fab5(56.dp)
            }
            NavS.DOCK -> Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(CircleShape).background(floatBg).padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    tabs5.forEachIndexed { i, (ic, _) ->
                        Column(Modifier.padding(horizontal = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(ic, null, Modifier.size(25.dp), tint = if (i == 0) b.ink else b.sub)
                            Box(Modifier.padding(top = 4.dp).size(4.dp).clip(CircleShape).background(if (i == 0) s.hues.action.b else Color.Transparent))
                        }
                    }
                    Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(28.dp).background(b.dim))
                    Box(Modifier.padding(horizontal = 9.dp)) { Icon(Icons.Rounded.Search, null, Modifier.size(25.dp), tint = b.sub) }
                }
                Spacer(Modifier.weight(1f)); Fab5(58.dp)
            }
            NavS.SEGMENTS -> Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clip(CircleShape).background(floatBg).padding(4.dp)) {
                    tabs5.forEachIndexed { i, (_, l) ->
                        val on = i == 0
                        Box(Modifier.weight(1f).clip(CircleShape).background(if (on) s.hues.action.a.mix(b.s2, 0.7f) else Color.Transparent).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                            Tx(l, 13.sp, Role.TEXT, color = if (on) b.ink else b.sub, weight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp)); Fab5(54.dp)
            }
            NavS.TOPTABS -> Box(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 28.dp)) { Fab5(60.dp) }
            NavS.NOTER -> Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 22.dp, end = 16.dp, bottom = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs5.forEachIndexed { i, (ic, l) ->
                    Column(Modifier.padding(end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(ic, null, Modifier.size(25.dp), tint = if (i == 0) b.ink else b.dim.mix(b.sub, 0.5f))
                        Tx(l, 10.sp, Role.TEXT, color = if (i == 0) b.ink else b.sub, weight = FontWeight.Medium, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                Spacer(Modifier.weight(1f)); Fab5(54.dp, "Noter")
            }
        }
    }
}

private fun Modifier.matchParentWidthHeight(h: Dp) = this.fillMaxWidth().height(h)

@Composable
fun StyleHome(s: Style) = ProvideStyle(s) {
    Box(Modifier.fillMaxSize().background(s.base.bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(s.gap)) {
            Header5("Aujourd'hui")
            VA4(); VB1(); VC2(); VD12()
        }
        Nav5(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun StyleSheet(s: Style) = ProvideStyle(s) {
    Column(Modifier.fillMaxSize().background(s.base.bg).padding(horizontal = 16.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.padding(start = 4.dp, bottom = 6.dp)) { Tx(s.title, 30.sp, Role.DISPLAY); Tx(s.pitch, 14.sp, color = s.base.sub, lines = 2) }
        listOf<Pair<String, @Composable () -> Unit>>("A6" to { VA6() }, "B3" to { VB3() }, "B4" to { VB4() }, "B6" to { VB6() }, "C3" to { VC3() }, "C4" to { VC4() },
            "D3·D4" to { VD34() }, "D5" to { VD5() }).forEach { (id, w) ->
            Column {
                Box(Modifier.padding(start = 4.dp, bottom = 6.dp).clip(CircleShape).background(s.base.ink).padding(horizontal = 9.dp, vertical = 2.dp)) { Tx(id, 12.sp, Role.TEXT, color = s.base.bg, weight = FontWeight.SemiBold) }
                w()
            }
        }
    }
}
