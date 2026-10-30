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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Tour 9 — A. Score (lignée WHOOP / Oura / Keynote). Données au premier plan : trois cadrans en tête (Forme au centre),
 * tuiles à filet fin, dégradés uniquement sur les données, corps « rayons X ». Halo d'ambiance très doux en haut,
 * teinté par l'état du jour (pas de lueur sur les éléments). Inter Display pour les chiffres. Clair et sombre.
 */

private class ScP(val dark: Boolean) {
    val bg = if (dark) Color(0xFF08090B) else Color(0xFFF3F4F6)
    val tile = if (dark) Color(0xFF131519) else Color.White
    val tile2 = if (dark) Color(0xFF1D2025) else Color(0xFFF0F1F4)
    val line = if (dark) Color(0x14FFFFFF) else Color(0x12000000)
    val ink = if (dark) Color(0xFFF4F5F7) else Color(0xFF0F1114)
    val sub = if (dark) Color(0xFF8E939B) else Color(0xFF6B7079)
    val faint = if (dark) Color(0xFF2A2E35) else Color(0xFFE3E5E9)
    private fun h(d: Long, l: Long, d2: Long, l2: Long) = if (dark) Color(d) to Color(d2) else Color(l) to Color(l2)
    val forme = h(0xFF2EE59D, 0xFF0FA968, 0xFF24C6FF, 0xFF0E9BD8)
    val charge = h(0xFF3D9BFF, 0xFF1F6FE5, 0xFF7CC4FF, 0xFF4C9CFF)
    val repos = h(0xFF8B6CFF, 0xFF6A4BE0, 0xFFCE8CFF, 0xFFA66BF0)
    val effort = h(0xFFFF7A3D, 0xFFF0601F, 0xFFFF4D7E, 0xFFE8336A)
    val kcal = h(0xFF2ED3C6, 0xFF0E9F95, 0xFF3CA9FF, 0xFF1C86E0)
    val sleep = h(0xFF6E6BFF, 0xFF4F4FE0, 0xFFB387FF, 0xFF8B62F0)
    val heart = h(0xFFFF4D6D, 0xFFE02F55, 0xFFFF8FA8, 0xFFF0668A)
    val steps = h(0xFFFFC233, 0xFFE09A00, 0xFFFF8F33, 0xFFF07A1A)
    val prot = if (dark) Color(0xFFFF6B88) else Color(0xFFE23E62)
    val fat = if (dark) Color(0xFFFFC94D) else Color(0xFFD99A00)
    val carb = if (dark) Color(0xFF3DDB9A) else Color(0xFF14A86A)
    val onAccent = if (dark) Color(0xFF08090B) else Color.White
}
private val LocalSc = staticCompositionLocalOf { ScP(true) }

@Composable private fun St(text: String, size: TextUnit, color: Color? = null, weight: FontWeight = FontWeight.Normal, num: Boolean = false, modifier: Modifier = Modifier, brush: Brush? = null) =
    X(text, size, color ?: LocalSc.current.ink, weight, family = if (num) InterDisplay else Inter, spacing = if (num) (-0.02 * size.value).sp else 0.sp, modifier = modifier, lines = 1, brush = brush)

@Composable private fun STile(modifier: Modifier = Modifier, pad: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalSc.current; val sh = RoundedCornerShape(22.dp)
    Column(modifier.clip(sh).background(p.tile).border(1.dp, p.line, sh).padding(pad), content = content)
}

@Composable private fun SLabel(icon: ImageVector, text: String, c: Color, trailing: String? = null) {
    val p = LocalSc.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = c); Spacer(Modifier.width(6.dp)); St(text, 13.sp, p.sub, FontWeight.Medium, modifier = Modifier.weight(1f))
        if (trailing != null) St(trailing, 13.sp, c, FontWeight.SemiBold)
    }
}

private fun g(p: Pair<Color, Color>) = Brush.horizontalGradient(listOf(p.first, p.second))

