package com.koriit.positioner.android.lidar

/**
 * Utility for applying measurement filters.
 */
object MeasurementFilter {
    /**
     * Apply filtering rules to the provided measurements.
     *
     * @param measurements Raw lidar measurements.
     * @param confidenceThreshold Minimum confidence required.
     * @param minDistance Minimum distance in metres.
     * @param isolationDistance Points without neighbours closer than this radius are removed.
     */
    fun apply(
        measurements: List<LidarMeasurement>,
        confidenceThreshold: Int,
        minDistance: Float,
        isolationDistance: Float,
    ): List<LidarMeasurement> {
        if (measurements.isEmpty()) return emptyList()
        val points = measurements.map { it to it.toPoint() }
        return points.filter { (m, p1) ->
            if (m.confidence < confidenceThreshold) return@filter false
            val dist = m.distanceMm / 1000f
            if (dist < minDistance) return@filter false
            if (isolationDistance == 0f) return@filter true
            points.any { (other, p2) ->
                other !== m &&
                    kotlin.math.hypot(
                        (p1.first - p2.first).toDouble(),
                        (p1.second - p2.second).toDouble(),
                    ) <= isolationDistance
            }
        }.map { it.first }
    }
}
