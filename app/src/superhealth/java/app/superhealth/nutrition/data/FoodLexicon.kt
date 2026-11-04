package app.superhealth.nutrition.data

import java.text.Normalizer

/**
 * Lexique français ↔ anglais des mots d'aliments les plus courants, pour chercher dans la base de l'autre langue
 * (base suisse en français, USDA en anglais). Traduction mot à mot ; les mots inconnus sont gardés tels quels.
 */
object FoodLexicon {
    private val entries: List<Pair<String, String>> = """
        pomme=apple|poire=pear|banane=banana|orange=orange|citron=lemon|citron vert=lime|fraise=strawberry|framboise=raspberry
        myrtille=blueberry|cerise=cherry|raisin=grape|peche=peach|abricot=apricot|prune=plum|ananas=pineapple|mangue=mango
        kiwi=kiwifruit|melon=melon|pasteque=watermelon|figue=fig|datte=date|avocat=avocado|noix de coco=coconut|pamplemousse=grapefruit
        tomate=tomato|carotte=carrot|pomme de terre=potato|patate douce=sweet potato|oignon=onion|ail=garlic|poireau=leek|courgette=zucchini
        aubergine=eggplant|poivron=pepper|concombre=cucumber|salade=lettuce|laitue=lettuce|epinard=spinach|chou=cabbage|chou-fleur=cauliflower
        brocoli=broccoli|haricot=bean|haricot vert=snap bean|petit pois=pea|pois chiche=chickpea|lentille=lentil|champignon=mushroom|mais=corn
        celeri=celery|radis=radish|betterave=beet|asperge=asparagus|artichaut=artichoke|citrouille=pumpkin|courge=squash|navet=turnip
        fenouil=fennel|persil=parsley|basilic=basil|menthe=mint|olive=olive|poulet=chicken|dinde=turkey|boeuf=beef
        veau=veal|porc=pork|agneau=lamb|canard=duck|jambon=ham|lard=bacon|bacon=bacon|saucisse=sausage
        steak=steak|hache=ground|viande=meat|foie=liver|lapin=rabbit|cheval=horse|poisson=fish|saumon=salmon
        thon=tuna|cabillaud=cod|sardine=sardine|maquereau=mackerel|truite=trout|crevette=shrimp|moule=mussel|huitre=oyster
        crabe=crab|homard=lobster|calamar=squid|anchois=anchovy|colin=pollock|merlu=hake|oeuf=egg|lait=milk
        fromage=cheese|yaourt=yogurt|yogourt=yogurt|beurre=butter|creme=cream|mozzarella=mozzarella|parmesan=parmesan|chevre=goat
        cheddar=cheddar|ricotta=ricotta|feta=feta|lactose=lactose|ecreme=skim|entier=whole|pain=bread|riz=rice
        pates=pasta|pate=pasta|spaghetti=spaghetti|farine=flour|avoine=oat|flocon=flake|ble=wheat|semoule=semolina
        quinoa=quinoa|orge=barley|seigle=rye|cereale=cereal|biscuit=cookie|gateau=cake|croissant=croissant|crepe=pancake
        tortilla=tortilla|nouille=noodle|couscous=couscous|muesli=granola|complet=whole wheat|sucre=sugar|miel=honey|chocolat=chocolate
        confiture=jam|sirop=syrup|bonbon=candy|glace=ice cream|cacao=cocoa|huile=oil|huile d olive=olive oil|vinaigre=vinegar
        sel=salt|poivre=pepper|moutarde=mustard|mayonnaise=mayonnaise|ketchup=catsup|sauce=sauce|soja=soy|tofu=tofu
        beurre de cacahuete=peanut butter|cacahuete=peanut|amande=almond|noix=walnut|noisette=hazelnut|noix de cajou=cashew|pistache=pistachio|graine=seed
        tournesol=sunflower|lin=flaxseed|sesame=sesame|eau=water|cafe=coffee|the=tea|jus=juice|biere=beer
        vin=wine|soda=soda|boisson=beverage|lait de soja=soy milk|cru=raw|cuit=cooked|bouilli=boiled|grille=grilled
        frit=fried|roti=roasted|vapeur=steamed|seche=dried|surgele=frozen|conserve=canned|fume=smoked|sale=salted
        nature=plain|maigre=lean|gras=fat|blanc=white|rouge=red|vert=green|noir=black|cuisse=thigh
        blanc de poulet=chicken breast|filet=fillet|aile=wing|peau=skin|sans=no|avec=with|soupe=soup|puree=puree
        """.trimIndent().replace('\n', '|').split('|').map { it.substringBefore('=').trim() to it.substringAfter('=').trim() }

    fun norm(s: String) = Normalizer.normalize(s.lowercase().replace("œ", "oe").replace("æ", "ae").replace('’', ' ').replace('\'', ' '), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 -]+"), " ").replace(Regex("\\s+"), " ").trim()

    /** [french] : le mot est en français. */
    private fun singular(w: String, french: Boolean) = when {
        french && w.length > 3 && (w.endsWith('s') || w.endsWith('x')) -> w.dropLast(1)
        !french && w.length > 3 && w.endsWith("ies") -> w.dropLast(3) + "y"
        !french && w.length > 3 && w.endsWith("es") && (w.endsWith("oes") || w.endsWith("ches") || w.endsWith("shes")) -> w.dropLast(2)
        !french && w.length > 3 && w.endsWith('s') -> w.dropLast(1)
        else -> w
    }

    private val frToEn = entries.associate { it.first to it.second }
    private val enToFr = entries.reversed().associate { it.second to it.first }

    /**
     * Requête traduite vers l'anglais ([toEnglish]) ou le français, expressions de plusieurs mots d'abord. Null si aucun
     * mot n'est connu : chercher la requête telle quelle suffit alors.
     */
    fun translate(query: String, toEnglish: Boolean): String? {
        val dict = if (toEnglish) frToEn else enToFr
        val raw = norm(query).split(' ').filter { it.isNotBlank() }
        if (raw.isEmpty()) return null
        val single = raw.map { singular(it, french = toEnglish) }
        val out = mutableListOf<String>()
        var i = 0
        var hit = false
        while (i < raw.size) {
            // forme écrite d'abord (« mais », « asparagus »), puis le singulier (« pommes », « apples »)
            val match = (minOf(3, raw.size - i) downTo 1).firstNotNullOfOrNull { n ->
                (dict[raw.subList(i, i + n).joinToString(" ")] ?: dict[single.subList(i, i + n).joinToString(" ")])?.let { it to n }
            }
            if (match != null) { out += match.first; i += match.second; hit = true } else { out += raw[i]; i++ }
        }
        return if (hit) out.joinToString(" ") else null
    }
}
