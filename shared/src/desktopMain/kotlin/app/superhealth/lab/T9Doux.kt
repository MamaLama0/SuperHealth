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
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Tour 9 — C. Doux (lignée Gentler Streak / Bevel / Oura). Cartes pastel en dégradé, grands rayons, illustrations utiles :
 * le soleil de la Forme sur son arc, le ciel de nuit du sommeil, les repas posés sur la course du soleil, le chemin du programme.
 * Barre flottante (idée Pixel) et + sous le pouce. Sombre en bleu nuit, teintes froides uniquement (pas de brun).
 */

private class DxP(val dark: Boolean) {
    val bg = if (dark) Color(0xFF0C1120) else Color(0xFFF6F4FB)
    val card = if (dark) Color(0xFF151C2E) else Color.White
    val card2 = if (dark) Color(0xFF1E2740) else Color(0xFFF1EEF8)
    val ink = if (dark) Color(0xFFEEF0FA) else Color(0xFF1E1B2E)
    val sub = if (dark) Color(0xFF8D93AB) else Color(0xFF75718A)
    val track = if (dark) Color(0xFF263049) else Color(0xFFECE9F4)
    private fun c(l: Long, d: Long) = if (dark) Color(d) else Color(l)
    val mint = c(0xFF22B886, 0xFF5FE0B3); val sky = c(0xFF3E95F0, 0xFF7DBBFF); val lilac = c(0xFF8B67EE, 0xFFB39BFF)
    val peach = c(0xFFFF8A57, 0xFFFFAE85); val rose = c(0xFFF0578D, 0xFFFF8DB4); val sun = c(0xFFF5B024, 0xFFFFD36B)
    // fonds pastel ; en sombre, seules les teintes froides teintent (le chaud passerait au brun)
    val mintBg = c(0xFFDDF6EC, 0xFF12302E); val skyBg = c(0xFFDFEDFF, 0xFF15274A); val lilacBg = c(0xFFECE5FF, 0xFF221D47)
    val peachBg = c(0xFFFFE8DC, 0xFF1E2740); val roseBg = c(0xFFFFE3ED, 0xFF1E2740); val sunBg = c(0xFFFFF2CF, 0xFF1E2740)
    val night = if (dark) listOf(Color(0xFF111735), Color(0xFF241C52)) else listOf(Color(0xFF242A66), Color(0xFF4A3A9A))
}
private val LocalDx = staticCompositionLocalOf { DxP(false) }

@Composable private fun Dt(text: String, size: TextUnit, color: Color? = null, weight: FontWeight = FontWeight.Normal, display: Boolean = false, modifier: Modifier = Modifier) =
    X(text, size, color ?: LocalDx.current.ink, weight, display, modifier, lines = 1)

@Composable private fun DCard(modifier: Modifier = Modifier, bg: Brush? = null, pad: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalDx.current; val sh = RoundedCornerShape(26.dp)
    Column(modifier.shadow(if (p.dark) 0.dp else 6.dp, sh, ambientColor = Color(0x14302060), spotColor = Color(0x14302060)).clip(sh)
        .background(bg ?: Brush.linearGradient(listOf(p.card, p.card))).padding(pad), content = content)
}

@Composable private fun Chip(icon: ImageVector, c: Color, bg: Color, size: Dp = 30.dp) =
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(size * 0.56f), tint = c) }

@Composable
private fun DxScreen(dark: Boolean, tab: Int, kicker: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    val p = DxP(dark)
    CompositionLocalProvider(LocalDx provides p) {
        Box(Modifier.fillMaxSize().background(p.bg)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 42.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Dt(kicker, 14.sp, p.sub, FontWeight.Medium); Dt(title, 27.sp, weight = FontWeight.SemiBold, display = true) }
                    Box(Modifier.size(42.dp).clip(CircleShape).background(p.card), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, null, Modifier.size(21.dp), tint = p.ink) }
                }
                content()
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(Color.Transparent, p.bg))))
            FloatingBar(tab, if (dark) Color(0xFF222B45) else Color.White, if (dark) p.lilacBg else p.lilacBg, p.lilac, p.sub, p.lilac, if (dark) p.bg else Color.White,
                Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
    }
}

