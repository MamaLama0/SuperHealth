/*  Copyright (C) 2025  Thomas Kuehne

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
package app.superhealth.engine.service.devices.ultrahuman

import android.bluetooth.BluetoothGattCharacteristic
import app.superhealth.engine.devices.ultrahuman.UltrahumanConstants
import app.superhealth.engine.service.btle.actions.ConditionalWriteAction
import app.superhealth.engine.util.DateTimeUtils

/** calculate date/time on the fly to avoid setting an outdated value */
class UltrahumanWriteTime(characteristic: BluetoothGattCharacteristic) :
    ConditionalWriteAction(characteristic) {
    override fun checkCondition(): ByteArray {
        val epoc: Long = DateTimeUtils.getEpochSeconds()

        val value = byteArrayOf(
            UltrahumanConstants.OPERATION_SETTIME,
            (epoc and 0xff).toByte(),
            ((epoc shr 8) and 0xff).toByte(),
            ((epoc shr 16) and 0xff).toByte(),
            ((epoc shr 24) and 0xff).toByte()
        )
        return value
    }
}