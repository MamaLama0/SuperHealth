/*  Copyright (C) 2016-2024 Andreas Shimokawa, Carsten Pfeiffer, Taavi Eomäe

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
package app.superhealth.engine;

import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.Lock;

import app.superhealth.engine.database.DBHandler;
import app.superhealth.engine.entities.DaoMaster;
import app.superhealth.engine.entities.DaoSession;

/**
 * Provides low-level access to the database.
 */
public class LockHandler implements DBHandler {
    private static final Logger LOG = LoggerFactory.getLogger(LockHandler.class);

    private final Lock lock;

    private final DaoMaster daoMaster;
    private final DaoSession session;

    private boolean closed = false;

    public LockHandler(final Lock lock,
                       final DaoMaster daoMaster,
                       final DaoSession session) {
        this.lock = lock;
        this.daoMaster = daoMaster;
        this.session = session;
    }

    private void ensureNotClosed() {
        if (closed) {
            throw new IllegalStateException("LockHandler is not in a valid state");
        }
    }

    @Override
    public void close() {
        if (closed) {
            LOG.warn("{} was already closed", lock.getClass().getSimpleName());
            return;
        }
        closed = true;
        LOG.trace("Releasing {}", lock.getClass().getSimpleName());
        lock.unlock();
    }

    @NonNull
    @Override
    public DaoSession getDaoSession() {
        ensureNotClosed();
        return session;
    }

    @NonNull
    @Override
    public SQLiteDatabase getDatabase() {
        ensureNotClosed();
        return daoMaster.getDatabase();
    }
}
