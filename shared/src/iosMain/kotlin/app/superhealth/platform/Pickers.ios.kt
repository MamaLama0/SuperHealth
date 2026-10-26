package app.superhealth.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeEAN8Code
import platform.AVFoundation.AVMetadataObjectTypeUPCECode
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.UIModalPresentationFullScreen
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/* Sélecteurs iOS appelés depuis Compose : chaque délégué est gardé en vie tant que son écran est affiché. */

private val alive = mutableSetOf<Any>()

private fun main(block: () -> Unit) = dispatch_async(dispatch_get_main_queue()) { block() }

private class DocDelegate(val onResult: (List<PickedFile>) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val files = didPickDocumentsAtURLs.mapNotNull { u ->
            val url = u as? NSURL ?: return@mapNotNull null
            val access = url.startAccessingSecurityScopedResource()
            val data = NSData.dataWithContentsOfURL(url)
            if (access) url.stopAccessingSecurityScopedResource()
            data?.let { PickedFile(url.lastPathComponent ?: "fichier", it.toByteArray()) }
        }
        alive.remove(this); onResult(files)
    }
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { alive.remove(this); onResult(emptyList()) }
}

@Composable
actual fun rememberFilePicker(multiple: Boolean, onResult: (List<PickedFile>) -> Unit): () -> Unit = remember(multiple) {
    {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = true)
        picker.allowsMultipleSelection = multiple
        val d = DocDelegate(onResult); alive += d
        picker.delegate = d
        topController()?.presentViewController(picker, true, null)
    }
}

private class PhotoDelegate(val onResult: (ByteArray?) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)
        val r = didFinishPicking.firstOrNull() as? PHPickerResult
        if (r == null) { alive.remove(this); onResult(null); return }
        r.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            // HEIC ou PNG converti en JPEG (format attendu par l'IA et les photos de progression)
            val jpeg = data?.let { UIImage(data = it) }?.let { UIImageJPEGRepresentation(it, 0.9)?.toByteArray() }
            main { alive.remove(this); onResult(jpeg) }
        }
    }
}

@Composable
actual fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit = remember {
    {
        val cfg = PHPickerConfiguration().apply { filter = PHPickerFilter.imagesFilter; selectionLimit = 1 }
        val picker = PHPickerViewController(cfg)
        val d = PhotoDelegate(onResult); alive += d
        picker.delegate = d
        topController()?.presentViewController(picker, true, null)
    }
}

private class CameraDelegate(val onResult: (ByteArray?) -> Unit) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val img = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, null)
        alive.remove(this); onResult(img?.let { UIImageJPEGRepresentation(it, 0.9)?.toByteArray() })
    }
    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null); alive.remove(this); onResult(null)
    }
}

@Composable
actual fun rememberCamera(onResult: (ByteArray?) -> Unit): () -> Unit = remember {
    {
        val type = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        if (!UIImagePickerController.isSourceTypeAvailable(type)) onResult(null)
        else {
            val picker = UIImagePickerController().apply { sourceType = type }
            val d = CameraDelegate(onResult); alive += d
            picker.delegate = d
            topController()?.presentViewController(picker, true, null)
        }
    }
}

/** Lecteur de codes-barres alimentaires (EAN-13, EAN-8, UPC-E) sur la caméra arrière. */
private class ScannerController(val onResult: (String?) -> Unit) : UIViewController(nibName = null, bundle = null),
    AVCaptureMetadataOutputObjectsDelegateProtocol {
    private val session = AVCaptureSession()
    private var done = false

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.blackColor
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) as? AVCaptureDeviceInput }
        if (input == null || !session.canAddInput(input)) { finish(null); return }
        session.addInput(input)
        val output = AVCaptureMetadataOutput()
        session.addOutput(output)
        output.setMetadataObjectsDelegate(this, dispatch_get_main_queue())
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeEAN13Code, AVMetadataObjectTypeEAN8Code, AVMetadataObjectTypeUPCECode)
        val layer = AVCaptureVideoPreviewLayer(session = session)
        layer.videoGravity = AVLayerVideoGravityResizeAspectFill
        layer.frame = view.bounds
        view.layer.addSublayer(layer)
        val close = UIButton.buttonWithType(UIButtonTypeSystem)
        close.setTitle(app.superhealth.ui.tr("Annuler"), UIControlStateNormal)
        close.setTitleColor(UIColor.whiteColor, UIControlStateNormal)
        view.bounds.useContents { close.setFrame(CGRectMake(20.0, size.height - 100.0, size.width - 40.0, 50.0)) }
        close.addTarget(this, platform.objc.sel_registerName("cancel"), UIControlEventTouchUpInside)
        view.addSubview(close)
        platform.darwin.dispatch_async(platform.darwin.dispatch_get_global_queue(0, 0u)) { session.startRunning() }
    }

    @ObjCAction fun cancel() = finish(null)

    override fun captureOutput(output: AVCaptureOutput, didOutputMetadataObjects: List<*>, fromConnection: AVCaptureConnection) {
        val code = didOutputMetadataObjects.firstNotNullOfOrNull { (it as? AVMetadataMachineReadableCodeObject)?.stringValue } ?: return
        haptic(strong = true)
        finish(code)
    }

    private fun finish(code: String?) {
        if (done) return
        done = true
        session.stopRunning()
        dismissViewControllerAnimated(true) { onResult(code) }
    }
}

@Composable
actual fun rememberBarcodeScanner(onResult: (String?) -> Unit): () -> Unit = remember {
    {
        val vc = ScannerController(onResult)
        vc.modalPresentationStyle = UIModalPresentationFullScreen
        topController()?.presentViewController(vc, true, null)
    }
}

actual fun downscaleJpeg(bytes: ByteArray, maxSide: Int, quality: Int): ByteArray {
    val img = UIImage(data = bytes.toNSData())
    val (w, h) = img.size.useContents { width to height }
    if (w <= 0 || h <= 0) return bytes
    val scale = minOf(1.0, maxSide / maxOf(w, h))
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(w * scale, h * scale), true, 1.0)
    img.drawInRect(CGRectMake(0.0, 0.0, w * scale, h * scale))
    val out = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return out?.let { UIImageJPEGRepresentation(it, quality / 100.0)?.toByteArray() } ?: bytes
}
