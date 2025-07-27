package com.koriit.positioner.android.lidar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MeasurementFilterTest {
    @Test
    fun filtersByConfidence() {
        val input = listOf(
            LidarMeasurement(0f, 1000, 50),
            LidarMeasurement(90f, 1000, 250),
        )
        val result = MeasurementFilter.apply(input, 100, 0f, 0f, 0)
        assertEquals(1, result.size)
        assertEquals(250, result[0].confidence)
    }

    @Test
    fun filtersByMinDistance() {
        val input = listOf(
            LidarMeasurement(0f, 100, 200),
            LidarMeasurement(90f, 1000, 200),
        )
        val result = MeasurementFilter.apply(input, 0, 0.5f, 0f, 0)
        assertEquals(1, result.size)
        assertEquals(1000, result[0].distanceMm)
    }

    @Test
    fun filtersByIsolation() {
        val input = listOf(
            LidarMeasurement(0f, 1000, 200),
            LidarMeasurement(5f, 1000, 200),
            LidarMeasurement(90f, 5000, 200),
        )
        val result = MeasurementFilter.apply(input, 0, 0f, 1.1f, 1)
        assertEquals(2, result.size)
    }

    @Test
    fun filtersByMinNeighbours() {
        val input = listOf(
            LidarMeasurement(0f, 1000, 200),
            LidarMeasurement(5f, 1000, 200),
            LidarMeasurement(6f, 1000, 200),
        )
        val result = MeasurementFilter.apply(input, 0, 0f, 1.1f, 2)
        assertEquals(3, result.size)
    }
}
