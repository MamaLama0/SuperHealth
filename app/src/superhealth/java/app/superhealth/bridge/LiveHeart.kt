package app.superhealth.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService

/** Mesure envoyée en direct par la montre (fréquence cardiaque, pas cumulés depuis la mesure précédente). */
data class LiveSample(val timeMs: Long, val bpm: Int?, val steps: Int?)

/**
 * Fréquence cardiaque en direct : le moteur Gadgetbridge demande à la montre ses mesures temps réel tant qu'au moins
 * un écran (graphique du tableau de bord, séance en cours) écoute, puis les coupe pour préserver la batterie.
 */
object LiveHeart {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Une montre connectée sait-elle envoyer ses mesures en direct ? */
    fun available(): Boolean = Watch.devices().any { d ->
        d.isInitialized && runCatching { d.deviceCoordinator.supportsRealtimeData(d) }.getOrDefault(false)
    }

    private fun enable(on: Boolean) = Watch.devices().filter { it.isInitialized }.forEach { d ->
        runCatching {
            GBApplication.deviceService(d).onEnableRealtimeSteps(on)
            GBApplication.deviceService(d).onEnableRealtimeHeartRateMeasurement(on)
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
                    val steps = s.steps.takeIf { it > 0 }
                    if (bpm != null || steps != null) trySend(LiveSample(System.currentTimeMillis(), bpm, steps))
                }
            }
            val lbm = LocalBroadcastManager.getInstance(ctx)
            lbm.registerReceiver(receiver, IntentFilter(DeviceService.ACTION_REALTIME_SAMPLES))
            enable(true)
            awaitClose { enable(false); lbm.unregisterReceiver(receiver) }
        }.shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000))
    }
}
