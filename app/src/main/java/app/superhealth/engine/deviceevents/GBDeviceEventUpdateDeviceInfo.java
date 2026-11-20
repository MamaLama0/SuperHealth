/*  Copyright (C) 2021-2024 José Rebelo

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
package app.superhealth.engine.deviceevents;

import android.content.Context;

import androidx.annotation.NonNull;

import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.GenericItem;
import app.superhealth.engine.model.ItemWithDetails;

public class GBDeviceEventUpdateDeviceInfo extends GBDeviceEvent {
    public ItemWithDetails item;

    @NonNull
    @Override
    public String toString() {
        return super.toString() + (item != null ? ("item: " + item.getName() + "=" + item.getDetails()) : "<null>");
    }

    public GBDeviceEventUpdateDeviceInfo(final ItemWithDetails item) {
        this.item = item;
    }

    public GBDeviceEventUpdateDeviceInfo(final String name, final String details) {
        this(new GenericItem(name, details));
    }

    @Override
    public void evaluate(final Context context, final GBDevice device) {
        device.addDeviceInfo(this.item);
        device.sendDeviceUpdateIntent(context);
    }
}
