package app.superhealth.nutrition.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import app.superhealth.ui.LowPoly
import java.text.Normalizer

/**
 * Icônes d'aliments extraites de MacroFactor (assets/foods/iNNNN.webp) et index de termes anglais
 * (assets/foods/index.json, construit depuis icon_metadata.jsonl par tools/import_mf_assets.sh).
 * Les noms français sont traduits mot à mot via [FR_EN] avant la recherche.
 * assets/foods/pack.json échange une partie de ces icônes contre le pack pixel-art acheté (foods/pack/NNN.png, 16×16)
 * et ajoute quelques aliments absents de MacroFactor (identifiants « pNNN ») ; les autres ont une version pixel-art générée
 * dans le même style (foods/gen/iNNNN.png, tools/pixel_food_icons.js), en attendant validation une par une.
 */
object FoodIcons {
    @Serializable
    private data class Entry(val i: String, val n: String, val w: List<String>, val x: List<String> = emptyList())

    @Serializable
    private data class Pack(val map: Map<String, Int> = emptyMap(), val alias: Map<String, String> = emptyMap(), val extra: List<Entry> = emptyList())

    private var entries: List<Entry> = emptyList()
    private var pack: Map<String, Int> = emptyMap()
    private var alias: Map<String, String> = emptyMap()
    private var generated: Set<String> = emptySet()
    private val matchCache = HashMap<String, String?>()
    private val bitmaps = LruCache<String, ImageBitmap>(160)
    private lateinit var app: Context

    fun init(context: Context) {
        app = context.applicationContext
        val json = Json { ignoreUnknownKeys = true }
        val p = runCatching {
            json.decodeFromString(Pack.serializer(), app.assets.open("foods/pack.json").use { it.readBytes().decodeToString() })
        }.getOrDefault(Pack())
        entries = runCatching {
            val text = app.assets.open("foods/index.json").use { it.readBytes().decodeToString() }
            json.decodeFromString(ListSerializer(Entry.serializer()), text)
        }.getOrDefault(emptyList()) + p.extra
        pack = p.map + p.extra.associate { it.i to it.i.drop(1).toInt() }
        alias = p.alias
        generated = app.assets.list("foods/gen").orEmpty().map { it.removeSuffix(".png") }.toSet()
    }

    /** Identifiant d'icône (ex. « i1000 ») le plus pertinent pour un nom d'aliment, ou null. */
    @Synchronized
    fun match(name: String): String? = matchCache.getOrPut(name) { compute(name) }

    fun bitmap(id: String): ImageBitmap? {
        bitmaps.get(id)?.let { return it }
        val bmp = runCatching { app.assets.open("foods/$id.webp").use { BitmapFactory.decodeStream(it) } }.getOrNull() ?: return null
        return bmp.asImageBitmap().also { bitmaps.put(id, it) }
    }

    /** Icône pixel-art (pack, sinon générée) qui remplace [id] (16×16, à agrandir sans lissage), ou null ; « alias » = équivalent choisi. */
    fun pixel(id: String): ImageBitmap? = alias[id]?.let { pixel(it) } ?: pack[id]?.let { asset("foods/pack/${it.toString().padStart(3, '0')}.png") }
        ?: if (id in generated) asset("foods/gen/$id.png") else null

    fun asset(path: String): ImageBitmap? {
        bitmaps.get(path)?.let { return it }
        val bmp = runCatching { app.assets.open(LowPoly.path(app, path)).use { BitmapFactory.decodeStream(it) } }.getOrNull() ?: return null
        return bmp.asImageBitmap().also { bitmaps.put(path, it) }
    }

    private fun normalize(s: String) = Normalizer.normalize(s.lowercase().replace("œ", "oe").replace("æ", "ae"), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]+"), " ").replace(Regex("\\s+"), " ").trim()

    private fun compute(name: String): String? {
        var normalized = " " + normalize(name) + " "
        PHRASES.forEach { (fr, en) -> normalized = normalized.replace(" $fr ", " $en ") }
        val words = normalized.trim().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        val en = words.flatMap { w -> listOfNotNull(w, FR_EN[w], FR_EN[w.removeSuffix("s")], w.removeSuffix("s").takeIf { it != w }) }
        val hay = " " + en.joinToString(" ") + " "
        // Le premier mot porte le plus de sens (« Yaourt à la fraise » -> yaourt) : bonus de position.
        val firstEn = listOfNotNull(words[0], FR_EN[words[0]], FR_EN[words[0].removeSuffix("s")])
        var best: String? = null
        var bestScore = 0
        for (e in entries) {
            if (e.x.any { hay.contains(" $it ") }) continue
            for (w in e.w) {
                if (!hay.contains(" $w ")) continue
                var score = w.length * 2 + if (w == e.n.lowercase()) 3 else 0
                if (firstEn.any { w == it || w.startsWith("$it ") }) score += 20
                if (score > bestScore) { bestScore = score; best = e.i }
            }
        }
        return best
    }

