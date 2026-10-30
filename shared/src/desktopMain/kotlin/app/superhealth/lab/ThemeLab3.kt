package app.superhealth.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.ui.theme.SansDisplay
import app.superhealth.ui.theme.SansText
import kotlin.math.PI
import kotlin.math.sin

/*
 * Labo de thèmes, tour 3 : trois directions qui changent aussi l'organisation (navigation, recherche) — Pixel (Material 3
 * Expressive clair, recherche en haut), Bulles (organique sur quasi-noir, recherche au centre du dock), Fil (sans onglets :
 * la journée en chronologie, barre « Noter… » en bas).
 */

@Composable fun S(text: String, size: TextUnit = 15.sp, color: Color, weight: FontWeight = FontWeight.Normal, display: Boolean = false,
                  modifier: Modifier = Modifier, align: TextAlign? = null, spacing: TextUnit = 0.sp, lines: Int = Int.MAX_VALUE) =
    Text(text, modifier, maxLines = lines, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = if (display) SansDisplay else SansText,
        fontSize = size, color = color, fontWeight = weight, letterSpacing = spacing, textAlign = align ?: TextAlign.Unspecified,
        fontFeatureSettings = "tnum"))

@Composable
fun BodyIn(side: String, target: Set<String>, hue: Color, base: Color, muscle: Color, from: Float, to: Float, modifier: Modifier) {
    val art = androidx.compose.runtime.remember(side) { body(side) }
    Canvas(modifier) {
        val y0 = art.h * from; val hh = art.h * (to - from)
        val s = minOf(size.width / art.w, size.height / hh)
        val dx = (size.width - art.w * s) / 2; val dy = (size.height - hh * s) / 2
        clipRect {
            translate(dx, dy - y0 * s) {
                scale(s, s, pivot = Offset.Zero) {
                    art.parts.forEach { (id, p) -> drawPath(p, when { id == "body" || id == "bone" -> base; id in target -> hue; else -> muscle }) }
                }
            }
        }
    }
}

/** Indicateur ondulé de Material 3 Expressive. */
@Composable
fun Wavy(f: Float, color: Color, track: Color, modifier: Modifier, amp: Dp = 3.dp, stroke: Dp = 4.dp) {
    Canvas(modifier.height(amp * 2 + stroke)) {
        val a = amp.toPx(); val st = stroke.toPx(); val cy = size.height / 2; val end = size.width * f
        val wave = Path()
        var x = st / 2
        wave.moveTo(x, cy)
        while (x <= end) { wave.lineTo(x, cy + a * sin(x / (10.dp.toPx()) * PI).toFloat()); x += 1.5f }
        drawPath(wave, color, style = Stroke(st, cap = StrokeCap.Round))
        if (end + 8.dp.toPx() < size.width) drawLine(track, Offset(end + 8.dp.toPx(), cy), Offset(size.width - st / 2, cy), st, cap = StrokeCap.Round)
        drawCircle(color, st / 2, Offset(size.width - st / 2, cy))
    }
}

private val program = listOf(
    Day("Push", "Pecs · Épaules", "front", setOf("chest", "frontDelts", "sideDelts"), 0.1f, 0.5f, done = true, next = false),
    Day("Pull", "Dos · Biceps", "back", setOf("lats", "upperBack", "rearDelts", "upperTraps"), 0.1f, 0.5f, done = true, next = false),
    Day("Jambes", "Cuisses · Mollets", "front", setOf("quads", "adductors", "abductors", "calves"), 0.5f, 1f, done = false, next = true),
    Day("Full body", "Tout le corps", "front", setOf("chest", "abs", "quads", "biceps", "frontDelts", "obliques"), 0f, 1f, done = false, next = false),
)

private val dayMeals = listOf(
    Triple("Petit-déjeuner", "8:10", listOf(Food("Œufs brouillés", "2 œufs", 182, "foods/pack/070.png"), Food("Pain complet", "60 g", 148, "foods/pack/083.png"),
        Food("Banane", "1 moyenne", 105, "foods/pack/017.png"))),
    Triple("Déjeuner", "12:40", listOf(Food("Poulet rôti", "150 g", 285, "foods/pack/076.png"), Food("Riz basmati", "180 g", 230, "foods/pack/139.png"),
        Food("Brocolis", "120 g", 41, "foods/pack/058.png"), Food("Avocat", "½", 120, "foods/pack/036.png"))),
    Triple("Collation", "16:30", listOf(Food("Yaourt grec", "150 g", 146, "foods/gen/i103.png"), Food("Pomme", "1 moyenne", 95, "foods/pack/022.png"))),
)

@Composable
private fun Pix(path: String, size: Dp) { image(path)?.let { Image(it, null, Modifier.size(size), filterQuality = FilterQuality.None) } }

// =====================================================================================================================
// A. Pixel — Material 3 Expressive, clair, tonal
// =====================================================================================================================

