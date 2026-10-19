package app.localhealth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.localhealth.data.DeviceUi
import app.localhealth.data.HealthRepository
import app.localhealth.data.ManualLogStore
import app.localhealth.data.Metric
import app.localhealth.data.deviceStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes

class HealthViewModel(app: Application) : AndroidViewModel(app) {
    val devices: StateFlow<List<DeviceUi>> = deviceStateFlow(app)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val manualLogs = ManualLogStore.get(app)

    private val _refresh = MutableStateFlow(0)
    /** Bumped whenever screens should reload their data (sync finished, value logged, resume). */
    val refresh: StateFlow<Int> = _refresh.asStateFlow()

    init {
        // Reload when a sync finishes (busy -> idle) or the set of devices changes
        devices.distinctUntilChangedBy { list -> list.map { it.address to it.busy } }
            .drop(1)
            .onEach { HealthRepository.invalidate(); bump() }
            .launchIn(viewModelScope)
        manualLogs.changes.drop(1).onEach { HealthRepository.invalidate(); bump() }.launchIn(viewModelScope)
    }

    fun bump() {
        _refresh.value++
    }

    fun sync(device: DeviceUi) {
        if (!device.connected) {
            GBApplication.deviceService(device.device).connect()
        } else {
            GBApplication.deviceService(device.device).onFetchRecordedData(RecordedDataTypes.TYPE_SYNC)
        }
    }

    fun syncAll() = devices.value.forEach { sync(it) }

    fun log(metric: Metric, value: Double, timestampMs: Long = System.currentTimeMillis()) {
        viewModelScope.launch { manualLogs.add(metric, timestampMs, value) }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch { manualLogs.delete(id) }
    }
}
