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
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Tour 9 — B. Santé (lignée Apple Santé / Forme + Google Health). Fond groupé, cartes de résumé : en-tête icône + nom
 * dans la couleur de la mesure, valeur à gauche, mini-graphique à droite. Anneaux d'objectifs, listes groupées en retrait
 * pour les repas, liste de séances façon Forme. Police de l'app (Google Sans Flex). Clair et sombre.
 */

private class SaP(val dark: Boolean) {
    val bg = if (dark) Color.Black else Color(0xFFF2F2F7)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val card2 = if (dark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
    val sep = if (dark) Color(0xFF38383A) else Color(0xFFE5E5EA)
    val ink = if (dark) Color.White else Color.Black
    val sub = if (dark) Color(0xFF8E8E93) else Color(0xFF8A8A8E)
    val track = if (dark) Color(0xFF2C2C2E) else Color(0xFFE9E9EE)
    private fun c(l: Long, d: Long) = if (dark) Color(d) else Color(l)
    val red = c(0xFFFF2D55, 0xFFFF375F); val red2 = c(0xFFFF6482, 0xFFFF6E8A)
    val green = c(0xFF28B850, 0xFF30D158); val green2 = c(0xFF8EDC3C, 0xFFA6F04A)
    val cyan = c(0xFF1BA6E0, 0xFF5AC8FA); val cyan2 = c(0xFF35D0E8, 0xFF7FE7F5)
    val orange = c(0xFFFF8A00, 0xFFFF9F0A)
    val indigo = c(0xFF5856D6, 0xFF7D7AFF); val indigo2 = c(0xFF34AADC, 0xFF64D2FF)
    val purple = c(0xFFAF52DE, 0xFFBF5AF2)
    val blue = c(0xFF007AFF, 0xFF0A84FF)
    val yellow = c(0xFFE6A800, 0xFFFFD60A)
}
private val LocalSa = staticCompositionLocalOf { SaP(false) }

@Composable private fun Sa(text: String, size: TextUnit, color: Color? = null, weight: FontWeight = FontWeight.Normal, display: Boolean = false, modifier: Modifier = Modifier) =
    X(text, size, color ?: LocalSa.current.ink, weight, display, modifier, lines = 1)

@Composable private fun SaCard(modifier: Modifier = Modifier, pad: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LocalSa.current.card).padding(pad), content = content)

@Composable private fun Section(title: String, link: String? = null) {
    val p = LocalSa.current
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.Bottom) {
        Sa(title, 21.sp, weight = FontWeight.Bold, display = true, modifier = Modifier.weight(1f)); if (link != null) Sa(link, 16.sp, p.blue)
    }
}

/** Carte de résumé : en-tête coloré, valeur à gauche, graphique à droite. */
@Composable private fun Summary(icon: ImageVector, title: String, c: Color, time: String, chartW: Dp = 128.dp, value: @Composable () -> Unit, chart: @Composable RowScope.() -> Unit) {
    val p = LocalSa.current
    SaCard(pad = 13.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = c); Spacer(Modifier.width(6.dp)); Sa(title, 15.sp, c, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Sa(time, 13.sp, p.sub); Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = p.sub)
        }
        Row(Modifier.padding(top = 8.dp).height(44.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { value() }
            Row(Modifier.width(chartW).fillMaxHeight(), verticalAlignment = Alignment.Bottom, content = chart)
        }
    }
}

@Composable private fun Big(v: String, unit: String, v2: String? = null, unit2: String? = null) {
    val p = LocalSa.current
    Row(verticalAlignment = Alignment.Bottom) {
        Sa(v, 28.sp, weight = FontWeight.SemiBold, display = true); Sa(" $unit", 15.sp, p.sub, FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp))
        if (v2 != null) { Sa(" $v2", 28.sp, weight = FontWeight.SemiBold, display = true); Sa(" ${unit2 ?: ""}", 15.sp, p.sub, FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp)) }
    }
}