private object P {
    val surface = Color(0xFFF9F9FF); val c1 = Color(0xFFF1F1F9); val c2 = Color(0xFFE9E9F2); val c3 = Color(0xFFE2E2EC)
    val on = Color(0xFF1A1B21); val onV = Color(0xFF45464F); val outline = Color(0xFF8F909A)
    val primary = Color(0xFF3B5BA9); val pc = Color(0xFFDAE2FF); val onPc = Color(0xFF0F2F6E)
    val tertiary = Color(0xFF8A3F6B); val tc = Color(0xFFFFD8EA); val onTc = Color(0xFF3B0729)
    val sc = Color(0xFFDDE1F9); val green = Color(0xFF2E7D4F); val gc = Color(0xFFBDF0CF)
    val orange = Color(0xFFB4541F); val oc = Color(0xFFFFDBC9); val violet = Color(0xFF6750A4); val vc = Color(0xFFEADDFF)
    val teal = Color(0xFF006A6A); val tealc = Color(0xFFB1F0EF)
}

/** Groupe « connecté » d'Android 16 : grands coins aux extrémités, petits coins entre les éléments. */
@Composable
private fun PGroup(items: Int, content: @Composable (Int, Shape) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until items) {
            val top = if (i == 0) 26.dp else 6.dp; val bottom = if (i == items - 1) 26.dp else 6.dp
            content(i, RoundedCornerShape(top, top, bottom, bottom))
        }
    }
}

@Composable
private fun PScreen(tab: Int, hint: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(P.surface)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(top = 46.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // barre de recherche du haut, contextuelle ; l'avatar ouvre les réglages
            Row(Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(P.c3).padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, Modifier.size(24.dp), tint = P.onV); Spacer(Modifier.width(14.dp))
                S(hint, 16.sp, P.onV, modifier = Modifier.weight(1f), lines = 1)
                Icon(Icons.Rounded.Mic, null, Modifier.size(22.dp), tint = P.onV); Spacer(Modifier.width(12.dp))
                Box(Modifier.size(40.dp).clip(CircleShape).background(P.tc), contentAlignment = Alignment.Center) { S("B", 17.sp, P.onTc, FontWeight.SemiBold) }
            }
            content()
        }
        // barre d'outils flottante + bouton d'action en bas à droite (sous le pouce)
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.shadow(6.dp, CircleShape).clip(CircleShape).background(P.c2).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Icons.Rounded.Today to "Aujourd'hui", Icons.Rounded.Restaurant to "Nutrition", Icons.AutoMirrored.Rounded.DirectionsRun to "Activités").forEachIndexed { i, (ic, l) ->
                    val on = i == tab
                    Row(Modifier.clip(CircleShape).background(if (on) P.primary else Color.Transparent).padding(horizontal = if (on) 16.dp else 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(ic, null, Modifier.size(24.dp), tint = if (on) Color.White else P.onV)
                        if (on) { Spacer(Modifier.width(8.dp)); S(l, 15.sp, Color.White, FontWeight.SemiBold) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(64.dp).shadow(8.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(P.pc), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, null, Modifier.size(30.dp), tint = P.onPc)
            }
        }
    }
}

