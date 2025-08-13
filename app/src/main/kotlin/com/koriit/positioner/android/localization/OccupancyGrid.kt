package com.koriit.positioner.android.localization

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.prep.PreparedGeometry
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import kotlin.math.ceil
import kotlin.math.max

/**
 * Spatial occupancy map backed by a quadtree.
 *
 * The tree recursively subdivides the floor-plan polygon until regions are
 * wholly inside or outside the plan or the desired [cellSize] is reached. This
 * allows fast occupancy queries with modest memory usage compared to a dense
 * grid.
 */
class OccupancyGrid private constructor(
    val width: Int,
    val height: Int,
    val cellSize: Float,
    val originX: Float,
    val originY: Float,
    private val treeSize: Float,
    private val root: Node,
) {
    /**
     * Returns `true` if the given point lies inside the floor-plan polygon.
     */
    fun isOccupied(x: Float, y: Float): Boolean {
        if (x < originX || x >= originX + width * cellSize ||
            y < originY || y >= originY + height * cellSize) {
            return false
        }
        return isOccupied(root, originX, originY, treeSize, x, y)
    }

    private fun isOccupied(node: Node, x0: Float, y0: Float, size: Float, x: Float, y: Float): Boolean {
        return when (node) {
            Node.Empty -> false
            Node.Full -> true
            is Node.Quad -> {
                val half = size / 2
                val midX = x0 + half
                val midY = y0 + half
                if (x < midX) {
                    if (y < midY) isOccupied(node.sw, x0, y0, half, x, y)
                    else isOccupied(node.nw, x0, midY, half, x, y)
                } else {
                    if (y < midY) isOccupied(node.se, midX, y0, half, x, y)
                    else isOccupied(node.ne, midX, midY, half, x, y)
                }
            }
        }
    }

    private sealed class Node {
        object Empty : Node()
        object Full : Node()
        class Quad(
            val nw: Node,
            val ne: Node,
            val sw: Node,
            val se: Node,
        ) : Node()
    }

    companion object {
        fun fromPolygon(points: List<Pair<Float, Float>>, cellSize: Float = 0.1f): OccupancyGrid {
            require(points.size >= 3)
            val geomFactory = GeometryFactory()
            val coords = points.map { (x, y) -> Coordinate(x.toDouble(), y.toDouble()) } +
                listOf(Coordinate(points.first().first.toDouble(), points.first().second.toDouble()))
            val polygon: Polygon = geomFactory.createPolygon(coords.toTypedArray())
            val prepared: PreparedGeometry = PreparedGeometryFactory().create(polygon)

            val env = polygon.envelopeInternal
            val width = ceil(env.width / cellSize).toInt() + 1
            val height = ceil(env.height / cellSize).toInt() + 1
            val originX = env.minX.toFloat()
            val originY = env.minY.toFloat()
            val size = max(width, height) * cellSize

            fun build(x0: Float, y0: Float, size: Float): Node {
                val square = geomFactory.createPolygon(
                    arrayOf(
                        Coordinate(x0.toDouble(), y0.toDouble()),
                        Coordinate((x0 + size).toDouble(), y0.toDouble()),
                        Coordinate((x0 + size).toDouble(), (y0 + size).toDouble()),
                        Coordinate(x0.toDouble(), (y0 + size).toDouble()),
                        Coordinate(x0.toDouble(), y0.toDouble()),
                    )
                )
                if (prepared.contains(square)) return Node.Full
                if (!prepared.intersects(square)) return Node.Empty
                if (size <= cellSize) {
                    val cx = x0 + size / 2
                    val cy = y0 + size / 2
                    val point = geomFactory.createPoint(Coordinate(cx.toDouble(), cy.toDouble()))
                    return if (prepared.contains(point)) Node.Full else Node.Empty
                }
                val half = size / 2
                return Node.Quad(
                    nw = build(x0, y0 + half, half),
                    ne = build(x0 + half, y0 + half, half),
                    sw = build(x0, y0, half),
                    se = build(x0 + half, y0, half),
                )
            }

            val root = build(originX, originY, size)
            return OccupancyGrid(width, height, cellSize, originX, originY, size, root)
        }
    }
}

