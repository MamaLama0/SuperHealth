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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Direction 2 — Affiche. Une page de magazine sportif : chaque panneau s'ouvre sur un aplat de SA couleur (Forme vert,
 * Nutrition tomate, Activités cobalt) avec un chiffre géant, puis une grille stricte à deux colonnes séparée par des
 * filets noirs épais — pas de cartes. Noir, papier et une seule couleur par panneau ; motif du cercle (jauge, bouton +).
 */

private object A {
    val paper = Color(0xFFF2F1EE); val ink = Color(0xFF0E0E0E); val sub = Color(0xFF5E5E5E); val faint = Color(0x1F0E0E0E)
    val green = Color(0xFF2BD67B); val tomato = Color(0xFFFF5533); val cobalt = Color(0xFF2F54FF)
}

@Composable private fun D(text: String, size: TextUnit, color: Color = A.ink, weight: FontWeight = FontWeight.SemiBold, modifier: Modifier = Modifier) =
    X(text, size, color, weight, family = InterDisplay, spacing = (-0.035 * size.value).sp, modifier = modifier, lines = 1)
@Composable private fun Tq(text: String, size: TextUnit = 14.sp, color: Color = A.ink, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) =
    X(text, size, color, weight, family = Inter, modifier = modifier, lines = 1)

@Composable private fun Rule(thick: Boolean = true) = Box(Modifier.fillMaxWidth().height(if (thick) 2.dp else 1.dp).background(if (thick) A.ink else A.faint))

/** Section de la grille : numéro et titre à gauche (colonne fixe), contenu à droite. */
@Composable private fun Sec(n: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Rule()
        Row(Modifier.padding(top = 12.dp, bottom = 16.dp)) {
            Column(Modifier.width(104.dp)) { Tq(n, 12.sp, A.sub, FontWeight.Medium); D(title, 20.sp) }
            Column(Modifier.weight(1f), content = content)
        }
    }
}

@Composable
private fun AScreen(tab: Int, accent: Color, heroInk: Color, heroH: Int, hero: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(A.paper)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().height(heroH.dp).background(accent).padding(start = 20.dp, end = 20.dp, top = 50.dp, bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tq("Vendredi 30 octobre", 15.sp, heroInk, FontWeight.Medium, Modifier.weight(1f))
                    Box(Modifier.size(38.dp).clip(CircleShape).background(heroInk), contentAlignment = Alignment.Center) { Tq("B", 15.sp, accent, FontWeight.Bold) }
                }
                hero()
            }
            content()
        }
        // barre noire ; le « + » est un disque de la couleur du panneau qui déborde
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(110.dp)) {
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(84.dp).background(A.ink).padding(start = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("Aujourd'hui", "Nutrition", "Activités").forEachIndexed { i, l ->
                    Row(Modifier.padding(end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (i == tab) { Box(Modifier.size(8.dp).clip(CircleShape).background(accent)); Spacer(Modifier.width(7.dp)) }
                        Tq(l, 15.sp, if (i == tab) Color.White else Color(0xFF8A8A8A), FontWeight.SemiBold)
                    }
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(end = 18.dp).size(72.dp).clip(CircleShape).background(accent).border(4.dp, A.ink, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, null, Modifier.size(34.dp), tint = heroInk)
            }
        }
    }
}

@Composable private fun InkBar(f: Float, track: Color = A.faint, fill: Color = A.ink, h: Int = 6) =
    Box(Modifier.fillMaxWidth().height(h.dp).background(track)) { Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(fill)) }

// ---- Aujourd'hui --------------------------------------------------------------------------------------------------------------

