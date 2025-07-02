package com.koriit.positioner.android.lidar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.sin
import kotlin.math.PI

/**
 * Generates fake lidar measurements for testing in the emulator.
 */
class FakeLidarReader : LidarDataSource {
    override fun measurements(): Flow<LidarMeasurement> = flow {
        var angle = 0f
        while (true) {
            val radiusMm = 1000 + (500 * (1 + sin(angle / 180f * PI.toFloat()))).toInt()
            emit(LidarMeasurement(angle % 360f, radiusMm, 255))
            angle += 2f
            delay(20)
        }
    }.flowOn(Dispatchers.Default)
}
