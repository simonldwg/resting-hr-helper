package com.simonludwig.restinghr.data

import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.HeartRateAccuracy
import androidx.health.services.client.data.SampleDataPoint
import androidx.health.services.client.getCapabilities
import com.simonludwig.restinghr.domain.HeartRateSample
import com.simonludwig.restinghr.domain.HeartRateSensor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * [HeartRateSensor] backed by Health Services' MeasureClient.
 *
 * Note that MeasureClient only delivers readings while the app is in the foreground, so whatever
 * collects [samples] has to keep the screen awake.
 */
class HealthServicesHeartRateSensor(
    private val measureClient: MeasureClient,
) : HeartRateSensor {

    override suspend fun isSupported(): Boolean = runCatching {
        DataType.HEART_RATE_BPM in measureClient.getCapabilities().supportedDataTypesMeasure
    }.getOrDefault(false)

    override fun samples(): Flow<HeartRateSample> = callbackFlow {
        val callback = object : MeasureCallback {
            override fun onRegistrationFailed(throwable: Throwable) {
                close(throwable)
            }

            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability,
            ) = Unit // Unavailability just means no samples arrive, which the caller handles.

            override fun onDataReceived(data: DataPointContainer) {
                data.getData(DataType.HEART_RATE_BPM).forEach { point ->
                    trySend(HeartRateSample(bpm = point.value, reliable = point.isReliable()))
                }
            }
        }

        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
        awaitClose {
            // awaitClose is not a suspend context, so this is the future-returning overload
            // rather than the suspending extension.
            measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
        }
    }.buffer(Channel.UNLIMITED) // Readings arrive in batches from a callback that cannot suspend.
}

/**
 * Health Services reports accuracy per reading. Only outright bad readings are rejected: some
 * watches report UNKNOWN throughout, and demanding MEDIUM or better would leave those with nothing
 * to average.
 */
private fun SampleDataPoint<Double>.isReliable(): Boolean {
    val status = (accuracy as? HeartRateAccuracy)?.sensorStatus ?: return true
    return status != HeartRateAccuracy.SensorStatus.NO_CONTACT &&
        status != HeartRateAccuracy.SensorStatus.UNRELIABLE
}
