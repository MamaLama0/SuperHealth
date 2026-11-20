package app.superhealth.engine.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import app.superhealth.engine.activities.charts.steps.StepsDailyFragment;
import app.superhealth.engine.activities.charts.StepsPeriodFragment;

public class StepsFragmentAdapter extends NestedFragmentAdapter {

    public StepsFragmentAdapter(Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new StepsDailyFragment();
            case 1:
                return StepsPeriodFragment.newInstance(7);
            case 2:
                return StepsPeriodFragment.newInstance(30);
        }
        return new StepsDailyFragment();
    }
}
