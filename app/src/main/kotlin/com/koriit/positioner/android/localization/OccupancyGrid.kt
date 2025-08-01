package com.koriit.positioner.android.localization

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import kotlin.math.ceil

/**
 * Simple occupancy grid built from a polygon floor plan.
 * Each cell stores whether the polygon covers the cell centre.
 */
class OccupancyGrid(
    val width: Int,
    val height: Int,
    val cellSize: Float,
    val originX: Float,
    val originY: Float,
    private val data: Array<BooleanArray>,
) {
    fun isOccupied(x: Float, y: Float): Boolean {
        val ix = ((x - originX) / cellSize).toInt()
        val iy = ((y - originY) / cellSize).toInt()
        if (ix < 0 || ix >= width || iy < 0 || iy >= height) return false
        return data[iy][ix]
    }

    companion object {
        fun fromPolygon(points: List<Pair<Float, Float>>, cellSize: Float = 0.1f): OccupancyGrid {
            require(points.size >= 3)
            val geomFactory = GeometryFactory()
            val coords = points.map { (x, y) -> Coordinate(x.toDouble(), y.toDouble()) } +
                listOf(Coordinate(points.first().first.toDouble(), points.first().second.toDouble()))
            val polygon: Polygon = geomFactory.createPolygon(coords.toTypedArray())
            val env = polygon.envelopeInternal
            val width = ceil(env.width / cellSize).toInt() + 1
            val height = ceil(env.height / cellSize).toInt() + 1
            val data = Array(height) { BooleanArray(width) }
            val originX = env.minX.toFloat()
            val originY = env.minY.toFloat()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val cx = originX + x * cellSize + cellSize / 2
                    val cy = originY + y * cellSize + cellSize / 2
                    data[y][x] = polygon.contains(geomFactory.createPoint(Coordinate(cx.toDouble(), cy.toDouble())))
                }
            }
            return OccupancyGrid(width, height, cellSize, originX, originY, data)
        }
    }
}
