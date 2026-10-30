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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Tour 10 — Score, système complet. Chaque carte existe dans les quatre tailles de la grille de l'app (3 blocs) :
 *   S petit (1 bloc, carré)   : nom, LA valeur, un seul repère visuel.
 *   M moyen (2 blocs)         : nom + contexte, valeur et une ligne d'explication à gauche, le graphique à droite.
 *   W bandeau (toute largeur) : une ligne — pastille, nom et valeur, repère compact à droite.
 *   L grand (toute largeur)   : le détail — valeur, deux ou trois chiffres annexes, graphique complet, légende.
 * Même anatomie partout, mêmes tailles de texte ; dégradés uniquement sur les données.
 */

internal class SP(val dark: Boolean) {
    val bg = if (dark) Color(0xFF08090B) else Color(0xFFF3F4F6)
    val tile = if (dark) Color(0xFF131519) else Color.White
    val tile2 = if (dark) Color(0xFF1D2025) else Color(0xFFF0F1F4)
    val line = if (dark) Color(0x14FFFFFF) else Color(0x12000000)
    val ink = if (dark) Color(0xFFF4F5F7) else Color(0xFF0F1114)
    val sub = if (dark) Color(0xFF8E939B) else Color(0xFF6B7079)
    val faint = if (dark) Color(0xFF2A2E35) else Color(0xFFE3E5E9)
    private fun h(d: Long, l: Long, d2: Long, l2: Long) = if (dark) Color(d) to Color(d2) else Color(l) to Color(l2)
    val forme = h(0xFF3FCF8E, 0xFF0F9F62, 0xFF35B4E0, 0xFF0B8FC7)
    val charge = h(0xFF4A8EF0, 0xFF1F68D6, 0xFF79B4F2, 0xFF4A8FE8)
    val repos = h(0xFF8D7BEF, 0xFF6448D6, 0xFFB794F0, 0xFF9363E0)
    val effort = h(0xFFF2834B, 0xFFE8601F, 0xFFE95A7E, 0xFFD93465)
    val kcal = h(0xFF3EC1B4, 0xFF0C968B, 0xFF4E9EE8, 0xFF1C7FD4)
    val sleep = h(0xFF7470EA, 0xFF4B49D2, 0xFFA98BEE, 0xFF835CE0)
    val heart = h(0xFFEE5A72, 0xFFD62D52, 0xFFF38EA2, 0xFFE8648A)
    val steps = h(0xFFF0BA45, 0xFFD18F00, 0xFFEF9443, 0xFFE2700F)
    val weight = h(0xFF5BA0EC, 0xFF2873D0, 0xFF8CC3EE, 0xFF5AA5E6)
    val prot = if (dark) Color(0xFFEE6E87) else Color(0xFFD9385C)
    val fat = if (dark) Color(0xFFEDC055) else Color(0xFFCC9100)
    val carb = if (dark) Color(0xFF4CC994) else Color(0xFF119A60)
    val good = if (dark) Color(0xFF4CC994) else Color(0xFF0F9F62)
    val onAccent = if (dark) Color(0xFF08090B) else Color.White
    val bodyBase = if (dark) Color(0xFF2A2D34) else Color(0xFFD7DAE0)
    val bodyMuscle = if (dark) Color(0xFF383C45) else Color(0xFFC4C8D0)
    val bodyBone = if (dark) Color(0xFF202228) else Color(0xFFE6E8EC)
}
internal val LocalSP = staticCompositionLocalOf { SP(true) }

enum class Sz(val h: Dp?) { S(120.dp), M(120.dp), W(68.dp), L(null) }

internal fun gr(p: Pair<Color, Color>) = Brush.horizontalGradient(listOf(p.first, p.second))

@Composable internal fun Tq(text: String, size: TextUnit, color: Color? = null, weight: FontWeight = FontWeight.Normal, num: Boolean = false, modifier: Modifier = Modifier, brush: Brush? = null) =
    X(text, size, color ?: LocalSP.current.ink, weight, family = if (num) InterDisplay else Inter, spacing = if (num) (-0.02 * size.value).sp else 0.sp, modifier = modifier, lines = 1, brush = brush)

/** Valeur : chiffre en Inter Display, unité plus petite en gris ; [v2] pour « 7 h 32 ». */
@Composable internal fun V(v: String, unit: String? = null, size: TextUnit = 24.sp, v2: String? = null, unit2: String? = null, color: Color? = null) {
    val p = LocalSP.current; val us = maxOf(12f, size.value * 0.5f).sp; val pad = (size.value * 0.13f).dp
    Row(verticalAlignment = Alignment.Bottom) {
        Tq(v, size, color, FontWeight.SemiBold, true)
        if (unit != null) Tq(" $unit${if (v2 != null) " " else ""}", us, p.sub, FontWeight.Medium, modifier = Modifier.padding(bottom = pad))
        if (v2 != null) Tq(v2, size, color, FontWeight.SemiBold, true)
        if (unit2 != null) Tq(" $unit2", us, p.sub, FontWeight.Medium, modifier = Modifier.padding(bottom = pad))
    }
}

@Composable internal fun Tile10(sz: Sz, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalSP.current; val sh = RoundedCornerShape(if (sz == Sz.W) 20.dp else 22.dp)
    Column(modifier.then(if (sz.h != null) Modifier.height(sz.h) else Modifier).clip(sh).background(p.tile).border(1.dp, p.line, sh)
        .padding(horizontal = if (sz == Sz.S) 12.dp else 14.dp, vertical = if (sz == Sz.W) 0.dp else 12.dp),
        verticalArrangement = if (sz == Sz.W) Arrangement.Center else Arrangement.Top, content = content)
}

@Composable internal fun Head(icon: ImageVector, label: String, c: Color, trailing: String? = null, tc: Color = c) {
    val p = LocalSP.current
    Row(Modifier.height(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(15.dp), tint = c); Spacer(Modifier.width(5.dp))
        Tq(label, 13.sp, p.sub, FontWeight.Medium, modifier = Modifier.weight(1f))
        if (trailing != null) Tq(trailing, 13.sp, tc, FontWeight.SemiBold)
    }
}

@Composable internal fun Small(m: Modifier, icon: ImageVector, label: String, c: Color, trailing: String? = null, value: @Composable () -> Unit, visual: @Composable () -> Unit) =
    Tile10(Sz.S, m) { Head(icon, label, c, trailing); Spacer(Modifier.height(4.dp)); value(); Spacer(Modifier.weight(1f)); visual() }

