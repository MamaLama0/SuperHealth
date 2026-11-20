package app.superhealth.engine.prefs.migrators;

import android.content.SharedPreferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.database.DBHandler;
import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.Device;
import app.superhealth.engine.prefs.AbstractPreferenceMigrator;

public class PreferenceMigrator60 extends AbstractPreferenceMigrator {
    private static final Logger LOG = LoggerFactory.getLogger(PreferenceMigrator60.class);

    @Override
    public void migrate(final int oldVersion, final SharedPreferences sharedPrefs, final SharedPreferences.Editor editor) {
        try (DBHandler db = GBApplication.acquireDB()) {
            final Set<String> deviceTypesToMigrate = new HashSet<>() {{
                add("GARMIN_EDGE_25");
                add("GARMIN_EDGE_130");
                add("GARMIN_EDGE_130_PLUS");
                add("GARMIN_EDGE_540");
                add("GARMIN_EDGE_840");
                add("GARMIN_EDGE_1040");
                add("GARMIN_EDGE_EXPLORE");
                add("GARMIN_EDGE_EXPLORE_2");
                add("GARMIN_GPSMAP_66S");
                add("GARMIN_GPSMAP_H1");
                add("GARMIN_ETREX_SE");
                add("GARMIN_INREACH_MINI_2");
            }};

            final DaoSession daoSession = db.getDaoSession();
            final List<Device> activeDevices = DBHelper.getActiveDevices(daoSession);

            for (final Device dbDevice : activeDevices) {
                final SharedPreferences deviceSharedPrefs = GBApplication.getDeviceSpecificSharedPrefs(dbDevice.getIdentifier());
                if (deviceTypesToMigrate.contains(dbDevice.getTypeName()) && !deviceSharedPrefs.contains("garmin_exploresync")) {
                    deviceSharedPrefs.edit()
                            .putBoolean("garmin_exploresync", true)
                            .apply();
                }
            }
        } catch (final Exception e) {
            LOG.error("Failed to migrate prefs to version 60", e);
        }
    }
}
