/*  Copyright (C) 2016-2024 Carsten Pfeiffer

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
package app.superhealth.engine.service.devices.miband.operations;

import app.superhealth.engine.devices.miband.MiBandService;
import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.devices.miband.MiBandSupport;

public abstract class AbstractMiBand1Operation extends AbstractMiBandOperation<MiBandSupport> {
    protected AbstractMiBand1Operation(MiBandSupport support) {
        super(support);
    }

    @Override
    protected void enableOtherNotifications(TransactionBuilder builder, boolean enable) {
        builder.notify(MiBandService.UUID_CHARACTERISTIC_REALTIME_STEPS, enable)
                .notify(MiBandService.UUID_CHARACTERISTIC_SENSOR_DATA, enable);
    }
}
