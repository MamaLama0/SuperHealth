package app.superhealth.platform

import app.superhealth.platform.*

import kotlin.math.floor
import kotlin.math.pow

/** java.lang.Math : mêmes arrondis que la JVM (Math.round = floor(x + 0.5)). */
object Math {
    const val PI = kotlin.math.PI
    const val E = kotlin.math.E
    fun signum(x: Double) = kotlin.math.sign(x)
    fun signum(x: Float) = kotlin.math.sign(x)
    fun floorMod(a: Long, b: Long): Long = a.mod(b)
    fun floorMod(a: Int, b: Int): Int = a.mod(b)
    fun floorDiv(a: Long, b: Long): Long = kotlin.math.floor(a.toDouble() / b).toLong()
    fun floorDiv(a: Int, b: Int): Int = kotlin.math.floor(a.toDouble() / b).toInt()
    fun pow(a: Double, b: Double) = a.pow(b)
    fun log(x: Double) = kotlin.math.ln(x)
    fun log10(x: Double) = kotlin.math.log10(x)
    fun exp(x: Double) = kotlin.math.exp(x)
    fun sqrt(x: Double) = kotlin.math.sqrt(x)
    fun abs(x: Double) = kotlin.math.abs(x)
    fun abs(x: Int) = kotlin.math.abs(x)
    fun abs(x: Long) = kotlin.math.abs(x)
    fun abs(x: Float) = kotlin.math.abs(x)
    fun round(x: Double): Long = floor(x + 0.5).toLong()
    fun round(x: Float): Int = floor(x + 0.5f).toInt()
    fun floor(x: Double) = kotlin.math.floor(x)
    fun ceil(x: Double) = kotlin.math.ceil(x)
    fun max(a: Double, b: Double) = kotlin.math.max(a, b)
    fun min(a: Double, b: Double) = kotlin.math.min(a, b)
    fun max(a: Int, b: Int) = kotlin.math.max(a, b)
    fun min(a: Int, b: Int) = kotlin.math.min(a, b)
    fun max(a: Long, b: Long) = kotlin.math.max(a, b)
    fun min(a: Long, b: Long) = kotlin.math.min(a, b)
    fun toRadians(d: Double) = d * kotlin.math.PI / 180
    fun toDegrees(r: Double) = r * 180 / kotlin.math.PI
    fun sin(x: Double) = kotlin.math.sin(x)
    fun cos(x: Double) = kotlin.math.cos(x)
    fun atan2(y: Double, x: Double) = kotlin.math.atan2(y, x)
    fun hypot(a: Double, b: Double) = kotlin.math.hypot(a, b)
}

class JavaClassName(val simpleName: String, val name: String)

val Any.javaClass: JavaClassName get() = JavaClassName(this::class.simpleName ?: "Erreur", this::class.qualifiedName ?: "Erreur")

/** Teinte (0–360°) d'une couleur, comme android.graphics.Color.colorToHSV(…)[0]. */
fun hueDegrees(c: androidx.compose.ui.graphics.Color): Float {
    val r = c.red; val g = c.green; val b = c.blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
    if (d == 0f) return 0f
    val h = when (max) {
        r -> ((g - b) / d).let { if (it < 0) it + 6 else it }
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    }
    return h * 60f
}

/** java.util.Date réduit à son horodatage (ms). */
data class Date(val time: Long) : Comparable<Date> {
    constructor() : this(nowMs())
    override fun compareTo(other: Date) = time.compareTo(other.time)
    fun before(o: Date) = time < o.time
    fun after(o: Date) = time > o.time
}

/** android.graphics.Color.parseColor : « #RRGGBB » ou « #AARRGGBB ». */
fun parseColor(s: String): androidx.compose.ui.graphics.Color {
    val h = s.trim().removePrefix("#")
    val v = h.toLong(16)
    return when (h.length) {
        6 -> androidx.compose.ui.graphics.Color(0xFF000000 or v)
        8 -> androidx.compose.ui.graphics.Color(v)
        else -> throw IllegalArgumentException("couleur inconnue : $s")
    }
}

/** Widgets de l'écran d'accueil (WidgetKit) : rechargés quand l'app change de style ou de données. */
expect fun refreshWidgets()
