package app.superhealth.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.superhealth.nutrition.domain.AccentColor

/**
 * Icône du lanceur : le cœur prend la couleur d'accent choisie (alias d'activité du manifeste, un seul activé à la fois).
 * Les images viennent de tools/accent_icons/build.sh ; le rose garde l'icône d'origine.
 */
object LauncherIcon {
    private fun alias(a: AccentColor) = "app.superhealth.Launcher" + when (a) {
        AccentColor.PINK -> ""
        AccentColor.BLUE -> "Blue"; AccentColor.GREEN -> "Green"; AccentColor.YELLOW -> "Yellow"
        AccentColor.PURPLE -> "Purple"; AccentColor.ORANGE -> "Orange"; AccentColor.GRAY -> "Gray"
    }

    /**
     * Avant Android 10, les lanceurs ajoutent souvent une icône à l'accueil à chaque alias activé et gardent l'ancienne :
     * l'app y apparaît plusieurs fois. L'icône reste alors celle d'origine.
     */
    val supported get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q

    /** Active l'alias de [accent] puis désactive les autres (dans cet ordre : jamais d'app sans icône). */
    fun apply(ctx: Context, chosen: AccentColor) {
        val accent = if (supported) chosen else AccentColor.PINK
        val pm = ctx.packageManager
        fun set(a: AccentColor, on: Boolean) {
            val c = ComponentName(ctx.packageName, alias(a))
            val want = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            val cur = pm.getComponentEnabledSetting(c)
            // état par défaut du manifeste : seul le rose est activé
            val isOn = cur == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (cur == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && a == AccentColor.PINK)
            if (isOn != on) runCatching { pm.setComponentEnabledSetting(c, want, PackageManager.DONT_KILL_APP) }
        }
        set(accent, true)
        AccentColor.entries.filter { it != accent }.forEach { set(it, false) }
    }

    /**
     * Au démarrage : rétablit une icône seulement si aucun alias n'est actif (ancienne icône de l'Aventure, 1.2).
     * Ne réactive jamais un second alias : sur les anciens lanceurs, chaque bascule peut laisser une icône en double.
     */
    fun repair(ctx: Context, accent: AccentColor) {
        val pm = ctx.packageManager
        val active = AccentColor.entries.filter { a ->
            val cur = pm.getComponentEnabledSetting(ComponentName(ctx.packageName, alias(a)))
            cur == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (cur == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && a == AccentColor.PINK)
        }
        when {
            !supported -> if (active != listOf(AccentColor.PINK)) apply(ctx, accent)
            active.isEmpty() -> apply(ctx, accent)
            // plusieurs icônes (version précédente) : on garde la couleur choisie et on éteint les autres
            active.size > 1 -> apply(ctx, accent.takeIf { it in active } ?: active.first())
        }
    }
}
