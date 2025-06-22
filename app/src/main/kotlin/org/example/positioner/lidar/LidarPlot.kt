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
    rotate90: Boolean = false,
    autoScale: Boolean = false,
) {
    Canvas(modifier = modifier) {
        val points = measurements.map { m ->
            var (x, y) = m.toPoint()
            if (rotate90) {
                val rx = y
                val ry = -x
                x = rx
                y = ry
            }
            x to y
        }

        val maxRange = if (autoScale) {
            points.maxOfOrNull { (x, y) -> kotlin.math.hypot(x.toDouble(), y.toDouble()).toFloat() }
                ?: 1f
        } else {
            4f // metres, matches python default
        }

        val scale = min(size.width, size.height) / (maxRange * 2f)
        val center = Offset(size.width / 2f, size.height / 2f)
        points.forEach { (x, y) ->
            val px = center.x + x * scale
            val py = center.y - y * scale
            drawCircle(Color.Red, radius = 3f, center = Offset(px, py))
        }
    }
}

@Preview
@Composable
fun PreviewLidarPlot() {
    LidarPlot(
        measurements = listOf(
        LidarMeasurement(2.0f, 2, 2),
        LidarMeasurement(22.0f, 2, 2),
        LidarMeasurement(42.0f, 2, 2),
        LidarMeasurement(62.0f, 2, 2),
        ),
        rotate90 = true,
        autoScale = true,
    )
}