/*  Copyright (C) 2024 Me7c7

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
import app.superhealth.engine.entities.HuaweiWorkoutDataSample;
import app.superhealth.engine.entities.HuaweiWorkoutDataSampleDao;
import app.superhealth.engine.entities.HuaweiWorkoutSummarySampleDao;

public class SchemaUpdate_96 implements DBUpdateScript {
    @Override
    public void upgradeSchema(final SQLiteDatabase db) {
        if (!DBHelper.existsColumn(HuaweiWorkoutDataSampleDao.TABLENAME, HuaweiWorkoutDataSampleDao.Properties.HangTime.columnName, db)) {
            final String statement = "ALTER TABLE " + HuaweiWorkoutDataSampleDao.TABLENAME + " ADD COLUMN \""
                    + HuaweiWorkoutDataSampleDao.Properties.HangTime.columnName + "\" INTEGER NOT NULL DEFAULT -1;";
            db.execSQL(statement);
        }
        if (!DBHelper.existsColumn(HuaweiWorkoutDataSampleDao.TABLENAME, HuaweiWorkoutDataSampleDao.Properties.ImpactHangRate.columnName, db)) {
            final String statement = "ALTER TABLE " + HuaweiWorkoutDataSampleDao.TABLENAME + " ADD COLUMN \""
                    + HuaweiWorkoutDataSampleDao.Properties.ImpactHangRate.columnName + "\" INTEGER NOT NULL DEFAULT -1;";
            db.execSQL(statement);
        }
        if (!DBHelper.existsColumn(HuaweiWorkoutDataSampleDao.TABLENAME, HuaweiWorkoutDataSampleDao.Properties.RideCadence.columnName, db)) {
            final String statement = "ALTER TABLE " + HuaweiWorkoutDataSampleDao.TABLENAME + " ADD COLUMN \""
                    + HuaweiWorkoutDataSampleDao.Properties.RideCadence.columnName + "\" INTEGER NOT NULL DEFAULT -1;";
            db.execSQL(statement);
        }
        if (!DBHelper.existsColumn(HuaweiWorkoutDataSampleDao.TABLENAME, HuaweiWorkoutDataSampleDao.Properties.Ap.columnName, db)) {
            final String statement = "ALTER TABLE " + HuaweiWorkoutDataSampleDao.TABLENAME + " ADD COLUMN \""
                    + HuaweiWorkoutDataSampleDao.Properties.Ap.columnName + "\" FLOAT NOT NULL DEFAULT 0;";
            db.execSQL(statement);
        }
        if (!DBHelper.existsColumn(HuaweiWorkoutDataSampleDao.TABLENAME, HuaweiWorkoutDataSampleDao.Properties.Vo.columnName, db)) {
            final String statement = "ALTER TABLE " + HuaweiWorkoutDataSampleDao.TABLENAME + " ADD COLUMN \""
                    + HuaweiWorkoutDataSampleDao.Properties.Vo.columnName + "\" FLOAT NOT NULL DEFAULT 0;";
            db.execSQL(statement);
        }
        if (!DBHelper.existsColumn(HuaweiWorkoutDataSampleDao.TABLENAME, HuaweiWorkoutDataSampleDao.Properties.Gtb.columnName, db)) {
            final String statement = "ALTER TABLE " + HuaweiWorkoutDataSampleDao.TABLENAME + " ADD COLUMN \""
                    + HuaweiWorkoutDataSampleDao.Properties.Gtb.columnName + "\" FLOAT NOT NULL DEFAULT 0;";
            db.execSQL(statement);
        }
        if (!DBHelper.existsColumn(HuaweiWorkoutDataSampleDao.TABLENAME, HuaweiWorkoutDataSampleDao.Properties.Vr.columnName, db)) {
            final String statement = "ALTER TABLE " + HuaweiWorkoutDataSampleDao.TABLENAME + " ADD COLUMN \""
                    + HuaweiWorkoutDataSampleDao.Properties.Vr.columnName + "\" FLOAT NOT NULL DEFAULT 0;";
            db.execSQL(statement);
        }
        if (!DBHelper.existsColumn(HuaweiWorkoutDataSampleDao.TABLENAME, HuaweiWorkoutDataSampleDao.Properties.Ceiling.columnName, db)) {
            final String statement = "ALTER TABLE " + HuaweiWorkoutDataSampleDao.TABLENAME + " ADD COLUMN \""
                    + HuaweiWorkoutDataSampleDao.Properties.Ceiling.columnName + "\" INTEGER NOT NULL DEFAULT -1;";
            db.execSQL(statement);
        }
        if (!DBHelper.existsColumn(HuaweiWorkoutDataSampleDao.TABLENAME, HuaweiWorkoutDataSampleDao.Properties.Temp.columnName, db)) {
            final String statement = "ALTER TABLE " + HuaweiWorkoutDataSampleDao.TABLENAME + " ADD COLUMN \""
                    + HuaweiWorkoutDataSampleDao.Properties.Temp.columnName + "\" INTEGER NOT NULL DEFAULT -1;";
            db.execSQL(statement);
        }
        if (!DBHelper.existsColumn(HuaweiWorkoutDataSampleDao.TABLENAME, HuaweiWorkoutDataSampleDao.Properties.Spo2.columnName, db)) {
            final String statement = "ALTER TABLE " + HuaweiWorkoutDataSampleDao.TABLENAME + " ADD COLUMN \""
                    + HuaweiWorkoutDataSampleDao.Properties.Spo2.columnName + "\" INTEGER NOT NULL DEFAULT -1;";
            db.execSQL(statement);
        }
        if (!DBHelper.existsColumn(HuaweiWorkoutDataSampleDao.TABLENAME, HuaweiWorkoutDataSampleDao.Properties.Cns.columnName, db)) {
            final String statement = "ALTER TABLE " + HuaweiWorkoutDataSampleDao.TABLENAME + " ADD COLUMN \""
                    + HuaweiWorkoutDataSampleDao.Properties.Cns.columnName + "\" INTEGER NOT NULL DEFAULT -1;";
            db.execSQL(statement);
        }
    }

    @Override
    public void downgradeSchema(final SQLiteDatabase db) {
    }
}
