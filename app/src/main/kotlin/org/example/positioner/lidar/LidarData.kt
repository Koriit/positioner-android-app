package org.example.positioner.lidar

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Single measurement from the LIDAR.
 */
data class LidarMeasurement(
    val angle: Float,       // angle in degrees
    val distanceMm: Int,    // distance in millimetres
    val confidence: Int
) {
    /**
     * Convert the polar measurement to cartesian coordinates in metres.
     */
    fun toPoint(): Pair<Float, Float> {
        val r = distanceMm / 1000f
        val rad = angle / 180f * PI.toFloat()
        val x = sin(rad) * r
        val y = cos(rad) * r
        return x to y
    }
}
