package org.example.positioner.lidar

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Serializer for [OffsetDateTime] using ISO-8601 strings. */
@Suppress("unused")
object OffsetDateTimeSerializer : kotlinx.serialization.KSerializer<OffsetDateTime> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "OffsetDateTime",
        kotlinx.serialization.descriptors.PrimitiveKind.STRING
    )

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: OffsetDateTime) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): OffsetDateTime {
        return OffsetDateTime.parse(decoder.decodeString())
    }
}

/**
 * Single measurement from the LIDAR.
 */
@Serializable
data class LidarMeasurement(
    val angle: Float,       // angle in degrees
    val distanceMm: Int,    // distance in millimetres
    val confidence: Int,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val timestamp: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
) {
    /**
     * Convert the polar measurement to cartesian coordinates in metres.
     */
    fun toPoint(): Pair<Float, Float> {
        val r = distanceMm / 1000f
        val rad = angle / 180f * PI.toFloat()
        val x = sin(rad) * r
        val y = cos(rad) * r
        return x to y
    }
}
