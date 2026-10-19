package app.localhealth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.localhealth.data.DaySummary
import app.localhealth.data.DeviceUi
import app.localhealth.data.HealthRepository
import app.localhealth.data.Workout
import app.localhealth.data.deviceStateFlow
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes

class HealthViewModel(app: Application) : AndroidViewModel(app) {
    val devices: StateFlow<List<DeviceUi>> = deviceStateFlow(app)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _summary = MutableStateFlow<DaySummary?>(null)
    val summary: StateFlow<DaySummary?> = _summary.asStateFlow()

    private val _workouts = MutableStateFlow<List<Workout>>(emptyList())
    val workouts: StateFlow<List<Workout>> = _workouts.asStateFlow()

    private var loadJob: Job? = null

    init {
        // Reload when a sync finishes (busy -> idle) or the device set changes
        devices.distinctUntilChangedBy { list -> list.map { it.address to it.busy } }
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    fun setDate(date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return
        _date.value = date
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _summary.value = HealthRepository.daySummary(_date.value)
            _workouts.value = HealthRepository.recentWorkouts()
        }
    }

    fun sync(device: DeviceUi) {
        if (!device.connected) {
            GBApplication.deviceService(device.device).connect()
        } else {
            GBApplication.deviceService(device.device).onFetchRecordedData(RecordedDataTypes.TYPE_SYNC)
        }
    }

    fun syncAll() = devices.value.forEach { sync(it) }
}
