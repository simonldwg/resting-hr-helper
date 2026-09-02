package com.simonludwig.restinghr.domain

/**
 * A single heart-rate reading from the watch sensor.
 *
 * [reliable] is false when the sensor reported that it had no skin contact or that the reading
 * could not be trusted; such samples are dropped before averaging.
 */
data class HeartRateSample(
    val bpm: Double,
    val reliable: Boolean,
)
