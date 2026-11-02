package app.superhealth.debug

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import android.view.Gravity
import android.widget.FrameLayout
import app.superhealth.ui.widget.MindWidgetReceiver
import kotlin.math.roundToInt

/** Hôte Android isolé pour vérifier le vrai RemoteViews, ses actions et son redimensionnement en QA. */
class MindWidgetPreviewActivity : Activity() {
    private val host by lazy { AppWidgetHost(this, 3001) }
    private val manager by lazy { AppWidgetManager.getInstance(this) }
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val dimensions get() = when (intent.getStringExtra("size")) {
        "S" -> SizeF(57f, 57f)
        "M" -> SizeF(130f, 130f)
        "W" -> SizeF(270f, 57f)
        else -> SizeF(270f, 130f)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (packageName != "app.superhealth.qa") { finish(); return }
        widgetId = savedInstanceState?.getInt("widgetId") ?: run {
            host.deleteHost()
            host.allocateAppWidgetId()
        }
        if (manager.getAppWidgetInfo(widgetId) != null) { showWidget(); return }
        val provider = ComponentName(this, MindWidgetReceiver::class.java)
        if (manager.bindAppWidgetIdIfAllowed(widgetId, provider)) showWidget()
        else startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider), 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && manager.getAppWidgetInfo(widgetId) != null) showWidget() else finish()
    }

    private fun showWidget() {
        val size = dimensions
        val view = host.createView(this, widgetId, manager.getAppWidgetInfo(widgetId))
        view.setPadding(0, 0, 0, 0)
        val w = size.width.toInt()
        val h = size.height.toInt()
        if (android.os.Build.VERSION.SDK_INT >= 31) view.updateAppWidgetSize(Bundle(), listOf(size))
        else view.updateAppWidgetSize(Bundle(), w, h, w, h)
        val scale = resources.displayMetrics.density
        setContentView(FrameLayout(this).apply { addView(view, FrameLayout.LayoutParams(
            (w * scale).roundToInt(), (h * scale).roundToInt(), Gravity.CENTER)) })
        host.startListening()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("widgetId", widgetId)
    }

    override fun onDestroy() {
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            host.stopListening()
            if (isFinishing) host.deleteAppWidgetId(widgetId)
        }
        super.onDestroy()
    }
}
