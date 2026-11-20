package app.superhealth.engine.database.schema;

import android.database.sqlite.SQLiteDatabase;

import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.database.DBUpdateScript;
import app.superhealth.engine.entities.CyclingSampleDao;

public class SchemaUpdate_156 implements DBUpdateScript {
    @Override
    public void upgradeSchema(final SQLiteDatabase db) {
        DBHelper.addColumnIfNotExist(CyclingSampleDao.TABLENAME, CyclingSampleDao.Properties.Cadence.columnName, "INTEGER", db);
    }

    @Override
    public void downgradeSchema(final SQLiteDatabase db) {
    }
}
