package app.superhealth.engine.service.devices.huawei.p2p.dictionarysync;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.database.DBHandler;
import app.superhealth.engine.devices.huawei.HuaweiState;
import app.superhealth.engine.devices.huawei.HuaweiSleepApneaSampleProvider;
import app.superhealth.engine.devices.huawei.HuaweiUtil;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.HuaweiSleepApneaSample;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.devices.huawei.p2p.HuaweiP2PDataDictionarySyncService;

public class HuaweiDictionarySyncSleepApnea implements  HuaweiDictionarySyncInterface {
    private final Logger LOG = LoggerFactory.getLogger(HuaweiDictionarySyncSleepApnea.class);

    public static final int SLEEP_APNEA_CLASS = 500002;
    public static final int SLEEP_APNEA_LEVEL_VALUE = 500002847;

    @Override
    public int getDataClass() {
        return SLEEP_APNEA_CLASS;
    }

    @Override
    public boolean supports(HuaweiState state) {
        return state.supportsSleepApnea();
    }

    @Override
    public long getLastDataSyncTimestamp(GBDevice gbDevice) {
        try (DBHandler db = GBApplication.acquireDB()) {
            HuaweiSleepApneaSampleProvider sleepApneaSampleProvider = new HuaweiSleepApneaSampleProvider(gbDevice, db.getDaoSession());
            return sleepApneaSampleProvider.getLastFetchTimestamp();
        } catch (Exception e) {
            LOG.warn("Exception for getting sleep apnea start time", e);
        }
        return 0;
    }

    @Override
    public void handleData(Context context, GBDevice gbDevice, List<HuaweiP2PDataDictionarySyncService.DictData> dictData) {
        List<HuaweiSleepApneaSample> sleepApneaSamples = new ArrayList<>();
        for (HuaweiP2PDataDictionarySyncService.DictData dt : dictData) {
            long timestamp = dt.getStartTimestamp();
            long lastTime = Math.max(dt.getEndTimestamp(), dt.getModifyTimestamp());
            Integer level = null;
            for (HuaweiP2PDataDictionarySyncService.DictData.DictDataValue val : dt.getData()) {
                if (val.getTag() == 10) {
                    if (val.getDataType() == SLEEP_APNEA_LEVEL_VALUE) {
                        int value = (int) HuaweiUtil.convBytes2Double(val.getValue());
                        if (value == 1 || value == 2 || value == 3 || value == 4) {
                            level = value;
                        } else {
                            LOG.info("sleep apnea invalid value: {}", value);
                        }
                    } else {
                        LOG.info("sleep apnea unknown data type: {}", val.getDataType());
                    }
                } else {
                    LOG.info("sleep apnea unsupported tag: {}", val.getTag());
                }
            }
            if(level != null) {
                LOG.info("APNEA  timestamp: {}  lastTime: {} level: {}", new Date(timestamp), lastTime, level);
                HuaweiSleepApneaSample sample = new HuaweiSleepApneaSample();
                sample.setTimestamp(timestamp);
                sample.setLastTimestamp(lastTime);
                sample.setLevel(level);
                sleepApneaSamples.add(sample);
            }
        }
        try (DBHandler db = GBApplication.acquireDB()) {
            final DaoSession session = db.getDaoSession();
            new HuaweiSleepApneaSampleProvider(gbDevice, session).persistSamples(sleepApneaSamples, context);
        } catch (Exception e) {
            LOG.error("Cannot save sleep apnea samples, continue");
        }
    }
}
