package app.superhealth.engine.database.schema;

import android.database.sqlite.SQLiteDatabase;

import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.database.DBUpdateScript;
import app.superhealth.engine.entities.GarminSpo2SampleDao;

public class SchemaUpdate_111 implements DBUpdateScript {
    @Override
    public void upgradeSchema(final SQLiteDatabase db) {
        if (!DBHelper.existsColumn(GarminSpo2SampleDao.TABLENAME, GarminSpo2SampleDao.Properties.TypeNum.columnName, db)) {
            final String statement = "ALTER TABLE " + GarminSpo2SampleDao.TABLENAME + " ADD COLUMN \""
                    + GarminSpo2SampleDao.Properties.TypeNum.columnName + "\" INTEGER NOT NULL DEFAULT 2;";
            db.execSQL(statement);
        }
    }

    @Override
    public void downgradeSchema(SQLiteDatabase database) {

    }
}
