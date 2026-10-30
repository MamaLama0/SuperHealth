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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Labo de thèmes (maquettes, hors app) : les trois onglets principaux dessinés dans plusieurs directions graphiques, avec les
 * mêmes cartes aux mêmes places pour comparer. Rendu par ThemeLabShots.kt.
 */

enum class Hero { RING, DIALS, BLOB, TYPE }
enum class Nav { FLOATING, FLAT, TONAL, LINE }
enum class Chip { CIRCLE, SQUIRCLE, COOKIE, NONE }

data class Hues(
    val forme: Color, val charge: Color, val repos: Color, val effort: Color,
    val steps: Color, val heart: Color, val sleep: Color, val food: Color,
    val protein: Color, val fat: Color, val carbs: Color,
    val gym: Color, val run: Color, val bike: Color,
)

data class Look(
    val name: String,
    val tagline: String,
    val dark: Boolean,
    val bg: Color,
    val glow: List<Pair<Color, Offset>> = emptyList(),
    val card: Color,
    val border: Color? = null,
    val radius: Dp,
    val shadow: Dp = 0.dp,
    val ink: Color, val sub: Color, val faint: Color, val track: Color,
    val accent: Color, val onAccent: Color,
    val hues: Hues,
    val head: FontFamily, val headWeight: FontWeight, val body: FontFamily, val num: FontFamily, val numWeight: FontWeight,
    val caps: Boolean = false,
    val tinted: Boolean = false,
    val cards: Boolean = true,
    val chip: Chip,
    val hero: Hero,
    val nav: Nav,
    val stroke: Dp = 10.dp,
)

val LocalLook = staticCompositionLocalOf<Look> { error("look") }
private val L @Composable get() = LocalLook.current

// ---- polices ----------------------------------------------------------------------------------------------------

private fun f(path: String, w: FontWeight) = Font(java.io.File(path), w)
private const val INTER = "/usr/share/fonts/rsms-inter-fonts/"
val Inter = FontFamily(f(INTER + "Inter-Regular.ttf", FontWeight.Normal), f(INTER + "Inter-Medium.ttf", FontWeight.Medium),
    f(INTER + "Inter-SemiBold.ttf", FontWeight.SemiBold), f(INTER + "Inter-Bold.ttf", FontWeight.Bold))
val InterDisplay = FontFamily(f(INTER + "InterDisplay-Medium.ttf", FontWeight.Medium), f(INTER + "InterDisplay-SemiBold.ttf", FontWeight.SemiBold),
    f(INTER + "InterDisplay-Bold.ttf", FontWeight.Bold), f(INTER + "InterDisplay-ExtraBold.ttf", FontWeight.ExtraBold),
    f(INTER + "InterDisplay-Black.ttf", FontWeight.Black), f(INTER + "InterDisplay-Light.ttf", FontWeight.Light))
private const val CF = "/usr/share/fonts/aajohan-comfortaa-fonts/"
val Comfortaa = FontFamily(f(CF + "Comfortaa-Regular.otf", FontWeight.Normal), f(CF + "Comfortaa-Medium.otf", FontWeight.Medium),
    f(CF + "Comfortaa-SemiBold.otf", FontWeight.SemiBold), f(CF + "Comfortaa-Bold.otf", FontWeight.Bold))
private const val CA = "/usr/share/fonts/google-crosextra-caladea-fonts/"
val Caladea = FontFamily(f(CA + "Caladea-Regular.ttf", FontWeight.Normal), f(CA + "Caladea-Bold.ttf", FontWeight.Bold))
private const val JB = "/usr/share/fonts/nerd-fonts/JetBrainsMono/JetBrainsMonoNerdFontMono-"
val Mono = FontFamily(f(JB + "Regular.ttf", FontWeight.Normal), f(JB + "Medium.ttf", FontWeight.Medium),
    f(JB + "SemiBold.ttf", FontWeight.SemiBold), f(JB + "Bold.ttf", FontWeight.Bold))

// ---- les thèmes -------------------------------------------------------------------------------------------------

val Aube = Look(
    name = "Aube", tagline = "Clair et aérien : cartes blanches flottantes, halo pastel, grands chiffres",
    dark = false, bg = Color(0xFFF5F4F1),
    glow = listOf(Color(0xFFFFD6E4) to Offset(0.15f, 0.02f), Color(0xFFD7E6FF) to Offset(0.85f, 0.06f), Color(0xFFDFF5E6) to Offset(0.5f, 0.0f)),
    card = Color.White, radius = 28.dp, shadow = 10.dp,
    ink = Color(0xFF15171C), sub = Color(0xFF6B7080), faint = Color(0xFFA5A9B4), track = Color(0xFFEEEFF3),
    accent = Color(0xFFFF4F7B), onAccent = Color.White,
    hues = Hues(forme = Color(0xFF34C77B), charge = Color(0xFF5B8CFF), repos = Color(0xFF8E6CF0), effort = Color(0xFFFF8A3D),
        steps = Color(0xFFFF4F7B), heart = Color(0xFFFF5A5F), sleep = Color(0xFF7C6CF0), food = Color(0xFF26B5C9),
        protein = Color(0xFFFF6B6B), fat = Color(0xFFFFB020), carbs = Color(0xFF3CC48A),
        gym = Color(0xFF5B8CFF), run = Color(0xFFFF7A45), bike = Color(0xFF26B5C9)),
    head = InterDisplay, headWeight = FontWeight.ExtraBold, body = Inter, num = InterDisplay, numWeight = FontWeight.Bold,
    chip = Chip.CIRCLE, hero = Hero.RING, nav = Nav.FLOATING, stroke = 9.dp,
)

