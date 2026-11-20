/*  Copyright (C) 2026 SuperHealth

    This file is part of SuperHealth (SuperHealth fork), licensed under the GNU AGPL v3 or later. */
package app.superhealth.engine.devices.whoop

import de.greenrobot.dao.AbstractDao
import de.greenrobot.dao.Property
import app.superhealth.engine.devices.AbstractSampleProvider
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.entities.WhoopActivitySample
import app.superhealth.engine.entities.WhoopActivitySampleDao
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.model.ActivityKind

/** Minutes WHOOP : type (éveil, stades de sommeil, non porté), mouvement 0–100, pas et pouls moyen. */
class WhoopActivitySampleProvider(device: GBDevice, session: DaoSession) : AbstractSampleProvider<WhoopActivitySample>(device, session) {
    override fun getSampleDao(): AbstractDao<WhoopActivitySample, *> = session.whoopActivitySampleDao
    override fun getRawKindSampleProperty(): Property = WhoopActivitySampleDao.Properties.RawKind
    override fun getTimestampSampleProperty(): Property = WhoopActivitySampleDao.Properties.Timestamp
    override fun getDeviceIdentifierSampleProperty(): Property = WhoopActivitySampleDao.Properties.DeviceId
    override fun createActivitySample() = WhoopActivitySample()
    override fun normalizeType(rawType: Int): ActivityKind = when (rawType) {
        KIND_ACTIVITY -> ActivityKind.ACTIVITY
        KIND_LIGHT -> ActivityKind.LIGHT_SLEEP
        KIND_DEEP -> ActivityKind.DEEP_SLEEP
        KIND_REM -> ActivityKind.REM_SLEEP
        KIND_AWAKE -> ActivityKind.AWAKE_SLEEP
        KIND_NOT_WORN -> ActivityKind.NOT_WORN
        else -> ActivityKind.UNKNOWN
    }

    override fun toRawActivityKind(activityKind: ActivityKind): Int = when (activityKind) {
        ActivityKind.ACTIVITY -> KIND_ACTIVITY
        ActivityKind.LIGHT_SLEEP -> KIND_LIGHT
        ActivityKind.DEEP_SLEEP -> KIND_DEEP
        ActivityKind.REM_SLEEP -> KIND_REM
        ActivityKind.AWAKE_SLEEP -> KIND_AWAKE
        ActivityKind.NOT_WORN -> KIND_NOT_WORN
        else -> 0
    }

    override fun normalizeIntensity(rawIntensity: Int): Float = rawIntensity / 100f

    companion object {
        const val KIND_ACTIVITY = 1
        const val KIND_LIGHT = 2
        const val KIND_DEEP = 3
        const val KIND_REM = 4
        const val KIND_AWAKE = 5
        const val KIND_NOT_WORN = 100
    }
}
