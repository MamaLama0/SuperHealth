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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Tour 8 — Compact. Formes classiques (cercles, rectangles arrondis), tons sur tons par mesure, au service de la densité : pas de bloc vedette, texte
 * ≥ 12 sp, valeurs 22–26 sp, tuiles à la taille de leur contenu. Même code en clair et en sombre.
 */

private class Pc(val dark: Boolean) {
    val bg = if (dark) Color(0xFF111312) else Color(0xFFF3F2EE)
    val card = if (dark) Color(0xFF1B1E1C) else Color.White
    val card2 = if (dark) Color(0xFF242826) else Color(0xFFF1F0EC)
    val ink = if (dark) Color(0xFFEEF1EE) else Color(0xFF1C211E)
    val sub = if (dark) Color(0xFF9AA39E) else Color(0xFF5F6964)
    val faint = if (dark) Color(0xFF353A37) else Color(0xFFE2E1DC)
    // teintes de fond : les chaudes restent neutres en sombre (sinon elles virent au marron)
    val sage = if (dark) Color(0xFF1B2A21) else Color(0xFFE1EEE3)
    val lav = if (dark) Color(0xFF221F35) else Color(0xFFE9E5F8)
    val peach = if (dark) card else Color(0xFFFCE8DA)
    val sky = if (dark) Color(0xFF19253A) else Color(0xFFE0ECF8)
    val rose = if (dark) card else Color(0xFFF9E2E8)
    val green = if (dark) Color(0xFF6FD39A) else Color(0xFF2F8F5B)
    val indigo = if (dark) Color(0xFFA99BFF) else Color(0xFF4B3FB8)
    val orange = if (dark) Color(0xFFFF9A5C) else Color(0xFFE0662F)
    val blue = if (dark) Color(0xFF7CB3FF) else Color(0xFF2F6FD6)
    val pink = if (dark) Color(0xFFFF7F9F) else Color(0xFFD6456B)
    val amber = if (dark) Color(0xFFFFCB57) else Color(0xFFD99A00)
    val onStrong = if (dark) Color(0xFF111312) else Color.White
}
private val LocalPc = staticCompositionLocalOf { Pc(false) }

private val ArchC: Shape = GenericShape { s, _ ->
    val r = s.width / 2; val c = minOf(14f, s.width / 5)
    moveTo(0f, s.height - c); lineTo(0f, r); arcTo(Rect(0f, 0f, s.width, s.width), 180f, 180f, false)
    lineTo(s.width, s.height - c); quadraticTo(s.width, s.height, s.width - c, s.height); lineTo(c, s.height); quadraticTo(0f, s.height, 0f, s.height - c); close()
}

@Composable private fun Ct(text: String, size: TextUnit, color: Color? = null, weight: FontWeight = FontWeight.Normal, display: Boolean = false, modifier: Modifier = Modifier) =
    X(text, size, color ?: LocalPc.current.ink, weight, display = display, modifier = modifier, lines = 1)

@Composable private fun CTile(bg: Color? = null, modifier: Modifier = Modifier, pad: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(bg ?: LocalPc.current.card).padding(pad), content = content)

@Composable private fun PebbleIco(icon: ImageVector, tint: Color, bg: Color, size: Dp = 30.dp, seed: Float = 1f) =
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(size * 0.58f), tint = tint) }

@Composable private fun TileHead(icon: ImageVector, title: String, tint: Color, iconBg: Color, trailing: String? = null) {
    val p = LocalPc.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        PebbleIco(icon, tint, iconBg, 28.dp); Spacer(Modifier.width(8.dp)); Ct(title, 14.sp, p.sub, FontWeight.Medium, modifier = Modifier.weight(1f))
        if (trailing != null) Ct(trailing, 13.sp, tint, FontWeight.SemiBold)
    }
}

