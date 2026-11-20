/*  Copyright (C) 2025 José Rebelo

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

import android.app.Activity;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.superhealth.engine.R;
import app.superhealth.engine.activities.install.FwAppInstallerActivity;
import app.superhealth.engine.activities.install.InstallActivity;
import app.superhealth.engine.devices.DeviceCoordinator;
import app.superhealth.engine.devices.InstallHandler;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.GenericItem;
import app.superhealth.engine.service.devices.huami.zeppos.operations.ZeppOsMapsFile;

public class ZeppOsMapsInstallHandler implements InstallHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ZeppOsMapsInstallHandler.class);

    protected final Context mContext;
    private final ZeppOsMapsFile file;

    public ZeppOsMapsInstallHandler(final Uri uri, final Context context) {
        this.mContext = context;

        file = new ZeppOsMapsFile(uri, context);
    }

    @NonNull
    @Override
    public Class<? extends Activity> getInstallActivity() {
        return FwAppInstallerActivity.class;
    }

    @Override
    public boolean isValid() {
        return file.isValid();
    }

    @Override
    public void validateInstallation(@NonNull final InstallActivity installActivity, @NonNull final GBDevice device) {
        if (device.isBusy()) {
            installActivity.setInfoText(device.getBusyTask());
            installActivity.setInstallEnabled(false);
            installActivity.setCloseEnabled(true);
            return;
        }

        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        if (!(coordinator instanceof ZeppOsCoordinator zeppOsCoordinator)) {
            LOG.warn("Coordinator is not a ZeppOsCoordinator: {}", coordinator.getClass());
            installActivity.setInfoText(mContext.getString(R.string.fwapp_install_device_not_supported));
            installActivity.setInstallEnabled(false);
            installActivity.setCloseEnabled(true);
            return;
        }
        if (!zeppOsCoordinator.supportsMaps(device) ||
            (zeppOsCoordinator.supportsWifiHotspot(device) && !zeppOsCoordinator.supportsWifiFtp(device))) {
            installActivity.setInfoText(mContext.getString(R.string.fwapp_install_device_not_supported));
            installActivity.setInstallEnabled(false);
            installActivity.setCloseEnabled(true);
            return;
        }

        if (!device.isInitialized()) {
            installActivity.setInfoText(mContext.getString(R.string.fwapp_install_device_not_ready));
            installActivity.setInstallEnabled(false);
            installActivity.setCloseEnabled(true);
            return;
        }

        final GenericItem fwItem = createInstallItem(device);
        fwItem.setIcon(coordinator.getDefaultIconResource());

        final StringBuilder builder = new StringBuilder();
        final String map = mContext.getString(R.string.menuitem_map);
        builder.append(mContext.getString(R.string.fw_upgrade_notice, map));
        builder.append("\n\n").append(mContext.getString(R.string.zepp_os_install_map_instructions));
        installActivity.setInfoText(builder.toString());
        installActivity.setInstallItem(fwItem);
        installActivity.setInstallEnabled(true);
    }

    @Override
    public void onStartInstall(@NonNull final GBDevice device) {
    }

    public ZeppOsMapsFile getFile() {
        return file;
    }

    private GenericItem createInstallItem(final GBDevice device) {
        DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final String firmwareName = mContext.getString(
                R.string.installhandler_firmware_name,
                mContext.getString(coordinator.getDeviceNameResource()),
                mContext.getString(R.string.menuitem_map),
                ""
        );
        return new GenericItem(firmwareName);
    }
}
