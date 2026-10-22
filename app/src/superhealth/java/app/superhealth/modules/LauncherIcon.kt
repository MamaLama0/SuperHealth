package app.superhealth.modules

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.superhealth.SuperPrefs

/**
 * Icône de l'app : la lune habituelle, ou sa version pixel-art quand le module Aventure est actif et l'option choisie.
 * Deux alias du lanceur pointent vers MainActivity ; un seul est activé à la fois.
 */
object LauncherIcon {
    private const val DEFAULT = "app.superhealth.Launcher"
    private const val PIXEL = "app.superhealth.LauncherPixel"

    fun pixelWanted(): Boolean = Modules.isActive(Modules.ADVENTURE) && SuperPrefs.settings.value.pixelIcon

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
