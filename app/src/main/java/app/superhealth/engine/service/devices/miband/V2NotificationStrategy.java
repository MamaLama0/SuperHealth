/*  Copyright (C) 2015-2024 Carsten Pfeiffer, Daniele Gobbetti

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
package app.superhealth.engine.service.devices.miband;

import android.bluetooth.BluetoothGattCharacteristic;

import androidx.annotation.Nullable;
import app.superhealth.engine.devices.miband.VibrationProfile;
import app.superhealth.engine.service.btle.AbstractBTLESingleDeviceSupport;
import app.superhealth.engine.service.btle.BtLEAction;
import app.superhealth.engine.service.btle.GattCharacteristic;
import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.devices.common.SimpleNotification;

public class V2NotificationStrategy<T extends AbstractBTLESingleDeviceSupport> implements NotificationStrategy {
    private final T support;

    public V2NotificationStrategy(T support) {
        this.support = support;
    }

    protected T getSupport() {
        return support;
    }

    @Override
    public void sendDefaultNotification(TransactionBuilder builder, SimpleNotification simpleNotification, BtLEAction extraAction) {
        VibrationProfile profile = VibrationProfile.getProfile(VibrationProfile.ID_MEDIUM, (short) 3);
        sendCustomNotification(profile, simpleNotification, extraAction, builder);
    }

    protected void sendCustomNotification(VibrationProfile vibrationProfile, @Nullable SimpleNotification simpleNotification, BtLEAction extraAction, TransactionBuilder builder) {
        //use the new alert characteristic
        BluetoothGattCharacteristic alert = support.getCharacteristic(GattCharacteristic.CHARACTERISTIC_ALERT_LEVEL.uuid);
        for (short i = 0; i < vibrationProfile.getRepeat(); i++) {
            int[] onOffSequence = vibrationProfile.getOnOffSequence();
            for (int j = 0; j < onOffSequence.length; j++) {
                int on = onOffSequence[j];
                on = Math.min(500, on); // longer than 500ms is not possible
                builder.write(alert, new byte[]{GattCharacteristic.MILD_ALERT}); //MILD_ALERT lights up GREEN leds, HIGH_ALERT lights up RED leds
//                builder.sleep(on);
//                builder.write(alert, new byte[]{GattCharacteristic.HIGH_ALERT});
                builder.sleep(on);
                builder.write(alert, new byte[]{GattCharacteristic.NO_ALERT});

                if (++j < onOffSequence.length) {
                    int off = Math.max(onOffSequence[j], 25); // wait at least 25ms
                    builder.sleep(off);
                }

                if (extraAction != null) {
                    builder.add(extraAction);
                }
            }
        }
    }

    @Override
    public void sendCustomNotification(VibrationProfile vibrationProfile, SimpleNotification simpleNotification, int flashTimes, int flashColour, int originalColour, long flashDuration, BtLEAction extraAction, TransactionBuilder builder) {
        // all other parameters are unfortunately not supported anymore ;-(
        sendCustomNotification(vibrationProfile, simpleNotification, extraAction, builder);
    }

    @Override
    public void stopCurrentNotification(TransactionBuilder builder) {
        builder.write(GattCharacteristic.CHARACTERISTIC_ALERT_LEVEL.uuid, new byte[]{GattCharacteristic.NO_ALERT});
    }
}