/** Petit à cadran : la valeur au centre, un mot dans l'ouverture du cadran. */
@Composable private fun SmallDial(m: Modifier, icon: ImageVector, label: String, c: Pair<Color, Color>, f: Float, span: Float = 270f, foot: String? = null, center: @Composable () -> Unit) {
    val p = LocalSP.current
    Tile10(Sz.S, m) {
        Head(icon, label, c.first)
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp), contentAlignment = Alignment.Center) {
            Dial(f, c.first, c.second, p.faint, Modifier.size(76.dp), 8.dp, span)
            Column(horizontalAlignment = Alignment.CenterHorizontally) { center() }
            if (foot != null) Tq(foot, 11.sp, c.first, FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable internal fun Medium(m: Modifier, icon: ImageVector, label: String, c: Color, trailing: String? = null, tc: Color = c, chartW: Dp = 108.dp,
                               left: @Composable ColumnScope.() -> Unit, right: @Composable BoxScope.() -> Unit) =
    Tile10(Sz.M, m) {
        Head(icon, label, c, trailing, tc)
        Row(Modifier.weight(1f).padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, content = left)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(chartW).fillMaxHeight(), contentAlignment = Alignment.BottomCenter, content = right)
        }
    }

@Composable internal fun Wide(m: Modifier, icon: ImageVector, c: Color, label: String, value: @Composable () -> Unit, rightW: Dp = 130.dp, right: @Composable BoxScope.() -> Unit) {
    val p = LocalSP.current
    Tile10(Sz.W, m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(p.tile2), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(21.dp), tint = c) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Tq(label, 12.sp, p.sub, FontWeight.Medium); value() }
            Box(Modifier.width(rightW), contentAlignment = Alignment.CenterEnd, content = right)
        }
    }
}

@Composable private fun Large(m: Modifier, icon: ImageVector, label: String, c: Color, trailing: String? = null, tc: Color = c, content: @Composable ColumnScope.() -> Unit) =
    Tile10(Sz.L, m) { Head(icon, label, c, trailing, tc); Spacer(Modifier.height(8.dp)); content() }

/** Petite colonne « libellé / valeur » des grandes cartes. */
@Composable private fun Stat(label: String, value: String, modifier: Modifier = Modifier, c: Color? = null) {
    val p = LocalSP.current
    Column(modifier) { Tq(label, 12.sp, p.sub); Tq(value, 16.sp, c, FontWeight.SemiBold, true) }
}

@Composable private fun Bar(f: Float, c: Pair<Color, Color>, modifier: Modifier, h: Dp = 8.dp) {
    val p = LocalSP.current
    Box(modifier.height(h).clip(CircleShape).background(p.faint)) { if (f > 0f) Box(Modifier.fillMaxWidth(f.coerceIn(0.04f, 1f)).fillMaxHeight().clip(CircleShape).background(gr(c))) }
}

@Composable private fun Delta(text: String, up: Boolean, good: Boolean) {
    val p = LocalSP.current; val c = if (good) p.good else p.heart.first
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(c.copy(alpha = if (p.dark) 0.16f else 0.12f)), contentAlignment = Alignment.Center) {
            Icon(if (up) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null, Modifier.size(12.dp), tint = c)
        }
        Tq(" $text", 12.sp, c, FontWeight.SemiBold)
    }
}

// ---- données d'exemple propres au tour 10 ----------------------------------------------------------------------------------

/** Pouls de 0 h à 16 h, toutes les 30 min. */
private val hrDay = listOf(56, 54, 52, 51, 50, 49, 48, 47, 48, 49, 50, 52, 53, 55, 62, 70, 74, 72, 68, 66, 64, 70, 78, 72, 68, 66, 64, 63, 66, 70, 74, 72, 76)
/** VFC des 14 dernières nuits (ms) et plage habituelle. */
private val hrv14 = listOf(58, 61, 57, 63, 60, 55, 59, 62, 64, 58, 60, 63, 61, 62)
private const val HRV_LO = 55f; private const val HRV_HI = 65f
private val splits = listOf("5:38" to 338, "5:29" to 329, "5:31" to 331, "5:27" to 327, "5:24" to 324)
/** kcal par jour de la semaine, réparties P / L / G (0 = pas saisi). */
private val macroWeek = listOf(Triple(620, 640, 960), Triple(580, 700, 1000), Triple(560, 610, 880), Triple(600, 690, 980), Triple(392, 468, 564), Triple(0, 0, 0), Triple(0, 0, 0))

// ---- les familles ------------------------------------------------------------------------------------------------------------

@Composable fun WForme(sz: Sz, m: Modifier = Modifier) {
    val p = LocalSP.current
    when (sz) {
        Sz.S -> SmallDial(m, Icons.Rounded.Speed, "Forme", p.forme, 0.78f, foot = "Pousser") { Tq("78", 26.sp, weight = FontWeight.SemiBold, num = true) }
        Sz.M -> Medium(m, Icons.Rounded.Speed, "Forme", p.forme.first, "Prêt à pousser", chartW = 118.dp, left = {
            Box(contentAlignment = Alignment.Center) { Dial(0.78f, p.forme.first, p.forme.second, p.faint, Modifier.size(78.dp), 8.dp); Tq("78", 26.sp, weight = FontWeight.SemiBold, num = true) }
        }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Triple("Charge", 64, p.charge), Triple("Repos", 84, p.repos)).forEach { (l, v, c) ->
                    Column {
                        Row { Tq(l, 12.sp, p.sub, modifier = Modifier.weight(1f)); Tq("$v", 13.sp, weight = FontWeight.SemiBold) }
                        Bar(v / 100f, c, Modifier.fillMaxWidth().padding(top = 3.dp), 6.dp)
                    }
                }
            }
        }
        Sz.W -> Wide(m, Icons.Rounded.Speed, p.forme.first, "Forme", { Row(verticalAlignment = Alignment.Bottom) { Tq("78", 20.sp, weight = FontWeight.SemiBold, num = true); Tq("  Prêt à pousser", 14.sp, p.forme.first, FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp)) } }, 96.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(64 to p.charge, 84 to p.repos).forEach { (v, c) ->
                    Box(contentAlignment = Alignment.Center) { Dial(v / 100f, c.first, c.second, p.faint, Modifier.size(42.dp), 4.dp); Tq("$v", 13.sp, weight = FontWeight.SemiBold, num = true) }
                }
            }
        }
        Sz.L -> Large(m, Icons.Rounded.Speed, "Forme du jour", p.forme.first, "7:20", p.sub) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                @Composable fun D(v: Int, label: String, c: Pair<Color, Color>, size: Dp, stroke: Dp, big: Boolean) =
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            Dial(v / 100f, c.first, c.second, p.faint, Modifier.size(size), stroke)
                            Tq("$v", if (big) 38.sp else 22.sp, weight = FontWeight.SemiBold, num = true)
                        }
                        Tq(label, 13.sp, if (big) p.ink else p.sub, if (big) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.offset(y = (-6).dp))
                    }
                D(64, "Charge", p.charge, 80.dp, 7.dp, false); D(78, "Forme", p.forme, 124.dp, 11.dp, true); D(84, "Repos", p.repos, 80.dp, 7.dp, false)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(14.dp)).background(p.tile2).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Tq("Prêt à pousser", 15.sp, weight = FontWeight.SemiBold, brush = gr(p.forme), modifier = Modifier.weight(1f))
                Tq("Effort conseillé ", 13.sp, p.sub); Tq("12–14", 14.sp, p.effort.first, FontWeight.SemiBold)
            }
        }
    }
}

