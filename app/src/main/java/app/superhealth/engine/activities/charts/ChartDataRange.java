package app.superhealth.engine.activities.charts;

import androidx.annotation.Nullable;

import app.superhealth.engine.devices.SampleProvider;
import app.superhealth.engine.devices.TimeSampleProvider;
import app.superhealth.engine.model.ActivitySample;
import app.superhealth.engine.model.TimeSample;

/**
 * The time range with available data for a chart, in milliseconds.
 */
public final class ChartDataRange {
    private final long startMillis;
    private final long endMillis;

    public ChartDataRange(final long startMillis, final long endMillis) {
        this.startMillis = startMillis;
        this.endMillis = endMillis;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    @Nullable
    public static ChartDataRange ofActivitySamples(@Nullable final SampleProvider<?> provider) {
        if (provider == null) {
            return null;
        }
        final ActivitySample first = provider.getFirstActivitySample();
        final ActivitySample last = provider.getLatestActivitySample();
        if (first == null || last == null) {
            return null;
        }
        return new ChartDataRange(
            first.getTimestamp() * 1000L,
            last.getTimestamp() * 1000L
        );
    }

    @Nullable
    public static ChartDataRange ofSamples(@Nullable final TimeSampleProvider<?> provider) {
        if (provider == null) {
            return null;
        }
        final TimeSample first = provider.getFirstSample();
        final TimeSample last = provider.getLatestSample();
        if (first == null || last == null) {
            return null;
        }
        return new ChartDataRange(
            first.getTimestamp(),
            last.getTimestamp()
        );
    }

    /**
     * Returns the largest range that contains all the given ranges, or null if all of them are null.
     */
    @Nullable
    public static ChartDataRange union(final ChartDataRange... ranges) {
        ChartDataRange result = null;
        for (final ChartDataRange range : ranges) {
            if (range == null) {
                continue;
            }
            if (result == null) {
                result = range;
            } else {
                result = new ChartDataRange(
                    Math.min(result.startMillis, range.startMillis),
                    Math.max(result.endMillis, range.endMillis)
                );
            }
        }
        return result;
    }
}
