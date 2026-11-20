package app.superhealth.engine.devices;

import androidx.annotation.NonNull;

public interface DeviceDeletionProgress {
    enum Stage {
        DATABASE,
        FILES,
    }

    void onProgress(@NonNull Stage stage, int done, int total);
}