// ---- Aujourd'hui --------------------------------------------------------------------------------------------------------------

@Composable
fun DouxHome(dark: Boolean) = DxScreen(dark, 0, "Vendredi 30 octobre", "Bonjour Bad") {
    val p = LocalDx.current
    // la Forme : un soleil sur son arc, plus il est haut, plus on est en forme
    DCard(bg = Brush.linearGradient(listOf(p.mintBg, p.skyBg)), pad = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 18.dp, top = 16.dp, bottom = 16.dp)) {
                Dt("Forme", 15.sp, p.mint, FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.Bottom) { Dt("78", 44.sp, weight = FontWeight.SemiBold, display = true); Dt(" /100", 15.sp, p.sub, modifier = Modifier.padding(bottom = 8.dp)) }
                Dt("Prêt à pousser", 16.sp, weight = FontWeight.Medium)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Charge 64" to p.sky, "Repos 84" to p.lilac).forEach { (t, c) ->
                        Row(Modifier.clip(CircleShape).background(p.card.copy(alpha = if (p.dark) 0.6f else 0.75f)).padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(c)); Dt(" $t", 12.sp, weight = FontWeight.Medium)
                        }
                    }
                }
            }
            Canvas(Modifier.size(128.dp, 128.dp)) {
                val cx = size.width / 2; val base = size.height - 18.dp.toPx(); val r = size.width / 2 - 12.dp.toPx()
                val w = 3.dp.toPx()
                drawArc(p.ink.copy(alpha = 0.12f), 180f, 180f, false, Offset(cx - r, base - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 9f))))
                drawArc(Brush.horizontalGradient(listOf(p.sky, p.mint), cx - r, cx + r), 180f, 180f * 0.78f, false, Offset(cx - r, base - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
                drawLine(p.ink.copy(alpha = 0.18f), Offset(8.dp.toPx(), base), Offset(size.width - 8.dp.toPx(), base), 1.5.dp.toPx(), StrokeCap.Round)
                val a = PI * (1 + 0.78); val s = Offset(cx + r * cos(a).toFloat(), base + r * sin(a).toFloat())
                drawCircle(p.sun.copy(alpha = 0.28f), 17.dp.toPx(), s); drawCircle(p.sun, 11.dp.toPx(), s)
            }
        }
    }
    // la nuit : un vrai ciel, la courbe des phases posée dessus
    DCard(bg = Brush.linearGradient(p.night), pad = 0.dp) {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                listOf(0.08f to 0.2f, 0.22f to 0.1f, 0.38f to 0.28f, 0.52f to 0.12f, 0.6f to 0.34f, 0.7f to 0.08f, 0.9f to 0.42f, 0.95f to 0.15f, 0.3f to 0.45f, 0.46f to 0.5f)
                    .forEachIndexed { i, (x, y) -> drawCircle(Color.White.copy(alpha = if (i % 3 == 0) 0.75f else 0.4f), (if (i % 3 == 0) 1.6f else 1.1f).dp.toPx(), Offset(x * size.width, y * size.height)) }
                val m = Offset(size.width - 34.dp.toPx(), 30.dp.toPx()); val mr = 13.dp.toPx()
                drawPath(Path().apply { op(circlePath(m, mr), circlePath(m + Offset(6.dp.toPx(), -5.dp.toPx()), mr * 0.9f), PathOperation.Difference) }, Color(0xFFFFF1C8))
            }
            Column(Modifier.padding(start = 18.dp, top = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Bedtime, null, Modifier.size(17.dp), tint = Color(0xFFC9B8FF)); Dt("  Sommeil · 84", 15.sp, Color(0xFFC9B8FF), FontWeight.SemiBold) }
                Row(verticalAlignment = Alignment.Bottom) { Dt("7", 30.sp, Color.White, FontWeight.SemiBold, true); Dt(" h ", 15.sp, Color(0xB3FFFFFF), modifier = Modifier.padding(bottom = 4.dp)); Dt("32", 30.sp, Color.White, FontWeight.SemiBold, true) }
            }
            HypnoT(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp).padding(horizontal = 18.dp).padding(bottom = 14.dp),
                Color(0xFFFFE08A), Color(0xFF9EE6FF), Color(0xFFC9B8FF), Color(0xFF8C7BFF), Color(0x33C9B8FF), 2.5.dp)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        @Composable fun Small(icon: ImageVector, c: Color, bg: Color, v: String, u: String, chart: @Composable () -> Unit) =
            DCard(Modifier.weight(1f).height(136.dp), pad = 12.dp) {
                Chip(icon, c, bg); Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) { Dt(v, 21.sp, weight = FontWeight.SemiBold, display = true); Dt(" $u", 12.sp, p.sub, modifier = Modifier.padding(bottom = 3.dp)) }
                Spacer(Modifier.weight(1f)); chart()
            }
        Small(Icons.AutoMirrored.Rounded.DirectionsRun, p.peach, p.peachBg, "6 240", "pas") { StepBarsT(Modifier.fillMaxWidth().height(32.dp), p.peach, p.sun, p.track) }
        Small(Icons.Rounded.Favorite, p.rose, p.roseBg, "54", "bpm") { LineT(heart4.map { it.toFloat() }, Modifier.fillMaxWidth().height(32.dp), p.rose, p.rose, null, p.card, 2.dp) }
        Small(Icons.Rounded.LocalFireDepartment, p.sky, p.skyBg, "927", "kcal") {
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(p.track)) {
                Box(Modifier.fillMaxWidth(1353f / 2280).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(p.mint, p.sky))))
            }
        }
    }
    DCard(pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(120.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Chip(Icons.Rounded.MonitorWeight, p.sky, p.skyBg, 28.dp); Dt("  Poids", 14.sp, p.sub, FontWeight.Medium) }
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) { Dt("81,2", 24.sp, weight = FontWeight.SemiBold, display = true); Dt(" kg", 13.sp, p.sub, modifier = Modifier.padding(bottom = 3.dp)) }
                Dt("−1,4 kg / mois", 12.sp, p.mint, FontWeight.Medium)
            }
            LineT(weight4.map { it.toFloat() }, Modifier.weight(1f).height(64.dp), p.sky, p.lilac, p.sky.copy(alpha = 0.18f), p.card, 2.5.dp)
        }
    }
    DCard(pad = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(Icons.Rounded.FitnessCenter, p.lilac, p.lilacBg, 38.dp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Dt("Jambes aujourd'hui", 16.sp, weight = FontWeight.SemiBold); Dt("55 min · effort 12–14", 13.sp, p.sub) }
            Box(Modifier.size(40.dp).clip(CircleShape).background(p.lilac), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, tint = if (p.dark) p.bg else Color.White) }
        }
    }
}

