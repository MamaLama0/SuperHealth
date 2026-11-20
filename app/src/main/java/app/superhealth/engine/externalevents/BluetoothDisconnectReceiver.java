/*  Copyright (C) 2016-2024 Andreas Shimokawa, Daniel Dakhno, José Rebelo

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
package app.superhealth.engine.externalevents;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.impl.GBDevice;


public class BluetoothDisconnectReceiver extends BroadcastReceiver {
    private static final Logger LOG = LoggerFactory.getLogger(BluetoothDisconnectReceiver.class);

    public BluetoothDisconnectReceiver() {
    }


    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String action = intent.getAction();
        if (action == null) {
            return;
        }

        if (!action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED) || !intent.hasExtra(BluetoothDevice.EXTRA_DEVICE)) {
            return;
        }

        final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (device == null) {
            LOG.error("Got no device for {}", action);
            return;
        }

        LOG.info("disconnect detected from {}", device.getAddress());

        final GBDevice gbDevice = GBApplication.app().getDeviceManager().getDeviceByAddress(device.getAddress());
        if (gbDevice == null) {
            LOG.info("Connected device {} unknown", device.getAddress());
            return;
        }
        GBApplication.deviceService(gbDevice).disconnect();

    }
}
