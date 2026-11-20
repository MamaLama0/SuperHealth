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

import app.superhealth.engine.devices.huawei.HuaweiHeartRateZonesSpec;
import app.superhealth.engine.devices.huawei.HuaweiPacket;
import app.superhealth.engine.devices.huawei.packets.FitnessData;
import app.superhealth.engine.model.heartratezones.HeartRateZonesConfig;
import app.superhealth.engine.model.heartratezones.HeartRateZonesSpec;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;

public class SendHeartRateZonesConfig extends Request {

    public SendHeartRateZonesConfig(HuaweiSupportProvider support) {
        super(support);
        this.serviceId = FitnessData.id;
        this.commandId = supportProvider.getDeviceState().supportsExtendedHeartRateZones() ?
                FitnessData.HeartRateZoneConfigPacket.id_extended :
                FitnessData.HeartRateZoneConfigPacket.id_simple;
    }

    @Override
    protected boolean requestSupported() {
        return
                !supportProvider.getDeviceState().supportsTrack() && // In this case it uses P2P
                supportProvider.getDeviceState().supportsHeartRateZones();
    }

    @Override
    protected List<byte[]> createRequest() throws RequestCreationException {
        try {
            HuaweiHeartRateZonesSpec spec = new HuaweiHeartRateZonesSpec(supportProvider.getDevice(), supportProvider.getDeviceState());
            List<HeartRateZonesConfig> zones = spec.getDeviceConfig();
            HeartRateZonesConfig heartRateZonesConfig = HuaweiHeartRateZonesSpec.getByPosture(zones, HeartRateZonesSpec.PostureType.UPRIGHT);
            if (supportProvider.getDeviceState().supportsExtendedHeartRateZones()) {
                return FitnessData.HeartRateZoneConfigPacket.Request.requestExtended(paramsProvider, heartRateZonesConfig).serialize();
            } else {
                return FitnessData.HeartRateZoneConfigPacket.Request.requestSimple(paramsProvider, heartRateZonesConfig).serialize();
            }
        } catch (HuaweiPacket.CryptoException e) {
            throw new RequestCreationException(e);
        }
    }
}
