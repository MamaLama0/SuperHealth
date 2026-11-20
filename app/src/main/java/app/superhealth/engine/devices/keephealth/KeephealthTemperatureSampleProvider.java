package app.superhealth.engine.devices.keephealth;

import androidx.annotation.NonNull;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import app.superhealth.engine.devices.AbstractTimeSampleProvider;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.KeephealthTemperatureSample;
import app.superhealth.engine.entities.KeephealthTemperatureSampleDao;
import app.superhealth.engine.impl.GBDevice;

public class KeephealthTemperatureSampleProvider extends AbstractTimeSampleProvider<KeephealthTemperatureSample> {
    public KeephealthTemperatureSampleProvider(GBDevice device, DaoSession session) {
        super(device, session);
    }

    @Override
    public AbstractDao<KeephealthTemperatureSample, ?> getSampleDao() {
        return getSession().getKeephealthTemperatureSampleDao();
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return KeephealthTemperatureSampleDao.Properties.Timestamp;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return KeephealthTemperatureSampleDao.Properties.DeviceId;
    }

    @Override
    public KeephealthTemperatureSample createSample() {
        return new KeephealthTemperatureSample();
    }
}