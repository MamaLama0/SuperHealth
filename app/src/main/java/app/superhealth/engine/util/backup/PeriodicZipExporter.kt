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
package app.superhealth.engine.util.backup

import androidx.work.ListenableWorker
import app.superhealth.engine.util.PeriodicExporter

object PeriodicZipExporter: PeriodicExporter() {
    override fun getWorkerClass(): Class<out ListenableWorker> {
        return ZipExportWorker::class.java
    }

    override fun getKeyPrefix(): String {
        return "zip_"
    }

    override fun getFileMimeType(): String {
        return "application/zip"
    }

    override fun getFileExtension(): String {
        return "zip"
    }
}
