/*  Copyright (C) 2026 SuperHealth

    This file is part of Gadgetbridge (SuperHealth fork), licensed under the GNU AGPL v3 or later. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.whoop

import de.greenrobot.dao.AbstractDao
import de.greenrobot.dao.Property
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.whoop.WhoopActivitySampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHeartRateSample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHeartRateSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHrvValueSample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHrvValueSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.GenericRespiratoryRateSample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericRespiratoryRateSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.GenericSleepScoreSample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericSleepScoreSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.GenericStressSample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericStressSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTemperatureSample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTemperatureSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.WhoopActivitySample
import nodomain.freeyourgadget.gadgetbridge.entities.WhoopActivitySampleDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureSample
import kotlin.math.roundToInt

/**
 * Écrit le résultat de [WhoopAnalysis] dans la base Gadgetbridge pour une fenêtre recalculée en entier : les lignes de
 * la fenêtre sont d'abord effacées (un recalcul peut déplacer un début de nuit), puis réécrites. Tout ce que lisent les
 * écrans (pas, pouls, sommeil, VFC, température, respiration, stress, score de sommeil) passe par les tables génériques.
 */
object WhoopPersist {
    fun write(db: DBHandler, device: GBDevice, fromUnix: Long, toUnix: Long, r: WhoopAnalysis.Result, previousOnsets: List<Long>) {
        val s = db.daoSession
        val deviceId = DBHelper.getDevice(device, s).id!!
        val userId = DBHelper.getUser(s).id!!
        val fromMs = fromUnix * 1000; val toMs = toUnix * 1000
        s.database.beginTransaction()
        try {
            clear(s.whoopActivitySampleDao, WhoopActivitySampleDao.Properties.DeviceId, WhoopActivitySampleDao.Properties.Timestamp, deviceId, fromUnix, toUnix)
            clear(s.genericHrvValueSampleDao, GenericHrvValueSampleDao.Properties.DeviceId, GenericHrvValueSampleDao.Properties.Timestamp, deviceId, fromMs, toMs)
            clear(s.genericHeartRateSampleDao, GenericHeartRateSampleDao.Properties.DeviceId, GenericHeartRateSampleDao.Properties.Timestamp, deviceId, fromMs, toMs)
            clear(s.genericTemperatureSampleDao, GenericTemperatureSampleDao.Properties.DeviceId, GenericTemperatureSampleDao.Properties.Timestamp, deviceId, fromMs, toMs)
            clear(s.genericRespiratoryRateSampleDao, GenericRespiratoryRateSampleDao.Properties.DeviceId, GenericRespiratoryRateSampleDao.Properties.Timestamp, deviceId, fromMs, toMs)
            clear(s.genericStressSampleDao, GenericStressSampleDao.Properties.DeviceId, GenericStressSampleDao.Properties.Timestamp, deviceId, fromMs, toMs)
            clear(s.genericSleepScoreSampleDao, GenericSleepScoreSampleDao.Properties.DeviceId, GenericSleepScoreSampleDao.Properties.Timestamp, deviceId, fromMs, toMs)

            val stageOf = HashMap<Long, WhoopAnalysis.Stage>()
            r.sessions.forEach { ss -> ss.stages.forEach { (t, st) -> stageOf[t] = st } }
            s.whoopActivitySampleDao.insertOrReplaceInTx(r.minutes.map { m ->
                WhoopActivitySample().apply {
                    timestamp = m.start.toInt(); this.deviceId = deviceId; this.userId = userId
                    rawKind = when (stageOf[m.start]) {
                        WhoopAnalysis.Stage.LIGHT -> WhoopActivitySampleProvider.KIND_LIGHT
                        WhoopAnalysis.Stage.DEEP -> WhoopActivitySampleProvider.KIND_DEEP
                        WhoopAnalysis.Stage.REM -> WhoopActivitySampleProvider.KIND_REM
                        WhoopAnalysis.Stage.WAKE -> WhoopActivitySampleProvider.KIND_AWAKE
                        null -> if (m.worn || m.motion != null) WhoopActivitySampleProvider.KIND_ACTIVITY else WhoopActivitySampleProvider.KIND_NOT_WORN
                    }
                    rawIntensity = ((m.motion ?: 0.0) * 100).roundToInt()
                    // pas pendant le sommeil : seulement une vraie marche (≥ 6 pas dans la minute), pas un mouvement isolé
                    // dans le lit — règle « sleep-aware » de NOOP ramenée à la minute
                    steps = if (stageOf[m.start].let { it != null && it != WhoopAnalysis.Stage.WAKE } && m.steps < 6) 0 else m.steps
                    heartRate = m.hr ?: 0
                }
            })
            val onsets = previousOnsets.toMutableList()
            r.sessions.sortedBy { it.start }.forEach { ss ->
                val end = ss.end * 1000
                ss.restingHr?.let { s.genericHeartRateSampleDao.insertOrReplace(GenericHeartRateSample(end, deviceId, userId, it)) }
                ss.respRate?.let { s.genericRespiratoryRateSampleDao.insertOrReplace(GenericRespiratoryRateSample(end, deviceId, userId, it.toFloat())) }
                ss.hrv.forEach { (t, v) -> s.genericHrvValueSampleDao.insertOrReplace(GenericHrvValueSample(t * 1000, deviceId, userId, v)) }
                if (ss.asleepMin >= 120) {
                    s.genericSleepScoreSampleDao.insertOrReplace(GenericSleepScoreSample(end, deviceId, userId, WhoopAnalysis.restScore(ss, previousOnsets = onsets.takeLast(7))))
                    onsets += ss.start
                }
            }
            // température cutanée : moyenne par tranche de 10 min
            r.minutes.filter { it.tempC != null }.groupBy { it.start / 600 * 600 }.forEach { (t, ms) ->
                s.genericTemperatureSampleDao.insertOrReplace(GenericTemperatureSample(t * 1000, deviceId, userId,
                    ms.mapNotNull { it.tempC }.average().toFloat(), TemperatureSample.TYPE_SKIN, TemperatureSample.LOCATION_WRIST))
            }
            r.stress.forEach { (t, v) -> s.genericStressSampleDao.insertOrReplace(GenericStressSample(t * 1000, deviceId, userId, v)) }
            s.database.setTransactionSuccessful()
        } finally {
            s.database.endTransaction()
        }
    }