@Composable
private fun CScreen(dark: Boolean, tab: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    val p = Pc(dark)
    CompositionLocalProvider(LocalPc provides p) {
        Box(Modifier.fillMaxSize().background(p.bg)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(top = 44.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.padding(start = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Ct("Vendredi 30 octobre", 13.sp, p.sub, FontWeight.Medium); Ct(title, 26.sp, weight = FontWeight.Medium, display = true) }
                    Box(Modifier.size(40.dp).clip(CircleShape).background(p.card), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, null, Modifier.size(21.dp), tint = p.ink) }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(40.dp).clip(CircleShape).background(p.indigo), contentAlignment = Alignment.Center) { Ct("B", 16.sp, p.onStrong, FontWeight.SemiBold) }
                }
                content()
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(110.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, p.bg))))
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.shadow(if (dark) 0.dp else 10.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000)).clip(CircleShape)
                    .background(if (dark) p.card2 else p.card).padding(5.dp)) {
                    listOf(Icons.Rounded.Today to "Aujourd'hui", Icons.Rounded.Restaurant to "Nutrition", Icons.AutoMirrored.Rounded.DirectionsRun to "Activités").forEachIndexed { i, (ic, l) ->
                        val on = i == tab
                        Row(Modifier.clip(CircleShape).background(if (on) p.ink else Color.Transparent).padding(horizontal = if (on) 14.dp else 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(ic, null, Modifier.size(21.dp), tint = if (on) p.bg else p.sub); if (on) Ct("  $l", 14.sp, p.bg, FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(58.dp).shadow(if (dark) 0.dp else 10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(p.green), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(30.dp), tint = p.onStrong)
                }
            }
        }
    }
}

// ---- Aujourd'hui --------------------------------------------------------------------------------------------------------------

