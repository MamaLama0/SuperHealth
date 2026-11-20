package app.superhealth.engine.service.devices.soundcore.devices;

import app.superhealth.engine.impl.GBDevice;

public class SoundcoreLiberty4NCProtocol extends SoundcoreLibertyProtocol {
    public SoundcoreLiberty4NCProtocol(final GBDevice device) {
        super(device);
    }

    /**
     * Environment detection, transportation mode
     */
    @Override
    protected byte[] getAdvancedAudioModeTrailer() {
        return new byte[]{0x01, 0x00};
    }
}
