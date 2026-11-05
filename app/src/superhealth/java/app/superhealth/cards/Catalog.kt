package app.superhealth.cards

import app.superhealth.cards.Element.*
import kotlinx.serialization.Serializable

/** [bonus] : bonus de stat du fond en dixièmes de pour cent ; [refund] : éclats rendus par un doublon au niveau max ; [craft] : prix en éclats. */
enum class Rarity(val label: String, val symbol: String, val color: Long, val bonus: Int, val refund: Int, val craft: Int) {
    COMMUNE("Commune", "◆", 0xFFB8C2CC, 0, 5, 150),
    PEU_COMMUNE("Peu commune", "◆◆", 0xFF6FD3A0, 5, 10, 300),
    RARE("Rare", "◆◆◆", 0xFF4FA8FF, 10, 40, 1200),
    /** Épique : deux biomes peints sur un même fond, deux stats renforcées. */
    EPIQUE("Épique", "★", 0xFFB46CFF, 10, 200, 4000);
    val big get() = this >= RARE
}

enum class Finish(val label: String) { NORMALE("Normale"), BRILLANTE("Brillante"), ANIMEE("Animée") }

@Serializable
data class Palette(val a: Long, val b: Long, val c: Long)

/**
 * Composition d'une scène de biome, pour que deux fonds du même booster ne se ressemblent pas : [features] allume
 * jusqu'à quatre éléments du paysage (bit 1 à 8, voir CardShaders), [horizon] et [zoom] cadrent la scène, [frame]
 * choisit le cadrage : paysage, gros plan de matière, affiche en aplats, vue du ciel ou reflet dans un lac.
 */
data class Look(val features: Int, val horizon: Float, val zoom: Float, val frame: Int = PAYSAGE) {
    companion object { const val PAYSAGE = 0; const val MATIERE = 1; const val AFFICHE = 2; const val CIEL = 3; const val REFLET = 4 }
}

/**
 * Un fond. Son biome fixe la scène du shader et son booster ; [element2] fait une épique à deux biomes.
 * [neutral] : fonds de palier par défaut (aucun biome, aucun bonus).
 */
data class CardDef(val id: String, val number: Int, val name: String, val rarity: Rarity, val element: Element, val palette: Palette, val seed: Float, val series: Series,
                   val look: Look = Look(0, 0f, 1f), val element2: Element? = null, val palette2: Palette? = null, val look2: Look? = null, val neutral: Boolean = false) {
    val elements get() = listOfNotNull(element, element2)
    val code get() = "%03d/%03d".format(number, series.size)
}

data class Series(val id: String, val name: String, val tagline: String, val accent: Long) {
    val size get() = Catalog.cards.count { it.series.id == id }
}

/**
 * Série 1 « Nature » : quatre biomes de dix fonds (4 communes, 4 peu communes, 2 rares), chacun avec sa propre composition,
 * puis douze épiques, deux par paire de biomes. Les graines sont fixes : un fond reste identique d'une version à l'autre.
 */
object Catalog {
    val NATURE = Series("N", "Nature", "Quatre biomes, une seule salle.", 0xFFFF8A1F)

    /** Trois ambiances par biome (jour, crépuscule, nuit, selon le biome). */
    val palettes: Map<Element, List<Palette>> = mapOf(
        FEU to listOf(Palette(0xFFFFE08A, 0xFFFF5A1F, 0xFF1A0503), Palette(0xFFFFC0D0, 0xFFC4123E, 0xFF140210), Palette(0xFFFFF2B0, 0xFFFF9A00, 0xFF2A1200)),
        GLACE to listOf(Palette(0xFFEFFBFF, 0xFF5CC8FF, 0xFF061A3A), Palette(0xFFF0E0FF, 0xFF9A8CFF, 0xFF0C0828), Palette(0xFFE8FFF6, 0xFF3FE0C0, 0xFF02201E)),
        FOUDRE to listOf(Palette(0xFFFFF7B0, 0xFF8C6BFF, 0xFF07031C), Palette(0xFFE8F6FF, 0xFF4FA8FF, 0xFF050A20), Palette(0xFFFFE0F0, 0xFFFF4FA0, 0xFF12031A)),
        TERRE to listOf(Palette(0xFFFFE7C0, 0xFFB07A4A, 0xFF1A1008), Palette(0xFFE4F0FF, 0xFF6F8FB4, 0xFF0C1420), Palette(0xFFFFD0A8, 0xFFE0704A, 0xFF2A0C10)),
    )

    /** Noms par biome : 4 communes, 4 peu communes puis 2 rares, dans l'ordre de [looks]. */
    private val names: Map<Element, List<String>> = mapOf(
        FEU to listOf("Cendres", "Roche brûlante", "Fumerolles", "Braises", "Coulée", "Cratère", "Lac de lave", "Obsidienne", "Geyser de feu", "Éruption primordiale"),
        GLACE to listOf("Givre", "Banquise", "Stalactites", "Crevasse", "Séracs", "Poudreuse", "Grotte de glace", "Cristaux bleus", "Palais gelé", "Zéro absolu"),
        FOUDRE to listOf("Nuages lourds", "Grêle", "Averse", "Grondement", "Front orageux", "Rafales", "Supercellule", "Éclairs en chaîne", "Œil du cyclone", "Foudre originelle"),
        TERRE to listOf("Éboulis", "Granit", "Sentier", "Falaise", "Col", "Arête", "Paroi nord", "Mer de nuages", "Massif sacré", "Toit du monde"),
    )

