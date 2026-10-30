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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Direction 3 — Galet. Material 3 Expressive poussé jusqu'au bout : chaque mesure a SA forme (Forme = biscuit, sommeil =
 * arche, assiette = disque, pas et cœur = galets), posées en composition libre qui se chevauche au lieu d'une grille de
 * cartes. Tons sur tons doux (sauge, lavande, pêche, ciel), encre vert-de-gris. Barre d'outils flottante façon Pixel.
 */

private object G {
    val bg = Color(0xFFF3F2EE); val ink = Color(0xFF1E2421); val sub = Color(0xFF66706A); val card = Color.White
    val sage = Color(0xFFD8EADB); val green = Color(0xFF2F8F5B); val lav = Color(0xFFE3DEF6); val indigo = Color(0xFF4B3FB8)
    val peach = Color(0xFFFCE1CF); val orange = Color(0xFFE8703A); val sky = Color(0xFFD9E8F7); val blue = Color(0xFF2F6FD6)
    val rose = Color(0xFFF8DCE3); val pink = Color(0xFFD6456B)
}

/** Arche : demi-cercle en haut, droite en bas. */
private val Arch: Shape = GenericShape { s, _ ->
    val r = s.width / 2
    moveTo(0f, s.height); lineTo(0f, r); arcTo(Rect(0f, 0f, s.width, s.width), 180f, 180f, false); lineTo(s.width, s.height - 28f); quadraticTo(s.width, s.height, s.width - 28f, s.height)
    lineTo(28f, s.height); quadraticTo(0f, s.height, 0f, s.height - 28f); close()
}

@Composable private fun Gt(text: String, size: TextUnit, color: Color = G.ink, weight: FontWeight = FontWeight.Normal, display: Boolean = false, modifier: Modifier = Modifier) =
    X(text, size, color, weight, display = display, modifier = modifier, lines = 1)

@Composable
private fun GScreen(tab: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(G.bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Gt("Vendredi 30 octobre", 14.sp, G.sub, FontWeight.Medium); Gt(title, 32.sp, weight = FontWeight.Medium, display = true) }
                Box(Modifier.size(44.dp).clip(CircleShape).background(G.card), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = G.ink) }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(44.dp).clip(cookie(8, 0.07f)).background(G.indigo), contentAlignment = Alignment.Center) { Gt("B", 17.sp, Color.White, FontWeight.SemiBold) }
            }
            content()
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.shadow(12.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000)).clip(CircleShape).background(G.card).padding(6.dp)) {
                listOf(Icons.Rounded.Today to "Aujourd'hui", Icons.Rounded.Restaurant to "Nutrition", Icons.AutoMirrored.Rounded.DirectionsRun to "Activités").forEachIndexed { i, (ic, l) ->
                    val on = i == tab
                    Row(Modifier.clip(CircleShape).background(if (on) G.ink else Color.Transparent).padding(horizontal = if (on) 16.dp else 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(ic, null, Modifier.size(22.dp), tint = if (on) Color.White else G.sub); if (on) Gt("  $l", 14.sp, Color.White, FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(66.dp).shadow(12.dp, cookie(9, 0.06f)).clip(cookie(9, 0.06f)).background(G.green), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, null, Modifier.size(32.dp), tint = Color.White)
            }
        }
    }
}

@Composable private fun Pebble(seed: Float, bg: Color, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.clip(pebble(seed)).background(bg).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = content)

// ---- Aujourd'hui --------------------------------------------------------------------------------------------------------------

