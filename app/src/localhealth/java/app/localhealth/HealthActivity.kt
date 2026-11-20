package app.localhealth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.localhealth.ui.HealthApp
import app.localhealth.ui.theme.HealthTheme
import app.superhealth.engine.GBApplication
import app.superhealth.engine.activities.PermissionsActivity
import app.superhealth.engine.activities.welcome.WelcomeActivity
import app.superhealth.engine.util.PermissionsUtils

class HealthActivity : ComponentActivity() {
    private val vm: HealthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HealthTheme { HealthApp(vm) } }

        // Reuse SuperHealth's onboarding: welcome flow on first run, permission check afterwards
        if (savedInstanceState == null) {
            val prefs = GBApplication.getPrefs()
            if (prefs.getBoolean("first_run", true)) {
                startActivity(Intent(this, WelcomeActivity::class.java))
            } else if (prefs.getBoolean("permission_pestering", true) && !PermissionsUtils.checkAllPermissions(this)) {
                startActivity(
                    Intent(this, PermissionsActivity::class.java)
                        .putExtra(PermissionsActivity.ARG_SHOW_DO_NOT_ASK_BUTTON, true)
                )
            }
            GBApplication.deviceService().requestDeviceInfo()
        }
    }

    override fun onResume() {
        super.onResume()
        vm.bump()
    }
}
