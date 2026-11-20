package app.superhealth.engine.prefs.migrators;

import android.content.SharedPreferences;

import app.superhealth.engine.prefs.AbstractPreferenceMigrator;

public class PreferenceMigrator61 extends AbstractPreferenceMigrator {
    @Override
    public void migrate(final int oldVersion, final SharedPreferences sharedPrefs, final SharedPreferences.Editor editor) {
        migrateChartsPreference(61, "raceprediction", false);
    }
}
