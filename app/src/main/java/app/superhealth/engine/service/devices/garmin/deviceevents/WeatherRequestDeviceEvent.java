package app.superhealth.engine.service.devices.garmin.deviceevents;

import android.content.Context;

import app.superhealth.engine.deviceevents.GBDeviceEvent;
import app.superhealth.engine.impl.GBDevice;

public class WeatherRequestDeviceEvent extends GBDeviceEvent {
    private final int format;
    private final int latitude;
    private final int longitude;
    private final int hoursOfForecast;

    public WeatherRequestDeviceEvent(int format, int latitude, int longitude, int hoursOfForecast) {
        this.format = format;
        this.latitude = latitude;
        this.longitude = longitude;
        this.hoursOfForecast = hoursOfForecast;
    }

    @Override
    public void evaluate(final Context context, final GBDevice device) {
        // Handled in support class
    }
}