@Composable
fun CompactHome(dark: Boolean) = CScreen(dark, 0, "Aujourd'hui") {
    val p = LocalPc.current
    CTile(p.sage) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(70.dp).clip(CircleShape).background(p.green), contentAlignment = Alignment.Center) { Ct("78", 28.sp, p.onStrong, FontWeight.Medium, true) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Ct("Prêt à pousser", 18.sp, weight = FontWeight.SemiBold, display = true)
                Ct("Bien récupéré, charge équilibrée", 13.sp, p.sub)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Triple("Charge", 64, p.blue), Triple("Repos", 84, p.indigo), Triple("Effort", 42, p.orange)).forEachIndexed { i, (n, v, c) ->
                Row(Modifier.weight(1f).clip(CircleShape).background(if (dark) p.card else Color(0xB3FFFFFF)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(c)); Ct(" $n", 13.sp, p.sub, modifier = Modifier.weight(1f)); Ct("$v", 14.sp, weight = FontWeight.SemiBold)
                }
            }
        }
    }
    Row(Modifier.height(162.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CTile(p.lav, Modifier.weight(1f).fillMaxHeight()) {
            TileHead(Icons.Rounded.Bedtime, "Sommeil", p.indigo, p.card, "84")
            Spacer(Modifier.height(6.dp)); Ct("7 h 32", 24.sp, weight = FontWeight.Medium, display = true); Ct("Coucher conseillé 23:15", 12.sp, p.sub)
            Spacer(Modifier.weight(1f))
            Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                val total = night4.sumOf { it.second }.toFloat(); var x = 0f; val pts = mutableListOf<Offset>()
                night4.forEach { (st, m) -> val w = size.width * m / total; pts += Offset(x + w / 2, size.height * (0.12f + 0.25f * (4 - st))); x += w }
                val line = smooth(pts)
                drawPath(Path().apply { addPath(line); lineTo(pts.last().x, size.height); lineTo(pts[0].x, size.height); close() }, p.indigo.copy(alpha = 0.18f))
                drawPath(line, p.indigo, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        CTile(p.peach, Modifier.weight(1f).fillMaxHeight()) {
            TileHead(Icons.Rounded.LocalFireDepartment, "Calories", p.orange, if (dark) p.card2 else p.card)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(if (dark) p.card2 else p.card), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(50.dp)) {
                        val w = 6.dp.toPx(); val r = size.minDimension / 2 - w / 2
                        listOf(98f / 150 to p.pink, 52f / 76 to p.amber, 141f / 285 to p.green).forEachIndexed { i, (f, c) ->
                            val st = -90f + i * 120f + 8f; val sp = 104f
                            drawArc(p.faint, st, sp, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
                            drawArc(c, st, sp * f, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column { Ct("927", 24.sp, weight = FontWeight.Medium, display = true); Ct("restantes", 12.sp, p.sub) }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("P", 98 to 150, p.pink), Triple("L", 52 to 76, p.amber), Triple("G", 141 to 285, p.green)).forEach { (n, v, c) ->
                    Column(Modifier.weight(1f)) {
                        Ct("$n ${v.first}", 12.sp, weight = FontWeight.SemiBold)
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(p.faint)) { Box(Modifier.fillMaxWidth(v.first.toFloat() / v.second).fillMaxHeight().clip(CircleShape).background(c)) }
                    }
                }
            }
        }
    }
    Row(Modifier.height(100.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CTile(p.sky, Modifier.weight(1f).fillMaxHeight(), pad = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) { PebbleIco(Icons.AutoMirrored.Rounded.DirectionsRun, p.blue, p.card, 24.dp, 0.4f); Ct("  Pas", 13.sp, p.sub, FontWeight.Medium) }
            Ct("6 240", 20.sp, weight = FontWeight.Medium, display = true); Spacer(Modifier.weight(1f))
            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                val bw = size.width / 17; val max = steps4.max().toFloat()
                steps4.take(17).forEachIndexed { i, v -> val h = maxOf(2.dp.toPx(), size.height * v / max); drawRoundRect(if (i == 16) p.blue else p.blue.copy(alpha = 0.4f), Offset(i * bw + 1f, size.height - h), Size(bw - 2f, h), androidx.compose.ui.geometry.CornerRadius(bw)) }
            }
        }
        CTile(p.rose, Modifier.weight(1f).fillMaxHeight(), pad = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) { PebbleIco(Icons.Rounded.Favorite, p.pink, if (dark) p.card2 else p.card, 24.dp, 2.2f); Ct("  Repos", 13.sp, p.sub, FontWeight.Medium) }
            Ct("54 bpm", 20.sp, weight = FontWeight.Medium, display = true); Spacer(Modifier.weight(1f))
            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                val pts = heart4.mapIndexed { i, y -> Offset(size.width * i / (heart4.size - 1), size.height * (59 - y) / 7f) }
                drawPath(smooth(pts), p.pink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        CTile(p.card, Modifier.weight(1f).fillMaxHeight(), pad = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) { PebbleIco(Icons.Rounded.MonitorWeight, p.green, p.sage, 24.dp, 3.3f); Ct("  Poids", 13.sp, p.sub, FontWeight.Medium) }
            Ct("81,2 kg", 20.sp, weight = FontWeight.Medium, display = true); Spacer(Modifier.weight(1f))
            Ct("−1,4 kg / mois", 12.sp, p.green, FontWeight.Medium)
        }
    }
    CTile(p.card, pad = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            program4.forEach { d ->
                Box(Modifier.padding(end = 5.dp).size(40.dp, 54.dp).clip(RoundedCornerShape(16.dp)).background(if (d.next) p.orange else if (d.done) p.sage else p.card2)) {
                    BodyIn(d.side, d.target, if (d.next) p.onStrong else if (d.done) p.green else p.indigo, if (d.next) p.onStrong.copy(alpha = 0.3f) else p.faint, if (d.next) p.onStrong.copy(alpha = 0.18f) else p.faint.copy(alpha = 0.6f),
                        d.from, d.to, Modifier.fillMaxSize().padding(top = 6.dp, bottom = 3.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 6.dp)) { Ct("Jambes", 17.sp, weight = FontWeight.SemiBold, display = true); Ct("3 / 4 · 55 min", 12.sp, p.sub) }
            Box(Modifier.size(46.dp).clip(CircleShape).background(p.orange), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(26.dp), tint = p.onStrong) }
        }
    }
    CTile(p.card, pad = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ct("Ressenti du matin", 14.sp, p.sub, FontWeight.Medium, modifier = Modifier.weight(1f))
            listOf(Color(0xFFE5484D), Color(0xFFF08C3A), Color(0xFFE8B931), Color(0xFF5CB85C), Color(0xFF2FA4A0)).map { if (dark) it.mix(Color.White, 0.15f) else it }.forEachIndexed { i, c ->
                Box(Modifier.padding(start = 6.dp).size(if (i == 3) 34.dp else 28.dp).clip(CircleShape).background(if (i == 3) c else if (dark) p.card2 else c.copy(alpha = 0.22f))
                    .then(if (i == 3) Modifier.border(2.dp, p.ink, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                    Ct("${i + 1}", 12.sp, if (i == 3) Color.White else if (dark) c else p.ink, FontWeight.SemiBold)
                }
            }
        }
    }
    Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = p.indigo); Spacer(Modifier.width(8.dp))
        Ct("Charge correcte : séance Jambes conseillée.", 14.sp, p.ink)
    }
}

