package com.simonludwig.restinghr.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simonludwig.restinghr.domain.MeasureRestingHeartRateUseCase
import com.simonludwig.restinghr.domain.MeasurementOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RestingHrViewModel @Inject constructor(
    private val measureRestingHeartRate: MeasureRestingHeartRateUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RestingHrUiState>(RestingHrUiState.Idle)
    val uiState: StateFlow<RestingHrUiState> = _uiState.asStateFlow()

    private var measurement: Job? = null

    fun start() {
        if (measurement?.isActive == true) return
        measurement = viewModelScope.launch {
            _uiState.value = RestingHrUiState.Measuring
            _uiState.value = measureRestingHeartRate().toUiState()
        }
    }

    fun onPermissionDenied() {
        measurement?.cancel()
        _uiState.value = RestingHrUiState.Failed(FailureReason.PERMISSION_DENIED)
    }

    /**
     * The app left the foreground, which stops the sensor delivering. Reporting this beats
     * averaging whatever handful of readings arrived before the screen went dark.
     */
    fun onInterrupted() {
        if (_uiState.value !is RestingHrUiState.Measuring) return
        measurement?.cancel()
        _uiState.value = RestingHrUiState.Failed(FailureReason.INTERRUPTED)
    }
}

private fun MeasurementOutcome.toUiState(): RestingHrUiState = when (this) {
    is MeasurementOutcome.Bpm -> RestingHrUiState.Result(value)
    MeasurementOutcome.NoReading -> RestingHrUiState.Failed(FailureReason.NO_READINGS)
    MeasurementOutcome.Unsupported -> RestingHrUiState.Failed(FailureReason.NOT_SUPPORTED)
    MeasurementOutcome.SensorError -> RestingHrUiState.Failed(FailureReason.SENSOR_ERROR)
}
