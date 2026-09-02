package com.simonludwig.restinghr.domain

import com.simonludwig.restinghr.domain.MeasureRestingHeartRateUseCase.Companion.LEAD_IN
import com.simonludwig.restinghr.domain.MeasureRestingHeartRateUseCase.Companion.TOTAL
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The whole protocol runs on virtual time, so these cover in milliseconds what would otherwise
 * take three minutes of sitting still per case.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeasureRestingHeartRateUseCaseTest {

    private val sensor = FakeHeartRateSensor()
    private val measure = MeasureRestingHeartRateUseCase(sensor)

    @Test
    fun `averages only the readings from the final thirty seconds`() = runTest {
        launch {
            delay(2.minutes + 15.seconds) // sensor is warming up; discarded
            sensor.emit(200.0)
            delay(20.seconds) // 2:35, inside the window
            sensor.emit(60.0)
            delay(10.seconds) // 2:45
            sensor.emit(64.0)
        }

        assertEquals(MeasurementOutcome.Bpm(62), measure())
    }

    @Test
    fun `takes exactly three minutes`() = runTest {
        val start = testScheduler.currentTime
        launch {
            delay(2.minutes + 40.seconds)
            sensor.emit(58.0)
        }

        measure()

        assertEquals(TOTAL.inWholeMilliseconds, testScheduler.currentTime - start)
    }

    @Test
    fun `switches the sensor on before the window opens`() = runTest {
        launch {
            delay(2.minutes + 40.seconds)
            sensor.emit(58.0)
        }

        measure()

        assertEquals(1, sensor.registrations)
    }

    @Test
    fun `drops readings the sensor flagged as unreliable`() = runTest {
        launch {
            delay(2.minutes + 35.seconds)
            sensor.emit(120.0, reliable = false)
            sensor.emit(58.0)
        }

        assertEquals(MeasurementOutcome.Bpm(58), measure())
    }

    @Test
    fun `reports no reading when nothing usable arrives`() = runTest {
        launch {
            delay(2.minutes + 35.seconds)
            sensor.emit(58.0, reliable = false)
        }

        assertEquals(MeasurementOutcome.NoReading, measure())
    }

    @Test
    fun `reports no reading when the window is silent`() = runTest {
        assertEquals(MeasurementOutcome.NoReading, measure())
    }

    @Test
    fun `reports an unsupported watch without waiting or touching the sensor`() = runTest {
        sensor.supported = false
        val start = testScheduler.currentTime

        assertEquals(MeasurementOutcome.Unsupported, measure())
        assertEquals(0, sensor.registrations)
        assertEquals(0L, testScheduler.currentTime - start)
    }

    @Test
    fun `fails as soon as the sensor refuses to start, rather than after three minutes`() = runTest {
        sensor.registrationFailure = IllegalStateException("sensor unavailable")
        val start = testScheduler.currentTime

        assertEquals(MeasurementOutcome.SensorError, measure())
        assertEquals(LEAD_IN.inWholeMilliseconds, testScheduler.currentTime - start)
    }

    @Test
    fun `fails when the sensor stops before the window closes`() = runTest {
        sensor.stopsImmediately = true

        assertEquals(MeasurementOutcome.SensorError, measure())
    }
}
