package app.superhealth.engine.service.devices.xiaomi.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.entities.BaseActivitySummary;
import app.superhealth.engine.entities.Device;
import app.superhealth.engine.entities.XiaomiActivityFile;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.devices.xiaomi.activity.XiaomiActivityFileId.DetailType;
import app.superhealth.engine.test.TestBase;

public class XiaomiActivityTrackProviderRawFileTest extends TestBase {
    private static final long START_SECONDS = 1_750_000_000L;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private GBDevice gbDevice;
    private Device device;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        gbDevice = createDummyGDevice("AA:BB:CC:DD:EE:01");
        device = DBHelper.getDevice(gbDevice, daoSession);
    }

    @Test
    public void returnsFileOfRequestedType() throws IOException {
        final File summaryFile = register(DetailType.SUMMARY, START_SECONDS, true);
        final File detailsFile = register(DetailType.DETAILS, START_SECONDS, true);
        final File gpsFile = register(DetailType.GPS_TRACK, START_SECONDS, true);

        assertEquals(detailsFile, XiaomiActivityTrackProvider.getRawFile(gbDevice, summary(), DetailType.DETAILS));
        assertEquals(gpsFile, XiaomiActivityTrackProvider.getRawFile(gbDevice, summary(), DetailType.GPS_TRACK));
        assertEquals(summaryFile, XiaomiActivityTrackProvider.getRawFile(gbDevice, summary(), DetailType.SUMMARY));
    }

    @Test
    public void nullWithoutGpsRow() throws IOException {
        register(DetailType.DETAILS, START_SECONDS, true);

        assertNull(XiaomiActivityTrackProvider.getRawFile(gbDevice, summary(), DetailType.GPS_TRACK));
    }

    @Test
    public void nullWhenFileMissingOnDisk() throws IOException {
        register(DetailType.DETAILS, START_SECONDS, false);

        assertNull(XiaomiActivityTrackProvider.getRawFile(gbDevice, summary(), DetailType.DETAILS));
    }

    @Test
    public void ignoresOtherWorkouts() throws IOException {
        register(DetailType.DETAILS, START_SECONDS + 60, true);

        assertNull(XiaomiActivityTrackProvider.getRawFile(gbDevice, summary(), DetailType.DETAILS));
    }

    private BaseActivitySummary summary() {
        final BaseActivitySummary summary = new BaseActivitySummary();
        summary.setStartTime(new Date(START_SECONDS * 1000L));
        summary.setEndTime(new Date((START_SECONDS + 600) * 1000L));
        return summary;
    }

    private File register(final DetailType detailType, final long timestamp, final boolean onDisk) throws IOException {
        final File file = new File(tmp.getRoot(), timestamp + "_" + detailType.name() + ".bin");
        if (onDisk) {
            assertTrue(file.createNewFile());
        }
        final XiaomiActivityFile entry = new XiaomiActivityFile();
        entry.setDeviceId(device.getId());
        entry.setTimestamp(timestamp);
        entry.setType(1);
        entry.setSubtype(1);
        entry.setDetailType(detailType.getCode());
        entry.setTimezone(0);
        entry.setVersion(1);
        entry.setFilePath(file.getAbsolutePath());
        daoSession.getXiaomiActivityFileDao().insertOrReplace(entry);
        return file;
    }
}