@Composable
fun PixelHome() = PScreen(0, "Rechercher") {
    Column(Modifier.padding(start = 6.dp, top = 6.dp)) {
        S("Vendredi 30 octobre", 14.sp, P.onV, FontWeight.Medium)
        S("Bonjour B", 34.sp, P.on, FontWeight.Medium, display = true)
    }
    // Forme : biscuit festonné + trois indicateurs ondulés
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(P.gc.copy(alpha = 0.55f)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(118.dp).clip(cookie(9, 0.07f)).background(P.green), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                S("78", 44.sp, Color.White, FontWeight.Medium, display = true); S("Forme", 13.sp, Color.White.copy(alpha = 0.85f), FontWeight.Medium)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            S("Prêt à pousser", 18.sp, P.on, FontWeight.SemiBold, display = true)
            listOf(Triple("Charge", 64, P.primary), Triple("Repos", 84, P.violet), Triple("Effort", 42, P.orange)).forEach { (n, v, c) ->
                Column {
                    Row { S(n, 13.sp, P.onV, modifier = Modifier.weight(1f)); S("$v", 13.sp, P.on, FontWeight.SemiBold) }
                    Wavy(v / 100f, c, c.copy(alpha = 0.25f), Modifier.fillMaxWidth())
                }
            }
        }
    }
    // groupe connecté : calories, sommeil, pas, cœur
    PGroup(3) { i, shape ->
        when (i) {
            0 -> Row(Modifier.fillMaxWidth().clip(shape).background(P.c1).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(cookie(8, 0.07f)).background(P.tealc), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LocalFireDepartment, null, tint = P.teal) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { S("927 kcal restantes", 18.sp, P.on, FontWeight.Medium, display = true); S("P 98 · L 52 · G 141 g", 13.sp, P.onV) }
                ArcMini(1353f / 2280, P.teal, P.tealc)
            }
            1 -> Row(Modifier.fillMaxWidth().clip(shape).background(P.c1).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(cookie(8, 0.07f)).background(P.vc), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bedtime, null, tint = P.violet) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { S("7 h 32 de sommeil", 18.sp, P.on, FontWeight.Medium, display = true); S("Score 84 · couché 23:48", 13.sp, P.onV) }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = P.outline)
            }
            else -> Row(Modifier.fillMaxWidth().clip(shape).background(P.c1).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Triple(Icons.AutoMirrored.Rounded.DirectionsRun, "6 240", "pas"), Triple(Icons.Rounded.Favorite, "54", "bpm repos"), Triple(Icons.Rounded.MonitorWeight, "81,2", "kg")).forEach { (ic, v, l) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(P.surface).padding(12.dp)) {
                        Icon(ic, null, Modifier.size(20.dp), tint = P.primary); Spacer(Modifier.height(6.dp))
                        S(v, 20.sp, P.on, FontWeight.Medium, display = true); S(l, 12.sp, P.onV)
                    }
                }
            }
        }
    }
    // le coach, intégré en suggestion
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(P.tc.copy(alpha = 0.6f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(22.dp), tint = P.tertiary); Spacer(Modifier.width(12.dp))
        S("Bonne nuit, charge correcte : séance Jambes conseillée aujourd'hui.", 14.sp, P.onTc, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ArcMini(f: Float, c: Color, track: Color) {
    Canvas(Modifier.size(40.dp)) {
        val s = 6.dp.toPx(); val i = s / 2
        drawArc(track, 0f, 360f, false, Offset(i, i), Size(size.width - s, size.height - s), style = Stroke(s))
        drawArc(c, -90f, 360f * f, false, Offset(i, i), Size(size.width - s, size.height - s), style = Stroke(s, cap = StrokeCap.Round))
    }
}

@Composable
fun PixelJournal() = PScreen(1, "Rechercher un aliment") {
    // jours en puces
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("mar. 27", "mer. 28", "jeu. 29", "Aujourd'hui").forEachIndexed { i, d ->
            val on = i == 4
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) P.sc else Color.Transparent).border(1.dp, if (on) Color.Transparent else P.c3, RoundedCornerShape(12.dp))
                .padding(horizontal = 11.dp, vertical = 8.dp)) { S(d, 13.sp, if (on) P.on else P.onV, FontWeight.Medium) }
        }
    }
    // résumé compact (validé) : une rangée
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(P.tealc.copy(alpha = 0.5f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        ArcMini(1353f / 2280, P.teal, Color.White)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(96.dp)) { S("927", 24.sp, P.on, FontWeight.Medium, display = true); S("kcal restantes", 11.sp, P.onV) }
        listOf(Triple("P", 98 to 150, Color(0xFFBA1A1A)), Triple("L", 52 to 76, Color(0xFF8B6B00)), Triple("G", 141 to 285, P.green)).forEach { (n, v, c) ->
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                S("$n ${v.first}", 13.sp, P.on, FontWeight.SemiBold); Wavy(v.first.toFloat() / v.second, c, c.copy(alpha = 0.2f), Modifier.fillMaxWidth(), 2.dp, 3.dp)
            }
        }
    }
    dayMeals.forEach { (name, time, foods) ->
        Row(Modifier.padding(start = 8.dp, top = 4.dp), verticalAlignment = Alignment.Bottom) {
            S(name, 15.sp, P.primary, FontWeight.SemiBold, modifier = Modifier.weight(1f)); S("$time · ${foods.sumOf { it.kcal }} kcal", 13.sp, P.onV)
        }
        PGroup(foods.size) { i, shape ->
            val f = foods[i]
            Row(Modifier.fillMaxWidth().clip(shape).background(P.c1).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(P.surface), contentAlignment = Alignment.Center) { Pix(f.icon, 28.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { S(f.name, 15.sp, P.on); S(f.qty, 12.sp, P.onV) }
                S("${f.kcal}", 14.sp, P.onV, FontWeight.Medium)
            }
        }
    }
}

@Composable
fun PixelActivities() = PScreen(2, "Rechercher un exercice") {
    // semaine en une ligne
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { S("Cette semaine", 13.sp, P.onV); S("4 activités", 17.sp, P.on, FontWeight.Medium, display = true, lines = 1) }
        WeekDots(P.primary, P.c3, P.onV, Color.White)
    }
    // programme compact
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(P.pc.copy(alpha = 0.55f)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { S("Push Pull Legs", 20.sp, P.on, FontWeight.Medium, display = true); S("Semaine 3 · 2 séances sur 4 faites", 13.sp, P.onV) }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = P.onV)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            program.forEach { d ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (d.next) P.surface else Color.White.copy(alpha = 0.5f))
                    .then(if (d.next) Modifier.border(2.dp, P.primary, RoundedCornerShape(20.dp)) else Modifier).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(54.dp)) {
                        BodyIn(d.side, d.target, P.primary, Color(0xFFC5C6D0), Color(0xFFE2E2EC), d.from, d.to, Modifier.fillMaxSize())
                        if (d.done) Box(Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(P.green), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = Color.White)
                        }
                    }
                    S(d.name, 13.sp, P.on, FontWeight.SemiBold, lines = 1)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(P.primary).padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White); Spacer(Modifier.width(6.dp)); S("Commencer Jambes", 16.sp, Color.White, FontWeight.SemiBold)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Icons.Rounded.Bolt to "Séance libre", Icons.Rounded.AutoAwesome to "Demander au coach").forEach { (ic, l) ->
            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(P.sc).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(ic, null, Modifier.size(18.dp), tint = P.on); Spacer(Modifier.width(8.dp)); S(l, 14.sp, P.on, FontWeight.Medium)
            }
        }
    }
    S("Récentes", 15.sp, P.primary, FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    PGroup(3) { i, shape ->
        val r = recent[i]
        Row(Modifier.fillMaxWidth().clip(shape).background(P.c1).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(cookie(8, 0.07f)).background(P.oc), contentAlignment = Alignment.Center) { Icon(r.first, null, Modifier.size(22.dp), tint = P.orange) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { S(r.second, 15.sp, P.on); S(r.third.first, 12.sp, P.onV) }
            S(r.third.second, 15.sp, P.on, FontWeight.Medium, display = true)
        }
    }
}

