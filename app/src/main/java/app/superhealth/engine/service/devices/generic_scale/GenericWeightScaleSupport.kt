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
package app.superhealth.engine.service.devices.generic_scale

import android.content.Intent
import app.superhealth.engine.GBApplication
import app.superhealth.engine.deviceevents.GBDeviceEventBatteryInfo
import app.superhealth.engine.deviceevents.GBDeviceEventVersionInfo
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.service.btle.AbstractBTLESingleDeviceSupport
import app.superhealth.engine.service.btle.GattService
import app.superhealth.engine.service.btle.TransactionBuilder
import app.superhealth.engine.service.btle.profiles.IntentListener
import app.superhealth.engine.service.btle.profiles.battery.BatteryInfo
import app.superhealth.engine.service.btle.profiles.battery.BatteryInfoProfile
import app.superhealth.engine.service.btle.profiles.deviceinfo.DeviceInfo
import app.superhealth.engine.service.btle.profiles.deviceinfo.DeviceInfoProfile
import app.superhealth.engine.service.btle.profiles.weightScale.WeightScaleProfile
import app.superhealth.engine.util.kotlin.getParcelableCompat
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class GenericWeightScaleSupport(logger: Logger = LOG) :
    AbstractBTLESingleDeviceSupport(logger) {
    private var mBatteryInfoProfile: BatteryInfoProfile<GenericWeightScaleSupport>
    private var mDeviceInfoProfile: DeviceInfoProfile<GenericWeightScaleSupport>
    private var mWeightScaleProfile: WeightScaleProfile<GenericWeightScaleSupport>


    init {
        val mListener = IntentListener { intent: Intent? ->
            intent?.action?.let { action ->
                when (action) {
                    DeviceInfoProfile.ACTION_DEVICE_INFO -> {
                        handleDeviceInfo(intent.getParcelableCompat<DeviceInfo>(DeviceInfoProfile.EXTRA_DEVICE_INFO)!!)
                    }

                    BatteryInfoProfile.ACTION_BATTERY_INFO -> {
                        handleBatteryInfo(intent.getParcelableCompat<BatteryInfo>(BatteryInfoProfile.EXTRA_BATTERY_INFO)!!)
                    }

                    WeightScaleProfile.ACTION_WEIGHT_MEASUREMENT -> {
                        LOG.debug("received weight")
                        intent.putExtra(WeightScaleProfile.EXTRA_ADDRESS, device.address)
                        intent.setPackage(context.packageName)
                        context.sendBroadcast(intent)
                    }
                }
            }
        }

        addSupportedService(GattService.SERVICE_DEVICE_INFORMATION.uuid)
        mDeviceInfoProfile = DeviceInfoProfile<GenericWeightScaleSupport>(this)
        mDeviceInfoProfile.addListener(mListener)
        addSupportedProfile(mDeviceInfoProfile)

        addSupportedService(GattService.SERVICE_BATTERY.uuid)
        mBatteryInfoProfile = BatteryInfoProfile<GenericWeightScaleSupport>(this)
        mBatteryInfoProfile.addListener(mListener)
        addSupportedProfile(mBatteryInfoProfile)

        addSupportedService(GattService.SERVICE_WEIGHT_SCALE.uuid)
        mWeightScaleProfile = WeightScaleProfile<GenericWeightScaleSupport>(this)
        mWeightScaleProfile.addListener(mListener)
        addSupportedProfile(mWeightScaleProfile)
    }

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        if (device.firmwareVersion == null) {
            device.firmwareVersion = "N/A"
            device.firmwareVersion2 = "N/A"
        }

        builder.setDeviceState(GBDevice.State.INITIALIZING)

        mDeviceInfoProfile.requestDeviceInfo(builder)

        mBatteryInfoProfile.requestBatteryInfo(builder)
        mBatteryInfoProfile.enableNotify(builder, true)

        if (GBApplication.getPrefs().syncTime()) {
            mWeightScaleProfile.setTime(builder)
        }

        mWeightScaleProfile.enableNotify(builder, true)

        builder.setDeviceState(GBDevice.State.INITIALIZED)

        return builder
    }

    private fun handleBatteryInfo(info: BatteryInfo) {
        LOG.debug("handleBatteryInfo: {}", info)
        val batteryCmd = GBDeviceEventBatteryInfo()
        batteryCmd.level = info.percentCharged
        handleGBDeviceEvent(batteryCmd)
    }

    private fun handleDeviceInfo(deviceInfo: DeviceInfo) {
        LOG.debug("handleDeviceInfo: {}", deviceInfo)

        val versionCmd = GBDeviceEventVersionInfo()

        if (deviceInfo.hardwareRevision != null) {
            versionCmd.hwVersion = deviceInfo.hardwareRevision
        }
        if (deviceInfo.firmwareRevision != null) {
            versionCmd.fwVersion = deviceInfo.firmwareRevision
        }
        if (deviceInfo.softwareRevision != null) {
            versionCmd.fwVersion2 = deviceInfo.softwareRevision
        }

        handleGBDeviceEvent(versionCmd)
    }

    override fun useAutoConnect(): Boolean {
        return false
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(GenericWeightScaleSupport::class.java)
    }
}
