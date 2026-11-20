package app.superhealth.engine.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import app.superhealth.engine.activities.charts.BodyEnergyFragment;
import app.superhealth.engine.activities.charts.BodyEnergyPeriodFragment;

public class BodyEnergyFragmentAdapter extends NestedFragmentAdapter {

    public BodyEnergyFragmentAdapter(Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new BodyEnergyFragment();
            case 1:
                return BodyEnergyPeriodFragment.newInstance(7);
            case 2:
                return BodyEnergyPeriodFragment.newInstance(30);
        }
        return new BodyEnergyFragment();
    }
}

