/*  Copyright (C) 2021-2024 Daniel Dakhno, José Rebelo

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
package app.superhealth.engine.service.devices.sony.headphones.protocol.impl;

import java.util.List;

import app.superhealth.engine.deviceevents.GBDeviceEvent;
import app.superhealth.engine.devices.sony.headphones.SonyHeadphonesCapabilities;
import app.superhealth.engine.devices.sony.headphones.SonyHeadphonesCoordinator;
import app.superhealth.engine.devices.sony.headphones.prefs.AdaptiveVolumeControl;
import app.superhealth.engine.devices.sony.headphones.prefs.AmbientSoundControl;
import app.superhealth.engine.devices.sony.headphones.prefs.AmbientSoundControlButtonMode;
import app.superhealth.engine.devices.sony.headphones.prefs.AudioLDAC;
import app.superhealth.engine.devices.sony.headphones.prefs.AudioUpsampling;
import app.superhealth.engine.devices.sony.headphones.prefs.AutomaticPowerOff;
import app.superhealth.engine.devices.sony.headphones.prefs.ButtonFunctionNcAmbient;
import app.superhealth.engine.devices.sony.headphones.prefs.ButtonModes;
import app.superhealth.engine.devices.sony.headphones.prefs.EqualizerCustomBands;
import app.superhealth.engine.devices.sony.headphones.prefs.EqualizerPreset;
import app.superhealth.engine.devices.sony.headphones.prefs.PauseWhenTakenOff;
import app.superhealth.engine.devices.sony.headphones.prefs.QuickAccess;
import app.superhealth.engine.devices.sony.headphones.prefs.SoundPosition;
import app.superhealth.engine.devices.sony.headphones.prefs.SpeakToChatConfig;
import app.superhealth.engine.devices.sony.headphones.prefs.SpeakToChatEnabled;
import app.superhealth.engine.devices.sony.headphones.prefs.SurroundMode;
import app.superhealth.engine.devices.sony.headphones.prefs.TouchSensor;
import app.superhealth.engine.devices.sony.headphones.prefs.VoiceAssistant;
import app.superhealth.engine.devices.sony.headphones.prefs.VoiceNotifications;
import app.superhealth.engine.devices.sony.headphones.prefs.CaptureVoiceDuringCall;
import app.superhealth.engine.devices.sony.headphones.prefs.ConnectTwoDevices;
import app.superhealth.engine.devices.sony.headphones.prefs.ServiceLink;
import app.superhealth.engine.devices.sony.headphones.prefs.WideAreaTap;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.devices.sony.headphones.protocol.Request;
import app.superhealth.engine.service.devices.sony.headphones.protocol.MessageType;
import app.superhealth.engine.service.devices.sony.headphones.protocol.impl.v1.params.BatteryType;
import app.superhealth.engine.util.DeviceHelper;

public abstract class AbstractSonyProtocolImpl {
    private final GBDevice device;

    public AbstractSonyProtocolImpl(final GBDevice device) {
        this.device = device;
    }

    protected GBDevice getDevice() {
        return this.device;
    }

    protected SonyHeadphonesCoordinator getCoordinator() {
        return (SonyHeadphonesCoordinator) getDevice().getDeviceCoordinator();
    }

    public abstract Request getAmbientSoundControl();

    public abstract Request setAmbientSoundControl(final AmbientSoundControl config);


    public abstract Request setAdaptiveVolumeControl(final AdaptiveVolumeControl config);

    public abstract Request getAdaptiveVolumeControl();

    public abstract Request setSpeakToChatEnabled(final SpeakToChatEnabled config);

    public abstract Request getSpeakToChatEnabled();

    public abstract Request setSpeakToChatConfig(final SpeakToChatConfig config);

    public abstract Request getSpeakToChatConfig();

    public abstract Request getNoiseCancellingOptimizerState();

    public abstract Request getAudioCodec();

    public abstract Request getBattery(final BatteryType batteryType);

    public abstract Request getFirmwareVersion();

    public abstract Request getAudioUpsampling();

    public abstract Request getAudioLDAC();

    public abstract Request setAudioLDAC(final AudioLDAC config);

    public abstract Request getButtonFunctionNcAmbient();

    public abstract Request setButtonFunctionNcAmbient(final ButtonFunctionNcAmbient config);

    public abstract Request setAudioUpsampling(final AudioUpsampling config);

    public abstract Request getAutomaticPowerOff();

    public abstract Request setAutomaticPowerOff(final AutomaticPowerOff config);

    public abstract Request setWideAreaTap(final WideAreaTap config);

    public abstract Request getWideAreaTap();

    public abstract Request getCaptureVoiceDuringCall();

    public abstract Request setCaptureVoiceDuringCall(final CaptureVoiceDuringCall config);

    public abstract Request getServiceLink();

    public abstract Request setServiceLink(final ServiceLink config);

    public abstract Request applyServiceLink(final ServiceLink config);

    public abstract Request setConnectTwoDevices(final ConnectTwoDevices config);

    public abstract Request getConnectTwoDevices();

    public abstract Request getButtonModes();

    public abstract Request setButtonModes(final ButtonModes config);

    public abstract Request getQuickAccess();

    public abstract Request setQuickAccess(final QuickAccess quickAccess);

    public abstract Request getAmbientSoundControlButtonMode();

    public abstract Request setAmbientSoundControlButtonMode(final AmbientSoundControlButtonMode ambientSoundControlButtonMode);

    public abstract Request getPauseWhenTakenOff();

    public abstract Request setPauseWhenTakenOff(final PauseWhenTakenOff config);

    public abstract Request getEqualizer();

    public abstract Request setEqualizerPreset(final EqualizerPreset config);

    public abstract Request setEqualizerCustomBands(final EqualizerCustomBands config);

    public abstract Request getSoundPosition();

    public abstract Request setSoundPosition(final SoundPosition config);

    public abstract Request getSurroundMode();

    public abstract Request setSurroundMode(final SurroundMode config);

    public abstract Request getTouchSensor();

    public abstract Request setTouchSensor(final TouchSensor config);

    public abstract Request getVoiceAssistant();

    public abstract Request setVoiceAssistant(final VoiceAssistant config);

    public abstract Request getVoiceNotifications();

    public abstract Request setVoiceNotifications(final VoiceNotifications config);

    public abstract Request getVoiceNotificationsVolume();

    public abstract Request setVoiceNotificationsVolume(final VoiceNotifications config);

    public abstract Request startNoiseCancellingOptimizer(final boolean start);

    public abstract Request powerOff();

    public abstract Request reboot();

    public abstract Request factoryReset();

    public abstract Request getVolume();

    public abstract Request setVolume(final int volume);

    public abstract List<? extends GBDeviceEvent> handlePayload(final MessageType messageType, final byte[] payload);

    protected boolean supports(final SonyHeadphonesCapabilities capability) {
        return getCoordinator().getCapabilities(device).contains(capability);
    }
}
