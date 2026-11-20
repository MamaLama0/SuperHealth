package app.superhealth.engine.test;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Objects;

import ch.qos.logback.classic.util.ContextInitializer;
import app.superhealth.engine.GBApplication;
import app.superhealth.engine.GBDatabaseManager;
import app.superhealth.engine.GBEnvironment;
import app.superhealth.engine.database.DBHandler;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.DeviceType;
import java.lang.reflect.Field;
import java.util.List;
import app.superhealth.engine.devices.DeviceManager;

import static org.junit.Assert.assertNotNull;

/**
 * Base class for all testcases in SuperHealth that are supposed to run locally
 * with robolectric.
 * <p>
 * Important: To run them, create a run configuration and execute them in the app/
 * directory.
 */
@RunWith(RobolectricTestRunner.class)
// SuperHealth requires Android 8 (API 26); API 23 cannot parse its test manifest.
@Config(sdk = 26, application = GBTestApplication.class)
public abstract class TestBase {
    protected GBApplication app = (GBApplication) RuntimeEnvironment.application;
    protected DaoSession daoSession;
    protected DBHandler dbHandler;

    // Make sure logging is set up for all testcases, so that we can debug problems
    @BeforeClass
    public static void setupSuite() {
        GBEnvironment.setupEnvironment(GBEnvironment.createLocalTestEnvironment());

        // print everything going to android.util.Log to System.out
        System.setProperty("robolectric.logging", "stdout");

        if (System.getProperty(ContextInitializer.CONFIG_FILE_PROPERTY) == null) {
            File workingDir = new File(Objects.requireNonNull(System.getProperty("user.dir")));
            File configFile = new File(workingDir, "src/main/assets/logback.xml");
            System.out.println(configFile.getAbsolutePath());
            System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, configFile.getAbsolutePath());
        }
    }

    @Before
    public void setUp() throws Exception {
        app = (GBApplication) RuntimeEnvironment.application;
        assertNotNull(app);
        assertNotNull(getContext());
        GBDatabaseManager.setupDatabase(app);
        dbHandler = GBApplication.acquireDB();
        daoSession = dbHandler.getDaoSession();
        assertNotNull(daoSession);
    }

    @After
    public void tearDown() throws Exception {
        GBDatabaseManager.closeDatabase();
    }

    protected GBDevice createDummyGDevice(String macAddress) {
        GBDevice dummyGBDevice = new GBDevice(macAddress, "Testie", "Tesie Alias", "Test Folder", DeviceType.TEST);
        dummyGBDevice.setFirmwareVersion("1.2.3");
        dummyGBDevice.setModel("4.0");
        return dummyGBDevice;
    }

    protected Context getContext() {
        return app;
    }

    protected void registerDeviceWithManager(final GBDevice device) {
        try {
            final DeviceManager deviceManager = GBApplication.app().getDeviceManager();
            final Field field = DeviceManager.class.getDeclaredField("deviceList");
            field.setAccessible(true);
            final List<GBDevice> deviceList = (List<GBDevice>) field.get(deviceManager);
            deviceList.clear();
            deviceList.add(device);
        } catch (final Exception e) {
            throw new AssertionError("Failed to register test device", e);
        }
    }
}
