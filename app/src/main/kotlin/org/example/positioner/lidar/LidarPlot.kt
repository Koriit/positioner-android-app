package org.example.positioner.lidar

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.min

/**
 * Display a scatter plot of lidar measurements.
 */
@Composable
fun LidarPlot(
    measurements: List<LidarMeasurement>,
    modifier: Modifier = Modifier,
    rotation: Int = 0,
    autoScale: Boolean = false,
) {
    Canvas(modifier = modifier) {
        val points = measurements.map { m ->
            var (x, y) = m.toPoint()
            if (rotation != 0) {
                val angleRad = Math.toRadians(rotation.toDouble())
                val cos = kotlin.math.cos(angleRad).toFloat()
                val sin = kotlin.math.sin(angleRad).toFloat()
                val rx = x * cos - y * sin
                val ry = x * sin + y * cos
                x = rx
                y = ry
            }
            Triple(x, y, m.confidence)
        }

        val maxRange = if (autoScale) {
            points.maxOfOrNull { (x, y, _) ->
                kotlin.math.hypot(x.toDouble(), y.toDouble()).toFloat()
            }
                ?: 1f
        } else {
            4f // metres, matches python default
        }

        val scale = min(size.width, size.height) / (maxRange * 2f)
        val center = Offset(size.width / 2f, size.height / 2f)

        points.forEach { (x, y, confidence) ->
            val px = center.x + x * scale
            val py = center.y - y * scale

            // Create gradient color based on confidence (0-255)
            // 0 = red (low confidence), 255 = green (high confidence)
            val normalizedConfidence = confidence / 255f
            val color = Color(
                red = 1f - normalizedConfidence,
                green = normalizedConfidence,
                blue = 0f,
                alpha = 1f
            )

            drawCircle(color, radius = 3f, center = Offset(px, py))
        }
    }
}

@Preview
@Composable
fun PreviewLidarPlot() {
    LidarPlot(
        measurements = listOf(
            LidarMeasurement(2.0f, 2, 50),   // Low confidence
            LidarMeasurement(22.0f, 2, 128), // Medium confidence
            LidarMeasurement(42.0f, 2, 200), // High confidence
            LidarMeasurement(62.0f, 2, 255), // Maximum confidence
        ),
        rotation = 0,
        autoScale = true,
    )
}