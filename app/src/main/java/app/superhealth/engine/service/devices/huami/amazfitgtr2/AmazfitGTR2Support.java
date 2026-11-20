/*  Copyright (C) 2020-2024 Andreas Shimokawa, pangwalla, Vianney le Clément
    de Saint-Marcq

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
package app.superhealth.engine.service.devices.huami.amazfitgtr2;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.huami.HuamiFWHelper;
import app.superhealth.engine.devices.huami.amazfitgtr2.AmazfitGTR2FWHelper;
import app.superhealth.engine.model.CallSpec;
import app.superhealth.engine.model.NotificationSpec;
import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.devices.huami.amazfitgtr.AmazfitGTRSupport;
import app.superhealth.engine.service.devices.huami.operations.update.UpdateFirmwareOperation;
import app.superhealth.engine.service.devices.huami.operations.update.UpdateFirmwareOperation2020;

public class AmazfitGTR2Support extends AmazfitGTRSupport {

    @Override
    public boolean supportsSunriseSunsetWindHumidity() {
        return true;
    }

    @Override
    public HuamiFWHelper createFWHelper(Uri uri, Context context) throws IOException {
        return new AmazfitGTR2FWHelper(uri, context);
    }

    @Override
    public void onSetCallState(CallSpec callSpec) {
        onSetCallStateNew(callSpec);
    }

    @Override
    public UpdateFirmwareOperation createUpdateFirmwareOperation(Uri uri) {
        return new UpdateFirmwareOperation2020(uri, this);
    }

    @Override
    public int getActivitySampleSize() {
        return 8;
    }

    @Override
    protected AmazfitGTR2Support setDisplayItems(TransactionBuilder builder) {
        setDisplayItemsNew(builder, false, false, R.array.pref_gtsgtr2_display_items_default);
        return this;
    }

    @Override
    public String getNotificationBody(NotificationSpec notificationSpec) {
        // #6024
        return getNotificationBodyCheckAcceptsSender(notificationSpec);
    }
}
