package app.superhealth.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Tour 4 — A. Keynote : noir profond, tuiles « bento », anneaux et courbes en dégradé lumineux, corps en rayons X.
 * Navigation : les trois sections en grands mots en haut (on glisse de l'un à l'autre), recherche en haut à droite,
 * un seul orbe lumineux « + » sous le pouce.
 */

private object K {
    val bg = Color(0xFF000000); val tile = Color(0xFF0E0E11); val tile2 = Color(0xFF1A1A20); val line = Color(0x14FFFFFF)
    val ink = Color.White; val sub = Color(0xFF8E8E96); val dim = Color(0xFF3A3A40)
    val forme = Color(0xFF3DF5A7) to Color(0xFF26C6FF)
    val charge = Color(0xFF0A84FF) to Color(0xFF64D2FF)
    val repos = Color(0xFF7D5CFF) to Color(0xFFD17BFF)
    val effort = Color(0xFFFF6A3D) to Color(0xFFFF3D8B)
    val kcal = Color(0xFF2EE6C5) to Color(0xFF20B8FF)
    val sleep = Color(0xFF6E6BFF) to Color(0xFFB387FF)
    val heart = Color(0xFFFF375F) to Color(0xFFFF8AAE)
    val steps = Color(0xFFFFD60A) to Color(0xFFFF9F0A)
    val prot = Color(0xFFFF5E7E) to Color(0xFFFF9AA8)
    val fat = Color(0xFFFFD60A) to Color(0xFFFFB340)
    val carb = Color(0xFF3DF5A7) to Color(0xFF7CFFCB)
}

private fun h(p: Pair<Color, Color>) = Brush.horizontalGradient(listOf(p.first, p.second))

@Composable
private fun KTile(modifier: Modifier = Modifier, pad: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(modifier.clip(shape).background(K.tile).background(Brush.verticalGradient(listOf(Color(0x0DFFFFFF), Color.Transparent)))
        .border(1.dp, K.line, shape).padding(pad), content = content)
}

@Composable
private fun KLabel(icon: ImageVector, text: String, hue: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = hue); Spacer(Modifier.width(6.dp)); X(text, 13.sp, K.sub, FontWeight.Medium)
    }
}

@Composable
private fun KScreen(tab: Int, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(K.bg)) {
        // lueur d'ambiance en haut, teintée par la section
        val amb = listOf(K.forme.second, K.kcal.first, K.effort.first)[tab]
        Box(Modifier.offset((-80).dp, (-140).dp).size(420.dp).blur(120.dp).background(Brush.radialGradient(listOf(amb.copy(alpha = 0.35f), Color.Transparent))))
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                X("VENDREDI 30 OCTOBRE", 12.sp, K.sub, FontWeight.SemiBold, spacing = 1.6.sp, modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).background(K.tile2), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(21.dp), tint = K.ink)
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(K.repos.first, K.effort.second))), contentAlignment = Alignment.Center) {
                    X("B", 17.sp, K.ink, FontWeight.SemiBold)
                }
            }
            // sections en grands mots : l'actuelle à gauche, les suivantes s'effacent vers la droite
            Box(Modifier.fillMaxWidth().clipToBounds()) {
                Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true), horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.Bottom) {
                    val names = listOf("Aujourd'hui", "Nutrition", "Activités")
                    (names.indices.map { (tab + it) % 3 }).forEachIndexed { k, i ->
                        X(names[i], 36.sp, if (k == 0) K.ink else if (k == 1) Color(0xFF3C3C42) else Color(0xFF232327), FontWeight.SemiBold,
                            display = true, lines = 1, spacing = (-0.5).sp)
                    }
                }
            }
            content()
        }
        // fondu bas + orbe d'action
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Transparent, K.bg))))
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 12.dp).size(102.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { glow(circlePath(center, 33.dp.toPx()), Color(0xCCB066FF), 18.dp.toPx()) }
            Box(Modifier.size(66.dp).clip(CircleShape).background(Brush.linearGradient(listOf(K.repos.first, K.effort.second))), contentAlignment = Alignment.Center) {
                Box(Modifier.size(66.dp).background(Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color.Transparent))))
                Icon(Icons.Rounded.Add, null, Modifier.size(32.dp), tint = K.ink)
            }
        }
        // points de page (les mots se balaient)
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 58.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { Box(Modifier.size(if (it == tab) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (it == tab) K.ink else K.dim)) }
        }
    }
}