@Composable fun WSommeil(sz: Sz, m: Modifier = Modifier) {
    val p = LocalSP.current
    val phases = listOf(p.sleep.first, p.sleep.second, p.charge.second, p.heart.second)
    @Composable fun Hyp(mod: Modifier, area: Boolean) = HypnoT(mod, p.heart.second, p.charge.second, p.sleep.second, p.sleep.first, if (area) p.sleep.first.copy(alpha = 0.22f) else null, 2.dp)
    when (sz) {
        Sz.S -> Small(m, Icons.Rounded.Bedtime, "Sommeil", p.sleep.second, "84", { V("7", "h", 24.sp, "32") }) {
            Column { PhaseBarT(Modifier.fillMaxWidth().height(8.dp), phases); Spacer(Modifier.height(5.dp)); Tq("23:48 → 7:20", 11.sp, p.sub) }
        }
        Sz.M -> Medium(m, Icons.Rounded.Bedtime, "Sommeil", p.sleep.second, "Score 84", left = {
            V("7", "h", 26.sp, "32"); Tq("23:48 → 7:20", 12.sp, p.sub)
        }) { Hyp(Modifier.fillMaxWidth().height(58.dp), true) }
        Sz.W -> Wide(m, Icons.Rounded.Bedtime, p.sleep.second, "Sommeil · score 84", { V("7", "h", 20.sp, "32", "min") }) {
            Column(horizontalAlignment = Alignment.End) { PhaseBarT(Modifier.width(124.dp).height(8.dp), phases); Spacer(Modifier.height(5.dp)); Tq("23:48 → 7:20", 11.sp, p.sub) }
        }
        Sz.L -> Large(m, Icons.Rounded.Bedtime, "Sommeil", p.sleep.second, "Score 84") {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { V("7", "h", 30.sp, "32", "min") }
                Stat("Dette", "−28 min", Modifier.width(72.dp)); Stat("Régularité", "± 18 min", Modifier.width(84.dp))
            }
            Spacer(Modifier.height(10.dp))
            Hyp(Modifier.fillMaxWidth().height(64.dp), true)
            Row(Modifier.padding(top = 2.dp)) { Tq("23:48", 11.sp, p.sub); Spacer(Modifier.weight(1f)); Tq("7:20", 11.sp, p.sub) }
            Spacer(Modifier.height(8.dp))
            PhaseBarT(Modifier.fillMaxWidth().height(8.dp), phases)
            Spacer(Modifier.height(8.dp))
            listOf(listOf("Profond" to "19 %", "Léger" to "50 %"), listOf("Paradoxal" to "28 %", "Éveil" to "3 %")).forEachIndexed { r, row ->
                Row(Modifier.padding(top = if (r == 0) 0.dp else 4.dp)) {
                    row.forEachIndexed { k, (l, v) ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(phases[r * 2 + k])); Tq("  $l", 13.sp, p.sub, modifier = Modifier.weight(1f)); Tq(v, 13.sp, weight = FontWeight.SemiBold)
                            if (k == 0) Spacer(Modifier.width(22.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun MealBar(modifier: Modifier) {
    val p = LocalSP.current
    Row(modifier.height(10.dp).clip(CircleShape).background(p.faint), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(435 to p.steps.first, 676 to p.kcal.first, 241 to p.repos.first).forEach { (k, c) -> Box(Modifier.weight(k.toFloat()).fillMaxHeight().background(c)) }
        Box(Modifier.weight(927f).fillMaxHeight())
    }
}

@Composable fun WCalories(sz: Sz, m: Modifier = Modifier) {
    val p = LocalSP.current; val f = 1353f / 2280
    when (sz) {
        Sz.S -> SmallDial(m, Icons.Rounded.LocalFireDepartment, "Calories", p.kcal, f, 360f) {
            Tq("927", 22.sp, weight = FontWeight.SemiBold, num = true); Tq("restantes", 10.sp, p.sub)
        }
        Sz.M -> Medium(m, Icons.Rounded.LocalFireDepartment, "Calories", p.kcal.first, "1 353 / 2 280", p.sub, left = {
            V("927", null, 26.sp, color = p.kcal.first); Tq("restantes", 12.sp, p.sub)
        }) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(Triple("P", 98 to 150, p.prot), Triple("L", 52 to 76, p.fat), Triple("G", 141 to 285, p.carb)).forEach { (n, v, c) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tq(n, 12.sp, c, FontWeight.SemiBold, modifier = Modifier.width(14.dp)); Bar(v.first.toFloat() / v.second, c to c, Modifier.weight(1f), 6.dp)
                        Tq("${v.first}", 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.width(32.dp).padding(start = 6.dp))
                    }
                }
            }
        }
        Sz.W -> Wide(m, Icons.Rounded.LocalFireDepartment, p.kcal.first, "Calories restantes", { V("927", "/ 2 280 kcal", 20.sp) }, 112.dp) {
            Column(horizontalAlignment = Alignment.End) { MealBar(Modifier.width(112.dp)); Spacer(Modifier.height(5.dp)); Tq("1 353 mangées", 11.sp, p.sub) }
        }
        Sz.L -> Large(m, Icons.Rounded.LocalFireDepartment, "Calories", p.kcal.first, "927 restantes") {
            V("1 353", "/ 2 280 kcal", 30.sp)
            Spacer(Modifier.height(10.dp)); MealBar(Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
            Row {
                listOf(Triple("Petit-déj.", 435, p.steps.first), Triple("Déjeuner", 676, p.kcal.first), Triple("Collation", 241, p.repos.first)).forEach { (l, k, c) ->
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Tq("  $l", 12.sp, p.sub) }
                        Tq("$k", 16.sp, weight = FontWeight.SemiBold, num = true, modifier = Modifier.padding(start = 14.dp))
                    }
                }
            }
            Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(p.line))
            MacroRings()
        }
    }
}

@Composable private fun MacroRings() {
    val p = LocalSP.current
    Row {
        listOf(Triple("Protéines", 98 to 150, p.prot), Triple("Lipides", 52 to 76, p.fat), Triple("Glucides", 141 to 285, p.carb)).forEach { (l, v, c) ->
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    Dial(v.first.toFloat() / v.second, c, c, p.faint, Modifier.size(40.dp), 5.dp, 360f)
                    Tq("${v.first * 100 / v.second}", 11.sp, weight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Column { Tq(l, 12.sp, p.sub); Row(verticalAlignment = Alignment.Bottom) { Tq("${v.first}", 15.sp, weight = FontWeight.SemiBold, num = true); Tq(" g", 11.sp, p.sub, modifier = Modifier.padding(bottom = 1.dp)) } }
            }
        }
    }
}

@Composable fun WMacros(sz: Sz, m: Modifier = Modifier) {
    val p = LocalSP.current
    val all = listOf(Triple("P", 98 to 150, p.prot), Triple("L", 52 to 76, p.fat), Triple("G", 141 to 285, p.carb))
    when (sz) {
        // petit : la protéine seule, la macro qu'on surveille
        Sz.S -> SmallDial(m, Icons.Rounded.DonutLarge, "Protéines", p.prot to p.prot, 98f / 150, 360f) {
            Tq("98", 22.sp, weight = FontWeight.SemiBold, num = true); Tq("/ 150 g", 10.sp, p.sub)
        }
        Sz.M -> Medium(m, Icons.Rounded.DonutLarge, "Macros", p.prot, "aujourd'hui", p.sub, chartW = 0.dp, left = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                all.forEach { (n, v, c) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) { Dial(v.first.toFloat() / v.second, c, c, p.faint, Modifier.size(50.dp), 6.dp, 360f); Tq(n, 13.sp, c, FontWeight.Bold) }
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) { Tq("${v.first}", 15.sp, weight = FontWeight.SemiBold, num = true); Tq(" g", 11.sp, p.sub, modifier = Modifier.padding(bottom = 1.dp)) }
                    }
                }
            }
        }) {}
        Sz.W -> Wide(m, Icons.Rounded.DonutLarge, p.prot, "Macros", { Row(verticalAlignment = Alignment.Bottom) { Tq("98", 20.sp, weight = FontWeight.SemiBold, num = true); Tq(" / 150 g de protéines", 12.sp, p.sub, modifier = Modifier.padding(bottom = 2.dp)) } }, 104.dp) {
            Column(Modifier.width(104.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { all.forEach { (_, v, c) -> Bar(v.first.toFloat() / v.second, c to c, Modifier.fillMaxWidth(), 5.dp) } }
        }
        Sz.L -> Large(m, Icons.Rounded.DonutLarge, "Macros de la semaine", p.prot, "lun. → ven.", p.sub) {
            MacroRings()
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                val max = macroWeek.maxOf { it.first + it.second + it.third }.toFloat()
                macroWeek.forEachIndexed { i, (pr, fa, ca) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        val t = pr + fa + ca
                        Box(Modifier.fillMaxWidth().height(66.dp), contentAlignment = Alignment.BottomCenter) {
                            if (t == 0) Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(p.faint))
                            else Column(Modifier.fillMaxWidth().height((66 * t / max).dp).clip(RoundedCornerShape(6.dp)), verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                                Box(Modifier.fillMaxWidth().weight(ca.toFloat()).background(p.carb)); Box(Modifier.fillMaxWidth().weight(fa.toFloat()).background(p.fat)); Box(Modifier.fillMaxWidth().weight(pr.toFloat()).background(p.prot))
                            }
                        }
                        Tq("LMMJVSD"[i].toString(), 11.sp, if (i == 4) p.ink else p.sub, FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable fun WRepas(sz: Sz, m: Modifier = Modifier) {
    val p = LocalSP.current
    val cols = listOf(p.steps.first, p.kcal.first, p.repos.first)
    when (sz) {
        Sz.S -> Small(m, Icons.Rounded.Restaurant, "Repas", p.kcal.first, null, { V("1 353", "kcal", 22.sp) }) {
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                meals4.forEach { (_, _, foods) -> Box(Modifier.size(30.dp).clip(CircleShape).background(p.tile2).border(2.dp, p.tile, CircleShape), contentAlignment = Alignment.Center) { PixelIcon(foods[0].icon, 20.dp) } }
            }
        }
        Sz.M -> Medium(m, Icons.Rounded.Restaurant, "Repas du jour", p.kcal.first, "1 353 kcal", p.sub, chartW = 0.dp, left = {
            meals4.forEachIndexed { i, (n, t, foods) ->
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tq(t, 12.sp, p.sub, modifier = Modifier.width(38.dp)); Box(Modifier.size(7.dp).clip(CircleShape).background(cols[i]))
                    Tq("  $n", 14.sp, modifier = Modifier.weight(1f)); Tq("${foods.sumOf { it.kcal }}", 13.sp, weight = FontWeight.SemiBold)
                }
            }
        }) {}
        Sz.W -> Wide(m, Icons.Rounded.Restaurant, p.kcal.first, "3 repas", { V("1 353", "kcal", 20.sp) }, 150.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                meals4.flatMap { it.third }.take(6).forEach { f -> Box(Modifier.size(23.dp).clip(CircleShape).background(p.tile2), contentAlignment = Alignment.Center) { PixelIcon(f.icon, 16.dp) } }
            }
        }
        Sz.L -> Large(m, Icons.Rounded.Restaurant, "Repas du jour", p.kcal.first, "1 353 kcal", p.sub) {
            // le fil de la journée : l'heure, un point sur la ligne, le repas et ce qu'il contient
            meals4.forEachIndexed { i, (n, t, foods) ->
                Row(Modifier.height(54.dp)) {
                    Column(Modifier.width(44.dp)) { Tq(t, 13.sp, weight = FontWeight.SemiBold, num = true); Tq("${foods.sumOf { it.kcal }}", 11.sp, p.sub) }
                    Box(Modifier.width(14.dp).fillMaxHeight()) {
                        Box(Modifier.align(Alignment.TopCenter).padding(top = 14.dp).width(2.dp).fillMaxHeight().background(p.faint))
                        Box(Modifier.align(Alignment.TopCenter).padding(top = 4.dp).size(10.dp).clip(CircleShape).background(cols[i]))
                    }
                    Column(Modifier.padding(start = 8.dp)) {
                        Tq(n, 15.sp, weight = FontWeight.SemiBold)
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            foods.take(3).forEach { f -> Row(Modifier.clip(CircleShape).background(p.tile2).padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                PixelIcon(f.icon, 16.dp); Tq(" ${f.name.substringBefore(' ')}", 12.sp) } }
                            if (foods.size > 3) Tq("+${foods.size - 3}", 12.sp, p.sub, FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterVertically))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tq("19:30", 13.sp, p.sub, modifier = Modifier.width(44.dp))
                Box(Modifier.width(14.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(10.dp).clip(CircleShape).border(2.dp, p.sub, CircleShape)) }
                Row(Modifier.padding(start = 8.dp).clip(CircleShape).border(1.dp, p.faint, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(14.dp), tint = p.sub); Tq(" Dîner", 13.sp, p.sub, FontWeight.Medium)
                }
            }
        }
    }
}

@Composable fun WPas(sz: Sz, m: Modifier = Modifier) {
    val p = LocalSP.current
    when (sz) {
        // petit : l'objectif du jour, rien d'autre
        Sz.S -> SmallDial(m, Icons.AutoMirrored.Rounded.DirectionsRun, "Pas", p.steps, 0.78f, 360f) {
            Tq("6 240", 16.sp, weight = FontWeight.SemiBold, num = true); Tq("78 %", 11.sp, p.sub)
        }
        // moyen : aujourd'hui et la semaine face à l'objectif
        Sz.M -> Medium(m, Icons.AutoMirrored.Rounded.DirectionsRun, "Pas", p.steps.first, "78 %", left = {
            V("6 240", null, 26.sp); Tq("sur 8 000", 12.sp, p.sub)
        }) { GoalBars(steps7, 8000, p.steps, Modifier.fillMaxWidth().height(56.dp)) }
        Sz.W -> Wide(m, Icons.AutoMirrored.Rounded.DirectionsRun, p.steps.first, "Pas", { V("6 240", "/ 8 000", 20.sp) }, 112.dp) {
            Column(horizontalAlignment = Alignment.End) { Bar(0.78f, p.steps, Modifier.width(112.dp)); Spacer(Modifier.height(5.dp)); Tq("78 %", 12.sp, p.steps.first, FontWeight.SemiBold) }
        }
        Sz.L -> Large(m, Icons.AutoMirrored.Rounded.DirectionsRun, "Pas", p.steps.first, "78 % de 8 000") {
            Row(verticalAlignment = Alignment.Bottom) { Box(Modifier.weight(1f)) { V("6 240", "pas", 30.sp) }; Stat("Distance", "4,6 km", Modifier.width(78.dp)); Stat("Actives", "312 kcal", Modifier.width(76.dp)) }
            Spacer(Modifier.height(12.dp))
            StepBarsT(Modifier.fillMaxWidth().height(76.dp), p.steps.first, p.steps.second, p.faint)
            Row(Modifier.padding(top = 4.dp)) { listOf("0 h", "6 h", "12 h", "18 h").forEach { Tq(it, 11.sp, p.sub, modifier = Modifier.weight(1f)) } }
        }
    }
}

@Composable fun WCoeur(sz: Sz, m: Modifier = Modifier) {
    val p = LocalSP.current
    when (sz) {
        // petit : la valeur et où elle tombe dans la plage habituelle
        Sz.S -> Small(m, Icons.Rounded.Favorite, "Repos", p.heart.first, null, { V("54", "bpm", 22.sp) }) {
            Column { Delta("2 vs moy.", up = false, good = true); Spacer(Modifier.height(6.dp)); RangeBar(54f, 53f, 58f, 48f, 64f, p.heart.first, Modifier.fillMaxWidth()) }
        }
        // moyen : les 14 derniers jours sur la plage habituelle
        Sz.M -> Medium(m, Icons.Rounded.Favorite, "Cœur au repos", p.heart.first, left = {
            V("54", "bpm", 26.sp); Delta("2 vs moy.", up = false, good = true)
        }) { BandLine(rhr14, 53f, 58f, p.heart, Modifier.fillMaxWidth().height(58.dp), false) }
        Sz.W -> Wide(m, Icons.Rounded.Favorite, p.heart.first, "Cœur au repos", { V("54", "bpm", 20.sp) }, 120.dp) {
            Column(horizontalAlignment = Alignment.End) { RangeBar(54f, 53f, 58f, 48f, 64f, p.heart.first, Modifier.width(112.dp)); Spacer(Modifier.height(4.dp)); Tq("habituel 53–58", 11.sp, p.sub) }
        }
        Sz.L -> Large(m, Icons.Rounded.Favorite, "Rythme cardiaque", p.heart.first, "aujourd'hui", p.sub) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) { V("54", "bpm au repos", 30.sp) }
                Stat("Min", "47", Modifier.width(52.dp)); Stat("Max", "78", Modifier.width(52.dp))
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(86.dp)) {
                LineT(hrDay.map { it.toFloat() }, Modifier.fillMaxWidth(0.68f).fillMaxHeight(), p.heart.first, p.heart.second, p.heart.first.copy(alpha = 0.2f), p.tile, 2.dp)
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(p.faint, Offset(size.width * 0.68f, size.height - 1), Offset(size.width, size.height - 1), 1.5.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(4f, 8f)))
                }
            }
            Row(Modifier.padding(top = 4.dp)) { listOf("0 h", "6 h", "12 h", "18 h").forEach { Tq(it, 11.sp, p.sub, modifier = Modifier.weight(1f)) } }
        }
    }
}

