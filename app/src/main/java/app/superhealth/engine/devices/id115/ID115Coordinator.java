/*  Copyright (C) 2018-2025 Andreas Shimokawa, Damien Gaignon, Daniel Dakhno,
    Daniele Gobbetti, José Rebelo, Petr Vaněk, Vadim Kaushan, Thomas Kuehne

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
package app.superhealth.engine.devices.id115;

import android.bluetooth.le.ScanFilter;
import android.os.ParcelUuid;

import java.util.Collection;
import java.util.Collections;

import androidx.annotation.NonNull;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator;
import app.superhealth.engine.devices.DeviceCoordinator;
import app.superhealth.engine.devices.SampleProvider;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.impl.GBDeviceCandidate;
import app.superhealth.engine.model.ActivitySample;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.devices.id115.ID115Support;

public class ID115Coordinator extends AbstractBLEDeviceCoordinator {
    @NonNull
    @Override
    public Collection<? extends ScanFilter> createBLEScanFilters() {
        ParcelUuid service = new ParcelUuid(ID115Constants.UUID_SERVICE_ID115);
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(service).build();
        return Collections.singletonList(filter);
    }

    @Override
    public boolean supports(GBDeviceCandidate candidate) {
        return candidate.supportsService(ID115Constants.UUID_SERVICE_ID115);
    }

    @Override
    public int getBondingStyle(){
        return BONDING_STYLE_NONE;
    }

    @Override
    public boolean supportsDataFetching(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsActivityTracking(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public SampleProvider<? extends ActivitySample> getSampleProvider(GBDevice device, DaoSession session) {
        return new ID115SampleProvider(device, session);
    }

    @Override
    public String getManufacturer() {
        return "VeryFit";
    }

    @Override
    public int[] getSupportedDeviceSpecificSettings(GBDevice device) {
        return new int[]{
                R.xml.devicesettings_wearlocation,
                R.xml.devicesettings_screenorientation,
                R.xml.devicesettings_transliteration
        };
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return ID115Support.class;
    }


    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_id115;
    }

    @Override
    public DeviceCoordinator.DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceCoordinator.DeviceKind.FITNESS_BAND;
    }


    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_h30_h10;
    }

    @Override
    public boolean supportsAppNotifications(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsPhoneCalls(@NonNull final GBDevice device) {
        return true;
    }
}
