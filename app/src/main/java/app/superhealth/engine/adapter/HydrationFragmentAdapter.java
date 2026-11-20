package app.superhealth.engine.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import app.superhealth.engine.activities.charts.HydrationDailyFragment;
import app.superhealth.engine.activities.charts.HydrationPeriodFragment;

public class HydrationFragmentAdapter extends NestedFragmentAdapter {

    public HydrationFragmentAdapter(Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return switch (position) {
            case 1 -> HydrationPeriodFragment.newInstance(7);
            case 2 -> HydrationPeriodFragment.newInstance(30);
            default -> new HydrationDailyFragment();
        };
    }
}