/** Pouls au repos des 14 derniers jours et sa plage habituelle ; pas des 7 derniers jours et l'objectif. */
private val rhr14 = listOf(57, 56, 57, 55, 56, 58, 55, 56, 55, 54, 56, 55, 55, 54)
private val steps7 = listOf(8420, 9120, 5310, 7980, 10240, 6890, 6240)

/** Où tombe la valeur du jour dans sa plage habituelle : piste, plage en relief, point. */
@Composable private fun RangeBar(v: Float, lo: Float, hi: Float, min: Float, max: Float, c: Color, modifier: Modifier) {
    val p = LocalSP.current
    Canvas(modifier.height(12.dp)) {
        fun x(t: Float) = size.width * ((t - min) / (max - min)).coerceIn(0f, 1f)
        val y = size.height / 2
        drawRoundRect(p.faint, Offset(0f, y - 2.dp.toPx()), Size(size.width, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
        drawRoundRect(if (p.dark) Color(0xFF4A5059) else c.copy(alpha = 0.35f), Offset(x(lo), y - 3.dp.toPx()), Size(x(hi) - x(lo), 6.dp.toPx()), CornerRadius(3.dp.toPx()))
        drawCircle(p.tile, 6.dp.toPx(), Offset(x(v), y)); drawCircle(c, 4.5.dp.toPx(), Offset(x(v), y))
    }
}

/** Courbe posée sur sa plage habituelle (bande). */
@Composable private fun BandLine(values: List<Int>, lo: Float, hi: Float, c: Pair<Color, Color>, modifier: Modifier, dots: Boolean) {
    val p = LocalSP.current
    Canvas(modifier) {
        val ylo = minOf(values.min().toFloat(), lo) - 2f; val yhi = maxOf(values.max().toFloat(), hi) + 2f
        fun y(x: Float) = size.height * (yhi - x) / (yhi - ylo)
        drawRoundRect(if (p.dark) p.tile2 else c.first.copy(alpha = 0.08f), Offset(0f, y(hi)), Size(size.width, y(lo) - y(hi)), CornerRadius(6.dp.toPx()))
        val pts = values.mapIndexed { i, x -> Offset(size.width * (i + 0.5f) / values.size, y(x.toFloat())) }
        drawPath(smooth(pts), Brush.horizontalGradient(listOf(c.second, c.first)), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        if (dots) pts.forEach { drawCircle(p.tile, 3.5.dp.toPx(), it); drawCircle(c.first, 2.5.dp.toPx(), it) }
        else drawCircle(c.first, 4.dp.toPx(), pts.last())
    }
}

/** 7 jours en barres face à l'objectif (pointillés) ; le jour même plus vif. */
@Composable private fun GoalBars(values: List<Int>, goal: Int, c: Pair<Color, Color>, modifier: Modifier) {
    val p = LocalSP.current
    Canvas(modifier) {
        val max = maxOf(values.max(), goal) * 1.08f; val n = values.size; val slot = size.width / n; val bw = minOf(slot * 0.56f, 10.dp.toPx())
        values.forEachIndexed { i, v ->
            val h = size.height * v / max; val x = i * slot + (slot - bw) / 2
            drawRoundRect(if (v >= goal) Brush.verticalGradient(listOf(c.second, c.first)) else Brush.verticalGradient(listOf(if (p.dark) Color(0xFF3A3F47) else p.faint, if (p.dark) Color(0xFF3A3F47) else p.faint)),
                Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 2), alpha = if (i == n - 1) 1f else 0.9f)
        }
        val gy = size.height * (1 - goal / max)
        drawLine(p.sub, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
    }
}

/** VFC : nuits sur la plage habituelle (bande) ; [n] dernières nuits. */
@Composable private fun HrvBand(modifier: Modifier, n: Int, dots: Boolean) {
    val p = LocalSP.current
    Canvas(modifier) {
        val v = hrv14.takeLast(n); val lo = 52f; val hi = 67f
        fun y(x: Float) = size.height * (hi - x) / (hi - lo)
        drawRoundRect(p.repos.first.copy(alpha = if (p.dark) 0.14f else 0.08f), Offset(0f, y(HRV_HI)), Size(size.width, y(HRV_LO) - y(HRV_HI)), CornerRadius(6.dp.toPx()))
        val pts = v.mapIndexed { i, x -> Offset(size.width * (i + 0.5f) / v.size, y(x.toFloat())) }
        drawPath(smooth(pts), Brush.horizontalGradient(listOf(p.repos.second, p.repos.first)), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        if (dots) pts.forEach { drawCircle(p.tile, 3.5.dp.toPx(), it); drawCircle(p.repos.first, 2.5.dp.toPx(), it) }
        else drawCircle(p.repos.first, 4.dp.toPx(), pts.last())
    }
}

@Composable fun WVfc(sz: Sz, m: Modifier = Modifier) {
    val p = LocalSP.current
    when (sz) {
        Sz.S -> Small(m, Icons.Rounded.MonitorHeart, "VFC", p.repos.first, null, { V("62", "ms", 22.sp) }) {
            Column { Delta("5 vs moy.", up = true, good = true); Spacer(Modifier.height(6.dp)); RangeBar(62f, HRV_LO, HRV_HI, 45f, 75f, p.repos.first, Modifier.fillMaxWidth()) }
        }
        Sz.M -> Medium(m, Icons.Rounded.MonitorHeart, "VFC nocturne", p.repos.first, "Normale", p.good, left = {
            V("62", "ms", 26.sp); Delta("5 vs moy.", up = true, good = true)
        }) { HrvBand(Modifier.fillMaxWidth().height(58.dp), 7, true) }
        Sz.W -> Wide(m, Icons.Rounded.MonitorHeart, p.repos.first, "VFC nocturne", { V("62", "ms", 20.sp) }, 120.dp) {
            Column(horizontalAlignment = Alignment.End) {
                Canvas(Modifier.width(112.dp).height(12.dp)) {
                    val lo = 45f; val hi = 75f; fun x(v: Float) = size.width * (v - lo) / (hi - lo)
                    drawRoundRect(p.faint, Offset(0f, size.height / 2 - 2.dp.toPx()), Size(size.width, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                    drawRoundRect(p.repos.first.copy(alpha = 0.45f), Offset(x(HRV_LO), size.height / 2 - 3.dp.toPx()), Size(x(HRV_HI) - x(HRV_LO), 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                    drawCircle(p.tile, 6.dp.toPx(), Offset(x(62f), size.height / 2)); drawCircle(p.repos.first, 4.5.dp.toPx(), Offset(x(62f), size.height / 2))
                }
                Spacer(Modifier.height(4.dp)); Tq("Normale · 55–65", 11.sp, p.sub)
            }
        }
        Sz.L -> Large(m, Icons.Rounded.MonitorHeart, "Variabilité cardiaque", p.repos.first, "Normale", p.good) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) { V("62", "ms cette nuit", 30.sp) }
                Stat("Moyenne", "60 ms", Modifier.width(76.dp)); Stat("Plage", "55–65", Modifier.width(60.dp))
            }
            Spacer(Modifier.height(12.dp))
            HrvBand(Modifier.fillMaxWidth().height(82.dp), 14, true)
            Row(Modifier.padding(top = 4.dp)) { Tq("il y a 14 nuits", 11.sp, p.sub, modifier = Modifier.weight(1f)); Tq("cette nuit", 11.sp, p.sub) }
        }
    }
}

@Composable fun WPoids(sz: Sz, m: Modifier = Modifier) {
    val p = LocalSP.current; val wv = weight4.map { it.toFloat() }
    when (sz) {
        // petit : la tendance (pas la pesée du jour) et son sens sur 4 semaines
        Sz.S -> Small(m, Icons.Rounded.MonitorWeight, "Poids", p.weight.first, null, { V("81,2", "kg", 22.sp) }) {
            Column { LineT(wv, Modifier.fillMaxWidth().height(22.dp), p.weight.first, p.weight.second, null, p.tile, 2.dp); Spacer(Modifier.height(4.dp)); Delta("1,4 kg", up = false, good = true) }
        }
        Sz.M -> Medium(m, Icons.Rounded.MonitorWeight, "Poids", p.weight.first, left = {
            V("81,2", "kg", 26.sp); Delta("1,4 kg / 4 sem.", up = false, good = true)
        }) { LineT(wv, Modifier.fillMaxWidth().height(56.dp), p.weight.first, p.weight.second, p.weight.first.copy(alpha = 0.18f), p.tile) }
        Sz.W -> Wide(m, Icons.Rounded.MonitorWeight, p.weight.first, "Poids · tendance", { V("81,2", "kg", 20.sp) }, 120.dp) {
            Column(horizontalAlignment = Alignment.End) { LineT(wv, Modifier.width(110.dp).height(26.dp), p.weight.first, p.weight.second, null, p.tile, 2.dp); Delta("1,4 kg / 4 sem.", up = false, good = true) }
        }
        Sz.L -> Large(m, Icons.Rounded.MonitorWeight, "Tendance du poids", p.weight.first, "4 semaines", p.sub) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) { V("81,2", "kg", 30.sp); Delta("1,4 kg en 4 sem.", up = false, good = true) }
                Stat("Rythme", "−0,35/sem", Modifier.width(88.dp)); Stat("Objectif", "78 kg", Modifier.width(64.dp))
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(86.dp)) {
                // pesées brutes en points, tendance lissée par-dessus
                Canvas(Modifier.fillMaxSize()) {
                    val raw = listOf(82.9, 82.2, 82.8, 81.9, 82.2, 81.7, 81.9, 81.3, 81.8, 81.2, 81.6, 81.0)
                    val lo = 80.8f; val hi = 83.1f; val pad = 5.dp.toPx()
                    fun pt(i: Int, v: Float) = Offset(pad + (size.width - 2 * pad) * i / (wv.size - 1), pad + (size.height - 2 * pad) * (hi - v) / (hi - lo))
                    raw.forEachIndexed { i, v -> drawCircle(p.weight.first.copy(alpha = 0.35f), 3.dp.toPx(), pt(i, v.toFloat())) }
                    val pts = wv.mapIndexed { i, v -> pt(i, v) }
                    drawPath(smooth(pts).apply { lineTo(pts.last().x, size.height); lineTo(pts[0].x, size.height); close() }, Brush.verticalGradient(listOf(p.weight.first.copy(alpha = 0.16f), Color.Transparent)))
                    drawPath(smooth(pts), Brush.horizontalGradient(listOf(p.weight.second, p.weight.first)), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(p.weight.first, 4.5.dp.toPx(), pts.last()); drawCircle(p.tile, 2.dp.toPx(), pts.last())
                }
            }
        }
    }
}

/** Effort (0–21) : zone conseillée en relief sur le cadran. */
@Composable private fun EffortDial(size: Dp, stroke: Dp, modifier: Modifier = Modifier) {
    val p = LocalSP.current
    Canvas(modifier.size(size)) {
        val w = stroke.toPx(); val r = this.size.minDimension / 2 - w / 2; val from = 135f; val span = 270f
        arcT(center, r, w, 9.4f / 21, p.effort.first, p.effort.second, p.faint, from, span)
        val tl = Offset(center.x - r - w * 0.9f, center.y - r - w * 0.9f); val sz = Size(2 * (r + w * 0.9f), 2 * (r + w * 0.9f))
        drawArc(p.effort.second, from + span * 12f / 21, span * 2f / 21, false, tl, sz, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable private fun EffortBar(modifier: Modifier) {
    val p = LocalSP.current
    Box(modifier.height(10.dp)) {
        Box(Modifier.fillMaxWidth().height(8.dp).align(Alignment.Center).clip(CircleShape).background(p.faint)) { Box(Modifier.fillMaxWidth(9.4f / 21).fillMaxHeight().clip(CircleShape).background(gr(p.effort))) }
        Box(Modifier.fillMaxWidth(14f / 21).fillMaxHeight()) { Box(Modifier.fillMaxWidth(2f / 14).fillMaxHeight().align(Alignment.CenterEnd).border(2.dp, p.effort.second, CircleShape)) }
    }
}

@Composable fun WEffort(sz: Sz, m: Modifier = Modifier) {
    val p = LocalSP.current
    when (sz) {
        Sz.S -> Small(m, Icons.Rounded.Bolt, "Effort", p.effort.first, null, { V("9,4", "/ 21", 22.sp) }) {
            Column { EffortBar(Modifier.fillMaxWidth()); Spacer(Modifier.height(5.dp)); Tq("zone 12–14", 11.sp, p.effort.second, FontWeight.SemiBold) }
        }
        Sz.M -> Medium(m, Icons.Rounded.Bolt, "Effort du jour", p.effort.first, "zone 12–14", p.effort.second, chartW = 82.dp, left = {
            V("9,4", "/ 21", 26.sp); Tq("encore 2,6", 12.sp, p.sub)
        }) { Box(contentAlignment = Alignment.Center) { EffortDial(78.dp, 8.dp); Icon(Icons.Rounded.Bolt, null, Modifier.size(24.dp), tint = p.effort.first) } }
        Sz.W -> Wide(m, Icons.Rounded.Bolt, p.effort.first, "Effort du jour", { V("9,4", "/ 21", 20.sp) }, 120.dp) {
            Column(horizontalAlignment = Alignment.End) { EffortBar(Modifier.width(112.dp)); Spacer(Modifier.height(5.dp)); Tq("zone 12–14", 11.sp, p.effort.second, FontWeight.SemiBold) }
        }
        Sz.L -> Large(m, Icons.Rounded.Bolt, "Effort du jour", p.effort.first, "zone 12–14", p.effort.second) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) { EffortDial(128.dp, 11.dp); Column(horizontalAlignment = Alignment.CenterHorizontally) { Tq("9,4", 32.sp, weight = FontWeight.SemiBold, num = true); Tq("sur 21", 12.sp, p.sub) } }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("Course", "6,1", 6.1f), Triple("Marche", "2,4", 2.4f), Triple("Escaliers", "0,9", 0.9f)).forEach { (l, v, f) ->
                        Column {
                            Row { Tq(l, 13.sp, p.sub, modifier = Modifier.weight(1f)); Tq(v, 13.sp, weight = FontWeight.SemiBold) }
                            Bar(f / 9.4f, p.effort, Modifier.fillMaxWidth().padding(top = 3.dp), 5.dp)
                        }
                    }
                    Tq("Encore 2,6 pour la zone", 13.sp, p.effort.second, FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable private fun BodyBox(d: Day, modifier: Modifier, pad: Dp = 6.dp) {
    val p = LocalSP.current
    val hot = when { d.next -> p.effort; d.done -> p.sub to p.sub; else -> p.charge }
    BodyT(d, modifier.padding(vertical = pad), p.bodyBase, p.bodyMuscle, hot.first, hot.second, p.bodyBone)
}

@Composable private fun StartButton(text: String, modifier: Modifier, h: Dp = 48.dp) {
    val p = LocalSP.current
    Row(modifier.height(h).clip(CircleShape).background(gr(p.effort)), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White); Tq(" $text", 15.sp, Color.White, FontWeight.SemiBold)
    }
}

@Composable fun WSeance(sz: Sz, m: Modifier = Modifier) {
    val p = LocalSP.current; val next = program4[2]
    when (sz) {
        Sz.S -> Tile10(Sz.S, m) {
            Head(Icons.Rounded.FitnessCenter, "Séance", p.effort.first)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Tq("Jambes", 15.sp, weight = FontWeight.SemiBold, num = true); Tq("55 min", 12.sp, p.sub) }
                BodyBox(next, Modifier.size(28.dp, 70.dp), 0.dp)
            }
        }
        Sz.M -> Tile10(Sz.M, m) {
            Head(Icons.Rounded.FitnessCenter, "Prochaine séance", p.effort.first, "3 / 4", p.sub)
            Row(Modifier.weight(1f).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BodyBox(next, Modifier.size(52.dp, 76.dp), 0.dp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Tq("Jambes", 19.sp, weight = FontWeight.SemiBold, num = true); Tq("5 exos · 55 min", 12.sp, p.sub) }
                Box(Modifier.size(44.dp).clip(CircleShape).background(gr(p.effort)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(26.dp), tint = Color.White) }
            }
        }
        Sz.W -> Tile10(Sz.W, m) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp, 50.dp).clip(RoundedCornerShape(12.dp)).background(p.tile2)) { BodyBox(next, Modifier.fillMaxSize(), 3.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Tq("Prochaine · 3 / 4", 12.sp, p.sub, FontWeight.Medium); Row(verticalAlignment = Alignment.Bottom) { Tq("Jambes", 18.sp, weight = FontWeight.SemiBold, num = true); Tq("  55 min", 12.sp, p.sub, modifier = Modifier.padding(bottom = 2.dp)) } }
                Box(Modifier.size(42.dp).clip(CircleShape).background(gr(p.effort)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(24.dp), tint = Color.White) }
            }
        }
        Sz.L -> Large(m, Icons.Rounded.FitnessCenter, "Push Pull Legs · semaine 3", p.effort.first, "2 / 4", p.sub) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                program4.forEach { d ->
                    val sh = RoundedCornerShape(18.dp)
                    Column(Modifier.weight(1f).clip(sh).background(p.tile2).then(if (d.next) Modifier.border(1.5.dp, p.effort.first, sh) else Modifier).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(88.dp)) {
                            BodyBox(d, Modifier.fillMaxSize(), 0.dp)
                            if (d.done) Box(Modifier.align(Alignment.TopEnd).padding(end = 6.dp).size(18.dp).clip(CircleShape).background(p.good), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = p.onAccent)
                            }
                        }
                        Tq(d.name, 13.sp, if (d.done) p.sub else p.ink, FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            StartButton("Commencer Jambes · 55 min", Modifier.fillMaxWidth())
        }
    }
}

@Composable private fun WeekBars(modifier: Modifier, h: Dp, letters: Boolean = true, barW: Dp = 7.dp) {
    val p = LocalSP.current
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        week7.forEachIndexed { i, (d, m) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(h), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.width(barW).height(if (m > 0) h * (m / 52f) else barW).clip(CircleShape)
                        .background(if (m > 0) Brush.verticalGradient(listOf(p.effort.second, p.effort.first)) else Brush.verticalGradient(listOf(p.faint, p.faint))))
                }
                if (letters) Tq(d, 10.sp, if (i == 4) p.ink else p.sub, FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable fun WSemaine(sz: Sz, m: Modifier = Modifier) {
    val p = LocalSP.current
    when (sz) {
        Sz.S -> Small(m, Icons.Rounded.DateRange, "Semaine", p.effort.first, null, { V("3", "activités", 22.sp) }) { WeekBars(Modifier.fillMaxWidth(), 26.dp, letters = false, barW = 6.dp) }
        Sz.M -> Medium(m, Icons.Rounded.DateRange, "Cette semaine", p.effort.first, "2 h 09", p.ink, left = {
            V("3", "activités", 26.sp); Delta("18 min", up = true, good = true)
        }) { WeekBars(Modifier.fillMaxWidth(), 48.dp) }
        Sz.W -> Wide(m, Icons.Rounded.DateRange, p.effort.first, "Cette semaine", { V("2", "h", 20.sp, "09", "· 3 activités") }, 128.dp) { WeekBars(Modifier.width(124.dp), 30.dp) }
        Sz.L -> Large(m, Icons.Rounded.DateRange, "Semaine d'entraînement", p.effort.first, "+ 18 min", p.good) {
            Row { Stat("Durée", "2 h 09", Modifier.weight(1f)); Stat("Activités", "3", Modifier.weight(1f)); Stat("Calories", "1 840", Modifier.weight(1f)); Stat("Volume", "24,6 t", Modifier.weight(1f)) }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week7.forEachIndexed { i, (d, mn) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            Dial(if (mn > 0) mn / 60f else 0f, p.effort.first, p.effort.second, p.faint, Modifier.size(40.dp), 4.dp, 360f)
                            if (mn > 0) Tq("$mn", 12.sp, weight = FontWeight.SemiBold, num = true)
                        }
                        Tq(d, 11.sp, if (i == 4) p.ink else p.sub, FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Tq("Objectif 60 min par jour actif", 11.sp, p.sub, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable fun WCourse(sz: Sz, m: Modifier = Modifier) {
    val p = LocalSP.current
    when (sz) {
        Sz.S -> Small(m, Icons.AutoMirrored.Rounded.DirectionsRun, "Course", p.forme.first, null, { V("5,2", "km", 22.sp) }) {
            RouteT(Modifier.fillMaxWidth().height(34.dp), p.forme.first, p.forme.second, p.ink, 2.5.dp)
        }
        Sz.M -> Medium(m, Icons.AutoMirrored.Rounded.DirectionsRun, "Course · hier", p.forme.first, chartW = 108.dp, left = {
            V("5,2", "km", 26.sp); Tq("5:31 /km", 12.sp, p.sub)
        }) { RouteT(Modifier.fillMaxWidth().height(66.dp), p.forme.first, p.forme.second, p.ink) }
        Sz.W -> Wide(m, Icons.AutoMirrored.Rounded.DirectionsRun, p.forme.first, "Course · hier", { V("5,2", "km", 20.sp) }, 120.dp) {
            Column(horizontalAlignment = Alignment.End) { Tq("5:31 /km", 16.sp, weight = FontWeight.SemiBold, num = true); Tq("28:41 · D+ 64 m", 11.sp, p.sub) }
        }
        Sz.L -> Large(m, Icons.AutoMirrored.Rounded.DirectionsRun, "Course · hier", p.forme.first, "négatif", p.good) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { V("5,2", "km", 30.sp) }
                Stat("Durée", "28:41", Modifier.width(62.dp)); Stat("Allure", "5:31", Modifier.width(50.dp)); Stat("D+", "64 m", Modifier.width(46.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RouteT(Modifier.size(128.dp, 100.dp), p.forme.first, p.forme.second, p.ink)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    val best = splits.minOf { it.second }
                    splits.forEachIndexed { i, (t, s) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Tq("km ${i + 1}", 12.sp, p.sub, modifier = Modifier.width(38.dp))
                            Box(Modifier.weight(1f).height(8.dp)) {
                                Box(Modifier.fillMaxWidth(1f - (s - 300) / 60f * 0.6f).fillMaxHeight().clip(CircleShape).background(if (s == best) gr(p.forme) else Brush.horizontalGradient(listOf(p.faint, p.faint))))
                            }
                            Tq(t, 13.sp, if (s == best) p.forme.first else null, FontWeight.SemiBold, true, modifier = Modifier.width(40.dp).padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Toutes les familles, dans l'ordre du catalogue. */
val FAMILIES10: List<Pair<String, @Composable (Sz, Modifier) -> Unit>> = listOf(
    "Forme du jour" to { s, m -> WForme(s, m) }, "Sommeil" to { s, m -> WSommeil(s, m) }, "Calories" to { s, m -> WCalories(s, m) },
    "Macros" to { s, m -> WMacros(s, m) }, "Repas du jour" to { s, m -> WRepas(s, m) }, "Pas" to { s, m -> WPas(s, m) },
    "Cœur" to { s, m -> WCoeur(s, m) }, "Variabilité cardiaque" to { s, m -> WVfc(s, m) }, "Poids" to { s, m -> WPoids(s, m) },
    "Effort" to { s, m -> WEffort(s, m) }, "Prochaine séance" to { s, m -> WSeance(s, m) }, "Semaine" to { s, m -> WSemaine(s, m) },
    "Course" to { s, m -> WCourse(s, m) },
)

