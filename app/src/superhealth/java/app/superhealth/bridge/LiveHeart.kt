package app.superhealth.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService

/** Mesure envoyée en direct par la montre (fréquence cardiaque, pas cumulés depuis la mesure précédente). */
data class LiveSample(val timeMs: Long, val bpm: Int?, val steps: Int?)

/**
 * Fréquence cardiaque en direct : le moteur Gadgetbridge demande à la montre ses mesures temps réel tant qu'au moins
 * un écran (graphique du tableau de bord, séance en cours) écoute, puis les coupe pour préserver la batterie.
 *
 * Batterie de la montre : le mode direct fait émettre la montre chaque seconde. Il n'est donc actif que l'app au premier
 * plan (une écoute restée ouverte, écran éteint ou app en arrière-plan, le laissait tourner des heures), et seul le
 * pouls est demandé (le flux de pas en direct ne servait à rien).
 */
object LiveHeart {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Une montre connectée sait-elle envoyer ses mesures en direct ? */
    fun available(): Boolean = Watch.devices().any { d ->
        d.isInitialized && runCatching { d.deviceCoordinator.supportsRealtimeData(d) }.getOrDefault(false)
    }

    private fun enable(v: Boolean) {
        Watch.devices().filter { it.isInitialized }.forEach { d ->
            runCatching { GBApplication.deviceService(d).onEnableRealtimeHeartRateMeasurement(v) }
        }
    }

    /** Partagé entre les écrans ; la montre reste en mode direct 5 s après le dernier abonné (changement d'écran). */
    val samples: SharedFlow<LiveSample> by lazy {
        callbackFlow {
            val ctx: Context = GBApplication.getContext()
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    @Suppress("DEPRECATION")
                    val s = intent.getSerializableExtra(DeviceService.EXTRA_REALTIME_SAMPLE) as? ActivitySample ?: return
                    val bpm = s.heartRate.takeIf { it in 30..230 }
                    if (bpm != null) trySend(LiveSample(System.currentTimeMillis(), bpm, null))
                }
            }
            val lbm = LocalBroadcastManager.getInstance(ctx)
            lbm.registerReceiver(receiver, IntentFilter(DeviceService.ACTION_REALTIME_SAMPLES))
            // Premier plan seulement : arrêt dès que l'app passe en arrière-plan, reprise à son retour.
            val lifecycle = ProcessLifecycleOwner.get().lifecycle
            val observer = LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_START -> enable(true)
                    Lifecycle.Event.ON_STOP -> enable(false)
                    else -> Unit
                }
            }
            launch(Dispatchers.Main) {
                lifecycle.addObserver(observer)
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) enable(false)
            }
            awaitClose {
                enable(false)
                lbm.unregisterReceiver(receiver)
                scope.launch { withContext(Dispatchers.Main + NonCancellable) { lifecycle.removeObserver(observer) } }
            }
        }.shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000))
    }
}
