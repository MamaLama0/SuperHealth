/*  Copyright (C) 2026 Jan Petrlík

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
package app.superhealth.engine.service.devices.moondrop;

import static app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_MOONDROP_ANC_MODE;

import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.btbr.TransactionBuilder;
import app.superhealth.engine.service.serial.AbstractHeadphoneSerialDeviceSupportV2;

public class MoondropSpaceTravel2DeviceSupport extends AbstractHeadphoneSerialDeviceSupportV2<MoondropSpaceTravel2Protocol> {
    @Override
    protected MoondropSpaceTravel2Protocol createDeviceProtocol() {
        return new MoondropSpaceTravel2Protocol(getDevice());
    }

    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        builder.write(mDeviceProtocol.encodeGetEqualizerPreset());
        builder.write(mDeviceProtocol.encodeGetTouchActions());
        builder.write(mDeviceProtocol.encodeGetAudioCurationMode());
        builder.setDeviceState(GBDevice.State.INITIALIZED);

        return builder;
    }

    @Override
    public void onReadConfiguration(final String config) {
        if (PREF_MOONDROP_ANC_MODE.equals(config)) {
            final TransactionBuilder builder = createTransactionBuilder("read anc mode");
            builder.write(mDeviceProtocol.encodeGetAudioCurationMode());
            builder.queue();
        }
    }
}