@Composable
private fun SaScreen(dark: Boolean, tab: Int, kicker: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    val p = SaP(dark)
    CompositionLocalProvider(LocalSa provides p) {
        Box(Modifier.fillMaxSize().background(p.bg)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) { Sa(kicker.uppercase(), 13.sp, p.sub, FontWeight.SemiBold); Sa(title, 34.sp, weight = FontWeight.Bold, display = true) }
                    Box(Modifier.padding(bottom = 4.dp).size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.cyan, p.indigo))), contentAlignment = Alignment.Center) {
                        Sa("B", 17.sp, Color.White, FontWeight.SemiBold)
                    }
                }
                content()
            }
            // barre d'onglets pleine largeur, + dans le coin du pouce
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(if (dark) Color(0xFF161618) else Color(0xFFF9F9F9))) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(p.sep))
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(Icons.Rounded.Today to "Aujourd'hui", Icons.Rounded.Restaurant to "Nutrition", Icons.AutoMirrored.Rounded.DirectionsRun to "Activités").forEachIndexed { i, (ic, l) ->
                        val c = if (i == tab) p.blue else p.sub
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Icon(ic, null, Modifier.size(26.dp), tint = c); Sa(l, 11.sp, c, FontWeight.Medium) }
                    }
                    Box(Modifier.size(52.dp).clip(CircleShape).background(p.blue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, Modifier.size(30.dp), tint = Color.White) }
                }
            }
        }
    }
}

// ---- Aujourd'hui --------------------------------------------------------------------------------------------------------------

@Composable
fun SanteHome(dark: Boolean) = SaScreen(dark, 0, "Vendredi 30 octobre", "Résumé") {
    val p = LocalSa.current
    SaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val rings = listOf(Triple(1353f / 2280, p.red, p.red2), Triple(9.4f / 13, p.green, p.green2), Triple(6240f / 8000, p.cyan, p.cyan2))
            Canvas(Modifier.size(128.dp)) {
                val w = 15.dp.toPx(); val g = 2.5.dp.toPx()
                rings.forEachIndexed { i, (f, a, b) -> arcT(center, size.minDimension / 2 - w / 2 - i * (w + g), w, f, a, b, if (dark) p.track else a.copy(alpha = 0.16f)) }
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Triple("Calories", "1 353" to "/2 280 kcal", p.red), Triple("Effort", "9,4" to "/12–14", p.green), Triple("Pas", "6 240" to "/8 000", p.cyan)).forEach { (l, v, c) ->
                    Column {
                        Sa(l, 14.sp, c, FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.Bottom) { Sa(v.first, 21.sp, weight = FontWeight.SemiBold, display = true); Sa(v.second, 14.sp, p.sub, modifier = Modifier.padding(bottom = 2.dp)) }
                    }
                }
            }
        }
    }
    Section("Épinglés", "Modifier")
    Summary(Icons.Rounded.SelfImprovement, "Forme", p.green, "7:20", chartW = 150.dp, value = {
        Column { Big("78", "/ 100"); }
    }) {
        // Forme, Charge, Repos en trois barres compactes
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf(Triple("Charge", 0.64f, p.blue), Triple("Repos", 0.84f, p.indigo)).forEach { (l, f, c) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Sa(l, 11.sp, p.sub, modifier = Modifier.width(44.dp))
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(p.track)) { Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(CircleShape).background(c)) }
                }
            }
        }
    }
    Summary(Icons.Rounded.Bedtime, "Sommeil", p.indigo, "07:20", value = { Big("7", "h", "32", "min") }) {
        Column(Modifier.fillMaxWidth()) {
            HypnoT(Modifier.fillMaxWidth().height(32.dp), p.orange, p.indigo2, p.indigo, p.indigo.copy(alpha = 0.55f), stroke = 2.dp)
            Spacer(Modifier.height(5.dp))
            PhaseBarT(Modifier.fillMaxWidth().height(6.dp), listOf(p.indigo.copy(alpha = 0.6f).compositeOn(p.card), p.indigo, p.indigo2, p.orange))
        }
    }
    Summary(Icons.Rounded.Favorite, "Cœur au repos", p.red, "14:02", value = { Big("54", "bpm") }) {
        LineT(heart4.map { it.toFloat() }, Modifier.fillMaxWidth().fillMaxHeight(), p.red, p.red, null, p.card, 2.dp)
    }
    Summary(Icons.AutoMirrored.Rounded.DirectionsRun, "Pas", p.orange, "16:00", value = { Big("6 240", "pas") }) {
        StepBarsT(Modifier.fillMaxWidth().fillMaxHeight(), p.orange, p.orange, p.track)
    }
    Summary(Icons.Rounded.MonitorWeight, "Poids", p.purple, "Hier", value = { Big("81,2", "kg") }) {
        LineT(weight4.map { it.toFloat() }, Modifier.fillMaxWidth().fillMaxHeight(), p.purple, p.purple, null, p.card, 2.dp)
    }
}

