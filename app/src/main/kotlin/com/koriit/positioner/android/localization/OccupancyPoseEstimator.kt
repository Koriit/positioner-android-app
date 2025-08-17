package com.koriit.positioner.android.localization

import com.koriit.positioner.android.lidar.LidarMeasurement
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinx.multik.api.*
import org.jetbrains.kotlinx.multik.ndarray.data.*
import org.jetbrains.kotlinx.multik.ndarray.operations.*

/**
 * Estimate sensor pose relative to a floor plan using an occupancy grid.
 *
 * The search iterates over orientation, scale and translation. The expensive
 * evaluation of each orientation is parallelised across a dedicated dispatcher
 * sized to the number of available CPU cores. Basic multi-resolution search and
 * early pruning keep the total combinations manageable.
 */
object OccupancyPoseEstimator {
    data class Estimate(
        val orientation: Float,
        val scale: Float,
        val position: Pair<Float, Float>,
    )

    /**
     * Result of pose estimation containing the best [Estimate] found and the
     * number of candidate combinations evaluated.
     */
    data class EstimateResult(
        val estimate: Estimate?,
        val combinations: Int,
        val score: Int,
    )

    private val dispatcher =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
            .asCoroutineDispatcher()

    // Precomputed sine and cosine values at 0.1° resolution
    private val SIN_TABLE = FloatArray(3600) { angle ->
        sin(Math.toRadians(angle / 10.0)).toFloat()
    }
    private val COS_TABLE = FloatArray(3600) { angle ->
        cos(Math.toRadians(angle / 10.0)).toFloat()
    }

    /**
     * Perform brute-force search using parallel coroutines.
     *
     * Measurement rotation and scaling are vectorized with Multik to exploit
     * CPU SIMD instructions.
     *
     * @param missPenalty penalty subtracted for each measurement that does not
     * align with an occupied cell. Higher values increase sensitivity to
     * obstructions.
     */
    suspend fun estimate(
        measurements: List<LidarMeasurement>,
        grid: OccupancyGrid,
        orientationStep: Int = 5,
        scaleRange: ClosedFloatingPointRange<Float> = 0.8f..1.2f,
        scaleStep: Float = 0.05f,
        missPenalty: Int = 0,
    ): EstimateResult = withContext(dispatcher) {
        if (measurements.isEmpty()) return@withContext EstimateResult(null, 0, -1)

        val count = measurements.size
        val sinArr = FloatArray(count)
        val cosArr = FloatArray(count)
        val distArr = FloatArray(count)
        for (i in 0 until count) {
            val m = measurements[i]
            val deg = (((m.angle * 10).roundToInt() % 3600) + 3600) % 3600
            sinArr[i] = SIN_TABLE[deg]
            cosArr[i] = COS_TABLE[deg]
            distArr[i] = m.distanceMm / 1000f
        }
        val sinNd: NDArray<Float, D1> = mk.ndarray(sinArr.toList(), intArrayOf(count))
        val cosNd: NDArray<Float, D1> = mk.ndarray(cosArr.toList(), intArrayOf(count))
        val distNd: NDArray<Float, D1> = mk.ndarray(distArr.toList(), intArrayOf(count))

        val gridMaxX = grid.originX + grid.width * grid.cellSize
        val gridMaxY = grid.originY + grid.height * grid.cellSize

        val orientations = (0 until 360 step orientationStep).toList()
        val orientationTrig = orientations.map { orient ->
            orient to (COS_TABLE[orient * 10] to SIN_TABLE[orient * 10])
        }

        data class OrientationResult(val score: Int, val estimate: Estimate?, val combinations: Int)

        val globalBest = AtomicInteger(-1)

        val results = coroutineScope {
            orientationTrig.map { (orient, trig) ->
                async {
                    val (cosA, sinA) = trig
                    val xBase = sinNd * cosA - cosNd * sinA
                    val yBase = sinNd * sinA + cosNd * cosA

                    var localBestScore = -1
                    var localBestEstimate: Estimate? = null
                    var localCombinations = 0

                    var scale = scaleRange.start
                    while (scale <= scaleRange.endInclusive + 1e-6f) {
                        val scaledDist = distNd * scale
                        val xsNd = xBase * scaledDist
                        val ysNd = yBase * scaledDist
                        val xs = FloatArray(count) { i: Int -> xsNd[i] }
                        val ys = FloatArray(count) { i: Int -> ysNd[i] }
                        val search = searchTranslation(
                            xs,
                            ys,
                            grid,
                            gridMaxX,
                            gridMaxY,
                            globalBest,
                            missPenalty,
                        )
                        localCombinations += search.combinations
                        if (search.score > localBestScore) {
                            localBestScore = search.score
                            localBestEstimate = Estimate(orient.toFloat(), scale, search.x to search.y)
                            if (search.score > globalBest.get()) {
                                globalBest.updateAndGet { max(it, search.score) }
                            }
                        }
                        scale += scaleStep
                    }
                    OrientationResult(localBestScore, localBestEstimate, localCombinations)
                }
            }.awaitAll()
        }

        var bestScore = -1
        var bestEstimate: Estimate? = null
        var combinations = 0
        for (r in results) {
            combinations += r.combinations
            if (r.score > bestScore) {
                bestScore = r.score
                bestEstimate = r.estimate
            }
        }
        if (bestScore <= 0) EstimateResult(null, combinations, bestScore)
        else EstimateResult(bestEstimate, combinations, bestScore)
    }

    private data class SearchResult(
        val x: Float,
        val y: Float,
        val score: Int,
        val combinations: Int,
    )

    private fun searchTranslation(
        xs: FloatArray,
        ys: FloatArray,
        grid: OccupancyGrid,
        gridMaxX: Float,
        gridMaxY: Float,
        globalBest: AtomicInteger,
        missPenalty: Int,
    ): SearchResult {
        var bestScore = -1
        var bestX = 0f
        var bestY = 0f
        var combinations = 0

        // Skip evaluating this orientation only if it is impossible to beat the
        // current global best score. Using '<' rather than '<=' allows other
        // orientations with a potentially equal score to be considered so the
        // earliest matching orientation is chosen deterministically.
        if (xs.size < globalBest.get()) {
            return SearchResult(0f, 0f, -1, 0)
        }

        fun evaluate(step: Float, minX: Float, maxX: Float, minY: Float, maxY: Float) {
            var y = minY
            while (y <= maxY) {
                var x = minX
                while (x <= maxX) {
                    combinations++
                    var hits = 0
                    var misses = 0
                    var remaining = xs.size
                    val global = globalBest.get()
                    var i = 0
                    while (i < xs.size) {
                        if (grid.isOccupied(xs[i] + x, ys[i] + y)) hits++ else misses++
                        remaining--
                        val potential = hits + remaining - missPenalty * misses
                        if (potential <= max(bestScore, global)) break
                        i++
                    }
                    val score = hits - missPenalty * misses
                    if (score > bestScore) {
                        bestScore = score
                        bestX = x
                        bestY = y
                        if (score > global) {
                            globalBest.updateAndGet { max(it, score) }
                        }
                    }
                    x += step
                }
                y += step
            }
        }

        val coarse = grid.cellSize * 4
        evaluate(coarse, grid.originX, gridMaxX, grid.originY, gridMaxY)

        val minX = max(grid.originX, bestX - coarse)
        val maxX = min(gridMaxX, bestX + coarse)
        val minY = max(grid.originY, bestY - coarse)
        val maxY = min(gridMaxY, bestY + coarse)
        evaluate(grid.cellSize, minX, maxX, minY, maxY)

        return SearchResult(bestX, bestY, bestScore, combinations)
    }
}

