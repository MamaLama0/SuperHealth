package app.superhealth.cards

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Détourage de la photo de carte, entièrement sur le téléphone, comme ReFra (Apache-2.0) : MobileSAM exporté en ONNX
 * par Acly (Apache-2.0), un encodeur lancé une fois par photo et un décodeur relancé à chaque point posé.
 * Les modèles (≈ 45 Mo) sont téléchargés une fois, vérifiés par SHA-256, puis gardés dans les fichiers privés.
 */
object Cutout {
    private const val BASE = "https://huggingface.co/Acly/MobileSAM/resolve/main/"
    private const val SIDE = 1024
    data class Model(val file: String, val sha256: String, val bytes: Long)
    private val ENCODER = Model("mobile_sam_image_encoder.onnx", "580f5fb648ea1062c0aabc26217aed56921985f03f0cbbd852bba81d760cc749", 28_157_093)
    private val DECODER = Model("sam_mask_decoder_single.onnx", "93915fc7c993ab9d59ab8c9ccd3bce37f7509c81ab4150a74abd4d2abbd8570d", 16_501_323)
    val totalBytes = ENCODER.bytes + DECODER.bytes

    private fun dir(ctx: Context) = File(ctx.filesDir, "models/sam").apply { mkdirs() }
    fun installed(ctx: Context) = listOf(ENCODER, DECODER).all { File(dir(ctx), it.file).length() == it.bytes }

