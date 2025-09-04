package com.koriit.positioner.android.lidar

import org.apache.commons.math3.stat.descriptive.rank.Percentile
import org.apache.commons.math3.stat.regression.SimpleRegression
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.PI

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

    /**
     * Detect line features from [measurements].
     */
    fun detect(
        measurements: List<LidarMeasurement>,
        distanceThreshold: Float,
        minPoints: Int,
        angleTolerance: Float,
        gapTolerance: Float,
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
            val angleRad = if (slope.isNaN()) 0.0 else atan2(1.0, slope)
            val dirX = kotlin.math.sin(angleRad).toFloat()
            val dirY = kotlin.math.cos(angleRad).toFloat()
            val base = cluster.first()
            val projections = cluster.map { (x, y) ->
                (x - base.first) * dirX + (y - base.second) * dirY
            }
            val minProj = projections.minOrNull() ?: 0f
            val maxProj = projections.maxOrNull() ?: 0f
            val start = base.first + dirX * minProj to base.second + dirY * minProj
            val end = base.first + dirX * maxProj to base.second + dirY * maxProj
            val length = hypot(end.first - start.first, end.second - start.second)
            val orientation = (angleRad * 180.0 / PI).toFloat()
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

