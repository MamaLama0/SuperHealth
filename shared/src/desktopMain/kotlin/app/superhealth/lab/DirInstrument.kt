package app.superhealth.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Direction 1 — Instrument. Le tableau de bord d'un appareil de mesure : l'écran est un quadrillage de cellules
 * bord à bord séparées par des filets (pas de cartes flottantes), cadrans à graduations, chiffres à chasse fixe,
 * étiquettes numérotées en capitales. Une seule couleur de signal (citron vert) ; les autres teintes ne servent qu'aux
 * données. La navigation fait partie de la grille : quatre cellules en bas, le « + » est la cellule pleine sous le pouce.
 */

private object I {
    val bg = Color(0xFF0B0C0E); val cell = Color(0xFF111316); val line = Color(0xFF25282D); val ink = Color(0xFFE8EBEE)
    val sub = Color(0xFF7C828B); val dim = Color(0xFF353A41); val lime = Color(0xFFC6F36B)
    val blue = Color(0xFF5CC8FF); val violet = Color(0xFFA98BFF); val amber = Color(0xFFFFB547); val coral = Color(0xFFFF7A6B)
}

@Composable private fun M(text: String, size: Float = 11f, color: Color = I.sub, weight: FontWeight = FontWeight.Medium, modifier: Modifier = Modifier) =
    X(text.uppercase(), size.sp, color, weight, family = Mono, spacing = 1.1.sp, modifier = modifier, lines = 1)
@Composable private fun N(text: String, size: TextUnit, color: Color = I.ink, modifier: Modifier = Modifier) =
    X(text, size, color, FontWeight.Medium, family = InterDisplay, spacing = (-0.02 * size.value).sp, modifier = modifier, lines = 1)

@Composable private fun HLine() = Box(Modifier.fillMaxWidth().height(1.dp).background(I.line))
@Composable private fun VLine() = Box(Modifier.width(1.dp).fillMaxHeight().background(I.line))

@Composable private fun CellHead(n: String, title: String, meta: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        M(n, 10f, I.dim); Spacer(Modifier.width(8.dp)); M(title, 11f, I.ink, FontWeight.SemiBold); Spacer(Modifier.weight(1f)); M(meta, 10f)
    }
}

@Composable private fun RowScope.Cell(weight: Float = 1f, content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.weight(weight).fillMaxHeight().padding(14.dp), content = content)

/** Cadran à graduations sur 270°. */
@Composable private fun TickDial(v: Int, c: Color, label: String, size: Dp = 96.dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val n = 41; val lit = (v / 100f * (n - 1)).roundToInt(); val r1 = this.size.minDimension / 2 - 2.dp.toPx()
                for (i in 0 until n) {
                    val a = Math.toRadians(135.0 + 270.0 * i / (n - 1)); val major = i % 10 == 0
                    val r2 = r1 - (if (major) 12.dp.toPx() else 8.dp.toPx())
                    drawLine(if (i <= lit) c else I.dim, Offset(center.x + r1 * cos(a).toFloat(), center.y + r1 * sin(a).toFloat()),
                        Offset(center.x + r2 * cos(a).toFloat(), center.y + r2 * sin(a).toFloat()), (if (major) 2.dp else 1.5.dp).toPx(), StrokeCap.Butt)
                }
            }
            N("$v", 26.sp)
        }
        M(label, 10f)
    }
}

@Composable
private fun IScreen(tab: Int, title: String, meta: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(I.bg)) {
        Column(Modifier.fillMaxSize().padding(top = 44.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) { M(meta, 10f); N(title, 30.sp) }
                Box(Modifier.size(40.dp).border(1.dp, I.line, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = I.ink) }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(40.dp).border(1.dp, I.line, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) { M("B", 14f, I.ink, FontWeight.Bold) }
            }
            HLine()
            content()
        }
        // la navigation est la dernière rangée de la grille ; « + » = cellule pleine sous le pouce
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(I.bg)) {
            HLine()
            Row(Modifier.fillMaxWidth().height(84.dp)) {
                listOf(Icons.Rounded.Today to "Jour", Icons.Rounded.Restaurant to "Repas", Icons.AutoMirrored.Rounded.DirectionsRun to "Sport").forEachIndexed { i, (ic, l) ->
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Box(Modifier.fillMaxWidth().height(2.dp).background(if (i == tab) I.lime else Color.Transparent))
                        Column(Modifier.fillMaxSize().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(ic, null, Modifier.size(22.dp), tint = if (i == tab) I.ink else I.sub); Spacer(Modifier.height(4.dp)); M(l, 10f, if (i == tab) I.ink else I.sub)
                        }
                    }
                    VLine()
                }
                Box(Modifier.width(96.dp).fillMaxHeight().background(I.lime).padding(bottom = 18.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(34.dp), tint = I.bg)
                }
            }
        }
    }
}

