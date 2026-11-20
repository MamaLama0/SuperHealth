/*  Copyright (C) 2020-2024 opavlov

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
package app.superhealth.engine.devices.sonyswr12;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import app.superhealth.engine.devices.AbstractSampleProvider;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.SonySWR12Sample;
import app.superhealth.engine.entities.SonySWR12SampleDao;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.ActivityKind;
import app.superhealth.engine.service.devices.sonyswr12.SonySWR12Constants;

public class SonySWR12SampleProvider extends AbstractSampleProvider<SonySWR12Sample> {
    public SonySWR12SampleProvider(GBDevice device, DaoSession session) {
        super(device, session);
    }

    @Override
    public AbstractDao<SonySWR12Sample, ?> getSampleDao() {
        return getSession().getSonySWR12SampleDao();
    }

    @Nullable
    @Override
    protected Property getRawKindSampleProperty() {
        return SonySWR12SampleDao.Properties.RawKind;
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return SonySWR12SampleDao.Properties.Timestamp;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return SonySWR12SampleDao.Properties.DeviceId;
    }

    @Override
    public ActivityKind normalizeType(int rawType) {
        switch (rawType) {
            case SonySWR12Constants.TYPE_ACTIVITY:
                return ActivityKind.ACTIVITY;
            case SonySWR12Constants.TYPE_LIGHT:
                return ActivityKind.LIGHT_SLEEP;
            case SonySWR12Constants.TYPE_DEEP:
                return ActivityKind.DEEP_SLEEP;
            case SonySWR12Constants.TYPE_NOT_WORN:
                return ActivityKind.NOT_WORN;
        }
        return ActivityKind.UNKNOWN;
    }

    @Override
    public int toRawActivityKind(ActivityKind activityKind) {
        switch (activityKind) {
            case ACTIVITY:
                return SonySWR12Constants.TYPE_ACTIVITY;
            case LIGHT_SLEEP:
                return SonySWR12Constants.TYPE_LIGHT;
            case DEEP_SLEEP:
                return SonySWR12Constants.TYPE_DEEP;
            case NOT_WORN:
                return SonySWR12Constants.TYPE_NOT_WORN;
        }
        return SonySWR12Constants.TYPE_ACTIVITY;
    }

    @Override
    public float normalizeIntensity(int rawIntensity) {
        return rawIntensity;
    }

    @Override
    public SonySWR12Sample createActivitySample() {
        return new SonySWR12Sample();
    }
}
