package app.superhealth.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.superhealth.SuperPrefs
import app.superhealth.bridge.Bridge
import app.superhealth.bridge.HealthConnectHub
import app.superhealth.bridge.Watch
import app.superhealth.health.data.DeviceUi
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.ManualLogStore
import app.superhealth.health.data.Metric
import app.superhealth.health.data.deviceStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import app.superhealth.nutrition.ui.AppViewModel as NutritionViewModel
import app.superhealth.training.ui.AppViewModel as TrainingViewModel

/**
 * État de la coque SuperHealth. Les écrans des modules gardent leur ViewModel ([nutrition], [training]) ;
 * celui-ci ajoute la montre (ex-LocalHealth), Health Connect et la navigation entre modules.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SuperViewModel(app: Application) : AndroidViewModel(app) {
    lateinit var nutrition: NutritionViewModel
        private set
    lateinit var training: TrainingViewModel
        private set

    fun bind(n: NutritionViewModel, t: TrainingViewModel) {
        nutrition = n; training = t
    }

    val settings = SuperPrefs.settings

    /** Route demandée par un widget ou une notification (« training:… » pour le module entraînement). */
    val pendingRoute = MutableStateFlow<String?>(null)

    /** Montres associées ; vide quand le module montre est désactivé. */
    val devices: StateFlow<List<DeviceUi>> = settings.map { it.watchModule }.distinctUntilChanged()
        .flatMapLatest { deviceStateFlow(app) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _refresh = MutableStateFlow(0)
    /** Incrémenté quand les écrans doivent relire leurs données (synchro terminée, saisie, retour dans l'app). */
    val refresh: StateFlow<Int> = _refresh.asStateFlow()

    val syncing = MutableStateFlow(false)

    /** Dernière synchro de montre demandée automatiquement (retour dans l'app, connexion). */
    private var lastWatchSync = 0L

    init {
        // Synchro de montre terminée (occupée → libre) : données relues, pas et pesées recopiés vers la nutrition.
        devices.distinctUntilChangedBy { list -> list.map { it.address to it.busy } }
            .drop(1)
            .onEach { HealthRepository.invalidate(); Bridge.pullWatch(app); bump() }
            .launchIn(viewModelScope)
        // Montre qui vient de se (re)connecter : ses mesures sont récupérées si la dernière synchro date de plus de 15 min.
        // (connectée et libre : juste après la connexion, la montre est occupée par son initialisation).
        devices.map { list -> list.filter { it.connected && !it.busy }.map { it.address }.toSet() }.distinctUntilChanged()
            .onEach { ready0 ->
                val now = System.currentTimeMillis()
                val ready = devices.value.filter { it.address in ready0 }
                // Montre Garmin jamais interrogée et aucune activité en base (nouvel appairage, app réinitialisée) :
                // on rapatrie d'abord les activités qu'elle garde en mémoire.
                val history = ready.filter { Watch.supportsHistory(it.device) && !Watch.historyRequested(it.device) }
                    .filter { d -> runCatching { HealthRepository.workouts(0L, now).none { it.device.address == d.address } }.getOrDefault(false) }
                history.forEach { runCatching { Watch.fetchHistory(it.device) } }
                if (ready.isNotEmpty() && now - lastWatchSync > 15 * 60_000L) {
                    lastWatchSync = now
                    (ready - history.toSet()).forEach { runCatching { Watch.sync(it.device) } }
                }
            }.launchIn(viewModelScope)
        ManualLogStore.get(app).changes.drop(1).onEach { HealthRepository.invalidate(); bump() }.launchIn(viewModelScope)
    }

    fun bump() { _refresh.value++ }

    fun onResume(ctx: Context) {
        if (Watch.enabled) runCatching { GBApplication.deviceService().requestDeviceInfo() }
        // Montre déconnectée (après une mise à jour, hors de portée…) : reconnexion tout de suite plutôt qu'au prochain essai du moteur.
        if (Watch.enabled) runCatching { Watch.connectAll() }
        // Montre pas synchronisée depuis 15 min : on récupère ses mesures (courbe cardio, pas, sommeil) à l'ouverture.
        val now = System.currentTimeMillis()
        val ready = if (Watch.enabled) Watch.devices().filter { it.isInitialized && !it.isBusy } else emptyList()
        // Pas encore connectée : la synchro se fera à la connexion (voir init).
        if (ready.isNotEmpty() && now - lastWatchSync > 15 * 60_000L) {
            lastWatchSync = now
            ready.forEach { runCatching { Watch.sync(it) } }
        }
        val s = settings.value
        val stale = System.currentTimeMillis() - s.lastHcSync > 15 * 60_000L
        viewModelScope.launch {
            if (s.healthConnect && stale) runCatching { HealthConnectHub.sync(ctx.applicationContext) }
            runCatching { Bridge.pullWatch(ctx.applicationContext) }
            bump()
        }
    }

    /** Tout synchroniser : montres, puis Health Connect. */
    fun syncNow(ctx: Context) {
        Watch.syncAll()
        viewModelScope.launch {
            syncing.value = true
            if (settings.value.healthConnect) runCatching { HealthConnectHub.sync(ctx.applicationContext) }
            runCatching { Bridge.pullWatch(ctx.applicationContext) }
            HealthRepository.invalidate()
            syncing.value = false
            bump()
        }
    }

    fun syncDevice(d: DeviceUi) = Watch.sync(d.device)

    fun fetchHistory(d: DeviceUi) = Watch.fetchHistory(d.device)

    fun setWatchModule(on: Boolean) {
        Watch.setEnabled(on)
        HealthRepository.invalidate()
        bump()
    }

    fun log(metric: Metric, value: Double, timestampMs: Long = System.currentTimeMillis()) {
        viewModelScope.launch { ManualLogStore.get(getApplication()).add(metric, timestampMs, value) }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch { ManualLogStore.get(getApplication()).delete(id) }
    }
}
