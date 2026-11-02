package nodomain.freeyourgadget.gadgetbridge.util;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.util.DataSource;
import java.io.IOException;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, application = android.app.Application.class, manifest = Config.NONE)
public class FindPhoneSoundTest {
    private Context context;
    private AudioManager audio;
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        ShadowMediaPlayer.setMediaInfoProvider(source -> new ShadowMediaPlayer.MediaInfo(1000, 0));
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 2, 0);
    }
    @Test public void repeatedRequestsRestoreTheOriginalVolumeAndCleanupIsIdempotent() {
        FindPhoneSound sound = new FindPhoneSound(context);
        sound.start(null);
        assertEquals(audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), audio.getStreamVolume(AudioManager.STREAM_ALARM));
        sound.start(null);
        sound.stop();
        assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_ALARM));
        sound.stop();
        assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_ALARM));
    }
    @Test public void manualVolumeChangeWhileRingingIsPreserved() {
        FindPhoneSound sound = new FindPhoneSound(context);
        sound.start(null);
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 3, 0);
        sound.stop();
        assertEquals(3, audio.getStreamVolume(AudioManager.STREAM_ALARM));
    }
    @Test public void unavailableConfiguredToneFallsBackAndStillRestoresVolume() {
        Uri bad = Uri.parse("content://unavailable/tone");
        ShadowMediaPlayer.addException(DataSource.toDataSource(context, bad), new IOException("missing"));
        FindPhoneSound sound = new FindPhoneSound(context);
        sound.start(bad.toString());
        assertEquals(audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), audio.getStreamVolume(AudioManager.STREAM_ALARM));
        sound.stop();
        assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_ALARM));
    }
}
