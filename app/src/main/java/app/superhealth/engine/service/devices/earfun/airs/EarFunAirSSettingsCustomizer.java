package app.superhealth.engine.service.devices.earfun.airs;

import static app.superhealth.engine.service.devices.earfun.prefs.EarFunSettingsPreferenceConst.PREF_EARFUN_EQUALIZER_PRESET;
import static app.superhealth.engine.service.devices.earfun.prefs.Equalizer.SixBandEqualizerPresets;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsHandler;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.devices.earfun.EarFunSettingsCustomizer;
import app.superhealth.engine.service.devices.earfun.prefs.Equalizer;
import app.superhealth.engine.util.Prefs;

public class EarFunAirSSettingsCustomizer extends EarFunSettingsCustomizer {
    private static final Logger LOG = LoggerFactory.getLogger(EarFunAirSSettingsCustomizer.class);

    @Override
    public void onPreferenceChange(Preference preference, DeviceSpecificSettingsHandler handler) {
        super.onPreferenceChange(preference, handler);
        String key = preference.getKey();
        if (key == null) {
            return;
        }
        switch (key) {
            case PREF_EARFUN_EQUALIZER_PRESET:
                onPreferenceChangeEqualizerPreset(handler,
                        Equalizer.SixBandEqualizer, SixBandEqualizerPresets);
                break;
        }
        // if the band sliders match a preset, update the preset list
        if (Equalizer.containsKey(Equalizer.SixBandEqualizer, key)) {
            int equalizerPreset = getSelectedPresetFromEqualizerBands(handler,
                    Equalizer.SixBandEqualizer, SixBandEqualizerPresets);
            ListPreference listPreferenceEqualizerPreset = handler.findPreference(PREF_EARFUN_EQUALIZER_PRESET);
            if (listPreferenceEqualizerPreset != null) {
                listPreferenceEqualizerPreset.setValue(Integer.toString(equalizerPreset));
            }
        }
    }

    @Override
    public void customizeSettings(DeviceSpecificSettingsHandler handler, Prefs prefs, String rootKey) {
        super.customizeSettings(handler, prefs, rootKey);
        initializeEqualizerPresetListPreference(handler, SixBandEqualizerPresets);
    }

    public EarFunAirSSettingsCustomizer(final GBDevice device) {
        super(device);
    }
}
