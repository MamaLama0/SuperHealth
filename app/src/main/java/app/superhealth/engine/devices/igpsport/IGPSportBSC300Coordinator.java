package app.superhealth.engine.devices.igpsport;

import java.util.regex.Pattern;

import app.superhealth.engine.R;

public class IGPSportBSC300Coordinator extends IGPSportAbstractCoordinator{

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_igpsport_bsc300;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("BSC300");
    }

}