@Composable
fun GalHome() = GScreen(0, "Bonjour B") {
    // composition : le biscuit de Forme et trois galets qui s'y appuient
    Box(Modifier.fillMaxWidth().height(262.dp)) {
        Box(Modifier.size(214.dp).offset(y = 20.dp).clip(cookie(12, 0.05f)).background(G.green), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Gt("Forme", 15.sp, Color(0xCCFFFFFF), FontWeight.Medium); Gt("78", 76.sp, Color.White, FontWeight.Medium, true); Gt("prêt à pousser", 15.sp, Color.White, FontWeight.Medium)
            }
        }
        Pebble(0.6f, G.sky, Modifier.size(112.dp, 100.dp).align(Alignment.TopEnd).offset(x = (-14).dp, y = 4.dp)) {
            Gt("64", 30.sp, G.blue, FontWeight.Medium, true); Gt("Charge", 13.sp, G.blue)
        }
        Pebble(2.1f, G.lav, Modifier.size(104.dp, 96.dp).align(Alignment.CenterEnd).offset(x = 6.dp, y = 30.dp)) {
            Gt("84", 30.sp, G.indigo, FontWeight.Medium, true); Gt("Repos", 13.sp, G.indigo)
        }
        Pebble(4.0f, G.peach, Modifier.size(96.dp, 88.dp).align(Alignment.BottomEnd).offset(x = (-102).dp, y = 6.dp)) {
            Gt("42", 28.sp, G.orange, FontWeight.Medium, true); Gt("Effort", 13.sp, G.orange)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // nuit : une arche
        Column(Modifier.weight(1f).height(236.dp).clip(Arch).background(G.indigo).padding(start = 16.dp, end = 16.dp, top = 30.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Bedtime, null, Modifier.size(26.dp), tint = Color(0xFFFFE9B0))
            Gt("7 h 32", 30.sp, Color.White, FontWeight.Medium, true); Gt("score 84", 13.sp, Color(0xCCFFFFFF))
            Spacer(Modifier.weight(1f))
            Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                val total = night4.sumOf { it.second }.toFloat(); var x = 0f
                val pts = mutableListOf<Offset>()
                night4.forEach { (st, m) -> val w = size.width * m / total; pts += Offset(x + w / 2, size.height * (0.1f + 0.27f * (4 - st))); x += w }
                val line = smooth(pts)
                drawPath(Path().apply { addPath(line); lineTo(pts.last().x, size.height); lineTo(pts[0].x, size.height); close() }, Color(0x33FFFFFF))
                drawPath(line, Color.White, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        // assiette : un disque
        Column(Modifier.weight(1f).height(236.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(158.dp).clip(CircleShape).background(G.peach), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(138.dp)) {
                    val w = 14.dp.toPx(); val r = size.minDimension / 2 - w / 2; val gap = 16f
                    listOf(98f / 150 to G.pink, 52f / 76 to G.orange, 141f / 285 to G.green).forEachIndexed { i, (f, c) ->
                        val start = -90f + i * 120f + gap / 2; val span = 120f - gap
                        drawArc(Color.White, start, span, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
                        drawArc(c, start, span * f, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Gt("927", 28.sp, G.ink, FontWeight.Medium, true); Gt("kcal restantes", 11.sp, G.sub) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("P 98" to G.pink, "L 52" to G.orange, "G 141" to G.green).forEach { (t, c) ->
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Gt(" $t", 12.sp, G.sub, FontWeight.Medium) }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Pebble(1.3f, G.sky, Modifier.weight(1f).height(128.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Rounded.DirectionsRun, null, Modifier.size(18.dp), tint = G.blue); Gt(" 6 240 pas", 18.sp, G.ink, FontWeight.Medium, true) }
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth(0.8f).height(36.dp)) {
                val bw = size.width / 17; val max = steps4.max().toFloat()
                steps4.take(17).forEachIndexed { i, v -> val h = maxOf(bw, size.height * v / max)
                    drawRoundRect(if (i == 16) G.blue else G.blue.copy(alpha = 0.45f), Offset(i * bw + 1.5f, size.height - h), Size(bw - 3f, h), androidx.compose.ui.geometry.CornerRadius(bw)) }
            }
        }
        Pebble(3.2f, G.rose, Modifier.weight(1f).height(128.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Favorite, null, Modifier.size(18.dp), tint = G.pink); Gt(" 54 bpm", 18.sp, G.ink, FontWeight.Medium, true) }
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth(0.8f).height(36.dp)) {
                val v = heart4.map { it.toFloat() }; val pts = v.mapIndexed { i, y -> Offset(size.width * i / (v.size - 1), size.height * (59 - y) / 7f) }
                drawPath(smooth(pts), G.pink, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)); drawCircle(G.pink, 5.dp.toPx(), pts.last())
            }
        }
    }
}

// ---- Nutrition ---------------------------------------------------------------------------------------------------------------

@Composable
fun GalFood() = GScreen(1, "Nutrition") {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Mar.", "Mer.", "Jeu.", "Aujourd'hui").forEachIndexed { i, d ->
            Box(Modifier.clip(CircleShape).background(if (i == 3) G.ink else G.card).padding(horizontal = 14.dp, vertical = 9.dp)) { Gt(d, 13.sp, if (i == 3) Color.White else G.sub, FontWeight.Medium) }
        }
    }
    Box(Modifier.fillMaxWidth().height(206.dp)) {
        Box(Modifier.size(200.dp).clip(CircleShape).background(G.card), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(176.dp)) {
                val w = 20.dp.toPx(); val r = size.minDimension / 2 - w / 2; var a = -90f
                drawCircle(G.bg, r, center, style = Stroke(w))
                listOf(435 to G.orange, 676 to G.green, 241 to G.indigo).forEach { (k, c) ->
                    val sw = 360f * k / 2280; drawArc(c, a + 2f, sw - 4f, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round)); a += sw
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Gt("927", 40.sp, G.ink, FontWeight.Medium, true); Gt("kcal restantes", 12.sp, G.sub) }
        }
        Column(Modifier.align(Alignment.CenterEnd), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Protéines", "98 / 150 g", G.rose to G.pink), Triple("Lipides", "52 / 76 g", G.peach to G.orange), Triple("Glucides", "141 / 285 g", G.sage to G.green)).forEachIndexed { i, (n, v, c) ->
                Column(Modifier.offset(x = (if (i == 1) -14 else 0).dp).width(138.dp).clip(RoundedCornerShape(26.dp)).background(c.first).padding(horizontal = 16.dp, vertical = 9.dp)) {
                    Gt(n, 12.sp, c.second, FontWeight.Medium); Gt(v, 16.sp, G.ink, FontWeight.Medium, true)
                }
            }
        }
    }
    meals4.forEachIndexed { mi, (name, time, foods) ->
        val tint = listOf(G.peach, G.sage, G.lav)[mi]; val strong = listOf(G.orange, G.green, G.indigo)[mi]
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 34.dp, topEnd = 12.dp, bottomEnd = 34.dp, bottomStart = 12.dp)).background(tint).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Gt(name, 18.sp, G.ink, FontWeight.Medium, true, Modifier.weight(1f)); Gt("$time · ${foods.sumOf { it.kcal }} kcal", 13.sp, strong, FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                foods.forEach { f ->
                    Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(54.dp).clip(pebble(f.kcal / 40f)).background(Color(0xB3FFFFFF)), contentAlignment = Alignment.Center) { PixelIcon(f.icon, 34.dp) }
                        Gt(f.name.substringBefore(' '), 12.sp, G.ink, FontWeight.Medium, modifier = Modifier.padding(top = 4.dp)); Gt("${f.kcal}", 11.sp, G.sub)
                    }
                }
            }
        }
    }
}

