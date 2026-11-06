package app.superhealth.cards

import app.superhealth.platform.*

import app.superhealth.cards.Element.*
import kotlinx.serialization.Serializable

/** [bonus] : bonus de stat du fond en dixièmes de pour cent. */
enum class Rarity(val label: String, val symbol: String, val color: Long, val bonus: Int) {
    COMMUNE("Commune", "◆", 0xFFB8C2CC, 5),
    PEU_COMMUNE("Peu commune", "◆◆", 0xFF6FD3A0, 10),
    RARE("Rare", "◆◆◆", 0xFF4FA8FF, 20),
    EPIQUE("Épique", "★", 0xFFB46CFF, 40);
    val big get() = this >= RARE
}

/**
 * Effets d'un fond, chacun multipliant son bonus ([boost]) : brillante (reflet holographique posé sur la scène) × 1,1,
 * animée (scène en mouvement, éclats) × 1,25, chromatique (toute la scène irisée, liseré arc-en-ciel) × 1,5.
 */
enum class Finish(val label: String, val boost: Double) {
    NORMALE("Normale", 1.0), BRILLANTE("Brillante", 1.1), ANIMEE("Animée", 1.25), CHROMATIQUE("Chromatique", 1.5)
}

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
 * Un fond. Son biome fixe la scène du shader et son booster ; [element2] en fait un duo, deux biomes sur un même fond
 * et le bonus de sa rareté sur deux stats. [neutral] : fonds de palier par défaut (aucun biome, aucun bonus).
 */
data class CardDef(val id: String, val number: Int, val name: String, val rarity: Rarity, val element: Element, val palette: Palette, val seed: Float, val series: Series,
                   val look: Look = Look(0, 0f, 1f), val element2: Element? = null, val palette2: Palette? = null, val look2: Look? = null, val neutral: Boolean = false) {
    val elements get() = listOfNotNull(element, element2)
    val duo get() = element2 != null
    /** « Épique Duo », « Commune ». */
    val rarityLabel get() = rarity.label + if (duo) " Duo" else ""
    val code get() = "%03d/%03d".format(number, series.size)
}

data class Series(val id: String, val name: String, val tagline: String, val accent: Long) {
    val size get() = Catalog.cards.count { it.series.id == id }
}

/**
 * Série 1 « Nature » : quatre biomes de 36 fonds (14 communes, 11 peu communes, 7 rares, 4 épiques), puis six duos
 * par paire de biomes (2 communes, 2 peu communes, 1 rare, 1 épique), soit 180 fonds, chacun aussi en variante
 * chromatique. Calibré pour 4 à 6 packs par semaine (3.6.2) : en un an, une commune revient une dizaine de fois (ses doublons
 * se fusionnent ou s'échangent), on tire à peu près la moitié des rares d'un biome et moins d'une épique. Les compositions montent avec la
 * rareté : une commune essaie un cadrage simple, une rare porte le grand élément de son biome, une épique les réunit tous.
 * Les graines sont fixes : un fond reste identique d'une version à l'autre.
 */
object Catalog {
    val NATURE = Series("N", "Nature", "Quatre biomes, une seule salle.", 0xFFFF8A1F)

