/*  Copyright (C) 2023 Daniele Gobbetti

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
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package app.superhealth.engine.devices.sbm_67

import app.superhealth.engine.activities.devicesettings.dsl.DeviceSettingsSpec
import app.superhealth.engine.activities.devicesettings.dsl.components.bloodPressureActiveUser
import app.superhealth.engine.activities.devicesettings.dsl.deviceSettings
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.devices.GenericBloodPressureSampleProvider
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.devices.generic_bp.GenericBloodPressureSupport

/**
 * SBM67 devices seem to be sold under multiple brands, with slightly different bluetooth names and bonding behaviors.
 */
abstract class AbstractSBM67Coordinator : AbstractBLEDeviceCoordinator() {
    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return GenericBloodPressureSupport::class.java
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.BLOOD_PRESSURE_METER
    }

    override fun getBatteryCount(device: GBDevice): Int {
        return 0 // it does not report battery %
    }

    override fun suggestUnbindBeforePair(): Boolean {
        // Works just fine if already paired
        return false
    }

    override fun supportsBloodPressureMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun getBloodPressureSampleProvider(
        device: GBDevice,
        session: DaoSession,
    ): GenericBloodPressureSampleProvider? {
        return GenericBloodPressureSampleProvider(device, session)
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        bloodPressureActiveUser(device)
    }
}
