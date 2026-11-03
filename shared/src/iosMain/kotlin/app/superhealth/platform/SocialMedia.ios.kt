package app.superhealth.platform

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.*
import platform.AVFoundation.*
import platform.AVFAudio.*
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGImageRelease
import platform.CoreImage.*
import platform.CoreMedia.CMTimeMake
import platform.Foundation.*
import platform.UIKit.*

actual fun clipboardText(): String = UIPasteboard.generalPasteboard.string.orEmpty()
actual fun copyText(text: String) { UIPasteboard.generalPasteboard.string=text }
actual fun qrBitmap(text: String,pixels: Int): Bitmap {
    val filter=CIFilter.filterWithName("CIQRCodeGenerator") ?: error("QR unavailable")
    filter.setValue(text.encodeToByteArray().toNSData(),forKey="inputMessage")
    filter.setValue("M",forKey="inputCorrectionLevel")
    val code=filter.outputImage ?: error("QR encoding failed")
    // Integral scaling preserves crisp modules; white quiet zone is added by the Compose sheet.
    val width=code.extent.useContents { size.width }
    val scale=kotlin.math.max(1.0,kotlin.math.floor(pixels/width))
    val scaled=code.imageByApplyingTransform(CGAffineTransformMakeScale(scale,scale))
    val image=CIContext.contextWithOptions(null).createCGImage(scaled,scaled.extent) ?: error("QR rendering failed")
    val png=try { UIImagePNGRepresentation(UIImage.imageWithCGImage(image)) ?: error("QR bitmap failed") } finally { CGImageRelease(image) }
    return Bitmap(decodeImage(png.toByteArray()) ?: error("QR decoding failed"))
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
actual fun NativeVideo(url: String,playing: Boolean,controls: Boolean,modifier: Modifier) {
    val player=remember(url) { AVPlayer(uRL=NSURL.URLWithString(url)!!).apply { muted=true } }
    val active by rememberUpdatedState(playing)
    DisposableEffect(player) {
        val observer=NSNotificationCenter.defaultCenter.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification,player.currentItem,NSOperationQueue.mainQueue) {
            player.seekToTime(CMTimeMake(0,1)); if(active)player.play()
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            player.pause();player.replaceCurrentItemWithPlayerItem(null)
        }
    }
    LaunchedEffect(player,playing) { if(playing)player.play() else player.pause() }
    UIKitViewController(factory={AVPlayerViewController().apply { this.player=player;showsPlaybackControls=controls }},modifier=modifier,
        update={it.showsPlaybackControls=controls})
}
actual class LocalAudio actual constructor() {
    private var player: AVAudioPlayer?=null
    actual val isPlaying: Boolean get()=player?.playing==true
    actual var volume: Float=.3f
        set(value) {field=value.coerceIn(0f,1f);player?.volume=field}
    actual var looping: Boolean=false
        set(value) {field=value;player?.numberOfLoops=if(value)-1 else 0}
    actual fun load(path: String) {
        release()
        player=AVAudioPlayer(contentsOfURL=NSURL.fileURLWithPath(path),error=null).apply { volume=this@LocalAudio.volume;numberOfLoops=if(looping)-1 else 0;prepareToPlay() }
    }
    actual fun play() {player?.play()}
    actual fun pause() {player?.pause()}
    actual fun release() {player?.stop();player=null}
}
