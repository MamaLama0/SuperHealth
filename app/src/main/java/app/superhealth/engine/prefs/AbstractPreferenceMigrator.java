package app.superhealth.engine.prefs;

import android.content.SharedPreferences;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.database.DBHandler;
import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.Device;

public abstract class AbstractPreferenceMigrator {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractPreferenceMigrator.class);

    protected static final String TAG = "GBPrefMigration";

    private static final String PREF_CHARTS_TABS = "charts_tabs";

    public abstract void migrate(int oldVersion, SharedPreferences sharedPrefs, SharedPreferences.Editor editor);

    /**
     * Adds the chart to the enabled charts of all devices.
     * [first] controls whether it is added at the start or at the end of the list.
     */
    protected void migrateChartsPreference(final int version, final String newChart, final boolean first) {
        try (DBHandler db = GBApplication.acquireDB()) {
            final DaoSession daoSession = db.getDaoSession();
            final List<Device> activeDevices = DBHelper.getActiveDevices(daoSession);

            for (final Device dbDevice : activeDevices) {
                final SharedPreferences deviceSharedPrefs = GBApplication.getDeviceSpecificSharedPrefs(dbDevice.getIdentifier());
                final String chartsTabsValue = deviceSharedPrefs.getString(PREF_CHARTS_TABS, null);
                if (chartsTabsValue == null) {
                    continue;
                }

                final String newPrefValue;
                if (StringUtils.isBlank(chartsTabsValue)) {
                    newPrefValue = newChart;
                } else if (chartsTabsValue.contains(newChart)) {
                    newPrefValue = chartsTabsValue;
                } else if (first) {
                    newPrefValue = newChart + "," + chartsTabsValue;
                } else {
                    newPrefValue = chartsTabsValue + "," + newChart;
                }

                deviceSharedPrefs.edit()
                        .putString(PREF_CHARTS_TABS, newPrefValue)
                        .apply();
            }
        } catch (final Exception e) {
            LOG.error("Failed to add new chart preference {} on version {}", newChart, version, e);
        }
    }
}
