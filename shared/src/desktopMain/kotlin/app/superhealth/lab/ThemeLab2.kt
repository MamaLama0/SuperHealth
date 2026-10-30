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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreHoriz
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
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Labo de thèmes, tour 2 : quatre directions loin de l'app actuelle. Chaque thème est un « kit » (fond, cartes, anneaux,
 * barres, boutons, barre d'onglets) ; les écrans et les données sont les mêmes pour comparer.
 */

private fun font(path: String, w: FontWeight, style: FontStyle = FontStyle.Normal) = Font(java.io.File(path), w, style)
val Narrow = FontFamily(
    font("/usr/share/fonts/urw-base35/NimbusSansNarrow-Bold.otf", FontWeight.Bold),
    font("/usr/share/fonts/urw-base35/NimbusSansNarrow-BoldOblique.otf", FontWeight.Bold, FontStyle.Italic),
    font("/usr/share/fonts/urw-base35/NimbusSansNarrow-Regular.otf", FontWeight.Normal),
    font("/usr/share/fonts/urw-base35/NimbusSansNarrow-Oblique.otf", FontWeight.Normal, FontStyle.Italic),
)
val InterDisplayItalic = FontFamily(
    font("/usr/share/fonts/rsms-inter-fonts/InterDisplay-BlackItalic.ttf", FontWeight.Black, FontStyle.Italic),
    font("/usr/share/fonts/rsms-inter-fonts/InterDisplay-Black.ttf", FontWeight.Black),
)

// ---- kit -----------------------------------------------------------------------------------------------------------

open class Kit(
    val name: String, val tagline: String,
    val ink: Color, val sub: Color, val faint: Color, val track: Color,
    val accent: Color, val onAccent: Color, val hues: Hues,
    val head: FontFamily, val headWeight: FontWeight = FontWeight.Black, val headItalic: Boolean = false,
    val num: FontFamily, val numWeight: FontWeight = FontWeight.Bold, val numItalic: Boolean = false,
    val body: FontFamily = Inter, val caps: Boolean = false, val gap: Dp = 12.dp,
    /** couleurs du corps : silhouette, muscles, muscles travaillés (null = teinte du jour) */
    val bodyBase: Color, val bodyMuscle: Color,
) {
    @Composable open fun Background() {}
    @Composable open fun Card(modifier: Modifier, slot: Int, hue: Color?, pad: Dp, content: @Composable ColumnScope.() -> Unit) {}
    /** Couleur d'encre sur la carte [slot] (les blocs colorés du néo-brutal gardent le noir). */
    open fun inkOn(slot: Int): Color = ink
    open fun subOn(slot: Int): Color = sub
    @Composable open fun Ring(f: Float, hue: Color, size: Dp, stroke: Dp, content: @Composable BoxScope.() -> Unit) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val s = stroke.toPx(); val i = s / 2; val sz = Size(this.size.width - s, this.size.height - s)
                drawArc(track, 0f, 360f, false, Offset(i, i), sz, style = Stroke(s))
                drawArc(hue, -90f, 360f * f, false, Offset(i, i), sz, style = Stroke(s, cap = StrokeCap.Round))
            }
            content()
        }
    }
    @Composable open fun Bar(f: Float, hue: Color, modifier: Modifier, h: Dp) {
        Box(modifier.fillMaxWidth().height(h).clip(CircleShape).background(track)) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(CircleShape).background(hue))
        }
    }
    @Composable open fun Button(text: String, icon: ImageVector, modifier: Modifier) {
        Row(modifier.clip(CircleShape).background(accent).padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = onAccent); Spacer(Modifier.width(8.dp))
            K(text, 17.sp, onAccent, FontWeight.Bold)
        }
    }
    @Composable open fun Chip(icon: ImageVector, hue: Color, size: Dp, slot: Int = -1) {
        Box(Modifier.size(size).clip(CircleShape).background(hue.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(size * 0.56f), tint = hue)
        }
    }
    @Composable open fun Nav(tab: Int, modifier: Modifier) {}
    @Composable open fun Title(text: String) = K(if (caps) text.uppercase() else text, 36.sp, ink, headWeight, head, italic = headItalic, spacing = (-0.5).sp)
    @Composable open fun Section(text: String, modifier: Modifier = Modifier) =
        K(if (caps) text.uppercase() else text, if (caps) 13.sp else 20.sp, ink, FontWeight.Black, head, modifier, italic = headItalic, spacing = if (caps) 1.5.sp else 0.sp)
    /** Forme des tuiles de musculation. */
    open fun tileShape(i: Int): Shape = RoundedCornerShape(26.dp)
}

val LocalKit = staticCompositionLocalOf<Kit> { error("kit") }
private val Q @Composable get() = LocalKit.current

@Composable fun K(text: String, size: TextUnit = 15.sp, color: Color = Q.ink, weight: FontWeight = FontWeight.Normal, family: FontFamily = Q.body,
                  modifier: Modifier = Modifier, align: TextAlign? = null, spacing: TextUnit = TextUnit.Unspecified, italic: Boolean = false, maxLines: Int = Int.MAX_VALUE) =
    Text(text, modifier, maxLines = maxLines, style = TextStyle(fontFamily = family, fontSize = size, color = color, fontWeight = weight, letterSpacing = spacing,
        textAlign = align ?: TextAlign.Unspecified, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal))

@Composable fun KNum(text: String, size: TextUnit, color: Color = Q.ink, modifier: Modifier = Modifier) =
    K(text, size, color, Q.numWeight, Q.num, modifier, italic = Q.numItalic, spacing = (-0.5).sp)

@Composable fun KLabel(text: String, color: Color = Q.sub, modifier: Modifier = Modifier) =
    if (Q.caps) K(text.uppercase(), 11.sp, color, FontWeight.Bold, modifier = modifier, spacing = 1.3.sp)
    else K(text, 13.sp, color, FontWeight.Medium, modifier = modifier)

// ---- 1. Bloc : néo-brutal ----------------------------------------------------------------------------------------------