@Composable
fun AffHome() = AScreen(0, A.green, A.ink, 400, hero = {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            D("78", 150.sp, modifier = Modifier.offset(x = (-6).dp))
            D("Prêt à pousser.", 24.sp, modifier = Modifier.offset(y = (-14).dp))
        }
        Canvas(Modifier.size(118.dp)) {
            val w = 22.dp.toPx(); val r = size.minDimension / 2 - w / 2
            drawCircle(A.ink.copy(alpha = 0.12f), r, center, style = Stroke(w))
            drawArc(A.ink, -90f, 360f * 0.78f, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Butt))
        }
    }
    Spacer(Modifier.weight(1f))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf("Charge" to 64, "Repos" to 84, "Effort" to 42).forEach { (n, v) ->
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) { D("$v", 30.sp); Tq(" $n", 13.sp, A.ink, FontWeight.Medium, Modifier.padding(bottom = 5.dp)) }
                InkBar(v / 100f, A.ink.copy(alpha = 0.15f))
            }
        }
    }
}) {
    Spacer(Modifier.height(18.dp))
    Sec("01", "Sommeil") {
        Row(verticalAlignment = Alignment.Bottom) { D("7 h 32", 36.sp); Tq("  score 84", 14.sp, A.sub, modifier = Modifier.padding(bottom = 6.dp)) }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(50.dp)) {
            val total = night4.sumOf { it.second }.toFloat(); var x = 0f; val p = Path()
            night4.forEachIndexed { i, (st, m) ->
                val w = size.width * m / total; val y = size.height * (4 - st) / 3.4f + 2f
                if (i == 0) p.moveTo(0f, y) else p.lineTo(x, y); p.lineTo(x + w, y)
                if (st == 1) drawRect(A.green, Offset(x, y), Size(w, size.height - y))
                x += w
            }
            drawPath(p, A.ink, style = Stroke(2.dp.toPx()))
        }
        Row { Tq("23:48", 11.sp, A.sub); Spacer(Modifier.weight(1f)); Tq("profond ▮", 11.sp, A.sub); Spacer(Modifier.weight(1f)); Tq("07:20", 11.sp, A.sub) }
    }
    Sec("02", "Assiette") {
        Row(verticalAlignment = Alignment.Bottom) { D("927", 36.sp); Tq("  kcal restantes", 14.sp, A.sub, modifier = Modifier.padding(bottom = 6.dp)) }
        Spacer(Modifier.height(8.dp))
        listOf(Triple("Protéines", 98, 150), Triple("Lipides", 52, 76), Triple("Glucides", 141, 285)).forEach { (n, v, of) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Tq(n, 12.sp, modifier = Modifier.width(72.dp)); Box(Modifier.weight(1f)) { InkBar(v.toFloat() / of) }; Tq("$v g", 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.width(48.dp).padding(start = 8.dp))
            }
        }
    }
    Sec("03", "Corps") {
        Row {
            Column(Modifier.weight(1f)) {
                Tq("Pas", 12.sp, A.sub); D("6 240", 26.sp)
                Canvas(Modifier.fillMaxWidth().padding(end = 12.dp).height(30.dp)) {
                    val bw = size.width / 17; val max = steps4.max().toFloat()
                    steps4.take(17).forEachIndexed { i, v -> val h = maxOf(1.5f, size.height * v / max); drawRect(if (i == 16) A.green else A.ink, Offset(i * bw, size.height - h), Size(bw - 2f, h)) }
                }
            }
            Column(Modifier.weight(1f)) {
                Tq("Cœur au repos", 12.sp, A.sub); D("54 bpm", 26.sp)
                Canvas(Modifier.fillMaxWidth().height(30.dp)) {
                    val v = heart4; val pts = v.mapIndexed { i, y -> Offset(size.width * i / (v.size - 1), size.height * (59 - y) / 7f) }
                    drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }, A.ink, style = Stroke(2.dp.toPx()))
                    drawCircle(A.green, 5.dp.toPx(), pts.last()); drawCircle(A.ink, 5.dp.toPx(), pts.last(), style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}

// ---- Nutrition ---------------------------------------------------------------------------------------------------------------

@Composable
fun AffFood() = AScreen(1, A.tomato, A.ink, 330, hero = {
    D("927", 150.sp, modifier = Modifier.offset(x = (-6).dp))
    D("kcal restantes sur 2 280.", 22.sp, modifier = Modifier.offset(y = (-14).dp))
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth().height(14.dp).background(A.ink.copy(alpha = 0.15f)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(435, 676, 241).forEach { Box(Modifier.weight(it.toFloat()).fillMaxHeight().background(A.ink)) }
        Spacer(Modifier.weight(927f))
    }
    Row(Modifier.padding(top = 6.dp)) { Tq("Matin 435", 12.sp, weight = FontWeight.Medium); Spacer(Modifier.width(18.dp)); Tq("Midi 676", 12.sp, weight = FontWeight.Medium); Spacer(Modifier.width(18.dp)); Tq("16 h 241", 12.sp, weight = FontWeight.Medium) }
}) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(Triple("Protéines", 98, 150), Triple("Lipides", 52, 76), Triple("Glucides", 141, 285)).forEach { (n, v, of) ->
            Column(Modifier.weight(1f)) { Tq(n, 12.sp, A.sub); Row(verticalAlignment = Alignment.Bottom) { D("$v", 30.sp); Tq(" /$of g", 12.sp, A.sub, modifier = Modifier.padding(bottom = 5.dp)) }; InkBar(v.toFloat() / of, fill = A.tomato) }
        }
    }
    meals4.forEachIndexed { mi, (name, time, foods) ->
        Sec(time, name.replace("Petit-déjeuner", "Petit-déj.")) {
            foods.forEach { f ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
                    PixelIcon(f.icon, 20.dp); Spacer(Modifier.width(8.dp))
                    Tq(f.name, 15.sp)
                    Canvas(Modifier.weight(1f).height(10.dp).padding(horizontal = 6.dp)) {
                        var x = 0f; while (x < size.width) { drawCircle(A.ink.copy(alpha = 0.45f), 1.dp.toPx(), Offset(x, size.height - 2.dp.toPx())); x += 5.dp.toPx() }
                    }
                    Tq("${f.kcal}", 15.sp, weight = FontWeight.SemiBold)
                }
            }
            Tq("${foods.sumOf { it.kcal }} kcal", 12.sp, A.tomato, FontWeight.SemiBold, Modifier.padding(top = 4.dp))
        }
    }
}

