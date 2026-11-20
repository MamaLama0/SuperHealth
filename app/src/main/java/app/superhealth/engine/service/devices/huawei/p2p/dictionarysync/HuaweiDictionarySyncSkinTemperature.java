package app.superhealth.engine.service.devices.huawei.p2p.dictionarysync;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.database.DBHandler;
import app.superhealth.engine.devices.huawei.HuaweiState;
import app.superhealth.engine.devices.huawei.HuaweiDictTypes;
import app.superhealth.engine.devices.huawei.HuaweiTemperatureSampleProvider;
import app.superhealth.engine.devices.huawei.HuaweiUtil;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.HuaweiTemperatureSample;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.TemperatureSample;
import app.superhealth.engine.service.devices.huawei.p2p.HuaweiP2PDataDictionarySyncService;

public class HuaweiDictionarySyncSkinTemperature implements  HuaweiDictionarySyncInterface {
    private final Logger LOG = LoggerFactory.getLogger(HuaweiDictionarySyncSkinTemperature.class);

    @Override
    public int getDataClass() {
        return HuaweiDictTypes.SKIN_TEMPERATURE_CLASS;
    }

    @Override
    public boolean supports(HuaweiState state) {
        return state.supportsTemperature();
    }

    @Override
    public long getLastDataSyncTimestamp(GBDevice gbDevice) {
        try (DBHandler db = GBApplication.acquireDB()) {
            HuaweiTemperatureSampleProvider sleepStatsSampleProvider = new HuaweiTemperatureSampleProvider(gbDevice, db.getDaoSession());
            return sleepStatsSampleProvider.getLastFetchTimestamp();
        } catch (Exception e) {
            LOG.warn("Exception for getting skin temperature start time", e);
        }
        return 0;
    }

    @Override
    public void handleData(Context context, GBDevice gbDevice, List<HuaweiP2PDataDictionarySyncService.DictData> dictData) {
        List<HuaweiTemperatureSample> temperatureSamples = new ArrayList<>();
        for (HuaweiP2PDataDictionarySyncService.DictData dt : dictData) {
            long timestamp = dt.getStartTimestamp();
            long lastTime = Math.max(dt.getEndTimestamp(), dt.getModifyTimestamp());
            Float temperature = null;
            for (HuaweiP2PDataDictionarySyncService.DictData.DictDataValue val : dt.getData()) {
                if (val.getTag() != 10) {
                    LOG.info("skin temperature unexpected tag: {}", val.getTag());
                    continue;
                }
                if (val.getDataType() == HuaweiDictTypes.SKIN_TEMPERATURE_VALUE) {
                    double value = HuaweiUtil.convBytes2Double(val.getValue());
                    if (value >= 20 && value <= 42) {
                        temperature = (float) value;
                    }
                } else {
                    LOG.info("skin temperature unknown data type: {}", val.getDataType());
                }
            }
            if(temperature != null) {
                HuaweiTemperatureSample sample = new HuaweiTemperatureSample();
                sample.setTimestamp(timestamp);
                sample.setLastTimestamp(lastTime);
                sample.setTemperature(temperature);
                sample.setTemperatureType(TemperatureSample.TYPE_SKIN);
                sample.setTemperatureLocation(TemperatureSample.LOCATION_WRIST);
                temperatureSamples.add(sample);
            }
        }
        try (DBHandler db = GBApplication.acquireDB()) {
            final DaoSession session = db.getDaoSession();
            new HuaweiTemperatureSampleProvider(gbDevice, session).persistSamples(temperatureSamples, context);
        } catch (Exception e) {
            LOG.error("Cannot save skin temperature samples, continue");
        }
    }
}
