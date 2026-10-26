package app.superhealth.update

import app.superhealth.platform.*

/**
 * Version de l'app. Sur iOS les mises à jour passent par l'outil d'installation (SideStore, AltStore…) : l'app
 * n'installe rien elle-même, elle garde seulement la comparaison de versions (plugins).
 */
object Updater {
    fun installed(ctx: Context): String = appVersion()

    /** « v1.2.3 », « 1.2 » → ["1", "2", "3"] (chiffres de chaque composante). */
    fun parts(v: String): List<String> = v.trim().removePrefix("v").removePrefix("V").split('.', '-', '+').map { p -> p.takeWhile(Char::isDigit) }

    /**
     * Versions décimales (majeure +0.1, mineure +0.01) : la composante après le premier point est une fraction,
     * donc 1.41 < 1.5 < 1.51 < 1.6. Les composantes suivantes (1.1.1) se comparent comme des entiers.
     */
    fun newer(remote: String, local: String): Boolean {
        val a = parts(remote); val b = parts(local)
        val major = (a.getOrNull(0)?.toIntOrNull() ?: 0) - (b.getOrNull(0)?.toIntOrNull() ?: 0)
        if (major != 0) return major > 0
        val fa = a.getOrElse(1) { "" }; val fb = b.getOrElse(1) { "" }
        val w = maxOf(fa.length, fb.length)
        val frac = fa.padEnd(w, '0').compareTo(fb.padEnd(w, '0'))
        if (frac != 0) return frac > 0
        for (i in 2 until maxOf(a.size, b.size)) {
            val d = (a.getOrNull(i)?.toIntOrNull() ?: 0) - (b.getOrNull(i)?.toIntOrNull() ?: 0)
            if (d != 0) return d > 0
        }
        return false
    }

    fun display(tag: String): String {
        val v = tag.trim().removePrefix("v").removePrefix("V")
        val dot = v.indexOf('.')
        if (dot < 0 || v.indexOf('.', dot + 1) >= 0 || v.substring(dot + 1).any { !it.isDigit() }) return v
        val frac = v.substring(dot + 1).trimEnd('0').ifEmpty { "0" }
        return v.substring(0, dot) + "." + frac
    }
}
