package app.superhealth.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Langue de l'interface de SuperHealth, commune aux trois modules. Le texte source est en français ;
 * [english] est un état Compose : tout texte passé par un tr() pendant la composition suit le changement.
 */
object Lang {
    var english by mutableStateOf(false)
        private set

    /** [name] : SYSTEM, FR ou EN (même enum dans chaque module). */
    fun apply(name: String) {
        english = when (name) {
            "EN" -> true
            "FR" -> false
            else -> Locale.getDefault().language != "fr"
        }
    }

    val locale: Locale get() = if (english) Locale.UK else Locale.FRANCE
}

/** Dictionnaires de la coque, puis des modules nutrition et entraînement. */
private val dictionaries by lazy {
    listOf(SuperStrings.map, Strings11.map, Strings12.map, Strings13.map, Strings14.map, Strings15.map, Strings16.map, Strings17.map, Strings18.map, Strings21.map, Strings22.map, app.superhealth.nutrition.ui.EnStrings.map, app.superhealth.training.ui.EnStrings.map)
}
private val reverse: Map<String, String> by lazy { dictionaries.reversed().flatMap { it.entries }.associate { (k, v) -> v to k } }

/** Traduit un libellé source (français). Idempotent comme le tr() des modules. */
fun tr(s: String): String = if (Lang.english) dictionaries.firstNotNullOfOrNull { it[s] } ?: s else reverse[s] ?: s

/** Libellé avec arguments : le gabarit source utilise %1$s, %2$s… */
fun trf(fmt: String, vararg args: Any?): String = String.format(Lang.locale, tr(fmt), *args)

@JvmName("trExt") fun String.tr(): String = tr(this)

/** Prénom saisi (profil Gadgetbridge, envoyé aux montres) ; null s'il n'a pas été renseigné. */
fun userName(): String? = nodomain.freeyourgadget.gadgetbridge.model.ActivityUser().name?.trim()?.takeIf { it.isNotBlank() && it != DEFAULT_GB_USER }

private const val DEFAULT_GB_USER = "gadgetbridge-user"
