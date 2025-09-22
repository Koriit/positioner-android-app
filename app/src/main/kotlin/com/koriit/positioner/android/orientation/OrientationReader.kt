package com.koriit.positioner.android.orientation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Streams rotation vector measurements as quaternions.
 */
class OrientationReader private constructor(
    private val manager: SensorManager,
    private val sensor: Sensor,
) {
    /**
     * Emit orientation quaternions as they arrive.
     */
    fun measurements(): Flow<OrientationMeasurement> = channelFlow {
        val listener = object : SensorEventListener {
            private val quaternion = FloatArray(4)

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getQuaternionFromVector(quaternion, event.values)
                trySend(
                    OrientationMeasurement(
                        w = quaternion[0],
                        x = quaternion[1],
                        y = quaternion[2],
                        z = quaternion[3],
                        accuracy = event.accuracy,
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = manager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_GAME,
        )
        if (!registered) {
            close(IllegalStateException("Orientation sensor disabled"))
            return@channelFlow
        }

        awaitClose { manager.unregisterListener(listener) }
    }.flowOn(Dispatchers.Default)

    sealed class OpenResult {
        class Success(val reader: OrientationReader) : OpenResult()
        object NoSensor : OpenResult()
    }

    companion object {
        /**
         * Attempt to open the fused orientation sensor.
         *
         * Prefer the game rotation vector because it ignores the magnetometer
         * and therefore avoids sudden jumps when the magnetic field changes.
         * Fall back to the full rotation vector if the game variant is not
         * available on the device so older hardware keeps working.
         */
        fun open(context: Context): OpenResult {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                ?: return OpenResult.NoSensor

            val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: return OpenResult.NoSensor

            return OpenResult.Success(OrientationReader(manager, sensor))
        }
    }
}
