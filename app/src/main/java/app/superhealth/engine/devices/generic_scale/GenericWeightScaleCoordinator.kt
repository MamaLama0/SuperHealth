/*  Copyright (C) 2025 Thomas Kuehne

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
package app.superhealth.engine.devices.generic_scale

import app.superhealth.engine.R
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.DeviceCardAction
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.devices.GenericWeightSampleProvider
import app.superhealth.engine.devices.TimeSampleProvider
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.impl.GBDeviceCandidate
import app.superhealth.engine.model.WeightSample
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.btle.GattService
import app.superhealth.engine.service.devices.generic_scale.GenericWeightScaleSupport
import java.util.Collections

class GenericWeightScaleCoordinator : AbstractBLEDeviceCoordinator() {
    override fun getCustomActions(): MutableList<DeviceCardAction?> {
        return Collections.singletonList(
            DeviceCardAction.forActivity(
                R.drawable.ic_balance,
                R.string.weight_scale_show_measurement,
                GenericWeightScaleMeasurementActivity::class.java
            )
        )
    }

    override fun getOrderPriority(): Int {
        return Int.MAX_VALUE
    }

    override fun getWeightSampleProvider(
        device: GBDevice, session: DaoSession
    ): TimeSampleProvider<out WeightSample?> {
        return GenericWeightSampleProvider(device, session)
    }

    override fun isExperimental(): Boolean {
        // needs more testing
        return true
    }

    override fun getManufacturer(): String {
        return "Generic"
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport?> {
        return GenericWeightScaleSupport::class.java
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_ASK
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_miscale
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_generic_weight_scale
    }

    override fun supports(candidate: GBDeviceCandidate): Boolean {
        return candidate.supportsService(GattService.SERVICE_WEIGHT_SCALE.uuid)
    }

    override fun supportsWeightMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun supportsCharts(device: GBDevice): Boolean {
        return true
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.SCALE
    }
}
