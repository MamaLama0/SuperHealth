package app.superhealth.cards

import app.superhealth.platform.*

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Partage d'une carte dans le Fil ou un Cercle. La publication reste un texte ordinaire (titre et détail), lisible tel
 * quel par les versions qui ne connaissent pas les cartes : une version 3.5 refuserait toute la carte d'un ami portant
 * un type de publication inconnu. Sa dernière ligne, « Fond N123 · BRILLANTE · Niv. 3 · Note 87 », permet aux versions
 * récentes de redessiner la carte elle-même. Aucune donnée de santé : le fond, sa finition, son niveau et la note globale.
 */
object CardShare {
    enum class Event { PULL, COLLECTION, EQUIPPED, ATHLETE }
    data class Shared(val pull: Pull, val level: Int, val ovr: Int?)

    private val line = Regex("""(?m)^Fond (N\d{3}) · ([A-Z]+) · Niv\. ([1-5])(?: · Note (\d{1,3}))?$""")

    fun parse(detail: String): Shared? {
        val m = line.find(detail) ?: return null
        val pull = Pull.of(m.groupValues[1] + ":" + m.groupValues[2]) ?: return null
        return Shared(pull, m.groupValues[3].toInt(), m.groupValues[4].toIntOrNull()?.takeIf { it in 1..99 })
    }

    /** Le détail sans la ligne technique, pour l'afficher sous la carte. */
    fun caption(detail: String) = detail.replace(line, "").trim()

    /** Brouillon à reprendre par le compositeur du fil des amis (titre puis détail, en texte ordinaire). */
    val draft = MutableStateFlow<String?>(null)

    /** Prépare le compositeur du fil des amis. */
    fun open(pull: Pull, level: Int, event: Event, ovr: Int? = null) { draft.value = draft(pull, level, event, ovr) }

    fun draft(pull: Pull, level: Int, event: Event, ovr: Int? = null): String {
        val c = pull.card
        val title = when (event) {
            Event.PULL -> (if (pull.finish == Finish.CHROMATIQUE) "Carte ${c.rarityLabel.lowercase()} chromatique tirée : "
                else if (c.rarity.big || c.duo) "Carte ${c.rarityLabel.lowercase()} tirée : " else "Nouvelle carte : ") + c.name
            Event.COLLECTION -> "Dans ma collection : " + c.name
            Event.EQUIPPED -> "Mon nouveau fond : " + c.name
            Event.ATHLETE -> "Ma carte Athlète" + (ovr?.let { " · $it" } ?: "")
        }
        val detail = listOf(
            c.rarityLabel + " · " + c.elements.joinToString(" + ") { it.label } + if (pull.finish != Finish.NORMALE) " · " + pull.finish.label else "",
            Bonus.label(pull, level) + " en duel",
            "Fond ${c.id} · ${pull.finish.name} · Niv. $level" + (ovr?.let { " · Note $it" } ?: ""),
        ).joinToString("\n")
        return title.take(100) + "\n" + detail
    }
}
