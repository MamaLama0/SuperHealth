package app.superhealth.engine.devices;

import androidx.annotation.Nullable;

import app.superhealth.engine.model.Vo2MaxSample;

public interface Vo2MaxSampleProvider<T extends Vo2MaxSample> extends TimeSampleProvider<T> {
    @Nullable
    T getLatestSample(Vo2MaxSample.Type type, long until);

    @Override
    @Nullable
    default T getLatestSample(long until) {
        return getLatestSample(Vo2MaxSample.Type.ANY, until);
    }
}
