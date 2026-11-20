/*  Copyright (C) 2026 Freeyourgadget

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

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import app.superhealth.engine.entities.${entity.className};
import app.superhealth.engine.entities.${entity.classNameDao};
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.impl.GBDevice;

public class ${entity.className}Provider extends AbstractTimeSampleProvider<${entity.className}> {
    public ${entity.className}Provider(@NonNull final GBDevice device, @NonNull final DaoSession session) {
        super(device, session);
    }

    @NonNull
    @Override
    public AbstractDao<${entity.className}, ?> getSampleDao() {
        return getSession().get${entity.classNameDao}();
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return ${entity.classNameDao}.Properties.Timestamp;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return ${entity.classNameDao}.Properties.DeviceId;
    }

    @NonNull
    @Override
    public ${entity.className} createSample() {
        return new ${entity.className}();
    }
}
