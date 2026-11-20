package app.superhealth.engine.devices.huawei;

import android.content.Context;
import android.net.Uri;

import app.superhealth.engine.devices.GpxRouteInstallHandler;
import app.superhealth.engine.impl.GBDevice;

public class HuaweiGpxRouteInstallHandler extends GpxRouteInstallHandler {
    public HuaweiGpxRouteInstallHandler(final Uri uri, final Context context) {
        super(uri, context);
    }

    @Override
    protected boolean isCompatible(final GBDevice device) {
        return HuaweiDeviceStateManager.get(device).supportsRouteUpload();
    }
}