val Nocturne = Look(
    name = "Nocturne", tagline = "Noir profond façon WHOOP : trois cadrans, chiffres en chasse fixe, néons",
    dark = true, bg = Color.Black,
    card = Color(0xFF0E1013), border = Color(0xFF1F232A), radius = 14.dp,
    ink = Color(0xFFF2F4F7), sub = Color(0xFF8A919E), faint = Color(0xFF4A505B), track = Color(0xFF1B1F25),
    accent = Color(0xFF00E58F), onAccent = Color.Black,
    hues = Hues(forme = Color(0xFF00E58F), charge = Color(0xFF2EC5FF), repos = Color(0xFF8C7BFF), effort = Color(0xFFFFB627),
        steps = Color(0xFF00E58F), heart = Color(0xFFFF4D6D), sleep = Color(0xFF8C7BFF), food = Color(0xFF2EC5FF),
        protein = Color(0xFFFF4D6D), fat = Color(0xFFFFB627), carbs = Color(0xFF00E58F),
        gym = Color(0xFF2EC5FF), run = Color(0xFFFFB627), bike = Color(0xFF8C7BFF)),
    head = Inter, headWeight = FontWeight.Bold, body = Inter, num = Mono, numWeight = FontWeight.SemiBold,
    caps = true, chip = Chip.NONE, hero = Hero.DIALS, nav = Nav.FLAT, stroke = 6.dp,
)

val Galet = Look(
    name = "Galet", tagline = "Organique et expressif : formes de galet, cartes teintées, typo ronde",
    dark = true, bg = Color(0xFF17151F),
    card = Color(0xFF221F2C), radius = 34.dp,
    ink = Color(0xFFF5F1FF), sub = Color(0xFFB4ADC8), faint = Color(0xFF6E6784), track = Color(0x22FFFFFF),
    accent = Color(0xFFC6B4FF), onAccent = Color(0xFF221A3D),
    hues = Hues(forme = Color(0xFF9EE6B8), charge = Color(0xFF9CC7FF), repos = Color(0xFFC6B4FF), effort = Color(0xFFFFC09A),
        steps = Color(0xFFFFA8C4), heart = Color(0xFFFF9AA2), sleep = Color(0xFFC6B4FF), food = Color(0xFF8FE3E0),
        protein = Color(0xFFFF9AA2), fat = Color(0xFFFFD58A), carbs = Color(0xFF9EE6B8),
        gym = Color(0xFF9CC7FF), run = Color(0xFFFFC09A), bike = Color(0xFF8FE3E0)),
    head = Comfortaa, headWeight = FontWeight.Bold, body = Inter, num = Comfortaa, numWeight = FontWeight.Bold,
    tinted = true, chip = Chip.COOKIE, hero = Hero.BLOB, nav = Nav.TONAL, stroke = 14.dp,
)

val Carnet = Look(
    name = "Carnet", tagline = "Éditorial et calme : papier, titres à empattements, filets plutôt que cartes",
    dark = false, bg = Color(0xFFFAF8F3),
    card = Color(0xFFFAF8F3), border = Color(0xFFE6E1D6), radius = 0.dp,
    ink = Color(0xFF1C1D21), sub = Color(0xFF6E6B66), faint = Color(0xFFB3AEA5), track = Color(0xFFECE8DF),
    accent = Color(0xFF2F5BEA), onAccent = Color.White,
    hues = Hues(forme = Color(0xFF2F5BEA), charge = Color(0xFF1C1D21), repos = Color(0xFF7A6FD8), effort = Color(0xFFE8613C),
        steps = Color(0xFF2F5BEA), heart = Color(0xFFD9434F), sleep = Color(0xFF7A6FD8), food = Color(0xFF2F5BEA),
        protein = Color(0xFFD9434F), fat = Color(0xFFE0A21B), carbs = Color(0xFF2E9E6B),
        gym = Color(0xFF2F5BEA), run = Color(0xFFE8613C), bike = Color(0xFF2E9E6B)),
    head = Caladea, headWeight = FontWeight.Bold, body = Inter, num = Caladea, numWeight = FontWeight.Bold,
    cards = false, chip = Chip.NONE, hero = Hero.TYPE, nav = Nav.LINE, stroke = 3.dp,
)

val ALL = listOf(Aube, Nocturne, Galet, Carnet)

// ---- formes -----------------------------------------------------------------------------------------------------

