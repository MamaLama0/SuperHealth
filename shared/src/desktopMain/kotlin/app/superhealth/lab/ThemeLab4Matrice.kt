package app.superhealth.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Tour 4 — C. Matrice : noir et blanc, une seule couleur (rouge), tout est fait de points — chiffres en police matricielle
 * 5 × 7 dessinée, anneaux et graphiques pointillés, corps en trame. Organisation : la recherche est une ligne de commande
 * en haut ; en bas, trois pastilles rondes à gauche et le « + » rouge à droite.
 */

private object M {
    val bg = Color(0xFF000000); val card = Color(0xFF111111); val card2 = Color(0xFF1C1C1C); val paper = Color(0xFFF1F1EF)
    val ink = Color(0xFFF4F4F4); val gray = Color(0xFF8A8A8A); val dim = Color(0xFF2C2C2C); val dimOnPaper = Color(0xFFD2D2D0)
    val black = Color(0xFF0A0A0A); val red = Color(0xFFE8202A)
}

// ---- police matricielle 5 × 7 ----------------------------------------------------------------------------------------------

private val GLYPHS: Map<Char, List<String>> = mapOf(
    '0' to "01110 10001 10011 10101 11001 10001 01110", '1' to "00100 01100 00100 00100 00100 00100 01110",
    '2' to "01110 10001 00001 00010 00100 01000 11111", '3' to "11111 00010 00100 00010 00001 10001 01110",
    '4' to "00010 00110 01010 10010 11111 00010 00010", '5' to "11111 10000 11110 00001 00001 10001 01110",
    '6' to "00110 01000 10000 11110 10001 10001 01110", '7' to "11111 00001 00010 00100 01000 01000 01000",
    '8' to "01110 10001 10001 01110 10001 10001 01110", '9' to "01110 10001 10001 01111 00001 00010 01100",
    'A' to "01110 10001 10001 11111 10001 10001 10001", 'B' to "11110 10001 10001 11110 10001 10001 11110",
    'C' to "01110 10001 10000 10000 10000 10001 01110", 'D' to "11100 10010 10001 10001 10001 10010 11100",
    'E' to "11111 10000 10000 11110 10000 10000 11111", 'F' to "11111 10000 10000 11110 10000 10000 10000",
    'G' to "01110 10001 10000 10111 10001 10001 01111", 'H' to "10001 10001 10001 11111 10001 10001 10001",
    'I' to "01110 00100 00100 00100 00100 00100 01110", 'J' to "00111 00010 00010 00010 00010 10010 01100",
    'K' to "10001 10010 10100 11000 10100 10010 10001", 'L' to "10000 10000 10000 10000 10000 10000 11111",
    'M' to "10001 11011 10101 10101 10001 10001 10001", 'N' to "10001 10001 11001 10101 10011 10001 10001",
    'O' to "01110 10001 10001 10001 10001 10001 01110", 'P' to "11110 10001 10001 11110 10000 10000 10000",
    'Q' to "01110 10001 10001 10001 10101 10010 01101", 'R' to "11110 10001 10001 11110 10100 10010 10001",
    'S' to "01111 10000 10000 01110 00001 00001 11110", 'T' to "11111 00100 00100 00100 00100 00100 00100",
    'U' to "10001 10001 10001 10001 10001 10001 01110", 'V' to "10001 10001 10001 10001 10001 01010 00100",
    'W' to "10001 10001 10001 10101 10101 10101 01010", 'X' to "10001 10001 01010 00100 01010 10001 10001",
    'Y' to "10001 10001 10001 01010 00100 00100 00100", 'Z' to "11111 00001 00010 00100 01000 10000 11111",
    ':' to "0 1 1 0 1 1 0", '.' to "0 0 0 0 0 1 1", ',' to "00 00 00 00 01 01 10", '\'' to "1 1 0 0 0 0 0",
    '%' to "11000 11001 00010 00100 01000 10011 00011", '/' to "00000 00001 00010 00100 01000 10000 00000",
    '-' to "0000 0000 0000 1111 0000 0000 0000", '+' to "00000 00100 00100 11111 00100 00100 00000",
    ' ' to "000 000 000 000 000 000 000",
).mapValues { it.value.split(' ') }

