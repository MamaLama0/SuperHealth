/*  Copyright (C) 2025 Me7c7

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

package app.superhealth.engine.devices.huawei.freebuds6;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.huawei.HuaweiFreebudsCoordinator;
import app.superhealth.engine.devices.huawei.HuaweiHeadphonesCapabilities;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.devices.huawei.HuaweiFreebudsSupport;

public class HuaweiFreebuds6Coordinator extends HuaweiFreebudsCoordinator {

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("huawei freebuds 6", Pattern.CASE_INSENSITIVE);
    }

    @Override
    public Set<HuaweiHeadphonesCapabilities> getCapabilities() {
        return new HashSet<>(Arrays.asList(
                HuaweiHeadphonesCapabilities.InEarDetection,
                HuaweiHeadphonesCapabilities.BetterAudioQuality
        ));
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return HuaweiFreebudsSupport.class;
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_huawei_freebuds_6;
    }
}