/** Biscuit festonné (Material 3 Expressive « cookie »). */
fun cookie(n: Int = 9, depth: Float = 0.08f): Shape = GenericShape { size, _ ->
    val cx = size.width / 2; val cy = size.height / 2; val r = minOf(cx, cy)
    val steps = 180
    for (i in 0..steps) {
        val a = 2 * PI * i / steps
        val rr = r * (1 - depth + depth * cos(n * a)).toFloat()
        val x = cx + rr * cos(a).toFloat(); val y = cy + rr * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Galet : cercle déformé par deux harmoniques douces. */
fun pebble(seed: Float = 0f): Shape = GenericShape { size, _ ->
    val cx = size.width / 2; val cy = size.height / 2
    for (i in 0..180) {
        val a = 2 * PI * i / 180
        val k = 1 + 0.045 * sin(2 * a + seed) + 0.03 * cos(3 * a + seed * 2)
        val x = cx + (size.width / 2 * k * cos(a)).toFloat(); val y = cy + (size.height / 2 * k * sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

// ---- briques ----------------------------------------------------------------------------------------------------

@Composable fun T(text: String, size: TextUnit = 15.sp, color: Color = L.ink, weight: FontWeight = FontWeight.Normal,
                  family: FontFamily = L.body, modifier: Modifier = Modifier, align: TextAlign? = null, spacing: TextUnit = TextUnit.Unspecified) =
    Text(text, modifier, style = TextStyle(fontFamily = family, fontSize = size, color = color, fontWeight = weight,
        letterSpacing = spacing, textAlign = align ?: TextAlign.Unspecified))

@Composable fun Num(text: String, size: TextUnit, color: Color = L.ink, modifier: Modifier = Modifier) =
    T(text, size, color, L.numWeight, L.num, modifier, spacing = if (L.num == Mono) (-0.5).sp else (-0.6).sp)

@Composable fun Label(text: String, color: Color = L.sub, modifier: Modifier = Modifier) =
    if (L.caps) T(text.uppercase(), 11.sp, color, FontWeight.SemiBold, modifier = modifier, spacing = 1.4.sp)
    else T(text, 13.sp, color, FontWeight.Medium, modifier = modifier)

/** Fond d'une carte selon le thème ; [hue] teinte la carte quand le thème le veut. */
@Composable
fun Card(modifier: Modifier = Modifier, hue: Color? = null, pad: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val l = L
    val shape = RoundedCornerShape(l.radius)
    if (!l.cards) {
        // Carnet : un filet au-dessus, pas de boîte
        Column(modifier.fillMaxWidth().drawBehind {
            drawLine(l.border!!, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }.padding(top = 16.dp, bottom = 14.dp), content = content)
        return
    }
    val bg = if (l.tinted && hue != null && !hue.warm()) hue.copy(alpha = 0.16f).compositeOver(l.card) else l.card
    Column(modifier.fillMaxWidth()
        .then(if (l.shadow > 0.dp) Modifier.shadow(l.shadow, shape, ambientColor = Color(0x22000000), spotColor = Color(0x18000000)) else Modifier)
        .clip(shape).background(bg)
        .then(if (l.border != null) Modifier.border(1.dp, l.border, shape) else Modifier)
        .padding(pad), content = content)
}

/** Rouge, orange, jaune, rose : teintés sur fond sombre, ils tournent au brun. */
fun Color.warm(): Boolean {
    val mx = maxOf(red, green, blue); val mn = minOf(red, green, blue)
    if (mx - mn < 0.08f) return false
    val h = when (mx) { red -> 60 * (((green - blue) / (mx - mn)) % 6); green -> 60 * ((blue - red) / (mx - mn) + 2); else -> 60 * ((red - green) / (mx - mn) + 4) }
    val hh = (h + 360) % 360
    return hh < 70 || hh > 300
}

fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

/** Pastille d'icône. */
@Composable
fun IconChip(icon: ImageVector, hue: Color, size: Dp = 32.dp) {
    val l = L
    when (l.chip) {
        Chip.NONE -> Icon(icon, null, Modifier.size(size * 0.62f), tint = hue)
        else -> {
            val shape = when (l.chip) { Chip.COOKIE -> cookie(8, 0.07f); Chip.SQUIRCLE -> RoundedCornerShape(size * 0.32f); else -> CircleShape }
            val bg = if (l.dark) hue.copy(alpha = 0.22f) else hue.copy(alpha = 0.14f)
            Box(Modifier.size(size).clip(shape).background(bg), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(size * 0.56f), tint = hue)
            }
        }
    }
}

/** En-tête d'une carte : pastille, titre, chevron. */
@Composable
fun Header(title: String, icon: ImageVector, hue: Color, trailing: String? = null) {
    val l = L
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconChip(icon, hue, 30.dp)
        Spacer(Modifier.width(if (l.chip == Chip.NONE) 6.dp else 10.dp))
        if (l.caps) T(title.uppercase(), 12.sp, l.sub, FontWeight.Bold, modifier = Modifier.weight(1f), spacing = 1.5.sp)
        else if (!l.cards) T(title, 19.sp, l.ink, FontWeight.Bold, l.head, Modifier.weight(1f))
        else T(title, 16.sp, l.ink, FontWeight.SemiBold, if (l.head == Comfortaa) Comfortaa else l.body, Modifier.weight(1f))
        trailing?.let { T(it, 13.sp, l.sub) }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = l.faint)
    }
}

/** Arc de progression. */
@Composable
fun Ring(f: Float, hue: Color, size: Dp, stroke: Dp = L.stroke, content: @Composable BoxScope.() -> Unit = {}) {
    val l = L
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx(); val inset = s / 2
            val sz = Size(this.size.width - s, this.size.height - s)
            drawArc(if (l.tinted && !hue.warm()) hue.copy(alpha = 0.2f) else l.track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(s))
            drawArc(Brush.sweepGradient(listOf(hue.copy(alpha = 0.75f), hue, hue)), -90f, 360f * f, false, Offset(inset, inset), sz,
                style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun Bar(f: Float, hue: Color, modifier: Modifier = Modifier, h: Dp = 8.dp) {
    val l = L
    Box(modifier.fillMaxWidth().height(h).clip(CircleShape).background(if (l.tinted && !hue.warm()) hue.copy(alpha = 0.2f) else l.track)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(CircleShape).background(hue))
    }
}

// ---- données fictives (les mêmes partout) -------------------------------------------------------------------------

/** Nuit fictive : (phase, minutes), 1 profond, 2 léger, 3 paradoxal, 4 éveil. */
private val sleepRuns = listOf(2 to 18, 1 to 42, 2 to 30, 3 to 16, 2 to 34, 1 to 28, 2 to 22, 3 to 24, 4 to 5, 2 to 38, 3 to 30, 2 to 26, 4 to 6, 3 to 34, 2 to 20)

private fun pixel(path: String) = runCatching {
    org.jetbrains.skia.Image.makeFromEncoded(java.io.File(System.getProperty("superhealth.assets", "assets"), path).readBytes()).toComposeImageBitmap()
}.getOrNull()

data class Food(val name: String, val qty: String, val kcal: Int, val icon: String)
data class Meal(val name: String, val time: String, val foods: List<Food>)

private val meals = listOf(
    Meal("Petit-déjeuner", "8:10", listOf(Food("Œufs brouillés", "2 œufs", 182, "foods/pack/070.png"), Food("Pain complet", "60 g", 148, "foods/pack/083.png"),
        Food("Banane", "1 moyenne", 105, "foods/pack/017.png"), Food("Café", "1 tasse", 2, "foods/pack/117.png"))),
    Meal("Déjeuner", "12:40", listOf(Food("Poulet rôti", "150 g", 285, "foods/pack/076.png"), Food("Riz basmati", "180 g", 230, "foods/pack/139.png"),
        Food("Brocolis", "120 g", 41, "foods/pack/058.png"), Food("Avocat", "½", 120, "foods/pack/036.png"))),
    Meal("Collation", "16:30", listOf(Food("Yaourt grec", "150 g", 146, "foods/gen/i103.png"), Food("Pomme", "1 moyenne", 95, "foods/pack/022.png"))),
    Meal("Dîner", "", emptyList()),
)

// ---- page ---------------------------------------------------------------------------------------------------------

@Composable
fun Page(look: Look, tab: Int, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalLook provides look) {
        val l = look
        Box(Modifier.fillMaxSize().background(l.bg)) {
            if (l.glow.isNotEmpty()) Canvas(Modifier.fillMaxWidth().height(360.dp)) {
                l.glow.forEach { (c, o) ->
                    drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), Offset(o.x * size.width, o.y * size.height + 40f), size.width * 0.55f),
                        size.width * 0.55f, Offset(o.x * size.width, o.y * size.height + 40f))
                }
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 54.dp), verticalArrangement = Arrangement.spacedBy(if (l.cards) 12.dp else 0.dp),
                content = content)
            NavBar(tab, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
fun TopTitle(over: String, title: String) {
    val l = L
    Row(Modifier.fillMaxWidth().padding(bottom = if (l.cards) 4.dp else 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (over.isNotEmpty()) Label(over)
            Spacer(Modifier.height(2.dp))
            when {
                l.caps -> T(title.uppercase(), 26.sp, l.ink, FontWeight.Black, InterDisplay, spacing = 0.5.sp)
                else -> T(title, if (l.cards) 34.sp else 38.sp, l.ink, l.headWeight, l.head, spacing = if (l.head == Caladea) 0.sp else (-0.8).sp)
            }
        }
        Box(Modifier.size(38.dp).clip(if (l.chip == Chip.COOKIE) cookie(10, 0.06f) else CircleShape)
            .background(if (l.cards) l.accent.copy(alpha = if (l.dark) 0.25f else 0.15f) else Color.Transparent)
            .then(if (!l.cards) Modifier.border(1.dp, l.ink, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
            T("B", 16.sp, if (l.cards) l.accent else l.ink, FontWeight.Bold, l.head)
        }
    }
}

@Composable
fun NavBar(tab: Int, modifier: Modifier) {
    val l = L
    val items = listOf(Icons.Rounded.GridView to "Accueil", Icons.Rounded.Restaurant to "Journal", null to "",
        Icons.AutoMirrored.Rounded.DirectionsRun to "Activités", Icons.Rounded.Settings to "Réglages")
    val shape = when (l.nav) { Nav.FLOATING, Nav.TONAL -> RoundedCornerShape(30.dp); else -> RoundedCornerShape(0.dp) }
    val outer = when (l.nav) { Nav.FLOATING, Nav.TONAL -> Modifier.padding(horizontal = 14.dp, vertical = 18.dp); else -> Modifier }
    Row(modifier.then(outer).fillMaxWidth()
        .then(if (l.nav == Nav.FLOATING) Modifier.shadow(18.dp, shape, ambientColor = Color(0x33000000)) else Modifier)
        .clip(shape)
        .background(when (l.nav) { Nav.FLOATING -> Color.White.copy(alpha = 0.96f); Nav.TONAL -> Color(0xFF2B2738); Nav.FLAT -> Color(0xFF07080A); Nav.LINE -> l.bg })
        .drawBehind {
            if (l.nav == Nav.LINE) drawLine(l.border!!, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            if (l.nav == Nav.FLAT) drawLine(l.border!!, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }
        .padding(horizontal = 8.dp, vertical = 8.dp).padding(bottom = if (l.nav == Nav.FLAT || l.nav == Nav.LINE) 18.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { i, (icon, label) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (icon == null) {
                    val s = when (l.nav) { Nav.TONAL -> cookie(10, 0.06f); Nav.LINE -> CircleShape; Nav.FLAT -> RoundedCornerShape(14.dp); else -> CircleShape }
                    Box(Modifier.size(if (l.nav == Nav.LINE) 44.dp else 52.dp).clip(s).background(if (l.nav == Nav.LINE) l.ink else l.accent),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(28.dp), tint = if (l.nav == Nav.LINE) l.bg else l.onAccent)
                    }
                } else {
                    val on = i == tab
                    val pill = l.nav == Nav.TONAL || l.nav == Nav.FLOATING
                    Box(Modifier.size(width = 56.dp, height = 30.dp).clip(CircleShape)
                        .background(if (on && pill) l.accent.copy(alpha = if (l.dark) 0.28f else 0.14f) else Color.Transparent),
                        contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(22.dp), tint = if (on) (if (l.nav == Nav.LINE) l.ink else l.accent) else l.faint)
                    }
                    Spacer(Modifier.height(3.dp))
                    if (l.caps) T(label.uppercase(), 9.sp, if (on) l.ink else l.faint, FontWeight.Bold, spacing = 1.sp)
                    else T(label, 11.sp, if (on) l.ink else l.sub, if (on) FontWeight.SemiBold else FontWeight.Medium)
                    if (l.nav == Nav.LINE && on) Box(Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape).background(l.ink))
                }
            }
        }
    }
}

// ---- Accueil ------------------------------------------------------------------------------------------------------

private data class Score(val name: String, val v: Int, val word: String, val hue: (Hues) -> Color, val icon: ImageVector)
private val scores = listOf(
    Score("Forme", 78, "Prêt", { it.forme }, Icons.Rounded.Spa),
    Score("Charge", 64, "Bonne", { it.charge }, Icons.Rounded.Bolt),
    Score("Repos", 84, "Réparateur", { it.repos }, Icons.Rounded.Bedtime),
    Score("Effort", 42, "Modéré", { it.effort }, Icons.Rounded.LocalFireDepartment),
)

@Composable
fun Home(look: Look) = Page(look, 0) {
    val l = L; val h = l.hues
    TopTitle("Vendredi 30 octobre", "Aujourd'hui")
    // A — Scores du jour
    ScoresHero()
    // B — Calories restantes
    Card(hue = h.food) {
        Header("Calories restantes", Icons.Rounded.LocalFireDepartment, h.food, "Objectif 2 280")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (l.hero == Hero.TYPE) {
                Column(Modifier.width(130.dp)) { Num("927", 52.sp); T("kcal restantes", 13.sp, l.sub) }
            } else Ring(1353f / 2280, h.food, 112.dp, stroke = l.stroke + 2.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Num("927", 28.sp); T("restantes", 11.sp, l.sub) }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Macro("Protéines", 98, 150, h.protein); Macro("Lipides", 52, 76, h.fat); Macro("Glucides", 141, 285, h.carbs)
            }
        }
    }
    // C — Sommeil  +  D — Pas / Cœur
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.weight(1.25f), hue = h.sleep) {
            Header("Sommeil", Icons.Rounded.Bedtime, h.sleep)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) { Num("7 h 32", 26.sp); Spacer(Modifier.width(6.dp)); T("score 84", 12.sp, l.sub, modifier = Modifier.padding(bottom = 4.dp)) }
            Spacer(Modifier.height(10.dp))
            Hypnogram(h.sleep)
            Spacer(Modifier.height(4.dp))
            Row { T("23:48", 10.sp, l.faint, modifier = Modifier.weight(1f)); T("7:20", 10.sp, l.faint) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (l.cards) 12.dp else 0.dp)) {
            Card(hue = h.steps, pad = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) { IconChip(Icons.AutoMirrored.Rounded.DirectionsRun, h.steps, 26.dp); Spacer(Modifier.width(8.dp)); Label("Pas") }
                Spacer(Modifier.height(6.dp)); Num("6 240", 22.sp); Spacer(Modifier.height(6.dp)); Bar(0.78f, h.steps, h = 6.dp)
            }
            Card(hue = h.heart, pad = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) { IconChip(Icons.Rounded.Favorite, h.heart, 26.dp); Spacer(Modifier.width(8.dp)); Label("FC repos") }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) { Num("54", 22.sp); T(" bpm", 12.sp, l.sub, modifier = Modifier.padding(bottom = 3.dp)) }
                Spacer(Modifier.height(4.dp)); Spark(h.heart)
            }
        }
    }
}