    /**
     * Compositions des dix fonds d'un biome : (cadrage et éléments du paysage, palette). Les communes essaient chaque
     * cadrage, les peu communes en mêlent deux éléments, les rares portent le focal du biome (bit 1). Aucune paire ne se ressemble.
     */
    private val looks = listOf(
        Look(0, -0.08f, 1.0f, Look.MATIERE) to 0, Look(0, 0f, 1.1f, Look.CIEL) to 1, Look(0, -0.12f, 1.0f, Look.AFFICHE) to 2, Look(4, -0.02f, 1.3f) to 0,
        Look(4 or 8, 0.1f, 1.1f) to 2, Look(2, -0.02f, 1.0f, Look.REFLET) to 1, Look(0, -0.2f, 0.9f, Look.AFFICHE) to 0, Look(1 or 8, -0.04f, 1.18f) to 2,
        Look(1 or 4 or 8, 0.02f, 0.92f, Look.REFLET) to 2, Look(1 or 2 or 4 or 8, -0.16f, 1.05f) to 0,
    )
    private val layout = List(4) { Rarity.COMMUNE } + List(4) { Rarity.PEU_COMMUNE } + List(2) { Rarity.RARE }

    /** Deux épiques par paire de biomes, nommées d'après ce que la nature fait quand les deux se rencontrent. */
    private val duos: List<Triple<Element, Element, String>> = listOf(
        Triple(FEU, GLACE, "Glace ardente"), Triple(FEU, GLACE, "Vapeur"),
        Triple(FEU, FOUDRE, "Plasma"), Triple(FEU, FOUDRE, "Orage de cendres"),
        Triple(FEU, TERRE, "Volcan"), Triple(FEU, TERRE, "Forge tellurique"),
        Triple(GLACE, FOUDRE, "Orage polaire"), Triple(GLACE, FOUDRE, "Aurore électrique"),
        Triple(GLACE, TERRE, "Permafrost"), Triple(GLACE, TERRE, "Glacier suspendu"),
        Triple(FOUDRE, TERRE, "Fulgurite"), Triple(FOUDRE, TERRE, "Pic foudroyé"),
    )

    val cards: List<CardDef> = buildList {
        var n = 1
        for (e in Element.entries) names[e]!!.forEachIndexed { i, name ->
            val (look, pal) = looks[i]
            add(CardDef("N%03d".format(n), n, name, layout[i], e, palettes[e]!![pal], ((n * 7.31f) % 13f) + 0.5f, NATURE, look)); n++
        }
        duos.forEachIndexed { i, (a, b, name) ->
            val alt = i % 2
            add(CardDef("N%03d".format(n), n, name, Rarity.EPIQUE, a, palettes[a]!![alt * 2], ((n * 5.17f) % 11f) + 0.5f, NATURE, Look(1 or 2 or (if (alt == 0) 4 else 8), -0.1f + alt * 0.06f, 1f),
                element2 = b, palette2 = palettes[b]!![alt], look2 = Look(1 or (if (alt == 0) 8 else 2), -0.06f - alt * 0.08f, 1.05f, if (alt == 0) Look.PAYSAGE else Look.AFFICHE))); n++
        }
    }

    private val byId = cards.associateBy { it.id }
    fun card(id: String) = byId[id]
    fun ofRarity(r: Rarity) = cards.filter { it.rarity == r }
    /** Contenu d'un booster : les fonds de son biome et les épiques qui le croisent. */
    fun ofBooster(b: Booster) = cards.filter { b.element in it.elements }

    /** Fond par défaut d'une carte sans fond choisi, selon le palier : sans biome ni bonus. */
    fun defaultFor(tier: Tier): CardDef {
        val (pal, r) = when (tier) {
            Tier.BRONZE -> Palette(0xFFF2C9A0, 0xFFB06A3B, 0xFF2A1408) to Rarity.COMMUNE
            Tier.ARGENT -> Palette(0xFFF4F7FA, 0xFF9AA6B2, 0xFF1C2228) to Rarity.COMMUNE
            Tier.OR -> Palette(0xFFFFF1C2, 0xFFD9A520, 0xFF2E1E00) to Rarity.COMMUNE
            Tier.ELITE -> Palette(0xFFCFEFFF, 0xFF3C9BEA, 0xFF061A3A) to Rarity.RARE
            Tier.ICONE -> Palette(0xFFFFF4D6, 0xFFE7B24A, 0xFF120A28) to Rarity.RARE
        }
        return CardDef("_" + tier.name, 0, tier.label, r, Element.TERRE, pal, 2f + tier.ordinal * 2, NATURE, neutral = true)
    }
}

/** Une carte possédée : fond + finition. Clé stable « N012:BRILLANTE ». */
data class Pull(val card: CardDef, val finish: Finish) {
    val key get() = "${card.id}:${finish.name}"
    companion object {
        fun of(key: String): Pull? {
            val (id, f) = key.split(':').takeIf { it.size == 2 } ?: return null
            return Catalog.card(id)?.let { c -> runCatching { Finish.valueOf(f) }.getOrNull()?.let { Pull(c, it) } }
        }
    }
}
