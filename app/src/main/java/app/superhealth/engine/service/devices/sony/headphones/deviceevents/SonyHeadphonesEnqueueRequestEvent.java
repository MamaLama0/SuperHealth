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
package app.superhealth.engine.service.devices.sony.headphones.deviceevents;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import app.superhealth.engine.deviceevents.GBDeviceEvent;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.devices.sony.headphones.protocol.Request;

public class SonyHeadphonesEnqueueRequestEvent extends GBDeviceEvent {
    private final List<Request> requests = new ArrayList<>();

    public SonyHeadphonesEnqueueRequestEvent(final Request request) {
        this.requests.add(request);
    }

    public SonyHeadphonesEnqueueRequestEvent(final Collection<Request> requests) {
        this.requests.addAll(requests);
    }

    public List<Request> getRequests() {
        return this.requests;
    }

    @Override
    public void evaluate(final Context context, final GBDevice device) {
        // Handled in support class
    }
}
