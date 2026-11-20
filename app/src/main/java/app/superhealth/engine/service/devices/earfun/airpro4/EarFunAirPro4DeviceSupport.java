package app.superhealth.engine.service.devices.earfun.airpro4;

import app.superhealth.engine.service.devices.earfun.EarFunDeviceSupport;

public class EarFunAirPro4DeviceSupport extends EarFunDeviceSupport {

    @Override
    protected EarFunAirPro4Protocol createDeviceProtocol() {
        return new EarFunAirPro4Protocol(getDevice());
    }
}