object Bloc : Kit(
    name = "Bloc", tagline = "Néo-brutal : blocs de couleur pleine, contours noirs épais, ombres dures, typo massive",
    ink = Color(0xFF111111), sub = Color(0xFF3A3A3A), faint = Color(0xFF8A8577), track = Color(0x22000000),
    accent = Color(0xFF111111), onAccent = Color(0xFFFFF6E6),
    hues = Hues(forme = Color(0xFF16A34A), charge = Color(0xFF2563EB), repos = Color(0xFF7C3AED), effort = Color(0xFFEA580C),
        steps = Color(0xFFDB2777), heart = Color(0xFFDC2626), sleep = Color(0xFF7C3AED), food = Color(0xFF0891B2),
        protein = Color(0xFFDC2626), fat = Color(0xFFCA8A04), carbs = Color(0xFF16A34A), gym = Color(0xFF2563EB), run = Color(0xFFEA580C), bike = Color(0xFF0891B2)),
    head = InterDisplay, num = InterDisplay, numWeight = FontWeight.Black, caps = true, gap = 16.dp,
    bodyBase = Color(0xFF111111), bodyMuscle = Color(0x33FFFFFF),
) {
    val bg = Color(0xFFFFF6E6)
    /** Couleurs des blocs, une par place (toujours les mêmes places dans les trois onglets). */
    val blocks = listOf(Color(0xFFC6F432), Color(0xFFFF8FB8), Color(0xFF8FC8FF), Color(0xFFFFD43B), Color(0xFFB9A4FF), Color(0xFFFFFFFF), Color(0xFF7CE0B5))
    @Composable override fun Background() { Box(Modifier.fillMaxSize().background(bg)) }
    @Composable override fun Card(modifier: Modifier, slot: Int, hue: Color?, pad: Dp, content: @Composable ColumnScope.() -> Unit) {
        val shape = RoundedCornerShape(14.dp)
        Box(modifier.fillMaxWidth()) {
            Box(Modifier.matchParentSize().offset(5.dp, 5.dp).clip(shape).background(ink))
            Column(Modifier.fillMaxWidth().clip(shape).background(if (slot < 0) Color.White else blocks[slot % blocks.size])
                .border(2.5.dp, ink, shape).padding(pad), content = content)
        }
    }
    @Composable override fun Ring(f: Float, hue: Color, size: Dp, stroke: Dp, content: @Composable BoxScope.() -> Unit) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val s = stroke.toPx() * 1.6f; val i = s / 2; val sz = Size(this.size.width - s, this.size.height - s)
                drawArc(Color.White, 0f, 360f, false, Offset(i, i), sz, style = Stroke(s))
                drawArc(ink, -90f, 360f * f, false, Offset(i, i), sz, style = Stroke(s, cap = StrokeCap.Butt))
                drawCircle(ink, this.size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.5.dp.toPx()))
                drawCircle(ink, this.size.minDimension / 2 - s, style = Stroke(2.5.dp.toPx()))
            }
            content()
        }
    }
    @Composable override fun Bar(f: Float, hue: Color, modifier: Modifier, h: Dp) {
        Box(modifier.fillMaxWidth().height(h + 4.dp).clip(RoundedCornerShape(4.dp)).background(Color.White).border(2.dp, ink, RoundedCornerShape(4.dp))) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(ink))
        }
    }
    @Composable override fun Button(text: String, icon: ImageVector, modifier: Modifier) {
        val shape = RoundedCornerShape(12.dp)
        Box(modifier) {
            Box(Modifier.matchParentSize().offset(4.dp, 4.dp).clip(shape).background(ink))
            Row(Modifier.fillMaxWidth().clip(shape).background(Color(0xFFC6F432)).border(2.5.dp, ink, shape).padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(24.dp), tint = ink); Spacer(Modifier.width(8.dp))
                K(text.uppercase(), 17.sp, ink, FontWeight.Black, InterDisplay, spacing = 1.sp)
            }
        }
    }
    @Composable override fun Chip(icon: ImageVector, hue: Color, size: Dp, slot: Int) {
        Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color.White).border(2.dp, ink, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(size * 0.6f), tint = ink)
        }
    }
    @Composable override fun Nav(tab: Int, modifier: Modifier) {
        Row(modifier.fillMaxWidth().background(bg).drawBehind { drawLine(ink, Offset(0f, 0f), Offset(size.width, 0f), 2.5.dp.toPx()) }
            .padding(horizontal = 10.dp).padding(top = 10.dp, bottom = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            navItems.forEachIndexed { i, (icon, label) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (icon == null) Button("", Icons.Rounded.Add, Modifier.width(56.dp))
                    else {
                        val on = i == tab
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) ink else Color.Transparent).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Icon(icon, null, Modifier.size(22.dp), tint = if (on) bg else ink)
                        }
                        K(label.uppercase(), 9.sp, ink, FontWeight.Black, spacing = 0.8.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }
    @Composable override fun Title(text: String) = K(text.uppercase(), 40.sp, ink, FontWeight.Black, InterDisplay, spacing = (-1).sp)
    override fun tileShape(i: Int): Shape = RoundedCornerShape(14.dp)
}

// ---- 2. Verre : liquid glass -------------------------------------------------------------------------------------------

object Verre : Kit(
    name = "Verre", tagline = "Verre liquide : dégradé vif en fond, cartes translucides, reflets, tout en blanc",
    ink = Color.White, sub = Color(0xCCFFFFFF), faint = Color(0x80FFFFFF), track = Color(0x2EFFFFFF),
    accent = Color.White, onAccent = Color(0xFF3A1B8F),
    hues = Hues(forme = Color(0xFF7CFFC4), charge = Color(0xFF7FD8FF), repos = Color(0xFFD9B8FF), effort = Color(0xFFFFC38A),
        steps = Color(0xFFFF9EC7), heart = Color(0xFFFF8FA3), sleep = Color(0xFFD9B8FF), food = Color(0xFF7FD8FF),
        protein = Color(0xFFFF8FA3), fat = Color(0xFFFFE08A), carbs = Color(0xFF7CFFC4), gym = Color(0xFF7FD8FF), run = Color(0xFFFFC38A), bike = Color(0xFF7CFFC4)),
    head = InterDisplay, headWeight = FontWeight.Bold, num = InterDisplay, numWeight = FontWeight.SemiBold,
    bodyBase = Color(0x66FFFFFF), bodyMuscle = Color(0x40FFFFFF),
) {
    @Composable override fun Background() {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2A1A8C), Color(0xFF5B2BD9), Color(0xFF1D3FB8), Color(0xFF0D1B5E))))
            fun blob(c: Color, x: Float, y: Float, r: Float) = drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), Offset(x * size.width, y * size.height), r * size.width),
                r * size.width, Offset(x * size.width, y * size.height))
            blob(Color(0xFFFF4FA3), 0.95f, 0.08f, 0.75f)
            blob(Color(0xFF00C6FF), 0.0f, 0.35f, 0.7f)
            blob(Color(0xFFB04DFF), 0.8f, 0.6f, 0.65f)
            blob(Color(0xFF00E0B0), 0.1f, 0.88f, 0.6f)
        }
    }
    @Composable override fun Card(modifier: Modifier, slot: Int, hue: Color?, pad: Dp, content: @Composable ColumnScope.() -> Unit) {
        val shape = RoundedCornerShape(30.dp)
        Column(modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x14FFFFFF)), Offset.Zero, Offset(600f, 900f)))
            .border(1.dp, Brush.linearGradient(listOf(Color(0x99FFFFFF), Color(0x1AFFFFFF), Color(0x55FFFFFF))), shape)
            .padding(pad), content = content)
    }
    @Composable override fun Ring(f: Float, hue: Color, size: Dp, stroke: Dp, content: @Composable BoxScope.() -> Unit) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val s = stroke.toPx(); val i = s / 2; val sz = Size(this.size.width - s, this.size.height - s)
                drawArc(track, 0f, 360f, false, Offset(i, i), sz, style = Stroke(s))
                // halo puis trait
                drawArc(hue.copy(alpha = 0.35f), -90f, 360f * f, false, Offset(i, i), sz, style = Stroke(s * 2.2f, cap = StrokeCap.Round))
                drawArc(Brush.sweepGradient(listOf(Color.White, hue, hue, Color.White)), -90f, 360f * f, false, Offset(i, i), sz, style = Stroke(s, cap = StrokeCap.Round))
            }
            content()
        }
    }
    @Composable override fun Button(text: String, icon: ImageVector, modifier: Modifier) {
        Row(modifier.clip(CircleShape).background(Color.White).padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = onAccent); Spacer(Modifier.width(8.dp)); K(text, 17.sp, onAccent, FontWeight.Bold)
        }
    }
    @Composable override fun Chip(icon: ImageVector, hue: Color, size: Dp, slot: Int) {
        Box(Modifier.size(size).clip(CircleShape).background(Color(0x26FFFFFF)).border(1.dp, Color(0x55FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(size * 0.56f), tint = hue)
        }
    }
    @Composable override fun Nav(tab: Int, modifier: Modifier) {
        Row(modifier.padding(horizontal = 16.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(CircleShape).background(Color(0x33FFFFFF)).border(1.dp, Color(0x66FFFFFF), CircleShape).padding(6.dp)) {
                navItems.filter { it.first != null }.forEachIndexed { j, (icon, label) ->
                    val i = if (j >= 2) j + 1 else j
                    val on = i == tab
                    Column(Modifier.weight(1f).clip(CircleShape).background(if (on) Color(0x40FFFFFF) else Color.Transparent).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon!!, null, Modifier.size(22.dp), tint = if (on) Color.White else Color(0xB3FFFFFF))
                        K(label, 10.sp, if (on) Color.White else Color(0xB3FFFFFF), FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(58.dp).clip(CircleShape).background(Color(0x40FFFFFF)).border(1.dp, Color(0x99FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, null, Modifier.size(30.dp), tint = Color.White)
            }
        }
    }
    override fun tileShape(i: Int): Shape = RoundedCornerShape(30.dp)
}

// ---- 3. Argile : claymorphism ------------------------------------------------------------------------------------------

object Argile : Kit(
    name = "Argile", tagline = "Pâte à modeler : volumes gonflés en pastel, ombres douces, formes rondes et joueuses",
    ink = Color(0xFF2B2350), sub = Color(0xFF6E6699), faint = Color(0xFFA9A3C9), track = Color(0xFFDCD5F5),
    accent = Color(0xFF7B5CFF), onAccent = Color.White,
    hues = Hues(forme = Color(0xFF3ECF8E), charge = Color(0xFF4F8DFF), repos = Color(0xFF9B6BFF), effort = Color(0xFFFF8A4C),
        steps = Color(0xFFFF5C9A), heart = Color(0xFFFF5C7A), sleep = Color(0xFF9B6BFF), food = Color(0xFF22B8CF),
        protein = Color(0xFFFF5C7A), fat = Color(0xFFFFB84C), carbs = Color(0xFF3ECF8E), gym = Color(0xFF4F8DFF), run = Color(0xFFFF8A4C), bike = Color(0xFF22B8CF)),
    head = Comfortaa, headWeight = FontWeight.Bold, num = Comfortaa, numWeight = FontWeight.Bold,
    bodyBase = Color(0xFFCFC6F2), bodyMuscle = Color(0xFFE6E0FF), gap = 16.dp,
) {
    val bg = Color(0xFFEFEAFF)
    @Composable override fun Background() { Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF4EEFF), Color(0xFFE9F0FF))))) }
    @Composable override fun Card(modifier: Modifier, slot: Int, hue: Color?, pad: Dp, content: @Composable ColumnScope.() -> Unit) {
        val shape = RoundedCornerShape(32.dp)
        Column(modifier.fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = Color(0xFF6A55C8), spotColor = Color(0xFF6A55C8))
            .clip(shape).background(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF3EFFF))))
            .drawBehind {
                // reflet en haut à gauche, creux en bas : effet gonflé
                drawRoundRect(Brush.verticalGradient(listOf(Color.White, Color.Transparent), 0f, size.height * 0.4f), Offset(3f, 3f),
                    Size(size.width - 6f, size.height * 0.5f), CornerRadius(30.dp.toPx()))
                drawRoundRect(Color(0x146A55C8), Offset(0f, size.height - 6.dp.toPx()), Size(size.width, 6.dp.toPx()), CornerRadius(30.dp.toPx()))
            }
            .padding(pad), content = content)
    }
    @Composable override fun Ring(f: Float, hue: Color, size: Dp, stroke: Dp, content: @Composable BoxScope.() -> Unit) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val s = stroke.toPx() * 1.5f; val i = s / 2 + 2.dp.toPx(); val sz = Size(this.size.width - 2 * i, this.size.height - 2 * i)
                drawArc(track, 0f, 360f, false, Offset(i, i), sz, style = Stroke(s))
                // tube : ombre portée, couleur, reflet
                drawArc(hue.copy(alpha = 0.35f), -90f, 360f * f, false, Offset(i, i + 3.dp.toPx()), sz, style = Stroke(s, cap = StrokeCap.Round))
                drawArc(hue, -90f, 360f * f, false, Offset(i, i), sz, style = Stroke(s, cap = StrokeCap.Round))
                drawArc(Color.White.copy(alpha = 0.45f), -90f, 360f * f, false, Offset(i, i - s * 0.18f), sz, style = Stroke(s * 0.25f, cap = StrokeCap.Round))
            }
            content()
        }
    }
    @Composable override fun Bar(f: Float, hue: Color, modifier: Modifier, h: Dp) {
        Box(modifier.fillMaxWidth().height(h + 3.dp).clip(CircleShape).background(track)) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(CircleShape).background(Brush.verticalGradient(listOf(hue.copy(alpha = 0.75f).compositeOver(Color.White), hue))))
        }
    }
    @Composable override fun Button(text: String, icon: ImageVector, modifier: Modifier) {
        val shape = CircleShape
        Row(modifier.shadow(12.dp, shape, ambientColor = accent, spotColor = accent).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF9A82FF), accent))).padding(vertical = 17.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp), tint = Color.White); Spacer(Modifier.width(8.dp)); K(text, 17.sp, Color.White, FontWeight.Bold, Comfortaa)
        }
    }
    @Composable override fun Chip(icon: ImageVector, hue: Color, size: Dp, slot: Int) {
        Box(Modifier.size(size).shadow(6.dp, RoundedCornerShape(size * 0.36f), ambientColor = hue, spotColor = hue).clip(RoundedCornerShape(size * 0.36f))
            .background(Brush.verticalGradient(listOf(hue.copy(alpha = 0.7f).compositeOver(Color.White), hue))), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(size * 0.56f), tint = Color.White)
        }
    }
    @Composable override fun Nav(tab: Int, modifier: Modifier) {
        val shape = RoundedCornerShape(30.dp)
        Row(modifier.padding(horizontal = 14.dp, vertical = 18.dp).fillMaxWidth().shadow(18.dp, shape, ambientColor = Color(0xFF6A55C8), spotColor = Color(0xFF6A55C8))
            .clip(shape).background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            navItems.forEachIndexed { i, (icon, label) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (icon == null) Box(Modifier.size(54.dp).shadow(10.dp, CircleShape, ambientColor = accent, spotColor = accent).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF9A82FF), accent))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(30.dp), tint = Color.White)
                    } else {
                        val on = i == tab
                        Icon(icon, null, Modifier.size(24.dp), tint = if (on) accent else faint)
                        K(label, 11.sp, if (on) ink else faint, FontWeight.Bold, Comfortaa)
                    }
                }
            }
        }
    }
    override fun tileShape(i: Int): Shape = RoundedCornerShape(32.dp)
}

