/*  Copyright (C) 2019 krzys_h

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
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package app.superhealth.engine.devices.moyoung;

import lineageos.weather.util.TemperatureUtils;
import app.superhealth.engine.GBApplication;
import app.superhealth.engine.model.TemperatureUnit;
import app.superhealth.engine.model.WeatherSpec;

public class MoyoungWeatherForecast {
    public final byte conditionId;
    public final byte minTemp;
    public final byte maxTemp;

    public MoyoungWeatherForecast(byte conditionId, byte minTemp, byte maxTemp) {
        this.conditionId = conditionId;
        this.minTemp = minTemp;
        this.maxTemp = maxTemp;
    }

    public MoyoungWeatherForecast(WeatherSpec.Daily forecast) {
        conditionId = MoyoungConstants.openWeatherConditionToMoyoungConditionId(forecast.getConditionCode());
        final TemperatureUnit temperatureUnit = GBApplication.getPrefs().getTemperatureUnit();
        if (temperatureUnit == TemperatureUnit.FAHRENHEIT) {
            minTemp = (byte) TemperatureUtils.celsiusToFahrenheit(forecast.getMinTemp() - 273); // Kelvin -> Fahrenheit
            maxTemp = (byte) TemperatureUtils.celsiusToFahrenheit(forecast.getMaxTemp() - 273); // Kelvin -> Fahrenheit
        } else {
            minTemp = (byte) (forecast.getMinTemp() - 273); // Kelvin -> Celsius
            maxTemp = (byte) (forecast.getMaxTemp() - 273); // Kelvin -> Celsius
        }
    }
}