private val recent = listOf(
    Triple(Icons.AutoMirrored.Rounded.DirectionsRun, "Course à pied", "Hier · 28:41" to "5,2 km"),
    Triple(Icons.Rounded.FitnessCenter, "Pull", "Mardi · 52 min" to "12 340 kg"),
    Triple(Icons.AutoMirrored.Rounded.DirectionsBike, "Vélo", "Lundi · 48 min" to "21,0 km"),
)

@Composable
private fun WeekDots(on: Color, off: Color, label: Color, icon: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val acts = listOf(Icons.AutoMirrored.Rounded.DirectionsRun, Icons.Rounded.FitnessCenter, null, Icons.AutoMirrored.Rounded.DirectionsBike, Icons.Rounded.FitnessCenter, null, null)
        listOf("L", "M", "M", "J", "V", "S", "D").forEachIndexed { i, d ->
            Box(Modifier.size(26.dp).clip(CircleShape).background(if (acts[i] != null) on else off), contentAlignment = Alignment.Center) {
                acts[i]?.let { Icon(it, null, Modifier.size(15.dp), tint = icon) } ?: S(d, 11.sp, label, FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun PixelSearch() = PScreen(-1, "squat") {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Tout", "Aliments", "Exercices", "Mesures").forEachIndexed { i, c ->
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (i == 0) P.sc else Color.Transparent).border(1.dp, if (i == 0) Color.Transparent else P.c3, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp)) { S(c, 13.sp, P.on, FontWeight.Medium) }
        }
    }
    S("Exercices", 15.sp, P.primary, FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    val ex = listOf(Triple("Squat barre", "Record 105 kg × 5 · dernier mardi", "ex/1a25c6f170d8803d8231d083fdd65458_a.webp"),
        Triple("Fente bulgare", "Dans Jambes · 3 × 10", "ex/1a25c6f170d880d8bb7fef2de64a1e60_a.webp"),
        Triple("Presse à 45°", "Dans Jambes · 3 × 12", "ex/1a25c6f170d88079a926dde776766181_a.webp"))
    PGroup(ex.size) { i, shape ->
        Row(Modifier.fillMaxWidth().clip(shape).background(P.c1).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)) {
                image(ex[i].third)?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { S(ex[i].first, 15.sp, P.on); S(ex[i].second, 12.sp, P.onV) }
        }
    }
    S("Mesures et graphiques", 15.sp, P.primary, FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    PGroup(2) { i, shape ->
        Row(Modifier.fillMaxWidth().clip(shape).background(P.c1).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (i == 0) Icons.Rounded.FitnessCenter else Icons.Rounded.History, null, tint = P.primary); Spacer(Modifier.width(14.dp))
            S(if (i == 0) "Progression du squat (1RM estimé)" else "Historique des séances Jambes", 15.sp, P.on)
        }
    }
    S("Aliments", 15.sp, P.primary, FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    PGroup(1) { _, shape ->
        Row(Modifier.fillMaxWidth().clip(shape).background(P.c1).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = P.onV); Spacer(Modifier.width(14.dp)); S("Aucun aliment « squat »", 15.sp, P.onV)
        }
    }
}

// =====================================================================================================================
// B. Bulles — organique sur quasi-noir
// =====================================================================================================================

private object B {
    val bg = Color(0xFF0E0F13); val card = Color(0xFF181A20); val ink = Color(0xFFF3F4F8); val sub = Color(0xFF9BA0AD); val faint = Color(0xFF5A5F6B)
    val green = Color(0xFF4ADE80); val blue = Color(0xFF60A5FA); val violet = Color(0xFFA78BFA); val orange = Color(0xFFFB923C)
    val pink = Color(0xFFF472B6); val red = Color(0xFFF87171); val cyan = Color(0xFF22D3EE); val yellow = Color(0xFFFACC15)
}

