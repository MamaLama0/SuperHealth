package app.superhealth.lab

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Tour 10 — Score : les trois onglets montés uniquement avec les cartes à quatre tailles (grille de 3 blocs, écart 10),
 * barre flottante + rond (H1). En Nutrition, la recherche d'aliment reste fixée en bas, au-dessus de la barre.
 */

private val GAP10 = 10.dp

@Composable private fun GRow(content: @Composable RowScope.() -> Unit) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP10), content = content)

@Composable
private fun Screen10(dark: Boolean, tab: Int, title: String, wash: (SP) -> Color, bottomExtra: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = SP(dark)
    CompositionLocalProvider(LocalSP provides p) {
        Box(Modifier.fillMaxSize().background(p.bg)) {
            Box(Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(listOf(wash(p).copy(alpha = if (dark) 0.12f else 0.10f), Color.Transparent))))
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 44.dp), verticalArrangement = Arrangement.spacedBy(GAP10)) {
                Row(Modifier.padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Tq("Vendredi 30 octobre", 13.sp, p.sub, FontWeight.Medium); Tq(title, 28.sp, weight = FontWeight.SemiBold, num = true) }
                    Box(Modifier.size(40.dp).clip(CircleShape).background(p.tile).border(1.dp, p.line, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = p.ink) }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(40.dp).clip(CircleShape).background(p.ink), contentAlignment = Alignment.Center) { Tq("B", 16.sp, p.bg, FontWeight.SemiBold) }
                }
                content()
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(if (bottomExtra != null) 40.dp else 70.dp).background(Brush.verticalGradient(listOf(Color.Transparent, p.bg))))
                Column(Modifier.fillMaxWidth().background(p.bg).padding(start = 16.dp, end = 16.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    bottomExtra?.invoke()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.shadow(if (dark) 0.dp else 10.dp, CircleShape, ambientColor = Color(0x1F000000), spotColor = Color(0x1F000000)).clip(CircleShape)
                            .background(if (dark) p.tile2 else p.tile).border(1.dp, p.line, CircleShape).padding(5.dp)) {
                            listOf(Icons.Rounded.Today to "Aujourd'hui", Icons.Rounded.Restaurant to "Nutrition", Icons.AutoMirrored.Rounded.DirectionsRun to "Activités").forEachIndexed { i, (ic, l) ->
                                val on = i == tab
                                Row(Modifier.clip(CircleShape).background(if (on) p.ink else Color.Transparent).padding(horizontal = if (on) 14.dp else 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(ic, null, Modifier.size(21.dp), tint = if (on) p.bg else p.sub); if (on) Tq("  $l", 14.sp, p.bg, FontWeight.SemiBold)
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(56.dp).shadow(if (dark) 0.dp else 10.dp, CircleShape).clip(CircleShape).background(gr(p.forme)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(30.dp), tint = p.onAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Score10Home(dark: Boolean) = Screen10(dark, 0, "Aujourd'hui", { it.forme.first }) {
    WForme(Sz.L, Modifier.fillMaxWidth())
    GRow { WSommeil(Sz.M, Modifier.weight(2f)); WCalories(Sz.S, Modifier.weight(1f)) }
    GRow { WPas(Sz.S, Modifier.weight(1f)); WCoeur(Sz.S, Modifier.weight(1f)); WPoids(Sz.S, Modifier.weight(1f)) }
    WSeance(Sz.W, Modifier.fillMaxWidth())
    GRow { WVfc(Sz.S, Modifier.weight(1f)); WSemaine(Sz.M, Modifier.weight(2f)) }
}

@Composable
fun Score10Food(dark: Boolean) = Screen10(dark, 1, "Nutrition", { it.kcal.first }, bottomExtra = {
    // recherche fixe, au-dessus de la barre : toujours sous le pouce, quel que soit le défilement
    val p = LocalSP.current
    Row(Modifier.fillMaxWidth().height(50.dp).shadow(if (p.dark) 0.dp else 8.dp, CircleShape, ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000))
        .clip(CircleShape).background(p.tile).border(1.dp, p.line, CircleShape).padding(start = 16.dp, end = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = p.sub); Tq("  Ajouter un aliment", 15.sp, p.sub, modifier = Modifier.weight(1f))
        Box(Modifier.size(40.dp).clip(CircleShape).background(p.tile2), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(20.dp), tint = p.ink) }
    }
}) {
    val p = LocalSP.current
    Row(Modifier.fillMaxWidth()) {
        listOf("L" to 26, "M" to 27, "M" to 28, "J" to 29, "V" to 30, "S" to 31, "D" to 1).forEachIndexed { i, (d, n) ->
            val today = i == 4
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (today) p.tile else Color.Transparent).then(if (today) Modifier.border(1.dp, p.line, RoundedCornerShape(16.dp)) else Modifier)
                .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Tq(d, 11.sp, if (today) p.ink else p.sub, FontWeight.SemiBold)
                Box(Modifier.padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    Dial(listOf(0.96f, 1f, 0.88f, 0.99f, 0.59f, 0f, 0f)[i], p.kcal.first, p.kcal.second, p.faint, Modifier.size(32.dp), 3.5.dp, 360f)
                    Tq("$n", 11.sp, if (i > 4) p.sub else p.ink, FontWeight.SemiBold)
                }
            }
        }
    }
    GRow { WCalories(Sz.M, Modifier.weight(2f)); WMacros(Sz.S, Modifier.weight(1f)) }
    meals4.forEachIndexed { mi, (name, time, foods) ->
        val sh = RoundedCornerShape(22.dp)
        Column(Modifier.fillMaxWidth().clip(sh).background(p.tile).border(1.dp, p.line, sh).padding(12.dp)) {
            Row(Modifier.padding(start = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(listOf(p.steps.first, p.kcal.first, p.repos.first)[mi])); Spacer(Modifier.width(8.dp))
                Tq(name, 16.sp, weight = FontWeight.SemiBold, num = true); Tq("  $time", 13.sp, p.sub, modifier = Modifier.weight(1f)); Tq("${foods.sumOf { it.kcal }} kcal", 13.sp, weight = FontWeight.SemiBold)
            }
            foods.forEach { f ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(p.tile2), contentAlignment = Alignment.Center) { PixelIcon(f.icon, 26.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Tq(f.name, 15.sp); Tq(f.qty, 12.sp, p.sub) }
                    Tq("${f.kcal}", 14.sp, p.sub, FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun Score10Act(dark: Boolean) = Screen10(dark, 2, "Activités", { it.charge.first }) {
    val p = LocalSP.current
    WSemaine(Sz.W, Modifier.fillMaxWidth())
    WSeance(Sz.L, Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Icons.Rounded.Bolt to "Séance libre", Icons.Rounded.AutoAwesome to "Coach", Icons.Rounded.History to "Historique").forEach { (ic, l) ->
            Row(Modifier.weight(1f).clip(CircleShape).background(p.tile).border(1.dp, p.line, CircleShape).padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(ic, null, Modifier.size(16.dp), tint = p.ink); Tq(" $l", 13.sp, weight = FontWeight.Medium)
            }
        }
    }
    GRow {
        WCourse(Sz.M, Modifier.weight(2f))
        Small(Modifier.weight(1f), Icons.Rounded.FitnessCenter, "Pull", p.effort.first, null, { V("12,3", "t", 22.sp) }) { Tq("mardi · 18 séries", 11.sp, p.sub) }
    }
    GRow {
        Small(Modifier.weight(1f), Icons.AutoMirrored.Rounded.DirectionsBike, "Vélo", p.charge.first, null, { V("21,0", "km", 22.sp) }) { Tq("lundi · 48 min", 11.sp, p.sub) }
        WEffort(Sz.M, Modifier.weight(2f))
    }
}

/** Planche : chaque famille dans ses quatre tailles, rangées comme dans la grille de l'app. */
@Composable
fun Score10Sheet(dark: Boolean, from: Int, to: Int) {
    val p = SP(dark)
    CompositionLocalProvider(LocalSP provides p) {
        Row(Modifier.fillMaxSize().background(p.bg).padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            FAMILIES10.subList(from, to).chunked(((to - from) + 2) / 3).forEach { col ->
                Column(Modifier.width(393.dp).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(GAP10)) {
                    col.forEach { (name, w) ->
                        Row(Modifier.padding(start = 4.dp, top = 14.dp), verticalAlignment = Alignment.Bottom) {
                            Tq(name, 19.sp, weight = FontWeight.SemiBold, num = true, modifier = Modifier.weight(1f)); Tq("S · M · W · L", 12.sp, p.sub, FontWeight.Medium)
                        }
                        GRow { w(Sz.S, Modifier.weight(1f)); w(Sz.M, Modifier.weight(2f)) }
                        w(Sz.W, Modifier.fillMaxWidth())
                        w(Sz.L, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
