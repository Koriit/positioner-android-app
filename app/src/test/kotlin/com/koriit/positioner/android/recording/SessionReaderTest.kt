package com.koriit.positioner.android.recording

import com.koriit.positioner.android.lidar.LidarMeasurement
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SessionReaderTest {
    @Test
    fun `reads gzipped new format`() {
        val rotation = Rotation(
            measurements = listOf(
                LidarMeasurement(10f, 100, 255),
                LidarMeasurement(20f, 100, 255)
            )
        )
        val json = Json.encodeToString(rotation)
        val file = File.createTempFile("rotation", ".json.gz")
        GZIPOutputStream(FileOutputStream(file)).bufferedWriter().use { writer ->
            writer.write(json)
            writer.newLine()
        }

        FileInputStream(file).use { stream ->
            val result = SessionReader.read(stream)
            assertEquals(1, result.size)
            assertEquals(2, result.first().measurements.size)
        }
    }

    @Test
    fun `reads gzipped legacy format`() {
        val measurements = listOf(
            LidarMeasurement(10f, 100, 255),
            LidarMeasurement(20f, 100, 255),
            LidarMeasurement(5f, 100, 255) // triggers new rotation
        )
        val json = Json.encodeToString(measurements)
        val file = File.createTempFile("legacy", ".json.gz")
        GZIPOutputStream(FileOutputStream(file)).bufferedWriter().use { writer ->
            writer.write(json)
        }

        FileInputStream(file).use { stream ->
            val result = SessionReader.read(stream)
            assertEquals(2, result.size)
            assertEquals(2, result.first().measurements.size)
            assertEquals(1, result.last().measurements.size)
        }
    }
}
