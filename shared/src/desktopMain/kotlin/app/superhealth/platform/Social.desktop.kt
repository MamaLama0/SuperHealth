package app.superhealth.platform

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import java.security.KeyPairGenerator
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.DataFlavor
import java.awt.Toolkit

actual fun generateSigningIdentity(): SigningIdentity {
    val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    return SigningIdentity(pair.private.encoded, pair.public.encoded)
}
actual fun signIdentity(privateKey: ByteArray, message: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
    initSign(KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privateKey))); update(message); sign()
}
actual fun verifyIdentity(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean = runCatching {
    Signature.getInstance("SHA256withECDSA").run { initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKey))); update(message); verify(signature) }
}.getOrDefault(false)
actual fun clipboardText(): String = runCatching { Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String }.getOrDefault("")
actual fun copyText(text: String) { runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) } }
actual fun qrBitmap(text: String, pixels: Int): Bitmap {
    val m = com.google.zxing.MultiFormatWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, pixels, pixels)
    val image = java.awt.image.BufferedImage(pixels, pixels, java.awt.image.BufferedImage.TYPE_INT_RGB)
    for (y in 0 until pixels) for (x in 0 until pixels) image.setRGB(x, y, if (m[x,y]) 0 else 0xffffff)
    val out = java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(image,"png",out)
    return Bitmap(decodeImage(out.toByteArray())!!)
}
@Composable actual fun NativeVideo(url: String, playing: Boolean, controls: Boolean, modifier: Modifier) { Box(modifier) }
actual class LocalAudio actual constructor() {
    actual val isPlaying: Boolean get() = false
    actual var volume: Float = .3f
    actual var looping: Boolean = false
    actual fun load(path: String) {}
    actual fun play() {}
    actual fun pause() {}
    actual fun release() {}
}

actual fun identitySharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray = javax.crypto.KeyAgreement.getInstance("ECDH").run {
    init(KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privateKey)))
    doPhase(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKey)), true)
    generateSecret()
}

actual fun normalizeSigningIdentity(identity: SigningIdentity) = SigningIdentity(app.superhealth.social.P256Encoding.pkcs8(identity.privateKey, identity.publicKey), identity.publicKey)
