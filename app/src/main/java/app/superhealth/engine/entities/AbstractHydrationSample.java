package app.superhealth.engine.entities;

import androidx.annotation.NonNull;

import app.superhealth.engine.model.HydrationSample;
import app.superhealth.engine.util.DateTimeUtils;

public abstract class AbstractHydrationSample extends AbstractTimeSample implements HydrationSample {
    @NonNull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
            "timestamp=" + DateTimeUtils.formatDateTime(DateTimeUtils.parseTimestampMillis(getTimestamp())) +
            ", day=" + getDay() +
            ", volumeMl=" + getVolumeMl() +
            ", userId=" + getUserId() +
            ", deviceId=" + getDeviceId() +
            "}";
    }
}
