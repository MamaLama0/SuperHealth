/*  Copyright (C) 2023-2024 José Rebelo

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
package app.superhealth.engine.devices.huami.amazfitbip3pro;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;

import app.superhealth.engine.devices.huami.HuamiFWHelper;
import app.superhealth.engine.service.devices.huami.amazfitbip3pro.AmazfitBip3ProFirmwareInfo;

public class AmazfitBip3ProFWHelper extends HuamiFWHelper {
    public AmazfitBip3ProFWHelper(final Uri uri, final Context context) throws IOException {
        super(uri, context);
    }

    @Override
    protected void determineFirmwareInfo(final byte[] wholeFirmwareBytes) {
        firmwareInfo = new AmazfitBip3ProFirmwareInfo(wholeFirmwareBytes);
        if (!firmwareInfo.isHeaderValid()) {
            throw new IllegalArgumentException("Not an Amazfit Bip 3 Pro firmware");
        }
    }
}
