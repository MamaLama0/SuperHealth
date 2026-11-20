package app.superhealth.engine.devices.garmin

import android.content.Intent
import android.os.Bundle
import androidx.core.content.edit
import app.superhealth.engine.GBApplication
import app.superhealth.engine.R
import app.superhealth.engine.activities.AbstractGBActivity
import app.superhealth.engine.activities.devicesettings.DeviceSettingsActivity
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsScreen
import app.superhealth.engine.databinding.ActivityGarminAuthExpiredBinding
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.service.devices.garmin.GarminPrefs
import app.superhealth.engine.util.kotlin.getDevice


class GarminAuthExpiredActivity : AbstractGBActivity() {
    private var devicePrefs: GarminPrefs? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityGarminAuthExpiredBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val device = intent.getDevice()
        if (device == null) {
            finish()
            return
        }

        devicePrefs = GarminPrefs(GBApplication.getDeviceSpecificSharedPrefs(device.address), device)

        binding.oauthExpiredDescription.text =
            getString(R.string.garmin_auth_expired_activity_description, device.aliasOrName)

        binding.buttonGotoAuthenticationSettings.setOnClickListener { _ ->
            val startIntent = Intent(this@GarminAuthExpiredActivity, DeviceSettingsActivity::class.java)
            startIntent.putExtra(GBDevice.EXTRA_DEVICE, device)
            startIntent.putExtra(
                DeviceSettingsActivity.MENU_ENTRY_POINT,
                DeviceSettingsActivity.MENU_ENTRY_POINTS.AUTH_SETTINGS
            )
            startActivity(startIntent)
            finish()
        }
        binding.buttonDontShowAgain.setOnClickListener { _ ->
            devicePrefs!!.preferences.edit {
                putBoolean(GarminPrefs.PREF_AUTH_EXPIRED_NOTIFICATION_ENABLED, false)
            }
            finish()
        }
    }
}
