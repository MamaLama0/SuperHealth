package app.superhealth.engine.database.schema;

import android.database.sqlite.SQLiteDatabase;
import app.superhealth.engine.database.DBUpdateScript;

/** SuperHealth 158 was allocated to WHOOP before upstream 158 added Xiaomi's into-bed timestamp. */
public class SchemaUpdate_160 implements DBUpdateScript {
    @Override public void upgradeSchema(final SQLiteDatabase db) {
        new SchemaUpdate_158().upgradeSchema(db);
    }
    @Override public void downgradeSchema(final SQLiteDatabase db) { }
}
