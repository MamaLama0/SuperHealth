package app.superhealth.engine.prefs.migrators;

import android.content.SharedPreferences;

import app.superhealth.engine.externalevents.notifications.apps.NotificationAppMigration;
import app.superhealth.engine.prefs.AbstractPreferenceMigrator;

public class PreferenceMigrator70 extends AbstractPreferenceMigrator {
    @Override
    public void migrate(final int oldVersion, final SharedPreferences sharedPrefs, final SharedPreferences.Editor editor) {
        NotificationAppMigration.INSTANCE.migrate(sharedPrefs, editor);
    }
}