private fun norm(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").uppercase()

/** Texte en points ([pitch] = pas de la grille). [ghost] : points éteints visibles. */
@Composable
private fun Dots(text: String, pitch: Dp, color: Color, modifier: Modifier = Modifier, ghost: Color? = null, size: Float = 0.42f) {
    val g = norm(text).map { GLYPHS[it] ?: GLYPHS.getValue(' ') }
    val cols = g.sumOf { it[0].length + 1 } - 1
    Canvas(modifier.size(pitch * cols, pitch * 7)) {
        val p = pitch.toPx(); var x0 = 0
        g.forEach { rows ->
            rows.forEachIndexed { r, row -> row.forEachIndexed { c, b ->
                if (b == '1' || ghost != null) drawCircle(if (b == '1') color else ghost!!, p * size, Offset((x0 + c + 0.5f) * p, (r + 0.5f) * p))
            } }
            x0 += rows[0].length + 1
        }
    }
}

@Composable
private fun Mono(text: String, size: Float = 11f, color: Color = M.gray, weight: FontWeight = FontWeight.Medium, modifier: Modifier = Modifier) =
    X(text.uppercase(), size.sp, color, weight, family = Mono, spacing = 1.sp, modifier = modifier, lines = 1)

/** Rangée de points : [n] points, [on] allumés, le dernier allumé peut être rouge. */
@Composable
private fun DotBar(n: Int, on: Int, lit: Color, off: Color, modifier: Modifier = Modifier, pitch: Dp = 7.dp, lastRed: Boolean = false) {
    Canvas(modifier.size(pitch * n, pitch)) {
        val p = pitch.toPx()
        repeat(n) { drawCircle(if (it < on) (if (lastRed && it == on - 1) M.red else lit) else off, p * 0.36f, Offset((it + 0.5f) * p, p / 2)) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.dotRing(c: Offset, r: Float, n: Int, f: Float, lit: Color, off: Color, dot: Float) {
    val on = (n * f).roundToInt()
    repeat(n) { i ->
        val a = Math.toRadians(-90.0 + 360.0 * i / n)
        drawCircle(if (i < on) (if (i == on - 1) M.red else lit) else off, dot, Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()))
    }
}

/** Corps en trame de points : muscles visés en [hot]. */
@Composable
private fun DotBody(d: Day, cols: Int, on: Color, hot: Color, modifier: Modifier) {
    val art = remember(d.side) { body(d.side) }
    val y0 = art.h * d.from; val hh = art.h * (d.to - d.from)
    val dots = remember(d, cols) {
        val pitch = art.w / cols; val rows = (hh / pitch).toInt()
        val sk = art.parts.map { it.first to it.second.asSkiaPath() }
        buildList {
            for (r in 0 until rows) for (c in 0 until cols) {
                val x = (c + 0.5f) * pitch; val y = y0 + (r + 0.5f) * pitch
                val k = when { sk.any { it.first in d.target && it.second.contains(x, y) } -> 2; sk.any { it.second.contains(x, y) } -> 1; else -> 0 }
                if (k > 0) add(Triple(x, y - y0, k))
            }
        }
    }
    Canvas(modifier) {
        val s = minOf(size.width / art.w, size.height / hh); val dx = (size.width - art.w * s) / 2; val dy = (size.height - hh * s) / 2
        val rad = art.w / cols * s * 0.38f
        dots.forEach { (x, y, k) -> drawCircle(if (k == 2) hot else on, rad, Offset(dx + x * s, dy + y * s)) }
    }
}

@Composable
private fun MCard(modifier: Modifier = Modifier, light: Boolean = false, pad: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.clip(RoundedCornerShape(26.dp)).background(if (light) M.paper else M.card).padding(pad), content = content)

@Composable
private fun MScreen(tab: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(M.bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ligne de commande : chercher ou noter
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).height(46.dp).clip(CircleShape).border(1.dp, M.dim, CircleShape).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Mono("/", 14f, M.red, FontWeight.Bold); Spacer(Modifier.width(10.dp)); Mono("Chercher ou noter", 13f, M.gray)
                    Spacer(Modifier.width(4.dp)); Box(Modifier.size(8.dp, 16.dp).background(M.ink))
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(46.dp).clip(CircleShape).background(M.paper), contentAlignment = Alignment.Center) { Dots("B", 3.dp, M.black) }
            }
            Row(Modifier.padding(top = 6.dp, start = 2.dp), verticalAlignment = Alignment.Bottom) {
                Dots(title, 3.6.dp, M.ink); Spacer(Modifier.weight(1f)); Mono("Ven. 30 oct.", 11f, M.gray)
            }
            content()
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, M.bg, M.bg))))
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(Icons.Rounded.Today, Icons.Rounded.Restaurant, Icons.AutoMirrored.Rounded.DirectionsRun).forEachIndexed { i, ic ->
                val on = i == tab
                Box(Modifier.padding(end = 10.dp).size(58.dp), contentAlignment = Alignment.Center) {
                    if (on) Box(Modifier.size(58.dp).clip(CircleShape).background(M.paper))
                    else Canvas(Modifier.size(58.dp)) { dotRing(center, size.minDimension / 2 - 2.dp.toPx(), 28, 0f, M.ink, Color(0xFF4A4A4A), 1.3.dp.toPx()) }
                    Icon(ic, null, Modifier.size(24.dp), tint = if (on) M.black else M.gray)
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(M.red), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, null, Modifier.size(32.dp), tint = Color.White)
            }
        }
    }
}

