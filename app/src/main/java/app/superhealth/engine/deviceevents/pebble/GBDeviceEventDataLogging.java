/*  Copyright (C) 2017-2024 Andreas Shimokawa

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

import java.util.UUID;

import app.superhealth.engine.deviceevents.GBDeviceEvent;
import app.superhealth.engine.impl.GBDevice;

public class GBDeviceEventDataLogging extends GBDeviceEvent {
    public static final int COMMAND_RECEIVE_DATA = 1;
    public static final int COMMAND_FINISH_SESSION = 2;

    public int command;
    public UUID appUUID;
    public long timestamp;
    public long tag;
    public byte pebbleDataType;
    public Object[] data;

    @Override
    public void evaluate(final Context context, final GBDevice device) {
        // FIXME: Pebble-specific, handled in support class
    }
}