// ---- 4. Sprint : sport éditorial -----------------------------------------------------------------------------------------

/** Carte à coin coupé en biais. */
fun cut(corner: Float = 22f): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val c = corner * density.density
        val p = Path().apply { moveTo(0f, 0f); lineTo(size.width - c, 0f); lineTo(size.width, c); lineTo(size.width, size.height); lineTo(c, size.height); lineTo(0f, size.height - c); close() }
        return androidx.compose.ui.graphics.Outline.Generic(p)
    }
}

object Sprint : Kit(
    name = "Sprint", tagline = "Sport éditorial : noir et jaune fluo, chiffres géants en italique condensé, coins coupés",
    ink = Color.White, sub = Color(0xFF9A9AA2), faint = Color(0xFF55555C), track = Color(0xFF26262B),
    accent = Color(0xFFD4FF1F), onAccent = Color(0xFF0B0B0D),
    hues = Hues(forme = Color(0xFFD4FF1F), charge = Color(0xFFFFFFFF), repos = Color(0xFF9F8BFF), effort = Color(0xFFFF5A1F),
        steps = Color(0xFFD4FF1F), heart = Color(0xFFFF3B5C), sleep = Color(0xFF9F8BFF), food = Color(0xFFD4FF1F),
        protein = Color(0xFFFF3B5C), fat = Color(0xFFFFFFFF), carbs = Color(0xFFD4FF1F), gym = Color(0xFFD4FF1F), run = Color(0xFFFF5A1F), bike = Color(0xFF3BD1FF)),
    head = Narrow, headWeight = FontWeight.Bold, headItalic = true, num = Narrow, numWeight = FontWeight.Bold, numItalic = true, caps = true,
    bodyBase = Color(0xFF2A2A30), bodyMuscle = Color(0xFF3A3A42),
) {
    @Composable override fun Background() {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFF0B0B0D))
            // rayures en biais très discrètes en haut
            for (k in 0 until 14) {
                val x = k * 34.dp.toPx() - 200f
                drawLine(Color(0x0FFFFFFF), Offset(x, 0f), Offset(x + 260f, 260f), 10.dp.toPx())
            }
        }
    }
    @Composable override fun Card(modifier: Modifier, slot: Int, hue: Color?, pad: Dp, content: @Composable ColumnScope.() -> Unit) {
        Column(modifier.fillMaxWidth().clip(cut()).background(Color(0xFF16161A)).padding(pad), content = content)
    }
    @Composable override fun Ring(f: Float, hue: Color, size: Dp, stroke: Dp, content: @Composable BoxScope.() -> Unit) {
        // pas d'anneau : une jauge en segments obliques
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val n = 28; val r = this.size.minDimension / 2; val c = center
                for (k in 0 until n) {
                    val a = Math.toRadians(-90.0 + k * 360.0 / n).toFloat()
                    val on = k < (f * n).toInt()
                    val r1 = r * 0.74f; val r2 = r
                    drawLine(if (on) hue else track, Offset(c.x + r1 * kotlin.math.cos(a), c.y + r1 * kotlin.math.sin(a)),
                        Offset(c.x + r2 * kotlin.math.cos(a + 0.08f), c.y + r2 * kotlin.math.sin(a + 0.08f)), stroke.toPx() * 0.55f, cap = StrokeCap.Butt)
                }
            }
            content()
        }
    }
    @Composable override fun Bar(f: Float, hue: Color, modifier: Modifier, h: Dp) {
        Canvas(modifier.fillMaxWidth().height(h + 2.dp)) {
            val n = 22; val w = size.width / n
            for (k in 0 until n) {
                val p = Path().apply { moveTo(k * w + 3f, size.height); lineTo(k * w + w * 0.45f + 3f, 0f); lineTo(k * w + w - 1f, 0f); lineTo(k * w + w * 0.55f - 1f, size.height); close() }
                drawPath(p, if (k < f * n) hue else track)
            }
        }
    }
    @Composable override fun Button(text: String, icon: ImageVector, modifier: Modifier) {
        Row(modifier.clip(cut(16f)).background(accent).padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp), tint = onAccent); Spacer(Modifier.width(6.dp))
            K(text.uppercase(), 22.sp, onAccent, FontWeight.Bold, Narrow, italic = true, spacing = 0.5.sp)
        }
    }
    @Composable override fun Chip(icon: ImageVector, hue: Color, size: Dp, slot: Int) {
        Box(Modifier.size(size).clip(cut(8f)).background(hue), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(size * 0.6f), tint = if (hue == Color.White || hue == accent) onAccent else Color.White)
        }
    }
    @Composable override fun Nav(tab: Int, modifier: Modifier) {
        Row(modifier.fillMaxWidth().background(Color(0xFF0B0B0D)).padding(horizontal = 10.dp).padding(top = 8.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            navItems.forEachIndexed { i, (icon, label) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (icon == null) Box(Modifier.size(width = 60.dp, height = 46.dp).clip(cut(12f)).background(accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(30.dp), tint = onAccent)
                    } else {
                        val on = i == tab
                        Icon(icon, null, Modifier.size(22.dp), tint = if (on) accent else faint)
                        K(label.uppercase(), 12.sp, if (on) Color.White else faint, FontWeight.Bold, Narrow, italic = true, spacing = 0.5.sp)
                        Box(Modifier.padding(top = 3.dp).size(width = 18.dp, height = 3.dp).background(if (on) accent else Color.Transparent))
                    }
                }
            }
        }
    }
    @Composable override fun Title(text: String) = K(text.uppercase(), 50.sp, ink, FontWeight.Bold, Narrow, italic = true, spacing = (-0.5).sp)
    override fun tileShape(i: Int): Shape = cut(20f)
}

