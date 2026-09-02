package com.simonludwig.restinghr.presentation

sealed interface RestingHrUiState {

    data object Idle : RestingHrUiState

    /**
     * Carries no progress information on purpose: the user is not shown how much time is left,
     * so there is nothing here for a countdown to be built from.
     */
    data object Measuring : RestingHrUiState

    data class Result(val bpm: Int) : RestingHrUiState

    data class Failed(val reason: FailureReason) : RestingHrUiState
}

enum class FailureReason {
    NOT_SUPPORTED,
    SENSOR_ERROR,
    NO_READINGS,
    INTERRUPTED,
    PERMISSION_DENIED,
}
