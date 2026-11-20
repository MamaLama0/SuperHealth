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

import java.util.ArrayList;
import java.util.List;

import app.superhealth.engine.devices.huawei.HuaweiPacket;
import app.superhealth.engine.devices.huawei.packets.Weather;
import app.superhealth.engine.model.WeatherSpec;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;
import app.superhealth.engine.devices.huawei.packets.Weather.WeatherForecastData;

public class SendWeatherForecastRequest extends Request {
    Weather.Settings weatherSettings;
    WeatherSpec weatherSpec;

    public SendWeatherForecastRequest(HuaweiSupportProvider support, Weather.Settings weatherSettings, WeatherSpec weatherSpec) {
        super(support);
        this.serviceId = Weather.id;
        this.commandId = Weather.WeatherForecastData.id;
        this.weatherSettings = weatherSettings;
        this.weatherSpec = weatherSpec;
    }

    @Override
    protected List<byte[]> createRequest() throws RequestCreationException {
        int hourlyCount = Math.min(weatherSpec.getHourly().size(), 24);
        int dayCount = Math.min(weatherSpec.getForecasts().size() + 1, 8); // We add today as well

        ArrayList<WeatherForecastData.TimeData> timeDataArrayList = new ArrayList<>(hourlyCount);
        ArrayList<WeatherForecastData.DayData> dayDataArrayList = new ArrayList<>(dayCount);
        for (int i = 0; i < hourlyCount; i++) {
            WeatherSpec.Hourly hourly = weatherSpec.getHourly().get(i);
            WeatherForecastData.TimeData timeData = new WeatherForecastData.TimeData();
            timeData.timestamp = hourly.getTimestamp();
            timeData.icon = supportProvider.openWeatherMapConditionCodeToHuaweiIcon(hourly.getConditionCode());
            timeData.temperature = (byte) (hourly.getTemp() - 273);
            timeData.precipitation = (byte) hourly.getPrecipProbability();
            timeData.uvIndex = (byte) Math.round(hourly.getUvIndex());
            timeDataArrayList.add(timeData);
        }

        // Add today as well
        WeatherForecastData.DayData today = new WeatherForecastData.DayData();
        today.timestamp = weatherSpec.getTimestamp();
        today.icon = supportProvider.openWeatherMapConditionCodeToHuaweiIcon(weatherSpec.getCurrentConditionCode());
        today.highTemperature = (byte) (weatherSpec.getTodayMaxTemp() - 273);
        today.lowTemperature = (byte) (weatherSpec.getTodayMinTemp() - 273);
        today.sunriseTime = weatherSpec.getSunRise();
        today.sunsetTime = weatherSpec.getSunSet();
        today.moonRiseTime = weatherSpec.getMoonRise();
        today.moonSetTime = weatherSpec.getMoonSet();
        today.moonPhase = Weather.degreesToMoonPhase(weatherSpec.getMoonPhase());
        dayDataArrayList.add(today);

        for (int i = 0; i < dayCount - 1; i++) {
            WeatherSpec.Daily daily = weatherSpec.getForecasts().get(i);
            WeatherForecastData.DayData dayData = new WeatherForecastData.DayData();
            dayData.timestamp = weatherSpec.getTimestamp() + (60*60*24 * (i + 1));
            dayData.icon = supportProvider.openWeatherMapConditionCodeToHuaweiIcon(daily.getConditionCode());
            dayData.highTemperature = (byte) (daily.getMaxTemp() - 273);
            dayData.lowTemperature = (byte) (daily.getMinTemp() - 273);
            dayData.sunriseTime = daily.getSunRise();
            dayData.sunsetTime = daily.getSunSet();
            dayData.moonRiseTime = daily.getMoonRise();
            dayData.moonSetTime = daily.getMoonSet();
            dayData.moonPhase = Weather.degreesToMoonPhase(daily.getMoonPhase());
            dayDataArrayList.add(dayData);
        }
        try {
            return new WeatherForecastData.Request(
                    this.paramsProvider,
                    this.weatherSettings,
                    timeDataArrayList,
                    dayDataArrayList
            ).serialize();
        } catch (HuaweiPacket.CryptoException e) {
            throw new RequestCreationException(e);
        }
    }
}
