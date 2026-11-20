package app.superhealth.engine.widgets

import app.superhealth.engine.GBApplication
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.widgets.impl.ActiveCaloriesWidget
import app.superhealth.engine.widgets.impl.ActiveTimeWidget
import app.superhealth.engine.widgets.impl.BloodPressureWidget
import app.superhealth.engine.widgets.impl.BodyEnergyWidget
import app.superhealth.engine.widgets.impl.CaloriesWidget
import app.superhealth.engine.widgets.impl.DistanceWidget
import app.superhealth.engine.widgets.impl.GoalsWidget
import app.superhealth.engine.widgets.impl.HeartRateWidget
import app.superhealth.engine.widgets.impl.HrvWidget
import app.superhealth.engine.widgets.impl.HydrationWidget
import app.superhealth.engine.widgets.impl.LastActivityWidget
import app.superhealth.engine.widgets.impl.PaiWidget
import app.superhealth.engine.widgets.impl.RespiratoryRateWidget
import app.superhealth.engine.widgets.impl.SleepScoreWidget
import app.superhealth.engine.widgets.impl.SleepWidget
import app.superhealth.engine.widgets.impl.Spo2Widget
import app.superhealth.engine.widgets.impl.StepsWidget
import app.superhealth.engine.widgets.impl.StressWidget
import app.superhealth.engine.widgets.impl.TodayWidget
import app.superhealth.engine.widgets.impl.TrainingLoadWidget
import app.superhealth.engine.widgets.impl.TrainingReadinessWidget
import app.superhealth.engine.widgets.impl.Vo2MaxWidget
import app.superhealth.engine.widgets.impl.BmiWidget
import app.superhealth.engine.widgets.impl.BasalMetabolicRateWidget
import app.superhealth.engine.widgets.impl.BodyFatWidget
import app.superhealth.engine.widgets.impl.BodyWaterWidget
import app.superhealth.engine.widgets.impl.MuscleMassWidget
import app.superhealth.engine.widgets.impl.WeightWidget

/**
 * Resolves [GBWidget] implementations by their IDs ([GBWidget.id]) - the common ones
 * plus the ones that coordinators of currently paired devices declare via
 * [app.superhealth.engine.devices.DeviceCoordinator.getWidgetsProvider].
 */
object WidgetRegistry {
    private val common: List<GBWidget<*>> = listOf(
        ActiveCaloriesWidget,
        ActiveTimeWidget,
        BasalMetabolicRateWidget,
        BloodPressureWidget,
        BmiWidget,
        BodyEnergyWidget,
        BodyFatWidget,
        BodyWaterWidget,
        CaloriesWidget,
        DistanceWidget,
        GoalsWidget,
        HeartRateWidget,
        HrvWidget,
        HydrationWidget,
        LastActivityWidget,
        MuscleMassWidget,
        PaiWidget,
        RespiratoryRateWidget,
        SleepScoreWidget,
        SleepWidget,
        Spo2Widget,
        StepsWidget,
        StressWidget,
        TodayWidget,
        TrainingLoadWidget,
        TrainingReadinessWidget,
        Vo2MaxWidget,
        WeightWidget,
    )

    /**
     * All known widget kinds: common + device-specific.
     */
    fun all(): List<GBWidget<*>> {
        val deviceSpecificWidgets = GBApplication.app().deviceManager.devices
            .map { it.deviceCoordinator }
            .distinct()
            .flatMap { it.widgetsProvider.getWidgets() }

        val byId = LinkedHashMap<String, GBWidget<*>>()
        for (widget in common) byId.putIfAbsent(widget.id, widget)
        for (widget in deviceSpecificWidgets) byId.putIfAbsent(widget.id, widget)
        return byId.values.toList()
    }

    fun byId(id: String): GBWidget<*>? = all().find { it.id == id }

    /**
     * The devices that are enabled for the dashboard.
     */
    fun dashboardDevices(): List<GBDevice> {
        val prefs = GBApplication.getPrefs()
        val showAllDevices = prefs.getBoolean("dashboard_devices_all", true)
        val selectedDevices = prefs.getStringSet("dashboard_devices_multiselect", emptySet())
        return GBApplication.app().deviceManager.devices
            .filter { showAllDevices || it.address in selectedDevices }
    }

    /**
     * Widget kinds supported by at least one device that is enabled for the dashboard.
     */
    fun available(): List<GBWidget<*>> {
        val devices = dashboardDevices()
        return all().filter { widget -> devices.any { widget.isSupportedBy(it) } }
    }

    /**
     * Whether at least one widget supports the device.
     */
    @JvmStatic
    fun supportsAnyWidget(device: GBDevice): Boolean = all().any { it.isSupportedBy(device) }
}