private fun Color.compositeOn(bg: Color) = Color(bg.red + (red - bg.red) * alpha, bg.green + (green - bg.green) * alpha, bg.blue + (blue - bg.blue) * alpha)

// ---- Nutrition ---------------------------------------------------------------------------------------------------------------

@Composable
fun SanteFood(dark: Boolean) = SaScreen(dark, 1, "Semaine 44", "Nutrition") {
    val p = LocalSa.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        listOf("L" to 26, "M" to 27, "M" to 28, "J" to 29, "V" to 30, "S" to 31, "D" to 1).forEachIndexed { i, (d, n) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Sa(d, 12.sp, p.sub, FontWeight.Medium)
                Box(Modifier.padding(top = 4.dp).size(34.dp).clip(CircleShape).background(if (i == 4) p.red else Color.Transparent), contentAlignment = Alignment.Center) {
                    Sa("$n", 17.sp, if (i == 4) Color.White else if (i > 4) p.sub else p.ink, if (i == 4) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
    SaCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) { Sa("Restantes", 14.sp, p.red, FontWeight.SemiBold); Big("927", "kcal") }
            Column(horizontalAlignment = Alignment.End) { Sa("1 353 mangées", 13.sp, p.sub); Sa("objectif 2 280", 13.sp, p.sub) }
        }
        Box(Modifier.padding(top = 10.dp, bottom = 14.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(p.track)) {
            Box(Modifier.fillMaxWidth(1353f / 2280).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(p.red2, p.red))))
        }
        Row {
            listOf(Triple("Protéines", 98 to 150, p.red), Triple("Lipides", 52 to 76, p.yellow), Triple("Glucides", 141 to 285, p.green)).forEach { (l, v, c) ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        Dial(v.first.toFloat() / v.second, c, c, if (dark) p.track else c.copy(alpha = 0.16f), Modifier.size(38.dp), 5.dp, 360f)
                        Sa("${v.first * 100 / v.second}", 11.sp, weight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(7.dp))
                    Column { Sa(l, 12.sp, c, FontWeight.SemiBold); Sa("${v.first}/${v.second}", 13.sp, weight = FontWeight.Medium) }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (dark) p.card else Color(0xFFE3E3E8)).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = p.sub); Sa(" Rechercher un aliment", 16.sp, p.sub, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(20.dp), tint = p.blue)
    }
    meals4.forEach { (name, time, foods) ->
        Column {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp, top = 4.dp)) {
                Sa("${name.uppercase()} · $time", 13.sp, p.sub, FontWeight.Medium, modifier = Modifier.weight(1f)); Sa("${foods.sumOf { it.kcal }} KCAL", 13.sp, p.sub, FontWeight.Medium)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.card)) {
                foods.forEach { f ->
                    Row(Modifier.padding(start = 12.dp, end = 14.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(p.card2), contentAlignment = Alignment.Center) { PixelIcon(f.icon, 24.dp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Sa(f.name, 16.sp); Sa(f.qty, 13.sp, p.sub) }
                        Sa("${f.kcal} kcal", 15.sp, p.sub)
                    }
                    Box(Modifier.padding(start = 58.dp).fillMaxWidth().height(0.5.dp).background(p.sep))
                }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, Modifier.size(22.dp), tint = p.blue) }
                    Spacer(Modifier.width(12.dp)); Sa("Ajouter", 16.sp, p.blue)
                }
            }
        }
    }
}

// ---- Activités ---------------------------------------------------------------------------------------------------------------

