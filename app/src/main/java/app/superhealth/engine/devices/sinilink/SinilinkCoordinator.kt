package app.superhealth.engine.devices.sinilink

import app.superhealth.engine.GBApplication
import app.superhealth.engine.R
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst
import app.superhealth.engine.activities.devicesettings.dsl.DeviceSettingsSpec
import app.superhealth.engine.activities.devicesettings.dsl.components.PasswordMode
import app.superhealth.engine.activities.devicesettings.dsl.components.deviceName
import app.superhealth.engine.activities.devicesettings.dsl.components.enumList
import app.superhealth.engine.activities.devicesettings.dsl.components.equalizerPreset
import app.superhealth.engine.activities.devicesettings.dsl.components.passwordScreen
import app.superhealth.engine.activities.devicesettings.dsl.components.volume
import app.superhealth.engine.activities.devicesettings.dsl.deviceSettings
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.devices.cards.QuickControl
import app.superhealth.engine.devices.cards.QuickControlButton
import app.superhealth.engine.devices.cards.QuickControlButtons
import app.superhealth.engine.devices.cards.QuickControls
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.devices.sinilink.SinilinkButton
import app.superhealth.engine.service.devices.sinilink.SinilinkEqualizer
import app.superhealth.engine.service.devices.sinilink.SinilinkMediaSource
import app.superhealth.engine.service.devices.sinilink.SinilinkPlaybackMode
import app.superhealth.engine.service.devices.sinilink.SinilinkPlaybackState
import app.superhealth.engine.service.devices.sinilink.SinilinkSupport
import java.util.regex.Pattern

class SinilinkCoordinator : AbstractBLEDeviceCoordinator() {
    override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("^Sinilink-APP$")
    }

    override fun getManufacturer(): String {
        return "Xinyi Electronics"
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport?> {
        return SinilinkSupport::class.java
    }

    override fun getBondingStyle(): Int {
        // Does not seem to be needed?
        return BONDING_STYLE_ASK
    }

    override fun suggestUnbindBeforePair(): Boolean {
        return false
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_sinilink
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_speaker
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.SPEAKER
    }

    override fun getBatteryCount(device: GBDevice): Int {
        return 0
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        enumList<SinilinkMediaSource>(
            key = DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE,
            title = R.string.media_source,
            icon = R.drawable.ic_music_note,
            defaultValue = SinilinkMediaSource.BLUETOOTH,
        )
        equalizerPreset<SinilinkEqualizer>(
            defaultValue = SinilinkEqualizer.NORMAL,
        )
        enumList<SinilinkPlaybackMode>(
            key = DeviceSettingsPreferenceConst.PREF_MEDIA_PLAYBACK_MODE,
            title = R.string.media_playback_mode,
            icon = R.drawable.ic_play,
            defaultValue = SinilinkPlaybackMode.LIST_CYCLE,
        )
        volume(max = 30, defaultValue = 15)
        switchSetting(
            key = DeviceSettingsPreferenceConst.PREF_PROMPT_TONE,
            title = R.string.sinilink_prompt_tone,
            icon = R.drawable.ic_notifications,
            defaultValue = true,
        )
        deviceName(maxLength = 10)
        passwordScreen(PasswordMode.VISIBLE_NUMBERS_4_DIGITS_0_TO_9)
    }

    override fun getQuickControls(device: GBDevice): List<QuickControl> {
        return listOf(MEDIA_CONTROLS) + QuickControls.fromDeviceSettings(
            device,
            keys = listOf(
                DeviceSettingsPreferenceConst.PREF_VOLUME,
                DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE,
            ),
            optionalKeys = listOf(
                DeviceSettingsPreferenceConst.PREF_HEADPHONES_EQUALIZER,
                DeviceSettingsPreferenceConst.PREF_MEDIA_PLAYBACK_MODE,
            ),
        )
    }

    companion object {
        private fun playPauseIcon(device: GBDevice): Int {
            val state = device.getExtraInfo("playback_state") as? String
            return if (state != null && SinilinkPlaybackState.fromPreference(state) == SinilinkPlaybackState.PLAYING) {
                R.drawable.ic_pause
            } else {
                R.drawable.ic_play
            }
        }

        private fun sendButton(device: GBDevice, button: SinilinkButton) {
            GBApplication.deviceService(device).onSendConfiguration(button.name)
        }

        private val MEDIA_CONTROLS = QuickControlButtons(
            id = "sinilink_media",
            icon = R.drawable.ic_play,
            title = R.string.prefs_media_controls,
            buttons = listOf(
                QuickControlButton(R.string.pref_media_previous, { R.drawable.ic_skip_previous }) { device, _ ->
                    sendButton(device, SinilinkButton.PREVIOUS)
                },
                QuickControlButton(R.string.moondrop_touch_action_play_pause, ::playPauseIcon) { device, _ ->
                    sendButton(device, SinilinkButton.PLAY_PAUSE)
                },
                QuickControlButton(R.string.pref_media_next, { R.drawable.ic_skip_next }) { device, _ ->
                    sendButton(device, SinilinkButton.NEXT)
                },
            ),
        )
    }
}
