package app.superhealth.engine.service

import android.bluetooth.BluetoothAdapter
import android.content.Context
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.model.CallSpec
import app.superhealth.engine.model.NotificationSpec
import app.superhealth.engine.service.btbr.AbstractBTBRDeviceSupport
import org.slf4j.Logger

abstract class AbstractHeadphoneBTBRDeviceSupport(logger: Logger, bufferSize: Int = 1024) :
    AbstractBTBRDeviceSupport(logger, bufferSize), HeadphoneHelper.Callback {

    private lateinit var headphoneHelper: HeadphoneHelper

    override fun setContext(gbDevice: GBDevice, btAdapter: BluetoothAdapter, context: Context) {
        super.setContext(gbDevice, btAdapter, context)
        headphoneHelper = HeadphoneHelper(getContext(), gbDevice, this)
    }

    override fun dispose() {
        synchronized(ConnectionMonitor) {
            headphoneHelper.dispose()
            super.dispose()
        }
    }

    override fun onSetCallState(callSpec: CallSpec) {
        headphoneHelper.onSetCallState(callSpec)
    }

    override fun onNotification(notificationSpec: NotificationSpec) {
        headphoneHelper.onNotification(notificationSpec)
    }

    override fun onSendConfiguration(config: String) {
        if (!headphoneHelper.onSendConfiguration(config)) {
            super.onSendConfiguration(config)
        }
    }
}
