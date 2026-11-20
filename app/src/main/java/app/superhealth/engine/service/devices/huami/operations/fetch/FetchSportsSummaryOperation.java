/*  Copyright (C) 2018-2024 Andreas Shimokawa, Carsten Pfeiffer, Daniel
    Dakhno, Daniele Gobbetti, José Rebelo, Oleg Vasilev, Petr Vaněk

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

import java.util.GregorianCalendar;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.R;
import app.superhealth.engine.database.DBHandler;
import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.devices.DeviceCoordinator;
import app.superhealth.engine.devices.huami.HuamiActivitySummaryParser;
import app.superhealth.engine.entities.BaseActivitySummary;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.Device;
import app.superhealth.engine.entities.User;
import app.superhealth.engine.model.ActivitySummaryParser;
import app.superhealth.engine.service.devices.huami.AbstractHuamiActivityDetailsParser;
import app.superhealth.engine.service.devices.huami.HuamiFetcher;
import app.superhealth.engine.util.GB;

/**
 * An operation that fetches activity data. For every fetch, a new operation must
 * be created, i.e. an operation may not be reused for multiple fetches.
 */
public class FetchSportsSummaryOperation extends AbstractFetchOperation {
    private static final Logger LOG = LoggerFactory.getLogger(FetchSportsSummaryOperation.class);

    public FetchSportsSummaryOperation(final HuamiFetcher fetcher, int fetchCount) {
        super(fetcher, HuamiFetchDataType.SPORTS_SUMMARIES);
        this.fetchCount = fetchCount;
    }

    @StringRes
    @Override
    public int taskDescription() {
        return R.string.busy_task_fetch_sports_summaries;
    }

    @Override
    protected void startFetching() {
        LOG.info("start {}", getName());
        final GregorianCalendar sinceWhen = getLastSuccessfulSyncTime();
        startFetching(HuamiFetchDataType.SPORTS_SUMMARIES.getCode(), sinceWhen);
    }

    @Override
    protected boolean processBufferedData() {
        LOG.info("{} has finished round {}", getName(), fetchCount);

        if (buffer.size() < 2) {
            LOG.warn("Buffer size {} too small for activity summary", buffer.size());
            return false;
        }

        final DeviceCoordinator coordinator = getDevice().getDeviceCoordinator();
        final ActivitySummaryParser summaryParser = coordinator.getActivitySummaryParser(getDevice(), getContext());

        BaseActivitySummary summary = new BaseActivitySummary();
        summary.setStartTime(getLastStartTimestamp().getTime()); // due to a bug this has to be set
        summary.setRawSummaryData(buffer.toByteArray());
        try {
            summary = summaryParser.parseBinaryData(summary, false);
        } catch (final Exception e) {
            GB.toast(getContext(), "Failed to parse activity summary", Toast.LENGTH_LONG, GB.ERROR, e);
            return false;
        }

        if (summary == null) {
            return false;
        }

        summary.setSummaryData(null); // remove json before saving to database,
        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            final DaoSession session = dbHandler.getDaoSession();
            final Device device = DBHelper.getDevice(getDevice(), session);
            final User user = DBHelper.getUser(session);
            summary.setDevice(device);
            summary.setUser(user);
            summary.setRawSummaryData(buffer.toByteArray());
            session.getBaseActivitySummaryDao().insertOrReplace(summary);
        } catch (final Exception ex) {
            GB.toast(getContext(), "Error saving activity summary", Toast.LENGTH_LONG, GB.ERROR, ex);
            return false;
        }

        final AbstractHuamiActivityDetailsParser detailsParser = ((HuamiActivitySummaryParser) summaryParser).getDetailsParser(summary);
        final FetchSportsDetailsOperation nextOperation = new FetchSportsDetailsOperation(summary, detailsParser, fetcher, getLastSyncTimeKey(), fetchCount);
        fetcher.getFetchOperationQueue().add(0, nextOperation);

        return true;
    }

    @Override
    protected String getLastSyncTimeKey() {
        return "lastSportsActivityTimeMillis";
    }
}
