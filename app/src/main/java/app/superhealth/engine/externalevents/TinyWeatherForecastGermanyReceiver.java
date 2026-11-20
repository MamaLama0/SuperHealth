/*  Copyright (C) 2020-2024 Andreas Shimokawa

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

package app.superhealth.engine.externalevents;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.core.os.BundleCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.model.weather.Weather;
import app.superhealth.engine.model.WeatherSpec;
import app.superhealth.engine.util.GB;

public class TinyWeatherForecastGermanyReceiver extends BroadcastReceiver {
    private static final Logger LOG = LoggerFactory.getLogger(TinyWeatherForecastGermanyReceiver.class);

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            LOG.warn("Got null intent");
            return;
        }
        final Bundle bundle = intent.getExtras();
        if (bundle == null) {
            LOG.warn("Got null bundle");
            return;
        }

        LOG.debug("Got intent for {}", intent.getAction());

        try {
            bundle.setClassLoader(WeatherSpec.class.getClassLoader());

            final WeatherSpec weatherSpec = BundleCompat.getParcelable(bundle, "WeatherSpec", WeatherSpec.class);

            if (weatherSpec == null) {
                LOG.warn("Got null WeatherSpec");
                return;
            }

            final ArrayList<WeatherSpec> weatherSpecs = new ArrayList<>(Collections.singletonList(weatherSpec));
            weatherSpec.setTimestamp((int) (System.currentTimeMillis() / 1000));
            Weather.setWeatherSpec(weatherSpecs);
            GBApplication.deviceService().onSendWeather();
        } catch (Exception e) {
            GB.toast("SuperHealth received broken or incompatible weather data", Toast.LENGTH_SHORT, GB.ERROR, e);

            for (String key : bundle.keySet()) {
                try {
                    LOG.debug("WeatherSpec {} -> {}", key, bundle.get(key));
                } catch (final Exception e2) {
                    LOG.warn("Failed to log WeatherSpec key {}", key, e2);
                }
            }
        }
    }
}