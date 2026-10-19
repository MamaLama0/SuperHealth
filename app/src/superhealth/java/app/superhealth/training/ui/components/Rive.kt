package app.superhealth.training.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop

/**
 * Illustration animée Rive des assets MacroFactor (`assets/rive/<name>.riv`).
 * Sans [artboard] : artboard par défaut et sa machine à états ; avec [animation] : cette animation seule.
 */
@Composable
fun RiveAsset(
    name: String, modifier: Modifier = Modifier, artboard: String? = null, animation: String? = null,
    fit: Fit = Fit.CONTAIN, loop: Loop = Loop.AUTO,
) {
    AndroidView(modifier = modifier, factory = { ctx ->
        RiveAnimationView(ctx).apply {
            val bytes = ctx.assets.open("rive/$name.riv").use { it.readBytes() }
            isClickable = false; isFocusable = false
            runCatching { setRiveBytes(bytes, artboardName = artboard ?: DEFAULT_ARTBOARD[name], animationName = animation, autoplay = true, fit = fit, loop = loop) }
        }
    })
}

/** Fichiers dont l'artboard par défaut est un plan de référence (fond gris) : on prend la scène. */
private val DEFAULT_ARTBOARD = mapOf("mf_rocket_launcher" to "scene", "mf_rocket_launcher_v2" to "scene", "mf_saturn" to "scene", "mf_flying_rocket" to "scene", "mf_landing_module" to "scene")