val KITS = listOf(Bloc, Verre, Argile, Sprint)

private val navItems = listOf(Icons.Rounded.GridView to "Accueil", Icons.Rounded.Restaurant to "Journal", null to "",
    Icons.AutoMirrored.Rounded.DirectionsRun to "Activités", Icons.Rounded.Settings to "Réglages")

// ---- corps (silhouettes de l'app, un tracé par muscle) ------------------------------------------------------------------

private data class BodyArt(val w: Float, val h: Float, val parts: List<Pair<String, Path>>)
private val bodies = HashMap<String, BodyArt>()
private fun body(side: String): BodyArt = bodies.getOrPut(side) {
    val txt = java.io.File(System.getProperty("superhealth.assets", "assets"), "body/avatar_male_$side.json").readText()
    val o = kotlinx.serialization.json.Json.parseToJsonElement(txt) as kotlinx.serialization.json.JsonObject
    val num = { k: String -> (o[k] as kotlinx.serialization.json.JsonPrimitive).content.toFloat() }
    val parts = (o["paths"] as kotlinx.serialization.json.JsonArray).map { p ->
        val po = p as kotlinx.serialization.json.JsonObject
        (po["id"] as kotlinx.serialization.json.JsonPrimitive).content to PathParser().parsePathString((po["d"] as kotlinx.serialization.json.JsonPrimitive).content).toPath()
    }
    BodyArt(num("w"), num("h"), parts)
}

