package app.superhealth.engine.activities.charts;

import java.util.Calendar;
import java.util.List;

import app.superhealth.engine.R;
import app.superhealth.engine.database.DBHandler;
import app.superhealth.engine.devices.TimeSampleProvider;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.ActivitySample;
import app.superhealth.engine.model.SleepScoreSample;

abstract class SleepFragment<D extends ChartsData> extends AbstractActivityChartFragment<D> {
    @Override
    public String getTitle() {
        return getString(R.string.sleepchart_your_sleep);
    }

    @Override
    protected List<? extends ActivitySample> getSamples(DBHandler db, GBDevice device, int tsFrom, int tsTo) {
        return super.getAllSamples(db, device, tsFrom, tsTo);
    }

    public boolean supportsSleepScore() {
        final GBDevice device = getChartsHost().getDevice();
        return device.getDeviceCoordinator().supportsSleepScore(device);
    }

    protected List<SleepScoreSample> getSleepScoreSamples(DBHandler db, GBDevice device, int tsFrom, int tsTo) {
        TimeSampleProvider<? extends SleepScoreSample> provider = device.getDeviceCoordinator().getSleepScoreProvider(device, db.getDaoSession());
        return (List<SleepScoreSample>) provider.getAllSamples(tsFrom * 1000L, tsTo * 1000L);
    }

    protected List<SleepScoreSample> getSleepScoreSamples(DBHandler db, GBDevice device, Calendar day) {
        int startTs;
        int endTs;

        day = (Calendar) day.clone(); // do not modify the caller's argument
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.add(Calendar.HOUR, 0);
        startTs = (int) (day.getTimeInMillis() / 1000);
        endTs = startTs + 24 * 60 * 60 - 1;

        TimeSampleProvider<? extends SleepScoreSample> provider = device.getDeviceCoordinator().getSleepScoreProvider(device, db.getDaoSession());
        return (List<SleepScoreSample>) provider.getAllSamples(startTs * 1000L, endTs * 1000L);
    }

}
