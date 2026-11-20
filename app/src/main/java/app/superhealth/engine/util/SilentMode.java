/*  Copyright (C) 2023-2024 José Rebelo

    This file is part of SuperHealth.

    SuperHealth is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SuperHealth is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package app.superhealth.engine.util;

import static app.superhealth.engine.GBApplication.getContext;

import android.content.Context;
import android.media.AudioManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst;

public class SilentMode {
    private static final Logger LOG = LoggerFactory.getLogger(SilentMode.class);

    public enum RingerMode {
        NORMAL(AudioManager.RINGER_MODE_NORMAL),
        VIBRATE(AudioManager.RINGER_MODE_VIBRATE),
        SILENT(AudioManager.RINGER_MODE_SILENT),
        UNKNOWN(-1);

        private final int code;

        RingerMode(final int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static RingerMode fromCode(final int code) {
            for (final RingerMode ringerMode : values()) {
                if (ringerMode.code == code) {
                    return ringerMode;
                }
            }

            return RingerMode.UNKNOWN;
        }
    }

    public static void setPhoneSilentMode(final CharSequence deviceAddress, final boolean enabled) {
        final RingerMode[] phoneSilentMode = getPhoneSilentMode(deviceAddress);
        final RingerMode ringerMode = phoneSilentMode[enabled ? 1 : 0];

        LOG.debug("Set phone silent mode = {} ({})", enabled, ringerMode);

        setRingerMode(ringerMode);
    }

    public static boolean isPhoneInSilenceMode(final CharSequence deviceAddress) {
        final AudioManager audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        final RingerMode currentRingerMode = RingerMode.fromCode(audioManager.getRingerMode());
        final RingerMode[] phoneSilentMode = getPhoneSilentMode(deviceAddress);

        // Check if current mode "is more silent than" desired ringer mode
        return currentRingerMode.getCode() < phoneSilentMode[0].getCode();
    }

    public static RingerMode[] getPhoneSilentMode(final CharSequence deviceAddress) {
        final Prefs prefs = new Prefs(GBApplication.getDeviceSpecificSharedPrefs(deviceAddress));
        final String phoneSilentModePref = prefs.getString(DeviceSettingsPreferenceConst.PREF_PHONE_SILENT_MODE, "normal_silent").toUpperCase(Locale.ROOT);
        final String[] prefSplit = phoneSilentModePref.split("_");
        return new RingerMode[]{
                RingerMode.valueOf(prefSplit[0]),
                RingerMode.valueOf(prefSplit[1])
        };
    }

    public static void setRingerMode(final RingerMode mode) {
        if (mode == RingerMode.UNKNOWN) {
            LOG.warn("Unable to set unknown ringer mode");
            return;
        }

        final AudioManager audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setRingerMode(mode.getCode());
    }
}