@Composable
private fun SScreen(dark: Boolean, tab: Int, title: String, tint: Color, content: @Composable ColumnScope.() -> Unit) {
    val p = ScP(dark)
    CompositionLocalProvider(LocalSc provides p) {
        Box(Modifier.fillMaxSize().background(p.bg)) {
            // ambiance : un voile très doux, teinté par l'état du jour
            Box(Modifier.fillMaxWidth().height(340.dp).background(Brush.verticalGradient(listOf(tint.copy(alpha = if (dark) 0.16f else 0.12f), Color.Transparent))))
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(top = 44.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.padding(start = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { St("Vendredi 30 octobre", 13.sp, p.sub, FontWeight.Medium); St(title, 28.sp, weight = FontWeight.SemiBold, num = true) }
                    Box(Modifier.size(40.dp).clip(CircleShape).background(p.tile).border(1.dp, p.line, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = p.ink) }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(40.dp).clip(CircleShape).background(p.ink), contentAlignment = Alignment.Center) { St("B", 16.sp, p.bg, FontWeight.SemiBold) }
                }
                content()
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Transparent, p.bg))))
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.shadow(if (dark) 0.dp else 10.dp, CircleShape, ambientColor = Color(0x1F000000), spotColor = Color(0x1F000000)).clip(CircleShape)
                    .background(if (dark) p.tile2 else p.tile).border(1.dp, p.line, CircleShape).padding(5.dp)) {
                    listOf(Icons.Rounded.Today to "Aujourd'hui", Icons.Rounded.Restaurant to "Nutrition", Icons.AutoMirrored.Rounded.DirectionsRun to "Activités").forEachIndexed { i, (ic, l) ->
                        val on = i == tab
                        Row(Modifier.clip(CircleShape).background(if (on) p.ink else Color.Transparent).padding(horizontal = if (on) 14.dp else 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(ic, null, Modifier.size(21.dp), tint = if (on) p.bg else p.sub); if (on) St("  $l", 14.sp, p.bg, FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(58.dp).shadow(if (dark) 0.dp else 10.dp, CircleShape).clip(CircleShape).background(g(p.forme)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(30.dp), tint = p.onAccent)
                }
            }
        }
    }
}

// ---- Aujourd'hui --------------------------------------------------------------------------------------------------------------

@Composable
fun ScoreHome(dark: Boolean) = SScreen(dark, 0, "Aujourd'hui", ScP(dark).forme.first) {
    val p = LocalSc.current
    // trois cadrans, la Forme au centre (comme les trois scores de WHOOP)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        @Composable fun D(v: Int, label: String, c: Pair<Color, Color>, size: Dp, stroke: Dp, big: Boolean) =
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Dial(v / 100f, c.first, c.second, p.faint, Modifier.size(size), stroke)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        St("$v", if (big) 40.sp else 24.sp, weight = FontWeight.SemiBold, num = true)
                        if (big) St("Forme", 12.sp, p.sub, FontWeight.Medium)
                    }
                }
                if (!big) St(label, 13.sp, p.sub, FontWeight.Medium, modifier = Modifier.offset(y = (-4).dp))
            }
        D(64, "Charge", p.charge, 88.dp, 8.dp, false); D(78, "Forme", p.forme, 136.dp, 12.dp, true); D(84, "Repos", p.repos, 88.dp, 8.dp, false)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        St("Prêt à pousser", 17.sp, p.forme.first, FontWeight.SemiBold, brush = g(p.forme)); St("  ·  effort conseillé 12–14", 14.sp, p.sub)
    }
    Row(Modifier.height(166.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        STile(Modifier.weight(1.35f).fillMaxHeight()) {
            SLabel(Icons.Rounded.Bedtime, "Sommeil", p.sleep.second, "84")
            Row(verticalAlignment = Alignment.Bottom) { St("7", 26.sp, weight = FontWeight.SemiBold, num = true); St(" h ", 14.sp, p.sub, modifier = Modifier.padding(bottom = 3.dp)); St("32", 26.sp, weight = FontWeight.SemiBold, num = true) }
            Spacer(Modifier.weight(1f))
            HypnoT(Modifier.fillMaxWidth().height(58.dp), p.heart.second, p.charge.second, p.sleep.second, p.sleep.first, p.sleep.first.copy(alpha = 0.22f))
            Row { St("23:48", 11.sp, p.sub); Spacer(Modifier.weight(1f)); St("07:20", 11.sp, p.sub) }
        }
        STile(Modifier.weight(1f).fillMaxHeight()) {
            SLabel(Icons.Rounded.LocalFireDepartment, "Calories", p.kcal.first)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Dial(1353f / 2280, p.kcal.first, p.kcal.second, p.faint, Modifier.size(104.dp), 10.dp, 360f)
                Column(horizontalAlignment = Alignment.CenterHorizontally) { St("927", 24.sp, weight = FontWeight.SemiBold, num = true); St("restantes", 11.sp, p.sub) }
            }
        }
    }
    Row(Modifier.height(140.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        STile(Modifier.weight(1f).fillMaxHeight()) {
            SLabel(Icons.AutoMirrored.Rounded.DirectionsRun, "Pas", p.steps.first, "78 %")
            St("6 240", 24.sp, weight = FontWeight.SemiBold, num = true); Spacer(Modifier.weight(1f))
            StepBarsT(Modifier.fillMaxWidth().height(40.dp), p.steps.first, p.steps.second, p.faint)
        }
        STile(Modifier.weight(1f).fillMaxHeight()) {
            SLabel(Icons.Rounded.Favorite, "Cœur au repos", p.heart.first)
            Row(verticalAlignment = Alignment.Bottom) { St("54", 24.sp, weight = FontWeight.SemiBold, num = true); St(" bpm", 13.sp, p.sub, modifier = Modifier.padding(bottom = 3.dp)) }
            Spacer(Modifier.weight(1f))
            LineT(heart4.map { it.toFloat() }, Modifier.fillMaxWidth().height(40.dp), p.heart.first, p.heart.second, p.heart.first.copy(alpha = 0.22f), p.tile)
        }
    }
    Row(Modifier.height(112.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        STile(Modifier.weight(1f).fillMaxHeight()) {
            SLabel(Icons.Rounded.Bolt, "Effort du jour", p.effort.first)
            Row(verticalAlignment = Alignment.Bottom) { St("9,4", 24.sp, weight = FontWeight.SemiBold, num = true); St(" / 12–14", 13.sp, p.sub, modifier = Modifier.padding(bottom = 3.dp)) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(p.faint)) {
                Box(Modifier.fillMaxWidth(9.4f / 21).fillMaxHeight().clip(CircleShape).background(g(p.effort)))
                Box(Modifier.fillMaxWidth(14f / 21).fillMaxHeight()) { Box(Modifier.fillMaxWidth(2f / 14).fillMaxHeight().align(Alignment.CenterEnd).border(1.5.dp, p.effort.first, CircleShape)) }
            }
        }
        STile(Modifier.weight(1f).fillMaxHeight()) {
            SLabel(Icons.Rounded.MonitorWeight, "Poids", p.charge.first, "−1,4 kg")
            Row(verticalAlignment = Alignment.Bottom) { St("81,2", 24.sp, weight = FontWeight.SemiBold, num = true); St(" kg", 13.sp, p.sub, modifier = Modifier.padding(bottom = 3.dp)) }
            Spacer(Modifier.weight(1f))
            LineT(weight4.map { it.toFloat() }, Modifier.fillMaxWidth().height(28.dp), p.charge.first, p.charge.second, null, p.tile, 2.dp)
        }
    }
    STile(pad = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(p.tile2), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = p.repos.second) }
            Spacer(Modifier.width(10.dp)); St("Jambes conseillées aujourd'hui", 14.sp, modifier = Modifier.weight(1f)); Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.sub)
        }
    }
}

