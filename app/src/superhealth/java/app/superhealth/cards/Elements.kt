package app.superhealth.cards

/**
 * Les quatre biomes de la série Nature, un par famille musculaire. Chaque biome est un élément de magie (ce qu'une
 * carte lance en duel), a son booster et ses fonds, et renforce la stat qu'il évoque. RÉG et CAR n'ont pas de biome :
 * elles ne se boostent qu'en s'entraînant.
 */
enum class Element(val label: String, val magic: String, val stat: Stat, val core: Long, val mid: Long, val outer: Long, val tagline: String) {
    FEU("Magma", "Feu", Stat.PEC, 0xFFFFF6C0, 0xFFFF8A1F, 0xFFE53935, "La poussée qui fait trembler le sol."),
    GLACE("Glacier", "Glace", Stat.DOS, 0xFFE8FBFF, 0xFF7FDBFF, 0xFF2F6FDE, "Un dos large et solide comme la banquise."),
    FOUDRE("Tempête", "Foudre", Stat.BRA, 0xFFFFFFFF, 0xFFFFE45C, 0xFF8C6BFF, "Des bras et des épaules qui frappent comme l'éclair."),
    TERRE("Montagne", "Roche", Stat.JAM, 0xFFFFE7B0, 0xFFD89A4A, 0xFF6B3E1E, "Des jambes faites pour les sommets.");

    companion object {
        fun of(stat: Stat): Element? = entries.firstOrNull { it.stat == stat }
    }
}

/** Un booster par biome : il ne contient que les fonds de ce biome, et les épiques qui le croisent. */
enum class Booster(val element: Element) {
    MAGMA(Element.FEU), GLACIER(Element.GLACE), TEMPETE(Element.FOUDRE), MONTAGNE(Element.TERRE);
    val label get() = element.label
    companion object { fun of(e: Element) = entries.first { it.element == e } }
}

/**
 * Bonus de statistique d'un fond équipé, en dixièmes de pour cent : rien pour une commune (purement cosmétique),
 * 0,5 % pour une peu commune, 1 % pour une rare ou une épique (sur ses deux stats), plus 0,1 % par niveau gagné avec
 * les doublons. Il compte en duel et s'affiche à côté de la stat (« +0,4 ») ; la note globale reste celle des séances.
 */
object Bonus {
    fun tenths(card: CardDef, level: Int): Int = if (card.rarity.bonus == 0) 0 else card.rarity.bonus + (level.coerceIn(1, Levels.MAX) - 1)

    /** Points ajoutés à chaque stat renforcée, au dixième près (les stats restent entières, le bonus non). */
    fun points(stats: AthleteStats?, card: CardDef?, level: Int): Map<Stat, Double> {
        if (stats == null || card == null || card.neutral) return emptyMap()
        val pct = tenths(card, level).takeIf { it > 0 } ?: return emptyMap()
        return card.elements.mapNotNull { e -> stats[e.stat]?.let { v -> e.stat to Math.round(v * pct / 100.0) / 10.0 } }.toMap()
    }

    fun label(card: CardDef, level: Int): String {
        val t = tenths(card, level)
        if (t == 0) return "Aucun bonus"
        val pct = if (t % 10 == 0) "${t / 10}" else "${t / 10},${t % 10}"
        return card.elements.joinToString(" · ") { "+$pct % ${it.stat.code}" }
    }

    /** « +0,4 » : un bonus de stat au dixième, à la française. */
    fun format(points: Double) = "+" + "%.1f".format(java.util.Locale.FRANCE, points)
}

/** Niveaux d'un fond : chaque doublon le fait monter, jusqu'au niveau 5 (11 exemplaires) ; au-delà, des éclats. */
object Levels {
    const val MAX = 5
    /** Exemplaires cumulés pour atteindre chaque niveau. */
    val copies = intArrayOf(1, 2, 4, 7, 11)
    fun of(copies: Int): Int = this.copies.count { copies >= it }.coerceAtLeast(1)
    /** Progression vers le niveau suivant : (exemplaires gagnés dans ce niveau, exemplaires nécessaires), null au max. */
    fun progress(copies: Int): Pair<Int, Int>? {
        val l = of(copies); if (l >= MAX) return null
        return (copies - this.copies[l - 1]) to (this.copies[l] - this.copies[l - 1])
    }
}

enum class PhotoSource(val label: String) { PROFIL("Photo de profil"), DETOUREE("Photo détourée"), AUCUNE("Sans photo") }
