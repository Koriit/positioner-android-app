package com.koriit.positioner.android.gyro

import com.koriit.positioner.android.recording.Rotation
import kotlinx.datetime.Instant

/**
 * Utility that integrates gyroscope samples into an absolute orientation.
 */
class GyroscopeOrientationTracker {

    private var orientationRad: Float = 0f
    private var orientationDeg: Float = 0f
    private var lastTimestamp: Instant? = null

    /**
     * Integrate [samples] into the internal orientation estimate.
     *
     * @param samples samples ordered by timestamp.
     * @param startTimestamp optional timestamp to use for the first integration
     * window. When not provided the tracker falls back to the previous sample
     * time or the first element in [samples].
     * @return normalised orientation in degrees within [-180, 180).
     */
    fun integrate(samples: List<GyroscopeMeasurement>, startTimestamp: Instant? = null): Float {
        if (samples.isEmpty()) return orientationDeg
        var previous = startTimestamp ?: lastTimestamp ?: samples.first().timestamp
        var updated = orientationRad
        samples.forEach { measurement ->
            val delta = measurement.timestamp - previous
            val deltaNs = delta.inWholeNanoseconds
            if (deltaNs != 0L) {
                val deltaSeconds = deltaNs / 1_000_000_000f
                updated += measurement.z * deltaSeconds
            }
            previous = measurement.timestamp
        }
        lastTimestamp = previous
        orientationRad = normalizeRadians(updated)
        orientationDeg = normalizeDegrees(Math.toDegrees(orientationRad.toDouble()).toFloat())
        return orientationDeg
    }

    /**
     * Replace the current orientation with an absolute value.
     */
    fun apply(orientation: Float, lastTimestamp: Instant?) {
        val normalized = normalizeDegrees(orientation)
        orientationDeg = normalized
        orientationRad = Math.toRadians(normalized.toDouble()).toFloat()
        if (lastTimestamp != null) {
            this.lastTimestamp = lastTimestamp
        }
    }

    fun reset() {
        orientationRad = 0f
        orientationDeg = 0f
        lastTimestamp = null
    }

    fun currentOrientation(): Float = orientationDeg

    fun lastSampleTimestamp(): Instant? = lastTimestamp

    companion object {
        fun normalizeRadians(angle: Float): Float {
            val twoPi = (Math.PI * 2.0).toFloat()
            var value = angle % twoPi
            if (value >= Math.PI) value -= twoPi
            if (value < -Math.PI) value += twoPi
            return value
        }

        fun normalizeDegrees(angle: Float): Float {
            var value = angle % 360f
            if (value >= 180f) value -= 360f
            if (value < -180f) value += 360f
            return value
        }

        fun withOrientation(rotations: List<Rotation>): List<Rotation> {
            val tracker = GyroscopeOrientationTracker()
            var currentOrientation = 0f
            var previousTimestamp: Instant? = null
            return rotations.map { rotation ->
                val stored = rotation.gyroscopeOrientation?.let { normalizeDegrees(it) }
                val orientation = when {
                    stored != null -> {
                        tracker.apply(stored, rotation.gyroscope.lastOrNull()?.timestamp ?: previousTimestamp)
                        stored
                    }
                    rotation.gyroscope.isEmpty() -> currentOrientation
                    else -> {
                        val start = previousTimestamp ?: rotation.gyroscope.first().timestamp
                        tracker.integrate(rotation.gyroscope, start)
                    }
                }
                currentOrientation = orientation
                previousTimestamp = rotation.gyroscope.lastOrNull()?.timestamp ?: previousTimestamp
                rotation.copy(gyroscopeOrientation = orientation)
            }
        }
    }
}
