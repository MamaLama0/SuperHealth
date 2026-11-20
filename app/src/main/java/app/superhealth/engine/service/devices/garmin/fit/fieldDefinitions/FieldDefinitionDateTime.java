package app.superhealth.engine.service.devices.garmin.fit.fieldDefinitions;

import app.superhealth.engine.service.devices.garmin.fit.FieldDefinition;
import app.superhealth.engine.service.devices.garmin.fit.baseTypes.BaseType;

import static app.superhealth.engine.service.devices.garmin.GarminTimeUtils.GARMIN_TIME_EPOCH;

public class FieldDefinitionDateTime extends FieldDefinition {
    public FieldDefinitionDateTime(int localNumber, int size, BaseType baseType, String name) {
        super(localNumber, size, baseType, name, 1, -GARMIN_TIME_EPOCH);
    }
}
