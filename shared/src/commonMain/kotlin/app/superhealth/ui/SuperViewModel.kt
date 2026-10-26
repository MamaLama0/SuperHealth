package app.superhealth.ui

import app.superhealth.platform.*

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.superhealth.nutrition.ui.AppViewModel as NutritionViewModel
import app.superhealth.training.ui.AppViewModel as TrainingViewModel

/**
 * État de la coque SuperHealth. Les écrans des modules gardent leur ViewModel ([nutrition], [training]) ;
 * celui-ci ajoute Apple Santé (rôle de la montre et de Health Connect sur Android) et la navigation entre modules.
 */
class SuperViewModel(app: Application) : AndroidViewModel(app) {
    lateinit var nutrition: NutritionViewModel
        private set
    lateinit var training: TrainingViewModel
        private set

    fun bind(n: NutritionViewModel, t: TrainingViewModel) {
        nutrition = n; training = t
    }

    val settings = SuperPrefs.settings

    /** Route demandée par un raccourci ou une notification (« training:… » pour le module entraînement). */
    val pendingRoute = MutableStateFlow<String?>(null)

    /** Sources de santé actives (Apple Santé) ; vide quand le module est désactivé. */
    val devices: StateFlow<List<DeviceUi>> = deviceStateFlow(app)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _refresh = MutableStateFlow(0)
    /** Incrémenté quand les écrans doivent relire leurs données (synchro terminée, saisie, retour dans l'app). */
    val refresh: StateFlow<Int> = _refresh.asStateFlow()

    val syncing = MutableStateFlow(false)

    init {
        ManualLogStore.get(app).changes.drop(1).onEach { HealthRepository.invalidate(); bump() }.launchIn(viewModelScope)
    }

    fun bump() { _refresh.value++ }

    /** Retour dans l'app : jour courant, données de Santé relues, échange avec Santé s'il date de plus de 15 min. */
    fun onResume(ctx: Context) {
        if (::nutrition.isInitialized) nutrition.refreshDay()
        HealthRepository.invalidate()
        val s = settings.value
        val stale = System.currentTimeMillis() - s.lastHcSync > 15 * 60_000L
        viewModelScope.launch {
            if (s.healthConnect && stale) runCatching { HealthConnectHub.sync(ctx.applicationContext) }
            runCatching { Bridge.pullWatch(ctx.applicationContext) }
            bump()
        }
    }

    /** Tout synchroniser avec Apple Santé. */
    fun syncNow(ctx: Context) {
        viewModelScope.launch {
            syncing.value = true
            if (settings.value.healthConnect) runCatching { HealthConnectHub.sync(ctx.applicationContext) }
            runCatching { Bridge.pullWatch(ctx.applicationContext) }
            HealthRepository.invalidate()
            syncing.value = false
            bump()
        }
    }

    fun syncDevice(d: DeviceUi) = syncNow(appContext)

    fun fetchHistory(d: DeviceUi) = Unit

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
