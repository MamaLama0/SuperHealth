package app.superhealth.engine.devices.igpsport;

import java.util.regex.Pattern;

import app.superhealth.engine.R;

public class IGPSportBSC100MaxCoordinator extends IGPSportAbstractCoordinator {

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_igpsport_bsc100max;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("BSC 100Max");
    }

}