@Composable
private fun Macro(name: String, v: Int, of: Int, hue: Color) {
    val l = L
    Column {
        Row { T(name, 12.sp, l.sub, FontWeight.Medium, modifier = Modifier.weight(1f)); T("$v / $of g", 12.sp, l.ink, FontWeight.SemiBold, if (l.num == Mono) Mono else l.body) }
        Spacer(Modifier.height(4.dp)); Bar(v.toFloat() / of, hue, h = 7.dp)
    }
}

@Composable
private fun Hypnogram(hue: Color) {
    val l = L
    // escalier : une barre par phase à sa hauteur (éveil en haut, profond en bas), reliées par des filets
    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val total = sleepRuns.sumOf { it.second }.toFloat()
        val th = 8.dp.toPx()
        fun y(s: Int) = when (s) { 4 -> 0f; 3 -> 1f; 2 -> 2f; else -> 3f } / 3f * (size.height - th)
        var x = 0f
        val xs = sleepRuns.map { r -> val a = x; x += r.second / total * size.width; a to x }
        sleepRuns.forEachIndexed { i, r ->
            if (i > 0) {
                val a = y(sleepRuns[i - 1].first); val b = y(r.first)
                drawLine(l.faint.copy(alpha = 0.5f), Offset(xs[i].first, minOf(a, b) + th / 2), Offset(xs[i].first, maxOf(a, b) + th / 2), 1.dp.toPx())
            }
        }
        sleepRuns.forEachIndexed { i, (st, _) ->
            val c = when (st) { 4 -> l.faint; 3 -> hue.copy(alpha = 0.55f); 2 -> hue.copy(alpha = 0.8f); else -> hue }
            drawRoundRect(c, Offset(xs[i].first, y(st)), Size(maxOf(th, xs[i].second - xs[i].first), th), androidx.compose.ui.geometry.CornerRadius(th / 2))
        }
    }
}

