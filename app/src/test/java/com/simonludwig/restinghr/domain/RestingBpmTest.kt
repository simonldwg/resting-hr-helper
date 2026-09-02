package com.simonludwig.restinghr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestingBpmTest {

    @Test
    fun `averages and rounds the readings`() {
        assertEquals(62, restingBpm(listOf(60.0, 64.0)))
        assertEquals(61, restingBpm(listOf(60.4, 61.0)))
    }

    @Test
    fun `has no result without readings`() {
        assertNull(restingBpm(emptyList()))
    }

    @Test
    fun `ignores readings outside a plausible pulse range`() {
        assertEquals(60, restingBpm(listOf(60.0, 0.0, 500.0)))
    }

    @Test
    fun `has no result when every reading is implausible`() {
        assertNull(restingBpm(listOf(0.0, 1000.0)))
    }
}
