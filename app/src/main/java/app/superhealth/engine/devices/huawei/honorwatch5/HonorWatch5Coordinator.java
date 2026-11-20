/*  Copyright (C) 2025 Vitalii Tomin

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
package app.superhealth.engine.devices.huawei.honorwatch5;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.huawei.HonorBRCoordinator;
import app.superhealth.engine.devices.huawei.HuaweiConstants;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.DeviceType;

public class HonorWatch5Coordinator extends HonorBRCoordinator {
    @Override
    public boolean isTransactionCrypted() {
        return false;
    }

    @Override
    public boolean supportsHiChainPake() {
        return true;
    }

    @Override
    public String getManufacturer() {
        return "Honor";
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile(HuaweiConstants.HO_WATCH5_NAME + ".*", Pattern.CASE_INSENSITIVE);
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_honor_watch5;
    }

    @Override
    public DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceKind.WATCH;
    }
}