// ---- Nutrition ---------------------------------------------------------------------------------------------------------------

@Composable
fun CompactFood(dark: Boolean) = CScreen(dark, 1, "Nutrition") {
    val p = LocalPc.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("L" to 26, "M" to 27, "M" to 28, "J" to 29, "V" to 30, "S" to 31, "D" to 1).forEachIndexed { i, (d, n) ->
            val on = i == 4
            Column(Modifier.width(44.dp).clip(RoundedCornerShape(16.dp)).background(if (on) p.ink else Color.Transparent).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Ct(d, 11.sp, if (on) p.bg.copy(alpha = 0.7f) else p.sub, FontWeight.Medium); Ct("$n", 15.sp, if (on) p.bg else if (i > 4) p.sub else p.ink, FontWeight.SemiBold)
                Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(if (i < 4) p.green else Color.Transparent))
            }
        }
    }
    CTile(p.card) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 9.dp.toPx(); val r = size.minDimension / 2 - w / 2; var a = -90f
                    drawCircle(p.faint, r, center, style = Stroke(w))
                    listOf(435 to p.amber, 676 to p.green, 241 to p.indigo).forEach { (k, c) -> val sw = 360f * k / 2280
                        drawArc(c, a + 2f, sw - 4f, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round)); a += sw }
                }
                Ct("59 %", 12.sp, weight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Ct("927 kcal restantes", 18.sp, weight = FontWeight.SemiBold, display = true); Ct("1 353 mangées sur 2 280", 13.sp, p.sub) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Triple("Protéines", 98 to 150, p.pink), Triple("Lipides", 52 to 76, p.amber), Triple("Glucides", 141 to 285, p.green)).forEach { (n, v, c) ->
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) { Ct("${v.first}", 15.sp, weight = FontWeight.SemiBold); Ct(" / ${v.second} g", 12.sp, p.sub) }
                    Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(p.faint)) { Box(Modifier.fillMaxWidth(v.first.toFloat() / v.second).fillMaxHeight().clip(CircleShape).background(c)) }
                    Ct(n, 12.sp, p.sub)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(p.card).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = p.sub); Ct("  Ajouter un aliment", 15.sp, p.sub, modifier = Modifier.weight(1f))
        Box(Modifier.size(38.dp).clip(CircleShape).background(p.card2), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(20.dp), tint = p.ink) }
    }
    meals4.forEachIndexed { mi, (name, time, foods) ->
        val c = listOf(p.amber, p.green, p.indigo)[mi]; val tint = listOf(p.peach, p.sage, p.lav)[mi]
        CTile(p.card, pad = 12.dp) {
            Row(Modifier.padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(8.dp))
                Ct(name, 16.sp, weight = FontWeight.SemiBold, display = true); Ct("  $time", 13.sp, p.sub, modifier = Modifier.weight(1f))
                Ct("${foods.sumOf { it.kcal }} kcal", 13.sp, weight = FontWeight.SemiBold)
            }
            foods.forEach { f ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) { PixelIcon(f.icon, 26.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Ct(f.name, 15.sp); Ct(f.qty, 12.sp, p.sub) }
                    Ct("${f.kcal}", 14.sp, p.sub, FontWeight.Medium)
                }
            }
        }
    }
}

// ---- Activités ---------------------------------------------------------------------------------------------------------------

