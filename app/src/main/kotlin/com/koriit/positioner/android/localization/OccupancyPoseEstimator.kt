package com.koriit.positioner.android.localization

import com.koriit.positioner.android.lidar.LidarMeasurement
import kotlin.math.cos
import kotlin.math.sin
import kotlin.system.measureTimeMillis

/**
 * Estimate sensor pose relative to a floor plan using an occupancy grid.
 * This performs a brute force search over orientation, scale and translation
 * and picks the combination with the most measurements hitting occupied cells.
 */
object OccupancyPoseEstimator {
    data class Estimate(
        val orientation: Float,
        val scale: Float,
        val position: Pair<Float, Float>,
    )

    /**
     * Result of a pose estimation attempt including performance statistics.
     */
    data class Result(
        val estimate: Estimate?,
        val combinations: Long,
        val durationMs: Long,
    )

    fun estimate(
        measurements: List<LidarMeasurement>,
        grid: OccupancyGrid,
        orientationStep: Int = 5,
        scaleRange: ClosedFloatingPointRange<Float> = 0.8f..1.2f,
        scaleStep: Float = 0.05f,
    ): Result {
        if (measurements.isEmpty()) return Result(null, 0, 0)
        var bestScore = -1
        var bestOrientation = 0
        var bestScale = 1f
        var bestX = 0f
        var bestY = 0f

        val gridMaxX = grid.originX + grid.width * grid.cellSize
        val gridMaxY = grid.originY + grid.height * grid.cellSize

        var combinations = 0L
        val duration = measureTimeMillis {
            var orient = 0
            while (orient < 360) {
                val angleRad = Math.toRadians(orient.toDouble())
                val cosA = cos(angleRad).toFloat()
                val sinA = sin(angleRad).toFloat()
                var scale = scaleRange.start
                while (scale <= scaleRange.endInclusive + 1e-6f) {
                    val transformed = measurements.map { m ->
                        val r = m.distanceMm / 1000f * scale
                        val mRad = Math.toRadians(m.angle.toDouble())
                        val x = sin(mRad).toFloat() * r
                        val y = cos(mRad).toFloat() * r
                        val rx = x * cosA - y * sinA
                        val ry = x * sinA + y * cosA
                        rx to ry
                    }

                    var y = grid.originY
                    while (y <= gridMaxY) {
                        var x = grid.originX
                        while (x <= gridMaxX) {
                            combinations++
                            var score = 0
                            for ((px, py) in transformed) {
                                if (grid.isOccupied(px + x, py + y)) score++
                            }
                            if (score > bestScore) {
                                bestScore = score
                                bestOrientation = orient
                                bestScale = scale
                                bestX = x
                                bestY = y
                            }
                            x += grid.cellSize
                        }
                        y += grid.cellSize
                    }
                    scale += scaleStep
                }
                orient += orientationStep
            }
        }
        if (bestScore <= 0) {
            return Result(null, combinations, duration)
        }
        val estimate = Estimate(bestOrientation.toFloat(), bestScale, bestX to bestY)
        return Result(estimate, combinations, duration)
    }
}
