package app.superhealth.platform

import app.superhealth.platform.*

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/*
 * Texte : Locale, String.format, NumberFormat, Normalizer et URLEncoder de la JVM, limités à ce que l'app utilise.
 * Le français écrit les décimales avec une virgule et groupe les milliers par une espace fine insécable, comme Android.
 */

class Locale(val language: String, val country: String = "") {
    companion object {
        val FRENCH = Locale("fr")
        val FRANCE = Locale("fr", "FR")
        val ENGLISH = Locale("en")
        val US = Locale("en", "US")
        val UK = Locale("en", "GB")
        val ROOT = Locale("")
        private var default: Locale? = null
        fun getDefault(): Locale = default ?: Locale(systemLanguage())
        fun setDefault(l: Locale) { default = l }
    }

    internal val decimal: Char get() = if (language == "fr") ',' else '.'
    internal val grouping: Char get() = if (language == "fr") ' ' else ','

    override fun equals(other: Any?) = other is Locale && other.language == language && other.country == country
    override fun hashCode() = language.hashCode() * 31 + country.hashCode()
    override fun toString() = if (country.isEmpty()) language else "${language}_$country"
    fun toLanguageTag() = if (country.isEmpty()) language else "$language-$country"
}

fun String.uppercase(locale: Locale): String = uppercase()
fun String.lowercase(locale: Locale): String = lowercase()
fun Char.uppercase(locale: Locale): String = uppercase()
fun Char.titlecase(locale: Locale): String = titlecase()

fun String.format(vararg args: Any?): String = Printf.format(Locale.getDefault(), this, args)
fun String.format(locale: Locale?, vararg args: Any?): String = Printf.format(locale ?: Locale.ROOT, this, args)
fun String.Companion.format(fmt: String, vararg args: Any?): String = Printf.format(Locale.getDefault(), fmt, args)
fun String.Companion.format(locale: Locale?, fmt: String, vararg args: Any?): String = Printf.format(locale ?: Locale.ROOT, fmt, args)

/** printf de la JVM : %s %d %f %e %x %c %b %%, index (%1$s), drapeaux « -+0,( # », largeur et précision. */
object Printf {
    private val spec = Regex("%(\\d+\\$)?([-#+ 0,(<]*)?(\\d+)?(\\.\\d+)?([a-zA-Z%])")

    fun format(locale: Locale, fmt: String, args: Array<out Any?>): String {
        var next = 0
        return spec.replace(fmt) { m ->
            val conv = m.groupValues[5]
            if (conv == "%") return@replace "%"
            if (conv == "n") return@replace "\n"
            val idx = m.groupValues[1].takeIf { it.isNotEmpty() }?.dropLast(1)?.toInt()?.minus(1) ?: next++
            val flags = m.groupValues[2]
            val width = m.groupValues[3].toIntOrNull() ?: 0
            val prec = m.groupValues[4].takeIf { it.isNotEmpty() }?.drop(1)?.toInt()
            val arg = args.getOrNull(idx)
            val body = when (conv) {
                "d" -> integer(arg, flags, locale)
                "f" -> fixed(toDouble(arg), prec ?: 6, flags, locale)
                "e", "E" -> sci(toDouble(arg), prec ?: 6, conv == "E")
                "g", "G" -> fixed(toDouble(arg), prec ?: 6, flags, locale)
                "x", "X" -> (arg as? Number)?.toLong()?.toString(16)?.let { if (conv == "X") it.uppercase() else it } ?: "null"
                "c" -> arg.toString()
                "b", "B" -> (arg != null && arg != false).toString()
                "s", "S" -> (if (prec != null) arg.toString().take(prec) else arg.toString()).let { if (conv == "S") it.uppercase() else it }
                else -> m.value
            }
            when {
                body.length >= width -> body
                '-' in flags -> body.padEnd(width)
                '0' in flags && conv in setOf("d", "f") -> {
                    val neg = body.startsWith('-') || body.startsWith('+')
                    if (neg) body[0] + body.drop(1).padStart(width - 1, '0') else body.padStart(width, '0')
                }
                else -> body.padStart(width)
            }
        }
    }

