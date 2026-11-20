package app.superhealth.engine.devices.onemoresonoflow

import app.superhealth.engine.R
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsScreen
import app.superhealth.engine.activities.devicesettings.dsl.DeviceSettingsSpec
import app.superhealth.engine.activities.devicesettings.dsl.components.enumList
import app.superhealth.engine.activities.devicesettings.dsl.deviceSettings
import app.superhealth.engine.devices.AbstractBLClassicDeviceCoordinator
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.model.BatteryConfig
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.devices.onemore_sonoflow.OneMoreNoiseControlMode
import app.superhealth.engine.service.devices.onemore_sonoflow.OneMoreSonoFlowSupport
import java.util.regex.Pattern

open class OneMoreSonoFlowCoordinator : AbstractBLClassicDeviceCoordinator() {
    protected override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("1MORE SonoFlow", Pattern.LITERAL)
    }

    open fun supportsLdac(device: GBDevice): Boolean = true

    override fun getManufacturer(): String {
        return "1MORE"
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return OneMoreSonoFlowSupport::class.java
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_onemore_sonoflow
    }

    override fun getBatteryConfig(device: GBDevice): Array<BatteryConfig> {
        return arrayOf(
            BatteryConfig(
                0,
                GBDevice.BATTERY_ICON_DEFAULT.toInt(),
                GBDevice.BATTERY_LABEL_DEFAULT.toInt(),
                20,
                100
            )
        )
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        screen(
            key = DeviceSpecificSettingsScreen.SOUND.key,
            title = R.string.pref_header_sound,
            icon = R.drawable.ic_volume_up,
        ) {
            enumList<OneMoreNoiseControlMode>(
                key = DeviceSettingsPreferenceConst.PREF_NOISE_CONTROL_SELECTOR,
                title = R.string.prefs_noise_control,
                icon = R.drawable.ic_surround,
                defaultValue = OneMoreNoiseControlMode.OFF,
            )
            if (supportsLdac(device)) {
                switchSetting(
                    key = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LDAC_MODE,
                    title = R.string.soundcore_ldac_mode_title,
                    summary = R.string.soundcore_ldac_mode_summary,
                    icon = R.drawable.ic_music_note,
                    defaultValue = false,
                )
            }
        }
        screen(
            key = DeviceSpecificSettingsScreen.CONNECTION.key,
            title = R.string.pref_header_connection,
            icon = R.drawable.ic_mtu,
        ) {
            switchSetting(
                key = DeviceSettingsPreferenceConst.PREF_DUAL_DEVICE_SUPPORT,
                title = R.string.dual_device_mode_title,
                summary = R.string.dual_device_mode_summary,
                icon = R.drawable.ic_devices_other,
                defaultValue = false,
                connectedOnly = false,
            )
        }
        xmlScreen(
            DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS,
            R.xml.devicesettings_headphones,
        )
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_headphones
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.HEADPHONES
    }

    override fun supportsAppNotifications(device: GBDevice): Boolean = true

    override fun supportsPhoneCalls(device: GBDevice): Boolean = true

    override fun supportsCallControl(device: GBDevice): Boolean = true
}
