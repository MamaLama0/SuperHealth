package app.superhealth.cards

import app.superhealth.platform.*

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
 * Bonus de statistique d'un fond équipé, en centièmes de pour cent : 0,5 % pour une commune, 1 % pour une peu commune,
 * 2 % pour une rare, 4 % pour une épique, sur ses deux stats pour un duo ; chaque effet le multiplie ([Finish.boost] :
 * × 1,1, × 1,25 ou × 1,5), puis chaque niveau de fusion ajoute 10 % ([Levels]). Il compte en duel et s'affiche à côté
 * de la stat (« +0,4 ») ; la note globale reste celle des séances.
 */
object Bonus {
    fun hundredths(card: CardDef, level: Int, finish: Finish = Finish.NORMALE): Int {
        if (card.neutral) return 0
        return Math.round(card.rarity.bonus * 10 * finish.boost * Levels.boost(level)).toInt()
    }
    fun hundredths(pull: Pull, level: Int) = hundredths(pull.card, level, pull.finish)

    /** Points ajoutés à chaque stat renforcée, au dixième près (les stats restent entières, le bonus non). */
    fun points(stats: AthleteStats?, card: CardDef?, level: Int, finish: Finish = Finish.NORMALE): Map<Stat, Double> {
        if (stats == null || card == null || card.neutral) return emptyMap()
        val pct = hundredths(card, level, finish).takeIf { it > 0 } ?: return emptyMap()
        return card.elements.mapNotNull { e -> stats[e.stat]?.let { v -> e.stat to Math.round(v * pct / 1000.0) / 10.0 } }.toMap()
    }

    fun label(card: CardDef, level: Int, finish: Finish = Finish.NORMALE): String {
        val h = hundredths(card, level, finish)
        if (h == 0) return "Aucun bonus"
        val pct = "%.2f".format(Locale.FRANCE, h / 100.0).trimEnd('0').trimEnd(',')
        return card.elements.joinToString(" · ") { "+$pct % ${it.stat.code}" }
    }
    fun label(pull: Pull, level: Int) = label(pull.card, level, pull.finish)

    /** « +0,4 » : un bonus de stat au dixième, à la française. */
    fun format(points: Double) = "+" + "%.1f".format(Locale.FRANCE, points)
}

/**
 * Fusion des doublons : un fond monte d'un niveau quand on y fusionne assez d'exemplaires, 2 au total pour le niveau 2
 * (+10 % d'effet), 4 pour le 3 (+20 %), 16 pour le 4 (+30 %), 256 pour le 5 (+40 %). Chaque palier est le carré du
 * précédent : les premiers viennent vite, le dernier est un trophée. Les doublons non fusionnés s'échangent entre joueurs.
 */
object Levels {
    const val MAX = 5
    /** Exemplaires fusionnés cumulés (l'original compris) pour atteindre chaque niveau. */
    val copies = intArrayOf(1, 2, 4, 16, 256)
    fun of(copies: Int): Int = this.copies.count { copies >= it }.coerceAtLeast(1)
    /** Multiplicateur d'effet d'un niveau : × 1, × 1,1… × 1,4. */
    fun boost(level: Int) = 1 + 0.1 * (level.coerceIn(1, MAX) - 1)
    /** Doublons à fusionner pour passer du niveau [level] au suivant (null au max). */
    fun cost(level: Int): Int? = if (level >= MAX) null else copies[level] - copies[level - 1]
    /** Éclats d'une fusion vers le niveau 2, 3, 4 puis 5 (FUSE_PRICE du serveur) : un petit coût qui monte avec le niveau. */
    private val prices = intArrayOf(20, 40, 80, 160)
    fun price(level: Int): Int? = if (level >= MAX) null else prices[level.coerceAtLeast(1) - 1]
    /** Progression vers le niveau suivant : (exemplaires fusionnés dans ce niveau, exemplaires nécessaires), null au max. */
    fun progress(copies: Int): Pair<Int, Int>? {
        val l = of(copies); if (l >= MAX) return null
        return (copies - this.copies[l - 1]) to (this.copies[l] - this.copies[l - 1])
    }
}

enum class PhotoSource(val label: String) { PROFIL("Photo de profil"), DETOUREE("Photo détourée"), AUCUNE("Sans photo") }
