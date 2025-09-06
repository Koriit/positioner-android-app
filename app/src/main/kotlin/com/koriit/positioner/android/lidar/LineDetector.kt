package com.koriit.positioner.android.lidar

import org.apache.commons.math3.stat.descriptive.rank.Percentile
import org.apache.commons.math3.stat.regression.SimpleRegression
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

/**
 * Detect straight line segments in a set of lidar measurements.
 */
object LineDetector {
    data class LineFeature(
        val start: Pair<Float, Float>,
        val end: Pair<Float, Float>,
        val orientation: Float,
        val length: Float,
        /** Number of original measurements that formed this line. */
        val pointCount: Int,
    )

    data class AdaptiveFilterParams(
        val enabled: Boolean,
        val lengthPercentile: Double,
        val lengthFactor: Double,
        val lengthMin: Double,
        val lengthMax: Double,
        val inliersPercentile: Double,
        val inliersFactor: Double,
        val inliersMin: Double,
        val inliersMax: Double,
    )

    data class AdaptiveStats(
        val lengthPx: Double,
        val inliersPx: Double,
    )

    /** Normalize orientation in degrees to the [0, 180) range. */
    private fun normalizeOrientation(deg: Float): Float {
        var angle = deg % 180f
        if (angle < 0f) angle += 180f
        return angle
    }

    /**
     * Detect line features from [measurements].
     */
    fun detect(
        measurements: List<LidarMeasurement>,
        distanceThreshold: Float,
        minPoints: Int,
        angleTolerance: Float,
        gapTolerance: Float,
        algorithm: LineAlgorithm = LineAlgorithm.CLUSTER,
        merge: Boolean = true,
    ): List<LineFeature> {
        return when (algorithm) {
            LineAlgorithm.CLUSTER -> detectCluster(
                measurements,
                distanceThreshold,
                minPoints,
                angleTolerance,
                gapTolerance,
                merge,
            )
            LineAlgorithm.RANSAC -> detectRansac(
                measurements,
                distanceThreshold,
                minPoints,
                angleTolerance,
                merge,
            )
        }
    }

    private fun merge(lines: List<LineFeature>, angleTolerance: Float): List<LineFeature> {
        if (lines.size <= 1 || angleTolerance <= 0f) return lines
        val merged = mutableListOf<LineFeature>()
        for (line in lines) {
            val last = merged.lastOrNull()
            if (last != null && abs(line.orientation - last.orientation) <= angleTolerance) {
                val newLine = LineFeature(
                    last.start,
                    line.end,
                    last.orientation,
                    hypot(line.end.first - last.start.first, line.end.second - last.start.second),
                    last.pointCount + line.pointCount,
                )
                merged[merged.lastIndex] = newLine
            } else {
                merged.add(line)
            }
        }
        return merged
    }

    private fun detectCluster(
        measurements: List<LidarMeasurement>,
        distanceThreshold: Float,
        minPoints: Int,
        angleTolerance: Float,
        gapTolerance: Float,
        mergeLines: Boolean,
    ): List<LineFeature> {
        if (measurements.isEmpty()) return emptyList()
        val sorted = measurements.sortedBy { it.angle }
        val lines = mutableListOf<LineFeature>()
        var cluster = mutableListOf<Pair<Float, Float>>()
        var reg = SimpleRegression()
        var lastAngle = sorted.first().angle
        fun finalizeCluster() {
            if (cluster.size < minPoints) {
                cluster = mutableListOf()
                reg = SimpleRegression()
                return
            }
            val slope = reg.slope
            val angleRad = if (slope.isFinite()) {
                kotlin.math.atan2(1.0, slope)
            } else {
                val first = cluster.first()
                val last = cluster.last()
                atan2((last.first - first.first).toDouble(), (last.second - first.second).toDouble())
            }
            val ux = kotlin.math.sin(angleRad).toFloat()
            val uy = kotlin.math.cos(angleRad).toFloat()
            val refx = cluster.sumOf { it.first.toDouble() }.toFloat() / cluster.size
            val refy = cluster.sumOf { it.second.toDouble() }.toFloat() / cluster.size
            var minT = Float.POSITIVE_INFINITY
            var maxT = Float.NEGATIVE_INFINITY
            for ((x, y) in cluster) {
                val t = (x - refx) * ux + (y - refy) * uy
                if (t < minT) minT = t
                if (t > maxT) maxT = t
            }
            val start = Pair(refx + minT * ux, refy + minT * uy)
            val end = Pair(refx + maxT * ux, refy + maxT * uy)
            val length = hypot(end.first - start.first, end.second - start.second)
            val orientation = normalizeOrientation((angleRad * 180f / PI).toFloat())
            lines.add(LineFeature(start, end, orientation, length, cluster.size))
            cluster = mutableListOf()
            reg = SimpleRegression()
        }
        for (m in sorted) {
            val point = m.toPoint()
            if (cluster.isEmpty()) {
                cluster.add(point)
                reg.addData(point.first.toDouble(), point.second.toDouble())
                lastAngle = m.angle
                continue
            }
            val angleGap = abs(m.angle - lastAngle)
            if (angleGap > gapTolerance) {
                finalizeCluster()
                cluster.add(point)
                reg.addData(point.first.toDouble(), point.second.toDouble())
                lastAngle = m.angle
                continue
            }
            val slope = reg.slope
            val intercept = reg.intercept
            val dist = abs(slope * point.first - point.second + intercept) / hypot(slope.toFloat(), 1f)
            if (dist > distanceThreshold) {
                finalizeCluster()
                cluster.add(point)
                reg.addData(point.first.toDouble(), point.second.toDouble())
            } else {
                cluster.add(point)
                reg.addData(point.first.toDouble(), point.second.toDouble())
            }
            lastAngle = m.angle
        }
        finalizeCluster()
        return if (mergeLines) merge(lines, angleTolerance) else lines
    }