@Composable
private fun Bubble(d: Dp, hue: Color, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.size(d).clip(CircleShape)
        .background(Brush.radialGradient(listOf(hue.copy(alpha = 0.38f), hue.copy(alpha = 0.16f)))), contentAlignment = Alignment.Center, content = content)
}

@Composable
private fun BScreen(tab: Int, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(B.bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 52.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        // dock : quatre bulles et la recherche au centre
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).clip(CircleShape).background(Color(0xFF1C1E25)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Icons.Rounded.Spa, Icons.Rounded.Restaurant, null, Icons.AutoMirrored.Rounded.DirectionsRun, Icons.Rounded.Add).forEachIndexed { i, ic ->
                if (ic == null) Box(Modifier.size(62.dp).clip(CircleShape).background(Brush.linearGradient(listOf(B.violet, B.blue))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(30.dp), tint = Color.White)
                } else {
                    val on = (i == 0 && tab == 0) || (i == 1 && tab == 1) || (i == 3 && tab == 2)
                    Box(Modifier.size(50.dp).clip(CircleShape).background(if (on) Color(0xFF2C2F3A) else Color.Transparent), contentAlignment = Alignment.Center) {
                        Icon(ic, null, Modifier.size(25.dp), tint = if (on) B.ink else B.faint)
                    }
                }
            }
        }
    }
}

@Composable
private fun BHead(over: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { S(over, 14.sp, B.sub, FontWeight.Medium); S(title, 32.sp, B.ink, FontWeight.Medium, display = true) }
        Box(Modifier.size(42.dp).clip(CircleShape).background(B.card), contentAlignment = Alignment.Center) { S("B", 17.sp, B.ink, FontWeight.SemiBold) }
    }
}

