package com.koriit.positioner.android.localization

import com.koriit.positioner.android.lidar.LidarMeasurement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Estimate sensor pose using a basic particle filter.
 *
 * This implementation explores translation and orientation while keeping scale
 * fixed at 1.0. It is intentionally simple yet provides a useful alternative to
 * the brute-force [OccupancyPoseEstimator].
 */
object ParticlePoseEstimator {
    private data class Particle(
        var x: Float,
        var y: Float,
        var orientation: Float,
        var weight: Float,
    )

    /**
     * Perform particle filter based pose estimation.
     *
     * @param particles number of particles in the filter
     * @param iterations number of iterations to run
     * @param missPenalty penalty subtracted for each measurement that does not
     * align with a wall cell.
     * @param random source of randomness, exposed for deterministic testing
     */
    suspend fun estimate(
        measurements: List<LidarMeasurement>,
        grid: OccupancyGrid,
        particles: Int = 500,
        iterations: Int = 10,
        missPenalty: Int = 0,
        random: Random = Random.Default,
    ): OccupancyPoseEstimator.EstimateResult = withContext(Dispatchers.Default) {
        if (measurements.isEmpty()) return@withContext OccupancyPoseEstimator.EstimateResult(null, 0, -1)

        val width = grid.width * grid.cellSize
        val height = grid.height * grid.cellSize
        val particleList = MutableList(particles) {
            Particle(
                x = grid.originX + random.nextFloat() * width,
                y = grid.originY + random.nextFloat() * height,
                orientation = random.nextFloat() * 360f,
                weight = 1f / particles,
            )
        }

        repeat(iterations) {
            var totalWeight = 0f
            for (p in particleList) {
                val score = scorePose(measurements, grid, p.x, p.y, p.orientation, missPenalty)
                p.weight = score.coerceAtLeast(0).toFloat() + 1e-6f
                totalWeight += p.weight
            }
            particleList.forEach { it.weight /= totalWeight }

            val cumulative = FloatArray(particles)
            var acc = 0f
            for (i in particleList.indices) {
                acc += particleList[i].weight
                cumulative[i] = acc
            }
            val newParticles = MutableList(particles) {
                val r = random.nextFloat()
                val idx = cumulative.indexOfFirst { it >= r }.coerceAtLeast(0)
                val src = particleList[idx]
                Particle(
                    x = src.x + random.nextFloat() * 0.1f - 0.05f,
                    y = src.y + random.nextFloat() * 0.1f - 0.05f,
                    orientation = (src.orientation + random.nextFloat() * 10f - 5f + 360f) % 360f,
                    weight = 1f / particles,
                )
            }
            particleList.clear()
            particleList.addAll(newParticles)
        }

        var best: Particle? = null
        var bestScore = -1
        for (p in particleList) {
            val score = scorePose(measurements, grid, p.x, p.y, p.orientation, missPenalty)
            if (score > bestScore) {
                bestScore = score
                best = p
            }
        }
        val estimate = best?.let { OccupancyPoseEstimator.Estimate(it.orientation, 1f, it.x to it.y) }
        OccupancyPoseEstimator.EstimateResult(estimate, particles * iterations, bestScore)
    }

    private fun scorePose(
        measurements: List<LidarMeasurement>,
        grid: OccupancyGrid,
        x: Float,
        y: Float,
        orientation: Float,
        missPenalty: Int,
    ): Int {
        var score = 0
        val orientRad = Math.toRadians(orientation.toDouble())
        val cosA = cos(orientRad).toFloat()
        val sinA = sin(orientRad).toFloat()
        for (m in measurements) {
            val dist = m.distanceMm / 1000f
            val angleRad = Math.toRadians(m.angle.toDouble())
            val dx = sin(angleRad).toFloat() * dist
            val dy = cos(angleRad).toFloat() * dist
            val worldX = x + cosA * dx - sinA * dy
            val worldY = y + sinA * dx + cosA * dy
            if (grid.isOccupied(worldX, worldY)) score++ else score -= missPenalty
        }
        return score
    }
}

