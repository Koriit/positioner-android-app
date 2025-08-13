package com.koriit.positioner.android.localization

import com.koriit.positioner.android.lidar.LidarMeasurement
import kotlin.math.min
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OccupancyPoseEstimatorTest {
    @Test
    fun estimatesOrientationAndScaleUsingGrid() {
        val square = listOf(
            -1f to -1f,
            1f to -1f,
            1f to 1f,
            -1f to 1f,
            -1f to -1f,
        )
        val grid = OccupancyGrid.fromPolygon(square)
        val measurements = mutableListOf<LidarMeasurement>()
        val orientation = 30f
        val scale = 1.0f
        val profile = PositionEstimatorTestHelper.profile(square)
        for (deg in 0 until 360 step 10) {
            val worldAngle = (deg + orientation).toInt() % 360
            val dist = profile[worldAngle] * scale
            measurements.add(LidarMeasurement(deg.toFloat(), (dist * 1000).toInt(), 200))
        }
        val result = runBlocking { OccupancyPoseEstimator.estimate(measurements, grid) }
        val est = result.estimate!!
        println("Est: $est")
        val diff = Math.abs((est.orientation - orientation + 360) % 360)
        val angularDiff = minOf(diff, 360 - diff)
        assert(angularDiff <= 20f)
        assertEquals(scale, est.scale, 0.2f)
        assertEquals(0f, est.position.first, 1f)
        assertEquals(0f, est.position.second, 1f)
    }
}
