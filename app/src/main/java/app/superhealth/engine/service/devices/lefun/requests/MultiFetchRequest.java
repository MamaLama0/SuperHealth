/*  Copyright (C) 2020-2024 Damien Gaignon, Yukai Li

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
package app.superhealth.engine.service.devices.lefun.requests;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.widget.Toast;

import androidx.annotation.StringRes;

import java.io.IOException;

import app.superhealth.engine.devices.lefun.LefunConstants;
import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.devices.lefun.LefunDeviceSupport;
import app.superhealth.engine.service.devices.miband.operations.OperationStatus;
import app.superhealth.engine.util.GB;

/**
 * Represents a request that receives several responses
 */
public abstract class MultiFetchRequest extends Request {
    /**
     * Instantiates a new MultiFetchRequest
     * @param support the device support
     */
    protected MultiFetchRequest(LefunDeviceSupport support) {
        super(support, null);
        removeAfterHandling = false;
    }

    protected int lastRecord = 0;
    protected int totalRecords = -1;

    @Override
    protected void prePerform() throws IOException {
        super.prePerform();
        builder = performInitialized(getClass().getSimpleName());
        if (getDevice().isBusy()) {
            throw new IllegalStateException("Device is busy");
        }
        builder.setBusyTask(getOperationName());
        builder.sleep(1000); // Wait a bit (after previous operation), or device sometimes won't respond
    }

    @Override
    protected void operationFinished() {
        if (lastRecord == totalRecords)
            removeAfterHandling = true;
        try {
            super.operationFinished();
            TransactionBuilder builder = performInitialized("Finishing operation");
            builder.setCallback(null);
            builder.queue();
        } catch (IOException e) {
            GB.toast(getContext(), "Failed to reset callback", Toast.LENGTH_SHORT,
                    GB.ERROR, e);
        }
        unsetBusy();
        operationStatus = OperationStatus.FINISHED;
        getSupport().runNextQueuedRequest();
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] data) {
        if (characteristic.getUuid().equals(LefunConstants.UUID_CHARACTERISTIC_LEFUN_NOTIFY)) {
            // Parse response
            if (data.length >= LefunConstants.CMD_HEADER_LENGTH && data[0] == LefunConstants.CMD_RESPONSE_ID) {
                try {
                    handleResponse(data);
                    return true;
                } catch (IllegalArgumentException e) {
                    log("Failed to handle response");
                    operationFinished();
                }
            }

            getSupport().logMessageContent(data);
            log("Invalid response received");
            return false;
        }

        return super.onCharacteristicChanged(gatt, characteristic, data);
    }

    @Override
    public boolean isSelfQueue() {
        return true;
    }

    /**
     * Gets the display operation name
     * @return the operation name
     */
    protected abstract @StringRes int getOperationName();
}
