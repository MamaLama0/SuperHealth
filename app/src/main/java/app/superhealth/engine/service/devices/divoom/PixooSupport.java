/*  Copyright (C) 2023-2024 Andreas Shimokawa

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

package app.superhealth.engine.service.devices.divoom;

import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;

import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.btbr.TransactionBuilder;
import app.superhealth.engine.service.serial.AbstractSerialDeviceSupportV2;

public class PixooSupport extends AbstractSerialDeviceSupportV2<PixooProtocol> {
    @Override
    protected PixooProtocol createDeviceProtocol() {
        return new PixooProtocol(getDevice());
    }

    @Override
    public void onInstallApp(final Uri uri, @NonNull final Bundle options) {
        final TransactionBuilder builder = createTransactionBuilder("show frame");
        builder.write(mDeviceProtocol.encodeShowFrame(uri));
        builder.queue();
    }

    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        builder.write(mDeviceProtocol.encodeReqestAlarms());
        builder.setDeviceState(GBDevice.State.INITIALIZED);
        return builder;
    }
}
