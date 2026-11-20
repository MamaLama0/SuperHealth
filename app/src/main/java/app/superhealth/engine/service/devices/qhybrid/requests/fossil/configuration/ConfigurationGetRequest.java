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
package app.superhealth.engine.service.devices.qhybrid.requests.fossil.configuration;

import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.GenericItem;
import app.superhealth.engine.service.devices.qhybrid.QHybridSupport;
import app.superhealth.engine.service.devices.qhybrid.adapter.fossil.FossilWatchAdapter;
import app.superhealth.engine.service.devices.qhybrid.file.FileHandle;
import app.superhealth.engine.service.devices.qhybrid.requests.fossil.file.FileGetRequest;

public class ConfigurationGetRequest extends FileGetRequest {
    public ConfigurationGetRequest(FossilWatchAdapter adapter) {
        super(FileHandle.CONFIGURATION, adapter);
    }

    @Override
    public void handleFileData(byte[] fileData) {
        GBDevice device = getAdapter().getDeviceSupport().getDevice();

        ConfigurationPutRequest.ConfigItem[] items = ConfigurationPutRequest.parsePayload(fileData);
        for(ConfigurationPutRequest.ConfigItem item : items){
            if(item instanceof ConfigurationPutRequest.VibrationStrengthConfigItem){
//                device.addDeviceInfo(new GenericItem(QHybridSupport.ITEM_VIBRATION_STRENGTH, String.valueOf(((ConfigurationPutRequest.VibrationStrengthConfigItem) item).getValue())));
            }else if(item instanceof ConfigurationPutRequest.DailyStepGoalConfigItem){
//                device.addDeviceInfo(new GenericItem(QHybridSupport.ITEM_STEP_GOAL, String.valueOf(((ConfigurationPutRequest.DailyStepGoalConfigItem) item).getValue())));
            }else if(item instanceof ConfigurationPutRequest.CurrentStepCountConfigItem){
                device.addDeviceInfo(new GenericItem(QHybridSupport.ITEM_STEP_COUNT, String.valueOf(((ConfigurationPutRequest.CurrentStepCountConfigItem) item).getValue())));
            }else if(item instanceof ConfigurationPutRequest.TimezoneOffsetConfigItem) {
                device.addDeviceInfo(new GenericItem(QHybridSupport.ITEM_TIMEZONE_OFFSET, String.valueOf(((ConfigurationPutRequest.TimezoneOffsetConfigItem) item).getValue())));
            }
        }

        device.sendDeviceUpdateIntent(getAdapter().getContext());

        handleConfigurationLoaded();
    }

    public void handleConfigurationLoaded(){}
}
