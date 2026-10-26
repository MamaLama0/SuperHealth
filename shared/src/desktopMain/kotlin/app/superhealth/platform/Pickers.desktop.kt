package app.superhealth.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberFilePicker(multiple: Boolean, onResult: (List<PickedFile>) -> Unit): () -> Unit = remember {
    {
        val fc = javax.swing.JFileChooser().apply { isMultiSelectionEnabled = multiple }
        if (fc.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION)
            onResult((if (multiple) fc.selectedFiles.toList() else listOf(fc.selectedFile)).map { PickedFile(it.name, it.readBytes()) })
        else onResult(emptyList())
    }
}

@Composable
actual fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit = remember {
    {
        val fc = javax.swing.JFileChooser()
        onResult(if (fc.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) fc.selectedFile.readBytes() else null)
    }
}

@Composable
actual fun rememberCamera(onResult: (ByteArray?) -> Unit): () -> Unit = remember { { onResult(null) } }

@Composable
actual fun rememberBarcodeScanner(onResult: (String?) -> Unit): () -> Unit = remember {
    { onResult(javax.swing.JOptionPane.showInputDialog("Code-barres")?.takeIf { it.isNotBlank() }) }
}

actual fun downscaleJpeg(bytes: ByteArray, maxSide: Int, quality: Int): ByteArray {
    val img = javax.imageio.ImageIO.read(bytes.inputStream()) ?: return bytes
    val scale = minOf(1.0, maxSide.toDouble() / maxOf(img.width, img.height))
    val w = (img.width * scale).toInt().coerceAtLeast(1); val h = (img.height * scale).toInt().coerceAtLeast(1)
    val out = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
    out.createGraphics().apply { drawImage(img.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), 0, 0, null); dispose() }
    return java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(out, "jpg", it) }.toByteArray()
}
