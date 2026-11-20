/*  Copyright (C) 2024-2026 Daniele Gobbetti, José Rebelo, punchdeerflyscorpion, Thomas Kuehne

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
package app.superhealth.engine.service.devices.garmin.fit;

import app.superhealth.engine.service.devices.garmin.fit.baseTypes.BaseType;
import app.superhealth.engine.service.devices.garmin.fit.fieldDefinitions.FieldDefinitionAlarm;
import app.superhealth.engine.service.devices.garmin.fit.fieldDefinitions.FieldDefinitionBool;
import app.superhealth.engine.service.devices.garmin.fit.fieldDefinitions.FieldDefinitionCoordinate;
import app.superhealth.engine.service.devices.garmin.fit.fieldDefinitions.FieldDefinitionDayOfWeek;
import app.superhealth.engine.service.devices.garmin.fit.fieldDefinitions.FieldDefinitionFileType;
import app.superhealth.engine.service.devices.garmin.fit.fieldDefinitions.FieldDefinitionLocalDateTime;
import app.superhealth.engine.service.devices.garmin.fit.fieldDefinitions.FieldDefinitionTemperature;
import app.superhealth.engine.service.devices.garmin.fit.fieldDefinitions.FieldDefinitionDateTime;
import app.superhealth.engine.service.devices.garmin.fit.fieldDefinitions.FieldDefinitions;

public class FieldDefinitionFactory {
    public static FieldDefinition create(int localNumber, int size, FieldDefinitions field, BaseType baseType, String name, double scale, int offset) {
        if (null == field) {
            return new FieldDefinition(localNumber, size, baseType, name, scale, offset);
        }
        return switch (field) {
            case ALARM -> new FieldDefinitionAlarm(localNumber, size, baseType, name, scale, offset);
            case Bool -> new FieldDefinitionBool(localNumber, size, baseType, name, scale, offset);
            case DayOfWeek -> new FieldDefinitionDayOfWeek(localNumber, size, baseType, name, scale, offset);
            case FILE_TYPE -> new FieldDefinitionFileType(localNumber, size, baseType, name);
            case TEMPERATURE -> new FieldDefinitionTemperature(localNumber, size, baseType, name, scale, offset);
            case DateTime -> new FieldDefinitionDateTime(localNumber, size, baseType, name);
            case LocalDateTime -> new FieldDefinitionLocalDateTime(localNumber, size, baseType, name);
            case COORDINATE -> new FieldDefinitionCoordinate(localNumber, size, baseType, name, scale, offset);
            default -> FieldDefinitions.create(localNumber, size, field, baseType, name, scale, offset);
        };
    }
}
