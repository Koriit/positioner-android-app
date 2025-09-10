package com.koriit.positioner.android.gyro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Streams gyroscope measurements as a [Flow].
 *
 * Sampling rate defaults to [DEFAULT_RATE_HZ] and will never fall below [MIN_RATE_HZ].
 * If the sensor is unavailable, disabled or permissions are missing the flow completes without emitting.
 */
class GyroscopeReader private constructor(
    private val manager: SensorManager,
    private val sensor: Sensor,
    private val samplingPeriodUs: Int,
) {
    /**
     * Emit gyroscope readings as they arrive.
     */
    fun measurements(): Flow<GyroscopeMeasurement> = channelFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(
                    GyroscopeMeasurement(
                        event.values[0],
                        event.values[1],
                        event.values[2],
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = try {
            manager.registerListener(listener, sensor, samplingPeriodUs)
        } catch (e: SecurityException) {
            close(e)
            return@channelFlow
        }
        if (!registered) {
            close(IllegalStateException("Gyroscope disabled"))
            return@channelFlow
        }

        awaitClose { manager.unregisterListener(listener) }
    }.flowOn(Dispatchers.Default)

    sealed class OpenResult {
        class Success(val reader: GyroscopeReader) : OpenResult()
        object NoSensor : OpenResult()
        object NoPermission : OpenResult()
    }

    companion object {
        const val MIN_RATE_HZ = 100
        const val DEFAULT_RATE_HZ = 200
        private const val HIGH_RATE_THRESHOLD_HZ = 200

        /**
         * Attempt to open a gyroscope reader.
         */
        fun open(context: Context, rateHz: Int = DEFAULT_RATE_HZ): OpenResult {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
                return OpenResult.NoPermission
            }
            if (
                rateHz > HIGH_RATE_THRESHOLD_HZ &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.HIGH_SAMPLING_RATE_SENSORS) != PackageManager.PERMISSION_GRANTED
            ) {
                return OpenResult.NoPermission
            }
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                ?: return OpenResult.NoSensor
            val sensor = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return OpenResult.NoSensor
            val clamped = rateHz.coerceAtLeast(MIN_RATE_HZ)
            val sampling = 1_000_000 / clamped
            return OpenResult.Success(GyroscopeReader(manager, sensor, sampling))
        }
    }
}
