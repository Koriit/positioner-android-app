package com.koriit.positioner.android.recording

import com.koriit.positioner.android.lidar.LidarMeasurement
import com.koriit.positioner.android.gyro.GyroscopeMeasurement
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Single LiDAR rotation with timestamped measurements.
 *
 * The class is deliberately extendable; new attributes can be
 * added in future versions without breaking serialization.
 */
@Serializable
data class Rotation(
    val measurements: List<LidarMeasurement>,
    val start: Instant = Clock.System.now(),
    val gyroscope: List<GyroscopeMeasurement> = emptyList(),
    /**
     * Absolute device orientation in degrees after this rotation.
     */
    val gyroscopeOrientation: Float? = null,
    /**
     * Packets discarded due to CRC errors while collecting this rotation.
     */
    val corruptedPackets: Int = 0,
)
