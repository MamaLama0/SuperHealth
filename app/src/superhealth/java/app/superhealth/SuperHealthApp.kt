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
    }
}
