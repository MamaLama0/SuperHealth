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
package app.superhealth.engine.service.devices.xiaomi.services;

import android.content.Context;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.devices.xiaomi.XiaomiCoordinator;
import app.superhealth.engine.proto.xiaomi.XiaomiProto;
import app.superhealth.engine.service.devices.xiaomi.XiaomiSupport;
import app.superhealth.engine.util.Prefs;
import app.superhealth.engine.util.preferences.DevicePrefs;

public abstract class AbstractXiaomiService {
    private final XiaomiSupport mSupport;

    public AbstractXiaomiService(final XiaomiSupport support) {
        this.mSupport = support;
    }

    public void setContext(final Context context) {

    }

    public abstract void handleCommand(final XiaomiProto.Command cmd);

    public void initialize() {

    }

    public void dispose() {

    }

    /**
     * Handle a preference change.
     * @param config the preference key
     * @param prefs the device preferences
     * @return true if the preference was handled, false otherwise
     */
    public boolean onSendConfiguration(final String config, final Prefs prefs) {
        return false;
    }

    public XiaomiSupport getSupport() {
        return mSupport;
    }

    protected XiaomiCoordinator getCoordinator() {
        return (XiaomiCoordinator) getSupport().getDevice().getDeviceCoordinator();
    }

    protected DevicePrefs getDevicePrefs() {
        return GBApplication.getDevicePrefs(getSupport().getDevice());
    }

    public void onDisconnect() {}
}
