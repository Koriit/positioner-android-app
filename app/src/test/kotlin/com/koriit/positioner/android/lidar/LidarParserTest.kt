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
            put(0) // crc placeholder
        }.array().apply { this[lastIndex] = computeCrc(this).toByte() }

        val measurements = LidarParser().parse(packet)

        assertEquals(12, measurements.size)
        assertEquals(10f, measurements.last().angle, 1e-3f)
        assertTrue(measurements.all { it.angle >= 0f && it.angle < 360f })
    }

    @Test
    fun `returns empty list when crc is invalid`() {
        val packet = ByteBuffer.allocate(47).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0x54.toByte())
            put(0x2C.toByte())
            putShort(0)
            putShort((0).toShort())
            repeat(12) {
                putShort(100.toShort())
                put(0)
            }
            putShort((1000).toShort())
            putShort(0)
            put(0)
        }.array().apply { this[lastIndex] = computeCrc(this).toByte() }

        val parser = LidarParser()
        val valid = parser.parse(packet)
        assertEquals(12, valid.size)

        val corrupted = packet.clone()
        corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 0xFF).toByte()
        val result = parser.parse(corrupted)

        assertTrue(result.isEmpty())
    }

    private fun computeCrc(data: ByteArray): Int {
        var crc = 0
        for (i in 0 until data.lastIndex) {
            var value = (crc xor (data[i].toInt() and 0xFF)) and 0xFF
            repeat(8) {
                value = if ((value and 0x80) != 0) {
                    ((value shl 1) xor 0x4D)
                } else {
                    value shl 1
                }
            }
            crc = value and 0xFF
        }
        return crc
    }
}
