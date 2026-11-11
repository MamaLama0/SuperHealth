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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val HOST = "appassets.androidplatform.net"
private const val PREFIX = "/arsenal/"

private class EquipmentWebView(context: android.content.Context, private val ownsGestures: Boolean) : WebView(context) {
    private var ready = false
    private var script = ""

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

private class EquipmentBridge(private val view: WebView, private val dispatch: (String, String) -> Unit) {
    @JavascriptInterface
    fun action(kind: String, value: String) {
        view.post {
            if (kind == "ready") (view as EquipmentWebView).pageLoaded()
            else dispatch(kind, value)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun EquipmentWeb(
    page: String,
    script: String,
    modifier: Modifier,
    enabled: Boolean = true,
    ownsGestures: Boolean = false,
    action: ((String, String) -> Unit)? = null,
) {
    val callback = rememberUpdatedState(action)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            EquipmentWebView(context, ownsGestures = ownsGestures).apply {
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
                                return WebResourceResponse(mime, "UTF-8", 200, "OK",
                                    mapOf("Cache-Control" to "no-cache"), context.assets.open("arsenal/$path"))
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
                if (action != null) addJavascriptInterface(EquipmentBridge(this) { kind, value -> callback.value?.invoke(kind, value) }, "ArsenalHost")
                sync(script)
                loadUrl("https://$HOST$PREFIX$page")
            }
        },
        update = { view ->
            view.sync(script)
            if (enabled) view.onResume() else view.onPause()
        },
        onRelease = { view ->
            view.removeJavascriptInterface("ArsenalHost")
            view.stopLoading()
            view.destroy()
        },
    )
}
