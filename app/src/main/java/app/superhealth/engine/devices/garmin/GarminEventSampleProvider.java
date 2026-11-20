/*  Copyright (C) 2024 José Rebelo

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
package app.superhealth.engine.devices.garmin;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import de.greenrobot.dao.query.QueryBuilder;
import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.devices.AbstractTimeSampleProvider;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.Device;
import app.superhealth.engine.entities.GarminEventSample;
import app.superhealth.engine.entities.GarminEventSampleDao;
import app.superhealth.engine.impl.GBDevice;

public class GarminEventSampleProvider extends AbstractTimeSampleProvider<GarminEventSample> {
    public GarminEventSampleProvider(final GBDevice device, final DaoSession session) {
        super(device, session);
    }

    @NonNull
    @Override
    public AbstractDao<GarminEventSample, ?> getSampleDao() {
        return getSession().getGarminEventSampleDao();
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return GarminEventSampleDao.Properties.Timestamp;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return GarminEventSampleDao.Properties.DeviceId;
    }

    @Override
    public GarminEventSample createSample() {
        return new GarminEventSample();
    }

    public List<GarminEventSample> getSleepEvents(final long timestampFrom, final long timestampTo) {
        final QueryBuilder<GarminEventSample> qb = getSampleDao().queryBuilder();
        final Property timestampProperty = getTimestampSampleProperty();
        final Device dbDevice = DBHelper.findDevice(getDevice(), getSession());
        if (dbDevice == null) {
            // no device, no samples
            return Collections.emptyList();
        }
        final Property deviceProperty = getDeviceIdentifierSampleProperty();
        qb.where(deviceProperty.eq(dbDevice.getId()), timestampProperty.ge(timestampFrom))
                .where(timestampProperty.le(timestampTo))
                .where(GarminEventSampleDao.Properties.Event.eq(74))
                .orderAsc(timestampProperty);

        final List<GarminEventSample> samples = qb.build().list();
        detachFromSession();
        return samples;
    }

    /**
     * Delete the sleep events FitImporter faked for a raw sleep file before it could estimate the
     * night, marked by a data value of -1 (#4048).
     */
    public void deleteFakedSleepEvents(final long timestampFrom, final long timestampTo) {
        final Device dbDevice = DBHelper.findDevice(getDevice(), getSession());
        if (dbDevice == null) {
            return;
        }
        getSampleDao().queryBuilder()
                .where(
                        getDeviceIdentifierSampleProperty().eq(dbDevice.getId()),
                        getTimestampSampleProperty().between(timestampFrom, timestampTo),
                        GarminEventSampleDao.Properties.Event.eq(74),
                        GarminEventSampleDao.Properties.Data.eq(-1L)
                )
                .buildDelete()
                .executeDeleteWithoutDetachingEntities();
    }

    public GarminEventSample getNextSleepEventAfter(final long timestampFrom) {
        final Device dbDevice = DBHelper.findDevice(getDevice(), getSession());
        if (dbDevice == null) {
            // no device, no sample
            return null;
        }

        final Property deviceIdSampleProp = getDeviceIdentifierSampleProperty();
        final Property timestampSampleProp = getTimestampSampleProperty();
        final List<GarminEventSample> samples = getSampleDao().queryBuilder()
                .where(
                        deviceIdSampleProp.eq(dbDevice.getId()),
                        timestampSampleProp.ge(timestampFrom),
                        GarminEventSampleDao.Properties.Event.eq(74)
                ).orderAsc(getTimestampSampleProperty())
                .limit(1)
                .list();

        return !samples.isEmpty() ? samples.get(0) : null;
    }
}
