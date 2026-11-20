/*  Copyright (C) 2020-2024 Andreas Shimokawa, angelpup, José Rebelo

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
package app.superhealth.engine.service.devices.huami.amazfitvergel;

import android.content.Context;
import android.net.Uri;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.huami.HuamiFWHelper;
import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.devices.huami.amazfitbip.AmazfitBipSupport;
import app.superhealth.engine.devices.huami.amazfitvergel.AmazfitVergeLFWHelper;
import app.superhealth.engine.service.devices.huami.operations.update.UpdateFirmwareOperation;
import app.superhealth.engine.service.devices.huami.operations.update.UpdateFirmwareOperation2020;

public class AmazfitVergeLSupport extends AmazfitBipSupport {
    private static final Logger LOG = LoggerFactory.getLogger(AmazfitVergeLSupport.class);

    @Override
    public byte getCryptFlags() {
        return (byte) 0x80;
    }

    @Override
    protected byte getAuthFlags() {
        return 0x00;
    }

    @Override
    protected AmazfitVergeLSupport setDisplayItems(TransactionBuilder builder) {
        setDisplayItemsNew(builder, false, true, R.array.pref_gts_display_items_values);
        return this;
    }

    @Override
    protected boolean notificationHasExtraHeader() {
        return true;
    }

    @Override
    public UpdateFirmwareOperation createUpdateFirmwareOperation(Uri uri) {
        return new UpdateFirmwareOperation2020(uri, this);
    }

    @Override
    public HuamiFWHelper createFWHelper(Uri uri, Context context) throws IOException {
        return new AmazfitVergeLFWHelper(uri, context);
    }
}