@Composable
private fun Spark(hue: Color) {
    val pts = listOf(58, 57, 56, 57, 55, 54, 55, 54, 53, 54)
    Canvas(Modifier.fillMaxWidth().height(20.dp)) {
        val p = androidx.compose.ui.graphics.Path()
        pts.forEachIndexed { i, v ->
            val x = i * size.width / (pts.size - 1); val y = (v - 52) / 7f * size.height
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, hue, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun ScoresHero() {
    val l = L; val h = l.hues
    when (l.hero) {
        Hero.DIALS -> Card(pad = 14.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                scores.take(3).forEach { s ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Ring(s.v / 100f, s.hue(h), 92.dp, 6.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Num("${s.v}", 26.sp, s.hue(h)); T("%", 10.sp, l.sub, family = Mono) }
                        }
                        Spacer(Modifier.height(8.dp))
                        T(s.name.uppercase(), 11.sp, l.ink, FontWeight.Bold, spacing = 1.5.sp)
                        T(s.word.uppercase(), 9.sp, l.sub, FontWeight.Medium, spacing = 1.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF15181D)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(16.dp), tint = h.effort)
                Spacer(Modifier.width(8.dp)); T("EFFORT", 11.sp, l.sub, FontWeight.Bold, spacing = 1.5.sp, modifier = Modifier.width(64.dp))
                Bar(0.42f, h.effort, Modifier.weight(1f), 5.dp); Spacer(Modifier.width(10.dp)); Num("42", 15.sp, h.effort); T(" %", 11.sp, l.sub, family = Mono)
            }
        }
        Hero.BLOB -> Card(hue = h.forme, pad = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(132.dp).clip(cookie(11, 0.06f)).background(h.forme.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
                    Ring(0.78f, h.forme, 108.dp, 12.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Num("78", 34.sp); T("Prêt", 12.sp, l.sub, FontWeight.SemiBold, Comfortaa) }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T("Forme", 22.sp, l.ink, FontWeight.Bold, Comfortaa)
                    scores.drop(1).forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(26.dp).clip(pebble(s.v.toFloat())).background(s.hue(h)), contentAlignment = Alignment.Center) {
                                Icon(s.icon, null, Modifier.size(15.dp), tint = Color(0xFF221A3D))
                            }
                            Spacer(Modifier.width(8.dp)); T(s.name, 13.sp, l.sub, modifier = Modifier.width(52.dp)); Num("${s.v}", 16.sp)
                        }
                    }
                }
            }
        }
        Hero.TYPE -> Card {
            Label("Forme du jour")
            Row(verticalAlignment = Alignment.Bottom) {
                T("78", 92.sp, l.ink, FontWeight.Bold, Caladea, spacing = (-3).sp)
                Column(Modifier.padding(start = 10.dp, bottom = 18.dp)) {
                    T("Prêt à pousser", 20.sp, h.forme, FontWeight.Bold, Caladea)
                    T("Charge 64 · Repos 84 · Effort 42", 13.sp, l.sub)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                scores.forEach { s ->
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(l.track)) { Box(Modifier.fillMaxWidth(s.v / 100f).fillMaxHeight().background(if (s.name == "Forme") h.forme else l.ink)) }
                        Spacer(Modifier.height(4.dp)); T(s.name, 11.sp, l.sub)
                    }
                }
            }
        }
        Hero.RING -> Card {
            Header("Scores du jour", Icons.Rounded.Spa, h.forme)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    Ring(0.42f, h.effort, 132.dp, 9.dp)
                    Ring(0.84f, h.repos, 108.dp, 9.dp)
                    Ring(0.64f, h.charge, 84.dp, 9.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Num("78", 26.sp, h.forme); T("Forme", 10.sp, l.sub, FontWeight.SemiBold) }
                }
                Spacer(Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    scores.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(s.hue(h)))
                            Spacer(Modifier.width(8.dp))
                            Column { T(s.name, 12.sp, l.sub, FontWeight.Medium); Row(verticalAlignment = Alignment.Bottom) { Num("${s.v}", 19.sp); T("  " + s.word, 11.sp, l.faint, modifier = Modifier.padding(bottom = 2.dp)) } }
                        }
                    }
                }
            }
        }
    }
}

