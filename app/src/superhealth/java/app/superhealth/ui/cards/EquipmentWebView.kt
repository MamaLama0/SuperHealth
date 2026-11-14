package app.superhealth.ui.cards

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import app.superhealth.modules.Modules
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val HOST = "appassets.androidplatform.net"
private const val PREFIX = "/arsenal/"

internal class EquipmentWebView(context: android.content.Context, private val ownsGestures: Boolean) : WebView(context) {
    private var ready = false
    private var script = ""
    /** Destinataire courant des événements de la page : il change quand la vue gardée en réserve est réaffichée. */
    var dispatch: ((String, String) -> Unit)? = null
    var page = ""
    var version: String? = null

    /** La vue quitte l'écran mais reste en réserve : la page se met en pause, le prochain script sera rejoué. */
    fun park() {
        dispatch = null
        // Le sachet est remis à neuf et sa dernière image effacée : au retour, on ne revoit jamais l'ouverture précédente.
        if (ready) evaluateJavascript("if(window.arena){arena.clearIfOpened();arena.pause(true);document.querySelector('.pack-canvas').style.visibility='hidden';" +
            "var f=document.querySelector('.fx');f&&f.getContext('2d').clearRect(0,0,f.width,f.height);}", null)
        script = ""
        onPause()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (ownsGestures && event.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return super.onTouchEvent(event)
    }

    fun sync(next: String) {
        if (script == next) return
        script = next
        if (ready && next.isNotEmpty()) evaluateJavascript(next, null)
    }

    fun pageLoaded() {
        ready = true
        if (script.isNotEmpty()) evaluateJavascript(script, null)
    }
}

private class EquipmentBridge(private val view: EquipmentWebView) {
    @JavascriptInterface
    fun action(kind: String, value: String) {
        view.post {
            if (kind == "ready") view.pageLoaded()
            else {
                if (kind == "rendered") PackWeb.rendered.value = true
                view.dispatch?.invoke(kind, value)
            }
        }
    }
}

/**
 * Le sachet 3D gardé en réserve : la première page WebGL coûte près d'une seconde (moteur, shaders, modèles), alors
 * elle se prépare dès l'accueil des cartes et sert ensuite à chaque passage sur l'écran des paquets.
 */
internal object PackWeb {
    const val PAGE = "packs.html"
    private var view: EquipmentWebView? = null
    /** Vrai dès qu'un sachet a été dessiné une fois : avant, l'écran montre une silhouette d'attente. */
    val rendered = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun take(context: android.content.Context, version: String?): EquipmentWebView? {
        val kept = view ?: return null
        if (kept.context !== context || kept.version != version) { drop(); return null }
        (kept.parent as? android.view.ViewGroup)?.removeView(kept)
        return kept
    }
    fun keep(next: EquipmentWebView) { if (view !== next) { drop(); view = next } }
    private fun drop() {
        view?.let { (it.parent as? android.view.ViewGroup)?.removeView(it); it.removeJavascriptInterface("ArsenalHost"); it.stopLoading(); it.destroy() }
        view = null; rendered.value = false
    }
    /** Prépare la page hors écran, si elle ne l'est pas déjà. */
    fun warm(context: android.content.Context) {
        val version = Modules.installed.value.firstOrNull { it.kind == Modules.LEAGUE && it.enabled }?.version
        if (take(context, version) != null) return
        keep(buildWeb(context, PAGE, version, ownsGestures = true, bridge = true).also { it.onPause() })
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun buildWeb(context: android.content.Context, page: String, version: String?, ownsGestures: Boolean, bridge: Boolean) =
    EquipmentWebView(context, ownsGestures = ownsGestures).apply {
        this.page = page; this.version = version
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = WebView.OVER_SCROLL_NEVER
        // Removing a focused Android view inside the dashboard pager can start a reentrant Compose focus search.
        isFocusableInTouchMode = false
        isFocusable = false
        if (nodomain.freeyourgadget.gadgetbridge.BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val url = request.url
                val path = url.path.orEmpty().removePrefix(PREFIX)
                if (url.scheme == "https" && url.host == HOST && url.path.orEmpty().startsWith(PREFIX) &&
                    request.method == "GET" && path.matches(Regex("(?:bestiary/)?[a-z0-9][a-z0-9.-]*"))) {
                    val mime = when (path.substringAfterLast('.')) {
                        "html" -> "text/html"
                        "js" -> "text/javascript"
                        "css" -> "text/css"
                        "json" -> "application/json"
                        "mesh" -> "application/octet-stream"
                        "glb" -> "model/gltf-binary"
                        "webp" -> "image/webp"
                        "svg" -> "image/svg+xml"
                        else -> null
                    }
                    if (mime != null) try {
                        val art = when (mime) {
                            "image/webp", "model/gltf-binary", "application/octet-stream" -> Modules.arsenalAsset("arsenal/$path")
                            else -> null
                        }
                        val input = art?.inputStream() ?: context.assets.open("arsenal/$path")
                        return WebResourceResponse(mime, "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-cache"), input)
                    } catch (_: java.io.IOException) {
                        // Other requests are blocked below.
                    }
                }
                return WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url == "https://$HOST$PREFIX$page") (view as EquipmentWebView).pageLoaded()
            }
        }
        if (bridge) addJavascriptInterface(EquipmentBridge(this), "ArsenalHost")
        loadUrl("https://$HOST$PREFIX$page")
    }

/** [keepAlive] : la vue survit à l'écran (voir [PackWeb]) au lieu d'être détruite puis recréée à chaque passage. */
@Composable
internal fun EquipmentWeb(
    page: String,
    script: String,
    modifier: Modifier,
    enabled: Boolean = true,
    ownsGestures: Boolean = false,
    keepAlive: Boolean = false,
    action: ((String, String) -> Unit)? = null,
) {
    val callback = rememberUpdatedState(action)
    val group = Modules.installed.collectAsState().value.firstOrNull { it.kind == Modules.LEAGUE && it.enabled }
    key(group?.version) { AndroidView(
        modifier = modifier,
        factory = { context ->
            val view = (if (keepAlive) PackWeb.take(context, group?.version) else null)
                ?: buildWeb(context, page, group?.version, ownsGestures, bridge = action != null).also { if (keepAlive) PackWeb.keep(it) }
            view.dispatch = { kind, value -> callback.value?.invoke(kind, value) }
            view.sync(script)
            view
        },
        update = { view ->
            view.sync(script)
            if (enabled) view.onResume() else view.onPause()
        },
        onRelease = { view ->
            if (keepAlive) view.park()
            else {
                view.removeJavascriptInterface("ArsenalHost")
                view.stopLoading()
                view.destroy()
            }
        },
    ) }
}
