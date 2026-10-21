package app.superhealth.training.domain

/**
 * Organisation d'un programme : ajout, retrait et déplacement des jours. Le jour courant est suivi par son identifiant,
 * pas par sa position : déplacer ou retirer un autre jour ne change pas la prochaine séance proposée.
 */
object ProgramEdit {
    /** [from] déplacé en [to] (les autres glissent). */
    fun <T> List<T>.moved(from: Int, to: Int): List<T> {
        if (from !in indices || to !in indices || from == to) return this
        return toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    fun moveDay(p: Program, from: Int, to: Int): Program = keepCursor(p, p.copy(days = p.days.moved(from, to)))

    fun removeDay(p: Program, i: Int): Program {
        if (i !in p.days.indices || p.days.size <= 1) return p
        val removed = p.days[i].id
        val next = p.copy(days = p.days.filterIndexed { k, _ -> k != i }, doneDayIds = p.doneDayIds - removed)
        // le jour courant retiré : on passe au suivant (même position, ou retour au début)
        return if (p.days.getOrNull(p.dayIndex)?.id == removed) fixCursor(next.copy(dayIndex = i.coerceAtMost(next.days.lastIndex)))
        else keepCursor(p, next)
    }

    fun addDay(p: Program, day: ProgramDay): Program = fixCursor(p.copy(days = p.days + day))

    fun setDay(p: Program, i: Int, day: ProgramDay): Program =
        if (i !in p.days.indices) p else fixCursor(p.copy(days = p.days.toMutableList().also { it[i] = day }))

    /** Même jour courant (par identifiant) après une modification de la liste. */
    private fun keepCursor(before: Program, after: Program): Program {
        val id = before.days.getOrNull(before.dayIndex)?.id
        val idx = after.days.indexOfFirst { it.id == id }
        return fixCursor(if (idx >= 0) after.copy(dayIndex = idx) else after)
    }

    /**
     * Jour courant valide : un jour d'entraînement pas encore fait du cycle, à partir de la position actuelle (en revenant au
     * début si besoin). Un jour de repos ou déjà fait n'est jamais proposé ; les jours faits disparus sont oubliés.
     */
    fun fixCursor(p: Program): Program {
        if (p.days.isEmpty()) return p.copy(dayIndex = 0, doneDayIds = emptyList())
        val ids = p.days.map { it.id }.toSet()
        val done = p.doneDayIds.filter { it in ids }
        val start = p.dayIndex.coerceIn(0, p.days.lastIndex)
        val order = (start until p.days.size) + (0 until start)
        val idx = order.firstOrNull { !p.days[it].isRest && p.days[it].id !in done }
            ?: order.firstOrNull { !p.days[it].isRest } ?: start
        return if (idx == p.dayIndex && done.size == p.doneDayIds.size) p else p.copy(dayIndex = idx, doneDayIds = done)
    }
}
