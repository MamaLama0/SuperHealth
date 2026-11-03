package app.superhealth.league

/** Les seuils SuperHealth utilisent uniquement les totaux enregistrés ; les images ne confèrent aucun badge Garmin. */
object Achievements {
    data class Badge(val id: String, val label: String, val metric: String, val threshold: Long, val asset: String, val title: String? = null)
    val catalog: List<Badge> = buildList {
        listOf(100L, 1000L, 5000L, 20000L, 100000L).forEachIndexed { i, n ->
            val title = listOf("En mouvement", "Assidu", "Persévérant", "Inarrêtable", "Légende")[i]
            add(Badge("xp_$n", title, "xp", n, "game/xp.png", title))
        }
        listOf(1L, 10L, 50L, 100L, 500L).forEach { n -> add(Badge("strength_$n", "$n séances de musculation", "sessions", n, "game/garmin/strength.png")) }
        listOf(1L, 10L, 50L, 100L).forEach { n -> add(Badge("records_$n", "$n records sportifs", "records", n, "game/trophy.png")) }
        listOf(7L, 30L, 100L, 365L).forEach { n -> add(Badge("food_$n", "$n jours de nutrition suivis", "foodDays", n, "game/garmin/food.png")) }
        listOf(60L, 300L, 1000L, 5000L).forEach { n -> add(Badge("mind_$n", "$n minutes pour l'esprit et la mobilité", "mindMinutes", n, "game/garmin/mind.png")) }
        listOf(1L, 4L, 12L, 50L).forEach { n -> add(Badge("quests_$n", "$n quêtes accomplies", "quests", n, "game/trophy.png")) }
        listOf(1L, 5L, 10L, 25L, 50L).forEachIndexed { i,n ->
            val tiers=listOf("bronze","silver","gold","platinum","diamond")
            val title=listOf("Premier sentier","Randonneur régulier","Esprit des sentiers","Explorateur","Compagnon des sommets")[i]
            add(Badge("hikes_$n",title,"hikes",n,"game/hiking/mountain-${tiers[i]}.png",title))
        }
        listOf(10L,50L,100L,250L,500L).forEachIndexed { i,n ->
            add(Badge("hike_km_$n","$n km sur les sentiers","hikeMeters",n*1000,"game/hiking/compass-${listOf("bronze","silver","gold","platinum","diamond")[i]}.png"))
        }
        listOf(100L,500L,1000L,5000L,10000L).forEachIndexed { i,n ->
            add(Badge("hike_ascent_$n","$n m de montée en randonnée","hikeAscent",n,"game/hiking/mountain-${listOf("bronze","silver","gold","platinum","diamond")[i]}.png"))
        }
        Progression.Track.entries.forEach { track ->
            (if (track.weekly) listOf(1L, 4L, 12L, 26L, 52L) else listOf(3L, 7L, 30L, 60L, 100L, 365L)).forEach { n ->
                val asset = when (track) {
                    Progression.Track.FOOD -> "food"; Progression.Track.STRENGTH -> "strength"; Progression.Track.RUN -> "run"
                    Progression.Track.STEPS -> when { n >= 60 -> "steps-60"; n >= 30 -> "steps-30"; n >= 7 -> "steps"; else -> "steps-3" }
                    Progression.Track.HIKE -> "run"; Progression.Track.SLEEP -> "sleep"; Progression.Track.MIND -> "mind"
                }
                add(Badge("streak_${track.name}_$n", "${track.label} · $n ${if (track.weekly) "semaines" else "jours"}", "streak:${track.name}", n, if(track==Progression.Track.HIKE) "game/hiking/mountain-gold.png" else "game/garmin/$asset.png"))
            }
        }
    }
    fun find(id: String?) = catalog.firstOrNull { it.id == id }
    fun value(badge: Badge, summary: Progression.Summary): Long = when {
        badge.metric == "xp" -> summary.totalXp
        badge.metric == "records" -> summary.records.toLong()
        badge.metric.startsWith("streak:") -> summary.streaks.firstOrNull { it.track == badge.metric.substringAfter(':') }?.best?.toLong() ?: 0L
        else -> summary.totals[badge.metric] ?: 0L
    }
    fun unlocked(summary: Progression.Summary) = catalog.filter { value(it, summary) >= it.threshold }.map { it.id }.toSet()
    fun validDecor(title: String?, badges: List<String>) = (title == null || find(title)?.title != null) &&
        badges.size <= 3 && badges.distinct().size == badges.size && badges.all { find(it) != null }
}
