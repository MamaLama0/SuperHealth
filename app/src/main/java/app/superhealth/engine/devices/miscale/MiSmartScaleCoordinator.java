/*  Copyright (C) 2024 Severin von Wnuck-Lipinski

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
package app.superhealth.engine.devices.miscale;

import androidx.annotation.NonNull;

import java.util.EnumSet;
import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettings;
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator;
import app.superhealth.engine.devices.MiScaleWeightSampleProvider;
import app.superhealth.engine.devices.TimeSampleProvider;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.WeightSample;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.ServiceDeviceSupport;
import app.superhealth.engine.service.devices.miscale.MiSmartScaleDeviceSupport;

public class MiSmartScaleCoordinator extends AbstractBLEDeviceCoordinator {
    @Override
    public String getManufacturer() {
        // Actual manufacturer is Huami
        return "Xiaomi";
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("MI SCALE2");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_mismartscale;
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_miscale;
    }

    @Override
    public int getBatteryCount(final GBDevice device) {
        return 0;
    }

    @Override
    public int getBondingStyle() {
        return BONDING_STYLE_NONE;
    }

    @Override
    public DeviceSpecificSettings getDeviceSpecificSettings(final GBDevice device) {
        final DeviceSpecificSettings settings = new DeviceSpecificSettings();

        settings.addRootScreen(R.xml.devicesettings_mismartscale);

        return settings;
    }

    @Override
    public TimeSampleProvider<? extends WeightSample> getWeightSampleProvider(final GBDevice device, final DaoSession session) {
        return new MiScaleWeightSampleProvider(device, session);
    }

    @Override
    public boolean supportsWeightMeasurement(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsCharts(@NonNull GBDevice device) {
        return true;
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return MiSmartScaleDeviceSupport.class;
    }

    @Override
    public EnumSet<ServiceDeviceSupport.Flags> getInitialFlags() {
        return EnumSet.noneOf(ServiceDeviceSupport.Flags.class);
    }

    @Override
    public DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceKind.SCALE;
    }
}
