/*  Copyright (C) 2016-2024 Andreas Shimokawa, Carsten Pfeiffer, Daniele
    Gobbetti

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
package app.superhealth.engine.service.devices.pebble;

import java.util.UUID;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.util.preferences.DevicePrefs;

abstract class DatalogSessionPebbleHealth extends DatalogSession {

    private final GBDevice mDevice;
    private final DevicePrefs devicePrefs;

    DatalogSessionPebbleHealth(byte id, UUID uuid, int timestamp, int tag, byte itemType, short itemSize, GBDevice device) {
        super(id, uuid, timestamp, tag, itemType, itemSize);
        mDevice = device;
        devicePrefs = GBApplication.getDevicePrefs(mDevice);
    }

    public GBDevice getDevice() {
        return mDevice;
    }

    boolean isPebbleHealthEnabled() {
        return devicePrefs.getBoolean("pebble_sync_health", true);
    }

    boolean storePebbleHealthRawRecord() {
        return devicePrefs.getBoolean("pebble_health_store_raw", true);
    }
}