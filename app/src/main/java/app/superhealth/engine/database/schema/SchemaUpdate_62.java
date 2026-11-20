/*  Copyright (C) 2023-2024 Daniel Dakhno

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
import app.superhealth.engine.entities.DeviceDao;

public class SchemaUpdate_62 implements DBUpdateScript {
    @Override
    public void upgradeSchema(SQLiteDatabase db) {
        if (!DBHelper.existsColumn(DeviceDao.TABLENAME, DeviceDao.Properties.TypeName.columnName, db)) {
            String ADD_COLUMN_CPONTAINED_FOLDER = "ALTER TABLE " + DeviceDao.TABLENAME + " ADD COLUMN "
                    + DeviceDao.Properties.TypeName.columnName + " TEXT NOT NULL DEFAULT \"\"";
            db.execSQL(ADD_COLUMN_CPONTAINED_FOLDER);
        }
    }

    @Override
    public void downgradeSchema(SQLiteDatabase db) {
    }
}
