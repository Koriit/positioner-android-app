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
     * @param isolationDistance Points without enough neighbours closer than this radius are removed.
     * @param minNeighbours Minimum number of neighbours required within the isolation distance.
     */
    fun apply(
        measurements: List<LidarMeasurement>,
        confidenceThreshold: Int,
        minDistance: Float,
        isolationDistance: Float,
        minNeighbours: Int,
    ): List<LidarMeasurement> {
        if (measurements.isEmpty()) return emptyList()
        val points = measurements.map { it to it.toPoint() }
        return points.filter { (m, p1) ->
            if (m.confidence < confidenceThreshold) return@filter false
            val dist = m.distanceMm / 1000f
            if (dist < minDistance) return@filter false
            if (isolationDistance == 0f || minNeighbours <= 0) return@filter true
            var neighbours = 0
            for ((other, p2) in points) {
                if (other === m) continue
                val dx = p1.first - p2.first
                val dy = p1.second - p2.second
                val distSq = dx * dx + dy * dy
                if (distSq <= isolationDistance * isolationDistance) {
                    neighbours++
                    if (neighbours >= minNeighbours) break
                }
            }
            neighbours >= minNeighbours
        }.map { it.first }
    }
}