// ---- Activités ---------------------------------------------------------------------------------------------------------------

@Composable
fun GalAct() = GScreen(2, "Activités") {
    Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        listOf("L" to 48, "M" to 52, "M" to 0, "J" to 29, "V" to 0, "S" to -1, "D" to -1).forEachIndexed { i, (d, m) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    val s = if (m > 0) (20 + 20 * m / 52).dp else 14.dp
                    Box(Modifier.size(s).clip(pebble(i * 1.3f)).background(if (m > 0) G.orange else if (i == 4) G.ink else Color(0xFFDCDAD4)))
                }
                Gt(d, 12.sp, if (i == 4) G.ink else G.sub, FontWeight.Medium)
            }
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(G.card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Gt("Push Pull Legs", 20.sp, G.ink, FontWeight.Medium, true); Gt("Semaine 3 · 2 séances sur 4", 13.sp, G.sub) }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(176.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            program4.forEach { d ->
                val bg = when { d.next -> G.orange; d.done -> G.sage; else -> G.lav }
                Column(Modifier.weight(1f).height(if (d.next) 176.dp else 148.dp).clip(Arch).background(bg).padding(top = 14.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        BodyIn(d.side, d.target, if (d.next) Color.White else if (d.done) G.green else G.indigo,
                            if (d.next) Color(0x40FFFFFF) else Color(0x1A000000), if (d.next) Color(0x26FFFFFF) else Color(0x12000000), d.from, d.to, Modifier.fillMaxSize().padding(horizontal = 6.dp))
                        if (d.done) Box(Modifier.align(Alignment.TopCenter).size(22.dp).clip(cookie(8, 0.08f)).background(G.green), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = Color.White)
                        }
                    }
                    Gt(d.name, 13.sp, if (d.next) Color.White else G.ink, FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(G.ink).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(cookie(9, 0.07f)).background(G.orange), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(28.dp), tint = Color.White) }
            Gt("  Commencer Jambes", 17.sp, Color.White, FontWeight.SemiBold, modifier = Modifier.weight(1f)); Gt("55 min  ", 13.sp, Color(0x99FFFFFF))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1.3f).height(140.dp).clip(RoundedCornerShape(topStart = 36.dp, topEnd = 14.dp, bottomEnd = 36.dp, bottomStart = 14.dp)).background(G.sky).padding(16.dp)) {
            Gt("Course · hier", 13.sp, G.blue, FontWeight.Medium); Gt("5,2 km", 26.sp, G.ink, FontWeight.Medium, true)
            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                val pts = listOf(0f to 0.7f, 0.15f to 0.5f, 0.3f to 0.62f, 0.45f to 0.3f, 0.6f to 0.45f, 0.75f to 0.2f, 0.9f to 0.4f, 1f to 0.3f).map { Offset(it.first * size.width, it.second * size.height) }
                drawPath(smooth(pts), G.blue, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)); drawCircle(G.blue, 5.dp.toPx(), pts.last())
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Triple(Icons.Rounded.FitnessCenter, "12 340 kg", "Pull · mar."), Triple(Icons.AutoMirrored.Rounded.DirectionsBike, "21,0 km", "Vélo · lun.")).forEachIndexed { i, (ic, v, l) ->
                Row(Modifier.fillMaxWidth().height(65.dp).clip(CircleShape).background(G.card).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(pebble(i * 2f)).background(if (i == 0) G.peach else G.sage), contentAlignment = Alignment.Center) { Icon(ic, null, Modifier.size(19.dp), tint = if (i == 0) G.orange else G.green) }
                    Column(Modifier.padding(start = 8.dp)) { Gt(v, 15.sp, G.ink, FontWeight.Medium, true); Gt(l, 11.sp, G.sub) }
                }
            }
        }
    }
}
