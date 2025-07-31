package com.koriit.positioner.android.localization

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import kotlin.math.cos
import kotlin.math.sin

internal object PositionEstimatorTestHelper {
    private val geomFactory = GeometryFactory()

    private fun polygon(points: List<Pair<Float, Float>>): Polygon {
        val coords = points.map { (x, y) -> Coordinate(x.toDouble(), y.toDouble()) } +
            listOf(Coordinate(points.first().first.toDouble(), points.first().second.toDouble()))
        return geomFactory.createPolygon(coords.toTypedArray())
    }

    fun profile(points: List<Pair<Float, Float>>): DoubleArray {
        val poly = polygon(points)
        val center = poly.centroid.coordinate
        val profile = DoubleArray(360)
        for (deg in 0 until 360) {
            val rad = Math.toRadians(deg.toDouble())
            val far = Coordinate(center.x + sin(rad) * 1000, center.y + cos(rad) * 1000)
            val line = geomFactory.createLineString(arrayOf(center, far))
            val inter = poly.boundary.intersection(line)
            profile[deg] = if (inter.isEmpty) 0.0 else inter.coordinates.minOf { center.distance(it) }
        }
        return profile
    }
}
