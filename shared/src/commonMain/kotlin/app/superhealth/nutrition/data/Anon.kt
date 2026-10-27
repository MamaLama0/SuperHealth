package app.superhealth.nutrition.data

import app.superhealth.ui.userName

/**
 * Anonymisation de tout ce qui part vers une IA : le nom de l'utilisateur (chaque mot du nom saisi, partout) et, dans
 * le texte tapé, les adresses e-mail et numéros de téléphone sont remplacés avant l'envoi. Si l'IA reprend le repère
 * « [prénom] », le prénom est remis localement dans sa réponse : il ne quitte jamais le téléphone.
 */
object Anon {
    private const val NAME = "[prénom]"
    private val EMAIL = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")
    private val PHONE = Regex("(?<![\\w.,])(\\+\\d{1,3}|0)[ .-]?\\d([ .-]?\\d){7,12}(?![\\w.,])")
    private val SPECIAL = Regex("[\\\\^$.|?*+()\\[\\]{}]")

    private fun parts(): List<String> = userName()?.split(Regex("[\\s,;]+"))?.map { it.trim('\'', '-', '.') }
        ?.filter { it.length >= 2 }?.sortedByDescending { it.length }.orEmpty()

    /** Nom retiré (données générées par l'appli : profil, activités, appareils…). */
    fun name(text: String): String = parts().fold(text) { t, p ->
        Regex("(?<![\\p{L}\\p{N}])" + SPECIAL.replace(p) { "\\" + it.value } + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).replace(t, NAME)
    }

    /** Texte tapé par l'utilisateur : nom, e-mails et téléphones retirés. */
    fun typed(text: String): String = name(PHONE.replace(EMAIL.replace(text, "[e-mail]"), "[téléphone]"))

    /** Réponse de l'IA : le repère redevient le prénom, sur le téléphone. */
    fun restore(text: String): String = text.replace(NAME, userName()?.substringBefore(' ') ?: "")
}