@Composable
fun BullesHome() = BScreen(0) {
    BHead("Vendredi 30 octobre", "Aujourd'hui")
    // grappe de bulles : la taille suit l'importance
    Box(Modifier.fillMaxWidth().height(330.dp)) {
        Bubble(178.dp, B.green, Modifier.offset(8.dp, 6.dp)) {
            Canvas(Modifier.size(158.dp)) {
                val s = 10.dp.toPx(); val i = s / 2
                drawArc(B.green.copy(alpha = 0.18f), 0f, 360f, false, Offset(i, i), Size(size.width - s, size.height - s), style = Stroke(s))
                drawArc(B.green, -90f, 281f, false, Offset(i, i), Size(size.width - s, size.height - s), style = Stroke(s, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { S("78", 54.sp, B.ink, FontWeight.Medium, display = true); S("Forme · prêt", 13.sp, B.green, FontWeight.Medium) }
        }
        Bubble(128.dp, B.cyan, Modifier.offset(200.dp, 0.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(18.dp), tint = B.cyan); S("927", 30.sp, B.ink, FontWeight.Medium, display = true); S("kcal restantes", 11.sp, B.sub) }
        }
        Bubble(112.dp, B.violet, Modifier.offset(232.dp, 132.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.Bedtime, null, Modifier.size(16.dp), tint = B.violet); S("7 h 32", 22.sp, B.ink, FontWeight.Medium, display = true); S("sommeil", 11.sp, B.sub) }
        }
        Bubble(92.dp, B.pink, Modifier.offset(130.dp, 194.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { S("6 240", 19.sp, B.ink, FontWeight.Medium, display = true); S("pas", 11.sp, B.sub) }
        }
        Bubble(76.dp, B.red, Modifier.offset(40.dp, 196.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { S("54", 20.sp, B.ink, FontWeight.Medium, display = true); S("bpm", 10.sp, B.sub) }
        }
        Bubble(62.dp, B.blue, Modifier.offset(0.dp, 270.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { S("64", 17.sp, B.ink, FontWeight.Medium, display = true); S("charge", 9.sp, B.sub) } }
        Bubble(56.dp, B.orange, Modifier.offset(232.dp, 252.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { S("42", 16.sp, B.ink, FontWeight.Medium, display = true); S("effort", 9.sp, B.sub) } }
    }
    // la journée en perles sur un fil
    S("Ta journée", 17.sp, B.ink, FontWeight.Medium, display = true)
    Box(Modifier.fillMaxWidth().height(86.dp)) {
        Canvas(Modifier.fillMaxWidth().height(40.dp)) { drawLine(Color(0xFF2A2D36), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx()) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(Triple("7:20", Icons.Rounded.WbSunny, B.yellow), Triple("8:10", Icons.Rounded.Restaurant, B.cyan), Triple("12:40", Icons.Rounded.Restaurant, B.cyan),
                Triple("16:30", Icons.Rounded.Restaurant, B.cyan), Triple("18:00", Icons.Rounded.FitnessCenter, B.blue)).forEachIndexed { i, (t, ic, c) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(if (i == 4) B.bg else c.copy(alpha = 0.25f)).then(if (i == 4) Modifier.border(2.dp, c, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center) { Icon(ic, null, Modifier.size(20.dp), tint = c) }
                    S(if (i == 4) "Jambes" else t, 11.sp, if (i == 4) B.ink else B.sub, FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun BullesJournal() = BScreen(1) {
    BHead("Vendredi 30 octobre", "Nutrition")
    // résumé compact en bulles
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(B.card).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Bubble(66.dp, B.cyan) { Column(horizontalAlignment = Alignment.CenterHorizontally) { S("927", 18.sp, B.ink, FontWeight.Medium, display = true); S("kcal", 9.sp, B.sub) } }
        Spacer(Modifier.width(10.dp))
        listOf(Triple("98", "/150 P", B.red), Triple("52", "/76 L", B.yellow), Triple("141", "/285 G", B.green)).forEach { (v, l, c) ->
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(6.dp))
                Column { S(v, 17.sp, B.ink, FontWeight.Medium, display = true); S(l, 10.sp, B.sub) }
            }
        }
    }
    // repas : une rangée de perles par repas
    dayMeals.forEach { (name, time, foods) ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(B.card).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                S(name, 17.sp, B.ink, FontWeight.Medium, display = true, modifier = Modifier.weight(1f)); S("$time · ${foods.sumOf { it.kcal }} kcal", 13.sp, B.sub)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                foods.forEach { f ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(62.dp)) {
                        Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF242731)), contentAlignment = Alignment.Center) { Pix(f.icon, 34.dp) }
                        S(f.name.substringBefore(' '), 11.sp, B.sub, lines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Box(Modifier.size(54.dp).clip(CircleShape).border(1.5.dp, Color(0xFF2E313B), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, tint = B.sub)
                }
            }
        }
    }
}

@Composable
fun BullesActivities() = BScreen(2) {
    BHead("Semaine 44", "Activités")
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(B.card).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { S("4 activités", 15.sp, B.ink, FontWeight.Medium); S("3 h 10 · 18,4 km", 12.sp, B.sub) }
        WeekDots(B.blue, Color(0xFF262932), B.faint, B.bg)
    }
    // programme : quatre bulles, la prochaine entourée
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(34.dp)).background(B.card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { S("Push Pull Legs", 19.sp, B.ink, FontWeight.Medium, display = true); S("Semaine 3 · 2 sur 4", 12.sp, B.sub) }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = B.faint)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            program.forEach { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(70.dp).clip(CircleShape).background(Color(0xFF22252E))
                        .then(if (d.next) Modifier.border(3.dp, B.green, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                        BodyIn(d.side, d.target, if (d.done) B.faint else B.green, Color(0xFF3A3E4A), Color(0xFF2E323C), d.from, d.to, Modifier.size(54.dp))
                    }
                    S(d.name, 12.sp, if (d.done) B.faint else B.ink, FontWeight.Medium, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(B.green).padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PlayArrow, null, tint = B.bg); Spacer(Modifier.width(6.dp)); S("Jambes", 16.sp, B.bg, FontWeight.SemiBold)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        recent.forEach { r ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(28.dp)).background(B.card).padding(14.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(B.orange.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Icon(r.first, null, Modifier.size(20.dp), tint = B.orange) }
                Spacer(Modifier.height(8.dp)); S(r.third.second, 17.sp, B.ink, FontWeight.Medium, display = true); S(r.second, 12.sp, B.sub, lines = 1)
            }
        }
    }
}

@Composable
fun BullesSearch() = BScreen(-1) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(B.card).padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = B.violet); Spacer(Modifier.width(12.dp)); S("pou", 17.sp, B.ink, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.Close, null, tint = B.sub)
    }
    S("Aliments", 14.sp, B.sub, FontWeight.Medium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("Poulet rôti" to "foods/pack/076.png", "Poulet pané" to "foods/pack/076.png", "Poulpe" to "foods/pack/095.png").forEach { (n, ic) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(B.card), contentAlignment = Alignment.Center) { Pix(ic, 48.dp) }
                S(n, 12.sp, B.ink, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    S("Exercices", 14.sp, B.sub, FontWeight.Medium)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(B.card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White)) { image("ex/1ec5c6f170d880e68b26fbc86bdd4d00_a.webp")?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
        Spacer(Modifier.width(12.dp)); Column { S("Tractions assistées", 15.sp, B.ink); S("Pull · 3 × 8", 12.sp, B.sub) }
    }
    S("Actions", 14.sp, B.sub, FontWeight.Medium)
    listOf(Icons.Rounded.MonitorWeight to "Ajouter une pesée", Icons.Rounded.Settings to "Réglages > Pouls au repos").forEach { (ic, l) ->
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(B.card).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(ic, null, tint = B.blue); Spacer(Modifier.width(12.dp)); S(l, 15.sp, B.ink)
        }
    }
}

// =====================================================================================================================
// C. Fil — sans onglets : sections en haut, chronologie, barre « Noter… » en bas
// =====================================================================================================================

private object F {
    val bg = Color(0xFFF6F6F3); val card = Color.White; val ink = Color(0xFF17181C); val sub = Color(0xFF6B6E76); val faint = Color(0xFFB4B6BC); val line = Color(0xFFE3E3DE)
    val accent = Color(0xFF5B5BD6); val green = Color(0xFF2FA36B); val cyan = Color(0xFF0E9AB0); val violet = Color(0xFF7B61D9); val orange = Color(0xFFE5732E); val pink = Color(0xFFE0457B)
}