// ---- Nutrition ---------------------------------------------------------------------------------------------------------------

@Composable
fun DouxFood(dark: Boolean) = DxScreen(dark, 1, "Vendredi 30 octobre", "Nutrition") {
    val p = LocalDx.current
    DCard(bg = Brush.linearGradient(listOf(p.sunBg, p.mintBg)), pad = 14.dp) {
        // la journée sur la course du soleil (6 h → 22 h), repas posés à leur heure
        BoxWithConstraints(Modifier.fillMaxWidth().height(112.dp)) {
            val wpx = maxWidth; val hgt = 112.dp
            val r = wpx / 2 - 22.dp; val base = hgt - 16.dp; val ry = base - 22.dp
            fun at(h: Float): Pair<Dp, Dp> { val a = PI * (1 + (h - 6f) / 16f); return (wpx / 2 + r * cos(a).toFloat()) to (base + ry * sin(a).toFloat()) }
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2; val rr = r.toPx(); val b = base.toPx(); val ryy = ry.toPx()
                val tl = Offset(cx - rr, b - ryy); val sz = Size(2 * rr, 2 * ryy)
                drawArc(p.ink.copy(alpha = 0.14f), 180f, 180f, false, tl, sz, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 8f))))
                drawArc(Brush.horizontalGradient(listOf(p.sun, p.mint), cx - rr, cx + rr), 180f, 180f * 10f / 16f, false, tl, sz, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                drawLine(p.ink.copy(alpha = 0.15f), Offset(0f, b), Offset(size.width, b), 1.dp.toPx())
            }
            listOf(8.17f to meals4[0].third[0].icon, 12.67f to meals4[1].third[0].icon, 16.5f to meals4[2].third[0].icon).forEach { (h, ic) ->
                val (x, y) = at(h)
                Box(Modifier.offset(x - 19.dp, y - 19.dp).size(38.dp).shadow(if (p.dark) 0.dp else 3.dp, CircleShape).clip(CircleShape).background(p.card), contentAlignment = Alignment.Center) { PixelIcon(ic, 24.dp) }
            }
            val (dx, dy) = at(19.5f)
            Box(Modifier.offset(dx - 16.dp, dy - 16.dp).size(32.dp).clip(CircleShape).border(2.dp, p.sub.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = p.sub)
            }
            Dt("6 h", 11.sp, p.sub, modifier = Modifier.align(Alignment.BottomStart)); Dt("22 h", 11.sp, p.sub, modifier = Modifier.align(Alignment.BottomEnd))
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) { Dt("927", 32.sp, weight = FontWeight.SemiBold, display = true); Dt(" kcal restantes", 14.sp, p.sub, modifier = Modifier.padding(bottom = 5.dp)) }
            }
            Dt("1 353 / 2 280", 13.sp, p.sub, modifier = Modifier.padding(bottom = 6.dp))
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Protéines", 98 to 150, p.rose), Triple("Lipides", 52 to 76, p.sun), Triple("Glucides", 141 to 285, p.mint)).forEach { (l, v, c) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(p.card.copy(alpha = if (p.dark) 0.55f else 0.8f)).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) { Dt("${v.first}", 18.sp, weight = FontWeight.SemiBold, display = true); Dt(" / ${v.second} g", 12.sp, p.sub, modifier = Modifier.padding(bottom = 2.dp)) }
                    Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(p.track)) { Box(Modifier.fillMaxWidth(v.first.toFloat() / v.second).fillMaxHeight().clip(CircleShape).background(c)) }
                    Dt(l, 12.sp, p.sub, FontWeight.Medium)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(p.card).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = p.sub); Dt("  Ajouter un aliment", 15.sp, p.sub, modifier = Modifier.weight(1f))
        Chip(Icons.Rounded.QrCodeScanner, p.lilac, p.lilacBg, 36.dp)
    }
    meals4.forEachIndexed { mi, (name, time, foods) ->
        val (c, bg) = listOf(p.sun to p.sunBg, p.mint to p.mintBg, p.lilac to p.lilacBg)[mi]
        DCard(pad = 12.dp) {
            Row(Modifier.padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Dt(name, 17.sp, weight = FontWeight.SemiBold, display = true); Dt("  $time", 13.sp, p.sub, modifier = Modifier.weight(1f))
                Box(Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) { Dt("${foods.sumOf { it.kcal }} kcal", 13.sp, c, FontWeight.SemiBold) }
            }
            foods.forEach { f ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(p.card2), contentAlignment = Alignment.Center) { PixelIcon(f.icon, 26.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Dt(f.name, 15.sp); Dt(f.qty, 12.sp, p.sub) }
                    Dt("${f.kcal}", 15.sp, p.sub, FontWeight.Medium)
                }
            }
        }
    }
}

