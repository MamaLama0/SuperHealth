package app.superhealth.engine.prefs.migrators;

import android.content.SharedPreferences;

import app.superhealth.engine.prefs.AbstractPreferenceMigrator;

public class PreferenceMigrator62 extends AbstractPreferenceMigrator {
    @Override
    public void migrate(final int oldVersion, final SharedPreferences sharedPrefs, final SharedPreferences.Editor editor) {
        migrateChartsPreference(62, "solarcharging", false);
    }
}
