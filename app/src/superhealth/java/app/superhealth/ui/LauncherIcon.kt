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

    /** Active l'alias de [accent] puis désactive les autres (dans cet ordre : jamais d'app sans icône). */
    fun apply(ctx: Context, accent: AccentColor) {
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
}
