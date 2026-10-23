package app.superhealth.modules

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Icône de l'app : toujours la lune de base (choix de l'utilisateur, 2026-10-23). L'alias pixel-art reste dans le manifeste,
 * désactivé : [apply] le désactive aussi sur les téléphones où l'Aventure l'avait activé.
 */
object LauncherIcon {
    private const val DEFAULT = "app.superhealth.Launcher"
    private const val PIXEL = "app.superhealth.LauncherPixel"

    fun pixelWanted(): Boolean = false

    fun apply(ctx: Context) {
        val pixel = pixelWanted()
        // la nouvelle icône d'abord, pour qu'il y en ait toujours une dans le lanceur
        if (pixel) { set(ctx, PIXEL, true, false); set(ctx, DEFAULT, false, true) } else { set(ctx, DEFAULT, true, true); set(ctx, PIXEL, false, false) }
    }

    private fun set(ctx: Context, cls: String, on: Boolean, manifestDefault: Boolean) {
        val pm = ctx.packageManager
        val cn = ComponentName(ctx.packageName, cls)
        val state = runCatching { pm.getComponentEnabledSetting(cn) }.getOrNull() ?: return
        val enabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && manifestDefault)
        if (enabled == on) return
        runCatching {
            pm.setComponentEnabledSetting(cn, if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP)
        }
    }
}
