package app.superhealth.engine.activities.dashboard.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.superhealth.engine.GBApplication
import app.superhealth.engine.util.Prefs
import app.superhealth.engine.widgets.DashboardQuery
import app.superhealth.engine.widgets.PlacedWidget
import app.superhealth.engine.widgets.WidgetConfig
import app.superhealth.engine.widgets.WidgetDataScope

/**
 * Loads the data of one configured widget instance.
 */
fun interface DashboardWidgetLoader {
    suspend fun load(placed: PlacedWidget, query: DashboardQuery, settings: DashboardSettings): Any?
}

/**
 * The [DashboardWidgetLoader] that reads the instance preferences and calls the widget's own loader.
 */
class DashboardWidgetLoaderImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DashboardWidgetLoader {
    override suspend fun load(
        placed: PlacedWidget,
        query: DashboardQuery,
        settings: DashboardSettings,
    ): Any? = withContext(ioDispatcher) {
        val instance = placed.instance
        val prefs = Prefs(GBApplication.getWidgetSharedPrefs(instance.instanceId))
        val config = WidgetConfig(
            instance,
            prefs,
            settings.showAllDevices,
            settings.deviceAddresses,
            placed.variant,
        )
        val devices = config.resolveDevices(placed.widget)
        val scope = WidgetDataScope(query, devices)
        placed.widget.loadData(scope, config)
    }
}
