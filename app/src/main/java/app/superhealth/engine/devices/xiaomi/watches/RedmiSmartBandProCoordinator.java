/*  Copyright (C) 2024 José Rebelo

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
import app.superhealth.engine.capabilities.password.PasswordCapabilityImpl;
import app.superhealth.engine.devices.DeviceCoordinator;
import app.superhealth.engine.devices.xiaomi.XiaomiCoordinator;
import app.superhealth.engine.impl.GBDevice;

public class RedmiSmartBandProCoordinator extends XiaomiCoordinator {
    @Override
    public boolean isExperimental() {
        return true;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^Redmi Band Pro [A-Z0-9]{4}$");
    }

    @Override
    public String getManufacturer() {
        return "Redmi";
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_redmi_smart_band_pro;
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_default;
    }

    @Override
    public PasswordCapabilityImpl.Mode getPasswordCapability() {
        return PasswordCapabilityImpl.Mode.NUMBERS_4_DIGITS_0_TO_9;
    }

    @Override
    public boolean checkDecryptionMac() {
        // Sometimes it fails to decrypt with an invalid mac
        // ticket #3766
        return false;
    }

    @Override
    public boolean supportsAlarms() {
        // Crashes when requesting alarms
        return false;
    }

    @Override
    public boolean supportsPai(@NonNull GBDevice device) {
        // Does not support vitality score
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
            new Size(110, 208)
        );
    }

    @Override
    public Size getScreenSize() {
        return new Size(194, 368);
    }
}
