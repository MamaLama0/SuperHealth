/*  Copyright (C) 2020-2024 Andreas Shimokawa, Dmytro Bielik

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
package app.superhealth.engine.service.devices.huami.amazfittrex;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.huami.HuamiFWHelper;
import app.superhealth.engine.devices.huami.amazfittrex.AmazfitTRexFWHelper;
import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.devices.huami.amazfitgts.AmazfitGTSSupport;

public class AmazfitTRexSupport extends AmazfitGTSSupport {

    @Override
    public HuamiFWHelper createFWHelper(Uri uri, Context context) throws IOException {
        return new AmazfitTRexFWHelper(uri, context);
    }

    @Override
    protected AmazfitTRexSupport setDisplayItems(TransactionBuilder builder) {
        setDisplayItemsNew(builder, false, true, R.array.pref_trex_display_items_default);
        return this;
    }

}
