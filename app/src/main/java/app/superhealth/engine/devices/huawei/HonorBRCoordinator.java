/*  Copyright (C) 2025 Vitalii Tomin

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

package app.superhealth.engine.devices.huawei;

import androidx.annotation.NonNull;

import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.devices.huawei.HonorBRSupport;


public abstract class  HonorBRCoordinator extends  HuaweiBRCoordinator {

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return HonorBRSupport.class;
    }
}
