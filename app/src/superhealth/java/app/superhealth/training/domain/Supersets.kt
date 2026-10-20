package app.superhealth.training.domain

/**
 * Supersets, trisets et circuits : des exercices voisins qui partagent un numéro de groupe. Les fonctions travaillent sur
 * la liste des groupes (un par exercice, dans l'ordre) pour servir aux séances comme aux programmes.
 */
object Supersets {
    /** Relie l'exercice [i] au suivant ; le groupe du suivant (s'il en a un) est fusionné. */
    fun link(g: List<Int?>, i: Int): List<Int?> {
        if (i !in g.indices || i + 1 > g.lastIndex) return g
        val target = g[i] ?: g[i + 1] ?: ((g.filterNotNull().maxOrNull() ?: 0) + 1)
        val old = g[i + 1]
        return normalize(g.mapIndexed { k, v -> if (k == i || k == i + 1 || (old != null && v == old)) target else v })
    }

    /** Sort l'exercice [i] de son groupe ; un groupe coupé en deux devient deux groupes. */
    fun unlink(g: List<Int?>, i: Int): List<Int?> = normalize(g.mapIndexed { k, v -> if (k == i) null else v })

    /** Groupes = suites contiguës renumérotées dans l'ordre ; un exercice seul n'est plus en superset. */
    fun normalize(g: List<Int?>): List<Int?> {
        val out = MutableList<Int?>(g.size) { null }
        var n = 1; var k = 0
        while (k < g.size) {
            val v = g[k]
            if (v == null) { k++; continue }
            var e = k
            while (e + 1 < g.size && g[e + 1] == v) e++
            if (e > k) { (k..e).forEach { out[it] = n }; n++ }
            k = e + 1
        }
        return out
    }

    /** Nom du groupe selon sa taille. */
    fun label(size: Int) = when { size >= 4 -> "Circuit"; size == 3 -> "Triset"; else -> "Superset" }
}
