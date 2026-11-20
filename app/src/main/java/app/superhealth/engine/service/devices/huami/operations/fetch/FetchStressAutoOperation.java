/*  Copyright (C) 2023-2024 Daniel Dakhno, José Rebelo

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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.R;
import app.superhealth.engine.database.DBHandler;
import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.devices.HuamiStressSampleProvider;
import app.superhealth.engine.devices.huami.HuamiCoordinator;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.Device;
import app.superhealth.engine.entities.HuamiStressSample;
import app.superhealth.engine.entities.User;
import app.superhealth.engine.model.StressSample;
import app.superhealth.engine.service.devices.huami.HuamiFetcher;
import app.superhealth.engine.util.GB;

/**
 * An operation that fetches auto stress data.
 */
public class FetchStressAutoOperation extends AbstractRepeatingFetchOperation {
    private static final Logger LOG = LoggerFactory.getLogger(FetchStressAutoOperation.class);

    public FetchStressAutoOperation(final HuamiFetcher fetcher) {
        super(fetcher, HuamiFetchDataType.STRESS_AUTOMATIC);
    }

    @StringRes
    @Override
    public int taskDescription() {
        return R.string.busy_task_fetch_stress_data;
    }

    @Override
    protected boolean handleActivityData(final GregorianCalendar timestamp, final byte[] bytes) {
        final List<HuamiStressSample> samples = new ArrayList<>();

        for (byte b : bytes) {
            if (b == -1) {
                timestamp.add(Calendar.MINUTE, 1);
                continue;
            }

            // 0-39 = relaxed
            // 40-59 = mild
            // 60-79 = moderate
            // 80-100 = high
            final int stress = b & 0xff;

            LOG.trace("Stress (auto) at {}: {}", timestamp.getTime(), stress);

            final HuamiStressSample sample = new HuamiStressSample();
            sample.setTimestamp(timestamp.getTimeInMillis());
            sample.setTypeNum(StressSample.Type.AUTOMATIC.getNum());
            sample.setStress(stress);
            samples.add(sample);

            timestamp.add(Calendar.MINUTE, 1);
        }

        timestamp.add(Calendar.MINUTE, -1);

        return persistSamples(samples);
    }

    protected boolean persistSamples(final List<HuamiStressSample> samples) {
        try (DBHandler handler = GBApplication.acquireDB()) {
            final DaoSession session = handler.getDaoSession();

            final HuamiCoordinator coordinator = (HuamiCoordinator) getDevice().getDeviceCoordinator();
            final HuamiStressSampleProvider sampleProvider = coordinator.getStressSampleProvider(getDevice(), session);

            sampleProvider.persistSamples(samples, getContext());
        } catch (final Exception e) {
            GB.toast(getContext(), "Error saving auto stress samples", Toast.LENGTH_LONG, GB.ERROR, e);
            return false;
        }

        return true;
    }

    @Override
    protected String getLastSyncTimeKey() {
        return "lastStressAutoTimeMillis";
    }
}
