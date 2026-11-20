package app.superhealth.engine.service.devices.haylou;

import static app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_HAYLOU_S35_ANC_EQ_PRESET;
import static app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_HAYLOU_S35_ANC_GAME_MODE;
import static app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_HAYLOU_S35_ANC_LDAC_MODE;
import static app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_HAYLOU_S35_ANC_MULTIPOINT;
import static app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_HAYLOU_S35_ANC_AUDIO_MODE;

import android.content.SharedPreferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.deviceevents.GBDeviceEvent;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.AbstractHeadphoneBTBRDeviceSupport;
import app.superhealth.engine.service.btbr.TransactionBuilder;

public class HaylouS35AncSupport extends AbstractHeadphoneBTBRDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(HaylouS35AncSupport.class);
    private static final int MAX_MTU = 1024;
    private static final UUID SERIAL_PORT_SERVICE = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private HaylouS35AncProtocol protocol;

    public HaylouS35AncSupport() {
        super(LOG, MAX_MTU);
        addSupportedService(SERIAL_PORT_SERVICE);
    }

    @Override
    public boolean useAutoConnect() {
        return false;
    }

    @Override
    protected int getRfcommChannel() {
        return 10;
    }

    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        getDevice().setFirmwareVersion("N/A");
        protocol = new HaylouS35AncProtocol();
        builder.write(protocol.encodeHandshake());
        builder.setDeviceState(GBDevice.State.INITIALIZED);
        return builder;
    }

    @Override
    public void onSocketRead(final byte[] data) {
        for (final GBDeviceEvent event : protocol().decodeResponse(data)) {
            if (event != null) {
                evaluateGBDeviceEvent(event);
            }
        }
    }

    @Override
    public void onFindDevice(final boolean start) {
        sendCommand("find device", protocol.encodeFindDevice(start));
    }

    @Override
    public void onFactoryReset() {
        sendCommand("factory reset", protocol.encodeReset());
    }

    public void onSendConfiguration(final String config) {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());

        switch (config) {
            case PREF_HAYLOU_S35_ANC_AUDIO_MODE:
                sendCommand("set audio mode", protocol.encodeAudioMode(prefs.getString(config, "off")));
                return;
            case PREF_HAYLOU_S35_ANC_GAME_MODE:
                sendCommand("set game mode", protocol.encodeGameMode(prefs.getBoolean(config, false)));
                return;
            case PREF_HAYLOU_S35_ANC_LDAC_MODE:
                sendCommand("set LDAC mode", protocol.encodeLdacMode(prefs.getBoolean(config, false)));
                return;
            case PREF_HAYLOU_S35_ANC_MULTIPOINT:
                sendCommand("set multipoint", protocol.encodeMultipoint(prefs.getBoolean(config, false)));
                return;
            case PREF_HAYLOU_S35_ANC_EQ_PRESET:
                sendCommand("set EQ preset", protocol.encodeEqPreset(prefs.getString(config, "default")));
                return;
            default:
                super.onSendConfiguration(config);
        }
    }

    private HaylouS35AncProtocol protocol() {
        if (protocol == null) {
            protocol = new HaylouS35AncProtocol();
        }
        return protocol;
    }

    private void sendCommand(final String taskName, final byte[] payload) {
        if (payload == null) {
            return;
        }

        final TransactionBuilder builder = createTransactionBuilder(taskName);
        builder.write(payload);
        builder.queue();
    }
}
