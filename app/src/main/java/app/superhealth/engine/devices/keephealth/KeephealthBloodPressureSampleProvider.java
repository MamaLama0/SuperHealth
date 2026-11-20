package app.superhealth.engine.devices.keephealth;

import androidx.annotation.NonNull;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import app.superhealth.engine.devices.AbstractTimeSampleProvider;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.KeephealthBloodPressureSample;
import app.superhealth.engine.entities.KeephealthBloodPressureSampleDao;
import app.superhealth.engine.impl.GBDevice;

public class KeephealthBloodPressureSampleProvider extends AbstractTimeSampleProvider<KeephealthBloodPressureSample> {
    public KeephealthBloodPressureSampleProvider(GBDevice device, DaoSession session) {
        super(device, session);
    }

    @Override
    public AbstractDao<KeephealthBloodPressureSample, ?> getSampleDao() {
        return getSession().getKeephealthBloodPressureSampleDao();
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return KeephealthBloodPressureSampleDao.Properties.Timestamp;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return KeephealthBloodPressureSampleDao.Properties.DeviceId;
    }

    @Override
    public KeephealthBloodPressureSample createSample() {
        return new KeephealthBloodPressureSample();
    }
}