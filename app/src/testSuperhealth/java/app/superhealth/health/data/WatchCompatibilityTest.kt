package app.superhealth.health.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WatchCompatibilityTest {
    @Test fun updatingGadgetbridgeRetainsEveryPreviouslyRegisteredDeviceIncludingWhoop() {
        val source = File("src/main/java/nodomain/freeyourgadget/gadgetbridge/model/DeviceType.java").readText()
        val names = Regex("(?m)^\\s*([A-Z][A-Z0-9_]+)\\(").findAll(source).map { it.groupValues[1] }.toSet()
        val previous = File("src/testSuperhealth/resources/device-types-before-update.txt").readLines().filter { it.isNotBlank() }.toSet()
        assertTrue("Removed device types: ${previous - names}", names.containsAll(previous))
        assertTrue("WHOOP extension retained", "WHOOP" in names)
        assertTrue("New upstream devices are included", setOf("XIAOMI_WATCH_S5", "HUAWEIWATCHD3", "COROS_PACE_3").all { it in names })
    }
}
