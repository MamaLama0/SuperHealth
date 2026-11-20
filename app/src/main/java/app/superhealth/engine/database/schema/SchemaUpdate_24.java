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
package app.superhealth.engine.database.schema;

import android.database.sqlite.SQLiteDatabase;

import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.database.DBUpdateScript;
import app.superhealth.engine.entities.AlarmDao;

public class SchemaUpdate_24 implements DBUpdateScript {
    @Override
    public void upgradeSchema(SQLiteDatabase db) {
        // Probably it is better to hardcode then using constants since constants could change after refactoring and potentially break migration
        db.execSQL("UPDATE DEVICE SET Type=170 where (Type=80 and Manufacturer like 'Fossil%')");
    }

    @Override
    public void downgradeSchema(SQLiteDatabase db) {
    }
}
