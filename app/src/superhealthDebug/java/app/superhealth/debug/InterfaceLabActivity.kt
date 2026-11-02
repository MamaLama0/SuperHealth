package app.superhealth.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.nutrition.domain.AccentColor
import app.superhealth.nutrition.domain.ThemeMode
import app.superhealth.ui.theme.SuperTheme

/**
 * Version de test seulement : proposition d'interface, avec des valeurs d'exemple, rien n'est lu ni écrit.
 * `adb shell am start -S -n app.superhealth/app.superhealth.debug.InterfaceLabActivity --ei page 0..3`
 */
class InterfaceLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val page = intent.getIntExtra("page", 0)
        setContent {
            SuperTheme(ThemeMode.DARK, AccentColor.GREEN) {
                Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
                    when (page) { 1 -> Detail(); 2 -> Live(); 3 -> Board(); else -> Home() }
                }
            }
        }
    }
}

private val Bg = Color(0xFF0B0D10)
private val Line = Color.White.copy(alpha = 0.08f)
private val Sub = Color.White.copy(alpha = 0.62f)
private val Accent = Color(0xFF2FD07E)
private val HeartC = Color(0xFFF0606B)
private val SleepC = Color(0xFF9A8CF5)
private val StepsC = Color(0xFF2FD07E)
private val LoadC = Color(0xFF4FA8E8)
private val FoodC = Color(0xFF45C7C0)
private val MindC = Color(0xFF7B8CDE)

/** Fond d'une carte de mesure : aplat profond de la teinte ; le chaud reste sur noir neutre (pas de brun). */
private fun deep(c: Color, warm: Boolean = false) = if (warm) Color(0xFF1A1C21) else Color(
    c.red * 0.20f + 0.03f, c.green * 0.20f + 0.03f, c.blue * 0.20f + 0.04f)

@Composable private fun T(s: String, size: Int, c: Color = Color.White, w: FontWeight = FontWeight.Normal, m: Modifier = Modifier, lines: Int = 1) =
    Text(s, m, color = c, fontSize = size.sp, fontWeight = w, maxLines = lines, overflow = TextOverflow.Ellipsis, lineHeight = (size + 5).sp)

@Composable private fun Chip(i: ImageVector, c: Color, s: Dp = 30.dp) =
    Box(Modifier.size(s).clip(CircleShape).background(c.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(i, null, Modifier.size(s * 0.58f), tint = c) }

@Composable private fun Card(m: Modifier = Modifier, bg: Color = Color(0xFF15181C), p: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) =
    Column(m.clip(RoundedCornerShape(24.dp)).background(bg).border(1.dp, Line, RoundedCornerShape(24.dp)).padding(p), content = content)

@Composable private fun Header(sup: String, title: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { T(sup, 13, Sub); T(title, 27, w = FontWeight.SemiBold) }
        Chip(Icons.Rounded.AutoAwesome, MindC, 40.dp); Spacer(Modifier.width(8.dp))
        Box(Modifier.size(40.dp).clip(CircleShape).background(Accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { T("B", 16, Accent, FontWeight.SemiBold) }
    }
}

/** Anneau de score, posé sur des cercles pointillés concentriques qui donnent l'échelle sans rien animer. */
@Composable private fun ScoreRing(v: Float, c: Color, size: Dp, center: @Composable BoxScope.() -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 6.dp.toPx()
            listOf(1f, 0.92f).forEach { k ->
                drawCircle(Color.White.copy(alpha = 0.13f), this.size.minDimension / 2 * k, style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 7.dp.toPx()))))
            }
            val inset = this.size.minDimension * 0.15f
            val s = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(Color.White.copy(alpha = 0.10f), 0f, 360f, false, Offset(inset, inset), s, style = Stroke(w))
            drawArc(c, -90f, 360f * v, false, Offset(inset, inset), s, style = Stroke(w, cap = StrokeCap.Round))
        }
        center()
    }
}

