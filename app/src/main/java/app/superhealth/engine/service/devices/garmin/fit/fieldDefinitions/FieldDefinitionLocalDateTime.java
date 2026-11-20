package app.superhealth.engine.service.devices.garmin.fit.fieldDefinitions;

import static app.superhealth.engine.service.devices.garmin.GarminTimeUtils.GARMIN_TIME_EPOCH;

import app.superhealth.engine.service.devices.garmin.fit.FieldDefinition;
import app.superhealth.engine.service.devices.garmin.fit.baseTypes.BaseType;

public class FieldDefinitionLocalDateTime extends FieldDefinition {
    public FieldDefinitionLocalDateTime(int localNumber, int size, BaseType baseType, String name) {
        super(localNumber, size, baseType, name, 1, -GARMIN_TIME_EPOCH);
    }
}
