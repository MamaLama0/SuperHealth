/*  Copyright (C) 2019-2024 Daniel Dakhno

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
package app.superhealth.engine.service.devices.qhybrid.requests.fossil.alarm;

import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import app.superhealth.engine.service.devices.qhybrid.adapter.fossil.FossilWatchAdapter;
import app.superhealth.engine.service.devices.qhybrid.file.FileHandle;
import app.superhealth.engine.service.devices.qhybrid.requests.fossil.file.FileGetRequest;
import app.superhealth.engine.util.GB;

public class AlarmsGetRequest extends FileGetRequest {
    public AlarmsGetRequest(FossilWatchAdapter adapter) {
        super(FileHandle.ALARMS, adapter);
    }

    @Override
    public void handleFileData(byte[] fileData) {
        ByteBuffer buffer = ByteBuffer.wrap(fileData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        int length = fileData.length / 3;
        Alarm[] alarms = new Alarm[length];

        for (int i = 0; i < length; i++){
            byte[] alarmBytes = new byte[]{
                    buffer.get(),
                    buffer.get(),
                    buffer.get()
            };
            alarms[i] = Alarm.fromBytes(alarmBytes);
        }

        this.handleAlarms(alarms);
    }

    public void handleAlarms(Alarm[] alarms){
    }
}
