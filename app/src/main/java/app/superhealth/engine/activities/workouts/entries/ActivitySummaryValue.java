/*  Copyright (C) 2024-2026 José Rebelo, Thomas Kuehne

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
package app.superhealth.engine.activities.workouts.entries;

import androidx.annotation.Nullable;

import app.superhealth.engine.activities.workouts.WorkoutValueFormatter;
import app.superhealth.engine.model.ActivitySummaryEntries;

public record ActivitySummaryValue(@Nullable Object value, String unit) {
    public ActivitySummaryValue(@Nullable Object value, String unit) {
        this.unit = unit;
        if (value instanceof Number number) {
            this.value = Double.isFinite(number.doubleValue()) ? value : null;
        } else {
            this.value = value;
        }
    }

    public ActivitySummaryValue(final String value) {
        this(value, ActivitySummaryEntries.UNIT_STRING);
    }

    public String format(final WorkoutValueFormatter formatter) {
        return formatter.formatValue(value, unit);
    }
}