// ---- Jour --------------------------------------------------------------------------------------------------------------------

@Composable
fun InstHome() = IScreen(0, "Aujourd'hui", "Ven. 30.10 · 16:45") {
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        CellHead("01", "Forme", "Màj 07:22")
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) { N("78", 88.sp); M("/100", 12f, modifier = Modifier.padding(bottom = 16.dp)) }
                M("Prêt à pousser", 12f, I.lime, FontWeight.Bold)
            }
            // sept jours de forme : barres fines, valeur du jour
            Column(horizontalAlignment = Alignment.End) {
                Canvas(Modifier.size(126.dp, 64.dp)) {
                    val v = listOf(66, 71, 64, 70, 74, 72, 78); val bw = 4.dp.toPx(); val step = (size.width - bw) / 6
                    v.forEachIndexed { i, x -> val h = size.height * (x - 50) / 40f
                        drawRect(if (i == 6) I.lime else I.dim, Offset(i * step, size.height - h), Size(bw, h)) }
                    drawLine(I.line, Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
                }
                M("7 j · +6", 10f, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TickDial(64, I.blue, "Charge"); TickDial(84, I.violet, "Repos"); TickDial(42, I.amber, "Effort")
        }
    }
    HLine()
    Row(Modifier.fillMaxWidth().height(180.dp)) {
        Cell(1.35f) {
            CellHead("02", "Sommeil", "Score 84")
            Row(verticalAlignment = Alignment.Bottom) { N("7:32", 32.sp); M(" h", 12f, modifier = Modifier.padding(bottom = 6.dp)) }
            Spacer(Modifier.weight(1f))
            Row {
                Column(Modifier.height(64.dp), verticalArrangement = Arrangement.SpaceBetween) { listOf("Év", "Par", "Lég", "Pro").forEach { M(it, 8f, I.dim) } }
                Spacer(Modifier.width(6.dp))
                Canvas(Modifier.weight(1f).height(64.dp)) {
                    val total = night4.sumOf { it.second }.toFloat(); val lane = size.height / 4
                    for (k in 0..4) drawLine(I.line, Offset(0f, k * lane), Offset(size.width, k * lane), 1f)
                    var x = 0f
                    night4.forEach { (st, m) ->
                        val w = size.width * m / total; val y = (4 - st) * lane
                        drawRect(when (st) { 4 -> I.coral; 3 -> I.blue; 2 -> I.violet.copy(alpha = 0.6f); else -> I.violet }, Offset(x, y + 3f), Size(w - 1f, lane - 6f))
                        x += w
                    }
                }
            }
            Row { M("23:48", 9f); Spacer(Modifier.weight(1f)); M("07:20", 9f) }
        }
        VLine()
        Cell {
            CellHead("03", "Kcal")
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(112.dp)) {
                    val n = 48; val lit = (n * 1353f / 2280).roundToInt(); val r1 = size.minDimension / 2 - 2.dp.toPx(); val r2 = r1 - 9.dp.toPx()
                    for (i in 0 until n) {
                        val a = Math.toRadians(-90.0 + 360.0 * i / n)
                        drawLine(if (i < lit) I.ink else I.dim, Offset(center.x + r1 * cos(a).toFloat(), center.y + r1 * sin(a).toFloat()),
                            Offset(center.x + r2 * cos(a).toFloat(), center.y + r2 * sin(a).toFloat()), 1.5.dp.toPx())
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { N("927", 26.sp); M("rest.", 9f) }
            }
        }
    }
    HLine()
    Row(Modifier.fillMaxWidth().height(150.dp)) {
        Cell {
            CellHead("04", "Pas", "Obj 8k")
            N("6 240", 26.sp); Spacer(Modifier.weight(1f))
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                val bw = size.width / 24; val max = steps4.max().toFloat()
                steps4.forEachIndexed { i, v -> val h = if (i > 16) 1.dp.toPx() else maxOf(1.dp.toPx(), size.height * v / max)
                    drawRect(if (i == 16) I.lime else if (i > 16) I.dim else I.ink.copy(alpha = 0.75f), Offset(i * bw + 1f, size.height - h), Size(bw - 2f, h)) }
            }
            Row { listOf("00", "06", "12", "18").forEach { M(it, 8f, I.dim, modifier = Modifier.weight(1f)) } }
        }
        VLine()
        Cell {
            CellHead("05", "Cœur", "Repos")
            Row(verticalAlignment = Alignment.Bottom) { N("54", 26.sp); M(" bpm", 10f, modifier = Modifier.padding(bottom = 5.dp)) }
            Spacer(Modifier.weight(1f))
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                val v = heart4.map { it.toFloat() }; val lo = 52f; val hi = 59f
                val pts = v.mapIndexed { i, y -> Offset(size.width * i / (v.size - 1), size.height * (hi - y) / (hi - lo)) }
                val p = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(p, I.coral, style = Stroke(1.5.dp.toPx()))
                pts.forEach { drawRect(I.coral, it - Offset(1.5f, 1.5f) * density, Size(3f, 3f) * density) }
            }
            Row { M("min 53", 8f, I.dim); Spacer(Modifier.weight(1f)); M("max 58", 8f, I.dim) }
        }
    }
    HLine()
    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        M("›", 14f, I.lime, FontWeight.Bold); Spacer(Modifier.width(10.dp)); X("Charge correcte : séance Jambes conseillée.", 14.sp, I.ink, family = Inter)
    }
}

