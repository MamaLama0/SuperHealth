/*  Copyright (C) 2023-2024 Frank Ertl

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
package app.superhealth.engine.service.devices.withingssteelhr.communication.message.incoming;

import android.location.LocationManager;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.database.DBHandler;
import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.entities.BaseActivitySummary;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.Device;
import app.superhealth.engine.entities.User;
import app.superhealth.engine.externalevents.opentracks.OpenTracksController;
import app.superhealth.engine.service.devices.withingssteelhr.WithingsSteelHRDeviceSupport;
import app.superhealth.engine.service.devices.withingssteelhr.activity.WithingsActivityType;
import app.superhealth.engine.service.devices.withingssteelhr.communication.datastructures.LiveWorkoutEnd;
import app.superhealth.engine.service.devices.withingssteelhr.communication.datastructures.LiveWorkoutPauseState;
import app.superhealth.engine.service.devices.withingssteelhr.communication.datastructures.LiveWorkoutStart;
import app.superhealth.engine.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;
import app.superhealth.engine.service.devices.withingssteelhr.communication.datastructures.WithingsStructureType;
import app.superhealth.engine.service.devices.withingssteelhr.communication.datastructures.WorkoutGpsState;
import app.superhealth.engine.service.devices.withingssteelhr.communication.datastructures.WorkoutType;
import app.superhealth.engine.service.devices.withingssteelhr.communication.message.Message;
import app.superhealth.engine.service.devices.withingssteelhr.communication.message.WithingsMessage;
import app.superhealth.engine.service.devices.withingssteelhr.communication.message.WithingsMessageType;
import app.superhealth.engine.util.GB;

public class LiveWorkoutHandler implements IncomingMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(LiveWorkoutHandler.class);
    private final WithingsSteelHRDeviceSupport support;
    private BaseActivitySummary baseActivitySummary;

    public LiveWorkoutHandler(WithingsSteelHRDeviceSupport support) {
        this.support = support;
    }

    @Override
    public void handleMessage(Message message) {
        List<WithingsStructure> data = message.getDataStructures();
        if (data != null) {
            handleLiveData(data);
        }
    }

    private void handleLiveData(List<WithingsStructure> dataList) {
        for (WithingsStructure data : dataList) {
            switch (data.getType()) {
                case WithingsStructureType.LIVE_WORKOUT_START:
                    handleStart((LiveWorkoutStart) data);
                    break;
                case WithingsStructureType.LIVE_WORKOUT_END:
                    handleEnd((LiveWorkoutEnd) data);
                    break;
                case WithingsStructureType.LIVE_WORKOUT_PAUSE_STATE:
                    handlePause((LiveWorkoutPauseState) data);
                    break;
                case WithingsStructureType.WORKOUT_TYPE:
                    handleType((WorkoutType) data);
                    break;
                default:
                    logger.info("Received yet unhandled live workout data of type '" + data.getType() + "' with data '" + GB.hexdump(data.getRawData()) + "'.");
            }
        }
    }

    private void handleStart(LiveWorkoutStart workoutStart) {
        sendGpsState();
        if (baseActivitySummary == null) {
            baseActivitySummary = new BaseActivitySummary();
        }

        baseActivitySummary.setStartTime(workoutStart.getStarttime());
    }

    private void handlePause(LiveWorkoutPauseState workoutPause) {
        // Not sure what to do with these events at the moment so we just log them.
        if (workoutPause.getStarttime() == null) {
            if (workoutPause.getLengthInSeconds() > 0) {
                logger.info("Got workout pause end with duration: " + workoutPause.getLengthInSeconds());
            } else {
                logger.info("Currently no pause happened");
            }
        } else {
            logger.info("Got workout pause started at: " + workoutPause.getStarttime());
        }
    }

    private void handleEnd(LiveWorkoutEnd workoutEnd) {
        OpenTracksController.stopRecording(support.getContext());
        baseActivitySummary.setEndTime(workoutEnd.getEndtime());
        saveBaseActivitySummary();
        baseActivitySummary = null;
    }

    private void handleType(WorkoutType workoutType) {
        WithingsActivityType withingsWorkoutType = WithingsActivityType.fromCode(workoutType.getActivityType());
        OpenTracksController.startRecording(support.getContext(), withingsWorkoutType.toActivityKind());
        if (baseActivitySummary == null) {
            baseActivitySummary = new BaseActivitySummary();
        }

        baseActivitySummary.setActivityKind(withingsWorkoutType.toActivityKind().getCode());
    }

    private void sendGpsState() {
        Message message = new WithingsMessage((short)(WithingsMessageType.START_LIVE_WORKOUT | 16384), new WorkoutGpsState(isGpsEnabled()));
        support.sendToDevice(message);
    }

    private boolean isGpsEnabled() {
        final LocationManager manager = (LocationManager) support.getContext().getSystemService(support.getContext().LOCATION_SERVICE );
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER );
    }

    private void saveBaseActivitySummary() {
        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            DaoSession session = dbHandler.getDaoSession();
            Device device = DBHelper.getDevice(support.getDevice(), session);
            User user = DBHelper.getUser(session);
            baseActivitySummary.setDevice(device);
            baseActivitySummary.setUser(user);
            session.getBaseActivitySummaryDao().insertOrReplace(baseActivitySummary);
        } catch (Exception ex) {
            GB.toast(support.getContext(), "Error saving activity summary", Toast.LENGTH_LONG, GB.ERROR, ex);
        }
    }
}
