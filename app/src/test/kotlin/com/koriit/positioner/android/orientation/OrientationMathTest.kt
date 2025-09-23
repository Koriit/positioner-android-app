package com.koriit.positioner.android.orientation

import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrientationMathTest {

    @Test
    fun `yawDegrees returns expected yaw`() {
        val component = sqrt(0.5f)
        val measurement = OrientationMeasurement(
            w = component,
            x = 0f,
            y = 0f,
            z = component,
        )

        assertEquals(90f, measurement.yawDegrees(), 1e-3f)
    }

    @Test
    fun `yawDegrees normalizes angle`() {
        val measurement = OrientationMeasurement(
            w = 0f,
            x = 0f,
            y = 1f,
            z = 0f,
        )

        assertEquals(-180f, measurement.yawDegrees(), 1e-3f)
    }
}
