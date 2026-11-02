package nodomain.freeyourgadget.gadgetbridge.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.R;

/** Audible phone locator, routed to the phone speaker, with reversible volume and audio focus. */
public final class FindPhoneSound {
    private static final Logger LOG = LoggerFactory.getLogger(FindPhoneSound.class);
    private final Context context;
    private final AudioManager audio;
    private final AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
    private MediaPlayer player;
    private Integer originalVolume;
    private AudioFocusRequest focus;

    public FindPhoneSound(final Context context) {
        this.context = context.getApplicationContext();
        this.audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void start(final String configuredTone) {
        // A repeated watch request must not overwrite the original volume with the forced maximum.
        if (player != null) return;
        if (audio != null && originalVolume == null) {
            originalVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM);
            try {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            } catch (final SecurityException e) {
                LOG.warn("Cannot adjust phone locator volume", e);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attributes).setOnAudioFocusChangeListener(change -> {}).build();
                audio.requestAudioFocus(focus);
            }
        }
        final Uri fallback = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.find_phone_alarm);
        final Uri chosen = configuredTone == null || configuredTone.isBlank() ? fallback : Uri.parse(configuredTone);
        if (!play(chosen) && !chosen.equals(fallback)) play(fallback);
        if (player == null) stop();
    }

    private boolean play(final Uri tone) {
        final MediaPlayer next = new MediaPlayer();
        try {
            next.setAudioAttributes(attributes);
            next.setDataSource(context, tone);
            next.setLooping(true);
            next.setVolume(1f, 1f);
            if (audio != null) {
                for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                    if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        next.setPreferredDevice(device);
                        break;
                    }
                }
            }
            next.prepare();
            next.start();
            player = next;
            return true;
        } catch (final Exception e) {
            LOG.warn("Cannot play phone locator tone", e);
            next.release();
            return false;
        }
    }

    public void stop() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (audio != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focus != null) audio.abandonAudioFocusRequest(focus);
            if (originalVolume != null) {
                try {
                    // Respect a volume change the user made during the alert.
                    if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == audio.getStreamMaxVolume(AudioManager.STREAM_ALARM))
                        audio.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0);
                } catch (final SecurityException e) {
                    LOG.warn("Cannot restore phone locator volume", e);
                }
            }
        }
        focus = null;
        originalVolume = null;
    }
}
