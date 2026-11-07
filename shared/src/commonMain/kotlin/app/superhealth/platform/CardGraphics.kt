package app.superhealth.platform

import androidx.compose.ui.graphics.Brush

/** Shader d'exécution (SkSL, le langage de l'AGSL d'Android) : uniformes réglés avant chaque dessin. */
expect class CardShader(source: String) {
    fun setFloatUniform(name: String, vararg values: Float)
    fun brush(): Brush
}

/** Inclinaison du téléphone (roulis, tangage en radians) ; renvoie de quoi arrêter l'écoute. */
expect fun startTilt(onTilt: (roll: Float, pitch: Float) -> Unit): () -> Unit

expect fun cpuCores(): Int

/**
 * Détoure la personne d'une photo (JPEG ou PNG) sur l'appareil : PNG au fond transparent, null si personne n'est
 * reconnu ou si le détourage n'est pas disponible.
 */
expect suspend fun cutoutPerson(photo: ByteArray): ByteArray?

/** Photo détourée (PNG transparent) réduite à [maxHeight] px de haut et encodée en WebP [quality], transparence gardée. */
expect fun encodeCardPhoto(png: ByteArray, maxHeight: Int, quality: Int): ByteArray?
