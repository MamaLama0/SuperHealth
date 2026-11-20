/*  Copyright (C) 2026 Dany Mestas

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
package app.superhealth.engine.devices;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.Device;
import app.superhealth.engine.entities.GenericDurationSample;
import app.superhealth.engine.entities.GenericDurationSampleDao;
import app.superhealth.engine.impl.GBDevice;

/**
 * Spans of time a device recorded, each with a device-specific {@code type}.
 */
public class GenericDurationSampleProvider extends AbstractTimeSampleProvider<GenericDurationSample> {
    public GenericDurationSampleProvider(@NonNull final GBDevice device, @NonNull final DaoSession session) {
        super(device, session);
    }

    @NonNull
    @Override
    public AbstractDao<GenericDurationSample, ?> getSampleDao() {
        return getSession().getGenericDurationSampleDao();
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return GenericDurationSampleDao.Properties.Timestamp;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return GenericDurationSampleDao.Properties.DeviceId;
    }

    @NonNull
    @Override
    public GenericDurationSample createSample() {
        return new GenericDurationSample();
    }

    /**
     * The spans of {@code type} that overlap {@code [fromMillis, toMillis]}, oldest first.
     */
    @NonNull
    public List<GenericDurationSample> getSamplesOverlapping(final int type, final long fromMillis, final long toMillis) {
        final Device dbDevice = DBHelper.findDevice(getDevice(), getSession());
        if (dbDevice == null) {
            return Collections.emptyList();
        }

        return getSampleDao().queryBuilder()
                .where(
                        GenericDurationSampleDao.Properties.DeviceId.eq(dbDevice.getId()),
                        GenericDurationSampleDao.Properties.Type.eq(type),
                        GenericDurationSampleDao.Properties.Timestamp.le(toMillis),
                        GenericDurationSampleDao.Properties.EndTimestamp.ge(fromMillis)
                )
                .orderAsc(GenericDurationSampleDao.Properties.Timestamp)
                .list();
    }
}