    private fun toDouble(a: Any?): Double = when (a) { is Number -> a.toDouble(); null -> Double.NaN; else -> a.toString().toDoubleOrNull() ?: Double.NaN }

    private fun integer(a: Any?, flags: String, locale: Locale): String {
        val v = (a as? Number)?.toLong() ?: return a.toString()
        var s = abs(v).toString()
        if (',' in flags) s = group(s, locale.grouping)
        return sign(v < 0, flags) + s
    }

    private fun sign(neg: Boolean, flags: String) = when { neg -> "-"; '+' in flags -> "+"; ' ' in flags -> " "; else -> "" }

    fun fixed(v: Double, prec: Int, flags: String, locale: Locale): String {
        if (v.isNaN()) return "NaN"
        if (v.isInfinite()) return if (v > 0) "Infinity" else "-Infinity"
        val neg = v < 0 || (v == 0.0 && 1.0 / v < 0)
        val scaled = abs(v) * 10.0.pow(prec)
        val r = halfUp(scaled)
        val digits = r.toString().padStart(prec + 1, '0')
        var intPart = digits.dropLast(prec).ifEmpty { "0" }
        val frac = digits.takeLast(prec)
        if (',' in flags) intPart = group(intPart, locale.grouping)
        val isZero = r == 0L
        return sign(neg && !isZero, flags) + intPart + (if (prec > 0) locale.decimal + frac else "")
    }

    /** Arrondi « HALF_UP » de Java, corrigé des erreurs de représentation binaire (2.675 → 2.68). */
    private fun halfUp(x: Double): Long {
        val f = kotlin.math.floor(x)
        val d = x - f
        return if (d >= 0.5 - 1e-9) f.toLong() + 1 else f.toLong()
    }

    private fun sci(v: Double, prec: Int, upper: Boolean): String {
        if (v == 0.0) return "0." + "0".repeat(prec) + (if (upper) "E+00" else "e+00")
        var e = kotlin.math.floor(kotlin.math.log10(abs(v))).toInt()
        var m = v / 10.0.pow(e)
        if (abs(halfUp(abs(m) * 10.0.pow(prec)) / 10.0.pow(prec)) >= 10) { e++; m = v / 10.0.pow(e) }
        val mant = fixed(m, prec, "", Locale.ROOT)
        return mant + (if (upper) "E" else "e") + (if (e < 0) "-" else "+") + abs(e).toString().padStart(2, '0')
    }

    fun group(digits: String, sep: Char): String {
        val sb = StringBuilder()
        digits.forEachIndexed { i, c -> if (i > 0 && (digits.length - i) % 3 == 0) sb.append(sep); sb.append(c) }
        return sb.toString()
    }
}

/** NumberFormat.getNumberInstance(locale) : regroupement des milliers, nombre de décimales borné. */
class NumberFormat private constructor(private val locale: Locale, private val percent: Boolean = false) {
    var minimumFractionDigits = 0
    var maximumFractionDigits = 3
    var isGroupingUsed = true
    var maximumIntegerDigits = Int.MAX_VALUE

    companion object {
        fun getNumberInstance(locale: Locale) = NumberFormat(locale)
        fun getInstance(locale: Locale) = NumberFormat(locale)
        fun getNumberInstance() = NumberFormat(Locale.getDefault())
        fun getIntegerInstance(locale: Locale) = NumberFormat(locale).apply { maximumFractionDigits = 0 }
        fun getPercentInstance(locale: Locale) = NumberFormat(locale, percent = true).apply { maximumFractionDigits = 0 }
    }

    fun format(v: Number): String = format(v.toDouble())

