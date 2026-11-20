package app.superhealth.engine.service.devices.beurer;

import app.superhealth.engine.service.devices.generic_bp.GenericBloodPressureSupport;

public class BeurerBm59Support extends GenericBloodPressureSupport {
    @Override
    protected boolean shouldSyncTime() {
        return true;
    }
}