// ---- Aujourd'hui ----------------------------------------------------------------------------------------------------------

@Composable
fun KeynoteHome() = KScreen(0) {
    KTile(Modifier.fillMaxWidth(), pad = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                X("Forme", 13.sp, K.sub, FontWeight.Medium)
                X("Prêt à pousser", 24.sp, weight = FontWeight.SemiBold, display = true, brush = h(K.forme))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = K.sub)
        }
        Box(Modifier.fillMaxWidth().height(214.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(208.dp)) {
                val c = center; val w = 15.dp.toPx(); val gap = 4.dp.toPx(); var r = size.minDimension / 2 - w / 2 - 4.dp.toPx()
                listOf(0.64f to K.charge, 0.84f to K.repos, 0.42f to K.effort).forEach { (f, p) ->
                    ring(c, r, w, f, p.first, p.second, p.second.copy(alpha = 0.13f)); r -= w + gap
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                X("78", 46.sp, weight = FontWeight.SemiBold, display = true, brush = Brush.verticalGradient(listOf(Color.White, K.forme.first)), spacing = (-1).sp)
                X("sur 100", 12.sp, K.sub)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Charge", "64", K.charge), Triple("Repos", "84", K.repos), Triple("Effort", "42", K.effort)).forEach { (n, v, p) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(K.tile2).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Box(Modifier.width(22.dp).height(4.dp).clip(CircleShape).background(h(p)))
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) { X(v, 20.sp, K.ink, FontWeight.SemiBold, display = true); Spacer(Modifier.width(5.dp)); X(n, 12.sp, K.sub, modifier = Modifier.padding(bottom = 3.dp)) }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KTile(Modifier.weight(1.4f).height(168.dp)) {
            KLabel(Icons.Rounded.Bedtime, "Sommeil", K.sleep.second)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                X("7", 30.sp, K.ink, FontWeight.SemiBold, display = true); X(" h ", 16.sp, K.sub, modifier = Modifier.padding(bottom = 4.dp))
                X("32", 30.sp, K.ink, FontWeight.SemiBold, display = true); Spacer(Modifier.weight(1f))
                X("84", 17.sp, weight = FontWeight.SemiBold, brush = h(K.sleep), modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            KHypno(Modifier.fillMaxWidth().height(60.dp))
            Row { X("23:48", 11.sp, K.sub); Spacer(Modifier.weight(1f)); X("07:20", 11.sp, K.sub) }
        }
        KTile(Modifier.weight(1f).height(168.dp)) {
            KLabel(Icons.Rounded.LocalFireDepartment, "Calories", K.kcal.first)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(104.dp)) { ring(center, size.minDimension / 2 - 7.dp.toPx(), 11.dp.toPx(), 1353f / 2280, K.kcal.first, K.kcal.second, K.kcal.second.copy(alpha = 0.12f)) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    X("927", 24.sp, K.ink, FontWeight.SemiBold, display = true); X("restantes", 11.sp, K.sub)
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KTile(Modifier.weight(1f).height(150.dp)) {
            KLabel(Icons.AutoMirrored.Rounded.DirectionsRun, "Pas", K.steps.first)
            X("6 240", 26.sp, K.ink, FontWeight.SemiBold, display = true)
            Spacer(Modifier.weight(1f))
            Canvas(Modifier.fillMaxWidth().height(46.dp)) {
                val n = 24; val gap = 2.dp.toPx(); val bw = (size.width - gap * (n - 1)) / n; val max = steps4.max().toFloat()
                steps4.forEachIndexed { i, v ->
                    val x = i * (bw + gap)
                    if (i > 16) drawCircle(K.dim, bw / 2.2f, Offset(x + bw / 2, size.height - bw / 2))
                    else {
                        val bh = maxOf(bw, size.height * v / max)
                        drawRoundRect(Brush.verticalGradient(listOf(K.steps.first, K.steps.second), startY = size.height - bh, endY = size.height),
                            Offset(x, size.height - bh), Size(bw, bh), CornerRadius(bw / 2), alpha = if (v == 0) 0.25f else 1f)
                    }
                }
            }
        }
        KTile(Modifier.weight(1f).height(150.dp)) {
            KLabel(Icons.Rounded.Favorite, "Cœur au repos", K.heart.first)
            Row(verticalAlignment = Alignment.Bottom) { X("54", 26.sp, K.ink, FontWeight.SemiBold, display = true); X(" bpm", 13.sp, K.sub, modifier = Modifier.padding(bottom = 4.dp)) }
            Spacer(Modifier.weight(1f))
            KLine(heart4.map { it.toFloat() }, K.heart, Modifier.fillMaxWidth().height(46.dp))
        }
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(K.tile)
        .border(1.dp, Brush.horizontalGradient(listOf(K.repos.second.copy(alpha = 0.7f), K.effort.second.copy(alpha = 0.5f), Color.Transparent)), RoundedCornerShape(22.dp))
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(20.dp), tint = K.repos.second); Spacer(Modifier.width(12.dp))
        X("Bonne nuit, charge correcte : Jambes aujourd'hui.", 14.sp, Color(0xDDFFFFFF), modifier = Modifier.weight(1f))
    }
}

/** Hypnogramme : courbe en marches arrondies, dégradé de l'éveil (haut) au profond (bas), aire lumineuse dessous. */
@Composable
private fun KHypno(modifier: Modifier) {
    Canvas(modifier) {
        val total = night4.sumOf { it.second }.toFloat(); val pad = 4.dp.toPx(); val r = 4.dp.toPx()
        fun y(st: Int) = pad + (size.height - 2 * pad) * (4 - st) / 3f
        val line = Path(); var x = 0f
        night4.forEachIndexed { i, (st, m) ->
            val w = size.width * m / total; val yy = y(st)
            if (i == 0) line.moveTo(0f, yy) else {
                val py = y(night4[i - 1].first); val d = if (yy > py) r else -r
                line.lineTo(x - r, py); line.quadraticTo(x, py, x, py + d); line.lineTo(x, yy - d); line.quadraticTo(x, yy, x + r, yy)
            }
            x += w
        }
        line.lineTo(size.width, y(night4.last().first))
        val grad = Brush.verticalGradient(listOf(Color(0xFFFF9FB8), Color(0xFF7CC8FF), Color(0xFF8E8BFF), Color(0xFF5B3FE0)), startY = pad, endY = size.height - pad)
        val area = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(area, Brush.verticalGradient(listOf(Color(0x557D5CFF), Color.Transparent)))
        glow(line, Color(0xAA8E8BFF), 5.dp.toPx(), 3.dp.toPx())
        drawPath(line, grad, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun KLine(v: List<Float>, p: Pair<Color, Color>, modifier: Modifier) {
    Canvas(modifier) {
        val lo = v.min() - 1; val hi = v.max() + 1; val pad = 6.dp.toPx()
        val pts = v.mapIndexed { i, y -> Offset(pad + (size.width - 2 * pad) * i / (v.size - 1), pad + (size.height - 2 * pad) * (hi - y) / (hi - lo)) }
        val line = smooth(pts)
        val area = smooth(pts).apply { lineTo(pts.last().x, size.height); lineTo(pts[0].x, size.height); close() }
        drawPath(area, Brush.verticalGradient(listOf(p.first.copy(alpha = 0.35f), Color.Transparent)))
        glow(line, p.first.copy(alpha = 0.8f), 5.dp.toPx(), 3.dp.toPx())
        drawPath(line, Brush.horizontalGradient(listOf(p.second, p.first)), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        glow(circlePath(pts.last(), 7.dp.toPx()), p.first, 6.dp.toPx())
        drawCircle(Color.White, 3.5.dp.toPx(), pts.last())
    }
}

// ---- Nutrition -------------------------------------------------------------------------------------------------------------

@Composable
fun KeynoteJournal() = KScreen(1) {
    // semaine : un micro-anneau par jour
    Row(Modifier.fillMaxWidth()) {
        listOf("L" to 26, "M" to 27, "M" to 28, "J" to 29, "V" to 30, "S" to 31, "D" to 1).forEachIndexed { i, (d, n) ->
            val today = i == 4
            Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (today) K.tile2 else Color.Transparent).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                X(d, 11.sp, if (today) K.ink else K.sub, FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(30.dp)) {
                        val f = listOf(0.96f, 1f, 0.88f, 0.99f, 0.59f, 0f, 0f)[i]
                        ring(center, size.minDimension / 2 - 2.5.dp.toPx(), 4.dp.toPx(), f, K.kcal.first, K.kcal.second, Color(0x1FFFFFFF), halo = if (today) 0.6f else 0f)
                    }
                    X("$n", 10.sp, if (i > 4) K.dim else K.ink, FontWeight.Medium)
                }
            }
        }
    }
    // résumé en une rangée
    KTile(Modifier.fillMaxWidth(), pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(66.dp)) {
                val w = 8.dp.toPx(); val r = size.minDimension / 2 - w / 2; val gap = 14f
                listOf(98f / 150 to K.prot, 52f / 76 to K.fat, 141f / 285 to K.carb).forEachIndexed { i, (f, p) ->
                    val start = -90f + i * 120f + gap / 2; val span = 120f - gap
                    drawArc(p.second.copy(alpha = 0.15f), start, span, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
                    glow(arcPath(center, r, start, span * f), p.first.copy(alpha = 0.6f), 5.dp.toPx(), w)
                    drawArc(p.first, start, span * f, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.width(98.dp)) {
                X("927", 30.sp, weight = FontWeight.SemiBold, display = true, brush = h(K.kcal)); X("kcal restantes", 12.sp, K.sub)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(Triple("Prot.", 98 to 150, K.prot), Triple("Lip.", 52 to 76, K.fat), Triple("Gluc.", 141 to 285, K.carb)).forEach { (n, v, p) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        X(n, 11.sp, K.sub, modifier = Modifier.width(34.dp))
                        Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(Color(0x1FFFFFFF))) {
                            Box(Modifier.fillMaxWidth(v.first.toFloat() / v.second).height(5.dp).clip(CircleShape).background(h(p)))
                        }
                        X("${v.first}", 12.sp, K.ink, FontWeight.SemiBold, modifier = Modifier.width(34.dp).padding(start = 6.dp), lines = 1)
                    }
                }
            }
        }
    }
    meals4.forEachIndexed { mi, (name, time, foods) ->
        KTile(Modifier.fillMaxWidth(), pad = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(h(listOf(K.steps, K.kcal, K.repos)[mi])))
                Spacer(Modifier.width(8.dp))
                X(name, 17.sp, K.ink, FontWeight.SemiBold, display = true, modifier = Modifier.weight(1f))
                X("$time  ·  ${foods.sumOf { it.kcal }} kcal", 13.sp, K.sub)
            }
            foods.forEach { f ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(K.tile2), contentAlignment = Alignment.Center) { PixelIcon(f.icon, 28.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { X(f.name, 15.sp, K.ink, lines = 1); X(f.qty, 12.sp, K.sub) }
                    X("${f.kcal}", 14.sp, Color(0xCCFFFFFF), FontWeight.Medium)
                }
            }
        }
    }
}

