package app.superhealth.engine.database.schema;

import android.database.sqlite.SQLiteDatabase;

import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.database.DBUpdateScript;
import app.superhealth.engine.entities.AlarmDao;

public class SchemaUpdate_112 implements DBUpdateScript {
    @Override
    public void upgradeSchema(SQLiteDatabase db) {
        if (!DBHelper.existsColumn(AlarmDao.TABLENAME, AlarmDao.Properties.SoundCode.columnName, db)) {
            String ADD_COLUMN_SOUND_CODE = "ALTER TABLE " + AlarmDao.TABLENAME + " ADD COLUMN "
                    + AlarmDao.Properties.SoundCode.columnName + " INTEGER NOT NULL DEFAULT 0;";
            db.execSQL(ADD_COLUMN_SOUND_CODE);
        }

        if (!DBHelper.existsColumn(AlarmDao.TABLENAME, AlarmDao.Properties.Backlight.columnName, db)) {
            String ADD_COLUMN_SOUND_CODE = "ALTER TABLE " + AlarmDao.TABLENAME + " ADD COLUMN "
                    + AlarmDao.Properties.Backlight.columnName + " BOOLEAN NOT NULL DEFAULT TRUE;";
            db.execSQL(ADD_COLUMN_SOUND_CODE);
        }
    }

    @Override
    public void downgradeSchema(SQLiteDatabase db) {
    }
}
