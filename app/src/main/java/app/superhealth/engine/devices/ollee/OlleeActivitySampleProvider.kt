/*  Copyright (C) 2026 Ken Blizzard-Caron

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
package app.superhealth.engine.devices.ollee

import de.greenrobot.dao.AbstractDao
import de.greenrobot.dao.Property
import app.superhealth.engine.devices.AbstractSampleProvider
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.entities.OlleeActivitySample
import app.superhealth.engine.entities.OlleeActivitySampleDao
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.model.ActivityKind

class OlleeActivitySampleProvider(device: GBDevice, session: DaoSession) :
    AbstractSampleProvider<OlleeActivitySample>(device, session) {

    override fun normalizeType(rawType: Int): ActivityKind = ActivityKind.fromCode(rawType)

    override fun toRawActivityKind(activityKind: ActivityKind): Int = activityKind.code

    override fun normalizeIntensity(rawIntensity: Int): Float = rawIntensity.toFloat()

    override fun createActivitySample(): OlleeActivitySample = OlleeActivitySample()

    override fun getSampleDao(): AbstractDao<OlleeActivitySample, *> = getSession().getOlleeActivitySampleDao()

    override fun getRawKindSampleProperty(): Property? = OlleeActivitySampleDao.Properties.RawKind

    override fun getTimestampSampleProperty(): Property = OlleeActivitySampleDao.Properties.Timestamp

    override fun getDeviceIdentifierSampleProperty(): Property = OlleeActivitySampleDao.Properties.DeviceId
}
