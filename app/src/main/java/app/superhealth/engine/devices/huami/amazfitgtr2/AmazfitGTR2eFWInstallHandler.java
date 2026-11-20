/*  Copyright (C) 2021-2024 Andreas Shimokawa

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
package app.superhealth.engine.devices.huami.amazfitgtr2;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.miband.AbstractMiBandFWHelper;
import app.superhealth.engine.devices.miband.AbstractMiBandFWInstallHandler;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.DeviceType;

class AmazfitGTR2eFWInstallHandler extends AbstractMiBandFWInstallHandler {
    AmazfitGTR2eFWInstallHandler(final Uri uri, final Context context) {
        super(uri, context);
    }

    @Override
    protected String getFwUpgradeNotice() {
        return mContext.getString(R.string.fw_upgrade_notice_amazfitgtr, helper.getHumanFirmwareVersion());
    }

    @Override
    protected AbstractMiBandFWHelper createHelper(final Uri uri, final Context context) throws IOException {
        return new AmazfitGTR2eFWHelper(uri, context);
    }

    @Override
    protected boolean isSupportedDeviceType(final GBDevice device) {
        return device.getType() == DeviceType.AMAZFITGTR2E;
    }
}
