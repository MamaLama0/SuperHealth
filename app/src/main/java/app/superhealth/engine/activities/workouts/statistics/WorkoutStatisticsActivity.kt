package app.superhealth.engine.activities.workouts.statistics

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import app.superhealth.engine.R
import app.superhealth.engine.activities.AbstractGBActivity
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.util.kotlin.getParcelableCompat

/**
 * Sport list and per-sport statistics for one device.
 */
class WorkoutStatisticsActivity : AbstractGBActivity() {
    private val viewModel: WorkoutStatisticsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val device = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)

        setContentView(R.layout.activity_workout_statistics)

        viewModel.load(device)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SportListFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
