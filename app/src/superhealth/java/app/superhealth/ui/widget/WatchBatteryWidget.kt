package app.superhealth.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.superhealth.bridge.WatchStatus
import app.superhealth.ui.dashboard.batteryColor
import app.superhealth.ui.tr
import app.superhealth.engine.R

/**
 * Montre : batterie en jauge (couleur selon le niveau, éclair en charge), nom, connexion, et dernière connexion montre
 * déconnectée. Petit : la jauge seule. Redessiné par WatchStatus quand le moteur montre signale un changement.
 */
class WatchBatteryWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WK.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val w = WatchStatus.primary()
        val col = batteryColor(w?.battery, w?.connected == true)
        val g = gauge(w?.battery?.div(100f), col, WK.px(context, 88.dp))
        val name = w?.name?.takeIf { it.isNotBlank() } ?: tr("Montre")
        val state = w?.let { WatchStatus.stateText(it) } ?: tr("Aucune montre")
        val seen = w?.let { WatchStatus.lastSeenText(it) }
        val charging = w?.charging == true
        provideContent {
            val size = WK.size(LocalSize.current)
            val v = w?.battery?.toString() ?: "—"
            WidgetCard(WK.open(context, if (w != null) "h/devices" else "home"), null, padding = if (size == WK.Size.SMALL) 6.dp else 12.dp,
                horizontal = Alignment.CenterHorizontally) {
                if (size == WK.Size.SMALL) {
                    GaugeBox(g, v, if (charging) "⚡" else "%", col, 48.dp)
                    return@WidgetCard
                }
                val gs = when (size) { WK.Size.STRIP -> 44.dp; WK.Size.LARGE -> 88.dp; else -> 56.dp }
                val text: @Composable () -> Unit = {
                    Text(name, style = TextStyle(color = WK.text, fontSize = if (size == WK.Size.LARGE) 15.sp else 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (charging) {
                            Image(ImageProvider(R.drawable.ic_w_bolt), null, GlanceModifier.size(12.dp), colorFilter = ColorFilter.tint(WK.on(col)))
                            Spacer(GlanceModifier.width(2.dp))
                        }
                        Text(if (size != WK.Size.LARGE && seen != null) seen else state, style = TextStyle(color = if (w?.connected == true) WK.on(col) else WK.sub, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                    if (seen != null && size == WK.Size.LARGE) Text(seen, style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
                }
                if (size == WK.Size.MEDIUM) Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                    GaugeBox(g, v, "%", col, gs)
                    Spacer(GlanceModifier.height(4.dp))
                    text()
                } else Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    GaugeBox(g, v, if (size == WK.Size.LARGE) "%" else null, col, gs)
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight()) { text() }
                }
            }
        }
    }
}

class WatchBatteryWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = WatchBatteryWidget() }

object WatchBatteryWidgets {
    /** Par identifiants système, comme les autres widgets ; rien à faire sans widget posé. */
    suspend fun refresh(ctx: Context) {
        val ids = android.appwidget.AppWidgetManager.getInstance(ctx).getAppWidgetIds(android.content.ComponentName(ctx, WatchBatteryWidgetReceiver::class.java))
        if (ids.isEmpty()) return
        val glance = androidx.glance.appwidget.GlanceAppWidgetManager(ctx)
        ids.forEach { id -> runCatching { WatchBatteryWidget().update(ctx, glance.getGlanceIdBy(id)) } }
    }
}