    private fun detectRansac(
        measurements: List<LidarMeasurement>,
        distanceThreshold: Float,
        minPoints: Int,
        angleTolerance: Float,
        mergeLines: Boolean,
    ): List<LineFeature> {
        if (measurements.isEmpty()) return emptyList()
        val remaining = measurements.toMutableList()
        val lines = mutableListOf<LineFeature>()
        while (remaining.size >= minPoints) {
            var bestInliers: List<LidarMeasurement> = emptyList()
            var bestA = 0f
            var bestB = 0f
            var bestC = 0f
            repeat(100) {
                val p1 = remaining.random()
                var p2 = remaining.random()
                if (p1 === p2) return@repeat
                val (x1, y1) = p1.toPoint()
                val (x2, y2) = p2.toPoint()
                val A = y1 - y2
                val B = x2 - x1
                val C = x1 * y2 - x2 * y1
                val norm = hypot(A, B)
                if (norm == 0f) return@repeat
                val inliers = remaining.filter {
                    val (x, y) = it.toPoint()
                    abs(A * x + B * y + C) / norm <= distanceThreshold
                }
                if (inliers.size > bestInliers.size) {
                    bestInliers = inliers
                    bestA = A
                    bestB = B
                    bestC = C
                }
            }
            if (bestInliers.size < minPoints) break
            val pts = bestInliers.map { it.toPoint() }
            val angleRad = atan2(bestB.toDouble(), -bestA.toDouble())
            val ux = sin(angleRad).toFloat()
            val uy = cos(angleRad).toFloat()
            val refx = pts.sumOf { it.first.toDouble() }.toFloat() / pts.size
            val refy = pts.sumOf { it.second.toDouble() }.toFloat() / pts.size
            var minT = Float.POSITIVE_INFINITY
            var maxT = Float.NEGATIVE_INFINITY
            for ((x, y) in pts) {
                val t = (x - refx) * ux + (y - refy) * uy
                if (t < minT) minT = t
                if (t > maxT) maxT = t
            }
            val start = Pair(refx + minT * ux, refy + minT * uy)
            val end = Pair(refx + maxT * ux, refy + maxT * uy)
            val length = hypot(end.first - start.first, end.second - start.second)
            val orientation = normalizeOrientation((angleRad * 180f / PI).toFloat())
            lines.add(LineFeature(start, end, orientation, length, bestInliers.size))
            remaining.removeAll(bestInliers.toSet())
        }
        return if (mergeLines) merge(lines, angleTolerance) else lines
    }

    /**
     * Adaptively filter [lines] based on length and inlier count percentiles.
     * Returns the filtered list along with the raw percentile values.
     */
    fun filterAdaptive(
        lines: List<LineFeature>,
        params: AdaptiveFilterParams,
    ): Pair<List<LineFeature>, AdaptiveStats> {
        if (lines.isEmpty()) return lines to AdaptiveStats(0.0, 0.0)
        val lengths = lines.map { it.length.toDouble() }.toDoubleArray()
        val counts = lines.map { it.pointCount.toDouble() }.toDoubleArray()
        val lengthPx = Percentile(params.lengthPercentile).evaluate(lengths)
        val inliersPx = Percentile(params.inliersPercentile).evaluate(counts)
        if (!params.enabled) return lines to AdaptiveStats(lengthPx, inliersPx)
        val minLength = (params.lengthFactor * lengthPx).coerceIn(params.lengthMin, params.lengthMax)
        val minInliers = (params.inliersFactor * inliersPx).coerceIn(params.inliersMin, params.inliersMax)
        val filtered = lines.filter { it.length >= minLength && it.pointCount >= minInliers }
        return filtered to AdaptiveStats(lengthPx, inliersPx)
    }

    /**
     * Convert [lines] back to lidar measurements by sampling along each line.
     * This preserves the contribution of long lines by generating a number of
     * points equal to the original measurement count.
     */
    fun asMeasurements(lines: List<LineFeature>): List<LidarMeasurement> {
        return lines.flatMap { line ->
            val count = line.pointCount.coerceAtLeast(2)
            val step = 1f / (count - 1)
            (0 until count).map { i ->
                val t = i * step
                val x = line.start.first + t * (line.end.first - line.start.first)
                val y = line.start.second + t * (line.end.second - line.start.second)
                val r = hypot(x, y)
                val angle = atan2(x, y) * 180f / PI.toFloat()
                LidarMeasurement(angle, (r * 1000f).toInt(), 255)
            }
        }
    }
}