// ---- Journal ------------------------------------------------------------------------------------------------------

@Composable
fun Journal(look: Look) = Page(look, 1) {
    val l = L; val h = l.hues
    TopTitle("", "Journal")
    // jours de la semaine
    Row(Modifier.fillMaxWidth().padding(bottom = if (l.cards) 0.dp else 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("L" to 26, "M" to 27, "M" to 28, "J" to 29, "V" to 30, "S" to 31, "D" to 1).forEachIndexed { i, (d, n) ->
            val on = i == 4
            val shape = if (l.chip == Chip.COOKIE && on) cookie(8, 0.07f) else if (l.cards) RoundedCornerShape(16.dp) else CircleShape
            Column(Modifier.width(44.dp).clip(shape).background(when { on && l.cards -> l.accent; on -> l.ink; else -> Color.Transparent }).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                T(if (l.caps) d else d, 11.sp, if (on) l.onAccent.takeIf { l.cards } ?: l.bg else l.sub, FontWeight.SemiBold)
                Num("$n", 17.sp, if (on) (if (l.cards) l.onAccent else l.bg) else if (i > 4) l.faint else l.ink)
                Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(if (i < 4) h.food else Color.Transparent))
            }
        }
    }
    // A — résumé
    Card(hue = h.food) {
        Row(verticalAlignment = Alignment.Bottom) {
            Num("1 353", 34.sp); T(" / 2 280 kcal", 14.sp, l.sub, modifier = Modifier.padding(bottom = 6.dp).weight(1f))
            Label("927 restantes", h.food)
        }
        Spacer(Modifier.height(10.dp))
        Bar(1353f / 2280, h.food, h = 10.dp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Triple("Protéines", 98 to 150, h.protein), Triple("Lipides", 52 to 76, h.fat), Triple("Glucides", 141 to 285, h.carbs)).forEach { (n, v, c) ->
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) { Num("${v.first}", 18.sp); T(" / ${v.second} g", 11.sp, l.sub, modifier = Modifier.padding(bottom = 2.dp)) }
                    Spacer(Modifier.height(4.dp)); Bar(v.first.toFloat() / v.second, c, h = 6.dp); Spacer(Modifier.height(4.dp)); T(n, 11.sp, l.sub)
                }
            }
        }
    }
    // B, C, D — repas
    meals.forEach { m -> MealCard(m) }
}

