/*  Copyright (C) 2023-2024 José Rebelo

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
package app.superhealth.engine.devices.xiaomi.watches;

import android.util.Size;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.DeviceCoordinator;
import app.superhealth.engine.devices.huami.HuamiConst;
import app.superhealth.engine.devices.xiaomi.XiaomiCoordinator;
import app.superhealth.engine.impl.GBDevice;

public class MiBand7ProCoordinator extends XiaomiCoordinator {
    @Override
    public boolean isExperimental() {
        return true;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile(HuamiConst.XIAOMI_SMART_BAND7_PRO_NAME + ".*");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_miband7pro;
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_default;
    }

    @Override
    public boolean supportsPai(@NonNull GBDevice device) {
        // no PAI nor vitality score
        return false;
    }

    @Override
    public DeviceCoordinator.DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceCoordinator.DeviceKind.FITNESS_BAND;
    }

    @Override
    public List<Size> getWatchfacePreviewSizes() {
        return List.of(
            // Scaled-down screen resolution, seen in an unofficial watchface
            new Size(220, 358)
        );
    }

    @Override
    public Size getScreenSize() {
        return new Size(280, 456);
    }
}