    /** Huit ambiances par biome : 0 à 3 pour les communes, 4 et 5 pour les peu communes, 6 et 7 les plus franches. */
    val palettes: Map<Element, List<Palette>> = mapOf(
        FEU to listOf(Palette(0xFFFFE08A, 0xFFFF5A1F, 0xFF1A0503), Palette(0xFFFFC0D0, 0xFFC4123E, 0xFF140210), Palette(0xFFFFF2B0, 0xFFFF9A00, 0xFF2A1200), Palette(0xFFFFD8B0, 0xFF8A3A20, 0xFF0E0806),
            Palette(0xFFFFB0F0, 0xFFFF2FA0, 0xFF1A0418), Palette(0xFFFFF6A0, 0xFF9AD21E, 0xFF141A04), Palette(0xFFFFFFFF, 0xFFFF3A00, 0xFF000000), Palette(0xFFB0E0FF, 0xFFFF4A1F, 0xFF04081A)),
        GLACE to listOf(Palette(0xFFEFFBFF, 0xFF5CC8FF, 0xFF061A3A), Palette(0xFFF0E0FF, 0xFF9A8CFF, 0xFF0C0828), Palette(0xFFE8FFF6, 0xFF3FE0C0, 0xFF02201E), Palette(0xFFFFFFFF, 0xFFA8C8E0, 0xFF2A3440),
            Palette(0xFFFFE8F0, 0xFFFF9AC8, 0xFF1A0A28), Palette(0xFFE0FFE0, 0xFF60FF9A, 0xFF021A10), Palette(0xFFFFFFFF, 0xFF2FA0FF, 0xFF000818), Palette(0xFFFFF4D0, 0xFFFFC860, 0xFF101830)),
        FOUDRE to listOf(Palette(0xFFFFF7B0, 0xFF8C6BFF, 0xFF07031C), Palette(0xFFE8F6FF, 0xFF4FA8FF, 0xFF050A20), Palette(0xFFFFE0F0, 0xFFFF4FA0, 0xFF12031A), Palette(0xFFE0E8F0, 0xFF7A8AA0, 0xFF0A0C12),
            Palette(0xFFE0FFF0, 0xFF30FFB0, 0xFF02140E), Palette(0xFFFFE8C0, 0xFFFF8A30, 0xFF1A0A04), Palette(0xFFFFFFFF, 0xFFB06CFF, 0xFF000000), Palette(0xFFF0FFFF, 0xFF00E5FF, 0xFF000A1A)),
        TERRE to listOf(Palette(0xFFFFE7C0, 0xFFB07A4A, 0xFF1A1008), Palette(0xFFE4F0FF, 0xFF6F8FB4, 0xFF0C1420), Palette(0xFFFFD0A8, 0xFFE0704A, 0xFF2A0C10), Palette(0xFFF0F8E8, 0xFF7AA060, 0xFF0C1408),
            Palette(0xFFFFE0F0, 0xFFC07AA8, 0xFF1A0C1A), Palette(0xFFFFF8E0, 0xFFD8B060, 0xFF1A1404), Palette(0xFFFFFFFF, 0xFF4A90C8, 0xFF000A14), Palette(0xFFFFC8A0, 0xFFA04A30, 0xFF100404)),
    )

