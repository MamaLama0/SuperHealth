/*  Copyright (C) 2018-2024 Da Pa, Pavel Elagin

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
package app.superhealth.engine.devices.jyou;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import app.superhealth.engine.devices.AbstractSampleProvider;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.JYouActivitySample;
import app.superhealth.engine.entities.JYouActivitySampleDao;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.ActivityKind;

public class JYouSampleProvider extends AbstractSampleProvider<JYouActivitySample> {

    public static final int TYPE_ACTIVITY = -1;
    private final float movementDivisor = 6000.0f;
    private GBDevice mDevice;
    private DaoSession mSession;

    public JYouSampleProvider(GBDevice device, DaoSession session) {
        super(device, session);

        mSession = session;
        mDevice = device;
    }

    @Override
    public ActivityKind normalizeType(int rawType) {
        return ActivityKind.fromCode(rawType);
    }

    @Override
    public int toRawActivityKind(ActivityKind activityKind) {
        return activityKind.getCode();
    }

    @Override
    public float normalizeIntensity(int rawIntensity) {
        return rawIntensity/movementDivisor;
    }

    @Override
    public JYouActivitySample createActivitySample() {
        return new JYouActivitySample();
    }

    @Override
    public AbstractDao<JYouActivitySample, ?> getSampleDao() {
        return getSession().getJYouActivitySampleDao();
    }

    @Nullable
    @Override
    protected Property getRawKindSampleProperty() {
        return JYouActivitySampleDao.Properties.RawKind;
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return JYouActivitySampleDao.Properties.Timestamp;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return JYouActivitySampleDao.Properties.DeviceId;
    }
}
