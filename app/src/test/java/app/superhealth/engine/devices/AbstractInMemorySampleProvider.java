package app.superhealth.engine.devices;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import app.superhealth.engine.entities.AbstractActivitySample;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.ActivityKind;

public abstract class AbstractInMemorySampleProvider<T extends AbstractActivitySample> extends AbstractSampleProvider<T> {
    protected AbstractInMemorySampleProvider(final GBDevice device, final DaoSession session) {
        super(device, session);
    }

    @Override
    public AbstractDao<T, ?> getSampleDao() {
        return null; // not database-backed
    }

    @Nullable
    @Override
    protected Property getRawKindSampleProperty() {
        return null; // not database-backed
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        //noinspection DataFlowIssue not database-backed
        return null;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        //noinspection DataFlowIssue not database-backed
        return null;
    }

    @Override
    public ActivityKind normalizeType(final int rawType) {
        return ActivityKind.fromCode(rawType);
    }

    @Override
    public int toRawActivityKind(final ActivityKind activityKind) {
        return activityKind.getCode();
    }

    @Override
    public float normalizeIntensity(final int rawIntensity) {
        return rawIntensity;
    }

    @Override
    public T createActivitySample() {
        throw new UnsupportedOperationException("read-only sample provider");
    }
}
