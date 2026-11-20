package app.superhealth.engine.database.schema;

import android.database.sqlite.SQLiteDatabase;

import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.database.DBUpdateScript;
import app.superhealth.engine.entities.WorkoutTemplateStepDao;

public class SchemaUpdate_152 implements DBUpdateScript {
    @Override
    public void upgradeSchema(final SQLiteDatabase db) {
        if (!DBHelper.existsColumn(WorkoutTemplateStepDao.TABLENAME, WorkoutTemplateStepDao.Properties.DurationPlus.columnName, db)) {
            final String statement = "ALTER TABLE " + WorkoutTemplateStepDao.TABLENAME + " ADD COLUMN \""
                + WorkoutTemplateStepDao.Properties.DurationPlus.columnName + "\" INTEGER;";
            db.execSQL(statement);
        }
    }

    @Override
    public void downgradeSchema(final SQLiteDatabase db) {
    }
}