    /** Noms candidats par biome : 20 communes, 15 peu communes, 10 rares, 5 épiques ; [layout] en retient une partie. */
    private val pool: Map<Element, List<String>> = mapOf(
        FEU to listOf(
            "Cendres", "Roche brûlante", "Fumerolles", "Braises", "Scories", "Pierre ponce", "Basalte", "Fissure chaude", "Sol fumant", "Tuf",
            "Lapilli", "Soufre", "Cheminée", "Coulée froide", "Croûte noire", "Vapeurs", "Charbons", "Brasero", "Étincelles", "Halo rouge",
            "Coulée", "Cratère", "Lac de lave", "Obsidienne", "Bombe volcanique", "Rivière ardente", "Fontaine de lave", "Dôme de lave", "Caldeira", "Tunnel de lave",
            "Nuée ardente", "Champ de braises", "Lave cordée", "Fournaise", "Pluie de feu",
            "Geyser de feu", "Cœur du volcan", "Mer de magma", "Colonne de feu", "Couronne ardente", "Volcan jumeau", "Tempête de cendres", "Forge des titans", "Lave d'azur", "Fleuve de soleil",
            "Éruption primordiale", "Noyau terrestre", "Phénix de lave", "Soleil souterrain", "Embrasement"),
        GLACE to listOf(
            "Givre", "Banquise", "Stalactites", "Crevasse", "Neige fraîche", "Glaçons", "Verglas", "Flocons", "Brume froide", "Névé",
            "Gel blanc", "Pack de glace", "Frimas", "Eau gelée", "Glace noire", "Grésil", "Pente gelée", "Banc de neige", "Souffle polaire", "Toundra",
            "Séracs", "Poudreuse", "Grotte de glace", "Cristaux bleus", "Iceberg", "Fjord", "Lac gelé", "Cascade figée", "Moraine", "Calotte glaciaire",
            "Langue glaciaire", "Pont de neige", "Mur de glace", "Plaine boréale", "Nuit arctique",
            "Palais gelé", "Aurore boréale", "Cathédrale de glace", "Cœur de glacier", "Banquise éternelle", "Prisme de givre", "Faille bleue", "Miroir polaire", "Lune de glace", "Silence blanc",
            "Zéro absolu", "Ère glaciaire", "Couronne boréale", "Glacier primordial", "Étoile de givre"),
        FOUDRE to listOf(
            "Nuages lourds", "Grêle", "Averse", "Grondement", "Bruine", "Ciel gris", "Vent d'ouest", "Vent fort", "Nuée", "Pluie fine",
            "Ondée", "Électricité statique", "Brise", "Coup de vent", "Nimbus", "Front froid", "Bourrasque", "Grain", "Ciel bas", "Pluie d'été",
            "Front orageux", "Rafales", "Supercellule", "Éclair de chaleur", "Orage de nuit", "Cumulonimbus", "Mer déchaînée", "Trombe", "Arc électrique", "Orage sec",
            "Ciel strié", "Tonnerre lointain", "Orage d'été", "Houle", "Ligne de grain",
            "Éclairs en chaîne", "Œil du cyclone", "Tornade", "Feu de Saint-Elme", "Tempête magnétique", "Mur d'eau", "Orage violet", "Sprite rouge", "Cyclone", "Colère du ciel",
            "Foudre originelle", "Ouragan", "Ciel de cristal", "Tonnerre éternel", "Tempête parfaite"),
        TERRE to listOf(
            "Éboulis", "Granit", "Sentier", "Falaise", "Rocher", "Cailloux", "Prairie", "Lande", "Pierre levée", "Ravin",
            "Plateau", "Combe", "Gravier", "Chaos de blocs", "Colline", "Bergerie", "Cairn", "Pâturage", "Rocaille", "Talus",
            "Col", "Arête", "Paroi nord", "Mer de nuages", "Cirque", "Vallée", "Forêt de sapins", "Lac d'altitude", "Refuge", "Gorge",
            "Canyon", "Pic rocheux", "Alpage", "Éperon", "Clair de lune",
            "Massif sacré", "Aube dorée", "Cime enneigée", "Grand canyon", "Mur des géants", "Pic solitaire", "Crête du ciel", "Chemin des étoiles", "Vallée perdue", "Sommet de feu",
            "Toit du monde", "Montagne mère", "Colonne du ciel", "Cime éternelle", "Cœur de pierre"),
    )
    val layout = List(14) { Rarity.COMMUNE } + List(11) { Rarity.PEU_COMMUNE } + List(7) { Rarity.RARE } + List(4) { Rarity.EPIQUE }
    private val names = pool.mapValues { (_, l) -> l.subList(0, 14) + l.subList(20, 31) + l.subList(35, 42) + l.subList(45, 49) }

    /**
     * Compositions (cadrage, éléments allumés, palette) d'un biome, dans l'ordre de [layout]. Les communes passent par
     * chaque cadrage avec au plus un élément, les peu communes en mêlent deux, les rares portent le focal (bit 1) et les
     * épiques allument presque tout. Aucun triplet ne revient deux fois dans un biome.
     */
    private val compositions: List<Triple<Int, Int, Int>> = buildList {
        val P = Look.PAYSAGE; val M = Look.MATIERE; val A = Look.AFFICHE; val C = Look.CIEL; val R = Look.REFLET
        for (p in 0..3) { add(Triple(M, 0, p)); add(Triple(C, 0, p)); add(Triple(A, 0, p)) }
        add(Triple(P, 2, 0)); add(Triple(R, 4, 1))
        add(Triple(P, 2 or 4, 4)); add(Triple(P, 2 or 8, 5)); add(Triple(P, 4 or 8, 4)); add(Triple(R, 2 or 4, 5)); add(Triple(R, 2 or 8, 4)); add(Triple(M, 0, 5))
        add(Triple(A, 0, 4)); add(Triple(C, 0, 5)); add(Triple(P, 2 or 4, 5)); add(Triple(R, 4 or 8, 5)); add(Triple(A, 0, 5))
        add(Triple(P, 1, 6)); add(Triple(P, 1 or 2, 7)); add(Triple(P, 1 or 4, 6)); add(Triple(P, 1 or 8, 7)); add(Triple(R, 1 or 2, 7)); add(Triple(R, 1 or 4, 6)); add(Triple(A, 0, 6))
        add(Triple(P, 15, 7)); add(Triple(R, 15, 6)); add(Triple(P, 1 or 2 or 8, 0)); add(Triple(A, 1, 7))
    }

