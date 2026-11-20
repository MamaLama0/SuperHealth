/*  Copyright (C) 2016-2024 Andreas Shimokawa, Carsten Pfeiffer, Daniele
    Gobbetti

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
package app.superhealth.engine.devices.pebble;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import de.greenrobot.dao.query.QueryBuilder;
import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.devices.AbstractSampleProvider;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.Device;
import app.superhealth.engine.entities.PebbleHealthActivityOverlay;
import app.superhealth.engine.entities.PebbleHealthActivityOverlayDao;
import app.superhealth.engine.entities.PebbleHealthActivitySample;
import app.superhealth.engine.entities.PebbleHealthActivitySampleDao;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.ActivityKind;

public class PebbleHealthSampleProvider extends AbstractSampleProvider<PebbleHealthActivitySample> {
    public static final int TYPE_LIGHT_SLEEP = 1;
    public static final int TYPE_DEEP_SLEEP = 2;
    public static final int TYPE_LIGHT_NAP = 3;
    public static final int TYPE_DEEP_NAP = 4;
    public static final int TYPE_WALK = 5;
    public static final int TYPE_RUN = 6;
    public static final int TYPE_ACTIVITY = -1;


    protected final float movementDivisor = 8000f;

    public PebbleHealthSampleProvider(GBDevice device, DaoSession session) {
        super(device, session);
    }

    @NonNull
    @Override
    public List<PebbleHealthActivitySample> getAllActivitySamples(int timestamp_from, int timestamp_to) {
        List<PebbleHealthActivitySample> samples = super.getGBActivitySamples(timestamp_from, timestamp_to);

        Device dbDevice = DBHelper.findDevice(getDevice(), getSession());
        if (dbDevice == null) {
            // no device, no samples
            return Collections.emptyList();
        }

        QueryBuilder<PebbleHealthActivityOverlay> qb = getSession().getPebbleHealthActivityOverlayDao().queryBuilder();

        // I assume it returns the records by id ascending ... (last overlay is dominant)
        qb.where(PebbleHealthActivityOverlayDao.Properties.DeviceId.eq(dbDevice.getId()), PebbleHealthActivityOverlayDao.Properties.TimestampTo.ge(timestamp_from))
                .where(PebbleHealthActivityOverlayDao.Properties.TimestampFrom.le(timestamp_to));
        List<PebbleHealthActivityOverlay> overlayRecords = qb.build().list();

        for (PebbleHealthActivityOverlay overlay : overlayRecords) {
            for (PebbleHealthActivitySample sample : samples) {
                if (overlay.getTimestampFrom() <= sample.getTimestamp() && sample.getTimestamp() < overlay.getTimestampTo()) {
                    // patch in the raw kind
                    sample.setRawKind(overlay.getRawKind());
                }
            }
        }
        detachFromSession();
        return samples;
    }

    @Override
    public AbstractDao<PebbleHealthActivitySample, ?> getSampleDao() {
        return getSession().getPebbleHealthActivitySampleDao();
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return PebbleHealthActivitySampleDao.Properties.Timestamp;
    }

    @Override
    protected Property getRawKindSampleProperty() {
        return null;
        // it is still in the database just hide it for now. remove these two commented lines later
        //return PebbleHealthActivitySampleDao.Properties.RawKind;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return PebbleHealthActivitySampleDao.Properties.DeviceId;
    }

    @Override
    public PebbleHealthActivitySample createActivitySample() {
        return new PebbleHealthActivitySample();
    }

    @Override
    public ActivityKind normalizeType(int rawType) {
        switch (rawType) {
            case TYPE_DEEP_NAP:
            case TYPE_DEEP_SLEEP:
                return ActivityKind.DEEP_SLEEP;
            case TYPE_LIGHT_NAP:
            case TYPE_LIGHT_SLEEP:
                return ActivityKind.LIGHT_SLEEP;
            case TYPE_ACTIVITY:
            case TYPE_WALK:
            case TYPE_RUN:
                return ActivityKind.ACTIVITY;
            default:
                return ActivityKind.UNKNOWN;
        }
    }

    @Override
    public int toRawActivityKind(ActivityKind activityKind) {
        switch (activityKind) {
            case DEEP_SLEEP:
                return TYPE_DEEP_SLEEP;
            case LIGHT_SLEEP:
                return TYPE_LIGHT_SLEEP;
            case ACTIVITY:
            default:
                return TYPE_ACTIVITY;
        }
    }

    @Override
    public float normalizeIntensity(int rawIntensity) {
        return rawIntensity / movementDivisor;
    }
}