@Composable
private fun FScreen(section: Int, typed: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(F.bg)) {
        Column(Modifier.fillMaxSize().padding(top = 50.dp)) {
            // sections en haut, défilantes : plus de barre d'onglets en bas
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("Aujourd'hui", "Manger", "Bouger", "Récup").forEachIndexed { i, s ->
                    Column(Modifier.padding(end = 14.dp)) {
                        S(s, if (i == section) 22.sp else 16.sp, if (i == section) F.ink else F.faint, FontWeight.Medium, display = true, lines = 1)
                        Box(Modifier.padding(top = 4.dp).size(width = if (i == section) 22.dp else 0.dp, height = 4.dp).clip(CircleShape).background(F.accent))
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFE6E5F7)), contentAlignment = Alignment.Center) { S("B", 15.sp, F.accent, FontWeight.SemiBold) }
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
        // barre de commande en bas, sous le pouce : chercher, noter, scanner, dicter
        Row(Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 24.dp).fillMaxWidth()
            .shadow(14.dp, CircleShape, ambientColor = Color(0x33000000)).clip(CircleShape).background(F.card).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(if (typed == null) Icons.Rounded.Add else Icons.Rounded.Search, null, Modifier.size(22.dp), tint = F.accent); Spacer(Modifier.width(10.dp))
            S(typed ?: "Noter un repas, une séance, un poids…", 15.sp, if (typed == null) F.sub else F.ink, modifier = Modifier.weight(1f), lines = 1)
            Box(Modifier.size(44.dp).clip(CircleShape).background(F.bg), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.QrCodeScanner, null, tint = F.ink) }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(44.dp).clip(CircleShape).background(F.accent), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Mic, null, tint = Color.White) }
        }
    }
}

/** Un instant de la journée, sur le fil à gauche. */
@Composable
private fun Moment(time: String, hue: Color, icon: ImageVector, last: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Row {
        Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            S(time, 12.sp, F.sub, FontWeight.Medium)
            Box(Modifier.padding(top = 4.dp).size(30.dp).clip(CircleShape).background(hue.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(17.dp), tint = hue) }
        }
        Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(F.card).padding(14.dp), content = content)
    }
}

@Composable
fun FilHome() = FScreen(0) {
    // l'essentiel en une ligne
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(F.card).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(Triple("78", "Forme", F.green), Triple("927", "kcal", F.cyan), Triple("7 h 32", "sommeil", F.violet), Triple("6 240", "pas", F.pink)).forEach { (v, l, c) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                S(v, 22.sp, c, FontWeight.Medium, display = true); S(l, 11.sp, F.sub)
            }
        }
    }
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.width(54.dp).height(560.dp)) { drawLine(F.line, Offset(size.width / 2, 40f), Offset(size.width / 2, size.height), 2.dp.toPx()) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Moment("7:20", F.violet, Icons.Rounded.Bedtime) {
                S("Nuit de 7 h 32", 16.sp, F.ink, FontWeight.Medium); S("Score 84 · Repos 84 · FC repos 54", 13.sp, F.sub)
            }
            Moment("7:25", F.green, Icons.Rounded.Spa) {
                S("Forme 78 : prêt à pousser", 16.sp, F.ink, FontWeight.Medium); S("Charge 64 · effort d'hier 42", 13.sp, F.sub)
            }
            Moment("8:10", F.cyan, Icons.Rounded.Restaurant) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    S("Petit-déjeuner", 16.sp, F.ink, FontWeight.Medium, modifier = Modifier.weight(1f)); S("435 kcal", 13.sp, F.sub)
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { dayMeals[0].third.forEach { Pix(it.icon, 26.dp) } }
            }
            Moment("12:40", F.cyan, Icons.Rounded.Restaurant) {
                Row(verticalAlignment = Alignment.CenterVertically) { S("Déjeuner", 16.sp, F.ink, FontWeight.Medium, modifier = Modifier.weight(1f)); S("676 kcal", 13.sp, F.sub) }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { dayMeals[1].third.forEach { Pix(it.icon, 26.dp) } }
            }
            Moment("18:00", F.accent, Icons.Rounded.FitnessCenter) {
                S("Prévu : Jambes", 16.sp, F.ink, FontWeight.Medium); S("5 exercices · ~60 min", 13.sp, F.sub)
                Row(Modifier.padding(top = 8.dp).clip(CircleShape).background(F.accent).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp), tint = Color.White); Spacer(Modifier.width(4.dp)); S("Commencer", 14.sp, Color.White, FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun FilJournal() = FScreen(1) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(F.card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        ArcMini(1353f / 2280, F.cyan, Color(0xFFE2F4F7)); Spacer(Modifier.width(12.dp))
        Column(Modifier.width(100.dp)) { S("927", 24.sp, F.ink, FontWeight.Medium, display = true); S("kcal restantes", 11.sp, F.sub) }
        listOf(Triple("P", 98 to 150, Color(0xFFE5484D)), Triple("L", 52 to 76, Color(0xFFE5A000)), Triple("G", 141 to 285, F.green)).forEach { (n, v, c) ->
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                S("$n ${v.first}", 13.sp, F.ink, FontWeight.SemiBold)
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(5.dp).clip(CircleShape).background(c.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(v.first.toFloat() / v.second).fillMaxHeight().clip(CircleShape).background(c))
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.width(54.dp).height(620.dp)) { drawLine(F.line, Offset(size.width / 2, 40f), Offset(size.width / 2, size.height), 2.dp.toPx()) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            dayMeals.forEach { (name, time, foods) ->
                Moment(time, F.cyan, Icons.Rounded.Restaurant) {
                    Row(verticalAlignment = Alignment.CenterVertically) { S(name, 16.sp, F.ink, FontWeight.Medium, modifier = Modifier.weight(1f)); S("${foods.sumOf { it.kcal }} kcal", 13.sp, F.sub) }
                    foods.forEach { f ->
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Pix(f.icon, 26.dp); Spacer(Modifier.width(10.dp)); S(f.name, 14.sp, F.ink, modifier = Modifier.weight(1f)); S("${f.qty} · ${f.kcal}", 12.sp, F.sub)
                        }
                    }
                }
            }
            Moment("19:30", F.faint, Icons.Rounded.Add) { S("Dîner : ≈ 930 kcal à placer", 15.sp, F.sub) }
        }
    }
}

