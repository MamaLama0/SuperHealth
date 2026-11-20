/*  Copyright (C) 2015-2026 Andreas Shimokawa, Carsten Pfeiffer, José Rebelo, Thomas Kuehne

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
package app.superhealth.engine.service.btle.actions;

import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import app.superhealth.engine.util.GB;

public class SetProgressAction extends PlainAction {

    private final String text;
    private final boolean ongoing;
    private final int percentage;
    private final Context context;

    /**
     * When run, will update the progress notification.
     *
     * @param textRes Text shown in the notification
     * @param ongoing State of action, true when the action is still being performed
     * @param percentage Current percentage indicating how far along the action has progressed
     * @param context Context in which to create the notification
     */
    public SetProgressAction(@StringRes int textRes, boolean ongoing, int percentage, @NonNull Context context) {
        this.text = context.getString(textRes);
        this.ongoing = ongoing;
        this.percentage = percentage;
        this.context = context;
    }

    @Override
    public boolean run(@NonNull BluetoothGatt gatt) {
        GB.updateInstallNotification(this.text, this.ongoing, this.percentage, this.context);

        final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(context);
        final Intent intent = new Intent(GB.ACTION_SET_PROGRESS_BAR);
        if (ongoing && percentage == 0) {
            intent.putExtra(GB.PROGRESS_BAR_INDETERMINATE, true);
        } else {
            intent.putExtra(GB.PROGRESS_BAR_PROGRESS, percentage);
        }
        broadcastManager.sendBroadcast(intent);

        return true;
    }

    @NonNull
    @Override
    public String toString() {
        return getCreationTime() + " " + getClass().getSimpleName() + ": " + text + "; " + percentage + "%";
    }
}
