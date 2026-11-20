/*  Copyright (C) 2024 Martin.JM

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

import java.util.List;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst;
import app.superhealth.engine.devices.huawei.HuaweiPacket;
import app.superhealth.engine.devices.huawei.packets.CameraRemote;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;

public class SendCameraRemoteSetupEvent extends Request {

    CameraRemote.CameraRemoteSetup.Request.Event event;

    public SendCameraRemoteSetupEvent(HuaweiSupportProvider support, CameraRemote.CameraRemoteSetup.Request.Event event) {
        super(support);
        this.serviceId = CameraRemote.id;
        this.commandId = CameraRemote.CameraRemoteSetup.id;
        this.event = event;
    }

    @Override
    protected boolean requestSupported() {
        return supportProvider.getDeviceState().supportsCameraRemote() && GBApplication.getDeviceSpecificSharedPrefs(supportProvider.getDevice().getAddress()).getBoolean(DeviceSettingsPreferenceConst.PREF_CAMERA_REMOTE, false);
    }

    @Override
    protected List<byte[]> createRequest() throws RequestCreationException {
        try {
            return new CameraRemote.CameraRemoteSetup.Request(
                    supportProvider.getParamsProvider(),
                    this.event
            ).serialize();
        } catch (HuaweiPacket.CryptoException e) {
            throw new RequestCreationException(e);
        }
    }
}