// ---- Activités -------------------------------------------------------------------------------------------------------------

@Composable
fun KeynoteActivities() = KScreen(2) {
    // semaine, fine
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) { X("3", 22.sp, K.ink, FontWeight.SemiBold, display = true); X(" activités", 14.sp, K.sub, modifier = Modifier.padding(bottom = 2.dp)) }
            X("2 h 09 cette semaine", 12.sp, K.sub)
        }
        listOf("L" to 48, "M" to 52, "M" to 0, "J" to 29, "V" to 0, "S" to -1, "D" to -1).forEach { (d, m) ->
            Column(Modifier.width(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.width(7.dp).height(34.dp)) {
                    val bh = if (m > 0) size.height * m / 52 else size.width
                    if (m > 0) glow(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, size.height - bh, size.width, size.height, CornerRadius(size.width / 2))) },
                        K.effort.second.copy(alpha = 0.6f), 4.dp.toPx())
                    drawRoundRect(if (m > 0) Brush.verticalGradient(listOf(K.effort.second, K.effort.first)) else Brush.linearGradient(listOf(K.dim, K.dim)),
                        Offset(0f, size.height - bh), Size(size.width, bh), CornerRadius(size.width / 2), alpha = if (m < 0) 0.4f else 1f)
                }
                Spacer(Modifier.height(4.dp)); X(d, 10.sp, K.sub, FontWeight.Medium)
            }
        }
    }
    KTile(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { X("Push Pull Legs", 21.sp, K.ink, FontWeight.SemiBold, display = true); X("Semaine 3 · 2 séances sur 4", 13.sp, K.sub) }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = K.sub)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            program4.forEach { d ->
                val shape = RoundedCornerShape(22.dp)
                Column(Modifier.weight(1f).clip(shape).background(if (d.next) Color(0xFF1C1418) else K.tile2)
                    .then(if (d.next) Modifier.border(1.5.dp, Brush.verticalGradient(listOf(K.effort.first, K.effort.second.copy(alpha = 0.2f))), shape) else Modifier)
                    .padding(top = 8.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(104.dp)) {
                        XRay(d, Modifier.fillMaxSize())
                        if (d.done) Box(Modifier.align(Alignment.TopEnd).padding(end = 6.dp).size(18.dp).clip(CircleShape).background(K.ink), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = K.bg)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    X(d.name, 13.sp, if (d.done) K.sub else K.ink, FontWeight.SemiBold, lines = 1)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(0.85f).height(40.dp).offset(y = 8.dp).blur(22.dp).clip(CircleShape).background(h(K.effort)))
            Row(Modifier.fillMaxSize().clip(CircleShape).background(h(K.effort)), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PlayArrow, null, tint = K.ink); Spacer(Modifier.width(6.dp)); X("Commencer Jambes", 17.sp, K.ink, FontWeight.SemiBold)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Icons.Rounded.Bolt to "Séance libre", Icons.Rounded.AutoAwesome to "Demander au coach").forEach { (ic, l) ->
            Row(Modifier.clip(CircleShape).background(K.tile2).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(ic, null, Modifier.size(17.dp), tint = K.ink); Spacer(Modifier.width(7.dp)); X(l, 14.sp, K.ink, FontWeight.Medium)
            }
        }
    }
    KTile(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                KLabel(Icons.AutoMirrored.Rounded.DirectionsRun, "Course · hier", K.forme.first)
                Row(verticalAlignment = Alignment.Bottom) { X("5,2", 34.sp, K.ink, FontWeight.SemiBold, display = true); X(" km", 15.sp, K.sub, modifier = Modifier.padding(bottom = 5.dp)) }
                X("28:41 · 5:31 /km", 13.sp, K.sub)
            }
            Canvas(Modifier.size(130.dp, 82.dp)) {
                val pts = listOf(0.08f to 0.78f, 0.2f to 0.52f, 0.16f to 0.26f, 0.36f to 0.12f, 0.55f to 0.3f, 0.7f to 0.14f, 0.92f to 0.3f, 0.86f to 0.62f, 0.62f to 0.7f, 0.46f to 0.88f, 0.22f to 0.84f)
                    .map { Offset(it.first * size.width, it.second * size.height) }
                val route = smooth(pts)
                glow(route, K.forme.second.copy(alpha = 0.7f), 6.dp.toPx(), 4.dp.toPx())
                drawPath(route, Brush.linearGradient(listOf(K.forme.first, K.forme.second)), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(K.ink, 4.dp.toPx(), pts.first()); drawCircle(K.forme.first, 4.dp.toPx(), pts.last(), style = Stroke(2.dp.toPx()))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple(Icons.Rounded.FitnessCenter, "Pull · mardi", "12 340 kg"), Triple(Icons.AutoMirrored.Rounded.DirectionsBike, "Vélo · lundi", "21,0 km")).forEach { (ic, l, v) ->
            KTile(Modifier.weight(1f)) { KLabel(ic, l, K.effort.first); X(v, 22.sp, K.ink, FontWeight.SemiBold, display = true) }
        }
    }
}

/** Corps « rayons X » : silhouette sombre, muscles visés lumineux. */
@Composable
private fun XRay(d: Day, modifier: Modifier) {
    val hot = when { d.next -> K.effort; d.done -> Color(0xFF8E8E96) to Color(0xFF5A5A62); else -> K.charge }
    BodyCanvas(d.side, d.from, d.to, modifier) { art, s, y0, hh ->
        art.parts.forEach { (id, p) ->
            when {
                id == "body" -> drawPath(p, Color(0xFF26262E))
                id == "bone" -> drawPath(p, Color(0xFF1A1A20))
                id !in d.target -> drawPath(p, Color(0xFF34343E))
            }
        }
        art.parts.filter { it.first in d.target }.forEach { (_, p) ->
            if (!d.done) glow(p, hot.second.copy(alpha = 0.9f), 9.dp.toPx() / s)
            drawPath(p, Brush.verticalGradient(listOf(hot.first, hot.second), startY = y0, endY = y0 + hh))
        }
    }
}