@Composable private fun Curve(pts: List<Float>, c: Color, m: Modifier, bars: Boolean = false) {
    Canvas(m) {
        val max = pts.max(); val min = if (bars) 0f else pts.min()
        fun x(i: Int) = size.width * i / (pts.size - 1)
        fun y(v: Float) = size.height - (v - min) / (max - min).coerceAtLeast(0.01f) * size.height * 0.92f
        if (bars) pts.forEachIndexed { i, v -> drawLine(c, Offset(x(i), size.height), Offset(x(i), y(v)), 3.dp.toPx(), StrokeCap.Round) }
        else {
            val p = Path().apply { pts.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
            val fill = Path().apply { addPath(p); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            drawPath(fill, Brush.verticalGradient(listOf(c.copy(alpha = 0.28f), c.copy(alpha = 0f))))
            drawPath(p, c, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable private fun Axis(vararg l: String) = Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { l.forEach { T(it, 11, Sub) } }

/** Carte de mesure : symbole et nom, valeur, courbe de la journée avec son axe. */
@Composable private fun Metric(m: Modifier, i: ImageVector, name: String, value: String, unit: String, c: Color, pts: List<Float>, bars: Boolean = false, warm: Boolean = false) =
    Card(m, deep(c, warm), 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) { Chip(i, c, 26.dp); Spacer(Modifier.width(7.dp)); T(name, 13, w = FontWeight.Medium) }
        Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.Bottom) { T(value, 22, w = FontWeight.SemiBold); T(" $unit", 12, Sub, m = Modifier.padding(bottom = 3.dp)) }
        Curve(pts, c, Modifier.fillMaxWidth().height(44.dp).padding(top = 8.dp), bars)
        Axis("0 h", "12 h", "24 h")
    }

private val hr = listOf(62f, 58f, 56f, 60f, 74f, 92f, 81f, 76f, 118f, 96f, 78f, 70f, 66f)
private val st = listOf(0f, 0f, 2f, 9f, 3f, 6f, 12f, 4f, 2f, 15f, 7f, 3f, 1f)
private val sl = listOf(3f, 5f, 6f, 4f, 7f, 6f, 3f, 5f, 8f, 6f, 4f, 2f)
private val lo = listOf(40f, 44f, 42f, 50f, 58f, 55f, 61f, 66f, 60f, 64f, 70f, 68f)

/** Accueil : un score, un conseil qu'on note, puis les mesures. */
@Composable private fun ColumnScope.Home() {
    Header("Samedi 31 octobre", "Bonjour Bad")
    Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Card(Modifier.fillMaxWidth(), deep(Accent), 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("Forme du jour", 13, Sub); T("Bonne", 25, w = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column { T("Sommeil", 11, Sub); T("7 h 42", 15, w = FontWeight.Medium) }
                        Column { T("Repos", 11, Sub); T("52 bpm", 15, w = FontWeight.Medium) }
                        Column { T("VFC", 11, Sub); T("68 ms", 15, w = FontWeight.Medium) }
                    }
                }
                ScoreRing(0.78f, Accent, 104.dp) { T("78", 26, w = FontWeight.SemiBold) }
            }
        }
        Card(Modifier.fillMaxWidth(), p = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) { Chip(Icons.Rounded.AutoAwesome, MindC, 26.dp); Spacer(Modifier.width(7.dp)); T("Le mot du coach", 13, w = FontWeight.Medium, m = Modifier.weight(1f)); T("Lire", 13, Accent, FontWeight.Medium) }
            T("Belle nuit et charge stable : la séance Jambes passe bien aujourd'hui, garde 2 répétitions de marge.", 14, m = Modifier.padding(top = 6.dp), lines = 2)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { Icon(Icons.Rounded.ThumbUp, null, Modifier.size(17.dp), tint = Sub); Icon(Icons.Rounded.ThumbDown, null, Modifier.size(17.dp), tint = Sub) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Metric(Modifier.weight(1f), Icons.Rounded.Favorite, "Cœur", "68", "bpm", HeartC, hr, warm = true)
            Metric(Modifier.weight(1f), Icons.Rounded.Bedtime, "Sommeil", "7 h 42", "", SleepC, sl, bars = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Metric(Modifier.weight(1f), Icons.AutoMirrored.Rounded.DirectionsRun, "Pas", "8 214", "", StepsC, st, bars = true)
            Metric(Modifier.weight(1f), Icons.Rounded.LocalFireDepartment, "Calories", "1 240", "restantes", FoodC, lo)
        }
    }
    Nav(0)
}

/** Détail d'une mesure : la plage, la courbe, trois repères, et ce que ça veut dire. */
@Composable private fun ColumnScope.Detail() {
    Header("Mesure", "Cœur au repos")
    Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(Color(0xFF15181C)).padding(4.dp)) {
            listOf("Jour", "Semaine", "Mois", "Année").forEachIndexed { i, s ->
                Box(Modifier.weight(1f).clip(CircleShape).background(if (i == 1) Accent.copy(alpha = 0.2f) else Color.Transparent).padding(vertical = 8.dp), contentAlignment = Alignment.Center) { T(s, 13, if (i == 1) Accent else Sub, FontWeight.Medium) }
            }
        }
        Card(Modifier.fillMaxWidth(), deep(LoadC)) {
            Row(verticalAlignment = Alignment.Bottom) { T("52", 40, w = FontWeight.SemiBold); T(" bpm", 15, Sub, m = Modifier.padding(bottom = 7.dp)); Spacer(Modifier.weight(1f)); T("−2 vs habitude", 13, Accent, FontWeight.Medium, Modifier.padding(bottom = 8.dp)) }
            Curve(listOf(55f, 54f, 56f, 54f, 53f, 53f, 52f), LoadC, Modifier.fillMaxWidth().height(170.dp).padding(top = 12.dp))
            Axis("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf("Moyenne" to "53", "Plus bas" to "52", "Habitude" to "54–58").forEach { (k, v) ->
                Card(Modifier.weight(1f), p = 12.dp) { T(k, 12, Sub); T(v, 19, w = FontWeight.SemiBold, m = Modifier.padding(top = 3.dp)) }
            }
        }
        Card(Modifier.fillMaxWidth(), p = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) { Chip(Icons.Rounded.AutoAwesome, MindC, 26.dp); Spacer(Modifier.width(7.dp)); T("Ce que ça veut dire", 13, w = FontWeight.Medium) }
            T("Sous ta plage habituelle depuis trois jours, en même temps que ton sommeil s'allonge : tu récupères bien.", 14, m = Modifier.padding(top = 6.dp), lines = 3)
        }
    }
}