// ---- Activités ---------------------------------------------------------------------------------------------------------------

@Composable
fun AffAct() = AScreen(2, A.cobalt, Color.White, 430, hero = {
    Box(Modifier.fillMaxWidth().weight(1f)) {
        val next = program4.first { it.next }
        BodyCanvas(next.side, 0.42f, 1f, Modifier.align(Alignment.CenterEnd).offset(x = 44.dp, y = 10.dp).size(150.dp, 300.dp)) { art, _, _, _ ->
            art.parts.forEach { (id, p) -> drawPath(p, when { id in next.target -> Color.White; id == "body" -> Color(0x33FFFFFF); id == "bone" -> Color(0x00000000); else -> Color(0x26FFFFFF) }) }
        }
        Column(Modifier.padding(top = 18.dp)) {
            Tq("Séance 03 / 04", 15.sp, Color.White, FontWeight.Medium)
            D("Jambes", 84.sp, Color.White, modifier = Modifier.offset(x = (-4).dp))
            Tq("Cuisses · mollets", 16.sp, Color.White, FontWeight.Medium); Tq("5 exercices · 55 min", 16.sp, Color(0xCCFFFFFF))
        }
        Row(Modifier.align(Alignment.BottomStart).clip(CircleShape).background(A.paper).padding(horizontal = 22.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            D("Commencer", 18.sp); Spacer(Modifier.width(10.dp)); Icon(Icons.Rounded.ArrowForward, null, tint = A.ink)
        }
    }
}) {
    Spacer(Modifier.height(18.dp))
    Sec("01", "Programme") {
        program4.forEachIndexed { i, d ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                D("0${i + 1}", 18.sp, if (d.next) A.cobalt else A.sub, modifier = Modifier.width(36.dp))
                D(d.name, 18.sp, if (d.done) A.sub else A.ink, modifier = Modifier.weight(1f))
                when { d.done -> Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = A.sub); d.next -> Tq("aujourd'hui", 12.sp, A.cobalt, FontWeight.SemiBold); else -> {} }
            }
            if (i < 3) Rule(false)
        }
    }
    Sec("02", "Semaine") {
        Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
            listOf("L" to 48, "M" to 52, "M" to 0, "J" to 29, "V" to 0, "S" to -1, "D" to -1).forEachIndexed { i, (d, m) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(if (m > 0) (46 * m / 52).dp else 2.dp).background(if (m > 0) A.cobalt else A.faint)
                        .then(if (i == 4) Modifier.border(2.dp, A.ink) else Modifier))
                    Tq(d, 12.sp, if (i == 4) A.ink else A.sub, FontWeight.SemiBold, Modifier.padding(top = 4.dp))
                }
            }
        }
        Tq("3 séances · 2 h 09", 12.sp, A.sub, modifier = Modifier.padding(top = 6.dp))
    }
    Sec("03", "Récent") {
        listOf("Course" to "5,2 km · 28:41", "Pull" to "12 340 kg · 52 min").forEachIndexed { i, (n, v) ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Bottom) { D(n, 17.sp, modifier = Modifier.width(80.dp)); Tq(v, 14.sp, A.sub) }
        }
    }
}