/** Silhouette recadrée de [from] à [to] (fractions de la hauteur), muscles [target] dans [hue]. */
@Composable
fun Body(side: String, target: Set<String>, hue: Color, from: Float, to: Float, modifier: Modifier) {
    val k = Q
    val art = rememberBody(side)
    Canvas(modifier) {
        val y0 = art.h * from; val hh = art.h * (to - from)
        val s = minOf(size.width / art.w, size.height / hh)
        val dx = (size.width - art.w * s) / 2; val dy = (size.height - hh * s) / 2
        clipRect {
            translate(dx, dy - y0 * s) {
                scale(s, s, pivot = Offset.Zero) {
                    art.parts.forEach { (id, p) ->
                        drawPath(p, when { id == "body" -> k.bodyBase; id == "bone" -> k.bodyBase; id in target -> hue; else -> k.bodyMuscle })
                    }
                }
            }
        }
    }
}
@Composable private fun rememberBody(side: String) = androidx.compose.runtime.remember(side) { body(side) }

private fun image(path: String) = runCatching {
    org.jetbrains.skia.Image.makeFromEncoded(java.io.File(System.getProperty("superhealth.assets", "assets"), path).readBytes()).toComposeImageBitmap()
}.getOrNull()

// ---- écrans -------------------------------------------------------------------------------------------------------------

@Composable
fun Screen2(kit: Kit, tab: Int, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalKit provides kit) {
        Box(Modifier.fillMaxSize()) {
            kit.Background()
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 52.dp), verticalArrangement = Arrangement.spacedBy(kit.gap), content = content)
            if (tab >= 0) kit.Nav(tab, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Head(title: String, over: String = "") {
    val k = Q
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) { if (over.isNotEmpty()) KLabel(over); k.Title(title) }
        k.Chip(Icons.Rounded.MoreHoriz, k.accent, 40.dp)
    }
}

