/*  Copyright (C) 2026 Toby Murray

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
package app.superhealth.engine.devices.una

import de.greenrobot.dao.AbstractDao
import de.greenrobot.dao.Property
import app.superhealth.engine.devices.AbstractSampleProvider
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.entities.UnaDailySample
import app.superhealth.engine.entities.UnaDailySampleDao
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.model.ActivityKind

class UnaDailySampleProvider(device: GBDevice, session: DaoSession) :
    AbstractSampleProvider<UnaDailySample>(device, session) {
    override fun normalizeType(rawType: Int): ActivityKind = ActivityKind.fromCode(rawType)
    override fun toRawActivityKind(activityKind: ActivityKind): Int = activityKind.code
    override fun normalizeIntensity(rawIntensity: Int): Float = rawIntensity.toFloat()
    override fun createActivitySample(): UnaDailySample = UnaDailySample()
    override fun getSampleDao(): AbstractDao<UnaDailySample, *> = getSession().unaDailySampleDao
    override fun getRawKindSampleProperty(): Property? = UnaDailySampleDao.Properties.RawKind
    override fun getTimestampSampleProperty(): Property = UnaDailySampleDao.Properties.Timestamp
    override fun getDeviceIdentifierSampleProperty(): Property = UnaDailySampleDao.Properties.DeviceId
}