    /** Débuts des dernières nuits enregistrées avant [beforeUnix] (régularité du score de sommeil). */
    fun previousOnsets(db: DBHandler, device: GBDevice, beforeUnix: Long): List<Long> {
        val s = db.daoSession
        val deviceId = DBHelper.getDevice(device, s)?.id ?: return emptyList()
        val sleepKinds = listOf(WhoopActivitySampleProvider.KIND_LIGHT, WhoopActivitySampleProvider.KIND_DEEP, WhoopActivitySampleProvider.KIND_REM)
        val rows = s.whoopActivitySampleDao.queryBuilder().where(WhoopActivitySampleDao.Properties.DeviceId.eq(deviceId),
            WhoopActivitySampleDao.Properties.Timestamp.ge(beforeUnix - 10 * 86400), WhoopActivitySampleDao.Properties.Timestamp.lt(beforeUnix),
            WhoopActivitySampleDao.Properties.RawKind.`in`(sleepKinds)).orderAsc(WhoopActivitySampleDao.Properties.Timestamp).list()
        // une nuit = des minutes de sommeil séparées de la précédente par plus de 3 h
        val onsets = ArrayList<Long>()
        var last = Long.MIN_VALUE
        rows.forEach { r -> val t = r.timestamp.toLong(); if (t - last > 3 * 3600) onsets += t; last = t }
        return onsets
    }

    private fun clear(dao: AbstractDao<*, *>, devProp: Property, tsProp: Property, deviceId: Long, from: Long, to: Long) {
        dao.queryBuilder().where(devProp.eq(deviceId), tsProp.ge(from), tsProp.lt(to)).buildDelete().executeDeleteWithoutDetachingEntities()
    }
}
