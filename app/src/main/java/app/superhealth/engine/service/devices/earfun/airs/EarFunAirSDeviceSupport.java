package app.superhealth.engine.service.devices.earfun.airs;

import app.superhealth.engine.service.devices.earfun.EarFunDeviceSupport;

public class EarFunAirSDeviceSupport extends EarFunDeviceSupport {

    @Override
    protected EarFunAirSProtocol createDeviceProtocol() {
        return new EarFunAirSProtocol(getDevice());
    }
}