@Composable
fun CompactAct(dark: Boolean) = CScreen(dark, 2, "Activités") {
    val p = LocalPc.current
    CTile(p.card, pad = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Ct("3 activités", 16.sp, weight = FontWeight.SemiBold, display = true); Ct("2 h 09 cette semaine", 12.sp, p.sub) }
            listOf("L" to 48, "M" to 52, "M" to 0, "J" to 29, "V" to 0, "S" to -1, "D" to -1).forEachIndexed { i, (d, m) ->
                Column(Modifier.width(27.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(if (m > 0) (10 + 12 * m / 52).dp else 8.dp).clip(CircleShape)
                            .background(if (m > 0) p.orange else Color.Transparent).border(if (m > 0) 0.dp else 1.5.dp, if (i == 4) p.ink else p.faint, CircleShape))
                    }
                    Ct(d, 11.sp, if (i == 4) p.ink else p.sub, FontWeight.Medium)
                }
            }
        }
    }
    CTile(p.card) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Ct("Push Pull Legs", 17.sp, weight = FontWeight.SemiBold, display = true); Ct("Semaine 3 · 2 séances sur 4", 12.sp, p.sub) }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.sub)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            program4.forEach { d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(16.dp)).background(if (d.next) p.orange else if (d.done) p.sage else p.card2)) {
                        BodyIn(d.side, d.target, if (d.next) p.onStrong else if (d.done) p.green else p.indigo, if (d.next) p.onStrong.copy(alpha = 0.3f) else p.faint,
                            if (d.next) p.onStrong.copy(alpha = 0.18f) else p.faint.copy(alpha = 0.6f), d.from, d.to, Modifier.fillMaxSize().padding(top = 12.dp, bottom = 6.dp, start = 4.dp, end = 4.dp))
                        if (d.done) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).clip(CircleShape).background(p.green), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = p.onStrong)
                        }
                    }
                    Ct(d.name, 13.sp, if (d.done) p.sub else p.ink, FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(p.ink).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(p.orange), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(24.dp), tint = p.onStrong) }
            Ct("  Commencer Jambes", 16.sp, p.bg, FontWeight.SemiBold, modifier = Modifier.weight(1f)); Ct("5 exos · 55 min  ", 12.sp, p.bg.copy(alpha = 0.65f))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Icons.Rounded.Bolt to "Libre", Icons.Rounded.AutoAwesome to "Coach", Icons.Rounded.History to "Historique").forEach { (ic, l) ->
            Row(Modifier.weight(1f).clip(CircleShape).background(p.card).padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(ic, null, Modifier.size(17.dp), tint = p.ink); Ct("  $l", 14.sp, weight = FontWeight.Medium)
            }
        }
    }
    CTile(p.card, pad = 12.dp) {
        Ct("Récentes", 14.sp, p.sub, FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        listOf(Triple(Icons.AutoMirrored.Rounded.DirectionsRun, "Course à pied" to "Hier · 28:41 · 5:31 /km", "5,2 km"),
            Triple(Icons.Rounded.FitnessCenter, "Pull" to "Mardi · 52 min · 18 séries", "12,3 t"),
            Triple(Icons.AutoMirrored.Rounded.DirectionsBike, "Vélo" to "Lundi · 48 min · 26 km/h", "21,0 km")).forEachIndexed { i, (ic, t, v) ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                PebbleIco(ic, listOf(p.green, p.orange, p.blue)[i], listOf(p.sage, if (dark) p.card2 else p.peach, p.sky)[i], 38.dp, i * 1.9f)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Ct(t.first, 15.sp, weight = FontWeight.Medium); Ct(t.second, 12.sp, p.sub) }
                if (i == 0) Canvas(Modifier.size(46.dp, 26.dp)) {
                    val pts = listOf(0f to 0.8f, 0.2f to 0.4f, 0.45f to 0.6f, 0.6f to 0.15f, 0.85f to 0.35f, 1f to 0.1f).map { Offset(it.first * size.width, it.second * size.height) }
                    drawPath(smooth(pts), p.green, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
                Ct(v, 15.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple("Volume muscu", "24,6 t", "+8 % vs sem. passée"), Triple("Allure course", "5:31 /km", "−6 s sur 4 sem.")).forEach { (l, v, d) ->
            CTile(p.card, Modifier.weight(1f), pad = 12.dp) { Ct(l, 13.sp, p.sub, FontWeight.Medium); Ct(v, 20.sp, weight = FontWeight.Medium, display = true); Ct(d, 12.sp, p.green, FontWeight.Medium) }
        }
    }
}