    /** Télécharge les deux modèles ; [progress] reçoit la part déjà reçue (0..1). */
    suspend fun download(ctx: Context, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        var done = 0L
        for (m in listOf(ENCODER, DECODER)) {
            val target = File(dir(ctx), m.file)
            if (target.length() == m.bytes) { done += m.bytes; continue }
            val tmp = File(dir(ctx), m.file + ".part")
            val digest = MessageDigest.getInstance("SHA-256")
            var url = URL(BASE + m.file)
            var conn: HttpURLConnection
            var hops = 0
            while (true) {
                conn = (url.openConnection() as HttpURLConnection).apply { instanceFollowRedirects = false; connectTimeout = 15000; readTimeout = 30000 }
                if (conn.responseCode in 300..399 && hops++ < 5) {
                    val next = URL(url, conn.getHeaderField("Location")); conn.disconnect()
                    require(next.protocol == "https") { "redirection non sécurisée" }
                    url = next
                } else break
            }
            require(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            conn.inputStream.use { input -> tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n); digest.update(buf, 0, n); done += n; progress(done.toFloat() / totalBytes) }
            } }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (sha != m.sha256) { tmp.delete(); error("empreinte invalide pour ${m.file}") }
            require(tmp.renameTo(target))
        }
        progress(1f)
    }

    /** Session de détourage d'une photo : l'image est encodée une fois, chaque appel à [mask] ne relance que le décodeur. */
    class Session private constructor(private val env: OrtEnvironment, private val decoder: OrtSession, private val embeddings: FloatArray,
                                      private val shape: LongArray, val image: Bitmap, private val scale: Float) : AutoCloseable {
        data class Point(val x: Float, val y: Float, val keep: Boolean)

        /** Masque alpha (0..255) à la taille de [image], affiné : plus grande zone contenant un point gardé, bords adoucis. */
        fun mask(points: List<Point>): ByteArray {
            val n = points.size + 1
            val coords = FloatArray(n * 2); val labels = FloatArray(n)
            points.forEachIndexed { i, p -> coords[i * 2] = p.x * scale; coords[i * 2 + 1] = p.y * scale; labels[i] = if (p.keep) 1f else 0f }
            labels[n - 1] = -1f
            val w = image.width; val h = image.height
            val inputs = mapOf(
                "image_embeddings" to OnnxTensor.createTensor(env, FloatBuffer.wrap(embeddings), shape),
                "point_coords" to OnnxTensor.createTensor(env, FloatBuffer.wrap(coords), longArrayOf(1, n.toLong(), 2)),
                "point_labels" to OnnxTensor.createTensor(env, FloatBuffer.wrap(labels), longArrayOf(1, n.toLong())),
                "mask_input" to OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(256 * 256)), longArrayOf(1, 1, 256, 256)),
                "has_mask_input" to OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(0f)), longArrayOf(1)),
                "orig_im_size" to OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(h.toFloat(), w.toFloat())), longArrayOf(2)),
            )
            val logits = try {
                decoder.run(inputs).use { r ->
                    val fb = (r[0] as OnnxTensor).floatBuffer
                    FloatArray(w * h).also { fb.get(it, 0, minOf(it.size, fb.remaining())) }
                }
            } finally { inputs.values.forEach { it.close() } }
            val binary = BooleanArray(w * h) { logits[it] > 0f }
            keepConnected(binary, w, h, points.filter { it.keep })
            return feather(binary, w, h)
        }

        /** Sujet détouré, recadré sur sa boîte englobante avec une petite marge, au plus 1 200 px de haut. */
        fun render(alpha: ByteArray): Bitmap? {
            val w = image.width; val h = image.height
            var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
            for (y in 0 until h) for (x in 0 until w) if ((alpha[y * w + x].toInt() and 0xFF) > 24) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y }
            if (x1 < 0) return null
            val m = ((y1 - y0) * 0.02f).roundToInt()
            x0 = (x0 - m).coerceAtLeast(0); y0 = (y0 - m).coerceAtLeast(0); x1 = (x1 + m).coerceAtMost(w - 1); y1 = (y1 + m).coerceAtMost(h - 1)
            val cw = x1 - x0 + 1; val ch = y1 - y0 + 1
            val px = IntArray(cw * ch)
            image.getPixels(px, 0, cw, x0, y0, cw, ch)
            for (y in 0 until ch) for (x in 0 until cw) {
                val a = alpha[(y + y0) * w + x + x0].toInt() and 0xFF
                px[y * cw + x] = (px[y * cw + x] and 0x00FFFFFF) or (a shl 24)
            }
            val out = Bitmap.createBitmap(px, cw, ch, Bitmap.Config.ARGB_8888)
            val k = 1200f / ch
            return if (k < 1f) Bitmap.createScaledBitmap(out, (cw * k).roundToInt().coerceAtLeast(1), 1200, true) else out
        }

        override fun close() = decoder.close()

        companion object {
            /** Encode la photo (réduite à 1 536 px au plus). Long : à lancer hors du fil principal. */
            suspend fun open(ctx: Context, photo: Bitmap): Session = withContext(Dispatchers.Default) {
                val env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply {
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
                }
                val k = 1536f / max(photo.width, photo.height)
                val image = if (k < 1f) Bitmap.createScaledBitmap(photo, (photo.width * k).roundToInt(), (photo.height * k).roundToInt(), true) else photo
                val scale = SIDE.toFloat() / max(image.width, image.height)
                val sw = (image.width * scale).roundToInt().coerceIn(1, SIDE); val sh = (image.height * scale).roundToInt().coerceIn(1, SIDE)
                val small = Bitmap.createScaledBitmap(image, sw, sh, true)
                val px = IntArray(sw * sh); small.getPixels(px, 0, sw, 0, 0, sw, sh)
                val input = FloatArray(SIDE * SIDE * 3)
                for (y in 0 until sh) for (x in 0 until sw) {
                    val c = px[y * sw + x]; val o = (y * SIDE + x) * 3
                    input[o] = Color.red(c).toFloat(); input[o + 1] = Color.green(c).toFloat(); input[o + 2] = Color.blue(c).toFloat()
                }
                val dir = dir(ctx)
                val (emb, shape) = env.createSession(File(dir, ENCODER.file).absolutePath, opts).use { enc ->
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(SIDE.toLong(), SIDE.toLong(), 3)).use { t ->
                        enc.run(mapOf("input_image" to t)).use { r ->
                            val out = r[0] as OnnxTensor
                            val fb = out.floatBuffer
                            FloatArray(fb.remaining()).also { fb.get(it) } to out.info.shape
                        }
                    }
                }
                Session(env, env.createSession(File(dir, DECODER.file).absolutePath, opts), emb, shape, image.copy(Bitmap.Config.ARGB_8888, false), scale)
            }
        }
    }

    /** Ne garde que les zones reliées à un point « garder » (évite les îlots du décor). */
    private fun keepConnected(mask: BooleanArray, w: Int, h: Int, seeds: List<Session.Point>) {
        if (seeds.isEmpty()) return
        val keep = BooleanArray(mask.size)
        val stack = IntArray(mask.size); var top = 0
        for (s in seeds) {
            val i = s.y.roundToInt().coerceIn(0, h - 1) * w + s.x.roundToInt().coerceIn(0, w - 1)
            if (mask[i] && !keep[i]) { keep[i] = true; stack[top++] = i }
        }
        if (top == 0) return
        while (top > 0) {
            val i = stack[--top]; val x = i % w; val y = i / w
            if (x > 0 && mask[i - 1] && !keep[i - 1]) { keep[i - 1] = true; stack[top++] = i - 1 }
            if (x < w - 1 && mask[i + 1] && !keep[i + 1]) { keep[i + 1] = true; stack[top++] = i + 1 }
            if (y > 0 && mask[i - w] && !keep[i - w]) { keep[i - w] = true; stack[top++] = i - w }
            if (y < h - 1 && mask[i + w] && !keep[i + w]) { keep[i + w] = true; stack[top++] = i + w }
        }
        for (i in mask.indices) mask[i] = keep[i]
    }

    /** Flou de boîte séparable puis contraste sigmoïde : bord net mais sans escalier (même idée que ReFra). */
    private fun feather(mask: BooleanArray, w: Int, h: Int): ByteArray {
        val r = (max(w, h) * 0.003f).roundToInt().coerceIn(1, 5)
        val a = FloatArray(w * h) { if (mask[it]) 1f else 0f }
        val tmp = FloatArray(w * h)
        for (y in 0 until h) { var sum = 0f; for (x in -r..r) sum += a[y * w + x.coerceIn(0, w - 1)]
            for (x in 0 until w) { tmp[y * w + x] = sum / (2 * r + 1); sum += a[y * w + (x + r + 1).coerceAtMost(w - 1)] - a[y * w + (x - r).coerceAtLeast(0)] } }
        for (x in 0 until w) { var sum = 0f; for (y in -r..r) sum += tmp[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) { a[y * w + x] = sum / (2 * r + 1); sum += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x] } }
        val lut = ByteArray(256) { i -> (255f / (1f + exp(-12.0 * (i / 255.0 - 0.5)).toFloat())).roundToInt().coerceIn(0, 255).toByte() }
        return ByteArray(w * h) { lut[(a[it] * 255).roundToInt().coerceIn(0, 255)] }
    }
}
