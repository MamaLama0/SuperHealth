package nodomain.freeyourgadget.gadgetbridge.database.schema;

import android.database.sqlite.SQLiteDatabase;
import nodomain.freeyourgadget.gadgetbridge.database.DBUpdateScript;

/** SuperHealth 158 was allocated to WHOOP before upstream 158 added Xiaomi's into-bed timestamp. */
public class GadgetbridgeUpdate_160 implements DBUpdateScript {
    @Override public void upgradeSchema(final SQLiteDatabase db) {
        new GadgetbridgeUpdate_158().upgradeSchema(db);
    }
    @Override public void downgradeSchema(final SQLiteDatabase db) { }
}
