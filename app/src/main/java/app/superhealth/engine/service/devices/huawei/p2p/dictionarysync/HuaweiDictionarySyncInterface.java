package app.superhealth.engine.service.devices.huawei.p2p.dictionarysync;

import android.content.Context;

import java.util.List;

import app.superhealth.engine.devices.huawei.HuaweiState;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.devices.huawei.p2p.HuaweiP2PDataDictionarySyncService;

public interface HuaweiDictionarySyncInterface {

    int getDataClass();

    boolean supports(HuaweiState state);

    long getLastDataSyncTimestamp(GBDevice gbDevice);

    void handleData(Context context, GBDevice gbDevice, List<HuaweiP2PDataDictionarySyncService.DictData> dictData);
}