@Composable
private fun MealCard(m: Meal) {
    val l = L; val h = l.hues
    val total = m.foods.sumOf { it.kcal }
    Card(pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (l.caps) T(m.name.uppercase(), 12.sp, l.ink, FontWeight.Bold, spacing = 1.5.sp, modifier = Modifier.weight(1f))
            else T(m.name, if (l.cards) 16.sp else 19.sp, l.ink, if (l.cards) FontWeight.SemiBold else FontWeight.Bold,
                if (l.cards) (if (l.head == Comfortaa) Comfortaa else l.body) else l.head, Modifier.weight(1f))
            if (m.time.isNotEmpty()) T(m.time + "  ·  ", 12.sp, l.faint, family = if (l.num == Mono) Mono else l.body)
            if (total > 0) Num("$total kcal", 14.sp, l.sub)
        }
        if (m.foods.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(if (l.cards) l.radius / 2 else 0.dp))
                .then(if (l.cards) Modifier.background(l.accent.copy(alpha = if (l.dark) 0.14f else 0.08f)) else Modifier.border(1.dp, l.border!!, RoundedCornerShape(0.dp)))
                .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = if (l.cards) l.accent else l.ink)
                Spacer(Modifier.width(8.dp)); T("Ajouter au dîner", 14.sp, if (l.cards) l.accent else l.ink, FontWeight.SemiBold)
                Spacer(Modifier.weight(1f)); T("≈ 930 kcal conseillées", 12.sp, l.sub)
            }
            return@Card
        }
        Spacer(Modifier.height(6.dp))
        m.foods.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                val img = pixel(f.icon)
                Box(Modifier.size(34.dp).clip(if (l.chip == Chip.COOKIE) pebble(f.kcal.toFloat()) else if (l.cards) RoundedCornerShape(10.dp) else CircleShape)
                    .background(if (l.dark) Color(0x14FFFFFF) else Color(0x0A000000)), contentAlignment = Alignment.Center) {
                    if (img != null) Image(img, null, Modifier.size(26.dp), filterQuality = FilterQuality.None)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { T(f.name, 14.sp, l.ink, FontWeight.Medium); T(f.qty, 12.sp, l.sub) }
                T("${f.kcal}", 14.sp, l.ink, FontWeight.Medium, if (l.num == Mono) Mono else l.body)
            }
        }
    }
}

// ---- Activités ----------------------------------------------------------------------------------------------------

