/*  Copyright (C) 2024 Arjan Schrijver

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
package app.superhealth.engine.activities.welcome;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.R;
import app.superhealth.engine.activities.DataManagementActivity;
import app.superhealth.engine.activities.discovery.DiscoveryActivityV2;
import app.superhealth.engine.util.Prefs;

public class WelcomeFragmentGetStarted extends Fragment {
    private static final Logger LOG = LoggerFactory.getLogger(WelcomeFragmentGetStarted.class);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.fragment_welcome_get_started, container, false);

        Button firstDevice = view.findViewById(R.id.welcome_button_add_device);
        firstDevice.setOnClickListener(firstDeviceButton -> startActivity(new Intent(requireActivity(), DiscoveryActivityV2.class)));
        Button restore = view.findViewById(R.id.welcome_button_restore);
        restore.setOnClickListener(restoreButton -> startActivity(new Intent(requireActivity(), DataManagementActivity.class)));
        Button toApp = view.findViewById(R.id.welcome_button_to_app);
        toApp.setOnClickListener(toAppButton -> {
            Prefs prefs = GBApplication.getPrefs();
            prefs.getPreferences().edit().putBoolean("first_run", false).apply();
            requireActivity().finish();
        });

        return view;
    }
}
