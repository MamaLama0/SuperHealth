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

import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.R;
import app.superhealth.engine.deviceevents.GBDeviceEventBatteryInfo;
import app.superhealth.engine.devices.huawei.HuaweiPacket;
import app.superhealth.engine.devices.huawei.packets.DeviceConfig;
import app.superhealth.engine.model.BatteryState;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;
import app.superhealth.engine.util.GB;

public class GetBatteryLevelRequest extends Request {
    private static final Logger LOG = LoggerFactory.getLogger(GetBatteryLevelRequest.class);

    public GetBatteryLevelRequest(HuaweiSupportProvider support) {
        super(support);
        this.serviceId = DeviceConfig.id;
        this.commandId = DeviceConfig.BatteryLevel.id;
    }

    @Override
    protected List<byte[]> createRequest() throws RequestCreationException {
        try {
            return new DeviceConfig.BatteryLevel.Request(paramsProvider).serialize();
        } catch (HuaweiPacket.CryptoException e) {
            throw new RequestCreationException(e);
        }
    }

    public static BatteryState byteToBatteryState(byte state) {
        if (state == 1)
            return BatteryState.BATTERY_CHARGING;
        return BatteryState.BATTERY_NORMAL;
    }

    @Override
    protected void processResponse() throws ResponseParseException {
        LOG.debug("handle Battery Level");

        if (!(receivedPacket instanceof DeviceConfig.BatteryLevel.Response))
            throw new ResponseTypeMismatchException(receivedPacket, DeviceConfig.BatteryLevel.Response.class);

        DeviceConfig.BatteryLevel.Response response = (DeviceConfig.BatteryLevel.Response) receivedPacket;

        if (response.multi_level == null) {
            byte batteryLevel = response.level;
            getDevice().setBatteryLevel(batteryLevel, 0);

            GBDeviceEventBatteryInfo batteryInfo = new GBDeviceEventBatteryInfo();
            batteryInfo.state = BatteryState.BATTERY_NORMAL;
            batteryInfo.level = (int) batteryLevel & 0xff;
            this.supportProvider.evaluateGBDeviceEvent(batteryInfo);
        } else {
            // Handle multiple batteries
            for (int i = 0; i < response.multi_level.length; i++) {
                int level = (int) response.multi_level[i] & 0xff;
                getDevice().setBatteryLevel(level, i);

                GBDeviceEventBatteryInfo batteryInfo = new GBDeviceEventBatteryInfo();
                batteryInfo.batteryIndex = i;
                batteryInfo.state = response.status != null && response.status.length > i ?
                        byteToBatteryState(response.status[i]) :
                        BatteryState.BATTERY_NORMAL;
                batteryInfo.level = level;
                this.supportProvider.evaluateGBDeviceEvent(batteryInfo);
            }
        }

        if (GBApplication.getDevicePrefs(getDevice()).getBatteryPollingEnabled()) {
            if (!this.supportProvider.startBatteryRunnerDelayed()) {
                GB.toast(getContext(), R.string.battery_polling_failed_start, Toast.LENGTH_SHORT, GB.ERROR);
                LOG.error("Failed to start the battery polling");
            }
        }
    }
}
