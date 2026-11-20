/*  Copyright (C) 2021-2024 Arjan Schrijver, Petr Vaněk

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
package app.superhealth.engine.service.devices.galaxy_buds;

import java.util.UUID;

import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.DeviceType;
import app.superhealth.engine.service.btbr.TransactionBuilder;
import app.superhealth.engine.service.serial.AbstractHeadphoneSerialDeviceSupportV2;

public class GalaxyBudsDeviceSupport extends AbstractHeadphoneSerialDeviceSupportV2<GalaxyBudsProtocol> {
    @Override
    protected GalaxyBudsProtocol createDeviceProtocol() {
        return new GalaxyBudsProtocol(getDevice());
    }

    @Override
    protected UUID getSupportedService() {
        if (getDevice().getType().equals(DeviceType.GALAXY_BUDS)) {
            return GalaxyBudsProtocol.UUID_GALAXY_BUDS_DEVICE_CTRL;
        }
        return GalaxyBudsProtocol.UUID_GALAXY_BUDS_LIVE_DEVICE_CTRL;
    }

    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        builder.setDeviceState(GBDevice.State.INITIALIZED);

        return builder;
    }
}
