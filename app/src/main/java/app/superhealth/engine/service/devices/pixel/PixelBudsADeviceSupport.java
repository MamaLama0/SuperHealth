package app.superhealth.engine.service.devices.pixel;

import java.util.UUID;

import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.btbr.TransactionBuilder;
import app.superhealth.engine.service.serial.AbstractHeadphoneSerialDeviceSupportV2;

public class PixelBudsADeviceSupport extends AbstractHeadphoneSerialDeviceSupportV2<PixelBudsAProtocol> {
    private static final UUID UUID_DEVICE_CTRL = UUID.fromString("df21fe2c-2515-4fdb-8886-f12c4d67927c");

    public PixelBudsADeviceSupport() {
        addSupportedService(UUID_DEVICE_CTRL);
    }

    @Override
    protected PixelBudsAProtocol createDeviceProtocol() {
        return new PixelBudsAProtocol(getDevice());
    }

    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        builder.setDeviceState(GBDevice.State.INITIALIZED);
        return builder;
    }
}
