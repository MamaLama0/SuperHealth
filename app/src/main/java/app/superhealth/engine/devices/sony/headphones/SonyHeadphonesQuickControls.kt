package app.superhealth.engine.devices.sony.headphones

import app.superhealth.engine.GBApplication
import app.superhealth.engine.R
import app.superhealth.engine.activities.devicesettings.DeviceSettingsActivity
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst
import app.superhealth.engine.devices.cards.QuickControlDialog
import app.superhealth.engine.devices.cards.QuickControl
import app.superhealth.engine.devices.cards.QuickControlOption
import app.superhealth.engine.devices.cards.QuickControlTrailingAction
import app.superhealth.engine.devices.cards.QuickControlToggleGroup
import app.superhealth.engine.devices.cards.QuickControlSlider
import app.superhealth.engine.devices.sony.headphones.prefs.AmbientSoundControl
import app.superhealth.engine.devices.sony.headphones.prefs.EqualizerPreset

fun sonyHeadphonesQuickControls(capabilities: Set<SonyHeadphonesCapabilities>): List<QuickControl> {
    val controls = mutableListOf<QuickControl>()

    if (capabilities.contains(SonyHeadphonesCapabilities.AmbientSoundControl) ||
        capabilities.contains(SonyHeadphonesCapabilities.AmbientSoundControl2)
    ) {
        controls.add(
            QuickControlToggleGroup.forPreference(
                key = DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_CONTROL,
                title = R.string.prefs_noise_control,
                icon = R.drawable.ic_hearing,
                defaultValue = sonyDefaultAmbientSoundMode(capabilities).name.lowercase(),
                options = sonyAmbientSoundModes(capabilities).map { mode ->
                    val (label, icon) = when (mode) {
                        AmbientSoundControl.Mode.OFF ->
                            R.string.sony_ambient_sound_off to R.drawable.ic_hearing_disabled

                        AmbientSoundControl.Mode.NOISE_CANCELLING ->
                            R.string.sony_ambient_sound_noise_cancelling_short to R.drawable.ic_noise_control_on

                        AmbientSoundControl.Mode.WIND_NOISE_REDUCTION ->
                            R.string.sony_ambient_sound_wind_noise_reduction_short to R.drawable.ic_air

                        AmbientSoundControl.Mode.AMBIENT_SOUND ->
                            R.string.sony_ambient_sound_ambient_sound_short to R.drawable.ic_hearing
                    }
                    QuickControlOption(mode.name.lowercase(), label, icon)
                },
            )
        )
        controls.add(
            QuickControlSlider.forPreference(
                key = DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_LEVEL,
                icon = R.drawable.ic_volume_up,
                title = R.string.sony_ambient_sound_level,
                min = 0,
                max = 19,
                defaultValue = 0,
                visible = { device -> sonyIsAmbientSoundMode(capabilities, GBApplication.getDevicePrefs(device)) },
            )
        )
    }

    if (capabilities.contains(SonyHeadphonesCapabilities.EqualizerSimple) ||
        capabilities.contains(SonyHeadphonesCapabilities.EqualizerWithCustomBands)
    ) {
        val customPresets = setOf(EqualizerPreset.MANUAL, EqualizerPreset.CUSTOM_1, EqualizerPreset.CUSTOM_2)
            .map { it.name.lowercase() }
        controls.add(
            QuickControlDialog.forPreference(
                key = DeviceSettingsPreferenceConst.PREF_SONY_EQUALIZER_MODE,
                icon = R.drawable.ic_graphic_eq,
                title = R.string.sony_equalizer,
                defaultValue = EqualizerPreset.OFF.name.lowercase(),
                options = EqualizerPreset.entries.map { QuickControlOption(it.name.lowercase(), it.label) },
                visible = { device -> sonyAudioSettingsVisible(capabilities, GBApplication.getDevicePrefs(device)) },
                trailingAction = if (capabilities.contains(SonyHeadphonesCapabilities.EqualizerWithCustomBands)) {
                    QuickControlTrailingAction(
                        icon = R.drawable.ic_settings,
                        description = R.string.pref_header_equalizer,
                        visible = { _, value -> value in customPresets },
                        onClick = { device, context ->
                            DeviceSettingsActivity.start(context, device)
                        },
                    )
                } else {
                    null
                },
            )
        )
    }

    return controls
}
