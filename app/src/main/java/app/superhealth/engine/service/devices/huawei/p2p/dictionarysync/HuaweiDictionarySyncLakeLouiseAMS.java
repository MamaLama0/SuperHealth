package app.superhealth.engine.service.devices.huawei.p2p.dictionarysync;


import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import app.superhealth.engine.devices.huawei.HuaweiState;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.devices.huawei.p2p.HuaweiP2PDataDictionarySyncService;

// TODO: not fully discovered.
public class HuaweiDictionarySyncLakeLouiseAMS implements  HuaweiDictionarySyncInterface {
    private final Logger LOG = LoggerFactory.getLogger(HuaweiDictionarySyncLakeLouiseAMS.class);

    public static final int LAKE_LOUISE_AMS_CLASS = 500006;

    @Override
    public int getDataClass() {
        return LAKE_LOUISE_AMS_CLASS;
    }

    @Override
    public boolean supports(HuaweiState state) {
        return false; // state.supportsAltitude()
    }

    @Override
    public long getLastDataSyncTimestamp(GBDevice gbDevice) {
        return 0;
    }

    @Override
    public void handleData(Context context, GBDevice gbDevice, List<HuaweiP2PDataDictionarySyncService.DictData> dictData) {

    }
}
