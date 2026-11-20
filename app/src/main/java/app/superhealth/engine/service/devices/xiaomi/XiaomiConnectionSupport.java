/*  Copyright (C) 2023 José Rebelo, Yoran Vulker

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
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package app.superhealth.engine.service.devices.xiaomi;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import androidx.annotation.Nullable;

import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.proto.xiaomi.XiaomiProto;

public abstract class XiaomiConnectionSupport {
    public abstract boolean connect();
    public void onAuthSuccess() {}
    public abstract void onUploadProgress(int textRsrc, int progressPercent, boolean ongoing);
    public abstract void runOnQueue(String taskName, Runnable run);
    public abstract void dispose();
    public abstract void setContext(final GBDevice device, final BluetoothAdapter adapter, final Context context);
    public abstract void sendCommand(final String taskName, final XiaomiProto.Command command);
    public abstract void sendDataChunk(final String taskName, final byte[] chunk, @Nullable final XiaomiSendCallback callback);
    public void setAutoReconnect(final boolean enabled) {}
}
