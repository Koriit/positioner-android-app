package com.koriit.positioner.android.lidar

import kotlinx.coroutines.flow.Flow

/**
 * Common interface for classes that can provide lidar measurements.
 */
interface LidarDataSource {
    /** Stream of lidar measurements. */
    fun measurements(): Flow<LidarMeasurement>
}
