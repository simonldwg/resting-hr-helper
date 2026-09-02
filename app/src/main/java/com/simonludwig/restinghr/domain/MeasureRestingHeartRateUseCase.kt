package com.simonludwig.restinghr.domain

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a completed (or abandoned) measurement produced. */
sealed interface MeasurementOutcome {
    data class Bpm(val value: Int) : MeasurementOutcome

    /** The window closed without a single usable reading. */
    data object NoReading : MeasurementOutcome

    /** This watch has no heart-rate sensor available to Health Services. */
    data object Unsupported : MeasurementOutcome

    /** The sensor refused to start, or stopped delivering before the window closed. */
    data object SensorError : MeasurementOutcome
}

/**
 * Estimates resting heart rate by asking the user to sit still for [TOTAL] and averaging only the
 * readings from the final [WINDOW]. The settling time is the whole point: it is what makes the
 * number a *resting* rate rather than whatever the wrist happened to be doing at the start.
 *
 * The sensor is switched on [WARM_UP] before the window opens, because an optical sensor needs a
 * few seconds to lock on and those first readings would otherwise be lost from the average.
 */
class MeasureRestingHeartRateUseCase @Inject constructor(
    private val sensor: HeartRateSensor,
) {

    suspend operator fun invoke(): MeasurementOutcome {
        if (!sensor.isSupported()) return MeasurementOutcome.Unsupported

        // Declared outside the scope below so that cancelling the collector cannot discard what
        // it already gathered.
        val readings = mutableListOf<Double>()
        val recording = AtomicBoolean(false)

        try {
            coroutineScope {
                delay(LEAD_IN)

                val collector = launch {
                    sensor.samples().collect { sample ->
                        if (recording.get() && sample.reliable) readings += sample.bpm
                    }
                    // Reaching here means the sensor stopped on its own before we were done.
                    error("Heart rate sensor stopped before the measurement window closed")
                }

                delay(WARM_UP)
                recording.set(true)
                delay(WINDOW)
                collector.cancel()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return MeasurementOutcome.SensorError
        }

        return restingBpm(readings)?.let(MeasurementOutcome::Bpm) ?: MeasurementOutcome.NoReading
    }

    companion object {
        /** How long the user has to sit still, start to finish. */
        val TOTAL: Duration = 3.minutes

        /** The closing stretch whose readings are averaged into the result. */
        val WINDOW: Duration = 30.seconds

        /** Sensor on, readings discarded, so it is locked on when [WINDOW] opens. */
        val WARM_UP: Duration = 20.seconds

        /** Settling time before the sensor is touched at all. */
        val LEAD_IN: Duration = TOTAL - WINDOW - WARM_UP
    }
}

/** Readings outside this range are sensor noise rather than a human pulse. */
private val PLAUSIBLE_BPM = 30.0..220.0

/**
 * The resting heart rate for a window's worth of readings, or null if none of them were usable.
 */
internal fun restingBpm(readings: List<Double>): Int? =
    readings.filter { it in PLAUSIBLE_BPM }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.roundToInt()
