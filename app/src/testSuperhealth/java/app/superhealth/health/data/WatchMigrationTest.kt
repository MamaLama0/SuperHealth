package app.superhealth.health.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import app.superhealth.engine.database.schema.SchemaUpdate_160
import app.superhealth.engine.entities.XiaomiSleepTimeSampleDao
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class WatchMigrationTest {
    @Test fun legacySuperhealth158GetsTheXiaomiColumnWithoutLosingRowsAndCanRunTwice() {
        SQLiteDatabase.create(null).use { db ->
            val table = XiaomiSleepTimeSampleDao.TABLENAME
            val column = XiaomiSleepTimeSampleDao.Properties.IntoBedTime.columnName
            db.execSQL("CREATE TABLE $table (_id INTEGER PRIMARY KEY, retained TEXT)")
            db.execSQL("INSERT INTO $table VALUES (1, 'keep')")
            SchemaUpdate_160().upgradeSchema(db)
            SchemaUpdate_160().upgradeSchema(db)
            db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                val names = mutableListOf<String>()
                while (c.moveToNext()) names += c.getString(1)
                assertEquals(1, names.count { it == column })
            }
            db.rawQuery("SELECT retained FROM $table", null).use { c -> assertTrue(c.moveToFirst()); assertEquals("keep", c.getString(0)) }
        }
    }
}