// ---- Nutrition ---------------------------------------------------------------------------------------------------------------

@Composable
fun ScoreFood(dark: Boolean) = SScreen(dark, 1, "Nutrition", ScP(dark).kcal.first) {
    val p = LocalSc.current
    Row(Modifier.fillMaxWidth()) {
        listOf("L" to 26, "M" to 27, "M" to 28, "J" to 29, "V" to 30, "S" to 31, "D" to 1).forEachIndexed { i, (d, n) ->
            val today = i == 4
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (today) p.tile else Color.Transparent).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                St(d, 11.sp, if (today) p.ink else p.sub, FontWeight.SemiBold)
                Box(Modifier.padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    Dial(listOf(0.96f, 1f, 0.88f, 0.99f, 0.59f, 0f, 0f)[i], p.kcal.first, p.kcal.second, p.faint, Modifier.size(32.dp), 3.5.dp, 360f)
                    St("$n", 11.sp, if (i > 4) p.sub else p.ink, FontWeight.SemiBold)
                }
            }
        }
    }
    STile {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(70.dp)) {
                val w = 8.dp.toPx(); val r = size.minDimension / 2 - w / 2
                listOf(98f / 150 to p.prot, 52f / 76 to p.fat, 141f / 285 to p.carb).forEachIndexed { i, (f, c) ->
                    arcT(center, r, w, f, c, c, p.faint, -90f + i * 120f + 9f, 102f)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.width(100.dp)) { St("927", 30.sp, p.kcal.first, FontWeight.SemiBold, true, brush = g(p.kcal)); St("kcal restantes", 12.sp, p.sub) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(Triple("Prot.", 98 to 150, p.prot), Triple("Lip.", 52 to 76, p.fat), Triple("Gluc.", 141 to 285, p.carb)).forEach { (n, v, c) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        St(n, 12.sp, p.sub, modifier = Modifier.width(38.dp))
                        Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(p.faint)) { Box(Modifier.fillMaxWidth(v.first.toFloat() / v.second).fillMaxHeight().clip(CircleShape).background(c)) }
                        St("${v.first}", 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.width(34.dp).padding(start = 7.dp))
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(p.tile).border(1.dp, p.line, CircleShape).padding(start = 16.dp, end = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = p.sub); St("  Ajouter un aliment", 15.sp, p.sub, modifier = Modifier.weight(1f))
        Box(Modifier.size(36.dp).clip(CircleShape).background(p.tile2), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(19.dp), tint = p.ink) }
    }
    meals4.forEachIndexed { mi, (name, time, foods) ->
        STile(pad = 12.dp) {
            Row(Modifier.padding(start = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(listOf(p.steps.first, p.kcal.first, p.repos.first)[mi])); Spacer(Modifier.width(8.dp))
                St(name, 16.sp, weight = FontWeight.SemiBold, num = true); St("  $time", 13.sp, p.sub, modifier = Modifier.weight(1f)); St("${foods.sumOf { it.kcal }} kcal", 13.sp, weight = FontWeight.SemiBold)
            }
            foods.forEach { f ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(p.tile2), contentAlignment = Alignment.Center) { PixelIcon(f.icon, 26.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { St(f.name, 15.sp); St(f.qty, 12.sp, p.sub) }
                    St("${f.kcal}", 14.sp, p.sub, FontWeight.Medium)
                }
            }
        }
    }
}

