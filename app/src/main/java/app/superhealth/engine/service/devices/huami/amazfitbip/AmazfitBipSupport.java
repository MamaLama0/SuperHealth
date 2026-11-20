/*  Copyright (C) 2018-2024 Andreas Shimokawa, DerFetzer, José Rebelo,
    Matthieu Baerts, NekoBox, Oleg Vasilev

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
package app.superhealth.engine.service.devices.huami.amazfitbip;

import android.content.Context;
import android.net.Uri;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.huami.HuamiCoordinator;
import app.superhealth.engine.devices.huami.HuamiFWHelper;
import app.superhealth.engine.devices.huami.amazfitbip.AmazfitBipFWHelper;
import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.devices.huami.HuamiSupport;
import app.superhealth.engine.service.devices.miband.NotificationStrategy;

public class AmazfitBipSupport extends HuamiSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AmazfitBipSupport.class);

    public AmazfitBipSupport() {
        super(LOG);
    }

    @Override
    public NotificationStrategy getNotificationStrategy() {
        return new AmazfitBipTextNotificationStrategy(this);
    }

    @Override
    protected AmazfitBipSupport setDisplayItems(TransactionBuilder builder) {
        Map<String, Integer> keyPosMap = new LinkedHashMap<>();
        keyPosMap.put("status", 1);
        keyPosMap.put("activity", 2);
        keyPosMap.put("weather", 3);
        keyPosMap.put("alarm", 4);
        keyPosMap.put("timer", 5);
        keyPosMap.put("compass", 6);
        keyPosMap.put("settings", 7);
        keyPosMap.put("alipay", 8);

        setDisplayItemsOld(builder, false, R.array.pref_bip_display_items_default, keyPosMap);
        return this;
    }

    @Override
    protected void setShortcuts(TransactionBuilder builder) {
        Map<String, Integer> keyPosMap = new LinkedHashMap<>();
        keyPosMap.put("alipay", 1);
        keyPosMap.put("weather", 2);

        setDisplayItemsOld(builder, true, R.array.pref_bip_shortcuts_default, keyPosMap);
    }

    @Override
    public void phase2Initialize(TransactionBuilder builder) {
        super.phase2Initialize(builder);
        LOG.info("phase2Initialize...");

        if (HuamiCoordinator.getOverwriteSettingsOnConnection(getDevice().getAddress())) {
            setLanguage(builder);
        }

        requestGPSVersion(builder);
    }

    @Override
    public HuamiFWHelper createFWHelper(Uri uri, Context context) throws IOException {
        return new AmazfitBipFWHelper(uri, context);
    }
}
