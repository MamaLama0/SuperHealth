/*  Copyright (C) 2025 Benjamin Temple

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
package app.superhealth.engine.deviceevents.pebble;

import android.content.Context;

import androidx.annotation.NonNull;

import app.superhealth.engine.deviceevents.GBDeviceEvent;
import app.superhealth.engine.impl.GBDevice;

/**
 * Sent by the Pebble watch in response to a FirmwareUpdateStart system message.
 * The watch indicates whether it is ready to receive the firmware transfer.
 */
public class GBDeviceEventFirmwareUpdateStart extends GBDeviceEvent {
    public static final byte STATUS_STOPPED   = 0;
    public static final byte STATUS_STARTED   = 1;
    public static final byte STATUS_CANCELLED = 2;

    public byte status;

    public GBDeviceEventFirmwareUpdateStart(byte status) {
        this.status = status;
    }

    @Override
    public void evaluate(@NonNull final Context context, @NonNull final GBDevice device) {
        // Stub — handled in PebbleIoThread.evaluateGBDeviceEventPebble
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() + "status: " + status;
    }
}
