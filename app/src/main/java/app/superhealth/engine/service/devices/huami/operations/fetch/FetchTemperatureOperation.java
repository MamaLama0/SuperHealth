/*  Copyright (C) 2023-2024 José Rebelo

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
package app.superhealth.engine.service.devices.huami.operations.fetch;

import android.widget.Toast;

import androidx.annotation.StringRes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.R;
import app.superhealth.engine.database.DBHandler;
import app.superhealth.engine.devices.GenericTemperatureSampleProvider;
import app.superhealth.engine.devices.huami.HuamiCoordinator;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.GenericTemperatureSample;
import app.superhealth.engine.service.devices.huami.HuamiFetcher;
import app.superhealth.engine.util.DateTimeUtils;
import app.superhealth.engine.util.GB;

/**
 * An operation that fetches temperature data.
 */
public class FetchTemperatureOperation extends AbstractRepeatingFetchOperation {
    private static final Logger LOG = LoggerFactory.getLogger(FetchTemperatureOperation.class);

    public FetchTemperatureOperation(final HuamiFetcher fetcher) {
        super(fetcher, HuamiFetchDataType.TEMPERATURE);
    }

    @StringRes
    @Override
    public int taskDescription() {
        return R.string.busy_task_fetch_temperature;
    }

    @Override
    protected boolean handleActivityData(final GregorianCalendar timestamp, final byte[] bytes) {
        if (bytes.length % 8 != 0) {
            LOG.info("Unexpected buffered temperature data size {} is not a multiple of 8", bytes.length);
            return false;
        }

        final ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        final List<GenericTemperatureSample> samples = new ArrayList<>();

        while (buffer.position() < bytes.length) {
            final int unk1 = buffer.getShort(); // 32767
            final int temperature = buffer.getShort();
            final int unk3 = buffer.getShort(); // 23130
            final int unk4 = buffer.getShort(); // 23130

            LOG.trace(
                    "Temperature at {}: {}",
                    DateTimeUtils.formatIso8601(timestamp.getTime()),
                    temperature
            );

            final GenericTemperatureSample sample = new GenericTemperatureSample();
            sample.setTimestamp(timestamp.getTimeInMillis());
            sample.setTemperature(temperature / 100f);
            sample.setTemperatureLocation(GenericTemperatureSample.LOCATION_WRIST);
            sample.setTemperatureType(GenericTemperatureSample.TYPE_SKIN);
            samples.add(sample);

            timestamp.add(Calendar.MINUTE, 1);
        }

        timestamp.add(Calendar.MINUTE, -1);

        return persistSamples(samples);
    }

    protected boolean persistSamples(final List<GenericTemperatureSample> samples) {
        try (DBHandler handler = GBApplication.acquireDB()) {
            final DaoSession session = handler.getDaoSession();

            final HuamiCoordinator coordinator = (HuamiCoordinator) getDevice().getDeviceCoordinator();
            final GenericTemperatureSampleProvider sampleProvider = coordinator.getTemperatureSampleProvider(getDevice(), session);

            sampleProvider.persistSamples(samples, getContext());
        } catch (final Exception e) {
            GB.toast(getContext(), "Error saving temperature samples", Toast.LENGTH_LONG, GB.ERROR, e);
            return false;
        }

        return true;
    }

    @Override
    protected String getLastSyncTimeKey() {
        return "lastTemperatureTimeMillis";
    }
}
