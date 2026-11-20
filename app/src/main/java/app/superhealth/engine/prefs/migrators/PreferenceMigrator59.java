package app.superhealth.engine.prefs.migrators;

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
import app.superhealth.engine.prefs.AbstractPreferenceMigrator;

public class PreferenceMigrator59 extends AbstractPreferenceMigrator {
    private static final Logger LOG = LoggerFactory.getLogger(PreferenceMigrator59.class);

    private static final String PREF_CHARTS_TABS = "charts_tabs";
    private static final String CHART_GENERIC_METRICS = "genericmetrics";

    @Override
    public void migrate(final int oldVersion, final SharedPreferences sharedPrefs, final SharedPreferences.Editor editor) {
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
                    newPrefValue = CHART_GENERIC_METRICS;
                } else if (chartsTabsValue.contains(CHART_GENERIC_METRICS)) {
                    newPrefValue = chartsTabsValue;
                } else {
                    newPrefValue = chartsTabsValue + "," + CHART_GENERIC_METRICS;
                }

                deviceSharedPrefs.edit()
                        .putString(PREF_CHARTS_TABS, newPrefValue)
                        .apply();
            }
        } catch (final Exception e) {
            LOG.error("Failed to migrate prefs to version 59", e);
        }
    }
}
