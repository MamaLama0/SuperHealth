/*  Copyright (C) 2018-2024 Carsten Pfeiffer, Felix Konstantin Maurer,
    Ganblejs, José Rebelo, Petr Vaněk

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
package app.superhealth.engine.database

import androidx.work.ListenableWorker
import app.superhealth.engine.util.PeriodicExporter

object PeriodicDbExporter: PeriodicExporter() {
    override fun getWorkerClass(): Class<out ListenableWorker> {
        return DatabaseExportWorker::class.java
    }

    /**
     * DB export has no prefix for backwards compatibility
     */
    override fun getKeyPrefix(): String {
        return ""
    }

    override fun getFileMimeType(): String {
        return "application/x-sqlite3"
    }

    override fun getFileExtension(): String {
        return "db"
    }
}
