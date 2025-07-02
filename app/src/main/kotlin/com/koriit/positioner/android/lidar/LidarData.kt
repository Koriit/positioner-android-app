package com.koriit.positioner.android.lidar

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlinx.serialization.Serializable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Single measurement from the LIDAR.
 */
@Serializable
data class LidarMeasurement(
    val angle: Float,       // angle in degrees
    val distanceMm: Int,    // distance in millimetres
    val confidence: Int,
    val timestamp: Instant = Clock.System.now()
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
