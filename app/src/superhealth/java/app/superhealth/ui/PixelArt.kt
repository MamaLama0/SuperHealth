package app.superhealth.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Côté réellement dessiné pour une image pixel-art qui doit tenir dans [side] px : multiple entier de sa taille pour que
 * chaque pixel source reste un carré net ; en dessous de la taille d'origine, simple réduction.
 */
fun pixelSide(img: ImageBitmap, side: Float): Float {
    val n = max(img.width, img.height).toFloat()
    return if (side >= n) floor(side / n) * n else side
}

/** Dessine [img] centrée sur [center] dans un carré d'au plus [side] px, agrandie sans lissage (voir [pixelSide]). */
fun DrawScope.drawPixelArt(img: ImageBitmap, center: Offset, side: Float) {
    val s = pixelSide(img, side)
    val k = s / max(img.width, img.height)
    val w = (img.width * k).roundToInt(); val h = (img.height * k).roundToInt()
    drawImage(img, dstOffset = IntOffset((center.x - w / 2f).roundToInt(), (center.y - h / 2f).roundToInt()), dstSize = IntSize(w, h),
        filterQuality = if (k >= 1f) FilterQuality.None else FilterQuality.Medium)
}
