/*  Copyright (C) 2020-2024 Andreas Shimokawa, José Rebelo

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
package app.superhealth.engine.service.devices.huami.amazfitgtr;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;

import app.superhealth.engine.devices.huami.HuamiCoordinator;
import app.superhealth.engine.devices.huami.HuamiFWHelper;
import app.superhealth.engine.devices.huami.amazfitgtr.AmazfitGTRLiteFWHelper;
import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.devices.huami.amazfitgts.AmazfitGTSSupport;
import app.superhealth.engine.service.devices.huami.operations.update.UpdateFirmwareOperation;
import app.superhealth.engine.service.devices.huami.operations.update.UpdateFirmwareOperationNew;
import app.superhealth.engine.util.Version;

public class AmazfitGTRLiteSupport extends AmazfitGTSSupport {

    @Override
    public HuamiFWHelper createFWHelper(Uri uri, Context context) throws IOException {
        return new AmazfitGTRLiteFWHelper(uri, context);
    }

    // override to skip requesting GPS version
    @Override
    public void phase2Initialize(TransactionBuilder builder) {
        super.phase2Initialize(builder);

        if (HuamiCoordinator.getOverwriteSettingsOnConnection(getDevice().getAddress())) {
            setLanguage(builder);
        }
    }

    @Override
    public UpdateFirmwareOperation createUpdateFirmwareOperation(Uri uri) {
        return new UpdateFirmwareOperationNew(uri, this);
    }

    @Override
    protected void handleDeviceInfo(app.superhealth.engine.service.btle.profiles.deviceinfo.DeviceInfo info) {
        super.handleDeviceInfo(info);
        if (gbDevice.getFirmwareVersion() != null) {
            Version version = new Version(gbDevice.getFirmwareVersion());
            if (version.compareTo(new Version("1.0.0.33")) >= 0) {
                mActivitySampleSize = 8;
            }
        }
    }
}
