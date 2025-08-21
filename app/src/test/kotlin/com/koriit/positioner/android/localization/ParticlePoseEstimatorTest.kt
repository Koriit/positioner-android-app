package com.koriit.positioner.android.localization

import com.koriit.positioner.android.lidar.LidarMeasurement
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ParticlePoseEstimatorTest {
    @Test
    fun estimatesPoseUsingParticles() {
        val rectangle = listOf(
            -1f to -0.5f,
            1f to -0.5f,
            1f to 0.5f,
            -1f to 0.5f,
            -1f to -0.5f,
        )
        val grid = OccupancyGrid.fromPolygon(rectangle)
        val measurements = mutableListOf<LidarMeasurement>()
        val orientation = 30f
        val profile = PositionEstimatorTestHelper.profile(rectangle)
        for (deg in 0 until 360 step 10) {
            val worldAngle = (deg + orientation).toInt() % 360
            val dist = profile[worldAngle]
            measurements.add(LidarMeasurement(deg.toFloat(), (dist * 1000).toInt(), 200))
        }
        val result = runBlocking {
            ParticlePoseEstimator.estimate(measurements, grid, particles = 200, iterations = 5, missPenalty = 1, random = Random(0))
        }
        val est = result.estimate!!
        assertEquals(0f, est.position.first, 1f)
        assertEquals(0f, est.position.second, 1f)
        val diff30 = ((est.orientation - 30f + 540f) % 360f) - 180f
        val diff150 = ((est.orientation - 150f + 540f) % 360f) - 180f
        assertTrue(kotlin.math.abs(diff30) < 10f || kotlin.math.abs(diff150) < 10f)
    }
}

