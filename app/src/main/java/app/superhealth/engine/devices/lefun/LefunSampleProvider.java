/*  Copyright (C) 2020-2024 Yukai Li

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
package app.superhealth.engine.devices.lefun;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import app.superhealth.engine.devices.AbstractSampleProvider;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.LefunActivitySample;
import app.superhealth.engine.entities.LefunActivitySampleDao;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.ActivityKind;

/**
 * Sample provider for Lefun devices
 */
public class LefunSampleProvider extends AbstractSampleProvider<LefunActivitySample> {
    public LefunSampleProvider(GBDevice device, DaoSession session) {
        super(device, session);
    }

    @Override
    public AbstractDao<LefunActivitySample, ?> getSampleDao() {
        return getSession().getLefunActivitySampleDao();
    }

    @Nullable
    @Override
    protected Property getRawKindSampleProperty() {
        return LefunActivitySampleDao.Properties.RawKind;
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return LefunActivitySampleDao.Properties.Timestamp;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return LefunActivitySampleDao.Properties.DeviceId;
    }

    @Override
    public ActivityKind normalizeType(int rawType) {
        switch (rawType) {
            case LefunConstants.DB_ACTIVITY_KIND_ACTIVITY:
            case LefunConstants.DB_ACTIVITY_KIND_HEART_RATE:
                return ActivityKind.ACTIVITY;
            case LefunConstants.DB_ACTIVITY_KIND_LIGHT_SLEEP:
                return ActivityKind.LIGHT_SLEEP;
            case LefunConstants.DB_ACTIVITY_KIND_DEEP_SLEEP:
                return ActivityKind.DEEP_SLEEP;
            default:
                return ActivityKind.UNKNOWN;
        }
    }

    @Override
    public int toRawActivityKind(ActivityKind activityKind) {
        switch (activityKind) {
            case ACTIVITY:
                return LefunConstants.DB_ACTIVITY_KIND_ACTIVITY;
            case LIGHT_SLEEP:
                return LefunConstants.DB_ACTIVITY_KIND_LIGHT_SLEEP;
            case DEEP_SLEEP:
                return LefunConstants.DB_ACTIVITY_KIND_DEEP_SLEEP;
            default:
                return LefunConstants.DB_ACTIVITY_KIND_UNKNOWN;
        }
    }

    @Override
    public float normalizeIntensity(int rawIntensity) {
        return rawIntensity / (float) LefunConstants.INTENSITY_MAX;
    }

    @Override
    public LefunActivitySample createActivitySample() {
        return new LefunActivitySample();
    }
}
