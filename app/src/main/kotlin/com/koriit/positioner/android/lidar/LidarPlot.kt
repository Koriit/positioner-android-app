package com.koriit.positioner.android.lidar

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
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
    confidenceThreshold: Int = 100,
    gradientMin: Int = 100,
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

        // Calculate confidence statistics
        val confidenceStats = if (measurements.isNotEmpty()) {
            val confidences = measurements.map { it.confidence }
            val minConfidence = confidences.minOrNull() ?: 0
            val maxConfidence = confidences.maxOrNull() ?: 255
            Triple(minConfidence, maxConfidence, confidences.average())
        } else {
            Triple(0, 255, 0.0)
        }

        translate(left = center.x, top = center.y) {
            points.forEach { (x, y, confidence) ->
                val px = x * scale
                val py = -y * scale

                // Create gradient color based on confidence (0-255)
                // 0 = red (low confidence), 255 = green (high confidence)
                val normalizedConfidence = ((confidence - gradientMin) / (255f - gradientMin)).coerceIn(0f, 1f)
                val color = Color(
                    red = 1f - normalizedConfidence,
                    green = normalizedConfidence,
                    blue = 0f,
                    alpha = 1f
                )

                drawCircle(color, radius = 3f, center = Offset(px, py))
            }
        }

        // Draw confidence indicators
        drawConfidenceIndicators(confidenceStats, size, confidenceThreshold, measurements.size)
    }
}

private fun DrawScope.drawConfidenceIndicators(
    confidenceStats: Triple<Int, Int, Double>,
    size: androidx.compose.ui.geometry.Size,
    confidenceThreshold: Int,
    totalMeasurementsCount: Int
) {
    val (minConf, maxConf, avgConf) = confidenceStats

    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 12.sp.toPx()
            isAntiAlias = true
        }

        val textPadding = 8.dp.toPx()
        val indicatorHeight = 20.dp.toPx()
        val indicatorWidth = 100.dp.toPx()

        // Background for indicators
        val bgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            alpha = 200
        }

        // Min confidence indicator (top-left)
        val minColor = android.graphics.Color.rgb(
            (255 * (1f - minConf / 255f)).toInt(),
            (255 * (minConf / 255f)).toInt(),
            0
        )
        val minPaint = android.graphics.Paint().apply {
            color = minColor
            style = android.graphics.Paint.Style.FILL
        }

        canvas.nativeCanvas.drawRect(
            textPadding,
            textPadding,
            textPadding + indicatorWidth,
            textPadding + indicatorHeight,
            bgPaint
        )
        canvas.nativeCanvas.drawRect(
            textPadding + 2.dp.toPx(),
            textPadding + 2.dp.toPx(),
            textPadding + 18.dp.toPx(),
            textPadding + 18.dp.toPx(),
            minPaint
        )
        canvas.nativeCanvas.drawText(
            "Min: $minConf",
            textPadding + 25.dp.toPx(),
            textPadding + 15.dp.toPx(),
            paint
        )

        // Max confidence indicator (top-right)
        val maxColor = android.graphics.Color.rgb(
            (255 * (1f - maxConf / 255f)).toInt(),
            (255 * (maxConf / 255f)).toInt(),
            0
        )
        val maxPaint = android.graphics.Paint().apply {
            color = maxColor
            style = android.graphics.Paint.Style.FILL
        }

        canvas.nativeCanvas.drawRect(
            size.width - textPadding - indicatorWidth,
            textPadding,
            size.width - textPadding,
            textPadding + indicatorHeight,
            bgPaint
        )
        canvas.nativeCanvas.drawRect(
            size.width - textPadding - indicatorWidth + 2.dp.toPx(),
            textPadding + 2.dp.toPx(),
            size.width - textPadding - indicatorWidth + 18.dp.toPx(),
            textPadding + 18.dp.toPx(),
            maxPaint
        )
        canvas.nativeCanvas.drawText(
            "Max: $maxConf",
            size.width - textPadding - indicatorWidth + 25.dp.toPx(),
            textPadding + 15.dp.toPx(),
            paint
        )

        // Average confidence indicator (bottom-left)
        canvas.nativeCanvas.drawRect(
            textPadding,
            size.height - textPadding - indicatorHeight,
            textPadding + indicatorWidth,
            size.height - textPadding,
            bgPaint
        )
        canvas.nativeCanvas.drawText(
            "Avg: ${String.format("%.1f", avgConf)}",
            textPadding + 5.dp.toPx(),
            size.height - textPadding - 5.dp.toPx(),
            paint
        )

        // Confidence threshold (bottom-right)
        val thresholdWidth = 150.dp.toPx()
        canvas.nativeCanvas.drawRect(
            size.width - textPadding - thresholdWidth,
            size.height - textPadding - 40.dp.toPx(),
            size.width - textPadding,
            size.height - textPadding,
            bgPaint
        )

        canvas.nativeCanvas.drawText(
            "Threshold: $confidenceThreshold",
            size.width - textPadding - thresholdWidth + 5.dp.toPx(),
            size.height - textPadding - 20.dp.toPx(),
            paint
        )
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
        confidenceThreshold = 100,
    )
}