/*  Copyright (C) 2015-2026 Carsten Pfeiffer

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
package app.superhealth.engine.service.btle.actions;

import androidx.annotation.NonNull;

import app.superhealth.engine.service.btle.BtLEAction;
import app.superhealth.engine.service.btle.GattCharacteristic;

/**
 * An abstract non-BTLE action. It performs no bluetooth operation,
 * does not have a {@link GattCharacteristic} instance and expects no result.
 */
public abstract class PlainAction extends BtLEAction {

    public PlainAction() {
        super(null);
    }

    @Override
    public boolean expectsResult() {
        return false;
    }

    @NonNull
    @Override
    public String toString() {
        return getCreationTime() + " " + getClass().getSimpleName();
    }
}
