package com.koriit.positioner.android.gyro

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Single timestamped gyroscope reading.
 *
 * Values are expressed as radians per second around each device axis.
 */
@Serializable
data class GyroscopeMeasurement(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestamp: Instant = Clock.System.now(),
)
