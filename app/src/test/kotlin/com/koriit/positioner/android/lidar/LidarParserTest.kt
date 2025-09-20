package com.koriit.positioner.android.lidar

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LidarParserTest {

    @Test
    fun `normalizes angles that wrap past 360 degrees`() {
        val packet = ByteBuffer.allocate(47).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0x54.toByte())
            put(0x2C.toByte())
            putShort(0) // speed
            putShort((35000).toShort()) // start angle = 350 degrees
            repeat(12) { index ->
                putShort((1000 + index).toShort())
                put(index.toByte())
            }
            putShort((1000).toShort()) // stop angle = 10 degrees
            putShort(0) // timestamp
            put(0) // crc
        }.array()

        val measurements = LidarParser().parse(packet)

        assertEquals(12, measurements.size)
        assertEquals(10f, measurements.last().angle, 1e-3f)
        assertTrue(measurements.all { it.angle >= 0f && it.angle < 360f })
    }
}
