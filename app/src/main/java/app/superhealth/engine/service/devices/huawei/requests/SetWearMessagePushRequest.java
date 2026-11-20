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
package app.superhealth.engine.service.devices.huawei.requests;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst;
import app.superhealth.engine.devices.huawei.HuaweiPacket;
import app.superhealth.engine.devices.huawei.packets.Notifications;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;

public class SetWearMessagePushRequest extends Request {
    private static final Logger LOG = LoggerFactory.getLogger(SetWearMessagePushRequest.class);

    public SetWearMessagePushRequest(HuaweiSupportProvider support) {
        super(support);
        this.serviceId = Notifications.id;
        this.commandId = Notifications.WearMessagePushRequest.id;
    }

    @Override
    protected boolean requestSupported() {
        return supportProvider.getDeviceState().supportsDoNotDisturb(supportProvider.getDevice()) && supportProvider.getDeviceState().supportsWearMessagePush();
    }

    @Override
    protected List<byte[]> createRequest() throws RequestCreationException {
        boolean status = GBApplication
            .getDeviceSpecificSharedPrefs(supportProvider.getDevice().getAddress())
            .getBoolean(DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_NOT_WEAR, false);
        try {
            return new Notifications.WearMessagePushRequest(paramsProvider, status).serialize();
        } catch (HuaweiPacket.CryptoException e) {
            throw new RequestCreationException(e);
        }
    }

    @Override
    protected void processResponse() {
        LOG.debug("handle Set WearMessage Push ");
    }
}
