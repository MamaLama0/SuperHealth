/*  Copyright (C) 2015-2026 Andreas Shimokawa, Carsten Pfeiffer, José Rebelo,
    Julien Pivotto, Steffen Liebergeld

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
package app.superhealth.engine.service.serial;

import android.location.Location;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.UUID;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.deviceevents.GBDeviceEvent;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.Alarm;
import app.superhealth.engine.model.CalendarEventSpec;
import app.superhealth.engine.model.CannedMessagesSpec;
import app.superhealth.engine.model.FindDeviceTarget;
import app.superhealth.engine.model.NotificationSpec;
import app.superhealth.engine.model.Reminder;
import app.superhealth.engine.model.WorldClock;
import app.superhealth.engine.util.preferences.DevicePrefs;

/**
 * @deprecated Use {@link app.superhealth.engine.service.btbr.AbstractBTBRDeviceSupport}
 */
@Deprecated
public abstract class GBDeviceProtocol {

    public static final int RESET_FLAGS_REBOOT = 1;
    public static final int RESET_FLAGS_FACTORY_RESET = 2;

    private final GBDevice mDevice;

    protected GBDeviceProtocol(GBDevice device) {
        mDevice = device;
    }

    @Nullable
    public byte[] encodeNotification(NotificationSpec notificationSpec) {
        return null;
    }

    @Nullable
    public byte[] encodeDeleteNotification(int id) {
        return null;
    }

    @Nullable
    public byte[] encodeSetTime() {
        return null;
    }

    @Nullable
    public byte[] encodeSetCallState(String number, String name, int command) {
        return null;
    }

    @Nullable
    public byte[] encodeSetCannedMessages(CannedMessagesSpec cannedMessagesSpec) {
        return null;
    }

    @Nullable
    public byte[] encodeSetMusicInfo(String artist, String album, String track, int duration, int trackCount, int trackNr) {
        return null;
    }

    @Nullable
    public byte[] encodeVolume(float volume) {
        return null;
    }

    @Nullable
    public byte[] encodeSetMusicState(byte state, int position, int playRate, byte shuffle, byte repeat) {
        return null;
    }

    @Nullable
    public byte[] encodeFirmwareVersionReq() {
        return null;
    }

    @Nullable
    public byte[] encodeAppInfoReq() {
        return null;
    }

    @Nullable
    public byte[] encodeScreenshotReq() {
        return null;
    }

    @Nullable
    public byte[] encodeAppDelete(UUID uuid) {
        return null;
    }

    @Nullable
    public byte[] encodeAppStart(UUID uuid, boolean start) {
        return null;
    }

    @Nullable
    public byte[] encodeAppReorder(UUID[] uuids) {
        return null;
    }

    @Nullable
    public byte[] encodeReset(int flags) {
        return null;
    }

    @Nullable
    public byte[] encodeFindDevice(boolean start) {
        return null;
    }

    @Nullable
    public byte[] encodeFindDevice(final boolean start, @NonNull final FindDeviceTarget target) {
        return encodeFindDevice(start);
    }

    @Nullable
    public byte[] encodeFindPhone(boolean start) {
        return null;
    }

    @Nullable
    public byte[] encodeEnableRealtimeSteps(boolean enable) {
        return null;
    }

    @Nullable
    public byte[] encodeEnableHeartRateSleepSupport(boolean enable) {
        return null;
    }

    @Nullable
    public byte[] encodeEnableRealtimeHeartRateMeasurement(boolean enable) { return null; }

    @Nullable
    public byte[] encodeAddCalendarEvent(CalendarEventSpec calendarEventSpec) {
        return null;
    }

    @Nullable
    public byte[] encodeDeleteCalendarEvent(byte type, long id) {
        return null;
    }

    @Nullable
    public byte[] encodeSendConfiguration(String config) {
        return null;
    }

    @Nullable
    public byte[] encodeTestNewFunction(@Nullable Bundle options) { return null; }

    @Nullable
    public GBDeviceEvent[] decodeResponse(byte[] responseData) {
        return null;
    }

    public GBDevice getDevice() {
        return mDevice;
    }

    @Nullable
    public byte[] encodeSendWeather() {
        return null;
    }

    @Nullable
    public byte[] encodeLedColor(int color) {
        return null;
    }

    @Nullable
    public byte[] encodePowerOff() {
        return null;
    }

    @Nullable
    public byte[] encodeSetAlarms(ArrayList<? extends Alarm> alarms)  {
        return null;
    }

    @Nullable
    public byte[] encodeReminders(ArrayList<? extends Reminder> reminders) {
        return null;
    }

    @Nullable
    public byte[] encodeWorldClocks(ArrayList<? extends WorldClock> clocks) {
        return null;
    }

    @Nullable
    public byte[] encodeFmFrequency(float frequency) {
        return null;
    }

    @Nullable
    public byte[] encodeGpsLocation(Location location) {
        return null;
    }

    @Nullable
    public byte[] encodeFetchRecordedData(int dataTypes) {
        return null;
    }

    protected DevicePrefs getDevicePrefs() {
        return GBApplication.getDevicePrefs(getDevice());
    }
}
