package app.superhealth

import app.superhealth.bridge.Bridge
import app.superhealth.bridge.SyncWorker
import app.superhealth.nutrition.NutriApp
import app.superhealth.training.WorkoutApp
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * SuperHealth : le moteur Gadgetbridge (module LocalHealth) démarre en premier, puis les modules nutrition (La Bouffe)
 * et entraînement (La Salle), puis le pont qui les relie.
 */
class SuperHealthApp : GBApplication() {
    override fun onCreate() {
        super.onCreate()
        SuperPrefs.init(this)
        app.superhealth.league.GroupIdentity.init(this)
        app.superhealth.modules.Modules.init(this)
        app.superhealth.hiking.Hiking.load(this)
        app.superhealth.league.HikingProgress.load(this)
        app.superhealth.stretch.Stretch.load(this)
        app.superhealth.meditation.Meditation.load(this)
        app.superhealth.league.League.load(this); app.superhealth.league.GroupHistory.load(this)
        runCatching { app.superhealth.league.GroupRecovery.resumeRestore(this) }.onFailure {
            app.superhealth.league.League.configure(this) { state -> state.copy(relayEnabled = false, syncError = "Restauration du Groupe à terminer dans les paramètres.") }
        }
        app.superhealth.league.SocialInbox.load(this)
        app.superhealth.league.League.pendingMediaPosts = app.superhealth.league.SocialOutbox.pendingIds(this)
        app.superhealth.ui.ProfilePhoto.load(this)
        app.superhealth.coach.Coach.init(this)
        app.superhealth.bridge.RunImport.init(this)
        app.superhealth.bridge.HcHeart.init(this)
        app.superhealth.meals.MealPlanner.init(this)
        app.superhealth.meds.Meds.init(this)
        app.superhealth.wellbeing.Journal.init(this)
        app.superhealth.wellbeing.MoodMoments.init(this)
        app.superhealth.wellbeing.Checkin.init(this)
        app.superhealth.wellbeing.Cycle.init(this)
        app.superhealth.wellbeing.Reminders.schedule(this)
        app.superhealth.bridge.WatchOnline.install(this)
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
        app.superhealth.league.LeagueWorker.schedule(this)
        app.superhealth.backup.BackupWorker.schedule(this)
        app.superhealth.update.UpdateWorker.schedule(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            app.superhealth.modules.Modules.upgradeBundled(this@SuperHealthApp)
            app.superhealth.modules.Modules.migrateSocial(this@SuperHealthApp)
        }
        // icône du lanceur : exactement un alias actif (l'ancien code réactivait le rose à côté de la couleur choisie)
        runCatching { app.superhealth.ui.LauncherIcon.repair(this, NutriApp.repository.prefs.settings.value.accent) }
    }

    /** Écrans Gadgetbridge (réglages de la montre, association…) : police et formes de SuperHealth. */
    override fun onActivityThemed(activity: android.app.Activity) {
        activity.theme.applyStyle(nodomain.freeyourgadget.gadgetbridge.R.style.SuperHealth_GBOverlay, true)
    }
}
