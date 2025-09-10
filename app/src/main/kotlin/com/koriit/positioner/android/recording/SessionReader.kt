package com.koriit.positioner.android.recording

import android.content.Context
import android.net.Uri
import com.koriit.positioner.android.lidar.LidarMeasurement
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.io.buffered
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Read session recordings supporting both legacy and current formats.
 */
object SessionReader {
    private val json = Json

    fun read(context: Context, uri: Uri): List<Rotation> {
        context.contentResolver.openInputStream(uri)?.use { input ->
            return read(input)
        }
        return emptyList()
    }

    internal fun read(input: InputStream): List<Rotation> {
        val buffered = input.buffered()
        val stream = if (isGzip(buffered)) GZIPInputStream(buffered) else buffered
        val content = stream.bufferedReader().use { it.readText() }
        val trimmed = content.trimStart()
        return if (trimmed.startsWith("[")) {
            // Legacy format: plain JSON array of measurements
            val data = json.decodeFromString<List<LidarMeasurement>>(content)
            val rotations = mutableListOf<Rotation>()
            var current = mutableListOf<LidarMeasurement>()
            var lastAngle: Float? = null
            data.forEach { m ->
                lastAngle?.let { prev ->
                    if (m.angle < prev) {
                        rotations.add(Rotation(current, current.first().timestamp))
                        current = mutableListOf()
                    }
                }
                current.add(m)
                lastAngle = m.angle
            }
            if (current.isNotEmpty()) rotations.add(Rotation(current, current.first().timestamp))
            rotations
        } else {
            // Current format: one rotation per line
            content.lineSequence()
                .filter { it.isNotBlank() }
                .map { json.decodeFromString<Rotation>(it) }
                .toList()
        }
    }

    private fun isGzip(input: InputStream): Boolean {
        input.mark(2)
        val header = ByteArray(2)
        val read = input.read(header)
        input.reset()
        return read == 2 && header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte()
    }
}
