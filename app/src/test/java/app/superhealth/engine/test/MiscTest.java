package app.superhealth.engine.test;

import org.junit.Assert;
import org.junit.Test;

import app.superhealth.engine.service.btle.GattCharacteristic;

public class MiscTest extends TestBase {
    @Test
    public void testGattCharacteristic() {
        GattCharacteristic desc = GattCharacteristic.lookup(GattCharacteristic.CHARACTERISTIC_HEART_RATE_CONTROL_POINT.uuid);
        Assert.assertEquals("Heart Rate Control Point", desc.prettyName);
    }
}
