package app.superhealth.engine.service.devices.garmin;

import app.superhealth.engine.service.devices.garmin.messages.GFDIMessage;

public interface MessageHandler {
    GFDIMessage handle(GFDIMessage message);
}