// ---- Repas -------------------------------------------------------------------------------------------------------------------

@Composable
fun InstFood() = IScreen(1, "Nutrition", "‹ Ven. 30.10 ›") {
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        CellHead("01", "Bilan", "Obj. 2 280")
        Row(verticalAlignment = Alignment.Bottom) { N("927", 64.sp); Column(Modifier.padding(start = 8.dp, bottom = 12.dp)) { M("kcal", 10f); M("restantes", 10f, I.lime) } }
        Spacer(Modifier.height(6.dp))
        // règle graduée 0 → 2 280 : repas en segments, curseur au total
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val k = size.width / 2400f; var x = 0f
            listOf(435 to I.amber, 676 to I.blue, 241 to I.violet).forEach { (v, c) -> drawRect(c, Offset(x, 0f), Size(v * k - 2f, 10.dp.toPx())); x += v * k }
            drawRect(I.dim, Offset(x, 0f), Size(2280 * k - x, 10.dp.toPx()))
            for (t in 0..2400 step 100) { val major = t % 500 == 0
                drawLine(if (major) I.sub else I.dim, Offset(t * k, 14.dp.toPx()), Offset(t * k, (if (major) 24 else 19).dp.toPx()), 1f) }
            drawLine(I.lime, Offset(2280 * k, 0f), Offset(2280 * k, 26.dp.toPx()), 2.dp.toPx())
        }
        Row { listOf("0", "500", "1000", "1500", "2000").forEach { M(it, 8f, I.dim, modifier = Modifier.weight(1f)) } }
    }
    HLine()
    Row(Modifier.fillMaxWidth().height(104.dp)) {
        listOf(Triple("Prot.", 98 to 150, I.coral), Triple("Lip.", 52 to 76, I.amber), Triple("Gluc.", 141 to 285, I.lime)).forEachIndexed { i, (n, v, c) ->
            Cell {
                M(n, 10f); Row(verticalAlignment = Alignment.Bottom) { N("${v.first}", 26.sp); M("/${v.second} g", 9f, modifier = Modifier.padding(bottom = 5.dp, start = 3.dp)) }
                Spacer(Modifier.weight(1f))
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    val n2 = 20; val lit = (v.first.toFloat() / v.second * n2).roundToInt(); val w = size.width / n2
                    repeat(n2) { drawRect(if (it < lit) c else I.dim, Offset(it * w, 0f), Size(w - 2f, size.height)) }
                }
            }
            if (i < 2) VLine()
        }
    }
    HLine()
    meals4.forEach { (name, time, foods) ->
        Row(Modifier.fillMaxWidth().background(I.cell).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            M(time, 11f, I.lime, FontWeight.Bold); Spacer(Modifier.width(10.dp)); M(name, 11f, I.ink, FontWeight.SemiBold); Spacer(Modifier.weight(1f)); M("${foods.sumOf { it.kcal }} kcal", 11f)
        }
        HLine()
        foods.forEachIndexed { i, f ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PixelIcon(f.icon, 26.dp); Spacer(Modifier.width(14.dp))
                X(f.name, 15.sp, I.ink, family = Inter, modifier = Modifier.weight(1f), lines = 1); M(f.qty, 10f, modifier = Modifier.width(76.dp))
                X("${f.kcal}".padStart(3), 14.sp, I.ink, FontWeight.Medium, family = Mono)
            }
            Box(Modifier.padding(start = if (i < foods.lastIndex) 56.dp else 0.dp).fillMaxWidth().height(1.dp).background(I.line))
        }
    }
}

