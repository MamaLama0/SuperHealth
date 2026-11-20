package app.superhealth.engine.service.devices.onemore_sonoflow;

import app.superhealth.engine.devices.onemoresonoflow.OneMoreSonoFlowCoordinator;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.btbr.TransactionBuilder;
import app.superhealth.engine.service.serial.AbstractHeadphoneSerialDeviceSupportV2;

public class OneMoreSonoFlowSupport extends AbstractHeadphoneSerialDeviceSupportV2<OneMoreSonoFlowProtocol> {
    public static boolean supportsLdac(final GBDevice device) {
        return ((OneMoreSonoFlowCoordinator) device.getDeviceCoordinator()).supportsLdac(device);
    }

    @Override
    protected OneMoreSonoFlowProtocol createDeviceProtocol() {
        return new OneMoreSonoFlowProtocol(getDevice());
    }

    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        // get some device information
        // TODO: we might not receive some responses, it might be worth requesting them again if that's a significant issue
        //  ticket #4637
        builder.write(OneMorePacket.createGetDeviceInfoPacket());
        builder.write(OneMorePacket.createGetNoiseControlModePacket());
        if (supportsLdac(getDevice())) {
            builder.write(OneMorePacket.createGetLdacModePacket());
        }
        builder.write(OneMorePacket.createGetDualDeviceModePacket());

        builder.setDeviceState(GBDevice.State.INITIALIZED);

        return builder;
    }
}
