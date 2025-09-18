package com.koriit.positioner.android.gyro

import com.koriit.positioner.android.lidar.LidarMeasurement
import com.koriit.positioner.android.recording.Rotation
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class GyroscopeOrientationTrackerTest {

    @Test
    fun `integrates sequential samples`() {
        val tracker = GyroscopeOrientationTracker()
        val base = Instant.parse("2024-01-01T00:00:00Z")
        val samples = listOf(
            GyroscopeMeasurement(0f, 0f, 1f, base),
            GyroscopeMeasurement(0f, 0f, 1f, base + 1.seconds),
        )

        val orientation = tracker.integrate(samples)
        assertEquals(57.29578, orientation.toDouble(), 1e-3)

        val negative = tracker.integrate(
            listOf(
                GyroscopeMeasurement(0f, 0f, -0.5f, base + 2.seconds)
            )
        )
        assertEquals(28.64789, negative.toDouble(), 1e-3)
    }

    @Test
    fun `normalises angles`() {
        assertEquals(-170.0, GyroscopeOrientationTracker.normalizeDegrees(190f).toDouble(), 0.0)
        assertEquals(170.0, GyroscopeOrientationTracker.normalizeDegrees(-190f).toDouble(), 0.0)

        val twoPi = (Math.PI * 2).toFloat()
        assertEquals(0.0, GyroscopeOrientationTracker.normalizeRadians(twoPi).toDouble(), 1e-6)
        assertEquals(
            Math.PI - 0.1,
            GyroscopeOrientationTracker.normalizeRadians(-(Math.PI + 0.1).toFloat()).toDouble(),
            1e-6
        )
    }

    @Test
    fun `reconstructs orientation for recorded rotations`() {
        val base = Instant.parse("2024-01-01T00:00:00Z")
        val rotation1 = Rotation(
            measurements = listOf(LidarMeasurement(0f, 0, 0)),
            start = base,
            gyroscope = listOf(
                GyroscopeMeasurement(0f, 0f, 0.5f, base),
                GyroscopeMeasurement(0f, 0f, 0.5f, base + 2.seconds),
            )
        )
        val rotation2 = Rotation(
            measurements = listOf(LidarMeasurement(0f, 0, 0)),
            start = base + 3.seconds,
            gyroscope = emptyList(),
        )
        val rotation3 = Rotation(
            measurements = listOf(LidarMeasurement(0f, 0, 0)),
            start = base + 4.seconds,
            gyroscope = listOf(
                GyroscopeMeasurement(0f, 0f, 1f, base + 4.seconds),
                GyroscopeMeasurement(0f, 0f, 1f, base + 5.seconds),
            ),
            gyroscopeOrientation = 200f,
        )

        val result = GyroscopeOrientationTracker.withOrientation(listOf(rotation1, rotation2, rotation3))

        assertEquals(57.29578, result[0].gyroscopeOrientation!!.toDouble(), 1e-3)
        assertEquals(result[0].gyroscopeOrientation, result[1].gyroscopeOrientation)
        assertEquals(-160.0, result[2].gyroscopeOrientation!!.toDouble(), 1e-3)
    }
}
