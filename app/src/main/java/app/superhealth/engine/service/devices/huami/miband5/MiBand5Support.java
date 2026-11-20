/*  Copyright (C) 2020-2024 Andreas Shimokawa, beardhatcode, José Rebelo,
    odavo32nof

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
package app.superhealth.engine.service.devices.huami.miband5;

import android.content.Context;
import android.net.Uri;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.huami.HuamiCoordinator;
import app.superhealth.engine.devices.huami.HuamiFWHelper;
import app.superhealth.engine.devices.huami.miband5.MiBand5FWHelper;
import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.devices.huami.miband4.MiBand4Support;

public class MiBand5Support extends MiBand4Support {
    private static final Logger LOG = LoggerFactory.getLogger(MiBand5Support.class);

    @Override
    protected MiBand5Support setDisplayItems(TransactionBuilder builder) {
        setDisplayItemsNew(builder, false, true, R.array.pref_miband5_display_items_default);
        return this;
    }

    @Override
    protected void setShortcuts(TransactionBuilder builder) {
        setDisplayItemsNew(builder, true, true, R.array.pref_miband5_shortcuts_default);
    }

    @Override
    public HuamiFWHelper createFWHelper(Uri uri, Context context) throws IOException {
        return new MiBand5FWHelper(uri, context);
    }

    @Override
    public boolean supportsSunriseSunsetWindHumidity() {
        return true;
    }

    @Override
    public int getActivitySampleSize() {
        return 8;
    }

    @Override
    public void phase3Initialize(TransactionBuilder builder) {
        super.phase3Initialize(builder);
        LOG.info("phase3Initialize...");
        if (HuamiCoordinator.getOverwriteSettingsOnConnection(getDevice().getAddress())) {
            setWorkoutActivityTypes(builder);  // TODO: Supported by other bands?
        }
    }
}
