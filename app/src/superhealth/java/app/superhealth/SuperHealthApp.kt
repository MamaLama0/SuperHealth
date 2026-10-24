package app.superhealth

import app.superhealth.bridge.Bridge
import app.superhealth.bridge.SyncWorker
import app.superhealth.nutrition.NutriApp
import app.superhealth.training.WorkoutApp
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs

/**
 * SuperHealth : le moteur Gadgetbridge (module LocalHealth) démarre en premier, puis les modules nutrition (La Bouffe)
 * et entraînement (La Salle), puis le pont qui les relie.
 */
class SuperHealthApp : GBApplication() {
    override fun onCreate() {
        super.onCreate()
        SuperPrefs.init(this)
        app.superhealth.modules.Modules.init(this)
        app.superhealth.coach.Coach.init(this)
        app.superhealth.bridge.RunImport.init(this)
        app.superhealth.meals.MealPlanner.init(this)
        // Module montre désactivé : le moteur reste chargé (base de données) mais ne se connecte à rien.
        if (!SuperPrefs.settings.value.watchModule) {
            getPrefs().preferences.edit().putBoolean(GBPrefs.AUTO_CONNECT_BLUETOOTH, false).apply()
        }
        // Le questionnaire de SuperHealth remplace l'accueil de Gadgetbridge.
        getPrefs().preferences.edit().putBoolean("first_run", false).apply()
        NutriApp.init(this)
        WorkoutApp.init(this)
        Bridge.start(this)
        SyncWorker.schedule(this)
        app.superhealth.backup.BackupWorker.schedule(this)
        app.superhealth.update.UpdateWorker.schedule(this)
        // icône du lanceur : l'icône pixel-art de l'Aventure (1.2) avait pu désactiver l'alias de base
        runCatching {
            val launcher = android.content.ComponentName(this, "app.superhealth.Launcher")
            if (packageManager.getComponentEnabledSetting(launcher) != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
                packageManager.setComponentEnabledSetting(launcher, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    android.content.pm.PackageManager.DONT_KILL_APP)
        }
    }

    /** Écrans Gadgetbridge (réglages de la montre, association…) : police et formes de SuperHealth. */
    override fun onActivityThemed(activity: android.app.Activity) {
        activity.theme.applyStyle(nodomain.freeyourgadget.gadgetbridge.R.style.SuperHealth_GBOverlay, true)
    }
}
