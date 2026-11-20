/*  Copyright (C) 2015-2024 Andreas Shimokawa, Carsten Pfeiffer

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
package app.superhealth.engine.deviceevents;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.UUID;

import app.superhealth.engine.impl.GBDevice;

public class GBDeviceEventAppMessage extends GBDeviceEvent {
    public static int TYPE_APPMESSAGE = 0;
    public static int TYPE_ACK = 1;
    public static int TYPE_NACK = 2;

    public int type;
    public UUID appUUID;
    public int id;
    public String message;

    @NonNull
    @Override
    public String toString() {
        return "GBDeviceEventAppMessage{" +
                "type=" + type +
                ", appUUID=" + appUUID +
                ", message='" + message + '\'' +
                ", id=" + id +
                '}';
    }

    @Override
    public void evaluate(final Context context, final GBDevice device) {
        // FIXME: Pebble-specific, handled in support class
    }
}