// ---- Sport -------------------------------------------------------------------------------------------------------------------

@Composable
fun InstAct() = IScreen(2, "Activités", "Semaine 44 · 2 h 09") {
    Row(Modifier.fillMaxWidth().height(96.dp)) {
        listOf("L" to 48, "M" to 52, "M" to 0, "J" to 29, "V" to 0, "S" to -1, "D" to -1).forEachIndexed { i, (d, m) ->
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (i == 4) I.lime else Color.Transparent))
                Column(Modifier.fillMaxSize().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    M(d, 10f, if (i == 4) I.ink else I.sub)
                    Box(Modifier.weight(1f).padding(vertical = 6.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.width(5.dp).height(if (m > 0) (38 * m / 52).dp else 1.dp).background(if (m > 0) I.ink else I.dim))
                    }
                    M(if (m > 0) "$m" else "—", 9f, if (m > 0) I.ink else I.dim)
                }
            }
            if (i < 6) VLine()
        }
    }
    HLine()
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        CellHead("01", "Programme", "PPL · S3 · 2/4")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(150.dp).border(1.dp, I.line)) {
            program4.forEachIndexed { i, d ->
                Column(Modifier.weight(1f).fillMaxHeight().background(if (d.next) I.cell else Color.Transparent).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BodyCanvas(d.side, d.from, d.to, Modifier.fillMaxWidth().weight(1f)) { art, _, _, _ ->
                        art.parts.forEach { (id, p) -> drawPath(p, when { id in d.target -> if (d.next) I.lime else if (d.done) I.sub else I.blue; id == "body" -> Color(0xFF23262B); id == "bone" -> I.bg; else -> Color(0xFF2E3238) }) }
                    }
                    M(d.name, 10f, if (d.next) I.ink else I.sub, FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    M(when { d.done -> "Fait"; d.next -> "Suivant"; else -> "—" }, 8f, if (d.next) I.lime else I.dim)
                }
                if (i < 3) VLine()
            }
        }
        Row(Modifier.fillMaxWidth().height(54.dp).background(I.lime).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            M("Commencer Jambes", 14f, I.bg, FontWeight.Bold); Spacer(Modifier.weight(1f)); M("5 exos · 55 min", 10f, I.bg.copy(alpha = 0.7f)); Spacer(Modifier.width(10.dp))
            Icon(Icons.Rounded.PlayArrow, null, tint = I.bg)
        }
    }
    HLine()
    Row(Modifier.fillMaxWidth().height(150.dp)) {
        Cell {
            CellHead("02", "Course", "Hier")
            Row(verticalAlignment = Alignment.Bottom) { N("5,2", 34.sp); M(" km", 11f, modifier = Modifier.padding(bottom = 6.dp)) }
            M("28:41", 11f, I.ink); M("5:31 /km", 11f)
        }
        Box(Modifier.weight(1.1f).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize()) {
                val g = 14.dp.toPx(); var x = 0f
                while (x < size.width) { drawLine(I.line, Offset(x, 0f), Offset(x, size.height), 1f); x += g }
                var y = 0f; while (y < size.height) { drawLine(I.line, Offset(0f, y), Offset(size.width, y), 1f); y += g }
                val pts = listOf(0.12f to 0.8f, 0.22f to 0.52f, 0.18f to 0.28f, 0.38f to 0.16f, 0.56f to 0.32f, 0.72f to 0.18f, 0.9f to 0.34f, 0.84f to 0.64f, 0.62f to 0.72f, 0.46f to 0.86f, 0.26f to 0.82f)
                    .map { Offset(it.first * size.width, it.second * size.height) }
                drawPath(smooth(pts), I.lime, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                drawRect(I.ink, pts.first() - Offset(4f, 4f) * density, Size(8f, 8f) * density); drawCircle(I.lime, 4.dp.toPx(), pts.last())
            }
        }
    }
    HLine()
    listOf(Triple(Icons.Rounded.FitnessCenter, "Pull", "Mar." to "52:10  12 340 kg"), Triple(Icons.AutoMirrored.Rounded.DirectionsBike, "Vélo", "Lun." to "48:02    21,0 km")).forEach { (ic, n, r) ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(ic, null, Modifier.size(18.dp), tint = I.sub); Spacer(Modifier.width(12.dp))
            X(n, 15.sp, I.ink, family = Inter, modifier = Modifier.width(60.dp)); M(r.first, 10f, modifier = Modifier.weight(1f)); M(r.second, 11f, I.ink)
        }
        HLine()
    }
}
