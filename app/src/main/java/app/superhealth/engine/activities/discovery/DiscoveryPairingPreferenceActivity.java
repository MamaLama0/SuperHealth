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
package app.superhealth.engine.activities.discovery;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.PreferenceFragmentCompat;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.R;
import app.superhealth.engine.activities.AbstractPreferenceFragment;
import app.superhealth.engine.activities.AbstractSettingsActivityV2;
import app.superhealth.engine.util.GB;

public class DiscoveryPairingPreferenceActivity extends AbstractSettingsActivityV2 {
    @Override
    protected PreferenceFragmentCompat newFragment() {
        return new DiscoveryPairingPreferenceFragment();
    }

    public static class DiscoveryPairingPreferenceFragment extends AbstractPreferenceFragment {
        @Override
        public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
            setPreferencesFromResource(R.xml.discovery_pairing_preferences, rootKey);

            findPreference("prefs_general_key_auto_reconnect_scan").setOnPreferenceChangeListener((preference, newValue) -> {
                GB.toast(GBApplication.getContext().getString(R.string.prompt_restart_app), Toast.LENGTH_LONG, GB.INFO);
                return true;
            });
        }
    }
}
