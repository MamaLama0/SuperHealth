package app.superhealth.engine.database.schema;

import static app.superhealth.engine.entities.GarminSleepStatsSampleDao.Properties.*;

import android.database.sqlite.SQLiteDatabase;

import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.database.DBUpdateScript;
import app.superhealth.engine.entities.GarminSleepStatsSampleDao;

public class SchemaUpdate_154 implements DBUpdateScript {
    @Override
    public void upgradeSchema(final SQLiteDatabase db) {
        final String GarminSleepStats = GarminSleepStatsSampleDao.TABLENAME;
        DBHelper.addColumnIfNotExist(GarminSleepStats, AverageStressDuringSleep.columnName, "FLOAT", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, AwakeTimeScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, AwakeningsCount.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, AwakeningsCountScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, CombinedAwakeScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, DeepSleepScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, InterruptionsScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, LightSleepScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, RemSleepScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, SleepDurationScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, SleepQualityScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, SleepRecoveryScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, SleepRestlessnessScore.columnName, "INTEGER", db);
    }

    @Override
    public void downgradeSchema(final SQLiteDatabase db) {
    }
}
