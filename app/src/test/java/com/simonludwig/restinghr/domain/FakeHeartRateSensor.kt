package com.simonludwig.restinghr.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onStart

/**
 * Hand-written stand-in for the watch sensor. Readings are pushed in from the test at whatever
 * virtual time the test chooses, which is what makes the three-minute protocol testable.
 */
class FakeHeartRateSensor : HeartRateSensor {

    var supported: Boolean = true

    /** Throw this instead of delivering readings, as a failed sensor registration would. */
    var registrationFailure: Throwable? = null

    /** Finish the stream immediately, as a sensor that stops on its own would. */
    var stopsImmediately: Boolean = false

    /** How many times the sensor was actually switched on. */
    var registrations: Int = 0
        private set

    private val readings = MutableSharedFlow<HeartRateSample>(extraBufferCapacity = 64)

    override suspend fun isSupported(): Boolean = supported

    override fun samples(): Flow<HeartRateSample> =
        (if (stopsImmediately) emptyFlow() else readings).onStart {
            registrations++
            registrationFailure?.let { throw it }
        }

    suspend fun emit(bpm: Double, reliable: Boolean = true) {
        readings.emit(HeartRateSample(bpm = bpm, reliable = reliable))
    }
}
