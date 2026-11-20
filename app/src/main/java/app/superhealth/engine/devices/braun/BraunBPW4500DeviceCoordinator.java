/*
    Copyright (C) 2026 Christian Breiteneder

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
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package app.superhealth.engine.devices.braun;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator;
import app.superhealth.engine.devices.GenericBloodPressureSampleProvider;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.GBApplication;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.impl.GBDeviceCandidate;
import app.superhealth.engine.model.DeviceType;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.devices.braun.BraunBPW4500DeviceSupport;

public class BraunBPW4500DeviceCoordinator extends AbstractBLEDeviceCoordinator {

    @Nullable
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^BPW4500$");
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(GBDevice device) {
        return BraunBPW4500DeviceSupport.class;
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_braun_bpw4500;
    }

    @Override
    public DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceKind.BLOOD_PRESSURE_METER;
    }

    @Override
    public int getBondingStyle() {
        return BONDING_STYLE_NONE;
    }

    @Override
    public boolean suggestUnbindBeforePair() {
        return false;
    }

    @Override
    public boolean supportsBloodPressureMeasurement(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public GenericBloodPressureSampleProvider getBloodPressureSampleProvider(
            final GBDevice device, final DaoSession session) {
        return new GenericBloodPressureSampleProvider(device, session);
    }

    @Override
    public String getManufacturer() {
        return "Braun";
    }

    @Override
    public GBDevice createDevice(final GBDeviceCandidate candidate, final DeviceType deviceType) {
        final GBDevice device = super.createDevice(candidate, deviceType);
        device.setAlias(GBApplication.getContext().getString(R.string.devicetype_braun_bpw4500));
        return device;
    }
}