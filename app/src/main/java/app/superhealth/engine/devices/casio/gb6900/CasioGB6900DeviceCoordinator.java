/*  Copyright (C) 2020-2024 Andreas Böhler, Damien Gaignon, Daniel Dakhno,
    Johannes Krude, José Rebelo, Petr Vaněk

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
/*  Based on code from BlueWatcher, https://github.com/masterjc/bluewatcher */
package app.superhealth.engine.devices.casio.gb6900;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.SampleProvider;
import app.superhealth.engine.devices.casio.CasioDeviceCoordinator;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.ActivitySample;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.devices.casio.gb6900.CasioGB6900DeviceSupport;

public class CasioGB6900DeviceCoordinator extends CasioDeviceCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("CASIO.*(6900B|5600B|STB-1000).*");
    }

    @Override
    public int getBondingStyle(){
        return BONDING_STYLE_LAZY;
    }

    @Override
    public boolean supportsFindDevice(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsAppNotifications(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsPhoneCalls(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsCallControl(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsMusicInfo(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public SampleProvider<? extends ActivitySample> getSampleProvider(GBDevice device, DaoSession session) {
        return null;
    }

    @Override
    public int getAlarmSlotCount(GBDevice device) {
        return 5; // 4 regular and one snooze
    }

    @Override
    public int[] getSupportedDeviceSpecificSettings(GBDevice device) {
        return new int[] {
                R.xml.devicesettings_disconnectnotification_noshed,
                R.xml.devicesettings_transliteration
        };
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return CasioGB6900DeviceSupport.class;
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_casiogb6900;
    }
}
