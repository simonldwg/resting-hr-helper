package com.simonludwig.restinghr.domain

import kotlinx.coroutines.flow.Flow

/**
 * The watch's heart-rate sensor, expressed without any Health Services types so that the
 * measurement logic can be exercised on the JVM with a fake.
 */
interface HeartRateSensor {

    /** Whether this watch can measure heart rate at all. */
    suspend fun isSupported(): Boolean

    /**
     * Readings for as long as the flow is collected. The sensor is switched on when collection
     * starts and off when it stops, so collect it only for the window you actually need.
     */
    fun samples(): Flow<HeartRateSample>
}