    /** Six duos par paire de biomes : 2 communes, 2 peu communes, 1 rare, 1 épique (pris parmi huit noms). */
    private val duoLayout = List(2) { Rarity.COMMUNE } + List(2) { Rarity.PEU_COMMUNE } + Rarity.RARE + Rarity.EPIQUE
    private val duoPool: List<Triple<Element, Element, List<String>>> = listOf(
        Triple(FEU, GLACE, listOf("Bain de vapeur", "Givre fumant", "Brume tiède", "Glace ardente", "Geyser glacé", "Fonte des glaces", "Volcan sous-glaciaire", "Feu et glace")),
        Triple(FEU, FOUDRE, listOf("Ciel de cendres", "Étincelle", "Ciel rouge", "Plasma", "Éclair de lave", "Nuage ardent", "Foudre volcanique", "Tempête de feu")),
        Triple(FEU, TERRE, listOf("Roche chaude", "Terre cuite", "Sentier de braises", "Volcan", "Forge tellurique", "Montagne de feu", "Coulée des cimes", "Naissance d'un monde")),
        Triple(GLACE, FOUDRE, listOf("Grésil électrique", "Vent glacé", "Neige d'orage", "Orage polaire", "Aurore électrique", "Blizzard", "Éclair de givre", "Tempête boréale")),
        Triple(GLACE, TERRE, listOf("Pic gelé", "Neige de col", "Roche givrée", "Permafrost", "Glacier suspendu", "Cime de glace", "Vallée glaciaire", "Montagne blanche")),
        Triple(FOUDRE, TERRE, listOf("Fulgurite", "Vent des crêtes", "Orage d'altitude", "Pic foudroyé", "Éclair sur la crête", "Tonnerre des cimes", "Mur d'orage", "Colère des sommets")),
    )
    /** Cadrages des deux moitiés d'un duo : paysage ou affiche (et un reflet), pour garder peu de variantes de shader. */
    private val duos = duoPool.map { (a, b, l) -> Triple(a, b, listOf(l[0], l[1], l[3], l[4], l[5], l[7])) }
    private val duoFrames = listOf(0 to 0, 2 to 0, 0 to 2, 4 to 0, 0 to 0, 0 to 2)

    val cards: List<CardDef> = buildList {
        var n = 1
        fun seed() = ((n * 7.31f) % 13f) + 0.5f
        for (e in Element.entries) names[e]!!.forEachIndexed { i, name ->
            val (frame, features, pal) = compositions[i]
            // Horizon et zoom varient d'une carte à l'autre, pour que deux cadrages voisins ne se superposent pas.
            val horizon = -0.18f + ((i * 37) % 11) * 0.028f; val zoom = 0.9f + ((i * 53) % 7) * 0.05f
            add(CardDef("N%03d".format(n), n, name, layout[i], e, palettes[e]!![pal], seed(), NATURE, Look(features, horizon, zoom, frame))); n++
        }
        for ((a, b, list) in duos) list.forEachIndexed { j, name ->
            val r = duoLayout[j]; val (f1, f2) = duoFrames[j]
            val rich = when (r) { Rarity.COMMUNE -> 0; Rarity.PEU_COMMUNE -> 1; Rarity.RARE -> 2; Rarity.EPIQUE -> 3 }
            val feat1 = listOf(listOf(2, 4, 8), listOf(2 or 4, 4 or 8), listOf(1 or 2, 1 or 8), listOf(15))[rich].let { it[j % it.size] }
            val feat2 = listOf(listOf(8, 2, 4), listOf(2 or 8, 2 or 4), listOf(1 or 4, 1 or 2), listOf(1 or 2 or 8))[rich].let { it[j % it.size] }
            add(CardDef("N%03d".format(n), n, name, r, a, palettes[a]!![(j * 3) % 8], seed(), NATURE, Look(feat1, -0.1f + (j % 3) * 0.05f, 1f, f1),
                element2 = b, palette2 = palettes[b]!![(j * 5 + 1) % 8], look2 = Look(feat2, -0.06f - (j % 2) * 0.08f, 1.05f, f2))); n++
        }
    }

    private val byId = cards.associateBy { it.id }
    fun card(id: String) = byId[id]
    fun ofRarity(r: Rarity) = cards.filter { it.rarity == r }
    /** Contenu d'un booster : les fonds de son biome et les duos qui le croisent. */
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