    fun format(v0: Double): String {
        val v = if (percent) v0 * 100 else v0
        var s = Printf.fixed(v, maximumFractionDigits, if (isGroupingUsed) "," else "", locale)
        if (maximumFractionDigits > minimumFractionDigits && s.contains(locale.decimal)) {
            val (i, f) = s.split(locale.decimal)
            var frac = f
            while (frac.length > minimumFractionDigits && frac.endsWith('0')) frac = frac.dropLast(1)
            s = if (frac.isEmpty()) i else i + locale.decimal + frac
        }
        if (s == "-0") s = "0"
        return if (percent) (if (locale.language == "fr") "$s %" else "$s%") else s
    }

    fun format(v: Long): String = format(v.toDouble())
    fun format(v: Int): String = format(v.toDouble())

    fun parse(s: String): Number? = s.replace(locale.grouping.toString(), "").replace(" ", "").replace(locale.decimal, '.').toDoubleOrNull()
}

/** Normalizer.normalize(s, NFD) suivi du retrait des diacritiques : on rend directement la forme sans accents. */
object Normalizer {
    enum class Form { NFD, NFC, NFKD, NFKC }

    private val map: Map<Char, String> by lazy {
        val pairs = listOf(
            "àáâãäåāăą" to "a", "ÀÁÂÃÄÅĀĂĄ" to "A", "çćĉċč" to "c", "ÇĆĈĊČ" to "C", "ďđ" to "d", "ĎĐ" to "D",
            "èéêëēĕėęě" to "e", "ÈÉÊËĒĔĖĘĚ" to "E", "ĝğġģ" to "g", "ĜĞĠĢ" to "G", "ĥħ" to "h", "ĤĦ" to "H",
            "ìíîïĩīĭįı" to "i", "ÌÍÎÏĨĪĬĮİ" to "I", "ĵ" to "j", "Ĵ" to "J", "ķ" to "k", "Ķ" to "K", "ĺļľŀł" to "l", "ĹĻĽĿŁ" to "L",
            "ñńņňŉ" to "n", "ÑŃŅŇ" to "N", "òóôõöøōŏő" to "o", "ÒÓÔÕÖØŌŎŐ" to "O", "ŕŗř" to "r", "ŔŖŘ" to "R",
            "śŝşšș" to "s", "ŚŜŞŠȘ" to "S", "ţťŧț" to "t", "ŢŤŦȚ" to "T", "ùúûüũūŭůűų" to "u", "ÙÚÛÜŨŪŬŮŰŲ" to "U",
            "ŵ" to "w", "Ŵ" to "W", "ýÿŷ" to "y", "ÝŸŶ" to "Y", "źżž" to "z", "ŹŻŽ" to "Z",
        )
        buildMap { pairs.forEach { (chars, base) -> chars.forEach { put(it, base) } } }
    }

    /** Forme décomposée : chaque lettre accentuée devient sa base suivie de l'accent combinant (U+0301), retiré par \p{M}. */
    fun normalize(s: CharSequence, form: Form): String =
        if (form == Form.NFD || form == Form.NFKD) buildString { s.forEach { c -> map[c]?.let { append(it).append('́') } ?: append(c) } }
        else s.toString()
}

/** Retire les marques combinantes laissées par [Normalizer.normalize] (équivalent de replace(Regex("\\p{M}+"), "")). */
fun String.stripMarks(): String = filterNot { it in '̀'..'ͯ' }

object URLEncoder {
    fun encode(s: String, charset: String = "UTF-8"): String = buildString {
        s.encodeToByteArray().forEach { b ->
            val c = b.toInt() and 0xFF
            when {
                c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c == '.'.code || c == '-'.code || c == '_'.code || c == '*'.code -> append(c.toChar())
                c == ' '.code -> append('+')
                else -> append('%').append(c.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
    fun encode(s: String, charset: Charsets): String = encode(s)
}

object Charsets { val UTF_8 = "UTF-8" }

expect fun systemLanguage(): String

/** Arrondi à [digits] décimales sans passer par le texte. */
fun Double.roundTo(digits: Int): Double { val p = 10.0.pow(digits); return (this * p).roundToLong() / p }
