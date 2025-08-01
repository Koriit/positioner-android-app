package com.koriit.positioner.android.localization

import com.koriit.positioner.android.lidar.LidarMeasurement
import kotlin.math.cos
import kotlin.math.sin

/**
 * Estimate sensor pose relative to a floor plan using an occupancy grid.
 * This performs a brute force search over orientation and scale
 * and picks the combination with the most measurements hitting occupied cells.
 */
object OccupancyPoseEstimator {
    data class Estimate(
        val orientation: Float,
        val scale: Float,
        val position: Pair<Float, Float>,
    )

    fun estimate(
        measurements: List<LidarMeasurement>,
        grid: OccupancyGrid,
        orientationStep: Int = 5,
        scaleRange: ClosedFloatingPointRange<Float> = 0.8f..1.2f,
        scaleStep: Float = 0.05f,
    ): Estimate? {
        if (measurements.isEmpty()) return null
        var bestScore = -1
        var bestOrientation = 0
        var bestScale = 1f
        var orient = 0
        while (orient < 360) {
            var scale = scaleRange.start
            while (scale <= scaleRange.endInclusive) {
                var score = 0
                for (m in measurements) {
                    val rad = Math.toRadians((m.angle + orient).toDouble())
                    val r = m.distanceMm / 1000f * scale
                    val x = sin(rad).toFloat() * r + grid.originX + grid.width * grid.cellSize / 2
                    val y = cos(rad).toFloat() * r + grid.originY + grid.height * grid.cellSize / 2
                    if (grid.isOccupied(x, y)) score++
                }
                if (score > bestScore) {
                    bestScore = score
                    bestOrientation = orient
                    bestScale = scale
                }
                scale += scaleStep
            }
            orient += orientationStep
        }
        if (bestScore <= 0) return null
        // compute translation between measurement centroid and grid centre
        val centerX = grid.originX + grid.width * grid.cellSize / 2
        val centerY = grid.originY + grid.height * grid.cellSize / 2
        var sumX = 0f
        var sumY = 0f
        for (m in measurements) {
            val rad = Math.toRadians((m.angle + bestOrientation).toDouble())
            val r = m.distanceMm / 1000f * bestScale
            sumX += sin(rad).toFloat() * r
            sumY += cos(rad).toFloat() * r
        }
        val avgX = sumX / measurements.size
        val avgY = sumY / measurements.size
        val posX = avgX - centerX
        val posY = avgY - centerY
        return Estimate(bestOrientation.toFloat(), bestScale, posX to posY)
    }
}
