/*  Copyright (C) 2024 Martin.JM

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
package app.superhealth.engine.service.devices.huawei.requests;

import java.util.List;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.devices.huawei.HuaweiPacket;
import app.superhealth.engine.devices.huawei.packets.Weather;
import app.superhealth.engine.model.TemperatureUnit;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;

public class SendWeatherUnitRequest extends Request {

    public SendWeatherUnitRequest(HuaweiSupportProvider support) {
        super(support);
        this.serviceId = Weather.id;
        this.commandId = Weather.WeatherUnitRequest.id;
    }

    @Override
    protected List<byte[]> createRequest() throws RequestCreationException {
        Weather.HuaweiTemperatureFormat temperatureFormat = Weather.HuaweiTemperatureFormat.CELSIUS;
        final TemperatureUnit temperatureUnit = GBApplication.getPrefs().getTemperatureUnit();
        if (temperatureUnit == TemperatureUnit.FAHRENHEIT)
            temperatureFormat = Weather.HuaweiTemperatureFormat.FAHRENHEIT;
        try {
            return new Weather.WeatherUnitRequest(this.paramsProvider, temperatureFormat).serialize();
        } catch (HuaweiPacket.CryptoException e) {
            throw new RequestCreationException(e);
        }
    }
}
