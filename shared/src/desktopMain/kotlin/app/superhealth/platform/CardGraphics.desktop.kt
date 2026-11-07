package app.superhealth.platform

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

actual class CardShader actual constructor(source: String) {
    private val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(source))

    actual fun setFloatUniform(name: String, vararg values: Float) {
        when (values.size) {
            1 -> builder.uniform(name, values[0])
            2 -> builder.uniform(name, values[0], values[1])
            3 -> builder.uniform(name, values[0], values[1], values[2])
            else -> builder.uniform(name, values[0], values[1], values[2], values[3])
        }
    }

    actual fun brush(): Brush = ShaderBrush(builder.makeShader().asComposeShader())
}

actual fun startTilt(onTilt: (roll: Float, pitch: Float) -> Unit): () -> Unit = {}

actual fun cpuCores(): Int = Runtime.getRuntime().availableProcessors()

actual suspend fun cutoutPerson(photo: ByteArray): ByteArray? = null

actual fun encodeCardPhoto(png: ByteArray, maxHeight: Int, quality: Int): ByteArray? = runCatching {
    val image = org.jetbrains.skia.Image.makeFromEncoded(png)
    val scale = minOf(1f, maxHeight.toFloat() / image.height)
    val w = (image.width * scale).toInt().coerceAtLeast(1); val h = (image.height * scale).toInt().coerceAtLeast(1)
    val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(w, h)
    surface.canvas.drawImageRect(image, org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()))
    surface.makeImageSnapshot().encodeToData(org.jetbrains.skia.EncodedImageFormat.WEBP, quality)?.bytes
}.getOrNull()
