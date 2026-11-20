package app.superhealth.engine.prefs.migrators;

import android.content.SharedPreferences;

import app.superhealth.engine.prefs.AbstractPreferenceMigrator;
import app.superhealth.engine.widgets.DashboardMigration;

public class PreferenceMigrator68 extends AbstractPreferenceMigrator {
    @Override
    public void migrate(final int oldVersion, final SharedPreferences sharedPrefs, final SharedPreferences.Editor editor) {
        // Version 0 is a fresh install, which has no dashboard to migrate
        if (oldVersion > 0) {
            editor.putBoolean(DashboardMigration.PREF_UPGRADE, true);
            // Enable the device cards for all users, even those that disabled them
            editor.putBoolean("dashboard_cards_enabled", true);
        }
    }
}
