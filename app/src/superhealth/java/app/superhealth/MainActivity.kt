package app.superhealth

import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.flow.first
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import app.superhealth.ui.SuperRoot
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.theme.SuperTheme
import app.superhealth.ui.theme.isDark

class MainActivity : ComponentActivity() {
    private val nutrition: app.superhealth.nutrition.ui.AppViewModel by viewModels {
        app.superhealth.nutrition.ui.AppViewModel.factory(app.superhealth.nutrition.NutriApp.repository)
    }
    private val training: app.superhealth.training.ui.AppViewModel by viewModels()
    private val vm: SuperViewModel by viewModels()

    companion object { const val EXTRA_ROUTE = "route" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.bind(nutrition, training)
        askNotificationsOnce()
        if (savedInstanceState == null) vm.pendingRoute.value = app.superhealth.ui.Routes.external(intent?.getStringExtra(EXTRA_ROUTE))
        if (savedInstanceState == null) receiveLeagueCard(intent)
        enableEdgeToEdge()
        setContent {
            val s by vm.nutrition.settings.collectAsState()
            val dark = s.themeMode.isDark(isSystemInDarkTheme())
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            val sh by SuperPrefs.settings.collectAsState()
            // écran masqué dans les Récents et sans capture
            LaunchedEffect(sh.secureScreen) {
                if (sh.secureScreen) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
            SuperTheme(s.themeMode, s.accent, s.textSize) {
                androidx.compose.foundation.layout.Box {
                    app.superhealth.ui.KeyboardGuard()
                    SuperRoot(vm)
                    if (app.superhealth.security.AppLock.locked)
                        app.superhealth.ui.settings.LockScreen({ moveTaskToBack(true) }) {
                            app.superhealth.security.AppLock.authenticate(this@MainActivity)
                        }
                }
            }
        }
    }

    /** Mises à jour et rappels passent par une notification : autorisation demandée une seule fois, après la prise en main. */
    private fun askNotificationsOnce() {
        val perm = android.Manifest.permission.POST_NOTIFICATIONS
        val prefs = getSharedPreferences("superhealth_perms", MODE_PRIVATE)
        if (android.os.Build.VERSION.SDK_INT < 33 || !SuperPrefs.settings.value.onboarded || checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            prefs.getBoolean("asked_$perm", false)) return
        prefs.edit().putBoolean("asked_$perm", true).apply()
        requestPermissions(arrayOf(perm), 4202)
    }

    /** Lien d'une carte de classement : seul le paramètre « c » est lu, vérifié puis soumis à confirmation (League.receive). */
    private fun receiveLeagueCard(i: Intent?) {
        val d = i?.data ?: return
        if (i.action != Intent.ACTION_VIEW || d.scheme != "superhealth" || d.host != "league") return
        if (d.getQueryParameter("code") != null) lifecycleScope.launch {
            try { app.superhealth.league.InvitationCodes.receive(this@MainActivity, d.toString()) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { app.superhealth.league.League.incomingError.value = true }
        } else app.superhealth.league.League.receive(this, d.getQueryParameter("c").orEmpty())
    }

    /** Raccourcis des widgets et de la notification de séance : l'activité existe déjà. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        app.superhealth.ui.Routes.external(intent.getStringExtra(EXTRA_ROUTE))?.let { vm.pendingRoute.value = it }
        receiveLeagueCard(intent)
    }

    private var nearbyJob: kotlinx.coroutines.Job? = null
    private var socialJob: kotlinx.coroutines.Job? = null
    private var nearby: app.superhealth.league.Nearby? = null

    override fun onStart() {
        super.onStart()
        app.superhealth.security.AppLock.returned()
        app.superhealth.league.LeagueWorker.onOpen(applicationContext)
        socialJob = lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                val s = app.superhealth.league.League.state.value
                if (s.notifyFriends || s.notifyPosts || s.notifyReactions || s.notifyReplies) app.superhealth.league.LeagueWorker.onOpen(applicationContext)
            }
        }
        if (!app.superhealth.league.League.available() || !app.superhealth.league.League.state.value.refreshOnOpen) return
        if (app.superhealth.league.League.state.value.friends.isEmpty()) return
        nearbyJob = lifecycleScope.launch {
            var peer: app.superhealth.league.Nearby? = null
            try {
                peer = app.superhealth.league.Nearby.create(applicationContext, knownOnly = true)
                nearby = peer
                if (!app.superhealth.league.League.available() || !app.superhealth.league.League.state.value.refreshOnOpen) return@launch
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { peer.start() }
                // Découverte seulement pendant que l'app est visible ; pas de polling permanent.
                kotlinx.coroutines.flow.combine(app.superhealth.SuperPrefs.settings, app.superhealth.league.League.state) { settings, league -> settings.leagueEnabled && league.refreshOnOpen }.first { !it }
            } finally { peer?.stop(); if (nearby === peer) nearby = null }
        }
    }

    /** En quittant l'app : widgets à jour (repas, pas et Body Battery de la synchro à l'ouverture). */
    override fun onStop() {
        super.onStop()
        socialJob?.cancel(); socialJob = null
        nearbyJob?.cancel(); nearbyJob = null
        nearby?.stop(); nearby = null
        // départ (Accueil, Récents, écran éteint) : pas l'ouverture d'une activité par l'app
        if (!openingActivity && !isChangingConfigurations) app.superhealth.security.AppLock.left()
        val ctx = applicationContext
        lifecycleScope.launch { runCatching { app.superhealth.ui.widget.DayWidgets.refresh(ctx) } }
    }

    /**
     * Cœur de l'icône dans la couleur d'accent : changé seulement quand l'utilisateur quitte l'app (Accueil, Récents).
     * Désactiver l'alias de lancement ferme la tâche : pas en onStop, qui arrive aussi en ouvrant la recherche de montre,
     * Health Connect ou les réglages système, alors fermés avec elle.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android l'appelle aussi quand l'app ouvre une activité (réglages, recherche de montre…) : pas un départ
        if (!openingActivity && app.superhealth.SuperPrefs.settings.value.onboarded)
            runCatching { app.superhealth.ui.LauncherIcon.apply(applicationContext, vm.nutrition.settings.value.accent) }
    }

    /** Vrai entre l'ouverture d'une activité par l'app et le retour ici. */
    private var openingActivity = false

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        openingActivity = true
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun startActivity(intent: Intent, options: Bundle?) {
        openingActivity = true
        super.startActivity(intent, options)
    }

    override fun onResume() {
        super.onResume()
        openingActivity = false
        vm.onResume(this)
        app.superhealth.security.AppLock.autoPrompt(this)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!app.superhealth.security.AppLock.onResult(requestCode, resultCode == RESULT_OK)) super.onActivityResult(requestCode, resultCode, data)
    }
}
