package app.superhealth.engine.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import app.superhealth.engine.activities.charts.RespiratoryRateDailyFragment;
import app.superhealth.engine.activities.charts.RespiratoryRatePeriodFragment;

public class RespiratoryRateFragmentAdapter extends NestedFragmentAdapter {

    public RespiratoryRateFragmentAdapter(Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new RespiratoryRateDailyFragment();
            case 1:
                return RespiratoryRatePeriodFragment.newInstance(7);
            case 2:
                return RespiratoryRatePeriodFragment.newInstance(30);
        }
        return new RespiratoryRateDailyFragment();
    }
}
