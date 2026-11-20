/*  Copyright (C) 2017-2026 Carsten Pfeiffer, Daniele Gobbetti, José Rebelo, Thomas Kuehne

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
package app.superhealth.engine.export;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

import app.superhealth.engine.entities.BaseActivitySummary;
import app.superhealth.engine.model.ActivityTrack;

public interface ActivityTrackExporter {
    void performExport(ActivityTrack track, File targetFile, @Nullable BaseActivitySummary summary) throws IOException, GPXTrackEmptyException;

    class GPXTrackEmptyException extends Exception {
    }
}
