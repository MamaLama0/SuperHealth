/*  Copyright (C) 2019-2024 Daniel Dakhno

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
package app.superhealth.engine.service.devices.qhybrid.requests.fossil_hr.json;

import org.json.JSONObject;

import app.superhealth.engine.service.devices.qhybrid.adapter.fossil_hr.FossilHRWatchAdapter;
import app.superhealth.engine.service.devices.qhybrid.requests.fossil.file.FilePutRawRequest;

public class JsonPutRequest extends FilePutRawRequest {
    public JsonPutRequest(JSONObject object, FossilHRWatchAdapter adapter) {
        this(object.toString(), adapter);
    }

    public JsonPutRequest(String json, FossilHRWatchAdapter adapter) {
        super((short)(0x0500 | (adapter.getJsonIndex() & 0xFF)), json.getBytes(), adapter);
    }
}
