package com.koriit.positioner.android.localization

import com.koriit.positioner.android.lidar.LidarMeasurement
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import kotlin.math.cos
import kotlin.math.sin

/**
 * Estimate orientation, scale and position of the sensor relative to a polygon floor plan.
 * Uses JTS for geometric calculations to avoid manual intersection code.
 */
object PositionEstimator {
    data class Estimate(
        val orientation: Float,
        val scale: Float,
        val position: Pair<Float, Float>,
    )

    private val geomFactory = GeometryFactory()

    private fun buildPolygon(points: List<Pair<Float, Float>>): Polygon {
        val coords = points.map { (x, y) -> Coordinate(x.toDouble(), y.toDouble()) } +
            listOf(Coordinate(points.first().first.toDouble(), points.first().second.toDouble()))
        return geomFactory.createPolygon(coords.toTypedArray())
    }

    private fun rayDistance(polygon: Polygon, origin: Coordinate, angleDeg: Int): Double {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = sin(rad)
        val dy = cos(rad)
        val far = Coordinate(origin.x + dx * 1000, origin.y + dy * 1000)
        val line = geomFactory.createLineString(arrayOf(origin, far))
        val intersection = polygon.boundary.intersection(line)
        if (intersection.isEmpty) return Double.POSITIVE_INFINITY
        return intersection.coordinates.minOf { origin.distance(it) }
    }

    private fun distanceProfile(polygon: Polygon, origin: Coordinate): DoubleArray {
        val profile = DoubleArray(360)
        for (deg in 0 until 360) {
            profile[deg] = rayDistance(polygon, origin, deg)
        }
        return profile
    }

    /**
     * Estimate orientation, scale and position relative to [polygonPoints].
     */
    fun estimate(
        measurements: List<LidarMeasurement>,
        polygonPoints: List<Pair<Float, Float>>,
    ): Estimate? {
        if (measurements.isEmpty() || polygonPoints.size < 3) return null
        val polygon = buildPolygon(polygonPoints)
        val center = polygon.centroid.coordinate
        val profile = distanceProfile(polygon, center)

        val measByDeg = DoubleArray(360) { Double.NaN }
        val counts = IntArray(360)
        for (m in measurements) {
            val idx = ((m.angle + 360) % 360).toInt()
            val dist = m.distanceMm / 1000.0
            measByDeg[idx] = if (measByDeg[idx].isNaN()) dist else measByDeg[idx] + dist
            counts[idx]++
        }
        for (i in 0 until 360) if (counts[i] > 0) measByDeg[i] /= counts[i]

        var bestOrientation = 0
        var bestScale = 1.0
        var bestError = Double.POSITIVE_INFINITY

        for (orient in 0 until 360) {
            var sumD2 = 0.0
            var sumMD = 0.0
            var count = 0
            for (i in 0 until 360) {
                val m = measByDeg[i]
                if (m.isNaN()) continue
                val d = profile[(i + orient) % 360]
                sumMD += m * d
                sumD2 += d * d
                count++
            }
            if (count < 10 || sumD2 == 0.0) continue
            val scale = sumMD / sumD2
            var error = 0.0
            for (i in 0 until 360) {
                val m = measByDeg[i]
                if (m.isNaN()) continue
                val d = profile[(i + orient) % 360] * scale
                val diff = m - d
                error += diff * diff
            }
            error /= count
            if (error < bestError) {
                bestError = error
                bestOrientation = orient
                bestScale = scale
            }
        }

        // Compute sensor position as difference between measured centroid and polygon centroid
        val transformed = measurements.map { m ->
            val r = m.distanceMm / 1000.0 * bestScale
            val ang = Math.toRadians(m.angle + bestOrientation.toDouble())
            val x = sin(ang) * r
            val y = cos(ang) * r
            Coordinate(x, y)
        }
        val multi = geomFactory.createMultiPointFromCoords(transformed.toTypedArray())
        val measCentroid = multi.centroid.coordinate
        val tx = (measCentroid.x - center.x).toFloat()
        val ty = (measCentroid.y - center.y).toFloat()

        return Estimate(bestOrientation.toFloat(), bestScale.toFloat(), tx to ty)
    }
}
