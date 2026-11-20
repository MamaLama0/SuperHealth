/*  Copyright (C) 2024 José Rebelo

    This file is part of SuperHealth.

    SuperHealth is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SuperHealth is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package app.superhealth.engine.devices.realme;

import android.util.Pair;
import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.oppo.OppoHeadphonesCoordinator;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.BatteryConfig;
import app.superhealth.engine.service.devices.oppo.OppoUuid;
import app.superhealth.engine.service.devices.oppo.commands.AncConfigValue;
import app.superhealth.engine.service.devices.oppo.commands.EarbudsStatusSide;
import app.superhealth.engine.service.devices.oppo.commands.EarbudsStatusValue;
import app.superhealth.engine.service.devices.oppo.commands.TouchConfigSide;
import app.superhealth.engine.service.devices.oppo.commands.TouchConfigType;
import app.superhealth.engine.service.devices.oppo.commands.TouchConfigValue;

public class RealmeBudsT200Coordinator extends OppoHeadphonesCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("realme Buds T200", Pattern.LITERAL);
    }

    @Override
    public String getManufacturer() {
        return "Realme";
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_realme_buds_t200;
    }

    @Override
    public BatteryConfig[] getBatteryConfig(final GBDevice device) {
        final BatteryConfig battery1 = new BatteryConfig(0, R.drawable.ic_realme_buds_t300_l, R.string.left_earbud);
        final BatteryConfig battery2 = new BatteryConfig(1, R.drawable.ic_realme_buds_t300_r, R.string.right_earbud);
        final BatteryConfig battery3 = new BatteryConfig(2, R.drawable.ic_realme_buds_t300_case, R.string.battery_case);
        return new BatteryConfig[]{battery1, battery2, battery3};
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_realme_buds_t300;
    }

    @Override
    public OppoUuid ctrlUuid(@NonNull GBDevice device) {
        return OppoUuid.STANDARD_SPP;
    }

    @Override
    public boolean canApplyAncMode(
        @NonNull final GBDevice device,
        @NonNull final Map<EarbudsStatusSide, ? extends EarbudsStatusValue> map,
        @NonNull final AncConfigValue mode
    ) {
        return map.values().stream().anyMatch(value -> value == EarbudsStatusValue.READY);
    }

    @Override
    public boolean supportsLdac(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsMultipoint(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsGameMode(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsAnc(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsFindPhone(@NonNull GBDevice device) {
        return true;
    }

    @Override
    protected Map<Pair<TouchConfigSide, TouchConfigType>, List<TouchConfigValue>> getTouchOptions() {
        return new LinkedHashMap<>() {{
            final List<TouchConfigValue> options = Arrays.asList(
                    TouchConfigValue.OFF,
                    TouchConfigValue.PLAY_PAUSE,
                    TouchConfigValue.PREVIOUS,
                    TouchConfigValue.NEXT,
                    TouchConfigValue.VOLUME_UP,
                    TouchConfigValue.VOLUME_DOWN,
                    TouchConfigValue.VOICE_ASSISTANT_REALME,
                    TouchConfigValue.GAME_MODE,
                    TouchConfigValue.NOISE_CONTROL
            );
            put(Pair.create(TouchConfigSide.LEFT, TouchConfigType.TAP_2), options);
            put(Pair.create(TouchConfigSide.LEFT, TouchConfigType.TAP_3), options);
            put(Pair.create(TouchConfigSide.LEFT, TouchConfigType.HOLD), options);
            put(Pair.create(TouchConfigSide.RIGHT, TouchConfigType.TAP_2), options);
            put(Pair.create(TouchConfigSide.RIGHT, TouchConfigType.TAP_3), options);
            put(Pair.create(TouchConfigSide.RIGHT, TouchConfigType.HOLD), options);
        }};
    }
}
