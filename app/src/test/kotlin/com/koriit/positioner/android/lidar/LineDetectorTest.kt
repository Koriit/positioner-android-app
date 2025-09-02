package com.koriit.positioner.android.lidar

import kotlin.math.atan2
import kotlin.math.hypot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LineDetectorTest {
    private fun meas(x: Float, y: Float): LidarMeasurement {
        val r = hypot(x.toDouble(), y.toDouble()).toFloat()
        val angle = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
        return LidarMeasurement(angle, (r * 1000).toInt(), 255)
    }

    @Test
    fun detectsTwoLines() {
        val points = mutableListOf<LidarMeasurement>()
        for (x in -10..10 step 2) {
            points.add(meas(x / 10f, 1f))
        }
        val lines = LineDetector.detect(points, 0.2f, 3, 10f, 10f)
        assertEquals(1, lines.size)
        assertEquals(90f, lines[0].orientation, 5f)
        val reps = LineDetector.asMeasurements(lines)
        assertEquals(lines[0].pointCount, reps.size)
    }

    @Test
    fun filtersShortLines() {
        val lines = listOf(
            LineDetector.LineFeature(Pair(0f, 0f), Pair(1f, 0f), 0f, 1f, 2),
            LineDetector.LineFeature(Pair(0f, 0f), Pair(2f, 0f), 0f, 2f, 5),
            LineDetector.LineFeature(Pair(0f, 0f), Pair(3f, 0f), 0f, 3f, 10),
        )
        val params = LineDetector.AdaptiveFilterParams(
            enabled = true,
            lengthPercentile = 100.0,
            lengthFactor = 0.5,
            lengthMin = 0.0,
            lengthMax = 10.0,
            inliersPercentile = 100.0,
            inliersFactor = 0.5,
            inliersMin = 0.0,
            inliersMax = 10.0,
        )
        val (filtered, stats) = LineDetector.filterAdaptive(lines, params)
        assertEquals(2, filtered.size)
        assertEquals(3.0, stats.lengthPx, 0.01)
        assertEquals(10.0, stats.inliersPx, 0.01)
    }
}