@Composable
private fun CardHead(title: String, icon: ImageVector, hue: Color, slot: Int, trailing: String? = null) {
    val k = Q
    Row(verticalAlignment = Alignment.CenterVertically) {
        k.Chip(icon, hue, 30.dp, slot); Spacer(Modifier.width(10.dp))
        if (k.caps) K(title.uppercase(), if (k.head == Narrow) 18.sp else 13.sp, k.inkOn(slot), FontWeight.Black, if (k.head == Narrow) Narrow else k.body,
            Modifier.weight(1f), italic = k.head == Narrow, spacing = if (k.head == Narrow) 0.5.sp else 1.2.sp)
        else K(title, 16.sp, k.inkOn(slot), FontWeight.Bold, if (k.head == Comfortaa) Comfortaa else k.body, Modifier.weight(1f))
        trailing?.let { K(it, 12.sp, k.subOn(slot), FontWeight.Medium) }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = k.subOn(slot))
    }
}

@Composable
fun Home2(kit: Kit) = Screen2(kit, 0) {
    val k = Q; val h = k.hues
    Head("Aujourd'hui", "Vendredi 30 octobre")
    // A — Scores du jour
    k.Card(Modifier, 0, h.forme, 16.dp) {
        CardHead("Scores du jour", Icons.Rounded.Spa, h.forme, 0)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            k.Ring(0.78f, h.forme, 128.dp, 12.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { KNum("78", 40.sp, k.inkOn(0)); KLabel("Prêt", k.subOn(0)) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Triple("Charge", 64, h.charge), Triple("Repos", 84, h.repos), Triple("Effort", 42, h.effort)).forEach { (n, v, c) ->
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) { KLabel(n, k.subOn(0), Modifier.weight(1f)); KNum("$v", 20.sp, k.inkOn(0)) }
                        Spacer(Modifier.height(3.dp)); k.Bar(v / 100f, c, Modifier, 6.dp)
                    }
                }
            }
        }
    }
    // B — Calories
    k.Card(Modifier, 1, h.food, 16.dp) {
        CardHead("Calories restantes", Icons.Rounded.LocalFireDepartment, h.food, 1, "sur 2 280")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(128.dp)) { KNum("927", 56.sp, k.inkOn(1)); KLabel("kcal", k.subOn(1)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("Protéines", 98 to 150, h.protein), Triple("Lipides", 52 to 76, h.fat), Triple("Glucides", 141 to 285, h.carbs)).forEach { (n, v, c) ->
                    Column {
                        Row { K(n, 12.sp, k.subOn(1), FontWeight.Medium, modifier = Modifier.weight(1f)); K("${v.first} / ${v.second} g", 12.sp, k.inkOn(1), FontWeight.Bold) }
                        Spacer(Modifier.height(3.dp)); k.Bar(v.first.toFloat() / v.second, c, Modifier, 6.dp)
                    }
                }
            }
        }
    }
    // C — Sommeil, D — Pas / Cœur
    Row(horizontalArrangement = Arrangement.spacedBy(k.gap)) {
        k.Card(Modifier.weight(1.2f), 2, h.sleep, 14.dp) {
            CardHead("Sommeil", Icons.Rounded.Bedtime, h.sleep, 2)
            Spacer(Modifier.height(8.dp))
            KNum("7 h 32", 30.sp, k.inkOn(2)); KLabel("Score 84", k.subOn(2))
            Spacer(Modifier.height(8.dp)); Hypno(h.sleep, k.inkOn(2).copy(alpha = 0.35f))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(k.gap)) {
            k.Card(Modifier, 3, h.steps, 14.dp) {
                KLabel("Pas", k.subOn(3)); KNum("6 240", 26.sp, k.inkOn(3)); Spacer(Modifier.height(4.dp)); k.Bar(0.78f, h.steps, Modifier, 6.dp)
            }
            k.Card(Modifier, 4, h.heart, 14.dp) {
                KLabel("FC repos", k.subOn(4))
                Row(verticalAlignment = Alignment.Bottom) { KNum("54", 26.sp, k.inkOn(4)); K(" bpm", 12.sp, k.subOn(4), modifier = Modifier.padding(bottom = 4.dp)) }
            }
        }
    }
}

private val runs = listOf(2 to 18, 1 to 42, 2 to 30, 3 to 16, 2 to 34, 1 to 28, 2 to 22, 3 to 24, 4 to 5, 2 to 38, 3 to 30, 2 to 26, 4 to 6, 3 to 34, 2 to 20)

@Composable
private fun Hypno(hue: Color, wake: Color) {
    Canvas(Modifier.fillMaxWidth().height(38.dp)) {
        val total = runs.sumOf { it.second }.toFloat(); val th = 7.dp.toPx()
        fun y(s: Int) = when (s) { 4 -> 0f; 3 -> 1f; 2 -> 2f; else -> 3f } / 3f * (size.height - th)
        var x = 0f
        runs.forEach { (st, m) ->
            val w = m / total * size.width
            drawRoundRect(if (st == 4) wake else hue.copy(alpha = when (st) { 3 -> 0.55f; 2 -> 0.8f; else -> 1f }), Offset(x, y(st)), Size(maxOf(th, w), th), CornerRadius(th / 2))
            x += w
        }
    }
}

// ---- Journal : résumé compact ----------------------------------------------------------------------------------------