// ---- Activités ---------------------------------------------------------------------------------------------------------------

@Composable
fun DouxAct(dark: Boolean) = DxScreen(dark, 2, "Semaine 44", "Activités") {
    val p = LocalDx.current
    DCard(bg = Brush.linearGradient(listOf(p.lilacBg, p.skyBg))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Dt("Push Pull Legs", 18.sp, weight = FontWeight.SemiBold, display = true); Dt("Semaine 3 · 2 sur 4", 13.sp, p.sub) }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.sub)
        }
        // le chemin du programme : séances faites, celle du jour, la suivante
        Box(Modifier.padding(top = 12.dp).fillMaxWidth()) {
            Canvas(Modifier.fillMaxWidth().height(80.dp)) {
                val y = size.height / 2; val x0 = size.width / 8; val x1 = size.width * 7 / 8
                drawLine(p.lilac, Offset(x0, y), Offset(size.width * 5 / 8, y), 3.dp.toPx(), StrokeCap.Round)
                drawLine(p.ink.copy(alpha = 0.18f), Offset(size.width * 5 / 8, y), Offset(x1, y), 3.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(1f, 10f)))
            }
            Row(Modifier.fillMaxWidth()) {
                program4.forEach { d ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        val s = if (d.next) 80.dp else 62.dp
                        Box(Modifier.height(80.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(s).clip(CircleShape).background(p.card).then(if (d.next) Modifier.border(3.dp, Brush.linearGradient(listOf(p.lilac, p.sky)), CircleShape) else Modifier)) {
                                BodyT(d, Modifier.fillMaxSize().padding(if (d.next) 8.dp else 6.dp), p.track, if (p.dark) Color(0xFF34405E) else Color(0xFFDCD7EA),
                                    if (d.done) p.sub else p.lilac, if (d.done) p.sub else p.sky, p.card2)
                            }
                            if (d.done) Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 4.dp).size(20.dp).clip(CircleShape).background(p.mint), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = Color.White)
                            }
                        }
                        Dt(d.name, 13.sp, if (d.next) p.ink else p.sub, if (d.next) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Row(Modifier.padding(top = 12.dp).fillMaxWidth().height(52.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(p.lilac, p.sky))),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            val on = if (p.dark) p.bg else Color.White
            Icon(Icons.Rounded.PlayArrow, null, tint = on); Dt(" Commencer Jambes", 16.sp, on, FontWeight.SemiBold); Dt("  · 55 min", 14.sp, on.copy(alpha = 0.8f))
        }
    }
    DCard(pad = 14.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) { Dt("Cette semaine", 14.sp, p.sub, FontWeight.Medium); Row(verticalAlignment = Alignment.Bottom) { Dt("2 h 09", 24.sp, weight = FontWeight.SemiBold, display = true); Dt("  3 activités", 13.sp, p.sub, modifier = Modifier.padding(bottom = 3.dp)) } }
        }
        Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            week7.forEachIndexed { i, (d, m) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(p.card2), contentAlignment = Alignment.BottomCenter) {
                        if (m > 0) Box(Modifier.fillMaxWidth().height((64 * m / 52).dp).clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(p.mint, p.sky))), contentAlignment = Alignment.TopCenter) {
                            Dt("$m", 11.sp, if (p.dark) p.bg else Color.White, FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Dt(d, 12.sp, if (i == 4) p.ink else p.sub, if (i == 4) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DCard(Modifier.weight(1.2f).height(132.dp), bg = Brush.linearGradient(listOf(p.mintBg, p.skyBg)), pad = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) { Chip(Icons.AutoMirrored.Rounded.DirectionsRun, p.mint, p.card, 28.dp); Dt("  Course · hier", 13.sp, p.sub, FontWeight.Medium) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) { Dt("5,2", 26.sp, weight = FontWeight.SemiBold, display = true); Dt(" km", 13.sp, p.sub, modifier = Modifier.padding(bottom = 3.dp)) }
                    Dt("5:31 /km", 12.sp, p.sub)
                }
                RouteT(Modifier.size(78.dp, 64.dp), p.mint, p.sky, p.ink, 2.5.dp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Triple(Icons.Rounded.FitnessCenter, "Pull", "12,3 t") to (p.lilac to p.lilacBg), Triple(Icons.AutoMirrored.Rounded.DirectionsBike, "Vélo", "21 km") to (p.sky to p.skyBg)).forEach { (t, cc) ->
                DCard(Modifier.fillMaxWidth().height(61.dp), pad = 10.dp) {
                    Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        Chip(t.first, cc.first, cc.second, 36.dp); Spacer(Modifier.width(8.dp))
                        Column { Dt(t.third, 17.sp, weight = FontWeight.SemiBold, display = true); Dt(t.second, 12.sp, p.sub) }
                    }
                }
            }
        }
    }
}
