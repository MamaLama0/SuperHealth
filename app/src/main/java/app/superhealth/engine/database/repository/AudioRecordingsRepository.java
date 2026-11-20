/*  Copyright (C) 2025 José Rebelo

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
package app.superhealth.engine.database.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import de.greenrobot.dao.query.QueryBuilder;
import app.superhealth.engine.GBApplication;
import app.superhealth.engine.database.DBHandler;
import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.entities.AudioRecording;
import app.superhealth.engine.entities.AudioRecordingDao;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.Device;
import app.superhealth.engine.impl.GBDevice;

public class AudioRecordingsRepository {
    private static final Logger LOG = LoggerFactory.getLogger(AudioRecordingsRepository.class);

    @NonNull
    public static List<AudioRecording> listAll(final GBDevice gbDevice) {
        try (DBHandler db = GBApplication.acquireDB()) {
            final DaoSession daoSession = db.getDaoSession();
            final Device dbDevice = DBHelper.findDevice(gbDevice, daoSession);
            if (dbDevice != null) {
                final AudioRecordingDao dao = daoSession.getAudioRecordingDao();
                final Long deviceId = dbDevice.getId();
                final QueryBuilder<AudioRecording> qb = dao.queryBuilder()
                        .where(AudioRecordingDao.Properties.DeviceId.eq(deviceId))
                        .orderDesc(AudioRecordingDao.Properties.Timestamp);
                return qb.build().list();
            }
        } catch (final Exception e) {
            LOG.error("Error listing audio recordings from db", e);
        }

        return Collections.emptyList();
    }

    @Nullable
    public static AudioRecording getByTimestamp(final GBDevice gbDevice, final long timestamp) {
        try (DBHandler db = GBApplication.acquireDB()) {
            final DaoSession daoSession = db.getDaoSession();
            final Device dbDevice = DBHelper.findDevice(gbDevice, daoSession);
            if (dbDevice != null) {
                final AudioRecordingDao dao = daoSession.getAudioRecordingDao();
                final Long deviceId = dbDevice.getId();
                final QueryBuilder<AudioRecording> qb = dao.queryBuilder()
                        .where(AudioRecordingDao.Properties.DeviceId.eq(deviceId),
                                AudioRecordingDao.Properties.Timestamp.eq(timestamp))
                        .orderDesc(AudioRecordingDao.Properties.Timestamp);
                final List<AudioRecording> list = qb.build().list();
                if (list.isEmpty()) {
                    return null;
                } else if (list.size() > 1) {
                    // this is not possible
                    LOG.error("More than 1 recording with the same timestamp {}", timestamp);
                }

                return list.get(0);
            }
        } catch (final Exception e) {
            LOG.error("Error listing audio recordings from db", e);
        }

        return null;
    }

    public static boolean insertOrReplace(@Nullable final GBDevice gbDevice, final AudioRecording entity) {
        try (DBHandler db = GBApplication.acquireDB()) {
            final DaoSession daoSession = db.getDaoSession();
            if (gbDevice != null) {
                final Device device = DBHelper.getDevice(gbDevice, daoSession);
                entity.setDevice(device);
            } else if (entity.getDevice() == null) {
                // FIXME this is ugly
                throw new Exception("Attempting to upsert entity without a device");
            }

            daoSession.insertOrReplace(entity);
        } catch (final Exception e) {
            LOG.error("Error inserting or replacing audio recording", e);
            return false;
        }
        return true;
    }

    public static boolean delete(final AudioRecording entity) {
        try (DBHandler db = GBApplication.acquireDB()) {
            final DaoSession daoSession = db.getDaoSession();
            daoSession.delete(entity);
        } catch (final Exception e) {
            LOG.error("Error deleting audio recording", e);
            return false;
        }
        return true;
    }
}
