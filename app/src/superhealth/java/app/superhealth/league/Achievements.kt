package app.superhealth.league

/** Les seuils SuperHealth utilisent uniquement les totaux enregistrés ; les images ne confèrent aucun badge Garmin. */
object Achievements {
    data class Badge(val id: String, val label: String, val metric: String, val threshold: Long, val asset: String, val title: String? = null)
    val STREAK_TRACKS = listOf(Progression.Track.STRENGTH, Progression.Track.FOOD, Progression.Track.RUN, Progression.Track.STEPS, Progression.Track.SLEEP, Progression.Track.MIND)
    val STREAK_WEEKS = listOf(1, 4, 10, 20, 30, 52, 104)
    val catalog: List<Badge> = buildList {
        // Paliers payés une fois : volontairement hauts, le dernier de chaque type est un objectif de plusieurs années.
        listOf(10L, 50L, 100L, 250L, 500L, 1000L, 2000L).forEach { n -> add(Badge("strength_$n", "$n séances de musculation", "sessions", n, "")) }
        listOf(5L, 25L, 50L, 100L, 250L, 500L, 1000L).forEach { n -> add(Badge("records_$n", "$n records sportifs", "records", n, "")) }
        listOf(30L, 100L, 250L, 500L, 1000L, 2000L).forEach { n -> add(Badge("food_$n", "$n jours de nutrition suivis", "foodDays", n, "")) }
        listOf(300L, 1000L, 3000L, 10000L, 25000L, 50000L).forEach { n -> add(Badge("mind_$n", "$n minutes pour l'esprit et la mobilité", "mindMinutes", n, "")) }
        listOf(10L, 25L, 50L, 100L, 250L, 500L, 1000L).forEach { n -> add(Badge("quests_$n", "$n quêtes accomplies", "quests", n, "")) }
        listOf(50L, 250L, 1000L, 2500L, 5000L, 10000L, 25000L).forEach { n -> add(Badge("distance_$n", "$n km parcourus", "distanceKm", n, "")) }
        // Séries, toutes à la semaine : 1, 4, 10, 20, 30, 52 puis 104 semaines d'affilée.
        STREAK_TRACKS.forEach { track ->
            STREAK_WEEKS.forEach { n -> add(Badge("streak_${track.name}_$n", "${track.label} · $n semaines", "streak:${track.name}", n.toLong(), "")) }
        }
    }
    fun find(id: String?) = catalog.firstOrNull { it.id == id }
    /** Or d'un palier, alignés sur BADGES.reward du serveur : de plus en plus à mesure que le palier monte. */
    val REWARDS = listOf(25, 50, 100, 175, 275, 400, 600)
    /** Paliers de série (1, 4, 10, 20, 30, 52, 104 semaines) : 200 à 10 semaines, 250 à 30. */
    val STREAK_REWARDS = listOf(20, 100, 200, 225, 250, 400, 600)
    /** Rang du badge dans son type (0 : premier palier). */
    fun rank(b: Badge) = catalog.filter { it.metric == b.metric }.indexOf(b)
    /** Or d'un badge. */
    fun reward(b: Badge): Int = (if (b.metric.startsWith("streak:")) STREAK_REWARDS else REWARDS).getOrElse(rank(b)) { 0 }
    /** Prochain palier non atteint d'un type, ou null s'ils le sont tous. */
    fun next(metric: String, earned: Set<String>) = catalog.firstOrNull { it.metric == metric && it.id !in earned }
    fun value(badge: Badge, summary: Progression.Summary): Long = when {
        badge.metric == "xp" -> summary.totalXp
        badge.metric == "records" -> summary.records.toLong()
        badge.metric.startsWith("streak:") -> summary.weekly.firstOrNull { it.track == badge.metric.substringAfter(':') }?.best?.toLong() ?: 0L
        else -> summary.totals[badge.metric] ?: 0L
    }
    fun unlocked(summary: Progression.Summary) = catalog.filter { value(it, summary) >= it.threshold }.map { it.id }.toSet()
    /** La mise en avant de badges n'existe plus : on accepte encore les anciens profils d'amis (titre ou badges retirés compris). */
    fun validDecor(title: String?, badges: List<String>) = (title == null || title.length <= 40) && badges.size <= 3 && badges.distinct().size == badges.size
}