// ---- Aujourd'hui ----------------------------------------------------------------------------------------------------------

@Composable
fun MatriceHome() = MScreen(0, "Aujourd'hui") {
    MCard(Modifier.fillMaxWidth(), light = true, pad = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Mono("Forme", 11f, Color(0xFF6A6A6A))
                Spacer(Modifier.height(8.dp))
                Dots("78", 10.dp, M.black, size = 0.46f)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(M.red)); Spacer(Modifier.width(6.dp))
                    Mono("Prêt à pousser", 12f, M.black, FontWeight.Bold)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Charge" to 64, "Repos" to 84, "Effort" to 42).forEach { (n, v) ->
                    Column {
                        Row(Modifier.width(126.dp)) { Mono(n, 10f, Color(0xFF6A6A6A), modifier = Modifier.weight(1f)); Mono("$v", 10f, M.black, FontWeight.Bold) }
                        Spacer(Modifier.height(3.dp))
                        DotBar(14, (v * 14f / 100).roundToInt(), M.black, M.dimOnPaper, pitch = 9.dp, lastRed = true)
                    }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MCard(Modifier.weight(1.35f).height(160.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Mono("Sommeil", modifier = Modifier.weight(1f)); Mono("84", 11f, M.ink, FontWeight.Bold) }
            Spacer(Modifier.height(10.dp))
            Dots("7:32", 5.dp, M.ink)
            Spacer(Modifier.weight(1f))
            // hypnogramme : une colonne toutes les 10 min, quatre rangées (éveil en haut, en rouge)
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                val cols = 34; val p = size.width / cols; val rp = size.height / 4
                val total = night4.sumOf { it.second }
                for (c in 0 until cols) {
                    var t = (c + 0.5f) * total / cols; var st = 2
                    for ((s, m) in night4) { if (t < m) { st = s; break }; t -= m }
                    for (r in 0 until 4) {
                        val lit = r == 4 - st
                        drawCircle(if (lit) (if (st == 4) M.red else M.ink) else M.dim, minOf(p, rp) * 0.36f, Offset((c + 0.5f) * p, (r + 0.5f) * rp))
                    }
                }
            }
        }
        MCard(Modifier.weight(1f).height(160.dp)) {
            Mono("Calories")
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(108.dp)) { dotRing(center, size.minDimension / 2 - 4.dp.toPx(), 40, 1353f / 2280, M.ink, M.dim, 2.4.dp.toPx()) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Dots("927", 3.6.dp, M.ink); Spacer(Modifier.height(5.dp)); Mono("rest.", 9f) }
            }
        }
    }
    MCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) { Mono("Pas"); Spacer(Modifier.height(8.dp)); Dots("6240", 5.dp, M.ink) }
            Mono("Obj. 8000", 10f)
        }
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(48.dp)) {
            val cols = 24; val rows = 7; val p = size.width / cols; val rp = size.height / rows; val max = steps4.max().toFloat()
            steps4.forEachIndexed { c, v ->
                val lit = if (c > 16) 0 else (v / max * rows).roundToInt().coerceAtLeast(if (v > 0) 1 else 0)
                for (r in 0 until rows) {
                    val on = rows - 1 - r < lit
                    drawCircle(if (on) (if (c == 16) M.red else M.ink) else M.dim, minOf(p, rp) * 0.34f, Offset((c + 0.5f) * p, (r + 0.5f) * rp))
                }
            }
        }
        Row { Mono("0 h", 9f, modifier = Modifier.weight(1f)); Mono("12 h", 9f, modifier = Modifier.weight(1f)); Mono("23 h", 9f) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple("Cœur repos", "54", "bpm"), Triple("Poids", "81.2", "kg")).forEach { (l, v, u) ->
            MCard(Modifier.weight(1f)) {
                Mono(l); Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) { Dots(v, 4.4.dp, M.ink); Spacer(Modifier.width(6.dp)); Mono(u, 10f) }
            }
        }
    }
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(M.red)); Spacer(Modifier.width(10.dp))
        Mono("Coach — Jambes aujourd'hui", 12f, M.ink)
    }
}

