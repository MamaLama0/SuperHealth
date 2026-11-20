/*  Copyright (C) 2017-2024 Andreas Shimokawa, José Rebelo

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
package app.superhealth.engine.service.devices.huami.amazfitcor;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.huami.HuamiFWHelper;
import app.superhealth.engine.devices.huami.amazfitcor.AmazfitCorFWHelper;
import app.superhealth.engine.model.MusicStateSpec;
import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.devices.huami.amazfitbip.AmazfitBipSupport;

public class AmazfitCorSupport extends AmazfitBipSupport {

    @Override
    protected AmazfitCorSupport setDisplayItems(TransactionBuilder builder) {
        Map<String, Integer> keyPosMap = new LinkedHashMap<>();
        keyPosMap.put("status", 1);
        keyPosMap.put("notifications", 2);
        keyPosMap.put("activity", 3);
        keyPosMap.put("weather", 4);
        keyPosMap.put("alarm", 5);
        keyPosMap.put("timer", 6);
        keyPosMap.put("settings", 7);
        keyPosMap.put("alipay", 8);
        keyPosMap.put("music", 9);

        setDisplayItemsOld(builder, false, R.array.pref_cor_display_items_default, keyPosMap);
        return this;
    }

    @Override
    public void onSetMusicState(MusicStateSpec stateSpec) {
        if (mediaManager.onSetMusicState(stateSpec)) {
            sendMusicStateToDevice(null, mediaManager.getBufferMusicStateSpec());
        }
    }

    @Override
    public HuamiFWHelper createFWHelper(Uri uri, Context context) throws IOException {
        return new AmazfitCorFWHelper(uri, context);
    }
}