@Composable
fun Journal2(kit: Kit) = Screen2(kit, 1) {
    val k = Q; val h = k.hues
    Head("Journal")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("L" to 26, "M" to 27, "M" to 28, "J" to 29, "V" to 30, "S" to 31, "D" to 1).forEachIndexed { i, (d, n) ->
            val on = i == 4
            Column(Modifier.width(44.dp).clip(if (k == Sprint) cut(8f) else if (k == Bloc) RoundedCornerShape(10.dp) else CircleShape)
                .background(if (on) k.accent else Color.Transparent)
                .then(if (on && k == Bloc) Modifier.border(2.5.dp, k.ink, RoundedCornerShape(10.dp)) else Modifier).padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                K(d, 11.sp, if (on) k.onAccent else k.sub, FontWeight.Bold)
                KNum("$n", 18.sp, if (on) k.onAccent else if (i > 4) k.faint else k.ink)
            }
        }
    }
    // résumé compact : une seule rangée
    k.Card(Modifier, 1, h.food, 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            k.Ring(1353f / 2280, h.food, 54.dp, 6.dp) { KNum("59", 14.sp, k.inkOn(1)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.width(92.dp)) {
                KNum("927", 24.sp, k.inkOn(1)); K("kcal restantes", 11.sp, k.subOn(1), FontWeight.Medium)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("P", 98 to 150, h.protein), Triple("L", 52 to 76, h.fat), Triple("G", 141 to 285, h.carbs)).forEach { (n, v, c) ->
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) { K(n, 11.sp, k.subOn(1), FontWeight.Bold); Spacer(Modifier.width(3.dp)); K("${v.first}", 14.sp, k.inkOn(1), FontWeight.Bold) }
                        Spacer(Modifier.height(4.dp)); k.Bar(v.first.toFloat() / v.second, c, Modifier, 5.dp)
                    }
                }
            }
        }
    }
    listOf(
        Triple("Petit-déjeuner", "8:10", listOf(Food("Œufs brouillés", "2 œufs", 182, "foods/pack/070.png"), Food("Pain complet", "60 g", 148, "foods/pack/083.png"),
            Food("Banane", "1 moyenne", 105, "foods/pack/017.png"))),
        Triple("Déjeuner", "12:40", listOf(Food("Poulet rôti", "150 g", 285, "foods/pack/076.png"), Food("Riz basmati", "180 g", 230, "foods/pack/139.png"),
            Food("Brocolis", "120 g", 41, "foods/pack/058.png"), Food("Avocat", "½", 120, "foods/pack/036.png"))),
        Triple("Collation", "16:30", listOf(Food("Yaourt grec", "150 g", 146, "foods/gen/i103.png"), Food("Pomme", "1 moyenne", 95, "foods/pack/022.png"))),
    ).forEachIndexed { mi, (name, time, foods) ->
        k.Card(Modifier, -1, null, 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (k.caps) K(name.uppercase(), if (k.head == Narrow) 20.sp else 13.sp, k.ink, FontWeight.Black, if (k.head == Narrow) Narrow else k.body, Modifier.weight(1f),
                    italic = k.head == Narrow, spacing = if (k.head == Narrow) 0.5.sp else 1.2.sp)
                else K(name, 17.sp, k.ink, FontWeight.Bold, if (k.head == Comfortaa) Comfortaa else k.body, Modifier.weight(1f))
                K("$time · ", 12.sp, k.faint); KNum("${foods.sumOf { it.kcal }} kcal", 15.sp, k.sub)
            }
            Spacer(Modifier.height(4.dp))
            foods.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    val img = image(f.icon)
                    Box(Modifier.size(34.dp).clip(if (k == Sprint) cut(6f) else if (k == Bloc) RoundedCornerShape(8.dp) else RoundedCornerShape(12.dp))
                        .background(if (k == Verre || k == Sprint) Color(0x1AFFFFFF) else Color(0x0F000000))
                        .then(if (k == Bloc) Modifier.border(2.dp, k.ink, RoundedCornerShape(8.dp)) else Modifier), contentAlignment = Alignment.Center) {
                        if (img != null) Image(img, null, Modifier.size(26.dp), filterQuality = FilterQuality.None)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { K(f.name, 14.sp, k.ink, FontWeight.SemiBold); K(f.qty, 12.sp, k.sub) }
                    K("${f.kcal}", 14.sp, k.ink, FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---- Activités : semaine fine, grandes tuiles de musculation --------------------------------------------------------------

private data class Day(val name: String, val groups: String, val side: String, val target: Set<String>, val from: Float, val to: Float,
                       val done: Boolean, val next: Boolean)
private val days = listOf(
    Day("Push", "Pecs · Épaules · Triceps", "front", setOf("chest", "frontDelts", "sideDelts"), 0.1f, 0.5f, done = true, next = false),
    Day("Pull", "Dos · Biceps", "back", setOf("lats", "upperBack", "rearDelts", "upperTraps"), 0.1f, 0.5f, done = true, next = false),
    Day("Jambes", "Cuisses · Mollets", "front", setOf("quads", "adductors", "abductors", "calves"), 0.5f, 1f, done = false, next = true),
    Day("Full body", "Tout le corps", "front", setOf("chest", "abs", "quads", "biceps", "frontDelts", "obliques"), 0f, 1f, done = false, next = false),
)

@Composable
fun Activities2(kit: Kit) = Screen2(kit, 3) {
    val k = Q; val h = k.hues
    Head("Activités")
    // semaine, en une ligne fine
    k.Card(Modifier, 5, null, 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(96.dp)) {
                KNum("4 activités", 16.sp, k.inkOn(5)); K("3 h 10 · 18,4 km", 11.sp, k.subOn(5), FontWeight.Medium)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                val acts = listOf(Icons.AutoMirrored.Rounded.DirectionsRun to h.run, Icons.Rounded.FitnessCenter to h.gym, null,
                    Icons.AutoMirrored.Rounded.DirectionsBike to h.bike, Icons.Rounded.FitnessCenter to h.gym, null, null)
                listOf("L", "M", "M", "J", "V", "S", "D").forEachIndexed { i, d ->
                    val a = acts[i]
                    val shape = if (k == Sprint) cut(6f) else if (k == Bloc) RoundedCornerShape(6.dp) else CircleShape
                    Box(Modifier.size(28.dp).clip(shape).background(a?.second ?: k.track)
                        .then(if (k == Bloc) Modifier.border(2.dp, k.ink, shape) else Modifier), contentAlignment = Alignment.Center) {
                        if (a != null) Icon(a.first, null, Modifier.size(16.dp), tint = if (k == Verre || k == Sprint) Color(0xFF111111) else Color.White)
                        else K(d, 11.sp, k.subOn(5), FontWeight.Bold)
                    }
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.Bottom) { k.Section("Musculation", Modifier.weight(1f)); KLabel("PPL · semaine 3") }
    // 2 × 2 grandes tuiles, toutes visibles
    days.chunked(2).forEachIndexed { r, pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(k.gap)) {
            pair.forEachIndexed { c, d -> DayTile(d, r * 2 + c, Modifier.weight(1f)) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(k.gap)) {
        listOf(Triple("Séance libre", Icons.Rounded.Bolt, h.charge), Triple("Coach", Icons.Rounded.AutoAwesome, h.repos)).forEachIndexed { i, (n, ic, c) ->
            k.Card(Modifier.weight(1f), 5 + i, c, 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    k.Chip(ic, c, 30.dp, 5 + i); Spacer(Modifier.width(10.dp))
                    K(if (k.caps) n.uppercase() else n, if (k.head == Narrow) 18.sp else 14.sp, k.inkOn(5 + i), FontWeight.Bold,
                        if (k.head == Narrow) Narrow else if (k.head == Comfortaa) Comfortaa else k.body, italic = k.head == Narrow)
                }
            }
        }
    }
    k.Card(Modifier, -1, null, 12.dp) {
        listOf(Triple(Icons.AutoMirrored.Rounded.DirectionsRun, h.run, listOf("Course à pied", "Hier", "5,2 km")),
            Triple(Icons.Rounded.FitnessCenter, h.gym, listOf("Pull", "Mardi", "52 min"))).forEach { (ic, c, t) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                k.Chip(ic, c, 32.dp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { K(t[0], 14.sp, k.ink, FontWeight.SemiBold); K(t[1], 12.sp, k.sub) }
                KNum(t[2], 16.sp)
            }
        }
    }
}

@Composable
private fun DayTile(d: Day, i: Int, modifier: Modifier) {
    val k = Q
    val hue = k.hues.gym
    val slot = listOf(2, 3, 0, 4)[i]
    k.Card(modifier.height(206.dp), slot, hue, 12.dp) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Body(d.side, d.target, if (k == Bloc) Color(0xFFFF3D7F) else if (k == Sprint) k.accent else hue, d.from, d.to, Modifier.fillMaxSize())
            if (d.done) Box(Modifier.align(Alignment.TopEnd).size(24.dp).clip(if (k == Sprint) cut(6f) else CircleShape)
                .background(if (k == Bloc) Color.White else k.hues.forme).then(if (k == Bloc) Modifier.border(2.dp, k.ink, CircleShape) else Modifier),
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = if (k == Bloc || k == Verre || k == Sprint) Color(0xFF111111) else Color.White) }
            if (d.next) Box(Modifier.align(Alignment.TopStart).clip(if (k == Sprint) cut(6f) else if (k == Bloc) RoundedCornerShape(6.dp) else CircleShape)
                .background(k.accent).padding(horizontal = 8.dp, vertical = 3.dp)) {
                K(if (k.caps) "PROCHAINE" else "Prochaine", 10.sp, k.onAccent, FontWeight.Black, spacing = if (k.caps) 1.sp else 0.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        if (k.head == Narrow) K(d.name.uppercase(), 28.sp, k.inkOn(slot), FontWeight.Bold, Narrow, italic = true)
        else K(if (k.caps) d.name.uppercase() else d.name, 20.sp, k.inkOn(slot), FontWeight.Black, k.head)
        K(d.groups, 11.sp, k.subOn(slot), FontWeight.Medium, maxLines = 1)
    }
}

// ---- Séance (après un toucher sur la tuile) --------------------------------------------------------------------------------

@Composable
fun Session2(kit: Kit) = Screen2(kit, -1) {
    val k = Q; val h = k.hues
    val d = days[2]
    Row(verticalAlignment = Alignment.CenterVertically) {
        k.Chip(Icons.AutoMirrored.Rounded.ArrowBack, k.ink, 40.dp)
        Spacer(Modifier.weight(1f)); KLabel("PPL · semaine 3 · jour 3")
    }
    k.Card(Modifier, 0, h.gym, 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (k.head == Narrow) K(d.name.uppercase(), 54.sp, k.inkOn(0), FontWeight.Bold, Narrow, italic = true)
                else K(if (k.caps) d.name.uppercase() else d.name, 36.sp, k.inkOn(0), FontWeight.Black, k.head)
                K(d.groups, 13.sp, k.subOn(0), FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf("5" to "exercices", "17" to "séries", "~60" to "min").forEach { (v, l) ->
                        Column { KNum(v, 22.sp, k.inkOn(0)); K(l, 11.sp, k.subOn(0)) }
                    }
                }
            }
            Body(d.side, d.target, if (k == Bloc) Color(0xFFFF3D7F) else if (k == Sprint) k.accent else h.gym, d.from, d.to, Modifier.width(120.dp).height(150.dp))
        }
    }
    k.Card(Modifier, -1, null, 12.dp) {
        listOf(
            Triple("Squat barre", "4 × 5 · 100 kg", "ex/1a25c6f170d8803d8231d083fdd65458_a.webp"),
            Triple("Soulevé de terre roumain", "3 × 8 · 80 kg", "ex/1a35c6f170d880bebc64f4dce0d5f5f1_a.webp"),
            Triple("Fente bulgare", "3 × 10 · 2 × 18 kg", "ex/1a25c6f170d880d8bb7fef2de64a1e60_a.webp"),
            Triple("Presse à 45°", "3 × 12 · 160 kg", "ex/1a25c6f170d88079a926dde776766181_a.webp"),
            Triple("Mollets à la presse", "4 × 15 · 120 kg", "ex/1a15c6f170d88074b3d4e1548e32c508_a.webp"),
        ).forEachIndexed { i, (n, s, img) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val bmp = image(img)
                Box(Modifier.size(56.dp).clip(if (k == Sprint) cut(8f) else RoundedCornerShape(14.dp)).background(Color.White)
                    .then(if (k == Bloc) Modifier.border(2.dp, k.ink, RoundedCornerShape(14.dp)) else Modifier), contentAlignment = Alignment.Center) {
                    if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { K(n, 15.sp, k.ink, FontWeight.SemiBold); K(s, 13.sp, k.sub) }
                K("${i + 1}", 13.sp, k.faint, FontWeight.Bold)
            }
        }
    }
    k.Card(Modifier, 5, null, 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            k.Chip(Icons.Rounded.Check, h.forme, 30.dp, 5); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                K("La dernière fois · 23 octobre", 13.sp, k.inkOn(5), FontWeight.SemiBold)
                K("58 min · 9 840 kg soulevés · squat +2,5 kg", 12.sp, k.subOn(5))
            }
        }
    }
    Spacer(Modifier.weight(1f))
    k.Button("Lancer la séance", Icons.Rounded.PlayArrow, Modifier.fillMaxWidth().padding(bottom = 30.dp))
}