    /** Expressions à traduire avant le découpage en mots (sinon « pomme de terre » donnerait une pomme). */
    private val PHRASES = listOf(
        "pomme de terre" to "potato", "pommes de terre" to "potato", "pois chiche" to "chickpeas", "pois chiches" to "chickpeas",
        "noix de coco" to "coconut", "fruit de la passion" to "passion fruit", "fruits de mer" to "seafood", "lait de coco" to "coconut milk",
        "chou fleur" to "cauliflower", "haricot vert" to "green beans", "haricots verts" to "green beans", "petit pois" to "peas",
        "petits pois" to "peas", "beurre de cacahuete" to "peanut butter", "oeuf au plat" to "fried egg", "oeufs au plat" to "fried egg",
        "aile de poulet" to "chicken wings", "ailes de poulet" to "chicken wings", "pain de mie" to "bread", "creme glacee" to "ice cream",
        "jaune d oeuf" to "egg", "blanc d oeuf" to "egg white", "salade verte" to "lettuce", "patate douce" to "sweet potato",
    )

    private val FR_EN = mapOf(
        "pomme" to "apple", "poire" to "pear", "banane" to "banana", "orange" to "orange", "citron" to "lemon", "fraise" to "strawberry",
        "framboise" to "raspberry", "myrtille" to "blueberry", "cerise" to "cherry", "raisin" to "grape", "peche" to "peach",
        "abricot" to "apricot", "ananas" to "pineapple", "mangue" to "mango", "kiwi" to "kiwi", "pasteque" to "watermelon",
        "melon" to "melon", "prune" to "plum", "figue" to "fig", "datte" to "date", "noix" to "walnut", "amande" to "almond",
        "noisette" to "hazelnut", "cacahuete" to "peanut", "arachide" to "peanut", "avocat" to "avocado", "tomate" to "tomato",
        "carotte" to "carrot", "courgette" to "zucchini", "aubergine" to "eggplant", "poivron" to "bell pepper", "oignon" to "onion",
        "ail" to "garlic", "salade" to "salad", "laitue" to "lettuce", "epinard" to "spinach", "brocoli" to "broccoli",
        "chou" to "cabbage", "concombre" to "cucumber", "champignon" to "mushroom", "haricot" to "bean", "pois" to "peas",
        "lentille" to "lentils", "mais" to "corn", "pomme de terre" to "potato", "patate" to "potato", "frite" to "french fries",
        "riz" to "rice", "pate" to "pasta", "pates" to "pasta", "spaghetti" to "spaghetti", "nouille" to "noodles", "pain" to "bread",
        "baguette" to "baguette", "brioche" to "brioche", "croissant" to "croissant", "cereale" to "cereal", "avoine" to "oats",
        "flocon" to "oats", "muesli" to "muesli", "granola" to "granola", "biscuit" to "cookie", "gateau" to "cake",
        "tarte" to "pie", "crepe" to "crepe", "gaufre" to "waffle", "beignet" to "donut", "sucette" to "lollipop", "chocolat" to "chocolate", "bonbon" to "candy",
        "glace" to "ice cream", "sucre" to "sugar", "miel" to "honey", "confiture" to "jam", "lait" to "milk", "yaourt" to "yogurt",
        "yogourt" to "yogurt", "fromage" to "cheese", "beurre" to "butter", "creme" to "cream", "oeuf" to "egg", "oeufs" to "egg",
        "poulet" to "chicken", "dinde" to "turkey", "boeuf" to "beef", "steak" to "steak", "porc" to "pork", "jambon" to "ham",
        "lard" to "bacon", "lardon" to "bacon", "saucisse" to "sausage", "agneau" to "lamb", "veau" to "veal", "canard" to "duck",
        "poisson" to "fish", "saumon" to "salmon", "thon" to "tuna", "cabillaud" to "cod", "crevette" to "shrimp", "moule" to "mussel",
        "huitre" to "oyster", "crabe" to "crab", "homard" to "lobster", "sardine" to "sardine", "tofu" to "tofu", "soupe" to "soup",
        "pizza" to "pizza", "burger" to "burger", "hamburger" to "burger", "sandwich" to "sandwich", "sushi" to "sushi",
        "taco" to "taco", "cafe" to "coffee", "the" to "tea", "jus" to "juice", "eau" to "water", "biere" to "beer", "vin" to "wine",
        "soda" to "soda", "huile" to "oil", "olive" to "olive", "vinaigre" to "vinegar", "sel" to "salt", "poivre" to "pepper",
        "sauce" to "sauce", "ketchup" to "ketchup", "mayonnaise" to "mayonnaise", "moutarde" to "mustard", "proteine" to "protein",
        "whey" to "protein powder", "barre" to "bar", "chips" to "chips", "popcorn" to "popcorn", "noix de coco" to "coconut",
        "coco" to "coconut", "quinoa" to "quinoa", "semoule" to "couscous", "couscous" to "couscous", "houmous" to "hummus",
        "pois chiche" to "chickpeas", "chiche" to "chickpeas", "compote" to "applesauce", "smoothie" to "smoothie",
    )
}