/** Activité en direct : le chiffre utile au centre, le reste en grille à filets, la pause toujours sous le pouce. */
@Composable private fun ColumnScope.Live() {
    Box(Modifier.weight(1f).fillMaxWidth()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            T("Course", 16, w = FontWeight.Medium, m = Modifier.padding(top = 14.dp))
            Spacer(Modifier.height(14.dp))
            ScoreRing(0.62f, LoadC, 250.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { T("ZONE 3", 13, Sub); T("148", 54, w = FontWeight.SemiBold); T("bpm", 14, Sub) }
            }
            Spacer(Modifier.height(18.dp))
            val rows = listOf(listOf("Distance" to "6,42 km", "Allure" to "5:18 /km"), listOf("Durée" to "34:02", "Calories" to "412 kcal"), listOf("Pas" to "5 906", "Cadence" to "174 /min"))
            rows.forEach { r ->
                Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 18.dp).background(Line))
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                    r.forEach { (k, v) -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { T(k, 12, Sub); T(v, 22, w = FontWeight.SemiBold, m = Modifier.padding(top = 2.dp)) } }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).clip(CircleShape).background(Color.White).padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Pause, null, Modifier.size(24.dp), tint = Color.Black); Spacer(Modifier.width(8.dp)); T("34:02", 16, Color.Black, FontWeight.SemiBold)
        }
    }
}

/** Classement : le défi de la semaine, puis les amis, avec la partie où chacun gagne ses points. */
@Composable private fun ColumnScope.Board() {
    Header("Semaine 44", "Classement")
    Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Card(Modifier.fillMaxWidth(), deep(LoadC)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("Ta place", 13, Sub); T("2e sur 5", 25, w = FontWeight.SemiBold)
                    T("41 points derrière Léa", 13, Sub, m = Modifier.padding(top = 3.dp))
                    T("Une séance de plus te fait passer devant", 13, Accent, m = Modifier.padding(top = 6.dp), lines = 2)
                }
                ScoreRing(0.71f, LoadC, 96.dp) { T("712", 22, w = FontWeight.SemiBold) }
            }
        }
        Card(Modifier.fillMaxWidth(), p = 4.dp) {
            listOf(Triple("Léa", 753, "Sommeil"), Triple("Toi", 712, "Musculation"), Triple("Sam", 644, "Pas"), Triple("Noa", 590, "Cardio"), Triple("Ines", 512, "Nutrition")).forEachIndexed { i, (n, s, best) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Row(Modifier.fillMaxWidth().background(if (n == "Toi") Accent.copy(alpha = 0.08f) else Color.Transparent).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("${i + 1}", 15, if (i == 0) Accent else Sub, FontWeight.SemiBold, Modifier.width(22.dp))
                    Box(Modifier.size(36.dp).clip(CircleShape).background(LoadC.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { T(n.take(1), 15, LoadC, FontWeight.SemiBold) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { T(n, 15, w = FontWeight.Medium); T("Fort en $best".lowercase().replaceFirstChar { it.uppercase() }, 12, Sub) }
                    T("$s", 17, w = FontWeight.SemiBold)
                }
            }
        }
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(Accent).padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EmojiEvents, null, Modifier.size(18.dp), tint = Color.Black); Spacer(Modifier.width(6.dp)); T("Partager ma semaine", 15, Color.Black, FontWeight.SemiBold)
        }
    }
    Nav(2)
}

/** Barre d'onglets : celle que tu as validée (à parts égales, avec les noms, + à côté), sur le fond noir. */
@Composable private fun Nav(sel: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).height(66.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF15181C)).border(1.dp, Line, RoundedCornerShape(26.dp)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(Icons.Rounded.GridView to "Accueil", Icons.Rounded.RestaurantMenu to "Journal", Icons.AutoMirrored.Rounded.DirectionsRun to "Activités").forEachIndexed { i, (ic, l) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.clip(CircleShape).background(if (i == sel) Accent.copy(alpha = 0.2f) else Color.Transparent).padding(horizontal = 14.dp, vertical = 3.dp)) { Icon(ic, null, Modifier.size(21.dp), tint = if (i == sel) Accent else Sub) }
                    T(l, 12, if (i == sel) Color.White else Sub, if (i == sel) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(Accent), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, Modifier.size(28.dp), tint = Color.Black) }
    }
}
