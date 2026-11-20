/*  Copyright (C) 2025 Ilya Nikitenkov

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

import android.content.SharedPreferences;

import java.util.List;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst;
import app.superhealth.engine.devices.huawei.HuaweiPacket;
import app.superhealth.engine.devices.huawei.packets.Earphones;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;

public class SetVoiceBoostRequest extends Request {

    public enum VoiceBoostMode {
        OFF((byte) 0x02, false),
        ON((byte) 0x01, true);

        private final byte code;
        private final boolean state;

        VoiceBoostMode(final byte code, final boolean state) {
            this.code = code;
            this.state = state;
        }

        public byte getCode() {
            return this.code;
        }

        private boolean getState() {
            return this.state;
        }

        private static VoiceBoostMode fromBoolean(boolean state) {
            return state ? ON : OFF;
        }

        public static VoiceBoostMode fromPreferences(final SharedPreferences prefs) {
            return VoiceBoostMode.fromBoolean(prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_HUAWEI_FREEBUDS_VOICE_BOOST, OFF.getState()));
        }
    }

    public SetVoiceBoostRequest(HuaweiSupportProvider supportProvider) {
        super(supportProvider);
        this.serviceId = Earphones.id;
        this.commandId = Earphones.SetANCModeRequest.id;
        this.addToResponse = false; // Response with different command ID
    }

    @Override
    protected List<byte[]> createRequest() throws RequestCreationException {
        try {
            VoiceBoostMode clearVoiceMode = VoiceBoostMode.fromPreferences(GBApplication.getDeviceSpecificSharedPrefs(this.getDevice().getAddress()));
            return new Earphones.SetVoiceBoostRequest(this.paramsProvider, clearVoiceMode).serialize();
        } catch (HuaweiPacket.CryptoException e) {
            throw new RequestCreationException(e);
        }
    }
}
