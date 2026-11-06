package app.superhealth.platform

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.cinterop.useContents
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.CoreImage.filterWithName
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.Foundation.setValue
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.Vision.VNGeneratePersonSegmentationRequest
import platform.Vision.VNGeneratePersonSegmentationRequestQualityLevelAccurate
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNPixelBufferObservation
import platform.CoreVideo.kCVPixelFormatType_OneComponent8

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

private val motion by lazy { CMMotionManager() }
private var listeners = 0

actual fun startTilt(onTilt: (roll: Float, pitch: Float) -> Unit): () -> Unit {
    if (!motion.deviceMotionAvailable) return {}
    var active = true
    motion.deviceMotionUpdateInterval = 1.0 / 60
    listeners++
    motion.startDeviceMotionUpdatesToQueue(NSOperationQueue.mainQueue) { m, _ ->
        val a = m?.attitude ?: return@startDeviceMotionUpdatesToQueue
        if (active) onTilt(a.roll.toFloat(), a.pitch.toFloat())
    }
    return { active = false; if (--listeners <= 0) { listeners = 0; motion.stopDeviceMotionUpdates() } }
}

actual fun cpuCores(): Int = NSProcessInfo.processInfo.activeProcessorCount.toInt()

/** Segmentation de personne de Vision (iOS 15+), appliquée en masque doux sur la photo d'origine. */
actual suspend fun cutoutPerson(photo: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
    val image = UIImage(data = photo.toNSData())
    val cg = image.CGImage ?: return@withContext null
    val request = VNGeneratePersonSegmentationRequest()
    request.qualityLevel = VNGeneratePersonSegmentationRequestQualityLevelAccurate
    request.outputPixelFormat = kCVPixelFormatType_OneComponent8
    val handler = VNImageRequestHandler(cGImage = cg, options = emptyMap<Any?, Any?>())
    if (!handler.performRequests(listOf(request), null)) return@withContext null
    val mask = (request.results?.firstOrNull() as? VNPixelBufferObservation)?.pixelBuffer ?: return@withContext null
    val source = CIImage(cGImage = cg)
    val w = CGImageGetWidth(cg).toDouble(); val h = CGImageGetHeight(cg).toDouble()
    val maskImage = CIImage(cVPixelBuffer = mask)
    val (mw, mh) = maskImage.extent.useContents { size.width to size.height }
    if (mw <= 0 || mh <= 0) return@withContext null
    val scaled = maskImage.imageByApplyingTransform(CGAffineTransformMakeScale(w / mw, h / mh))
    val blend = CIFilter.filterWithName("CIBlendWithMask") ?: return@withContext null
    blend.setValue(source, forKey = "inputImage")
    blend.setValue(CIImage.emptyImage(), forKey = "inputBackgroundImage")
    blend.setValue(scaled, forKey = "inputMaskImage")
    val output = blend.outputImage ?: return@withContext null
    val rendered = CIContext().createCGImage(output, fromRect = source.extent) ?: return@withContext null
    try { UIImagePNGRepresentation(UIImage(cGImage = rendered))?.toByteArray() } finally { CGImageRelease(rendered) }
}
