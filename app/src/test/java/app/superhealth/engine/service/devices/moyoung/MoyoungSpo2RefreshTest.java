/*  Copyright (C) 2026 oddballza

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
package app.superhealth.engine.service.devices.moyoung;

import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.atomic.AtomicBoolean;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.devices.moyoung.MoyoungConstants;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.test.TestBase;

/**
 * A blood oxygen measurement has to announce itself, or the dashboard keeps showing the
 * previous value until it is refreshed by hand.
 */
public class MoyoungSpo2RefreshTest extends TestBase {
    private MoyoungDeviceSupport support;
    private BluetoothGattCharacteristic dataIn;

    @Before
    public void setUpSupport() {
        final GBDevice device = createDummyGDevice("00:00:00:00:00:02");
        support = new MoyoungDeviceSupport();
        support.setContext(device, BluetoothAdapter.getDefaultAdapter(), getContext());
        dataIn = new BluetoothGattCharacteristic(MoyoungConstants.UUID_CHARACTERISTIC_DATA_IN,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
    }

    @Test
    public void bloodOxygenMeasurementSignalsNewData() {
        final AtomicBoolean signalled = new AtomicBoolean(false);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                signalled.set(true);
            }
        };
        final LocalBroadcastManager broadcasts = LocalBroadcastManager.getInstance(getContext());
        broadcasts.registerReceiver(receiver, new IntentFilter(GBApplication.ACTION_NEW_DATA));

        try {
            final byte[] packet = MoyoungPacketOut.buildPacket(
                    20, MoyoungConstants.CMD_TRIGGER_MEASURE_BLOOD_OXYGEN, new byte[]{(byte) 97});
            assertTrue("the packet was not handled", support.onCharacteristicChanged(null, dataIn, packet));
            // the local broadcast is posted to the main looper, which does not run on its own here
            ShadowLooper.shadowMainLooper().idle();
            assertTrue("no new-data broadcast, so the dashboard will not refresh", signalled.get());
        } finally {
            broadcasts.unregisterReceiver(receiver);
        }
    }
}
