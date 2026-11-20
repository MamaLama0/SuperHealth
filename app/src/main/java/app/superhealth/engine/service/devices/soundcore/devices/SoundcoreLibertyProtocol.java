package app.superhealth.engine.service.devices.soundcore.devices;


import app.superhealth.engine.devices.soundcore.SoundcoreEarbudsCoordinator.ButtonAction;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.devices.soundcore.protocol.impl.v1.SoundcoreProtocolImplV1;

public class SoundcoreLibertyProtocol extends SoundcoreProtocolImplV1 {

    public SoundcoreLibertyProtocol(GBDevice device) {
        super(device);
    }

    @Override
    protected byte[] getAdvancedAudioModeTrailer() {
        return new byte[]{0x01};
    }

    @Override
    protected int getTapFunctionGroup(final ButtonAction buttonAction) {
        switch (buttonAction) {
            case SINGLE_TAP:
            case DOUBLE_TAP: return 6;
            case TRIPLE_TAP: return 4;
            case LONG_PRESS: return 5;
            default: throw new IllegalArgumentException("Unknown button action: " + buttonAction);
        }
    }
}