// ---- Activités ---------------------------------------------------------------------------------------------------------------

@Composable
fun ScoreAct(dark: Boolean) = SScreen(dark, 2, "Activités", ScP(dark).charge.first) {
    val p = LocalSc.current
    STile(pad = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) { St("3", 22.sp, weight = FontWeight.SemiBold, num = true); St(" activités", 14.sp, p.sub, modifier = Modifier.padding(bottom = 2.dp)) }
                St("2 h 09 cette semaine", 12.sp, p.sub)
            }
            week7.forEachIndexed { i, (d, m) ->
                Column(Modifier.width(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(34.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.width(7.dp).height(if (m > 0) (34 * m / 52).dp else 7.dp).clip(CircleShape)
                            .background(if (m > 0) Brush.verticalGradient(listOf(p.effort.second, p.effort.first)) else Brush.verticalGradient(listOf(p.faint, p.faint))))
                    }
                    St(d, 10.sp, if (i == 4) p.ink else p.sub, FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
    STile {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { St("Push Pull Legs", 19.sp, weight = FontWeight.SemiBold, num = true); St("Semaine 3 · 2 séances sur 4", 13.sp, p.sub) }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.sub)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            program4.forEach { d ->
                val sh = RoundedCornerShape(18.dp)
                Column(Modifier.weight(1f).clip(sh).background(p.tile2).then(if (d.next) Modifier.border(1.5.dp, p.effort.first, sh) else Modifier).padding(top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(96.dp)) {
                        val base = if (dark) Color(0xFF2A2D34) else Color(0xFFD7DAE0); val mus = if (dark) Color(0xFF383C45) else Color(0xFFC4C8D0)
                        val hot = when { d.next -> p.effort; d.done -> p.sub to p.sub; else -> p.charge }
                        BodyT(d, Modifier.fillMaxSize(), base, mus, hot.first, hot.second, if (dark) Color(0xFF202228) else Color(0xFFE6E8EC))
                        if (d.done) Box(Modifier.align(Alignment.TopEnd).padding(end = 6.dp).size(18.dp).clip(CircleShape).background(p.forme.first), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = p.onAccent)
                        }
                    }
                    St(d.name, 13.sp, if (d.done) p.sub else p.ink, FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(50.dp).clip(CircleShape).background(g(p.effort)), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White); St(" Commencer Jambes", 16.sp, Color.White, FontWeight.SemiBold); St("  · 55 min", 14.sp, Color(0xCCFFFFFF))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Icons.Rounded.Bolt to "Séance libre", Icons.Rounded.AutoAwesome to "Coach", Icons.Rounded.History to "Historique").forEach { (ic, l) ->
            Row(Modifier.weight(1f).clip(CircleShape).background(p.tile).border(1.dp, p.line, CircleShape).padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(ic, null, Modifier.size(16.dp), tint = p.ink); St(" $l", 13.sp, weight = FontWeight.Medium)
            }
        }
    }
    STile {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SLabel(Icons.AutoMirrored.Rounded.DirectionsRun, "Course · hier", p.forme.first)
                Row(verticalAlignment = Alignment.Bottom) { St("5,2", 30.sp, weight = FontWeight.SemiBold, num = true); St(" km", 14.sp, p.sub, modifier = Modifier.padding(bottom = 4.dp)) }
                St("28:41 · 5:31 /km · D+ 64 m", 12.sp, p.sub)
            }
            RouteT(Modifier.size(118.dp, 74.dp), p.forme.first, p.forme.second, p.ink)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple(Icons.Rounded.FitnessCenter, "Pull · mardi", "12,3 t"), Triple(Icons.AutoMirrored.Rounded.DirectionsBike, "Vélo · lundi", "21,0 km")).forEach { (ic, l, v) ->
            STile(Modifier.weight(1f), pad = 12.dp) { SLabel(ic, l, p.effort.first); St(v, 22.sp, weight = FontWeight.SemiBold, num = true) }
        }
    }
}
