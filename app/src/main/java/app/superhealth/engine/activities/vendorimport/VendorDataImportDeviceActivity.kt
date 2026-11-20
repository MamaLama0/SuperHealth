package app.superhealth.engine.activities.vendorimport

import android.content.Intent
import android.os.Bundle
import app.superhealth.engine.GBApplication
import app.superhealth.engine.activities.AbstractGBActivity
import app.superhealth.engine.databinding.ActivityVendorDataImportDeviceBinding
import app.superhealth.engine.databinding.ItemVendorImportTargetDeviceBinding
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.util.DeviceIconUtils

class VendorDataImportDeviceActivity : AbstractGBActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityVendorDataImportDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val container = binding.vendorImportTargetDevices
        for (device in getSupportedDevices().sortedBy { it.aliasOrName }) {
            val row = ItemVendorImportTargetDeviceBinding.inflate(layoutInflater, container, false)
            row.vendorImportTargetIcon.setImageDrawable(
                DeviceIconUtils.tile(this, device.deviceCoordinator.defaultIconResource, device.isInitialized)
            )
            row.vendorImportTargetName.text = device.aliasOrName
            row.vendorImportTargetAddress.text = device.address
            row.root.setOnClickListener {
                startActivity(
                    Intent(this, VendorDataImportActivity::class.java).putExtra(GBDevice.EXTRA_DEVICE, device)
                )
            }
            container.addView(row.root)
        }
    }

    companion object {
        @JvmStatic
        fun getSupportedDevices(): List<GBDevice> =
            GBApplication.app().deviceManager.devices
                .filter { it.deviceCoordinator.getVendorDataImporter(it) != null }
    }
}
