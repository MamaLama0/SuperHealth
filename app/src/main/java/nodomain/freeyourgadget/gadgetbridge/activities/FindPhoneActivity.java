/*  Copyright (C) 2018-2024 Andreas Shimokawa, Anemograph, Carsten Pfeiffer,
    Cre3per, Daniele Gobbetti, Dmitriy Bogdanov, José Rebelo, Pauli Salmenrinne

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
package nodomain.freeyourgadget.gadgetbridge.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;

import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.FindPhoneSound;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;


public class FindPhoneActivity extends AbstractGBActivity {
    private static final Logger LOG = LoggerFactory.getLogger(FindPhoneActivity.class);

    public static final String ACTION_FOUND = "nodomain.freeyourgadget.gadgetbridge.findphone.action.reply";
    public static final String ACTION_VIBRATE = "nodomain.freeyourgadget.gadgetbridge.findphone.action.vibrate";
    public static final String ACTION_RING = "nodomain.freeyourgadget.gadgetbridge.findphone.action.ring";

    public static final String EXTRA_RING = "extra_ring";
    public static final String EXTRA_DURATION_SECONDS = "duration_seconds";
    private final Handler timeout = new Handler(Looper.getMainLooper());
    private GBDevice requestingDevice;
    private FindPhoneSound sound;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final GBDevice source = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE);
            if (source != null && requestingDevice != null && !source.getAddress().equals(requestingDevice.getAddress())) return;
            String action = intent.getAction();
            if (action != null) {
                LOG.info("Got action: {}", action);

                switch (action) {
                    case ACTION_FOUND:
                        finish();
                        break;
                    case ACTION_VIBRATE:
                        stopSound();
                        break;
                    case ACTION_RING:
                        playRingtone();
                        break;
                }
            }
        }
    };

    Vibrator mVibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_phone);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_FOUND);
        filter.addAction(ACTION_VIBRATE);
        filter.addAction(ACTION_RING);
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filter);
        ContextCompat.registerReceiver(this, mReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED); // for ACTION_FOUND

        Button foundButton = findViewById(R.id.foundbutton);
        foundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        final boolean ring = intent.getBooleanExtra(EXTRA_RING, true);
        final GBDevice nextDevice = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE);
        if (requestingDevice != null && nextDevice != null && !requestingDevice.getAddress().equals(nextDevice.getAddress()))
            GBApplication.deviceService(requestingDevice).onFindPhone(false);
        requestingDevice = nextDevice;
        timeout.removeCallbacksAndMessages(null);
        final int duration = intent.getIntExtra(EXTRA_DURATION_SECONDS, 0);
        if (duration > 0) timeout.postDelayed(this::finish, Math.min(duration, 256) * 1000L);

        GB.removeNotification(GB.NOTIFICATION_ID_PHONE_FIND, this);

        vibrate();
        if (ring) playRingtone(); else stopSound();
        if (requestingDevice == null) GBApplication.deviceService().onFindPhone(true);
        else GBApplication.deviceService(requestingDevice).onFindPhone(true);
    }

    private void vibrate() {
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        long[] vibrationPattern = new long[]{0, 1000, 600};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect vibrationEffect = VibrationEffect.createWaveform(vibrationPattern, 0);

            mVibrator.vibrate(vibrationEffect);
        } else {
            mVibrator.vibrate(vibrationPattern, 0);
        }
    }

    private void playRingtone() {
        if (sound == null) sound = new FindPhoneSound(this);
        sound.start(GBApplication.getPrefs().getString(GBPrefs.PING_TONE, null));
    }

    private void stopVibration() {
        if (mVibrator != null) mVibrator.cancel();
    }

    private void stopSound() {
        if (sound != null) sound.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeout.removeCallbacksAndMessages(null);
        stopVibration();
        stopSound();
        if (!isChangingConfigurations()) {
            if (requestingDevice == null) GBApplication.deviceService().onFindPhone(false);
            else GBApplication.deviceService(requestingDevice).onFindPhone(false);
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        unregisterReceiver(mReceiver);
    }
}
