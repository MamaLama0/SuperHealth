/*  Copyright (C) 2024 Damien Gaignon, Martin.JM

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
package app.superhealth.engine.devices.huawei.packets;

/*
 * TODO: It isn't clear if this class should handle more at this point, and thus might need a
 *       different name later
 */

import app.superhealth.engine.devices.huawei.HuaweiPacket;
import app.superhealth.engine.devices.huawei.HuaweiTLV;

public class DisconnectNotification {
    public static final byte id = 0x0b;

    public static class DisconnectNotificationSetting {
        public static final byte id = 0x03;

        public static class Request extends HuaweiPacket {
            public Request(ParamsProvider paramsProvider, boolean enable) {
                super(paramsProvider);

                this.serviceId = DisconnectNotification.id;
                this.commandId = id;
                this.tlv = new HuaweiTLV()
                        .put(0x01, enable);
                this.complete = true;
            }
        }
    }
}
