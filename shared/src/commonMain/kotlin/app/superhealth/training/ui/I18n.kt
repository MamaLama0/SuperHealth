package app.superhealth.training.ui

import app.superhealth.platform.*

import app.superhealth.training.domain.Language
import app.superhealth.platform.Locale

/**
 * Langue de l'interface, partagée par toute l'app (app.superhealth.ui.Lang). Le texte source est en français ;
 * ce module garde son propre dictionnaire anglais ([EnStrings]).
 */
object Lang {
    val english: Boolean get() = app.superhealth.ui.Lang.english
    fun apply(l: Language) = app.superhealth.ui.Lang.apply(l.name)
    val locale: Locale get() = app.superhealth.ui.Lang.locale
}

private val reverse: Map<String, String> by lazy { EnStrings.map.entries.associate { (k, v) -> v to k } }

/** Traduit un libellé source (français) ; à défaut, avec le dictionnaire commun de l'app. Idempotent : un libellé déjà traduit est rendu tel quel, ou ramené au français. */
fun tr(s: String): String = if (Lang.english) EnStrings.map[s] ?: app.superhealth.ui.tr(s) else reverse[s] ?: app.superhealth.ui.tr(s)

/** Libellé avec arguments : le gabarit source utilise %1$s, %2$s… */
fun trf(fmt: String, vararg args: Any?): String = String.format(Lang.locale, tr(fmt), *args)

@kotlin.jvm.JvmName("trExt") fun String.tr(): String = tr(this)
