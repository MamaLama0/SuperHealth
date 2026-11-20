/*  Copyright (C) 2026 Virgil Bulens, oddballza

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
import app.superhealth.engine.entities.GenericWeightSampleDao;
import app.superhealth.engine.entities.MiScaleWeightSampleDao;

public class SchemaUpdate_140 implements DBUpdateScript {
    @Override
    public void upgradeSchema(final SQLiteDatabase db) {
        addImpedanceColumn(db, MiScaleWeightSampleDao.TABLENAME, MiScaleWeightSampleDao.Properties.ImpedanceOhm.columnName);
        addImpedanceColumn(db, GenericWeightSampleDao.TABLENAME, GenericWeightSampleDao.Properties.ImpedanceOhm.columnName);
    }

    private static void addImpedanceColumn(final SQLiteDatabase db, final String table, final String column) {
        if (!DBHelper.existsColumn(table, column, db)) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN \"" + column + "\" INTEGER;");
        }
    }

    @Override
    public void downgradeSchema(final SQLiteDatabase db) {
    }
}
