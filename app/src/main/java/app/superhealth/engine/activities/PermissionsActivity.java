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
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package app.superhealth.engine.activities;

import android.os.Bundle;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import app.superhealth.engine.R;
import app.superhealth.engine.activities.welcome.WelcomeFragmentPermissions;

public class PermissionsActivity extends AbstractGBActivity {
    public static final String ARG_SHOW_DO_NOT_ASK_BUTTON = "show_do_not_ask";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);

        final WelcomeFragmentPermissions permissionsFragment = new WelcomeFragmentPermissions();
        final Bundle args = new Bundle();
        args.putBoolean(
                WelcomeFragmentPermissions.ARG_SHOW_DO_NOT_ASK_BUTTON,
                getIntent().getBooleanExtra(ARG_SHOW_DO_NOT_ASK_BUTTON, false)
        );
        permissionsFragment.setArguments(args);

        final FragmentManager fragmentManager = getSupportFragmentManager();
        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, permissionsFragment).commit();
    }
}