@Composable
fun SanteAct(dark: Boolean) = SaScreen(dark, 2, "Semaine 44", "Activités") {
    val p = LocalSa.current
    val next = program4[2]
    SaCard(pad = 0.dp) {
        Row(Modifier.padding(start = 12.dp, end = 14.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(96.dp, 120.dp).clip(RoundedCornerShape(12.dp)).background(p.card2)) {
                BodyT(next, Modifier.fillMaxSize().padding(vertical = 8.dp), if (dark) Color(0xFF3A3A3C) else Color(0xFFD9D9DE), if (dark) Color(0xFF48484A) else Color(0xFFC7C7CC),
                    p.orange, p.red, if (dark) Color(0xFF2C2C2E) else Color(0xFFE9E9EE))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Sa("PROCHAINE SÉANCE", 12.sp, p.orange, FontWeight.SemiBold)
                Sa("Jambes", 26.sp, weight = FontWeight.Bold, display = true)
                Sa("Cuisses · Mollets · 55 min", 14.sp, p.sub)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.clip(CircleShape).background(p.orange).padding(start = 14.dp, end = 18.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp), tint = Color.White); Sa(" Commencer", 16.sp, Color.White, FontWeight.SemiBold)
                }
            }
        }
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(0.5.dp).background(p.sep))
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Sa("Push Pull Legs", 15.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            program4.forEach { d ->
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(26.dp).clip(CircleShape).background(when { d.done -> p.green; d.next -> p.orange.copy(alpha = if (dark) 1f else 1f); else -> p.track }), contentAlignment = Alignment.Center) {
                    if (d.done) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Color.White)
                    else Sa(d.name.take(1), 12.sp, if (d.next) Color.White else p.sub, FontWeight.SemiBold)
                }
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.padding(start = 4.dp).size(18.dp), tint = p.sub)
        }
    }
    SaCard(pad = 13.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Sa("Cette semaine", 15.sp, p.green, FontWeight.SemiBold)
                Big("2", "h", "09", "min"); Sa("3 activités · 1 840 kcal", 13.sp, p.sub)
            }
            week7.forEachIndexed { i, (d, m) ->
                Column(Modifier.width(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(58.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.width(9.dp).height(if (m > 0) (58 * m / 52).dp else 9.dp).clip(CircleShape).background(if (m > 0) p.green else p.track))
                    }
                    Sa(d, 11.sp, if (i == 4) p.ink else p.sub, FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
    SaCard(pad = 13.dp) {
        Sa("Séries cette semaine", 15.sp, p.orange, FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        listOf(Triple("Pecs", 10, 12), Triple("Dos", 12, 12), Triple("Épaules", 8, 10), Triple("Jambes", 0, 12)).forEach { (m, v, t) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Sa(m, 14.sp, modifier = Modifier.width(70.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(p.track)) { if (v > 0) Box(Modifier.fillMaxWidth(v.toFloat() / t).fillMaxHeight().clip(CircleShape).background(p.orange)) }
                Sa("$v / $t", 13.sp, p.sub, modifier = Modifier.width(62.dp).padding(start = 10.dp))
            }
        }
    }
    Section("Récentes", "Tout voir")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.card)) {
        val rows = listOf(Triple(Icons.AutoMirrored.Rounded.DirectionsRun, p.green, 0), Triple(Icons.Rounded.FitnessCenter, p.orange, 1), Triple(Icons.AutoMirrored.Rounded.DirectionsBike, p.cyan, 2))
        rows.forEachIndexed { i, (ic, c, k) ->
            val (t, meta, v) = recent3[k]
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(if (dark) p.card2 else c.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(ic, null, Modifier.size(26.dp), tint = c)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Sa(t, 15.sp, weight = FontWeight.Medium)
                    Sa(v, 24.sp, c, FontWeight.SemiBold, display = true)
                    Sa(meta, 13.sp, p.sub)
                }
                if (k == 0) RouteT(Modifier.size(72.dp, 52.dp), c, c, p.ink, 2.5.dp)
            }
            if (i < rows.lastIndex) Box(Modifier.padding(start = 70.dp).fillMaxWidth().height(0.5.dp).background(p.sep))
        }
    }
}
