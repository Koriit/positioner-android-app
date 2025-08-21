package com.koriit.positioner.android.localization

import com.koriit.positioner.android.lidar.LidarMeasurement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Estimate sensor pose using a particle filter with low-variance resampling.
 *
 * The filter explores translation and orientation while keeping scale fixed at
 * 1.0. Precomputed measurement vectors keep scoring inexpensive and the
 * algorithm provides a lighter-weight alternative to the brute-force
 * [OccupancyPoseEstimator].
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
        particles: Int = 200,
        iterations: Int = 5,
        missPenalty: Int = 0,
        random: Random = Random.Default,
    ): OccupancyPoseEstimator.EstimateResult = withContext(Dispatchers.Default) {
        if (measurements.isEmpty()) return@withContext OccupancyPoseEstimator.EstimateResult(null, 0, -1)

        val count = measurements.size
        val dx = FloatArray(count)
        val dy = FloatArray(count)
        for (i in 0 until count) {
            val m = measurements[i]
            val dist = m.distanceMm / 1000f
            val angleRad = Math.toRadians(m.angle.toDouble())
            dx[i] = sin(angleRad).toFloat() * dist
            dy[i] = cos(angleRad).toFloat() * dist
        }

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
                val orientRad = Math.toRadians(p.orientation.toDouble())
                val cosA = cos(orientRad).toFloat()
                val sinA = sin(orientRad).toFloat()
                val score = scorePose(dx, dy, grid, p.x, p.y, cosA, sinA, missPenalty)
                p.weight = score.coerceAtLeast(0).toFloat() + 1e-6f
                totalWeight += p.weight
            }
            particleList.forEach { it.weight /= totalWeight }

            // Low variance resampling
            val newParticles = MutableList(particles) { Particle(0f, 0f, 0f, 1f / particles) }
            val step = 1f / particles
            var r = random.nextFloat() * step
            var c = particleList[0].weight
            var i = 0
            for (m in 0 until particles) {
                val u = r + m * step
                while (u > c && i < particleList.lastIndex) {
                    i++
                    c += particleList[i].weight
                }
                val src = particleList[i]
                newParticles[m].x = src.x + random.nextFloat() * grid.cellSize - grid.cellSize / 2
                newParticles[m].y = src.y + random.nextFloat() * grid.cellSize - grid.cellSize / 2
                newParticles[m].orientation = (src.orientation + random.nextFloat() * 4f - 2f + 360f) % 360f
            }
            particleList.clear()
            particleList.addAll(newParticles)
        }

        var best: Particle? = null
        var bestScore = -1
        for (p in particleList) {
            val orientRad = Math.toRadians(p.orientation.toDouble())
            val cosA = cos(orientRad).toFloat()
            val sinA = sin(orientRad).toFloat()
            val score = scorePose(dx, dy, grid, p.x, p.y, cosA, sinA, missPenalty)
            if (score > bestScore) {
                bestScore = score
                best = p
            }
        }
        val estimate = best?.let { OccupancyPoseEstimator.Estimate(it.orientation, 1f, it.x to it.y) }
        OccupancyPoseEstimator.EstimateResult(estimate, particles * iterations, bestScore)
    }

    private fun scorePose(
        dx: FloatArray,
        dy: FloatArray,
        grid: OccupancyGrid,
        x: Float,
        y: Float,
        cosA: Float,
        sinA: Float,
        missPenalty: Int,
    ): Int {
        var score = 0
        for (i in dx.indices) {
            val worldX = x + cosA * dx[i] - sinA * dy[i]
            val worldY = y + sinA * dx[i] + cosA * dy[i]
            if (grid.isOccupied(worldX, worldY)) score++ else score -= missPenalty
        }
        return score
    }
}

