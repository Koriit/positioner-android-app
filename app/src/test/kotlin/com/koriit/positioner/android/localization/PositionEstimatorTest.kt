package com.koriit.positioner.android.localization

import com.koriit.positioner.android.lidar.LidarMeasurement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PositionEstimatorTest {
    @Test
    fun estimatesOrientationAndScale() {
        val square = listOf(
            -1f to -1f,
            1f to -1f,
            1f to 1f,
            -1f to 1f,
            -1f to -1f,
        )
        val measurements = mutableListOf<LidarMeasurement>()
        val orientation = 45f
        val scale = 1.0f
        val profile = PositionEstimatorTestHelper.profile(square)
        for (deg in 0 until 360 step 10) {
            val worldAngle = (deg + orientation).toInt() % 360
            val dist = profile[worldAngle] * scale
            measurements.add(LidarMeasurement(deg.toFloat(), (dist * 1000).toInt(), 200))
        }
        val est = PositionEstimator.estimate(measurements, square)!!
        assertEquals(orientation, est.orientation, 1f)
        assertEquals(scale, est.scale, 0.01f)
        assertEquals(0f, est.position.first, 0.1f)
        assertEquals(0f, est.position.second, 0.1f)
    }
}
