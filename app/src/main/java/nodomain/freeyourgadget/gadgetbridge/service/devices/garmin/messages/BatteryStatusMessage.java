/*  Copyright (C) 2026 Thomas Kuehne

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;

public class BatteryStatusMessage extends GFDIMessage {
    protected static final Logger LOG = LoggerFactory.getLogger(BatteryStatusMessage.class);

    /** Pourcentage lu dans le message (SuperHealth), -1 s'il n'est pas exploitable. */
    private final int percent;
    private final float voltage;

    public BatteryStatusMessage(GarminMessage garminMessage) {
        this(garminMessage, -1, 0f);
    }

    public BatteryStatusMessage(GarminMessage garminMessage, int percent, float voltage) {
        this.garminMessage = garminMessage;
        this.percent = percent;
        this.voltage = voltage;
        this.statusMessage = this.getStatusMessage();
    }

    public static BatteryStatusMessage parseIncoming(MessageReader reader, GarminMessage originalGarminMessage) {
        final int wireStatus = reader.readByte() & 0xff; // 0
        final float voltage = (reader.readByte() & 0xff) / 100.0f; // 1
        final int percent = reader.readByte() & 0xff; // 2
        // 3-6 - unknown meaning

        final String status = switch (wireStatus & 0x70) {
            case 0x10 -> "good";
            case 0x20 -> "ok";
            case 0x30 -> "low";
            case 0x40 -> "critical";
            case 0x50 -> "new";
            case 0x70 -> "invalid";
            default -> Integer.toBinaryString(wireStatus & 0x70);
        };

        LOG.info("Received BATTERY_STATUS message with status={}, voltage={} and capacity={}",
                status, voltage, percent);

        // SuperHealth : niveau transmis à l'app (Forerunner 255 et autres montres qui l'envoient ici)
        final boolean valid = (wireStatus & 0x70) != 0x70 && percent <= 100;
        return new BatteryStatusMessage(originalGarminMessage, valid ? percent : -1, voltage);
    }

    @Override
    public List<GBDeviceEvent> getGBDeviceEvent() {
        if (percent < 0) {
            return Collections.emptyList();
        }
        final GBDeviceEventBatteryInfo event = new GBDeviceEventBatteryInfo();
        event.level = percent;
        if (voltage > 0) {
            event.voltage = voltage;
        }
        return Collections.singletonList(event);
    }

    @Override
    protected boolean generateOutgoing() {
        return false;
    }
}
