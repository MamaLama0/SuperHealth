/*  Copyright (C) 2024 Jonathan Gobbo

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
package app.superhealth.engine.service.devices.redmibuds;

import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.btbr.TransactionBuilder;
import app.superhealth.engine.service.serial.AbstractHeadphoneSerialDeviceSupportV2;

public class RedmiBudsDeviceSupport extends AbstractHeadphoneSerialDeviceSupportV2<RedmiBudsProtocol> {
    public RedmiBudsDeviceSupport() {
        addSupportedService(RedmiBudsProtocol.UUID_DEVICE_CTRL);
    }

    @Override
    protected RedmiBudsProtocol createDeviceProtocol() {
        return new RedmiBudsProtocol(getDevice());
    }

    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        builder.write(mDeviceProtocol.encodeStartAuthentication());
        builder.setDeviceState(GBDevice.State.INITIALIZING);
        return builder;
    }
}
