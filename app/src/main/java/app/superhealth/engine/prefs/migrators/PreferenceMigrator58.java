package app.superhealth.engine.prefs.migrators;

import android.content.SharedPreferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.superhealth.engine.prefs.AbstractPreferenceMigrator;

public class PreferenceMigrator58 extends AbstractPreferenceMigrator {
    private static final Logger LOG = LoggerFactory.getLogger(PreferenceMigrator58.class);

    @Override
    public void migrate(final int oldVersion, final SharedPreferences sharedPrefs, final SharedPreferences.Editor editor) {
        try {
            editor.putString("chart_sleep_range_mode", sharedPrefs.getBoolean("chart_sleep_range_24h", false) ? "24h" : "18:00");
        } catch (final Exception e) {
            LOG.error("Failed to migrate prefs to version 58", e);
        }
    }
}
