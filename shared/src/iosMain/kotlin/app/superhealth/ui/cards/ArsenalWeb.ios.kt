package app.superhealth.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import app.superhealth.modules.Modules
import app.superhealth.platform.readAsset
import app.superhealth.platform.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIColor
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKURLSchemeHandlerProtocol
import platform.WebKit.WKURLSchemeTaskProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

private const val SCHEME = "superhealth-arsenal"
private const val PREFIX = "/arsenal/"
private val SAFE = Regex("(?:bestiary/)?[a-z0-9][a-z0-9.-]*")


/** Sert les fichiers de l'Arsenal comme Android : modèles et images du plugin Groupe d'abord, pages et scripts de l'app. */
private class ArsenalFiles : NSObject(), WKURLSchemeHandlerProtocol {
    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, startURLSchemeTask: WKURLSchemeTaskProtocol) {
        val url = startURLSchemeTask.request.URL ?: return
        val path = url.path.orEmpty().removePrefix(PREFIX)
        val mime = when (path.substringAfterLast('.')) {
            "html" -> "text/html"; "js" -> "text/javascript"; "css" -> "text/css"; "json" -> "application/json"
            "mesh" -> "application/octet-stream"; "glb" -> "model/gltf-binary"; "webp" -> "image/webp"; "svg" -> "image/svg+xml"
            else -> null
        }
        val bytes = if (mime != null && url.path.orEmpty().startsWith(PREFIX) && path.matches(SAFE)) {
            val art = if (mime in setOf("image/webp", "model/gltf-binary", "application/octet-stream")) Modules.arsenalAsset("arsenal/$path") else null
            runCatching { art?.readBytes() }.getOrNull() ?: readAsset("arsenal/$path")
        } else null
        val response = NSHTTPURLResponse(uRL = url, statusCode = if (bytes != null) 200L else 404L, HTTPVersion = "HTTP/1.1",
            headerFields = mapOf<Any?, Any?>("Content-Type" to (mime ?: "text/plain"), "Cache-Control" to "no-cache"))
        startURLSchemeTask.didReceiveResponse(response)
        if (bytes != null) startURLSchemeTask.didReceiveData(bytes.toNSData())
        startURLSchemeTask.didFinish()
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, stopURLSchemeTask: WKURLSchemeTaskProtocol) {}
}

/** Messages `ArsenalHost.action(kind, value)` de la page. */
private class ArsenalMessages(val onMessage: (String, String) -> Unit) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
        val body = didReceiveScriptMessage.body as? List<*> ?: return
        onMessage(body.getOrNull(0)?.toString() ?: return, body.getOrNull(1)?.toString().orEmpty())
    }
}

/** Une page de l'Arsenal et son état : le script n'est envoyé qu'une fois la page prête. */
@OptIn(ExperimentalForeignApi::class)
private class ArsenalPage(page: String) {
    var dispatch: ((String, String) -> Unit)? = null
    private var ready = false
    private var script = ""
    private val messages = ArsenalMessages { kind, value ->
        if (kind == "ready") { ready = true; if (script.isNotEmpty()) run(script) }
        else {
            if (kind == "rendered") PackWeb.rendered.value = true
            dispatch?.invoke(kind, value)
        }
    }
    private val files = ArsenalFiles()
    val view: WKWebView = WKWebView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0), configuration = WKWebViewConfiguration().apply {
        setURLSchemeHandler(files, forURLScheme = SCHEME)
        userContentController.addScriptMessageHandler(messages, name = "arsenal")
        userContentController.addUserScript(WKUserScript(
            source = "window.ArsenalHost={action:function(k,v){window.webkit.messageHandlers.arsenal.postMessage([String(k),String(v)])}};",
            injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart, forMainFrameOnly = true))
    }).apply {
        opaque = false
        backgroundColor = UIColor.clearColor
        scrollView.backgroundColor = UIColor.clearColor
        scrollView.scrollEnabled = false
        scrollView.bounces = false
        scrollView.contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentNever
        NSURL.URLWithString("$SCHEME://app$PREFIX$page")?.let { loadRequest(NSURLRequest.requestWithURL(it)) }
    }

    private fun run(js: String) { view.evaluateJavaScript(js, completionHandler = null) }

    fun sync(next: String) {
        if (script == next) return
        script = next
        if (ready && next.isNotEmpty()) run(next)
    }

    fun pause(paused: Boolean) { if (ready) run("if(window.arena)arena.pause($paused);") }

    fun release() {
        dispatch = null
        view.configuration.userContentController.removeScriptMessageHandlerForName("arsenal")
        view.stopLoading()
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
@Composable
internal actual fun EquipmentWeb(page: String, script: String, modifier: Modifier, enabled: Boolean, ownsGestures: Boolean,
                                 keepAlive: Boolean, action: ((String, String) -> Unit)?) {
    val callback = rememberUpdatedState(action)
    val group = Modules.installed.collectAsState().value.firstOrNull { it.kind == Modules.LEAGUE && it.enabled }
    key(group?.version, page) {
        val web = remember { ArsenalPage(page) }
        web.dispatch = { kind, value -> callback.value?.invoke(kind, value) }
        LaunchedEffect(script) { web.sync(script) }
        LaunchedEffect(enabled) { web.pause(!enabled) }
        DisposableEffect(web) { onDispose { web.release() } }
        UIKitView(
            factory = { web.view },
            modifier = modifier,
            update = { web.sync(script) },
            properties = UIKitInteropProperties(
                interactionMode = if (ownsGestures) UIKitInteropInteractionMode.NonCooperative else UIKitInteropInteractionMode.Cooperative(),
                isNativeAccessibilityEnabled = false,
            ),
        )
    }
}
