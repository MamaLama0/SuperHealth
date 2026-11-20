/*  Copyright (C) 2023-2025 Daniel Dakhno, José Rebelo

    This file is part of SuperHealth.

    SuperHealth is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SuperHealth is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package app.superhealth.engine.devices.huami.zeppos;

import android.content.Context;
import android.net.Uri;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.superhealth.engine.devices.DeviceCoordinator;
import app.superhealth.engine.devices.GpxRouteInstallHandler;
import app.superhealth.engine.impl.GBDevice;

public class ZeppOsGpxRouteInstallHandler extends GpxRouteInstallHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ZeppOsGpxRouteInstallHandler.class);

    public ZeppOsGpxRouteInstallHandler(final Uri uri, final Context context) {
        super(uri, context);
    }

    @Override
    protected boolean isCompatible(GBDevice device) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        if (!(coordinator instanceof ZeppOsCoordinator zeppOsCoordinator)) {
            LOG.warn("Coordinator is not a ZeppOsCoordinator: {}", coordinator.getClass());
            return false;
        }
        return zeppOsCoordinator.supportsGpxUploads(device);
    }
}
