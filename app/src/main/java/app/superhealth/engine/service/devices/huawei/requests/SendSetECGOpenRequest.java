package app.superhealth.engine.service.devices.huawei.requests;

import java.util.List;

import app.superhealth.engine.devices.huawei.HuaweiPacket;
import app.superhealth.engine.devices.huawei.packets.ECG;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;

public class SendSetECGOpenRequest extends Request {

    public SendSetECGOpenRequest(HuaweiSupportProvider support) {
        super(support);
        this.serviceId = ECG.id;
        this.commandId = ECG.SetECGOpen.id;
    }

    @Override
    protected boolean requestSupported() {
        return supportProvider.getDeviceState().supportsECGOpen();
    }

    @Override
    protected List<byte[]> createRequest() throws Request.RequestCreationException {

        try {
            return new ECG.SetECGOpen.Request(paramsProvider, (byte) 0).serialize();
        } catch (HuaweiPacket.CryptoException e) {
            throw new Request.RequestCreationException(e);
        }
    }
}