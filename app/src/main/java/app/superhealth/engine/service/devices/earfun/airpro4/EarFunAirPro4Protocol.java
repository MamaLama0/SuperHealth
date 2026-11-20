/*  Copyright (C) 2025-2026 Lars Berning, Thomas Kuehne

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
package app.superhealth.engine.service.devices.earfun.airpro4;

import static app.superhealth.engine.service.devices.earfun.EarFunPacketEncoder.joinPackets;

import android.os.Bundle;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.DeviceType;
import app.superhealth.engine.service.devices.earfun.EarFunPacketEncoder;
import app.superhealth.engine.service.devices.earfun.EarFunProtocol;
import app.superhealth.engine.service.devices.earfun.prefs.Equalizer;
import app.superhealth.engine.util.Prefs;

public class EarFunAirPro4Protocol extends EarFunProtocol {

    private static final Logger LOG = LoggerFactory.getLogger(EarFunAirPro4Protocol.class);

    @Override
    public byte[] encodeTestNewFunction(@Nullable Bundle options) {
        return joinPackets(
                // new EarFunPacket(EarFunPacket.Command.COMMAND_REBOOT).encode()
        );
    }

    @Override
    public byte[] encodeSendConfiguration(String config) {
        if (Equalizer.containsKey(Equalizer.TenBandEqualizer, config)) {
            Prefs prefs = getDevicePrefs();
            return EarFunPacketEncoder.encodeSetEqualizerTenBands(prefs);
        }
        return super.encodeSendConfiguration(config);
    }

    @Override
    public byte[] encodeSettingsReq() {
        return EarFunPacketEncoder.encodeAirPro4SettingsReq();
    }

    protected EarFunAirPro4Protocol(GBDevice device) {
        super(device);
        DeviceType type = device.getType();
    }
}
