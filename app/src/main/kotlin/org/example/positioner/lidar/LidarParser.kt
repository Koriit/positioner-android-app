package org.example.positioner.lidar

import org.example.positioner.logging.AppLog
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser for packets produced by the YDLIDAR X4 and compatible devices.
 */
class LidarParser {
    companion object {
        private const val TAG = "LidarParser"
        private const val PACKET_LENGTH = 47
        private const val MEASUREMENT_LENGTH = 12
    }

    /**
     * Parse a single packet. `data` must contain exactly 47 bytes starting with
     * 0x54 and 0x2C.
     */
    fun parse(data: ByteArray): List<LidarMeasurement> {
        require(data.size == PACKET_LENGTH) { "Invalid packet length" }
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buf.get() // header 0x54
        buf.get() // length 0x2C
        buf.short // speed
        val startAngle = (buf.short.toUShort().toFloat()) / 100f
        val distance = IntArray(MEASUREMENT_LENGTH)
        val confidence = IntArray(MEASUREMENT_LENGTH)
        for (i in 0 until MEASUREMENT_LENGTH) {
            distance[i] = buf.short.toUShort().toInt()
            confidence[i] = buf.get().toUByte().toInt()
        }
        var stopAngle = (buf.short.toUShort().toFloat()) / 100f
        buf.short // timestamp
        buf.get()  // crc

        if (stopAngle < startAngle) {
            stopAngle += 360f
        }
        val step = (stopAngle - startAngle) / (MEASUREMENT_LENGTH - 1)
        AppLog.d(TAG, "startAngle=$startAngle stopAngle=$stopAngle")
        val result = mutableListOf<LidarMeasurement>()
        for (i in 0 until MEASUREMENT_LENGTH) {
            val angle = startAngle + step * i
            result.add(LidarMeasurement(angle, distance[i], confidence[i]))
        }
        return result
    }
}
