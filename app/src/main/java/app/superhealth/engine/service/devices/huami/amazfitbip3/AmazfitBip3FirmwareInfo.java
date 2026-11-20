/*  Copyright (C) 2024 José Rebelo

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
package app.superhealth.engine.service.devices.huami.amazfitbip3;

import java.util.HashMap;
import java.util.Map;

import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.DeviceType;
import app.superhealth.engine.service.devices.huami.HuamiFirmwareInfo;
import app.superhealth.engine.service.devices.huami.HuamiFirmwareType;
import app.superhealth.engine.service.devices.huami.miband4.MiBand4FirmwareInfo;
import app.superhealth.engine.util.ArrayUtils;

public class AmazfitBip3FirmwareInfo extends HuamiFirmwareInfo {
    private static final Map<Integer, String> crcToVersion = new HashMap<>();

    static {
        // firmware

        // resources

        // gps
    }

    public AmazfitBip3FirmwareInfo(final byte[] bytes) {
        super(bytes);
    }

    @Override
    protected HuamiFirmwareType determineFirmwareType(final byte[] bytes) {
        if (ArrayUtils.equals(bytes, MiBand4FirmwareInfo.FW_HEADER, MiBand4FirmwareInfo.FW_HEADER_OFFSET)) {
            if (searchString32BitAligned(bytes, "\0\0Amazfit Bip 3") && !searchString32BitAligned(bytes, "\0\0Amazfit Bip 3 Pro")) {
                return HuamiFirmwareType.FIRMWARE;
            }
            return HuamiFirmwareType.INVALID;
        }

        if (ArrayUtils.startsWith(bytes, NEWRES_HEADER)) {
            return HuamiFirmwareType.RES;
        }

        if (ArrayUtils.startsWith(bytes, UIHH_HEADER) && (bytes[4] == 1 || bytes[4] == 2)) {
            return HuamiFirmwareType.WATCHFACE;
        }

        if (ArrayUtils.startsWith(bytes, AGPS_UIHH_HEADER)) {
            return HuamiFirmwareType.AGPS_UIHH;
        }

        for (byte[] gpsHeader : GPS_HEADERS) {
            if (ArrayUtils.startsWith(bytes, gpsHeader)) {
                return HuamiFirmwareType.GPS;
            }
        }
        return HuamiFirmwareType.INVALID;
    }

    @Override
    public boolean isGenerallyCompatibleWith(final GBDevice device) {
        return isHeaderValid() && device.getType() == DeviceType.AMAZFITBIP3;
    }

    @Override
    protected Map<Integer, String> getCrcMap() {
        return crcToVersion;
    }
}
