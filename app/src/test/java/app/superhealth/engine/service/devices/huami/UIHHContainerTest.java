package app.superhealth.engine.service.devices.huami;

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import app.superhealth.engine.test.TestBase;

public class UIHHContainerTest extends TestBase {
    @Test
    @Ignore("helper test for development, remove this while debugging")
    public void localTest() throws IOException {
        byte[] bytes = Files.readAllBytes(new File("/storage/downloads/uihh.bin").toPath());
        final UIHHContainer uihhContainer = UIHHContainer.fromRawBytes(bytes);
    }
}
