/*  Copyright (C) 2021-2024 Daniel Dakhno

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
package app.superhealth.engine.service.devices.qhybrid.requests.fossil_hr.authentication;

import java.util.UUID;

import app.superhealth.engine.service.devices.qhybrid.requests.fossil.FossilRequest;

public abstract class AuthenticationRequest extends FossilRequest {
    @Override
    public UUID getRequestUUID() {
        return UUID.fromString("3dda0005-957f-7d4a-34a6-74696673696d");
    }
}
