package com.koriit.positioner.android.orientation

import com.koriit.positioner.android.gyro.GyroscopeOrientationTracker
import kotlin.math.atan2

/**
 * Convert the quaternion into a normalised yaw angle in degrees.
 */
fun OrientationMeasurement.yawDegrees(): Float {
    val sinyCosp = 2f * (w * z + x * y)
    val cosyCosp = 1f - 2f * (y * y + z * z)
    val yawRad = atan2(sinyCosp.toDouble(), cosyCosp.toDouble())
    val yawDeg = Math.toDegrees(yawRad).toFloat()
    return GyroscopeOrientationTracker.normalizeDegrees(yawDeg)
}
