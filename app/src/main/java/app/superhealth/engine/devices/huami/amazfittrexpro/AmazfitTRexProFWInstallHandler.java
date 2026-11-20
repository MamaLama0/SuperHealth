/*  Copyright (C) 2021-2024 GeekosaurusR3x

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
package app.superhealth.engine.devices.huami.amazfittrexpro;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.miband.AbstractMiBandFWHelper;
import app.superhealth.engine.devices.miband.AbstractMiBandFWInstallHandler;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.DeviceType;

class AmazfitTRexProFWInstallHandler extends AbstractMiBandFWInstallHandler {
    AmazfitTRexProFWInstallHandler(final Uri uri, final Context context) {
        super(uri, context);
    }

    @Override
    protected String getFwUpgradeNotice() {
        return mContext.getString(R.string.fw_upgrade_notice_amazfit_trex, helper.getHumanFirmwareVersion());
    }

    @Override
    protected AbstractMiBandFWHelper createHelper(final Uri uri, final Context context) throws IOException {
        return new AmazfitTRexProFWHelper(uri, context);
    }

    @Override
    protected boolean isSupportedDeviceType(final GBDevice device) {
        return device.getType() == DeviceType.AMAZFITTREXPRO;
    }
}
