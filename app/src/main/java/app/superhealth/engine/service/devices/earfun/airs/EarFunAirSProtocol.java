package app.superhealth.engine.service.devices.earfun.airs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.DeviceType;
import app.superhealth.engine.service.devices.earfun.EarFunPacketEncoder;
import app.superhealth.engine.service.devices.earfun.EarFunProtocol;
import app.superhealth.engine.service.devices.earfun.prefs.Equalizer;
import app.superhealth.engine.util.Prefs;

public class EarFunAirSProtocol extends EarFunProtocol {
    private static final Logger LOG = LoggerFactory.getLogger(EarFunAirSProtocol.class);

    @Override
    public byte[] encodeSendConfiguration(String config) {
        if (Equalizer.containsKey(Equalizer.SixBandEqualizer, config)) {
            Prefs prefs = getDevicePrefs();
            return EarFunPacketEncoder.encodeSetEqualizerSixBands(prefs);
        }
        return super.encodeSendConfiguration(config);
    }

    protected EarFunAirSProtocol(GBDevice device) {
        super(device);
        DeviceType type = device.getType();
    }
}