@Composable
fun FilActivities() = FScreen(2) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(F.card).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { S("4 activités", 15.sp, F.ink, FontWeight.Medium); S("3 h 10 · 18,4 km", 12.sp, F.sub) }
        WeekDots(F.accent, Color(0xFFEDEDEA), F.faint, Color.White)
    }
    // programme : une rangée de séances, sans jour imposé
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(F.card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { S("Push Pull Legs", 19.sp, F.ink, FontWeight.Medium, display = true); S("Semaine 3 · 2 séances sur 4", 12.sp, F.sub) }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = F.faint)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            program.forEach { d ->
                Row(Modifier.weight(if (d.next) 2.2f else 1f).height(64.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (d.next) F.accent else F.bg).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BodyIn(d.side, d.target, if (d.next) Color.White else if (d.done) F.faint else F.accent,
                        if (d.next) Color(0x66FFFFFF) else Color(0xFFD6D6D2), if (d.next) Color(0x33FFFFFF) else Color(0xFFE8E8E4), d.from, d.to, Modifier.size(42.dp))
                    if (d.next) Column(Modifier.padding(start = 4.dp)) { S(d.name, 15.sp, Color.White, FontWeight.SemiBold, lines = 1); S("Commencer", 11.sp, Color.White.copy(alpha = 0.8f), lines = 1) }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Icons.Rounded.Bolt to "Séance libre", Icons.Rounded.AutoAwesome to "Coach").forEach { (ic, l) ->
            Row(Modifier.clip(CircleShape).background(F.card).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(ic, null, Modifier.size(18.dp), tint = F.accent); Spacer(Modifier.width(6.dp)); S(l, 14.sp, F.ink, FontWeight.Medium)
            }
        }
    }
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.width(54.dp).height(300.dp)) { drawLine(F.line, Offset(size.width / 2, 40f), Offset(size.width / 2, size.height), 2.dp.toPx()) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Triple("Hier", F.orange, recent[0]), Triple("Mar.", F.accent, recent[1]), Triple("Lun.", F.cyan, recent[2])).forEach { (t, c, r) ->
                Moment(t, c, r.first) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { S(r.second, 16.sp, F.ink, FontWeight.Medium); S(r.third.first, 12.sp, F.sub) }
                        S(r.third.second, 17.sp, F.ink, FontWeight.Medium, display = true)
                    }
                }
            }
        }
    }
}

@Composable
fun FilSearch() = FScreen(0, typed = "80 kg ce matin") {
    // la barre comprend ce qu'on tape : action proposée d'abord
    Spacer(Modifier.weight(1f))
    S("Suggestions", 14.sp, F.sub, FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(F.accent).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.MonitorWeight, null, tint = Color.White); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { S("Noter une pesée : 80,0 kg", 16.sp, Color.White, FontWeight.SemiBold); S("Aujourd'hui · tendance 81,2 kg", 12.sp, Color.White.copy(alpha = 0.8f)) }
        Icon(Icons.Rounded.Check, null, tint = Color.White)
    }
    listOf(Triple(Icons.Rounded.History, "Poids : graphique et tendance", F.sub), Triple(Icons.Rounded.FitnessCenter, "Exercices à 80 kg : soulevé roumain", F.sub),
        Triple(Icons.Rounded.AutoAwesome, "Demander au coach : « 80 kg ce matin »", F.violet)).forEach { (ic, l, c) ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(F.card).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(ic, null, tint = c); Spacer(Modifier.width(12.dp)); S(l, 15.sp, F.ink, lines = 1)
        }
    }
    Spacer(Modifier.height(96.dp))
}
