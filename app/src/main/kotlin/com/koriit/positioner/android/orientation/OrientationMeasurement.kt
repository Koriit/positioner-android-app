package com.koriit.positioner.android.orientation

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Timestamped orientation quaternion reported by the rotation vector sensor.
 *
 * Quaternion components follow the [w, x, y, z] convention used by
 * [android.hardware.SensorManager.getQuaternionFromVector]. Values are stored
 * as floats to match the platform representation.
 */
@Serializable
data class OrientationMeasurement(
    val w: Float,
    val x: Float,
    val y: Float,
    val z: Float,
    val accuracy: Int? = null,
    val timestamp: Instant = Clock.System.now(),
)