// ---- Nutrition -------------------------------------------------------------------------------------------------------------

@Composable
fun MatriceJournal() = MScreen(1, "Nutrition") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("L" to "26", "M" to "27", "M" to "28", "J" to "29", "V" to "30", "S" to "31", "D" to "1").forEachIndexed { i, (d, n) ->
            val today = i == 4
            Column(Modifier.width(44.dp).clip(RoundedCornerShape(16.dp)).background(if (today) M.paper else Color.Transparent).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Mono(d, 10f, if (today) M.black else M.gray)
                Spacer(Modifier.height(6.dp))
                Dots(n, 2.6.dp, if (today) M.black else if (i > 4) Color(0xFF444444) else M.ink)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (i < 4) M.ink else if (today) M.red else Color.Transparent))
            }
        }
    }
    MCard(Modifier.fillMaxWidth(), pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(62.dp)) { dotRing(center, size.minDimension / 2 - 3.dp.toPx(), 28, 1353f / 2280, M.ink, M.dim, 2.dp.toPx()) }
                Mono("59%", 10f, M.ink, FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.width(100.dp)) { Dots("927", 4.6.dp, M.ink); Spacer(Modifier.height(6.dp)); Mono("kcal rest.", 9f) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Triple("P", 98, 150), Triple("L", 52, 76), Triple("G", 141, 285)).forEach { (n, v, of) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Mono(n, 10f, M.ink, FontWeight.Bold, modifier = Modifier.width(14.dp))
                        DotBar(8, (v * 8f / of).roundToInt(), M.ink, M.dim, pitch = 6.dp)
                        Spacer(Modifier.width(6.dp)); Mono("$v", 10f, M.ink)
                    }
                }
            }
        }
    }
    meals4.forEach { (name, time, foods) ->
        Column {
            Row(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Mono(time, 11f, M.red, FontWeight.Bold); Spacer(Modifier.width(10.dp)); Mono(name, 11f, M.ink, FontWeight.Bold, modifier = Modifier.weight(1f))
                Mono("${foods.sumOf { it.kcal }} kcal", 11f)
            }
            MCard(Modifier.fillMaxWidth(), pad = 10.dp) {
                foods.forEachIndexed { i, f ->
                    if (i > 0) Canvas(Modifier.fillMaxWidth().height(9.dp)) {
                        var x = 56.dp.toPx(); while (x < size.width) { drawCircle(M.dim, 1.dp.toPx(), Offset(x, size.height / 2)); x += 5.dp.toPx() }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(M.card2), contentAlignment = Alignment.Center) { PixelIcon(f.icon, 30.dp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { X(f.name, 15.sp, M.ink, lines = 1); Mono(f.qty, 10f) }
                        Mono("${f.kcal}", 13f, M.ink, FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---- Activités -------------------------------------------------------------------------------------------------------------

@Composable
fun MatriceActivities() = MScreen(2, "Activités") {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) { Dots("3", 4.6.dp, M.ink); Spacer(Modifier.width(8.dp)); Mono("activités", 10f, M.ink) }
            Spacer(Modifier.height(6.dp)); Mono("2 h 09 · semaine", 10f)
        }
        listOf("L" to 48, "M" to 52, "M" to 0, "J" to 29, "V" to 0, "S" to -1, "D" to -1).forEachIndexed { i, (d, m) ->
            Column(Modifier.width(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(8.dp, 42.dp)) {
                    val n = 6; val p = size.height / n; val lit = if (m > 0) (m / 10f).roundToInt().coerceIn(1, n) else 0
                    repeat(n) { r -> drawCircle(if (n - 1 - r < lit) (if (i == 3) M.red else M.ink) else M.dim, p * 0.34f, Offset(size.width / 2, (r + 0.5f) * p)) }
                }
                Spacer(Modifier.height(4.dp)); Mono(d, 9f, if (i == 4) M.ink else M.gray)
            }
        }
    }
    MCard(Modifier.fillMaxWidth(), light = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Mono("Push Pull Legs", 14f, M.black, FontWeight.Bold); Spacer(Modifier.height(3.dp)); Mono("Sem. 3 — 2 / 4", 10f, Color(0xFF6A6A6A)) }
            Dots("2/4", 3.dp, M.black)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            program4.forEach { d ->
                val shape = RoundedCornerShape(20.dp)
                Column(Modifier.weight(1f).clip(shape).background(M.black).then(if (d.next) Modifier.border(2.dp, M.red, shape) else Modifier).padding(top = 10.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(100.dp)) {
                        DotBody(d, 30, if (d.done) Color(0xFF555555) else Color(0xFF9A9A9A), if (d.done) M.ink else M.red, Modifier.fillMaxSize().padding(horizontal = 4.dp))
                        if (d.done) Box(Modifier.align(Alignment.TopEnd).padding(end = 6.dp).size(18.dp).clip(CircleShape).background(M.ink), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = M.black)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Mono(d.name, 10f, if (d.next) M.ink else M.gray, FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(M.black).padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PlayArrow, null, tint = M.red); Spacer(Modifier.width(8.dp)); Mono("Commencer Jambes", 14f, M.ink, FontWeight.Bold)
        }
    }
    MCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Mono("Course · hier"); Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) { Dots("5,2", 5.dp, M.ink); Spacer(Modifier.width(6.dp)); Mono("km", 10f) }
                Spacer(Modifier.height(8.dp)); Mono("28:41 · 5:31 /km", 10f)
            }
            Canvas(Modifier.size(128.dp, 84.dp)) {
                val pts = listOf(0.08f to 0.78f, 0.2f to 0.52f, 0.16f to 0.26f, 0.36f to 0.12f, 0.55f to 0.3f, 0.7f to 0.14f, 0.92f to 0.3f, 0.86f to 0.62f, 0.62f to 0.7f, 0.46f to 0.88f, 0.22f to 0.84f)
                    .map { Offset(it.first * size.width, it.second * size.height) }
                val m = PathMeasure().apply { setPath(smooth(pts), false) }
                var d = 0f; val step = 4.5.dp.toPx()
                while (d <= m.length) { drawCircle(M.ink, 1.5.dp.toPx(), m.getPosition(d)); d += step }
                drawCircle(M.red, 4.dp.toPx(), pts.last()); drawCircle(M.ink, 3.dp.toPx(), pts.first())
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple("Pull · mardi", "12340", "kg"), Triple("Vélo · lundi", "21,0", "km")).forEach { (l, v, u) ->
            MCard(Modifier.weight(1f)) {
                Mono(l); Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) { Dots(v, 3.4.dp, M.ink); Spacer(Modifier.width(6.dp)); Mono(u, 10f) }
            }
        }
    }
}