@Composable
fun Activities(look: Look) = Page(look, 3) {
    val l = L; val h = l.hues
    TopTitle("", "Activités")
    // A — Cette semaine
    Card(hue = h.run) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Cette semaine", modifier = Modifier.weight(1f)); T("Performances", 13.sp, l.accent.takeIf { l.cards } ?: l.ink, FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Num("4", 30.sp); T(" activités", 14.sp, l.sub, modifier = Modifier.padding(bottom = 5.dp).weight(1f))
            T("3 h 10  ·  18,4 km", 13.sp, l.sub, FontWeight.Medium, if (l.num == Mono) Mono else l.body, Modifier.padding(bottom = 5.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val acts = listOf(Icons.AutoMirrored.Rounded.DirectionsRun to h.run, Icons.Rounded.FitnessCenter to h.gym, null, Icons.AutoMirrored.Rounded.DirectionsBike to h.bike,
                Icons.Rounded.FitnessCenter to h.gym, null, null)
            listOf("L", "M", "M", "J", "V", "S", "D").forEachIndexed { i, d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val a = acts[i]
                    val shape = if (l.chip == Chip.COOKIE) pebble(i * 1.7f) else if (l.cards || l.dark) CircleShape else CircleShape
                    Box(Modifier.size(36.dp).clip(shape).background(a?.second ?: l.track)
                        .then(if (i == 4 && a == null) Modifier.border(2.dp, l.accent, shape) else Modifier), contentAlignment = Alignment.Center) {
                        a?.let { Icon(it.first, null, Modifier.size(19.dp), tint = if (l.dark && l.tinted) Color(0xFF221A3D) else if (l.dark) Color.Black else Color.White) }
                    }
                    Spacer(Modifier.height(4.dp)); T(d, 11.sp, if (i == 4) l.ink else l.sub, if (i == 4) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
    // B — Entraînement
    if (!l.cards) Spacer(Modifier.height(4.dp))
    Card(hue = h.gym) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Rounded.FitnessCenter, h.gym, 40.dp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Label("Prochaine séance · semaine 3")
                if (l.caps) T("PUSH A", 20.sp, l.ink, FontWeight.Black, InterDisplay, spacing = 1.sp)
                else T("Push A", 22.sp, l.ink, l.headWeight, l.head)
            }
            T("~55 min", 13.sp, l.sub, family = if (l.num == Mono) Mono else l.body)
        }
        Spacer(Modifier.height(12.dp))
        listOf("Développé couché" to "4 × 6–8 · 80 kg", "Développé militaire" to "3 × 8 · 45 kg", "Dips lestés" to "3 × 10 · +10 kg").forEach { (n, s) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                T(n, 14.sp, l.ink, FontWeight.Medium, modifier = Modifier.weight(1f)); T(s, 13.sp, l.sub, family = if (l.num == Mono) Mono else l.body)
            }
        }
        T("+ 2 exercices", 12.sp, l.faint, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(12.dp))
        val bshape = when { l.chip == Chip.COOKIE -> RoundedCornerShape(50); l.cards -> RoundedCornerShape(if (l.radius > 20.dp) 22.dp else 10.dp); else -> RoundedCornerShape(0.dp) }
        Row(Modifier.fillMaxWidth().clip(bshape).background(if (l.cards) h.gym else l.ink).padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp), tint = if (l.dark) Color(0xFF0B0C10) else Color.White)
            Spacer(Modifier.width(6.dp))
            T(if (l.caps) "COMMENCER" else "Commencer", 15.sp, if (l.dark) Color(0xFF0B0C10) else Color.White, FontWeight.Bold, spacing = if (l.caps) 1.5.sp else 0.sp)
        }
    }
    // C — gestes rapides
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = if (l.cards) 0.dp else 10.dp)) {
        listOf(Triple("Séance libre", Icons.Rounded.Bolt, h.charge), Triple("Créer", Icons.Rounded.Add, h.forme), Triple("Coach", Icons.Rounded.AutoAwesome, h.repos)).forEachIndexed { i, (n, ic, c) ->
            val shape = when { l.chip == Chip.COOKIE -> pebble(i * 2f); l.cards -> RoundedCornerShape(l.radius * 0.7f); else -> RoundedCornerShape(0.dp) }
            Column(Modifier.weight(1f).height(78.dp).clip(shape)
                .then(if (l.cards) Modifier.background(if (l.dark) c.copy(alpha = 0.18f).compositeOver(l.card) else c.copy(alpha = 0.12f).compositeOver(Color.White))
                    else Modifier.border(1.dp, l.border!!, shape))
                .then(if (l.border != null && l.cards) Modifier.border(1.dp, l.border, shape) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(ic, null, Modifier.size(24.dp), tint = if (l.cards) c else l.ink)
                Spacer(Modifier.height(6.dp))
                if (l.caps) T(n.uppercase(), 10.sp, l.ink, FontWeight.Bold, spacing = 1.sp) else T(n, 13.sp, l.ink, FontWeight.SemiBold)
            }
        }
    }
    // D — Récentes
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (l.caps) T("RÉCENTES", 12.sp, l.sub, FontWeight.Bold, spacing = 1.5.sp, modifier = Modifier.weight(1f))
            else T("Récentes", if (l.cards) 16.sp else 19.sp, l.ink, if (l.cards) FontWeight.SemiBold else FontWeight.Bold, if (l.cards) (if (l.head == Comfortaa) Comfortaa else l.body) else l.head, Modifier.weight(1f))
            Icon(Icons.Rounded.History, null, Modifier.size(18.dp), tint = l.faint); Spacer(Modifier.width(4.dp)); T("Tout voir", 13.sp, l.sub)
        }
        Spacer(Modifier.height(8.dp))
        listOf(
            Triple(Icons.AutoMirrored.Rounded.DirectionsRun, h.run, listOf("Course à pied", "Hier · 18:20", "5,2 km", "28:41")),
            Triple(Icons.Rounded.FitnessCenter, h.gym, listOf("Pull B", "Mardi · 7:05", "12 340 kg", "52 min")),
            Triple(Icons.AutoMirrored.Rounded.DirectionsBike, h.bike, listOf("Vélo", "Lundi · 17:45", "21,0 km", "48 min")),
        ).forEach { (ic, c, t) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                IconChip(ic, c, 36.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { T(t[0], 14.sp, l.ink, FontWeight.SemiBold); T(t[1], 12.sp, l.sub) }
                Column(horizontalAlignment = Alignment.End) { Num(t[2], 15.sp); T(t[3], 12.sp, l.sub, family = if (l.num == Mono) Mono else l.body) }
            }
        }
    }
}
