package app.superhealth.engine.activities.workouts

import android.os.Bundle
import android.view.MenuItem
import app.superhealth.engine.R
import app.superhealth.engine.activities.AbstractGBActivity
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.util.kotlin.getParcelableCompat

/**
 * Thin host for [WorkoutListFragment]. It requires [GBDevice.EXTRA_DEVICE], shows the fragment and
 * handles the up button.
 */
class WorkoutListActivity : AbstractGBActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val device = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)
            ?: throw IllegalArgumentException("Must provide a device when invoking this activity")

        setContentView(R.layout.activity_workout_list)

        // After a configuration change the FragmentManager restores the fragment by itself.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.workout_list_fragment_container, WorkoutListFragment.newInstance(device))
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}
