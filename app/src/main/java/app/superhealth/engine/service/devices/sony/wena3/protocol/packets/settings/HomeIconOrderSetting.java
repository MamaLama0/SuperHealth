/*  Copyright (C) 2023-2024 akasaka / Genjitsu Labs

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
package app.superhealth.engine.service.devices.sony.wena3.protocol.packets.settings;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import app.superhealth.engine.service.devices.sony.wena3.protocol.Wena3Packetable;
import app.superhealth.engine.service.devices.sony.wena3.protocol.packets.settings.defines.HomeIconId;

public class HomeIconOrderSetting implements Wena3Packetable {
    public final HomeIconId left;
    public final HomeIconId center;
    public final HomeIconId right;

    public HomeIconOrderSetting(HomeIconId left, HomeIconId center, HomeIconId right) {
        this.left = left;
        this.center = center;
        this.right = right;
    }

    @Override
    public byte[] toByteArray() {
        return ByteBuffer.allocate(7)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) 0x1B)
                .putShort(left.value)
                .putShort(center.value)
                .putShort(right.value)
                .array();
    }
}

