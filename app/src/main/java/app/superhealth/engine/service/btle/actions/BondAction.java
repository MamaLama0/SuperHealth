/*  Copyright (C) 2023-2026 Andreas Böhler, Thomas Kuehne

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
package app.superhealth.engine.service.btle.actions;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.impl.GBDeviceCandidate;
import app.superhealth.engine.util.AndroidUtils;
import app.superhealth.engine.util.BondingInterface;
import app.superhealth.engine.util.BondingUtil;

public class BondAction extends PlainAction implements BondingInterface {
    private GBDeviceCandidate mCandidate;
    private final BroadcastReceiver pairingReceiver = BondingUtil.getPairingReceiver(this);
    private final BroadcastReceiver bondingReceiver = BondingUtil.getBondingReceiver(this);

    @Override
    public void onBondingComplete(boolean success) {
        unregisterBroadcastReceivers();
    }

    @Override
    public GBDeviceCandidate getCurrentTarget() {
        return mCandidate;
    }

    @Override
    public boolean getAttemptToConnect() {
        return false;
    }

    @Override
    public void unregisterBroadcastReceivers() {
        AndroidUtils.safeUnregisterBroadcastReceiver(LocalBroadcastManager.getInstance(GBApplication.getContext()), pairingReceiver);
        AndroidUtils.safeUnregisterBroadcastReceiver(GBApplication.getContext(), bondingReceiver);
    }

    @Override
    public void registerBroadcastReceivers() {
        LocalBroadcastManager.getInstance(GBApplication.getContext()).registerReceiver(pairingReceiver, new IntentFilter(GBDevice.ACTION_DEVICE_CHANGED));
        ContextCompat.registerReceiver(getContext(), bondingReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED);
    }

    @Override
    public Context getContext() {
        return GBApplication.getContext();
    }

    @Override
    public boolean run(@NonNull BluetoothGatt gatt) {
        BluetoothDevice device = gatt.getDevice();
        mCandidate = new GBDeviceCandidate(device, GBDevice.RSSI_UNKNOWN, null, null);
        BondingUtil.tryBondThenComplete(this, device);
        return true;
    }
}
