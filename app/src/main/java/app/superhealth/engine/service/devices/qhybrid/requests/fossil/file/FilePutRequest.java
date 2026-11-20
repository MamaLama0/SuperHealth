/*  Copyright (C) 2019-2024 Andreas Shimokawa, Arjan Schrijver, Daniel Dakhno

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
package app.superhealth.engine.service.devices.qhybrid.requests.fossil.file;

import android.bluetooth.BluetoothGattCharacteristic;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.UUID;
import java.util.zip.CRC32;

import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.devices.qhybrid.adapter.fossil.FossilWatchAdapter;
import app.superhealth.engine.service.devices.qhybrid.file.FileHandle;
import app.superhealth.engine.service.devices.qhybrid.requests.fossil.FossilRequest;
import app.superhealth.engine.service.devices.qhybrid.requests.fossil_hr.file.ResultCode;
import app.superhealth.engine.util.CRC32C;

public class FilePutRequest extends FilePutRawRequest {
    public FilePutRequest(FileHandle fileHandle, byte[] file, FossilWatchAdapter adapter) {
        super(fileHandle, createFilePayload(fileHandle, file, adapter.getSupportedFileVersion(fileHandle)), adapter);
    }

    private static byte[] createFilePayload(FileHandle fileHandle, byte[] file, short fileVersion){
        ByteBuffer buffer = ByteBuffer.allocate(file.length + 12 + 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.putShort(fileHandle.getHandle());
        buffer.putShort(fileVersion);
        if (fileHandle == FileHandle.REPLY_MESSAGES) {
            buffer.put(new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x0d, (byte) 0x00});
        } else {
            buffer.putInt(0);
        }
        buffer.putInt(file.length);

        buffer.put(file);

        CRC32C crc = new CRC32C();

        crc.update(file,0,file.length);
        buffer.putInt((int) crc.getValue());

        return buffer.array();
    }
}
