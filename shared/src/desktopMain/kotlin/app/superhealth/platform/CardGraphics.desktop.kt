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
